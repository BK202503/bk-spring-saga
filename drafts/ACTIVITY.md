# Activity log

Chronological record of releases, community submissions, and upstream contributions
that started from this project. Update as new items land.

## 2026-05-31 — v0.1.0 launch

### Release

- Tag **v0.1.0** pushed to `origin/main`.
- GitHub Release published:
  <https://github.com/BK202503/bk-spring-saga/releases/tag/v0.1.0>

### Distribution

- **JitPack** build of `v0.1.0` warmed up by fetching
  `saga-spring-boot-starter-v0.1.0.pom` — `HTTP 200` in 55 s, so first
  consumer download is hot.
  - Library coordinate now available: `com.github.BK202503.bk-spring-saga:saga-spring-boot-starter:v0.1.0`
- **Maven Central** publishing: planned, not started.

### Curation lists

- PR **#1125** to `Heapy/awesome-kotlin` adding `spring-saga-kt` under
  `Libraries/Frameworks` → `Web`, alongside the existing Spring-microservice
  entries (next to `Ahoo-Wang/Wow`).
  - PR: <https://github.com/Heapy/awesome-kotlin/pull/1125>
  - Status: waiting for maintainer review.

### Drafts queued for the author to send manually

These can't be automated (account / web-form constraints) — copy-paste the
matching `drafts/*.md` file:

| Channel              | Source draft                               | Status                |
|----------------------|--------------------------------------------|-----------------------|
| dev.to (English)     | `drafts/launch-post-en.md`                 | not posted yet        |
| velog (Korean)       | `drafts/launch-post-ko.md`                 | not posted yet        |
| Kotlin Weekly        | `drafts/kotlin-weekly-submission.md`       | email not sent yet    |
| Reddit r/Kotlin      | `drafts/reddit-r-kotlin.md`                | not posted yet        |
| OKKY                 | `drafts/okky-post.md`                      | not posted yet        |
| GeekNews             | `drafts/geeknews-post.md`                  | not posted yet        |

Note: the old `www.kotlinweekly.net/submit-link` form is dead (SSL mismatch
plus the path is gone). Confirmed via `kotlinweekly.net` front page that
submissions are now by email to `mailinglist@kotlinweekly.net`.

## 2026-06-01 — Upstream contribution to Spring Modulith

Working on `spring-saga-kt` surfaced two natural upstream targets in
`spring-projects/spring-modulith` whose event-publication registry overlaps
with this library's saga state model.

### Spring Modulith #1439 — Automatic retry scheduler for FAILED events

Posted a design-focused comment proposing a **per-listener** retry policy
(`@ApplicationModuleListener(retry = …)`) rather than the global YAML
scheduler the original ticket suggested. Maintainer (`@odrotbohm`) had
already expressed skepticism about the one-size config approach; the comment
references the per-step retry policy / `COMPENSATION_FAILED` split this
library implements as an in-the-wild reference.

- Comment:
  <https://github.com/spring-projects/spring-modulith/issues/1439#issuecomment-4587093248>
- Status: waiting for maintainer reaction.

### Spring Modulith #1683 — `JpaEventPublicationAdapter#getStatus()` ignores persisted status

Bug: the JPA adapter derived `Status` purely from `completionDate`, so
publications correctly written as `FAILED` / `RESUBMITTED` were reported as
`PUBLISHED` through the API. The JDBC v2 adapter already does it the right
way (returns the persisted `status` column with a `PROCESSING` fallback);
the fix aligns the JPA adapter with that reference impl.

PR opened:

- **PR #1714** — *GH-1683 - Return persisted status from JpaEventPublicationAdapter#getStatus*
- <https://github.com/spring-projects/spring-modulith/pull/1714>
- Branch: `BK202503/spring-modulith:GH-1683-fix-jpa-getstatus`
- 1 source change, 1 test added (`exposesPersistedStatusOnReload`).
- Test demonstrates the bug — without the fix it fails with `expected: FAILED but was: PUBLISHED`.
- `em.flush() / em.clear()` between `markFailed` (JPQL update) and re-read,
  to load a fresh entity matching the production scenario.
- Local result on `spring-modulith-events-jpa`: `Tests run: 58, Failures: 0, Errors: 0`.
- Status: waiting for CLA + review. Spring maintainers typically take 1–2
  weeks; expect ~1 round of style feedback.

### Why this issue was the right first PR

- Smallest credible PR in `spring-modulith` that exposes a real bug.
- Uncontested (no in-flight PR — checked via `gh pr list`).
- Domain-matched: this library's `SagaRecord.status` is the same
  "always carry the explicit state, never derive from a timestamp" pattern.
- Reference impl already exists in the same repo (JDBC v2 adapter), giving
  the PR a strong "align with existing reference" framing instead of
  introducing new opinions.

#1701 ("delete batching") was a stronger candidate by size but already
covered by PR #1702 — moved on.

#1439 ("retry scheduler") was the strongest *domain* match but is a feature
where the maintainer is still in "wait and see" mode. Engaged via comment
rather than burning a first-PR slot on a feature that may not be accepted.

## 2026-06-06 — Two more Spring upstream PRs

### Spring Kafka #4469 — review iteration

`@artembilan` (Spring Kafka maintainer) flagged the `Thread.sleep(2_000)` in
the bounded-retry regression test as suspicious — "isn't the latch enough?".
The latch wasn't enough (`CountDownLatch(1)` only catches the *first*
recovery; under the bug the cycle keeps repeating), so the actual signal is
the delivery counter holding steady at 3.

Switched the test to Awaitility's `pollDelay + atMost + untilAsserted`
pattern, which expresses "count must reach 3 and stay at 3" without a bare
sleep, and posted a short reply explaining why the latch alone is
insufficient. New commit `f60f214`, DCO re-checked green, waiting for the
next round.

### Spring AI #6317 — regression test for #5971

Opened a new PR with a failing regression test that encodes the expected
streaming observation stop order from #5971. The test uses a custom
`ObservationHandler` on `TestObservationRegistry` to capture every
`onStart` / `onStop` event by name, then asserts that
`spring.ai.advisor` stops before `spring.ai.chat.client` (LIFO).

Confirmed locally that on `main` the assertion fails as expected:
`spring.ai.chat.client` is at stop index 0, `spring.ai.advisor` at index 1
— the inverse of LIFO, exactly as #5971 reports. The test is marked
`@Disabled` referencing the issue so CI stays green; the next contributor
who fixes the lifecycle can simply flip the annotation off and have a
machine-verified regression guard.

The PR body also documents the second half of #5971 (`spring.ai.tool`
observations getting a null parent in streaming) and points at the
suspected boundary: `Schedulers.boundedElastic()` inside
`ToolCallingAdvisor.handleToolCallRecursion` where
`ToolCallReactiveContextHolder` is read.

Branch: `BK202503/spring-ai:GH-5971-regression-test`. DCO green.

## 2026-06-03 — Email privacy cleanup

DCO bot on #1714 surfaced that every commit on every fork carried the
personal Gmail in author + `Signed-off-by:`. Rewrote history to use the
GitHub noreply address (`199436087+BK202503@users.noreply.github.com`)
across all three remotes:

- `BK202503/bk-spring-saga` — `git filter-branch --env-filter` over all
  six commits, recreated the annotated `v0.1.0` tag (the tag *object*
  carries its own `tagger` email, which filter-branch leaves alone),
  force-pushed `main` with `--force-with-lease` and the tag with `--force`.
- `BK202503/awesome-kotlin` (PR #1125 branch) — single-commit amend with
  `--reset-author`, force-pushed.
- `BK202503/spring-modulith` (PR #1714 branch) — single-commit amend with
  `--reset-author`, also rewrote the `Signed-off-by:` line in the body
  via `sed`, force-pushed. DCO bot re-checked → SUCCESS.

GitHub commit search confirmed zero remaining hits for the old address
across all three repos.

## Patterns worth keeping for next contributions

- **Before opening a PR, search for in-flight ones**:
  `gh pr list --repo <org>/<repo> --state all --search "fix #<issue> in:title,body"`.
- **Sanity-check the fix by failing first**: add the test, watch it fail,
  then apply the fix and watch it pass. This both proves the bug and proves
  the fix.
- **Persistence-context gotcha (JPA)**: JPQL `UPDATE` / `DELETE` bypasses
  the persistence context. After such an update, call `em.flush(); em.clear();`
  before re-reading in tests, otherwise cached entities make the test
  meaningless (or falsely red).
- **Branch naming**: Spring projects use `GH-<issue>-short-slug`. Spring
  Modulith uses `GH-` prefix specifically — observed from PR #1702.
- **PR title format**: `GH-<issue> - Imperative summary` (with the dash
  pattern Spring Modulith uses). Matches `git log` of merged PRs.

## Pending decisions

- Once the awesome-kotlin PR or Spring Modulith PR lands, write a short
  follow-up note in this file with the merge date and any review notes.
- After `v0.1.0` has been live for 2–4 weeks, decide whether to invest in
  Maven Central (Sonatype namespace + signing) or stay on JitPack.
- After the saga-events-kafka design has at least one external user, revisit
  whether to upstream a `@ApplicationModuleListener(retry = ...)` design doc
  on Spring Modulith #1439.
