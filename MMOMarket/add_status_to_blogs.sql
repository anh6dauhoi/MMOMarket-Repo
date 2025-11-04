-- Migration: Add status column to Blogs table
-- Description: Add status column for blog management (active/inactive)
-- Date: 2025-11-04
-- Database: MMOMarket

USE MMOMarket;

-- Add status column to Blogs table (after image column)
ALTER TABLE Blogs ADD COLUMN IF NOT EXISTS status TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Trạng thái: 1=Active, 0=Inactive' AFTER image;

-- Update existing records to have active status (if any null values exist)
UPDATE Blogs SET status = 1 WHERE status IS NULL OR status = 0;

