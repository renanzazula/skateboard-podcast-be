# Category management — implementation plan

Give admins control over the category rail on the Podcast screen: **rename** a
category, **change its order**, and **pick the default** one the app opens on —
without fighting the YouTube sync job that creates those categories in the
first place.

Spans four repos, in deploy order:

| Phase | Repo | What |
|---|---|---|
| 1 | `skateboard-podcast-be` | Contract, domain, persistence (this repo) |
| 2 | `skateboard-infrastructure` | Keycloak permission |
| 3 | `skateboard-ui-backend` | BFF pass-through |
| 4 | `skateboard-fe` | Admin screen |

## What the review found

Categories are already first-class — each one mirrors a public YouTube playlist
of the channel, created and refreshed by `SynchronizeYoutubeChannelService`
every 10 minutes. The pieces this feature needs mostly exist, but none are
admin-controllable yet:

- The `category` table (`V3__categories.sql`) already has a `display_order`
  column — but nothing writes it, and the read query ignores it:
  `SpringCategoryRepository.findAllEnabledOrdered()` orders by
  `isDefault DESC, createdAt ASC`.
- `is_default` is owned by configuration, not admins: the sync flags whichever
  category matches `youtube.default-playlist-id` (env var), and
  `PodcastService.applyDefaultFallback` falls back to the `podcasts` slug or
  the first category. The FE mirrors that fallback in `useCategories`.
- The only category endpoints are reads — `GET /api/categories` and
  `GET /api/categories/{slug}/posts`, both gated by `FUNC_TAB_PODCAST`. There
  is no admin surface at all.

**The one real design problem:** `Category.updateFromYoutube(...)` overwrites
`name`, `description`, `coverUrl` *and* `isDefault` on every sync cycle. A
naive "rename" or "set default" endpoint would work for at most ten minutes —
then the next sync would silently revert it. Every admin-owned field below is
designed to survive the sync, not race it.

## Design decisions

**Rename = an override, not an edit.** Add a nullable `custom_name` column
instead of mutating `name`. The sync keeps refreshing `name` from YouTube
freely; every read resolves the effective name as `COALESCE(custom_name, name)`.
Renaming never conflicts with the sync, and clearing the override is a
first-class "reset to YouTube title" action for free.

**Default becomes admin-owned the moment an admin touches it.** Add
`default_locked` (boolean). While false, today's behavior is unchanged —
`youtube.default-playlist-id` keeps working as the bootstrap default. Once an
admin sets a default, the chosen row gets `is_default = true`, every other row
is cleared, and `default_locked = true` is set on all rows so
`updateFromYoutube` stops touching `is_default`. The config property is demoted
to a fallback for fresh installs.

**Order is a full permutation, written in one transaction.** Reordering accepts
the complete ordered list of category ids and writes `display_order = 0..n-1`
in one transaction — no fragile "move up one slot" deltas, no partial states.
The read query becomes
`ORDER BY display_order ASC NULLS LAST, is_default DESC, created_at ASC`, so
categories created by a later sync simply append to the end until an admin
slots them in. The sync already never writes `display_order`, so there is
nothing to race.

**Slugs never change.** The FE addresses categories exclusively by `slug`
(feed URLs, cache keys, the selected chip). Rename changes the display name
only. This keeps deep links stable and means the feed cache (`POST_CACHE`,
keyed `category:{slug}:{page}:{size}`) is untouched by renames;
`getCategories()` is uncached today, so list changes show up immediately.

| | Today | After |
|---|---|---|
| Name | overwritten by sync every 10 min | `COALESCE(custom_name, name)`; sync keeps its half |
| Order | `display_order` exists but is dead; sorted by default-then-age | admin permutation in `display_order`, new categories append |
| Default | env var `youtube.default-playlist-id`, plus slug fallback | admin-picked, locked against sync; env var = bootstrap only |

## Phase 1 — skateboard-podcast-be

This repo's `api/openapi.yaml` is the canonical, build-driving spec — the API
changes start here and flow outward.

### Migration — `V4__category_admin.sql`

```sql
ALTER TABLE category ADD COLUMN custom_name    VARCHAR(255);
ALTER TABLE category ADD COLUMN default_locked BOOLEAN NOT NULL DEFAULT FALSE;

-- Enforce the single-default invariant at the database, not just in code.
CREATE UNIQUE INDEX uk_category_single_default
    ON category ((TRUE)) WHERE is_default;
```

### Domain — `Category`

- New fields `customName`, `defaultLocked`; expose `getEffectiveName()` =
  `customName != null ? customName : name`.
- Admin mutators: `rename(String)` / `clearRename()`,
  `setDisplayOrder(Integer)`, `markDefault()` / `clearDefault()` (both set
  `defaultLocked = true`).
- `updateFromYoutube(...)` keeps refreshing `name`/`description`/`coverUrl`
  but only applies its `isDefault` argument when `!defaultLocked`.

### Use cases (one `@Service` per port, following the existing pattern)

- `GetAdminCategoriesUseCase` — all categories including disabled ones, with
  order and lock state.
- `UpdateCategoryUseCase` — rename via `customName`; `null`/blank clears the
  override.
- `ReorderCategoriesUseCase` — validates the id list covers known categories,
  writes positions transactionally.
- `SetDefaultCategoryUseCase` — `@Transactional`: clear old default → set new
  → lock.

### Endpoints (new `FUNC_PODCAST_MANAGE_CATEGORIES` permission on all four)

| Endpoint | Body | Behavior |
|---|---|---|
| `GET /api/admin/categories` | — | All categories (disabled included) as `AdminCategoryResponse`: effective name, `youtubeName`, `customName`, `displayOrder`, `enabled`, `default`, `postCount`. |
| `PATCH /api/admin/categories/{id}` | `{ "name": "Interviews" }` | Sets `custom_name`; `null` resets to the YouTube title. 404 on unknown id. |
| `PUT /api/admin/categories/order` | `{ "categoryIds": [...] }` | Full permutation → `display_order` 0..n−1. 400 if ids are unknown or duplicated. |
| `PUT /api/admin/categories/{id}/default` | — | Makes this the single default and locks defaulting against the sync. |

### Reads & caching

- `findAllEnabledOrdered()` →
  `ORDER BY display_order ASC NULLS LAST, is_default DESC, created_at ASC`.
- `toCategoryDto` maps the *effective* name into the existing
  `CategoryResponse.name` — the public contract doesn't change shape at all.
- No cache eviction needed: the category list is uncached by design, and feeds
  are keyed by immutable slug. Only if `getCategories()` is ever made
  `@Cacheable` must all four mutations evict it.

### Tests

- `UpdateCategoryServiceTest`, `ReorderCategoriesServiceTest`,
  `SetDefaultCategoryServiceTest` — invariants above.
- Extend `SynchronizeYoutubeChannelServiceTest`: a renamed + defaulted category
  survives an `updateFromYoutube` pass (name refreshed, override and default
  intact).
- `GetCategoriesServiceTest`: ordering with mixed null/set `display_order`.

## Phase 2 — skateboard-infrastructure

In `.docker/keycloak/realm-export.json`: add realm role
`FUNC_PODCAST_MANAGE_CATEGORIES` and include it in the `ADMIN` composite
(STANDARD stays read-only). The FE client's `authorities` mapper already
flattens composites into the token, so no mapper changes are needed.

> **Existing environments:** the realm export only seeds fresh imports. On the
> already-running Keycloak, add the role and attach it to `ADMIN` by hand
> (admin console or `kcadm`), or re-import the realm with the merge strategy
> the project already uses.

## Phase 3 — skateboard-ui-backend

```
PodcastController (@PreAuthorize) → PodcastService → PodcastClient → podcast-be
```

- Re-copy the updated spec into `api/openapi.yaml` (vendored copy — that repo
  doesn't own the contract), rebuild to regenerate the WebClient client.
- Add the four endpoints to `PodcastController` with
  `@PreAuthorize("hasAuthority('FUNC_PODCAST_MANAGE_CATEGORIES')")`, mirroring
  the spec's `x-required-permissions` as the repo convention requires.
- Extend `PodcastControllerSecurityTest`: no token → 401, `FUNC_TAB_PODCAST`
  only → 403, manage authority → 200, for each new route.

## Phase 4 — skateboard-fe

- Copy the BFF spec to `api/bff-openapi.yaml` and run `npm run generate:api`.
- New hook `useCategoryAdmin` next to `usePodcastAdmin`:
  `listAdminCategories`, `renameCategory`, `reorderCategories`,
  `setDefaultCategory`, one `submitting` flag.
- New route `src/app/(tabs)/podcast/admin/categories.tsx`, gated by
  `hasAuthority('FUNC_PODCAST_MANAGE_CATEGORIES')` with the same `<Redirect>`
  pattern as the other admin screens. Entry point: a "Manage categories" link
  in the Podcast header, beside "Sync now".
- Row design: category name + post count, **↑ / ↓ buttons** for ordering
  (works with a mouse, a thumb, and a keyboard — no drag-and-drop dependency),
  a **star toggle** for default, **tap-to-rename** opening a modal with a
  `TextField` pre-filled with the current name and a "Reset to YouTube title"
  secondary action for overridden names.
- Reorder optimistically, then reconcile with the server response; on failure,
  roll back and `showAlert` (the web-safe helper — *not* `Alert.alert`).
- The public list screen needs no changes: `useCategories` keeps reading
  `GET /api/categories`, which now arrives renamed, reordered, and correctly
  defaulted.

## Rollout & edge cases

Ship in phase order — every step is additive and backward-compatible: the
migration adds nullable/default columns, the public `CategoryResponse` shape is
unchanged, and an old FE keeps working against a new backend throughout.

| Case | Handling |
|---|---|
| Sync runs mid-reorder | Sync never writes `display_order`; the reorder transaction wins by construction. |
| Playlist disappears from YouTube | Category is disabled as today; it drops from the public list but stays visible (greyed) in the admin list so its order slot and override aren't lost. |
| New playlist appears after a reorder | `display_order` is null → sorts last until an admin places it. |
| Default category gets disabled | `applyDefaultFallback` already covers the "no default in list" case; keep it as the safety net. |
| Reorder with a stale id list | 400 with the standard error shape; FE refetches and retries. |
| Two admins set different defaults | Last write wins; the partial unique index makes a torn state impossible. |

**Deliberately out of scope:** creating or deleting categories by hand (they
mirror playlists — manage those on YouTube), hiding/showing categories
independently of playlist existence, and per-category cover overrides. All
three fit the same override pattern later if wanted.
