# Deploy Edge Function Fix for Notification System

## Issue Discovered
The notification system was reporting "sent notification to 0 subscribers" despite having 2 active subscribers in the database.

**Root Cause**: Naming mismatch after vineyard → winery refactoring:
- **Android App** sends: `wineryId`, queries `wineries` and `winery_subscriptions` tables
- **Edge Function** expected: `wineyardId`, queries `wineyards` and `wineyard_subscriptions` tables

## Changes Made
Updated `supabase/functions/send-fcm-notification/index.ts` to use new naming:

### Interface Changes:
```typescript
// OLD:
interface NotificationPayload {
  wineyardId: string
  ...
}

// NEW:
interface NotificationPayload {
  wineryId: string
  ...
}
```

### Database Table References:
```typescript
// OLD:
.from('wineyards')
.from('wineyard_subscriptions')

// NEW:
.from('wineries')
.from('winery_subscriptions')
```

### Field References:
- `wineyard_id` → `winery_id`
- `wineyardId` → `wineryId`
- `OPEN_WINEYARD_DETAIL` → `OPEN_WINERY_DETAIL`

## Deployment Steps

### 1. Install Supabase CLI (if not already installed)
```bash
npm install -g supabase
```

### 2. Login to Supabase
```bash
supabase login
```

### 3. Link to Project
```bash
cd C:\Users\marko\Documents\Claude-Projects\Ausgetrunken\project-docs\supabase-config
supabase link --project-ref xjlbypzhixeqvksxnilk
```

### 4. Deploy the Edge Function
```bash
supabase functions deploy send-fcm-notification
```

### 5. Verify Deployment
1. Open Supabase Dashboard: https://supabase.com/dashboard/project/xjlbypzhixeqvksxnilk/functions
2. Check that `send-fcm-notification` function shows recent deployment timestamp
3. Review function logs for any deployment errors

## Testing After Deployment

### 1. Login to Winery Owner Account
- Open app on emulator
- Login as winery owner

### 2. Navigate to Notification Management
- Go to Profile → Notification Management
- Verify subscriber count shows correct number (should be 2)

### 3. Send Test Notification
- Select a wine with low stock
- Tap "Send" notification button
- **Expected Result**: "Sent notification to 2 subscribers" (not 0!)

### 4. Verify Customer Receipt
- Check customer device for push notification
- Notification should display with correct wine information

## Expected Edge Function Logs (After Fix)

```
🔍 Notification type: LOW_STOCK -> Column: low_stock_notifications
🔍 Querying subscribers for winery: <winery-id>
🔍 ALL subscriptions (before owner filtering): 2
🔍 Subscription query result (after owner filtering): 2 subscribers found
🔍 Getting FCM tokens for 2 target users
🔍 FCM token query result: 2 users with tokens
🔍 Final FCM tokens count: 2
```

## Rollback Plan (If Issues Occur)

If deployment causes issues:

1. **Immediate Rollback**: Redeploy previous version
```bash
git checkout <previous-commit-hash> -- supabase/functions/send-fcm-notification/index.ts
supabase functions deploy send-fcm-notification
```

2. **Alternative Fix**: Update Android app to use old naming (not recommended)

## Related Files
- **Edge Function**: `project-docs/supabase-config/supabase/functions/send-fcm-notification/index.ts`
- **Android Payload**: `app/src/main/java/com/ausgetrunken/data/repository/NotificationRepositoryImpl.kt`
- **Issue Tracking**: This document

## Success Criteria
✅ Edge Function deploys without errors
✅ Function logs show correct table/field names
✅ Subscriber count query returns 2 subscribers
✅ Notification sends to all 2 subscribers
✅ Customer devices receive push notifications

## Notes
- This fix is **critical** for the notification system to work
- No database migration needed (tables already use winery naming)
- Only the Edge Function code needed updating
- Fix should be deployed ASAP to restore notification functionality
