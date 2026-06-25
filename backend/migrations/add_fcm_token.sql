-- Migration: add fcm_token column to employee table
-- Run manually on Cloud SQL before deploying the new backend version
-- Safe to run multiple times (IF NOT EXISTS guard)

ALTER TABLE employee ADD COLUMN IF NOT EXISTS fcm_token VARCHAR(255);
