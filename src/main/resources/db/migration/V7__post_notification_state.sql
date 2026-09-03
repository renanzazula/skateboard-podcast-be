-- Guards against notifying twice about the same episode.
--
-- skateboard-notification-be dedupes by event id, but that only helps once an
-- event has been emitted. This column is what decides whether to emit at all:
-- it is set only after the broker accepted the event, so a failed publish
-- leaves it null and the reconciliation job in adapter/in/scheduler re-emits.
-- It is equally what stops an edit of an already-published post, a re-sync, or
-- a replayed job from producing a second notification.
--
-- Existing rows are backfilled to now() rather than left null: they were all
-- published before notifications existed, and leaving them null would make the
-- reconciliation job treat the entire back catalogue as owed. The recency
-- window would filter most of it out, but "most" is not a guarantee worth
-- betting a push to every user on.
ALTER TABLE posts ADD COLUMN notified_at TIMESTAMPTZ;
UPDATE posts SET notified_at = now() WHERE notified_at IS NULL;
