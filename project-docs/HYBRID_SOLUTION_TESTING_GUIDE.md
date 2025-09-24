# Hybrid User Type Solution - Testing Guide

**Status**: ✅ **DEPLOYED AND READY FOR TESTING**
**Build**: Successfully compiled and installed on emulator-5554
**Date**: 2025-09-24

## Quick Start Testing

### Prerequisites
- ✅ Hybrid solution deployed to device
- ✅ Emulator running on `emulator-5554`
- ✅ Fresh email addresses for testing (no cached data)

### Test Scenario 1: Fresh WINEYARD_OWNER Registration

**Goal**: Verify immediate profile creation works

1. **Start App & Register**:
   ```bash
   adb -s emulator-5554 shell am start -n com.ausgetrunken/.MainActivity
   ```

2. **Register New User**:
   - Use fresh email: `test-owner-{timestamp}@example.com`
   - Select **"Wineyard Owner"** account type
   - Complete registration

3. **Monitor Logs**:
   ```bash
   adb -s emulator-5554 logcat -s System.out | grep "USER_TYPE_DEBUG\|HYBRID"
   ```

4. **Expected Log Messages**:
   ```
   🔍 USER_TYPE_DEBUG: ========== REGISTRATION START ==========
   🔍 USER_TYPE_DEBUG: Requested UserType: WINEYARD_OWNER
   🚀 HYBRID SOLUTION: Attempting immediate profile creation
   ✅ HYBRID SOLUTION: Immediate profile created successfully with type: WINEYARD_OWNER
   ```

### Test Scenario 2: Backup System Verification

**Goal**: Test what happens if immediate creation fails

1. **Look for These Log Patterns**:
   ```
   ⚠️ HYBRID SOLUTION: Immediate profile creation failed
   🔧 HYBRID SOLUTION: Profile exists, attempting immediate correction
   ✅ HYBRID SOLUTION: Immediate correction successful
   ```

2. **Or on Login** (if immediate fails):
   ```
   🛡️ HYBRID BACKUP: Starting comprehensive user type verification
   🚨 HYBRID BACKUP: CRITICAL USER TYPE MISMATCH DETECTED!
   ✅ HYBRID BACKUP: SUCCESS! Profile corrected: CUSTOMER → WINEYARD_OWNER
   🎉 HYBRID SOLUTION: Self-healing system successfully recovered
   ```

### Test Scenario 3: Customer Registration (Control Test)

**Goal**: Ensure CUSTOMER registrations still work normally

1. **Register as Customer**:
   - Use fresh email: `test-customer-{timestamp}@example.com`
   - Select **"Customer"** account type

2. **Expected Behavior**:
   - Should register normally
   - Profile created with type: CUSTOMER
   - No correction messages needed

## Database Verification

### Check Profile Creation
```sql
-- Query to check recent profiles (run in Supabase SQL Editor)
SELECT
    id,
    email,
    user_type,
    hybrid_corrected,
    created_at,
    updated_at
FROM user_profiles
WHERE created_at > NOW() - INTERVAL '1 hour'
ORDER BY created_at DESC;
```

### Check Metadata Consistency
```sql
-- Verify metadata matches profile type
SELECT
    up.email,
    up.user_type as profile_type,
    au.raw_user_meta_data->>'user_type' as metadata_type,
    CASE
        WHEN up.user_type = au.raw_user_meta_data->>'user_type' THEN 'CONSISTENT'
        ELSE 'MISMATCH'
    END as consistency_check
FROM user_profiles up
JOIN auth.users au ON up.id = au.id
WHERE up.created_at > NOW() - INTERVAL '1 hour';
```

## Success Indicators

### ✅ Immediate Creation Success
- Log shows: `✅ HYBRID SOLUTION: Immediate profile created successfully`
- Database profile has correct user_type immediately
- No backup correction needed

### ✅ Immediate Correction Success
- Log shows: `🔧 HYBRID SOLUTION: Profile exists, attempting immediate correction`
- Followed by: `✅ HYBRID SOLUTION: Immediate correction successful`
- Database shows correct type after registration

### ✅ Backup System Success
- Log shows: `🛡️ HYBRID BACKUP: Starting comprehensive user type verification`
- If mismatch detected: `🚨 HYBRID BACKUP: CRITICAL USER TYPE MISMATCH DETECTED!`
- Followed by: `✅ HYBRID BACKUP: SUCCESS! Profile corrected`

## Failure Scenarios to Monitor

### ❌ Complete Failure (Both Systems Fail)
```
❌ HYBRID SOLUTION: Immediate profile creation failed
❌ HYBRID BACKUP: CRITICAL FAILURE - Could not correct user type
🚨 HYBRID BACKUP: Manual intervention may be required
```

If this occurs:
1. Check database connectivity
2. Verify user permissions
3. Check for database schema changes
4. Review error messages for specific issues

### ❌ Trigger Still Overriding
```
✅ HYBRID SOLUTION: Immediate profile created successfully
🚨 HYBRID BACKUP: CRITICAL USER TYPE MISMATCH DETECTED!
```

If immediate creation succeeds but backup still detects mismatch:
- Indicates trigger is running AFTER our immediate creation
- Backup system will fix it, but suggests trigger timing issue

## Testing Checklist

### Before Testing
- [ ] Hybrid solution deployed successfully
- [ ] Emulator/device running
- [ ] Logcat monitoring set up
- [ ] Fresh email addresses prepared
- [ ] Supabase console access ready

### During Testing
- [ ] Test WINEYARD_OWNER registration
- [ ] Monitor logs for hybrid messages
- [ ] Test CUSTOMER registration (control)
- [ ] Verify database entries
- [ ] Test login flow for both types

### After Testing
- [ ] Check database consistency
- [ ] Review logs for any failures
- [ ] Test existing user logins
- [ ] Verify navigation works correctly

## Advanced Testing

### Load Testing
- Register multiple users in succession
- Mix of CUSTOMER and WINEYARD_OWNER types
- Monitor for any race conditions

### Edge Case Testing
- Test with existing email addresses
- Test with malformed emails
- Test network interruption scenarios

### Performance Testing
- Monitor registration time impact
- Check database query efficiency
- Verify no unnecessary corrections on existing users

## Rollback Procedure

If critical issues are found:

1. **Immediate Rollback**:
   - Revert `SupabaseAuthRepository.kt` to previous version
   - Rebuild and redeploy

2. **Database State**:
   - Hybrid solution doesn't modify trigger
   - Existing profiles remain unchanged
   - `hybrid_corrected` field can be ignored

3. **User Impact**:
   - System returns to previous behavior
   - Manual corrections still needed for affected users
   - No data loss

## Contact Information

**For Issues**:
- Check logs first for specific error messages
- Review database state via Supabase console
- Validate that user metadata contains expected values

**Success Metrics**:
- Zero manual user type corrections needed
- All new WINEYARD_OWNER registrations have correct type
- Existing users with wrong type automatically corrected
- No performance degradation in registration flow