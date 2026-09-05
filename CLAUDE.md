# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

A standalone Spring Boot service (`podcast`, package `com.skateboard.podcast`) implementing posts/podcast-feed
functionality in hexagonal (ports & adapters) style. It started as a repackaged extraction of the `posts`
module from the sibling `standard-base` monorepo (`com.standardbase.posts`) — same design, same class names —
and was made independently buildable/runnable by generating its own copy of the REST DTOs/API interfaces and
adding the framework wiring (security, caching, migrations, exception handling) that used to come from
standard-base's `application`/`identity` modules.

`api/openapi.yaml` is the **canonical, build-driving spec** for this service. `pom.xml`'s
`openapi-generator-maven-plugin` generates `PodcastApi` and all request/response DTOs from it at
build time, into the same package names the original extraction used
(`com.standardbase.infrastructure.web.api`, `com.standardbase.application.dto`) so the hand-written
adapter/application/domain code didn't need touching. Edit this file to change the API.

The spec originally also had a parallel `/api/posts` / `/api/admin/posts` ("posts/feed") endpoint group,
generating a `PostsApi` implemented by a `PostController`. That group has been removed — the service now
only exposes the `/podcast`, `/admin/podcast` and `/categories`/`/admin/categories` endpoints — but the
underlying `Post` aggregate and use cases are unchanged and not tied to "podcast" specifically, so re-adding
a differently-tagged endpoint group over the same use cases is straightforward if needed.

## Build & run

- `mvn package` — compiles (this runs the openapi-generator step first) and runs tests. Some tests
  (`PodcastImportIntegrationTest`, `PostPlatformLinkPersistenceIntegrationTest`) use Testcontainers against a
  real Postgres, so **Docker must be running** for `mvn test`/`mvn package` to pass locally; the rest (plain
  Mockito unit tests, H2-backed and `ConcurrentMapCacheManager`-backed Spring-context tests) don't need it.
- `mvn spring-boot:run` — needs a reachable Postgres (`SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD`, default
  `jdbc:postgresql://localhost:5432/skateboard` / `postgres`/`postgres`) and applies
  `src/main/resources/db/migration/V1..V7__*.sql` via Flyway on startup, against the `skateboard-podcast`
  schema (`spring.flyway.schemas`, auto-created via `create-schemas: true`; Hibernate validation is pointed
  at the same schema via `spring.jpa.properties.hibernate.default_schema`) inside the `skateboard` database.
  This is the **default profile** and assumes a single instance with no Redis (`spring.cache.type: simple`,
  in-memory `ConcurrentMapCacheManager`).
- `application-railway.yml` (`SPRING_PROFILES_ACTIVE=railway`, the production/Railway deploy) switches
  `spring.cache.type` to `redis` (needs `REDIS_URL`) and uses schema name `skateboard_podcast` (underscore,
  vs. the default profile's `skateboard-podcast` with a hyphen) — don't assume the two profiles share a
  literal schema string. See "Caching" below for why Redis needs its own `CacheConfig` bean.
- Deployed to Railway via `railpack.json`: `./mvnw clean package -DskipTests` then, if `NEW_RELIC_ENABLED=true`,
  runs with `-javaagent:newrelic/newrelic.jar` (agent downloaded at build time, config from
  `newrelic-config/newrelic.yml`). Not wired into local `mvn spring-boot:run`.
- Auth is a bearer JWT issued by Keycloak — needs a reachable Keycloak too (`../skateboard-infrastructure/.docker/docker-compose.yaml`,
  `localhost:8180`, realm `skateboard-podcast`); see "Auth model" below for the issuer/audience config and
  the claims a token needs.

## Architecture

Hexagonal architecture, two aggregates (`Post`, `Category`) plus scheduled sync jobs and an outbound
messaging adapter:

```
adapter/in/rest       → PodcastController (implements generated PodcastApi) + PodcastService, a @Service
                         caching facade the controller delegates to (not a REST controller despite the name)
adapter/in/scheduler  → YoutubeSyncJob, PendingPodcastNotificationJob — @Scheduled + @SchedulerLock (ShedLock),
                         trigger a use case only, no HTTP/persistence logic of their own
application/port/in   → one interface per use case: Create/Get/GetById/GetBySlug/Update/DeletePostUseCase,
                         ImportPostsUseCase, SynchronizeYoutubeChannelUseCase for posts; GetCategoriesUseCase,
                         GetAdminCategoriesUseCase, GetPostsByCategoryUseCase, UpdateCategoryUseCase,
                         ReorderCategoriesUseCase, SetDefaultCategoryUseCase for categories — each with a
                         nested Input/Result record
application/service   → one @Service per use case, implementing the matching port/in interface, plus
                         MatchSpotifyEpisodeService (not exposed as a use case; called only from the YouTube
                         sync) and small stateless helpers: EpisodeNumberParser, YoutubeDescriptionParser,
                         TitleNormalizer
application/port/out  → LoadPostPort/SavePostPort (posts), CategoryRepositoryPort/PostCategoryPort
                         (categories and their post associations), YoutubeContentPort, SpotifyContentPort,
                         PublishDomainEventPort — all persistence/outbound-integration-facing
adapter/out/persistence → PostPersistenceAdapter, CategoryPersistenceAdapter over Spring Data JPA
                           (PostJpaEntity + PostPlatformLinkJpaEntity, CategoryJpaEntity, PostCategoryJpaEntity
                           keyed by embeddable PostCategoryId); each adapter does the domain↔entity mapping
adapter/out/youtube   → YoutubeClient (WebClient) implements YoutubeContentPort against the YouTube Data API v3
adapter/out/spotify   → SpotifyApiClient/SpotifyTokenClient implement SpotifyContentPort (client-credentials OAuth)
adapter/out/messaging → RabbitDomainEventPublisher implements PublishDomainEventPort over Spring AMQP
domain/model          → Post (mutable aggregate, private constructor; use Post.create(...) for new posts,
                         Post.reconstitute(...) when rehydrating), PostStatus (DRAFT, SCHEDULED, PUBLISHED),
                         PostPlatform (YOUTUBE, SPOTIFY), PostPlatformLink (record: at most one per platform
                         per post); Category (mutable aggregate, same create/reconstitute pattern)
domain/exception      → PostNotFoundException, CategoryNotFoundException
```

## Content-block model

`Post.blocksJson` / `socialMediaLinksJson` are stored as raw JSON strings on the domain model and entity
(`text` columns) — the domain and persistence layers never parse them. Serialization to/from the generated
DTO shapes happens only in the REST layer (`PodcastController`/`PodcastService`) via a shared `ObjectMapper`.
This is the same content-block shape `skateboard-app-config-be` reuses for the "About Us" feature and that
`skateboard-fe`'s post editor (reorderable blocks, admin JSON import) authors against — this service does not
itself define/validate individual block types, it just stores and returns whatever JSON array it's given.

Key conventions:
- Slug generation (`title.toLowerCase().replaceAll("[^a-z0-9]+", "-")...`) is duplicated in `PodcastService`,
  `ImportPostsService` and `SynchronizeYoutubeChannelService` — keep all three in sync if you change the
  slugging rule. `CreatePostService.ensureUniqueSlug` appends `-1`, `-2`, ... on collision;
  `SynchronizeYoutubeChannelService.ensureUniqueCategorySlug` does the same for category slugs.
- `PodcastService` (not the controller) owns Spring Cache annotations: `POST_CACHE` ("podcast-post") is keyed
  by `page:size` for feed reads, by `slug`/`id` for single-post reads, and by `category:{slug}:{page}:{size}`
  for category feeds; `sync = true` on feed reads to collapse concurrent misses, and
  `unless = "#result == null"` on single-post lookups so 404s aren't cached. Every post mutation
  (create/update/delete/import/YouTube sync/Spotify match) does `@CacheEvict(allEntries = true)` — update can
  change the slug, so key-targeted eviction isn't safe. `getCategories()`/admin category reads are
  deliberately **uncached** (see the category doc below for why).
- The feed `@Query`s (`SpringPostRepository`, `SpringPostCategoryRepository`) order by
  `publishAt DESC NULLS LAST, id` — episodes sort by their real publish date; `createdAt` is the bulk-import
  timestamp and is deliberately not a fallback. The YouTube sync sources `publishAt` from each item's
  `contentDetails.videoPublishedAt` (the video's actual publication time), not the playlistItem's
  `snippet.publishedAt` (which is only when the video was added to the playlist), falling back to the latter
  only when `videoPublishedAt` is absent. `UpdatePostService` keeps a post's `publishAt` when the
  update request omits it, so an edit can't null the date and reshuffle the feed.
- `Post.attachPlatformLink` replaces any existing link for the same `PostPlatform` — at most one YouTube and
  one Spotify link per post.

## Caching: in-memory locally, Redis in production

`spring.cache.type` is `simple` (in-memory `ConcurrentMapCacheManager`) in the default profile and `redis` in
`application-railway.yml`. `infrastructure/cache/CacheConfig` supplies a custom `RedisCacheConfiguration`
bean **only used when the Redis cache manager is active** — the generated response DTOs aren't `Serializable`,
so Spring's default JDK-serialization Redis config would throw on every write, and without activating Jackson
default-typing on a *copy* of the shared `ObjectMapper`, a type-erased `@Cacheable` read comes back as a raw
`LinkedHashMap` and fails a `ClassCastException` casting to `FeedPageResponse`/`PostResponse`. Redis is also
used independently for **ShedLock** distributed scheduler locks (`YoutubeSchedulerLockConfig`,
`@ConditionalOnProperty(spring.data.redis.url)`) — that bean only activates where Redis is configured, so
`@SchedulerLock` on `YoutubeSyncJob` is a harmless no-op on a single local instance. See
`.docs/CHANGE_REQUEST_REDIS_CACHE_PODCAST.md` for the migration history.

## YouTube sync & Spotify enrichment

`SynchronizeYoutubeChannelService` (`YoutubeSyncJob`, cron `youtube.sync.cron`, default every 10 min, gated by
`youtube.sync.enabled`) does three things every pass, and a failure in any one must not block the others
(each phase is wrapped in its own try/catch and logs `status=...FAILED`/`SKIPPED` rather than throwing):
1. Mirrors every public playlist on the configured channel as a `Category` (`source=YOUTUBE`,
   `externalId=playlistId`) and diffs each playlist's video membership against `post_category` rows
   (`PostCategoryPort`), adding/removing associations; disables categories whose playlist disappeared.
2. Still runs the pre-existing bounded incremental "uploads catch-all" poll for channel videos not (yet) in
   any playlist, created uncategorized.
3. If `spotify.sync.enabled`, runs `MatchSpotifyEpisodeService` to link existing YouTube-sourced posts to
   their Spotify episode by a weighted score (episode number 50, normalized title 30, publish date within 2
   days 15, duration within 30s 5; threshold 70) — it never creates posts, only attaches a
   `PostPlatformLink(SPOTIFY, ...)` to the best-scoring unclaimed match. Full design rationale in
   `.docs/README_SPOTIFY_YOUTUBE_PODCAST_INTEGRATION.md`; description-field stripping in
   `.docs/README_YOUTUBE_DESCRIPTION_FILTERING.md`; playlist→category migration history in
   `.docs/README_YOUTUBE_PLAYLIST_CATEGORIES_MIGRATION.md`.

`YoutubeDescriptionParser` strips known boilerplate/links out of a video's description before it's stored as
`Post.description`, and separately extracts `socialMediaLinksJson`. `EpisodeNumberParser` parses an episode
number out of a title (used both for `Post.episodeNumber` and Spotify score matching).

## Category admin management

Categories mirror YouTube playlists and are otherwise read-only/sync-owned, but `V4__category_admin.sql`
added an admin override layer described in full in `README_CATEGORY_MANAGEMENT_PLAN.md` (implemented, not
just a plan — `Category.customName`/`defaultLocked`, the four `/admin/categories*` endpoints, and their
`FUNC_PODCAST_MANAGE_CATEGORIES` permission all exist in code today):
- **Rename is an override, not an edit**: `customName` is nullable; `getEffectiveName()` returns
  `customName != null ? customName : name`. The sync keeps refreshing `name` from YouTube every cycle without
  ever touching `customName`.
- **Default becomes admin-owned the moment it's set**: `Category.markDefault()`/`clearDefault()` set
  `defaultLocked = true`; once *any* category is locked, `Category.updateFromYoutube(...)` stops applying its
  `isDefault` argument, and `SynchronizeYoutubeChannelService` won't even flag a *new* category as default via
  `youtube.default-playlist-id` once the fleet has an admin-owned default (`defaultAdminOwned` check in
  `upsertCategory`).
- **Reorder is a full permutation**: `ReorderCategoriesService` writes `display_order = 0..n-1` for the whole
  submitted id list in one transaction; the read query is
  `ORDER BY display_order ASC NULLS LAST, is_default DESC, created_at ASC`, so a newly-synced category with a
  null `display_order` just appends to the end.
- **Slugs never change** — admin actions never touch `Category.slug`, so FE deep links/cache keys are stable.

## Publishing a podcast notifies subscribers

`skateboard-notification-be` owns push notifications; this service only states that a podcast was
published. It emits `PODCAST_PUBLISHED` to the shared `application.events` topic exchange with routing
key `podcast.published.v1`, and knows nothing about devices, preferences, Expo or retries.

`PodcastPublicationNotifier` is the single decision point, called from `CreatePostService` (which every
create path funnels through — manual authoring, JSON import and the YouTube sync) and from
`UpdatePostService` on a genuine non-PUBLISHED → PUBLISHED transition. Three gates, each guarding a
specific failure:

- **`podcast.notifications.enabled`** — false by default, so a deployment is silent until someone turns
  it on deliberately.
- **`posts.notified_at`** — set only after the broker *confirmed* the event. An edit of a published post,
  a re-sync or a replayed job cannot notify twice.
- **`podcast.notifications.max-age-hours`** (48) — the back-catalogue guard. `SynchronizeYoutubeChannelService`
  hard-codes `PostStatus.PUBLISHED` and uses each video's *real* publication date, so without a recency
  window the first sync against an established channel would push the entire archive. `V7` backfills
  existing rows to `now()` for the same reason.

**The event id is derived from the post id** (`UUID.nameUUIDFromBytes("PODCAST_PUBLISHED:" + id)`), not
random. Two things can emit for one post — the inline call and `PendingPodcastNotificationJob` — and the
stable id is what lets the consumer's idempotency ledger collapse them. Do not make it random.

`PendingPodcastNotificationJob` (`@Scheduled` + `@SchedulerLock`, like `YoutubeSyncJob`) is the
transactional outbox without an outbox table: a post saved but never announced *is* a row with
`notified_at IS NULL`, so recovery is a query. It runs every five minutes and is bounded to 20 posts a
pass.

Publisher confirms (`spring.rabbitmq.publisher-confirm-type: simple`) are required for that to mean
anything — without them a send the broker never accepted looks successful and the post is marked
notified for an event nobody received. A confirm still only says the broker took the message, not that a
queue was bound to receive it.

## Auth model

`infrastructure/security/SecurityConfig` is an OAuth2 resource server validating access tokens issued by the
Keycloak realm in `../skateboard-infrastructure/.docker/keycloak/realm-export.json` (`skateboard-podcast`, run via `../skateboard-infrastructure/.docker/docker-compose.yaml`
on `localhost:8180`). The intended end-to-end flow is: FE authenticates against Keycloak directly (Authorization
Code + PKCE, `skateboard-podcast-fe` client) and obtains a user JWT, which a separate UI Backend/BFF service
relays as a Bearer token to this API (token relay — the BFF does not have its own Keycloak client here).

- `spring.security.oauth2.resourceserver.jwt.issuer-uri` (env `APP_SECURITY_OAUTH2_ISSUER_URI`, default
  `http://localhost:8180/realms/skateboard-podcast`) drives OIDC discovery of the JWKS endpoint at startup —
  Keycloak must be reachable when this app boots, same as Postgres.
- `AudienceValidator` rejects tokens whose `aud` doesn't include `app.security.oauth2.audience` (env
  `APP_SECURITY_OAUTH2_AUDIENCE`, default `skateboard-podcast-be`) — populated on the FE client's tokens via
  its `audience` protocol mapper in `realm-export.json`, so tokens minted for other clients in the realm are
  rejected here even if otherwise valid.
- Authorities are read verbatim (no `ROLE_`/`SCOPE_` prefix) from the token's `authorities` claim (array of
  strings), so `@PreAuthorize("hasAuthority('FUNC_...')")` on the controllers works against permission strings
  straight out of the token — matching the `x-required-permissions` values in `api/openapi.yaml`. That claim
  is populated by the FE client's `authorities` protocol mapper (`oidc-usermodel-realm-role-mapper`), which
  flattens the user's effective realm roles — composites resolved, so e.g. `ADMIN`/`STANDARD` expand to their
  `FUNC_*` roles — directly into a flat `authorities` array rather than the default nested `realm_access.roles`.
- The current user's id is read out of `SecurityContextHolder.getContext().getAuthentication().getName()` —
  i.e. the JWT's `sub` claim, which Keycloak populates with the user's UUID — as `resolveCurrentUserId()` in
  `PodcastController`; returns `null` if that fails rather than throwing.

## Errors

`infrastructure/web/GlobalExceptionHandler` (`@RestControllerAdvice`) maps every exception to the generated
`ErrorResponse` shape (`status`, `error`, `message`, `timestamp`) — `AccessDeniedException` (a `@PreAuthorize`
denial) → 403, `PostNotFoundException`/`CategoryNotFoundException` → 404, `IllegalArgumentException`/
`MethodArgumentNotValidException`/`MethodArgumentTypeMismatchException` → 400, everything else → 500 (logged,
message not leaked). Domain/application code should throw one of the above rather than writing
`ResponseEntity` error bodies by hand — `PodcastController` only does that itself for the two "not found"
cases that come back as `null` from a service method instead of an exception.

## Tests

JUnit 5 + Mockito (+ AssertJ), plus Testcontainers, H2 and OkHttp `MockWebServer` for the tests that need a
real dependency instead of a mock. Well beyond the two original unit tests — current suite includes:
- Plain Mockito unit tests per service (`PodcastServiceTest`, `UpdateCategoryServiceTest`,
  `ReorderCategoriesServiceTest`, `SetDefaultCategoryServiceTest`, `GetCategoriesServiceTest`,
  `GetPostsByCategoryServiceTest`, `SynchronizeYoutubeChannelServiceTest`, `MatchSpotifyEpisodeServiceTest`,
  `UpdatePostServiceTest`, `YoutubeDescriptionParserTest`, `PodcastPublicationNotifierTest`).
- Spring-context cache tests against `ConcurrentMapCacheManager` (`PodcastServiceCachingTest`,
  `SynchronizeYoutubeChannelServiceCachingTest`, `CacheConfigTest`) — verify `@Cacheable`/`@CacheEvict`
  semantics, explicitly not Redis serialization.
- Testcontainers integration tests against a **real Postgres** (`PodcastImportIntegrationTest`,
  `PostPlatformLinkPersistenceIntegrationTest`) — full end-to-end through persistence.
- `YoutubeClientTest`/`SpotifyApiClientTest`/`SpotifyTokenClientTest` run a local `MockWebServer` over loopback
  rather than mocking `WebClient`'s reactive internals, per the project convention noted in `pom.xml`.

Run with `mvn test`. **Docker must be running** for the Testcontainers-based classes to pass; everything else
needs no external services (ports/use cases stubbed directly).
