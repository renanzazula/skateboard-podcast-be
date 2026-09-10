---
name: add-use-case
description: >-
  Step-by-step workflow for adding or changing a feature in this hexagonal
  Spring Boot service — a new REST endpoint, a new use case over the Post or
  Category aggregate, a new outbound port, or a domain-model change. Use when
  the task is "add an endpoint", "add a use case", "expose X over the API",
  "add a field to Post/Category", "wire a new adapter", or any change that
  crosses the ports-and-adapters layers. Encodes the layer order, the
  OpenAPI-driven build, the cache-eviction rules, the triplicated slug logic,
  the publish-notification gates, and the test conventions so a change lands
  consistent with the rest of the codebase.
---

# Adding a use case / endpoint

This service is hexagonal (ports & adapters). `api/openapi.yaml` drives the
build: `openapi-generator-maven-plugin` regenerates `PodcastApi` and every
request/response DTO from it on every `mvn` run, into
`com.skateboard.infrastructure.web.api` / `com.skateboard.application.dto`.
**You never hand-write a controller interface or a DTO** — edit the spec and
rebuild.

Read `CLAUDE.md` first if you haven't — it is the architecture reference. This
skill is the *procedure*; CLAUDE.md is the *why*.

## Layer map (dependency direction points inward)

```
adapter/in/rest       PodcastController implements generated PodcastApi;
                      delegates to PodcastService (@Service caching facade,
                      owns all DTO<->domain mapping + Spring Cache annotations)
adapter/in/scheduler  @Scheduled + @SchedulerLock jobs; call a use case only
application/port/in    one interface per use case, with nested Input/Result records
application/service    one @Service per port/in interface (+ unexposed helpers)
application/port/out   persistence / outbound-integration interfaces
adapter/out/*          persistence (Spring Data JPA), youtube, spotify, messaging
domain/model           Post, Category (mutable aggregates; create/reconstitute pattern)
domain/exception       PostNotFoundException, CategoryNotFoundException
```

## Procedure

### 1. Decide the shape before touching code
- Is there already a use case that does this? (`application/port/in/` — there
  are ~15.) Extending an existing service is usually right; a new verb over an
  aggregate is a new use case.
- Read-only or a mutation? Mutations must evict `POST_CACHE` (step 5).
- Does it need a new outbound dependency (DB query, external API)? That's a new
  or extended `port/out` + adapter.

### 2. Edit `api/openapi.yaml` (only if there's an HTTP surface)
- Add the path/operation under `paths:`. Give it an `operationId` (becomes the
  `PodcastApi` method name), `tags: [podcast]`, `security: - bearerAuth: []`,
  and `x-required-permissions: [FUNC_...]`.
- Add or reuse schemas under `components/schemas/`. Response envelopes are
  POJOs (`FeedPageResponse`, `PostResponse`, `CategoryResponse`,
  `AdminCategoryResponse`) — **do not return a bare `array` at the top level of
  a cached read**; the Redis serializer (`CacheConfig`) can't round-trip a
  root-level `List` (see `PodcastService.getCategories` comment). Wrap it, or
  leave the endpoint uncached.
- Include an `ErrorResponse` content body on `400`/`401`/`404` for consistency.
- Regenerate: `./mvnw generate-sources` (or just build). Generated code lands
  under `target/generated-sources/openapi/` — never edit or commit it.

### 3. Add the port/in interface
`application/port/in/<Verb><Aggregate>UseCase.java`:
```java
public interface DoThingUseCase {
    record Input(UUID id, String someField) {}   // nested; records
    record Result(...) {}                          // nested; omit if returning a domain object
    Result execute(Input input);
}
```
Match the existing naming: `Create/Get/GetById/GetBySlug/Update/DeletePostUseCase`,
`GetCategoriesUseCase`, etc. Some return the domain object directly
(`UpdateCategoryUseCase` returns `Category`); some return a nested `Result`.

### 4. Add the application service
`application/service/<Verb><Aggregate>Service.java`, `@Service`, constructor
injection, `implements <Verb><Aggregate>UseCase`:
```java
@Service
public class DoThingService implements DoThingUseCase {
    private final SomePort somePort;
    public DoThingService(SomePort somePort) { this.somePort = somePort; }

    @Override
    public Result execute(Input input) {
        var entity = somePort.findById(input.id())
            .orElseThrow(() -> new PostNotFoundException(input.id().toString()));
        // mutate the aggregate via its own methods, then save
        return somePort.save(entity);
    }
}
```
- Throw `PostNotFoundException` / `CategoryNotFoundException` for 404,
  `IllegalArgumentException` for 400. **Never** build error `ResponseEntity`
  bodies here — `GlobalExceptionHandler` maps exceptions to `ErrorResponse`.
- Domain state changes go through aggregate methods (`post.attachPlatformLink`,
  `category.rename`, `category.markDefault`), not setters. New posts:
  `Post.create(...)`; rehydration in the persistence adapter: `Post.reconstitute(...)`.

### 5. Wire the REST layer (if step 2 applied)
- **`PodcastService`** (the facade, `adapter/in/rest/`): inject the new use
  case, add a method that maps DTO -> `Input`, calls `execute`, maps the result
  -> generated DTO with the existing `toDto` / `toCategoryDto` / `toAdminCategoryDto`
  helpers. Add helpers if a new DTO shape appears.
  - **Cached read**: `@Cacheable(cacheNames = POST_CACHE, key = "...", sync = true)`.
    Use `unless = "#result == null"` for single-item lookups so 404s aren't
    cached. Key conventions: `page:size` for feeds, `slug` / `'id:' + #id` for
    single posts, `'category:' + #slug + ':' + #page + ':' + #size` for category feeds.
  - **Mutation**: `@Caching(evict = {@CacheEvict(cacheNames = POST_CACHE, allEntries = true)})`.
    Always `allEntries = true` — an update can change a slug, so targeted
    eviction is unsafe. Exception: the YouTube sync path evicts itself
    conditionally inside `SynchronizeYoutubeChannelService`; don't double-evict.
  - Category admin reads (`getCategories`, `getAdminCategories`) are
    **deliberately uncached** — keep new category-admin methods uncached too.
- **`PodcastController`**: implement the generated method, `@PreAuthorize("hasAuthority('FUNC_...')")`
  matching the spec's `x-required-permissions`, delegate to `PodcastService`,
  set the HTTP status. For a service method that returns `null` on "not found",
  throw `new ResponseStatusException(HttpStatus.NOT_FOUND, "...")` (the only
  place hand-rolled error responses are acceptable). Current-user id:
  `resolveCurrentUserId()` (JWT `sub`).

### 6. Outbound side (if a new dependency)
- New method on an existing `port/out` interface, or a new interface in
  `application/port/out/`.
- Implement in the matching `adapter/out/` package. Persistence: add the query
  to the `Spring*Repository`, do domain<->entity mapping in the `*PersistenceAdapter`
  (never leak JPA entities past the adapter). Feed queries order by
  `publishAt DESC NULLS LAST, id` — not `createdAt`.

### 7. Domain-model / schema change
- Add a Flyway migration `src/main/resources/db/migration/V<n+1>__<desc>.sql`
  (next number after `V7`). Never edit an applied migration.
- Update the JPA entity, the `reconstitute(...)` signature + all callers, the
  domain aggregate, and the DTO mapping. `blocksJson` / `socialMediaLinksJson`
  stay raw JSON strings end to end — only `PodcastService` parses them.
- If you touch slug generation, update **all three** copies:
  `PodcastService.generateSlug`, `ImportPostsService`, `SynchronizeYoutubeChannelService`
  (CLAUDE.md "Key conventions").

### 8. Publish-notification gates (only if a code path can move a post to PUBLISHED)
Route it through `PodcastPublicationNotifier` (called from `CreatePostService`
and `UpdatePostService` on a genuine non-PUBLISHED -> PUBLISHED transition). Do
not re-implement the gates (`podcast.notifications.enabled`, `posts.notified_at`,
`max-age-hours`). The event id is derived from the post id — keep it deterministic.

### 9. Tests (required)
- **Service unit test** — `src/test/java/.../application/service/<Name>Test.java`,
  plain JUnit 5 + Mockito + AssertJ, `MockitoAnnotations.openMocks(this)` in
  `@BeforeEach`, stub the `port/out` mocks directly. Cover: happy path, the
  not-found throw, and any branch logic. Mirror `UpdateCategoryServiceTest`.
- **Cache behavior** — if you added/changed `@Cacheable`/`@CacheEvict`, add or
  extend a `*CachingTest` (Spring context, `ConcurrentMapCacheManager` — not
  Redis).
- **Persistence** — a new query/mapping gets a Testcontainers test against real
  Postgres (needs Docker), like `PostPlatformLinkPersistenceIntegrationTest`.
- **External HTTP client** — use OkHttp `MockWebServer` over loopback, never
  mock `WebClient` internals (`YoutubeClientTest` convention).

### 10. Build
`mvn package` (regenerates from the spec, compiles, runs tests). **Docker must
be running** or the Testcontainers classes fail. Pure-unit / H2 / cache tests
don't need Docker.

## Quick checklist
- [ ] `api/openapi.yaml` updated, `operationId` + `x-required-permissions` set
- [ ] `port/in` interface with nested `Input`/`Result` records
- [ ] `@Service` implementing it; throws domain exceptions, no `ResponseEntity` errors
- [ ] `PodcastService` facade method + DTO mapping + correct cache annotation
- [ ] `PodcastController` method with matching `@PreAuthorize`
- [ ] `port/out` + adapter changes if new dependency; JPA entities not leaked
- [ ] Flyway `V<n+1>__*.sql` for any schema change; all 3 slug copies in sync
- [ ] PUBLISHED transitions go through `PodcastPublicationNotifier`
- [ ] Service unit test + cache/persistence test as applicable
- [ ] `mvn package` green (Docker up)
