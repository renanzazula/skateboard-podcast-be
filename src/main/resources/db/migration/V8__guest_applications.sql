-- "Apply to be a podcast guest" (.docs/README_GUEST_APPLICATION_lang.md).

CREATE TABLE guest_applications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    email VARCHAR(320) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    social_links_json TEXT NOT NULL DEFAULT '[]',
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    reviewed_by UUID,
    -- Outbox-equivalent column, same role as posts.notified_at: null means
    -- GUEST_APPLICATION_SUBMITTED is still owed, set only once the broker
    -- confirmed it. PendingGuestApplicationNotificationJob retries the rest.
    notified_at TIMESTAMPTZ
);

CREATE INDEX ix_guest_applications_user ON guest_applications (user_id);

-- Admin list, filterable by status, newest first.
CREATE INDEX ix_guest_applications_status ON guest_applications (status, created_at DESC);

-- "One active application per user" (spec §4), enforced atomically so
-- concurrent submissions cannot both land: NEW/CONTACTED/ACCEPTED are active,
-- a DECLINED one does not block a new application.
CREATE UNIQUE INDEX ux_guest_applications_active_user ON guest_applications (user_id)
    WHERE status IN ('NEW', 'CONTACTED', 'ACCEPTED');
