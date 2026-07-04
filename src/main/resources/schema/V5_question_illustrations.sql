-- ============================================================
--  V5 — Question illustrations (owner: Person 2)
--
--  Adds the illustration bytes to Questions. The image is stored IN the
--  database (LONGBLOB) because client and server run on separate machines:
--  a client-side file path would be meaningless on the server, and a BLOB
--  survives the two-machine defense demo with zero file copying.
--  `image_path` (V1) keeps the original file name for display.
--
--  Idempotent: MySQL has no ADD COLUMN IF NOT EXISTS, so the ALTER is
--  guarded by an INFORMATION_SCHEMA lookup — safe to run repeatedly.
--  (Fresh installs already get the column from V1 / the seed; this file
--  upgrades databases created before Phase 4.)
-- ============================================================
USE hsts_a3_db;

SET @have_column := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'hsts_a3_db'
      AND TABLE_NAME   = 'Questions'
      AND COLUMN_NAME  = 'image_data');

SET @ddl := IF(@have_column = 0,
    'ALTER TABLE Questions ADD COLUMN image_data LONGBLOB NULL AFTER image_path',
    'SELECT 1');

PREPARE migrate_v5 FROM @ddl;
EXECUTE migrate_v5;
DEALLOCATE PREPARE migrate_v5;
