-- Optional SQL script to add hybrid correction tracking
-- This adds a field to track when the hybrid system corrects user types
-- Execute this in Supabase SQL Editor if you want to track hybrid corrections

-- Add hybrid_corrected column to user_profiles table
ALTER TABLE user_profiles
ADD COLUMN IF NOT EXISTS hybrid_corrected BOOLEAN DEFAULT FALSE;

-- Add index for performance if querying corrected profiles
CREATE INDEX IF NOT EXISTS idx_user_profiles_hybrid_corrected
ON user_profiles(hybrid_corrected)
WHERE hybrid_corrected = TRUE;

-- Query to check profiles that were corrected by hybrid system
-- SELECT id, email, user_type, hybrid_corrected, updated_at
-- FROM user_profiles
-- WHERE hybrid_corrected = TRUE
-- ORDER BY updated_at DESC;

-- Query to see all user type corrections over time
-- SELECT
--     id,
--     email,
--     user_type,
--     hybrid_corrected,
--     created_at,
--     updated_at,
--     CASE
--         WHEN updated_at > created_at + INTERVAL '5 minutes' THEN 'LIKELY_CORRECTED'
--         ELSE 'ORIGINAL'
--     END as correction_status
-- FROM user_profiles
-- ORDER BY created_at DESC;