-- Migration: Add ON DELETE CASCADE to posts table
-- This allows database to automatically delete all posts when a user is deleted
-- Performance: Optimized for bulk delete operations

-- Drop existing foreign key constraint if exists
ALTER TABLE posts
DROP CONSTRAINT IF EXISTS fk_posts_user_id;

ALTER TABLE posts
DROP CONSTRAINT IF EXISTS posts_user_id_fkey;

-- Add new foreign key with CASCADE DELETE
ALTER TABLE posts
ADD CONSTRAINT fk_posts_user_id
FOREIGN KEY (user_id)
REFERENCES users(id)
ON DELETE CASCADE;

-- Add index on user_id for performance (if not exists)
CREATE INDEX IF NOT EXISTS idx_posts_user_id ON posts(user_id);

-- Comment for documentation
COMMENT ON CONSTRAINT fk_posts_user_id ON posts IS
'Foreign key with CASCADE DELETE - automatically deletes all posts when user is deleted';
