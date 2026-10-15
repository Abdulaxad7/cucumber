-- V7 (phase-4 D9, E74 option B, approved 2026-10-14): drop the unused audit_log table with its four foreign keys.
-- Nothing ever wrote or read it (the entity had no accessors), and its json mapping fails on PostgreSQL. The
-- auth-service-audit spec creates audit_logs (plural) with metadata jsonb in its own migration, together with the code
-- that writes and reads it.
-- Assumption: the table is empty; no code path could insert a row.

DROP TABLE audit_log;
