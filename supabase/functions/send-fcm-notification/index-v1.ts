import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

interface NotificationPayload {
  wineyardId: string
  notificationType: 'LOW_STOCK' | 'NEW_RELEASE' | 'SPECIAL_OFFER' | 'GENERAL'
  title: string
  message: string
  wineId?: string
  targetSpecificUsers?: string[] // Optional: send to specific users instead of all subscribers
}

interface FCMMessage {
  message: {
    token?: string
    notification: {
      title: string
      body: string
    }
    data?: {
      wineId?: string
      wineyardId: string
      notificationType: string
      clickAction?: string
    }
    android?: {
      notification: {
        icon: string
        color: string
        sound: string
      }
    }
  }
}

interface FCMBatchMessage {
  messages: Array<{
    token: string
    notification: {
      title: string
      body: string
    }
    data?: {
      wineId?: string
      wineyardId: string
      notificationType: string
      clickAction?: string
    }
    android?: {
      notification: {
        icon: string
        color: string
        sound: string
      }
    }
  }>
}

serve(async (req) => {
  // Handle CORS preflight requests
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const supabaseClient = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )

    const payload: NotificationPayload = await req.json()
    console.log('Notification payload received:', payload)

    // Get Firebase service account from environment
    const firebaseServiceAccount = Deno.env.get('FIREBASE_SERVICE_ACCOUNT')
    if (!firebaseServiceAccount) {
      throw new Error('FIREBASE_SERVICE_ACCOUNT environment variable not set')
    }

    const serviceAccount = JSON.parse(firebaseServiceAccount)
    const projectId = serviceAccount.project_id

    // Determine target users
    let targetUserIds: string[] = []

    if (payload.targetSpecificUsers && payload.targetSpecificUsers.length > 0) {
      // Send to specific users
      targetUserIds = payload.targetSpecificUsers
    } else {
      // Get all subscribers to this wineyard with the relevant notification preference
      const subscriptionColumn = getSubscriptionColumn(payload.notificationType)
      
      const { data: subscribers, error: subscribersError } = await supabaseClient
        .from('wineyard_subscriptions')
        .select('user_id')
        .eq('wineyard_id', payload.wineyardId)
        .eq('is_active', true)
        .eq(subscriptionColumn, true)

      if (subscribersError) {
        throw new Error(`Failed to fetch subscribers: ${subscribersError.message}`)
      }

      targetUserIds = subscribers?.map(sub => sub.user_id) || []
    }

    if (targetUserIds.length === 0) {
      return new Response(
        JSON.stringify({ 
          success: true, 
          message: 'No subscribers found for this notification type',
          sentCount: 0 
        }),
        { 
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          status: 200
        }
      )
    }

    // Get FCM tokens for target users
    const { data: users, error: usersError } = await supabaseClient
      .from('user_profiles')
      .select('id, fcm_token')
      .in('id', targetUserIds)
      .not('fcm_token', 'is', null)

    if (usersError) {
      throw new Error(`Failed to fetch user FCM tokens: ${usersError.message}`)
    }

    const fcmTokens = users?.map(user => user.fcm_token).filter(token => token) || []

    if (fcmTokens.length === 0) {
      return new Response(
        JSON.stringify({ 
          success: true, 
          message: 'No valid FCM tokens found for target users',
          sentCount: 0 
        }),
        { 
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          status: 200
        }
      )
    }

    // Create notification record in database
    const { data: notificationRecord, error: notificationError } = await supabaseClient
      .from('notifications')
      .insert({
        wineyard_id: payload.wineyardId,
        sender_id: payload.wineyardId, // Assuming wineyard owner is the sender
        notification_type: payload.notificationType,
        title: payload.title,
        message: payload.message,
        wine_id: payload.wineId
      })
      .select()
      .single()

    if (notificationError) {
      console.error('Failed to create notification record:', notificationError)
      // Continue with FCM sending even if DB insert fails
    }

    // Get OAuth2 access token for Firebase
    const accessToken = await getFirebaseAccessToken(serviceAccount)

    // Prepare FCM messages for batch sending
    const messages = fcmTokens.map(token => ({
      token: token,
      notification: {
        title: payload.title,
        body: payload.message
      },
      data: {
        wineyardId: payload.wineyardId,
        notificationType: payload.notificationType,
        ...(payload.wineId && { wineId: payload.wineId }),
        clickAction: payload.wineId ? 'OPEN_WINE_DETAIL' : 'OPEN_WINEYARD_DETAIL'
      },
      android: {
        notification: {
          icon: 'ic_notification',
          color: '#D4AF37', // Gold color matching app theme
          sound: 'default'
        }
      }
    }))

    // Send FCM notifications in batches (FCM allows up to 500 messages per batch)
    const batchSize = 500
    let totalSuccess = 0
    let totalFailure = 0

    for (let i = 0; i < messages.length; i += batchSize) {
      const batch = messages.slice(i, i + batchSize)
      
      const fcmBatchPayload: FCMBatchMessage = {
        messages: batch
      }

      const fcmResponse = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${accessToken}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(fcmBatchPayload)
      })

      const fcmResult = await fcmResponse.json()
      console.log('FCM Batch Response:', fcmResult)

      if (fcmResult.responses) {
        const successCount = fcmResult.responses.filter((r: any) => !r.error).length
        const failureCount = fcmResult.responses.filter((r: any) => r.error).length
        totalSuccess += successCount
        totalFailure += failureCount
      }
    }

    // Create notification delivery records for successful sends
    if (notificationRecord && totalSuccess > 0) {
      const deliveryRecords = users?.slice(0, totalSuccess).map(user => ({
        notification_id: notificationRecord.id,
        user_id: user.id,
        delivered_at: new Date().toISOString()
      })) || []

      if (deliveryRecords.length > 0) {
        const { error: deliveryError } = await supabaseClient
          .from('notification_deliveries')
          .insert(deliveryRecords)

        if (deliveryError) {
          console.error('Failed to create delivery records:', deliveryError)
        }
      }
    }

    return new Response(
      JSON.stringify({ 
        success: true, 
        message: 'Notification sent successfully',
        sentCount: totalSuccess,
        failedCount: totalFailure
      }),
      { 
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        status: 200
      }
    )

  } catch (error) {
    console.error('Error sending notification:', error)
    return new Response(
      JSON.stringify({ 
        success: false, 
        error: error.message 
      }),
      { 
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        status: 500
      }
    )
  }
})

async function getFirebaseAccessToken(serviceAccount: any): Promise<string> {
  const now = Math.floor(Date.now() / 1000)
  const payload = {
    iss: serviceAccount.client_email,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600, // 1 hour
  }

  // Create JWT token
  const header = {
    alg: 'RS256',
    typ: 'JWT',
  }

  const encodedHeader = btoa(JSON.stringify(header)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '')
  const encodedPayload = btoa(JSON.stringify(payload)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '')

  // For production, you would need to sign this with the private key
  // This is a simplified version - in practice, you'd use a JWT library
  const unsignedToken = `${encodedHeader}.${encodedPayload}`
  
  // Sign the token (simplified - in practice use crypto library)
  const signature = await signJWT(unsignedToken, serviceAccount.private_key)
  const jwt = `${unsignedToken}.${signature}`

  // Exchange JWT for access token
  const response = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion: jwt,
    }),
  })

  const tokenData = await response.json()
  return tokenData.access_token
}

async function signJWT(data: string, privateKey: string): Promise<string> {
  // Import private key
  const keyData = privateKey.replace(/-----BEGIN PRIVATE KEY-----/, '')
    .replace(/-----END PRIVATE KEY-----/, '')
    .replace(/\s/g, '')
  
  const key = await crypto.subtle.importKey(
    'pkcs8',
    Uint8Array.from(atob(keyData), c => c.charCodeAt(0)),
    {
      name: 'RSASSA-PKCS1-v1_5',
      hash: 'SHA-256',
    },
    false,
    ['sign']
  )

  // Sign the data
  const signature = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5',
    key,
    new TextEncoder().encode(data)
  )

  // Convert to base64url
  return btoa(String.fromCharCode(...new Uint8Array(signature)))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=/g, '')
}

function getSubscriptionColumn(notificationType: string): string {
  switch (notificationType) {
    case 'LOW_STOCK':
      return 'low_stock_notifications'
    case 'NEW_RELEASE':
      return 'new_release_notifications'
    case 'SPECIAL_OFFER':
      return 'special_offer_notifications'
    case 'GENERAL':
      return 'general_notifications'
    default:
      return 'general_notifications'
  }
}