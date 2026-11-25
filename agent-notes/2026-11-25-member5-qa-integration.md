# Member 5 — QA integration: end-to-end tests, 100% line coverage, tooling

**Date:** 2026-11-25
**Author:** Member 5 (QA / integration) with the AI agent
**Branch:** `member5/qa-integration-tests`
**Status:** done

---

## Summary

- Integrated **all member work** into `member5/qa-integration-tests`: fast-forwarded to `origin/main`
  (which already contained Member 2 matching-engine, Member 4 controllers, Member 1 models, and the
  AGENTS.md coverage rule) and merged Member 3 (`origin/member3/adapters-seeder`, a superset of
  `origin/member3/pipeline-adapters`). Zero file overlap → clean merge.
- Added `src/test/java/com/researchmatch/integration/EndToEndWorkflowTest.java`: full student→professor
  flow with audit verification, privacy enforcement, equivalence-partitioning/boundary-value tests, and
  invalid-transition tests — all through `ResearchMatchFacade` on a real `@SpringBootTest` context.
- Added targeted coverage tests and one behaviour-preserving refactor to reach **100% line coverage**.
- **Result:** `mvn verify` green, **460 tests**, **line 100.00% (1699/1699)**, **branch 94.36%**
  (gate ≥ 85%).
- Added a coverage **dashboard tool** (`scripts/`) and this `agent-notes/` folder.

---

## 1. Branch integration (what was merged where)

At the start of the session `origin/main` was checked and the only branches **not** in main were the two
Member 3 branches.

| Branch | Owner | State | Action |
| --- | --- | --- | --- |
| `origin/main` | — | ahead of local `member5` (contained PR #6 matching-engine, PR #7 our AGENTS.md commit) | fast-forwarded local branch to it |
| `origin/feat/matching-engine` | Member 2 | already merged into main (PR #6) | — |
| `origin/member4/controllers-facade-ui` | Member 4 | already merged into main | — |
| `origin/member1/opportunity-min-academic-year` | Member 1 | already merged into main | — |
| `origin/member3/pipeline-adapters` | Member 3 | subset (ancestor) of `adapters-seeder` | covered by the merge below |
| `origin/member3/adapters-seeder` | Member 3 | superset of `pipeline-adapters` | **merged** (`--no-ff`) |

The two merged sources touched **disjoint** files (main changed only `matching/` + `AGENTS.md`; Member 3
changed only `adapters/`, `pipeline/`, `config/DataSeeder.java`, `factory/MatchResultBuilder.java`), so
there were no conflicts. A dry-run merge in a throwaway worktree confirmed a green build before merging.

> Only `origin/member3/adapters-seeder` was merged because it fully contains `origin/member3/pipeline-adapters`.

---

## 2. End-to-end test suite

File: `src/test/java/com/researchmatch/integration/EndToEndWorkflowTest.java` (`@SpringBootTest`).

Design: multiple **independent** `@Test`s, each creating uniquely-identified data (UUID e-mails) so they
never collide. The DB is seeded by `DataSeeder`, so tests never assume an empty DB. All student/faculty
interactions go through `ResearchMatchFacade`; assertions use the real repositories for the audit trail.

| Test | Verifies |
| --- | --- |
| `fullFlow_importSkills_rank_match_apply_and_lifecycle_withAudit` | Import GitHub skills (offline deterministic adapter; `java-wizard` → Java) → skills promoted to `verifiedSkills` → `recommend` returns ranked, eligible results each with a non-blank explanation → `submitApplication` → `shortlist` → `scheduleInterview` → `accept` → exactly **4** `APPLICATION_STATUS_CHANGED` `AuditEvent`s with correct `from→to` details. |
| `discoverCandidates_neverReturnsStudentsNotSearchableByFaculty` | A default student (`searchableByFaculty=false`) and an opted-in student both apply; `discoverCandidates` contains the opted-in one and **never** the non-searchable one. |
| `hoursEquivalencePartitions_andBoundaries` | Student hours `required-1` (ineligible), `==required` (eligible), `required+1` (eligible). |
| `academicYearEquivalencePartitions_andBoundaries` | `minAcademicYear` boundaries: `min-1` ineligible, `==min` eligible, `+1` eligible, and `null` student year ineligible. |
| `invalidTransitions_throwAndDoNotChangeStateOrAudit` | `interview` from NEW, `accept` from NEW, `shortlist`/`reject` after ACCEPTED all raise `IllegalStateTransitionException`; DB status unchanged and **no** extra audit rows written. |
| `recommend_supportsEveryStrategy_andRejectsUnknown` | `balanced`, `beginner-friendly`, `skill-strict`, `null`, blank all work; an unknown strategy raises `InvalidRequestException`. |

---

## 3. Coverage work (→ 100% line)

Baseline of the merged tree was **line 98.06% / branch 90.75%** with no integration tests. The E2E suite
added meaningful branch coverage; targeted tests then closed the remaining line gaps.

### New test files added (no teammate test files were edited)

| File | Covers |
| --- | --- |
| `integration/EndToEndWorkflowTest.java` | end-to-end flows (see §2) |
| `ResearchMatchApplicationTest.java` | `main()` bootstrap (non-web, lazy context, run on a background thread) |
| `matching/MatchingPlumbingCoverageTest.java` | `MatchContext` setters/totals/null verified skills, `SkillStrictStrategy` null missing-skills, `SemanticMatchHandler` null-config fallback |
| `matching/MatchingEngineBranchCoverageTest.java` | null repository result, negative result cap, chain-head `layerName()` delegation |
| `matching/SemanticMatchHandlerNullGuardTest.java` | private `studentText`/`opportunityText`/`tokenize` null guards + zero-magnitude guard (the latter via a list that reports non-empty but yields nothing) |
| `pipeline/AbstractApplicationStateCoverageTest.java` | deny-by-default transitions for every state + base `describe()` |
| `adapters/AdapterBranchCoverageTest.java` | GitHub offline hash rule, null repo-name fallback, blank-after-URL handle, null handle, `titleCase` empty guards |
| `controllers/ResearchMatchFacadeBranchCoverageTest.java` | `getOpportunity` null/unknown id, `importSkills` null verified-skills + null imported element, `toEvidence` null/empty |
| `controllers/WebControllerBranchCoverageTest.java` | discover error paths (domain + unsupported), missing opportunity, board null handling, null role/action branches, blank CSV |
| `controllers/ApiControllerBranchCoverageTest.java` | null request body guard (HTTP + direct call) |
| `models/ApplicationCoverageTest.java` | `setUpdatedAt`, identity equality self/type branches |

Some defensive guards are only reachable by calling a private helper directly (narrow reflection) —
documented in each test. These are legitimate, isolated coverage helpers; no production behaviour is
asserted differently.

### Production change (outside Member 5's area — flagged for the group)

`src/main/java/com/researchmatch/pipeline/AbstractApplicationState.java` (Member 3's file) was changed
**minimally and behaviour-preservingly**:

```java
// before: JaCoCo cannot count the unreachable instruction after a throwing call
return deny("shortlist");
// after: deny returns the exception; the caller throws it, so the line is coverable
throw deny("shortlist");
```

`deny(String)` now returns an `IllegalStateTransitionException` instead of throwing it. Behaviour is
identical (every disallowed transition still throws the same exception with the same message); only the
control-flow shape changed so JaCoCo can count the line. **Member 3 / the group should review this.**

---

## 4. Final verification

Run on the merged tree with **JDK 17** (`JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`;
note: the shell default `java` is 27, which is not the project target).

```bash
mvn clean test jacoco:report     # 460 tests, 0 failures
mvn verify                       # "All coverage checks have been met." BUILD SUCCESS
```

| Metric | Result | Policy |
| --- | --- | --- |
| Line coverage | **100.00% (1699/1699)** | 100% required (AGENTS.md §5) |
| Branch coverage | **94.36% (653/692)** | ≥ 85% gate (enforced by `mvn verify`) |
| Tests | **460**, 0 failures | — |

---

## 5. Tooling: coverage dashboard

New, dependency-free helpers (no Maven/app changes):

- `scripts/coverage.py` — parses `target/site/jacoco/jacoco.csv`, prints a terminal summary (overall %
  with PASS/FAIL badges, per-class table sorted by uncovered lines/branches), and can:
  - `--run` run `mvn clean test jacoco:report` first;
  - `--open` open the JaCoCo HTML report;
  - `--html` write a self-contained `target/site/jacoco-dashboard.html`;
  - `--class <substr>` focus one class;
  - `--fail-line N` / `--fail-branch N` exit non-zero below a threshold (usable later in CI).
- `scripts/coverage.sh` / `scripts/coverage.bat` — cross-platform wrappers.

When new functionality is added, just run the suite and open the dashboard: classes with uncovered
lines/branches are flagged `gap`, so it is obvious where tests are still needed. The built-in
`target/site/jacoco/index.html` remains the line-by-line source view.

---

## 6. Follow-ups / open questions for the group

1. **Review the `AbstractApplicationState` refactor** (§3). If the group prefers `return deny(...)`,
   the alternative is a JaCoCo class-level exclusion, which we rejected to keep real coverage.
2. **CI line gate** was intentionally **not** changed (per instruction). If desired later, add
   `scripts/coverage.py --fail-line 100` as a CI step, or a second JaCoCo `check` rule on `LINE`.
3. **Remaining branch coverage (94.36%)** — the uncovered branches are defensive/null-safety paths some of
   which are only reachable via reflection or are provably dead. If the group wants a higher branch
   number, coordinate with the owning members before adding reflective tests.
4. **`agent-notes/` convention** — introduced here; see `agent-notes/README.md`. Future sessions should
   add one note per task and update the index table there.
