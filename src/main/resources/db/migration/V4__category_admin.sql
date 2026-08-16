-- Admin-owned category fields (see README_CATEGORY_MANAGEMENT_PLAN.md).
-- custom_name overrides the YouTube playlist title without fighting the sync
-- (effective name = COALESCE(custom_name, name)); default_locked freezes
-- is_default against updateFromYoutube once an admin has picked a default.
ALTER TABLE category ADD COLUMN custom_name    VARCHAR(255);
ALTER TABLE category ADD COLUMN default_locked BOOLEAN NOT NULL DEFAULT FALSE;
