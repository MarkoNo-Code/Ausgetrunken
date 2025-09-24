-- Fix the current test user that was created incorrectly
-- Run this in Supabase SQL Editor to fix nonnsense90@gmail.com

-- 1. Fix the user type for the test user
UPDATE user_profiles
SET
    user_type = 'WINEYARD_OWNER',
    updated_at = NOW(),
    hybrid_corrected = true  -- Mark that this was fixed
WHERE email = 'nonnsense90@gmail.com'
  AND user_type = 'CUSTOMER';

-- 2. Verify the fix was applied
SELECT
    id,
    email,
    user_type,
    hybrid_corrected,
    created_at,
    updated_at
FROM user_profiles
WHERE email = 'nonnsense90@gmail.com';

-- 3. Check the metadata in auth.users to confirm it matches
SELECT
    id,
    email,
    raw_user_meta_data->>'user_type' as metadata_user_type
FROM auth.users
WHERE email = 'nonnsense90@gmail.com';

-- Expected results:
-- user_profiles.user_type should be 'WINEYARD_OWNER'
-- auth.users metadata should show 'WINEYARD_OWNER'
-- These should now match!