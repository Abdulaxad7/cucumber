-- V5 (phase-4 D8 items 5 and 6, E72): case-insensitive uniqueness.
-- Emails are stored lower case (the application trims and lower-cases them, phase-1 D12), so the existing unique
-- constraint uk_users_email is case-insensitive uniqueness once the database guarantees lower case.
-- Project names are stored with their case, so a unique index on (org_id, lower(name)) makes them unique per
-- organization ignoring letter case, archived projects included (phase-2 D5); its leading org_id also indexes the
-- foreign key. lower() runs under the ICU root collation "und-x-icu" (user decision 2026-10-15): it uses full Unicode
-- case mapping like Java's toLowerCase(Locale.ROOT), so "İzmir" and "izmir" stay different names, while the builtin
-- C.UTF-8 lower() (simple case mapping) would make them equal. The application check under the organization lock stays
-- the primary rule (PROJECT_NAME_TAKEN).
-- Assumption: stored emails are already lower case and no organization has two names equal under this lower(); the
-- application has always enforced both.

ALTER TABLE users
    ADD CONSTRAINT ck_users_email_lower_case CHECK (email = lower(email));

ALTER TABLE invitations
    ADD CONSTRAINT ck_invitations_email_lower_case CHECK (email = lower(email));

CREATE UNIQUE INDEX uk_projects_org_id_name_lower ON projects (org_id, lower(name COLLATE "und-x-icu"));
