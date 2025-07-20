import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'
import { create, getNumericDate } from "https://deno.land/x/djwt@v3.0.1/mod.ts"

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
  targetSpecificUsers?: string[]
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
    const firebaseServiceAccountB64 = Deno.env.get('FIREBASE_SERVICE_ACCOUNT_B64')
    if (!firebaseServiceAccountB64) {
      throw new Error('FIREBASE_SERVICE_ACCOUNT_B64 environment variable not set')
    }

    // Decode Base64 and parse JSON
    const serviceAccountJson = new TextDecoder().decode(
      Uint8Array.from(atob(firebaseServiceAccountB64), c => c.charCodeAt(0))
    )
    const serviceAccount = JSON.parse(serviceAccountJson)
    const projectId = serviceAccount.project_id

    // Determine target users
    let targetUserIds: string[] = []

    if (payload.targetSpecificUsers && payload.targetSpecificUsers.length > 0) {
      targetUserIds = payload.targetSpecificUsers
    } else {
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
        sender_id: payload.wineyardId,
        notification_type: payload.notificationType,
        title: payload.title,
        message: payload.message,
        wine_id: payload.wineId
      })
      .select()
      .single()

    if (notificationError) {
      console.error('Failed to create notification record:', notificationError)
    }

    // Get OAuth2 access token for Firebase
    const accessToken = await getFirebaseAccessToken(serviceAccount)

    // Send notifications individually (FCM HTTP v1 API)
    let totalSuccess = 0
    let totalFailure = 0

    for (const token of fcmTokens) {
      try {
        const message = {
          message: {
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
                color: '#D4AF37',
                sound: 'default'
              }
            }
          }
        }

        const fcmResponse = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${accessToken}`,
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(message)
        })

        if (fcmResponse.ok) {
          totalSuccess++
        } else {
          totalFailure++
          console.error('FCM Error for token:', token, await fcmResponse.text())
        }
      } catch (error) {
        totalFailure++
        console.error('Error sending to token:', token, error)
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
  const now = getNumericDate(new Date())
  const exp = getNumericDate(new Date(Date.now() + 3600 * 1000)) // 1 hour

  const payload = {
    iss: serviceAccount.client_email,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: exp,
  }

  // Import private key
  const privateKey = await crypto.subtle.importKey(
    'pkcs8',
    new TextEncoder().encode(serviceAccount.private_key),
    {
      name: 'RSASSA-PKCS1-v1_5',
      hash: 'SHA-256',
    },
    false,
    ['sign']
  )

  // Create and sign JWT
  const jwt = await create({ alg: 'RS256', typ: 'JWT' }, payload, privateKey)

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
  if (!tokenData.access_token) {
    throw new Error(`Failed to get access token: ${JSON.stringify(tokenData)}`)
  }
  
  return tokenData.access_token
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