# Content search execution strategy

For the opt-in English whole-word phrase path, v4 cursors and manual schema/backfill
steps, see [English word search](content-english-word-search.md). The flag defaults
to false; historical bigram measurements below are not word-search benchmarks.

For the Korean explanation of bookmark ordering, observed bottlenecks and the
limits of each experiment, see [북마크순 정렬과 성능 병목 분석](bookmark-order-search-performance.md).

## Contract

`GET /api/v1/contents/search` keeps the response, single-genre compatibility,
ranking and popularity version-1 cursor format. A provided keyword is normalized
with NFKC, lowercase and removal of non-letter/non-number characters. One Unicode
codepoint uses opt-in LIKE containment on the existing normalized titles, with whole-title
matches first and then content ID descending. Its relevance is zero. When the flag is off,
indexed whole-title equality remains. See [Single-character search](content-single-character-search.md)
for v5 cursors and the 50-result legacy cap. No character-index migration is required. Two or more
codepoints keep the existing query path. Missing, empty and
whitespace-only keywords select the popularity path. Symbol-only keywords return 400.
The error detail is `keyword는 문자 또는 숫자를 1자 이상 포함해야 합니다.`.

The localized-search option is not enabled by this change. Its false mode keeps
`LOWER(title)` equality and `title` FULLTEXT; true mode keeps normalized Korean/
English equality and `search_title` FULLTEXT. True mode uses required two-codepoint
ngrams and verifies a consecutive substring in either normalized title. Repeated
ngrams are not deduplicated: their TF-IDF contributions preserve the original
Natural Language score on matching documents. The FULLTEXT branch projects the
score once; an ID aggregation takes its maximum alongside zero-score exact hits. Display
titles remain unchanged, but scores may change when the search document changes.
True mode keyword cursors are v2 and reject old v1 keyword cursors; false mode
keyword cursors and all popularity cursors remain v1. Switch flags consistently
across API instances and restart keyword pagination after switching.

The legacy `/api/v1/search/contents` caps one-codepoint results at 50; two or more
codepoints still reuse the native keyword candidates without a result cap.
Its blank-query default remains 30 popular contents. Bookmarked
search keeps QueryDSL, its user restriction and descending bookmark-ID cursor,
using the registered FULLTEXT boolean function plus normalized exact matching.
It does not first materialize the entire catalog candidate set.

## Query paths

- No keyword/genre: existing QueryDSL popularity query.
- Genre without keyword: ordered content index scan, then an indexed membership
  lookup with `STRAIGHT_JOIN` and `uk_content_genre`. Media-specific requests use
  `idx_content_media_popular`; other requests use `idx_content_popular`. The
  existing popularity cursor restricts the scan before `LIMIT size + 1`.
- One-codepoint keyword: opt-in exact-title and LIKE reads apply genre/media/cursor
  filters before LIMIT and hydrate only the final page. v5 cursors separate this path
  from old equality-only pages. The partial branch scans the existing content-ID index
  in descending order; LIKE itself cannot narrow a B-tree range. Rare or absent matches
  can still scan most documents. QueryDSL bookmark search uses normalized-title contains
  while keeping owner/newest-bookmark ordering. No character table, trigger or backfill
  is required. The flag defaults off; off mode remains indexed whole-title equality.
- Keyword with two or more codepoints: independent indexed exact-match and FULLTEXT ID queries are combined
  with `UNION`, then joined to content and, when selected, the unique genre
  relation via `STRAIGHT_JOIN`. This prevents semijoin reordering from scanning
  all genre members before the small keyword candidate set. Media/cursor filters
  precede final ranking and `LIMIT size + 1`. Exact match ranks first, FULLTEXT TF-IDF
  score descends next, and descending content ID breaks ties. Candidate branches
  are not prematurely limited. IDs matching multiple branches appear once.

`STRAIGHT_JOIN` deliberately preserves content-first access for popularity and
candidate-first access for keyword search. Binding, projection and mapping use
the existing JPA transaction; no separate connection or in-memory ranking is used.

## Manual deployment sequence

Apply the genre/search-document maintenance procedure below before enabling the
localized flag. The index-only optimization measurements below predate normalized
phrase queries, stopword changes and genre migration and are not current benchmarks.

1. Recheck the actual MySQL version/schema and index definitions using the
   preflight in `content-search-performance-ddl.sql`. Tests target MySQL 8.4.8.
   The title/normalized columns must be `VARCHAR(255)` using `utf8mb4`, and
   existing collations are preserved. Stop and review a mismatch rather than
   truncating expressions or changing collation. Confirm ngram token size is 2,
   FULLTEXT uses the ngram parser, indexes are visible, and `uk_content_genre`
   uniquely covers `(content_id, genre_id)`.
2. Check storage capacity and active/long-running transactions. Create only the
   missing indexes, one statement at a time. The functional `LOWER(title)` index
   uses `ALGORITHM=INPLACE LOCK=SHARED`: MySQL 8.4.8 rejects `LOCK=NONE` when its
   hidden virtual column and index are added together. Reads remain available,
   but writes are blocked during construction; use an approved maintenance
   window, not a live-traffic online rollout. The normalized-title indexes use
   `ALGORITHM=INPLACE LOCK=NONE`; do not remove these safeguards after a failure.
   Online DDL still needs short metadata locks and uses CPU/I/O/storage. Missing
   FULLTEXT indexes require a separate planned maintenance window because their
   creation can rebuild the table and block writes. Do not run the whole DDL file
   blindly against an already indexed database.
3. Verify names, key order/expressions, collation and parser again. All new
   equality indexes must exist before deploying this API because SQL names them
   explicitly. No application startup creates indexes, backfills titles, or
   silently falls back to a full scan. Coordinate the minimum-keyword contract
   with clients before deploying; frontend changes are outside this task.
4. Deploy the API separately. In staging, compare ordered IDs, rank/score values,
   cursor pages and empty results with the prior query. Benchmark frequent/rare
   genres, media combinations, first/deep/last pages, keyword-only and combined
   filters. Use bounded sequential `EXPLAIN ANALYZE` (it executes the query) plus
   API timing; do not load-test production without separate authorization.
5. Confirm popularity uses an ordered content index with indexed genre lookups,
   and keyword candidates use equality/FULLTEXT indexes instead of a whole
   content scan. Observe latency, rows examined, CPU and I/O under real traffic.
   Current data differs from the earlier measurement, so prior timings are not
   an SLA or proof of deployed improvement.

## Known limits and recovery

Rare genres, empty results and deep popularity cursors may still scan many
ordered index entries, and forcing this path can be slower than genre-first
access for these requests. Validate these cases against actual staging data
before deployment; frequent-genre gains do not prove an improvement for every
filter. Broad keywords may produce many FULLTEXT candidates and
need a candidate sort. This does not introduce cache, denormalized genre rankings
or a hard scan cap that would change result completeness.

For an index-only rollout, API rollback is a separate explicitly reviewed action;
leave additive indexes in place. For the approved combined maintenance below,
validation failure keeps maintenance active: do not automatically restart the old
image. Do not drop named indexes while the new API is running. Data cleanup and
bookmark-count repair are not included.

## Verification

```bash
./gradlew :apps:api:test --tests '*ContentQueryRepository*' --tests '*ContentQueryFacadeTest'
./gradlew :apps:api:bootJar
```

Docker must be reachable for the MySQL tests. Skipped container tests are not
database validation. The repository tests cover both localized-search modes,
deduplication, exact-only matches, all filters and cursor traversal. Performance
verification compares the actual generated SQL on a synthetic local fixture;
timing is reported rather than asserted against a fixed wall-clock threshold.

### Local results (2026-10-09)

- MySQL 8.4.8: 45 related Repository/Service tests passed, with zero skipped
  tests; API `bootJar` succeeded.
- Isolated HTTP server using the real Controller/Facade/repositories: Korean,
  padded Korean and one supplementary Unicode codepoint returned 400; valid,
  empty and blank keywords returned 200. Generated OpenAPI documented the
  minimum keyword length, scalar genre enum and 400 response. Keyword cursor
  traversal returned all 30 fixture matches across six pages, without duplicates.
  This isolated server did not verify the full application's security chain or
  external integrations.
- A temporary 50,000-content fixture had 25,000 drama relations, five rare-genre
  relations, an empty genre and 30 keyword matches. Both search modes matched
  prior ordered IDs, exact-match ranks and unrounded relevance scores for eight
  request shapes each (genre, genre/media, empty, rare, deep, last, keyword,
  keyword/genre/media).

Single sequential `EXPLAIN ANALYZE` samples below are database milliseconds,
not API latency, concurrent-load results or a production SLA. False mode:

| Request | Prior | New | New access |
| --- | ---: | ---: | --- |
| Drama first page | 60.5 | 0.97 | 42 popularity entries, 21 results |
| Drama + TV | 27.3 | 0.05 | 21 media/popularity entries |
| Drama deep page | 22.3 | 0.07 | 42 popularity range entries |
| Keyword | 20.9 | 0.10 | 30 indexed candidate IDs, not 50,000 contents |
| Keyword + drama + TV | 24.6 | 0.10 | 30 candidates, seven genre lookups |
| Empty genre | 0.009 | 56.1 | 50,000 popularity entries, zero results |
| Rare genre (five total) | 0.028 | 51.6 | 50,000 popularity entries, five results |

The rare/empty regression is an acknowledged limit of the approved content-first
strategy, not a test failure or a claim that all searches are faster. No automatic
genre-size threshold, cache or additional count query was introduced. Production
DDL/deployment and production post-change measurements were not performed.

Primary references: [MySQL JOIN](https://dev.mysql.com/doc/refman/8.4/en/join.html),
[functional indexes](https://dev.mysql.com/doc/refman/8.4/en/create-index.html),
[Index Merge restrictions](https://dev.mysql.com/doc/refman/8.4/en/index-merge-optimization.html).

## Normalized-query regression measurements

The 2026-10-09 MySQL 8.4.8 Testcontainers run used a separate 50,000-content DB,
25,000 drama links, five rare-genre links, no horror links and 30 keyword matches.
Localized search was true; the search-title index used an empty session-specific
stopword table. These are single sequential database samples, not HTTP latency,
production measurements or a concurrent-load guarantee. The earlier table is
historical index-only evidence, not a controlled normalization A/B comparison.

| Request | Database ms | Repository ms | Access / results |
| --- | ---: | ---: | --- |
| Drama first page | 0.203 | 4.69 | 41 ordered entries, 21 results |
| Drama + TV | 0.119 | 3.34 | 41 media/popularity entries, 21 results |
| Rare genre | 93.6 | 91.60 | 50,000 ordered entries, five results |
| Empty genre | 93.1 | 93.53 | 50,000 ordered entries, zero results |
| Keyword | 0.120 | 4.64 | 30 UNION candidates, 21 results |
| Keyword + drama + TV | 0.098 | 4.09 | 30 candidates, seven results |
| Drama deep page | 0.076 | 2.30 | 42 range entries, 21 results |
| Drama last page | 0.004 | 1.57 | Empty popularity range, zero results |

Database and Repository timings are separate executions, so a warmed Repository
sample can be faster than its EXPLAIN sample. Repository time includes query
binding, genre lookup, projection and enabled test logging; it excludes HTTP,
authentication and response serialization.

`ContentSearchPlanRepositoryTest` asserts the actual FULLTEXT/normalized indexes,
candidate materialization and covering genre lookups, and records both EXPLAIN
and Repository elapsed time. It uses a DB separate from small ranking fixtures:
bulk deletion otherwise leaves FULLTEXT corpus statistics in those fixtures
temporarily unrepresentative. Timing is reported, not a brittle pass/fail limit.
The full application's HTTP/security/integration smoke test and staging/production
load measurements still need the maintenance deployment gates below.

Final local gates passed: `:modules:content:test :apps:batch:test :apps:api:test`
and the full `build`. Available suite results total 286 tests, zero failures,
errors or skips; unchanged tasks used Gradle's up-to-date cache. MySQL tests
verified genre preview/500-ID checkpoints/restore/FK and backfill rollback/restart.
The pre-existing coordinator resume/shutdown tests remain, alongside backfill-only
start/heartbeat/resume/shutdown isolation tests. No production DB was accessed.

## Genre and localized-search maintenance

The SQL and jobs do not automatically perform a production rollout. The approved
operator run combines schema, genre and search changes in one continuous,
write-stopped maintenance window. Keep the localized
flag false until every gate below passes. Genre code migration is required even
when localized search is false because genre lookup now uses `genre.code`.

1. Pause the API deployment workflow, record its prior state and the running
   immutable image/configuration, and prepare a verified new immutable image.
   Recheck the Online SSM target, private RDS, storage headroom, FK/triggers,
   indexes and unfinished jobs. Stop all API/admin writes, blue/green API instances and TMDB jobs; wait for
   their transactions to finish. Take/export the relevant genre and relation
   backups and retain them outside RDS. Do not perform language/duplicate cleanup.
2. Read `content-search-genre-normalization.sql` preflight and compare engines,
   column types, FK/UNIQUE definitions and triggers. Stop on unexpected references
   or names. The SQL intentionally requires source groups for all 24 canonical
   genres; do not manufacture missing IDs/names to bypass a failed guard.
3. Keep the existing `dev` profile. In Parameter Store's environment path set
   `spring.jpa.hibernate.ddl-auto=none`, `flint.batch.execution-enabled=false`,
   `flint.batch.scheduling.enabled=false`, `flint.batch.s3-cleanup.enabled=false`
   and `flint.admin.metrics.scheduling.enabled=false`. Host environment variables
   alone are not forwarded by the deploy script. Verify graceful shutdown and no
   remaining application transactions before DDL. Apply only missing admission
   schema items from `tmdb-batch-admission.sql`, including the permanent write lock.
   In one SQL connection install seeds/procedure definitions, set the database,
   run key and explicit approvals, then call `flint_genre_backup`. This records
   `BACKED_UP` without changing genre data or column types. Export all four backup
   tables (schema, genre, relation, TMDB mapping) off RDS and verify export counts.
   Set `@genre_backup_exported=1`, then call `flint_genre_prepare`. Existing
   `ENUM NOT NULL` becomes `VARCHAR(32) NULL` before code assignment; incorrect
   ACTION values are ignored in favor of explicit name aliases. The original
   snapshot is never overwritten, including after a failed prepare. DDL cannot
   be undone by ROLLBACK.
4. Call `flint_genre_chunk` with commit flag zero first: it processes at most 500
   distinct content IDs and rolls back. Review mappings and expected relationships.
   With explicit commit flag one, repeat the same call until `next_id` is NULL.
   Only successful committed chunks advance the persisted ID checkpoint.
   `BACKED_UP -> PREPARED -> RELATIONS_DONE -> FINALIZING -> COMPLETED` records
   durable stages. Retry the unfinished stage; completed stages are skipped.
   After reconnecting reload temporary seeds and approval variables. Never use
   a new run key to bypass a failed stage or replace an existing snapshot.
5. Call `flint_genre_finish`. It verifies original projected relations and lack
   of unexpected writes, removes unreferenced aliases, assigns Korean names,
   enforces non-null unique codes and seeds 35 Movie/TV TMDB mappings. Verify 24
   masters, 35 mappings, zero alias references, duplicate links and orphans.
6. Verify named native-query indexes with the existing performance DDL; create
   only missing compatible indexes. Run the manual Java search-document job
   using a temporary copy of the API image/configuration, with no public traffic:

```bash
java -jar apps/api/build/libs/flint-api-0.0.1-SNAPSHOT.jar \
  --server.port=0 \
  --spring.batch.job.enabled=false \
  --decorator.datasource.p6spy.enable-logging=false \
  --spring.jpa.hibernate.ddl-auto=none \
  --flint.batch.execution-enabled=false \
  --flint.batch.scheduling.enabled=false \
  --flint.batch.s3-cleanup.enabled=false \
  --flint.admin.metrics.scheduling.enabled=false \
  --flint.content.search-document-backfill-enabled=true \
  --flint.content.search-document-backfill-version=v1
```

Use the existing protected runtime configuration for DB/integrations; never
pass credentials on command lines. The batch module is a library of the API,
not a standalone bootJar. The opt-in runner launches only this job synchronously
and closes its context on completion. Failures abort startup. The same version
restarts a failed JobInstance; a completed version cannot run again. For a
deliberately new rebuild choose a new version only after reviewing the previous
execution. The job checks missing localized titles globally before starting,
updates only three derived fields and checkpoints every 500 rows. Optimistic
source-title checks use NULL-safe binary comparison (including case, accents and
trailing spaces) and roll back a chunk if titles changed after reading.
Local TMDB triggers and automatic resume are blocked; this does not stop other
processes, hence the mandatory maintenance window.

7. Confirm job COMPLETED and no skipped/missing rows. Compare blank normalized
   titles with the Java normalizer: symbol-only originals intentionally produce
   null normalized values; retain those works and do not invent translations.
   Repair actual omissions using the same document builder. Then rebuild only the
   FULLTEXT configuration using the commented empty-stopword stage in SQL.
   Configure `innodb_ft_user_stopword_table` on that connection, then restore
   its previous session value. Do not change global settings or ngram size 2.
   Empty stopwords matter for English substrings such as `it`, not just exact
   matches. Validate charset/collation and `It Follows` substring behavior.
   MySQL 8.4.8 retains the original table-level stopword configuration when only
   search-title FULLTEXT is rebuilt while title FULLTEXT remains. A successful
   DROP/ADD, even in separate statements, does not prove the new configuration.
   This case requires separately approved `ALTER TABLE content FORCE,
   ALGORITHM=COPY, LOCK=SHARED` under the empty session stopword setting. It keeps
   column/index/FK definitions but rebuilds the table and ALL indexes; both
   FULLTEXT indexes then use empty stopwords. Stop the API, check disk space,
   preserve checksums/definitions and verify them afterward. No global variable
   change is needed. Check partial membership on an actual existing title through
   a fresh connection, not merely normalized exact matching or a top-20 ranking
   assumption. The legacy-title flag no longer restores the old stopword list.
8. Start the new API with localized search enabled consistently on all instances.
   Smoke-test all three endpoints, full-width/space/punctuation equivalence,
   exact ranking, v2 keyword cursor traversal, v1 popularity and bookmark cursors,
   canonical genre filters and bookmark ownership. Turn the manual backfill option
   off; keep all batch switches off during smoke tests. These flags are read at
   startup, not dynamically refreshed: keep Nginx maintenance active for the
   execution-only restart followed by the scheduling restart, verify each stage,
   then resume external traffic once. Observe
   existing stopped runs/first bounded work. Do not start a fresh full import.
   Keep 5 requests/s, concurrency 3 and chunks of 50. If batch activation fails,
   disable batch again but retain the already validated API. Restore deployment
   workflow activation only after the maintenance gates succeed.

Rollback: disable localized search consistently to restore the legacy source
selection and keyword v1 contract, retaining additive indexes and genre codes.
This does not undo input validation or genre migration. To restore genre data,
keep ALL writers stopped, run `flint_genre_restore` with explicit approval and
verify original IDs/relationships. It refuses new relation/master IDs, restores
all backed-up rows/mappings and retains nullable additive schema. It removes
`uk_genre_code` only when this run recorded ownership and its exact definition
still matches; arbitrary existing constraints are not removed. Schema/index
definitions are retained in the exported snapshot for a separately reviewed
schema restore. Restoration does not restart any API. The approved failure policy
is to keep maintenance active, confirm SQL completion, fix the cause and resume
the recorded stage using the same image/job version. No automatic restoration,
old-image startup, content deletion, user-relation cleanup or RDS snapshot is part
of this run. DDL is a separate operation with implicit commits, not a transactional
rollback: [MySQL implicit commits](https://dev.mysql.com/doc/refman/8.4/en/implicit-commit.html).

Performance gates: compare first/deep/last pages and frequent/rare/empty genres
on representative staging data with sequential bounded EXPLAIN ANALYZE and API
timings. Preserve content-first popularity and candidate-first keyword joins.
The rare/empty full ordered-scan limitation is unchanged; no adaptive routing,
cache, scan cap or relevance-candidate truncation is introduced.

Reference: [MySQL table-specific stopwords](https://dev.mysql.com/doc/refman/8.4/en/fulltext-stopwords.html).
Related report: [MySQL bug 109885](https://bugs.mysql.com/bug.php?id=109885).
The production issue and workaround are independently reproduced by the MySQL
8.4.8 Repository test; the older bug report alone is not version-specific proof.

## Earlier operator checkpoint: 2026-10-10 KST

This historical hold point is superseded by the COPY checkpoint below. It records
why the additional rebuild needed separate approval; do not use its runtime state
as the current production state.

**ON HOLD: external maintenance remains active.** Do not enable traffic, batch
execution, schedules or the deployment workflow before the stopword gate passes.
The additional COPY table rebuild requires operator approval and has NOT been
executed against production. This is a partial rollout, not completion evidence.

- Target: private `flint-dev-mysql`, MySQL 8.4.8, database `flint`; dev profile
  retained. Parameter Store explicitly sets Hibernate DDL to `none`.
- Preflight: 946,275 contents, 49 genres (all codes were ACTION in ENUM NOT NULL),
  1,177,439 genre links. Relevant engines/FKs/triggers and free disk were checked.
- Genre run `genre-localized-20261010-v1` is COMPLETED: 24 canonical masters,
  35 mappings, 1,177,269 links after merging 170 duplicate genre relationships.
  Duplicate code/alias reference/genre orphan/duplicate link checks are zero.
- Original schema/master/relation/mapping backup was exported outside RDS and
  actually restored into a disconnected MySQL 8.4.8 container. Backup bytes:
  104,734,161; SHA-256:
  `7ddb2805a9cb936988eb2500369a259786339aa0737ae5b499d56fab49304868`.
  The protected export is in ignored `.local/catalog-maintenance/` and is not
  committed. No RDS snapshot was created.
- Admission generated column/index/permanent write-lock row and the three
  missing equality indexes are present. Only missing definitions were added.
- Search backfill execution 819, version `genre-localized-20261010-v1`, completed
  946,275 reads/writes, 1,893 commits, zero rollback/skip. A full Java comparison
  found zero derived-document mismatches; 90 symbol-only normalized-null rows
  were preserved. Do not relaunch this completed JobInstance.
- Original ID/source-title checksum and bookmark-count sum stayed identical.
  Content bookmarks (333), collection contents (459), collection bookmarks (12)
  retained their counts and ID sums. No works or user relationships were deleted.
- The verified ARM64 image is pinned to digest
  `sha256:7ba038bc8875e67aa692b5e1e312898bf1d8e0eb492c8f309790ed70a7d7c670`;
  tag `catalog-ee173b8a8ba3b5a8-20261010`, API JAR SHA-256
  `ee173b8a8ba3b5a841421625d4c477953ccc6d130ff6dc7a48f35d9b6b3e98ed`.
  API 8081 internal health was UP. After the failed stopword gate it was
  gracefully stopped; both 8080/8081 now have exit 143 without OOM/forced kill.
  No application transaction, Spring Batch job or TMDB run remains active.
  Source changes are still local/uncommitted and must enter release before a
  future automated image replaces these fixes.
- Public Nginx remains 503; `deploy-api.yml` is disabled (previously active).
  Localized search is true and manual backfill false. Execution, scheduling,
  S3 cleanup and admin metrics remain false. Before maintenance, dev defaults
  enabled batch scheduling/S3 cleanup; admin metrics had no enabled setting.
- Rebuilding only search-title FULLTEXT reported DDL success but failed the
  actual partial-English gate: both It Follows rows matched `itf` and `itfollows`,
  but neither matched `it`. The single/then separate DROP/ADD failure was
  reproduced locally with a legacy title FULLTEXT already present. COPY fixed
  it locally while preserving fields, generated key, FK relationship and index
  names; it also changes legacy title FULLTEXT stopwords. Production COPY remains
  pending approval. A top-20 intersection alone is not a valid stopword test.
- Main/legacy normalization, cursor traversal, v2 keyword/v1 popularity and
  invalid-input checks reached the final stopword assertion, where the combined
  smoke test failed. The run is NOT a passed smoke test. Authenticated bookmarked
  HTTP verification awaits a designated test account; Repository isolation tests
  passed. No existing STOPPED TMDB run needs auto-resume; eight historical FAILED
  runs were not retried. The next daily cron is 05:00 KST and is not yet observed.
- Full build and related suites passed: 293 available tests, zero failures/errors/
  skips after adding the stopword regression. `bash -n` and `git diff --check`
  also passed. Cached unchanged tasks are not new executions.

### Preliminary production plans

These sequential bounded samples were collected BEFORE stopword remediation,
without external traffic or TMDB activity. They are not final post-remediation
latency or a load-test/SLA. Database milliseconds are EXPLAIN ANALYZE timings;
the CLI elapsed time (which includes private-tunnel/connection overhead) is not
HTTP latency. Keyword joins remained candidate-first, popularity content-first.

| Query | Database ms | Evidence |
| --- | ---: | --- |
| Drama first | 25.0 | Ordered popularity scan and covering genre membership |
| Drama + TV | 35.9 | Media/popularity index and covering membership |
| Rare NEWS | 957 | 94,345 ordered entries for 21 matches; SQL-only, not a UI enum |
| Empty genre -1 | 8,372 | Synthetic predicate, 946,275 entries, zero matches; no data change |
| Korean keyword | 2.13 | 12 equality/FULLTEXT UNION candidates |
| Keyword + drama + TV | 0.272 | Zero matches after indexed candidate filtering |
| Drama deep | 2.03 | Cursor from the 10,000th popularity entry, not 10,000 genre results |
| Popular tail | 0.0228 | Empty keyset range |
| Keyword next page | 0.261 | Seven remaining candidates |

English requests also showed long `FULLTEXT initialization` (an observed in-flight
sample reached 12 seconds). Separate internal HTTP samples of `harry potter`
returned 200: main search 25,037ms for 20 items; unlimited legacy search 18,482ms
for 29 items. These are single sequential pre-remediation samples, not an SLA.
Re-measure after the approved stopword fix and retain any remaining bottleneck
explicitly; correctness remediation does not prove this latency is resolved.

Resume from this checkpoint: confirm no SQL is still running and both APIs remain stopped,
check disk/definitions, then perform the separately approved empty-stopword COPY.
Verify original checksums, all index/FK definitions, actual partial membership
and fresh-connection behavior before starting the same new image. Do not repeat
genre migration or completed backfill. Re-run smoke/performance checks, then do
the execution-only and scheduling restarts before restoring traffic/workflow.
If the additional rebuild is not approved, keep maintenance active; do not
silently weaken the stopword assertion, auto-roll back or start the old image.

## COPY checkpoint: 2026-10-10 11:05 KST

**COPY completed; API/batch reopening gates are still in progress.** External
Nginx maintenance remains active at this checkpoint. The separately approved
`ALTER TABLE content FORCE, ALGORITHM=COPY, LOCK=SHARED` used the empty
table-specific stopword setting and completed in approximately 14 minutes.

- All content fields, generated keys, column/index/FK definitions and original
  source/relationship fingerprints were preserved. There are still 946,275
  contents, 333 content bookmarks, 459 collection contents and 12 collection
  bookmarks. No works or user relationships were deleted.
- A fresh connection confirmed that both existing normalized-English It Follows
  rows match the Boolean phrase `"it"` through `search_title`. Legacy `title`
  matches only the row whose display title contains English; the Korean display
  title is correctly searchable through its English translation in `search_title`.
- Canonical genres/mappings remain 24/35; duplicate genre relationships, genre
  orphans and generated deduplication-key mismatches are zero. The off-RDS
  original backup checksum remains unchanged.
- The same digest-pinned new API is running on 8081 with internal health UP.
  Execution, schedules, S3 cleanup and admin metrics remain disabled. Hibernate
  DDL remains `none`, localized search true and manual backfill false.
- Code-only commits `65205bf` and `3bc9ffa` were pushed to `develop`; remote HEAD
  matches `3bc9ffaee06be55f0076146626d9fc5485fdb0ca`. Release still points to
  `05aa4d047ec1fc213c2471848783585c8acf852e`. The deployment workflow stays
  disabled until these fixes enter release; restoring traffic is a separate gate.
- All `.md` and `.sql` changes were deliberately excluded from the commits at the
  operator's request. `CatalogNormalizationJdbcRepositoryTest` reads the local
  untracked migration SQL, which does not exist on remote develop. The operator
  explicitly accepted the resulting known `NoSuchFileException` CI constraint.
  Tests were not removed, skipped or weakened to conceal it. Local SQL-backed
  test success does not imply that the code-only remote checkout can pass CI.
  CI run `38014916854` on `3bc9ffa` finished with failure. The exact exception
  in that new run was not reverified: the diagnostic-log fetch was rejected for
  sensitive-log risk. Do not describe the expected fixture failure as a freshly
  confirmed exception from this run.
- The unchanged targeted suites/full build were checked again successfully with
  cached tasks. This is not a new execution of the earlier 293-test result.
  A subsequent uncached focused run executed seven coordinator and eight catalog
  normalization Repository tests: 15 tests, zero failures/errors/skips, including
  real MySQL 8.4.8. Its temporary Testcontainers were confirmed stopped afterward.
- Actual daily scheduling is `0 */10 5-23 * * *` in Asia/Seoul, using the same
  date-based business key on each attempt. It is not a single 05:00 run. No new
  daily execution has been observed yet at this checkpoint; full monthly import
  and historical failed runs must not be retried as part of this rollout.

### Post-COPY production plans

These are sequential, bounded EXPLAIN ANALYZE samples on the actual production
dataset without external traffic or TMDB activity. Existing candidate-first
keyword and content-first popularity access order was verified. They are not
HTTP latency, load-test results or an SLA.

| Query | Database ms | Result rows |
| --- | ---: | ---: |
| Drama first | 15.1 | 21 |
| Drama + TV | 74.9 | 21 |
| Rare NEWS (SQL-only) | 909 | 21 |
| Empty genre -1 (synthetic predicate, no data change) | 9,038 | 0 |
| Korean keyword | 3.33 | 12 |
| Keyword + drama + TV | 1.44 | 0 |
| Popular deep (10,000th ordered entry) | 6.60 | 21 |
| Popular tail cursor | 1.63 | 0 |
| Keyword next page | 0.349 | 7 |

The existing rare/empty-genre popularity scan limitation remains. COPY repaired
stopword membership; it does not establish that broad English FULLTEXT latency
or sparse-genre scans are fixed.

### Post-COPY internal HTTP samples

These requests ran sequentially on EC2 against the new API while external
maintenance and all batch execution remained disabled. They are individual
samples, not an SLA or load test.

| Request | Status | Elapsed ms | Items |
| --- | ---: | ---: | ---: |
| Main `harry potter`, size 20 | 200 | 49,660.82 | 20 |
| Legacy `harry potter`, unlimited | 200 | 48,802.53 | 29 |
| Main Korean Harry Potter, size 20 | 200 | 126.15 | 12 |
| Main `it` + HORROR, size 20 | 200 | 31,278.20 | 20 |

An in-flight English request was observed in `FULLTEXT initialization` at 15
seconds. These measurements do not isolate all of that stage's internals, but
they show that the remaining delay cannot be attributed solely to sorting the
small final result. Query-plan correctness and stopword correctness do not fix
this initialization bottleneck.

The operator explicitly chose **keep maintenance; handle performance separately**
after being shown the approximately 49-second English latency. Do not restore
traffic, batch execution/schedules/S3 cleanup or the deployment workflow on the
basis of functional smoke success alone. The smoke observer timeout was extended
from 35 to 90 seconds solely to finish correctness assertions on slow requests;
matching/cursor assertions were retained, and this is not an accepted latency
threshold. Full functional verification is still in progress at this checkpoint.

## Final hold checkpoint: 2026-10-10 11:20 KST

**Maintenance intentionally remains active.** COPY and functional search gates
are complete, but performance remediation and service/batch reopening are not.
The operator explicitly chose to keep the hold after seeing the English latency.

- The internal functional observer completed normalization across Korean/English,
  NFKC/spacing/symbol variants, main/legacy matching, complete keyword keyset
  traversal, duplicate/omission checks, keyword v2 rejection of v1, popularity v1,
  six UI genre filters, invalid input, default lists and exact/partial English
  membership. Initial observer failures were diagnosed as its assumptions, not
  hidden by changing server behavior: database integer IDs were compared against
  the existing JSON string-ID contract, and unauthenticated access was incorrectly
  assumed to return JSON 401. Existing security actually denies it with 403.
  The observer was corrected, the first 48 requests retained, and the last three
  requests replayed from that checkpoint with all matching assertions intact.
- The replay verified exact English source IDs (200, 8,683ms), partial English
  search (200, 1,411ms) and unauthenticated saved-content denial (403, 14ms).
  These samples concern different queries from the approximately 49-second
  Harry Potter sample; they do not establish a broad-English performance fix.
- Authenticated saved-content HTTP isolation and numeric cursor verification
  remain unrun without a designated test account. Existing Repository isolation/
  cursor results must not be presented as authenticated HTTP evidence.
- Both API containers were gracefully stopped after observation: exit 143,
  no OOM or forced termination; 8080/8081 have no listeners. No TMDB run, Spring
  Batch Job or other database transaction is active. Nginx continues to return
  external 503. No old image was restarted and no automatic rollback was used.
- Parameter Store retains DDL `none`, localized search true and backfill false.
  Execution, scheduling, S3 cleanup and admin metrics remain false. Batch first-
  run/resume/scheduling observation was deliberately deferred; no full reimport,
  historical failed-run retry, work deletion or RDS snapshot was performed.
- Develop has the two code-only commits recorded above. All four outstanding
  `.md`/`.sql` changes remain local. CI is failed with the previously documented
  missing-SQL constraint; the new failure's exact log was not reverified. Release
  still lacks the new commits and the API deployment workflow remains disabled.

Resume work by separately addressing broad-English FULLTEXT initialization and
the existing sparse-genre popularity scan limitation, then repeat the relevant
bounded measurements. Do not reopen traffic/batch merely because correctness
passed. A new reopening decision, release source alignment and the remote CI
fixture decision are required before restoring automation. Do not repeat the
completed genre migration, search backfill or COPY reconstruction.

## Approved low-cost search read model: 2026-10-10

The operator requested end-to-end recovery, approved the AND/continuous-match
optimization, allowed the existing genre migration SQL fixture to be committed,
and approved dedicated account creation/verification/deletion. RDS upsizing was
explicitly rejected. A narrow, same-database search read model was then approved.
These decisions supersede the historical hold checkpoint's missing approvals;
they do not imply that traffic has already reopened.

`9522128` retains exact/relevance/ID ordering and repeated bigram contribution.
Local full build passed (296 tests, zero skips) and its remote CI passed. Its
final single-query sample still took 35,402ms for `it` + HORROR: approximately
17.7s fetching FULLTEXT rows, 9.9s re-reading 79,267 wide content rows, and 4.4s
checking their genre relationships. Short-query covering title scans reduced
the genre case to 9.8s but the unfiltered case still took 37.2s; that alternate
path was not activated. Runtime RDS has a 128MiB InnoDB buffer pool, no CPU-credit
exhaustion in the observed window, and limited free memory. No class or global
buffer setting was changed.

Dedicated account HTTP checks completed successfully: normalized English search,
numeric cursor traversal, owner isolation, saved-list membership, blank/invalid
inputs, withdrawal and token revocation. Both test users and all four temporary
bookmarks were removed; original relation counts and content counters matched
the baseline. This verifies HTTP behavior, not a TMDB-provider or device E2E run.

The new schema is prepared in `content-search-read-model.sql`, kept local under
the requested SQL commit exclusion. It copies all source rows, not just searchable
rows, to preserve corpus statistics. Independent monotonically increasing FTS
document IDs avoid the large gaps and reuse risks of using business IDs directly.
Source triggers maintain the copy atomically, including physical deletion, and
do not rewrite it for counter-only changes. Initial backfill is 500-row committed
chunks with a recorded source-ID checkpoint, with all writers stopped. The
FULLTEXT index is built after backfill using the same empty stopword list.

Read activation requires zero missing/mismatched/orphan documents and matching
membership. Production comparison showed that corpus statistics change numeric
scores and some relevance ordering. The approved read-model keyword cursor is
v3; prior v1/v2 keyword cursors return 400 and restart at the first page. Popular
v1 and numeric saved-content cursors are unchanged. The fulltext candidate must show `Using index`
when selecting only FTS_DOC_ID and score. Filtering/ranking takes place in the
narrow document table through a covering ID/type/normalized-title index, and only
the final page hydrates wide content rows. Popularity retains its access order,
filters, sort and v1 cursor, with covering ID/count scans followed by final-page
hydration as well. Saved-content QueryDSL is unchanged. The feature defaults
off; read rollback disables this flag without dropping triggers or data. Future
manual content cleanup must allowlist the three owned synchronization triggers.

The original source indexes remain in place. The legacy unlimited keyword API
still reads and serializes every match and is not promised bounded latency for a
query returning tens of thousands of works. No candidate limit, result truncation,
or infrastructure upsizing was introduced.

The local MySQL regression caught two initialization/lifecycle requirements:
an explicit FTS_DOC_ID must advance when indexed text changes, so the source
update trigger deletes/reinserts the derived document atomically; counter-only
updates leave its ID unchanged. FULLTEXT scoring uses InnoDB row statistics, so
the new table must be analyzed after the initial index build. Small fixture score
equivalence is not sufficient to assert production numerical equality; production
comparison remains a separate activation gate.

The temporary trigger parameter group has been removed. The default group is
in sync and log_bin_trust_function_creators is restored to 0. A production
insert/update/delete smoke ran inside one transaction and rolled back completely;
all three triggers synchronized successfully and no synthetic content remains.
Covering must be verified on the complete query: derived MATCH projections mark
the text column as read in MySQL 8.4.8 and can silently defeat FTS index-only
access. At this checkpoint API traffic remains in maintenance because broad
keyword latency is not yet acceptable; schema/correctness success is not service
recovery.

## Additional query experiments: 2026-10-10 (not adopted)

After the operator requested the next optimization step, read-only probes ran
against the existing private MySQL 8.4.8 RDS, without changing its class,
parameters, persistent schema, source content or user relationships. API traffic
was still in maintenance. These are SQL observations, not measurements of a new
deployed API or a concurrent production load test. No application source change
from these experiments was adopted.

The probes covered:

- A connection-local InnoDB temporary table containing only FTS document ID and
  relevance. Its INSERT SELECT used fulltext covering, but staging 79,267 `it`
  hits took 7.21s in one sample and subsequent HORROR filtering took 4.99s.
  Another unfiltered sample staged them in 0.73s and ranked them in 0.42s.
  The tables were dropped after the read-only transaction or removed with the
  connection; no persistent rows were written. Temporary-table DDL must be
  outside a READ ONLY transaction (error 1792), even though temporary DML is
  permitted inside it. This prototype is not an application transaction change.
- A single top-level fulltext handler, covering document metadata and global
  UNION ordering. An empty UNION branch preserves the fulltext covering plan;
  this alone is not a sufficient replacement because exact-only fallback must
  survive even when a fulltext token is absent.
- HORROR relationship point lookups, sequential materialization using the
  `(genre_id, content_id)` index, and a BNL hint. The hinted plan actually remained
  a nested-loop indexed lookup, not a hash join. Materialization and changing
  the index did not establish a stable improvement across mixed keywords.
- A separate indexed exact-hit lookup followed by one fulltext handler. All
  exact hits are retained while fetching `size + 1 + exactHitCount` globally
  ranked hits; exact hits bypass the SQL cursor filter, then the merged rows
  receive the original keyset comparison and final page bound. This protects
  zero-score exact-only fallback across pages without truncating FTS candidates.
  Eleven first/subsequent pages of `harrypotter`, `it` (with/without HORROR) and
  `banana` matched the existing IDs, ranks and numeric scores. Nevertheless,
  first observed fulltext-query samples took 9.24s, 12.13s and 12.27s. Some later
  samples improved to 0.68-1.33s, which does not prove a service recovery.
- Removing the projected relevance calculation while retaining fulltext
  candidates did not eliminate the latency variability. Replacing fulltext with
  covering normalized-title scans also changes the approved ranking policy;
  those alternative samples took 6.03-7.91s initially and about 0.97-1.82s on
  a later pass. Neither alternative was activated.

The samples mix query shapes and keyword order. They are not controlled cold
cache benchmarks, p95 statistics or isolated attribution of the time to one
operator. EXPLAIN/FT initialization itself can warm caches, so its wall time must
not be conflated with execution timing. A faster repeated sample must not be
reported as a stable performance fix.

CloudWatch's observed 30-minute window reported free memory around 64-130MiB
and swap around 259-452MiB, with CPU credits still available (about 287-288) and
gp2 BurstBalance around 54-57%. This supports memory pressure as a concern, but
does not establish swap as the sole cause or show CPU/storage credit exhaustion.

The next design decision is explicit: keep existing relevance and validate a
separate ngram posting/statistics read model, or approve a simpler title-oriented
ranking policy. Both need representative measurements; neither is an approved
or implemented policy at this checkpoint. Do not silently change sorting, cache
away misses, truncate candidates, increase instance size or repeat the completed
migration/COPY/backfill. Preserve the current source and v3 cursor implementation
until a replacement passes correctness and performance gates.

## Current service restoration: 2026-10-10 16:22 KST

The operator explicitly requested applying the currently verified changes and
ending maintenance with the remaining latency accepted. This supersedes the
historical maintenance hold points above; it does not establish a performance
fix or SLA compliance.

- Deployed `search-adaptive-genre2048-20261010-v1`, pinned to
  `sha256:6c04efeda6ad1a58c222405779287713efb5e388242ecbb2836732ec5da62e15`.
  The image includes the local bounded genre access-path improvement; keyword
  scoring and v3 cursors are unchanged.
- API 8081 is running, restart count zero, no OOM. Nginx targets 8081 and its
  maintenance responses are removed. Old API 8080 remains stopped.
- Parameter Store explicitly enables localized search and the search read model;
  Hibernate DDL remains `none` and manual backfill remains disabled. The
  verification-only read-only datasource override is not present on the live API.
- Source/search-document counts are both 946,275; genre/mapping counts are 24/35.
  Three search synchronization triggers and the existing read-model indexes are
  present. ngram is 2, trigger trust is 0, dedicated stopwords are empty. This
  restoration rechecked counts/schema, not another full document comparison.
- Internal HTTP normalization, keyword v3/popular v1 cursor traversal, previous
  v2 rejection, invalid input and unauthenticated saved-search checks passed.
  Authenticated saved-search checks were not repeated during this restoration.
- External health is HTTP 200/UP. External default/Korean/English/English+genre
  searches and popular collections are HTTP 200. Sequential samples measured
  158.61ms default, 209.63ms Korean, 13,669.23ms harry potter, 5,888.99ms it/HORROR
  and 621.01ms popular collections. These are not p95 or load-test results.
- TMDB execution/schedules, S3 cleanup and admin metrics schedules remain disabled.
  The deployment workflow remains disabled because release
  `eaa07402a54fdaf1632bda6ff55636e494d72c3f` predates the verified source/image.
  Reflect the verified source in release before re-enabling automatic deployment;
  do not accidentally replace the live image with the old release image.
- No new infrastructure, resizing, deletion, DDL, full import, COPY or backfill was
  performed. The task-owned private-RDS tunnel/listener was closed. Only the
  user-requested production API is intentionally left running.

Detailed restoration and residual latency evidence is in
`docs/bookmark-order-search-performance.md`, section 13. Markdown/SQL exclusions
from earlier commit requests remain unchanged; this operation did not commit or
push the remaining local changes.
