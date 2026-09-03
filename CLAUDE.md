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
only exposes the `/podcast` and `/admin/podcast` endpoints — but the underlying `Post` aggregate and use
cases are unchanged and not tied to "podcast" specifically, so re-adding a differently-tagged endpoint group
over the same use cases is straightforward if needed.

## Build & run

- `mvn package` — compiles (this runs the openapi-generator step first) and runs tests.
- `mvn spring-boot:run` — needs a reachable Postgres (`SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD`, default
  `jdbc:postgresql://localhost:5432/skateboard` / `postgres`/`postgres`) and applies
  `src/main/resources/db/migration/V1__posts.sql` via Flyway on startup, against the `skateboard-podcast`
  schema (`spring.flyway.schemas`, auto-created via `create-schemas: true`; Hibernate validation is pointed
  at the same schema via `spring.jpa.properties.hibernate.default_schema`) inside the `skateboard` database.
- Auth is a bearer JWT issued by Keycloak — needs a reachable Keycloak too (`../skateboard-infrastructure/.docker/docker-compose.yaml`,
  `localhost:8180`, realm `skateboard-podcast`); see "Auth model" below for the issuer/audience config and
  the claims a token needs.

## Architecture

Hexagonal architecture, one aggregate (`Post`):

```
adapter/in/rest    → PodcastController (implements generated PodcastApi) + PodcastService, a @Service
                      caching facade the controller delegates to (not a REST controller despite the name)
application/port/in  → one interface per use case (CreatePostUseCase, GetPostUseCase, GetPostBySlugUseCase,
                        UpdatePostUseCase, DeletePostUseCase, ImportPostsUseCase), each with a nested
                        Input/Result record
application/service  → one @Service per use case, implementing the matching port/in interface
application/port/out → LoadPostPort, SavePostPort — persistence-facing outbound ports
adapter/out/persistence → PostPersistenceAdapter implements both outbound ports over Spring Data JPA
                           (PostJpaEntity, SpringPostRepository); does the domain↔entity mapping
domain/model         → Post (mutable aggregate, private constructor; use Post.create(...) for new posts,
                        Post.reconstitute(...) when rehydrating from persistence), PostStatus enum
                        (DRAFT, SCHEDULED, PUBLISHED)
domain/exception      → PostNotFoundException
```

Key conventions:
- `Post.blocksJson` / `socialMediaLinksJson` are stored as raw JSON strings on the domain model and entity
  (`text` columns); serialization to/from the generated DTO shapes happens in the REST layer
  (`PodcastController`/`PodcastService`) via a shared `ObjectMapper`, not in the domain or persistence layer.
- Slug generation (`title.toLowerCase().replaceAll("[^a-z0-9]+", "-")...`) is duplicated in `PodcastService`
  and `ImportPostsService` — keep them in sync if you change the slugging rule.
  `CreatePostService.ensureUniqueSlug` appends `-1`, `-2`, ... on collision.
- `PodcastService` (not the controller) owns Spring Cache annotations: `POST_CACHE` ("podcast-post") is keyed
  by `page:size` for feed reads and by `slug` for single-post reads, `sync = true` on the feed read to
  collapse concurrent misses, and `unless = "#result == null"` on the slug lookup so 404s aren't cached. Every
  mutation (create/update/delete/import) does `@CacheEvict(allEntries = true)` — update can change the slug,
  so key-targeted eviction isn't safe.
- The feed `@Query`s (`SpringPostRepository`, `SpringPostCategoryRepository`) order by
  `publishAt DESC NULLS LAST, id` — episodes sort by their real publish date; `createdAt` is the bulk-import
  timestamp and is deliberately not a fallback. The YouTube sync sources `publishAt` from each item's
  `contentDetails.videoPublishedAt` (the video's actual publication time), not the playlistItem's
  `snippet.publishedAt` (which is only when the video was added to the playlist), falling back to the latter
  only when `videoPublishedAt` is absent. `UpdatePostService` keeps a post's `publishAt` when the
  update request omits it, so an edit can't null the date and reshuffle the feed.

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

## Tests

JUnit 5 + Mockito (+ AssertJ). Two test classes, both under
`src/test/java/com/skateboard/podcast/adapter/in/rest/`:
- `PodcastServiceTest` — plain Mockito unit tests of `PodcastService`'s DTO mapping and slug/status defaulting.
- `PodcastServiceCachingTest` — Spring context test (`ConcurrentMapCacheManager`) that verifies the
  `@Cacheable`/`@CacheEvict` semantics described above; explicitly not testing Redis serialization.

Run with `mvn test` — no external services needed for the current test suite (both classes stub the ports/use
cases directly).
