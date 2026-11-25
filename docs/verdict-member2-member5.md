# Alignment verdict — Member 2 (matching) & Member 5 (QA / coverage)

**Status:** ✅ **PASS** (no code changes required in this PR)
**Reviewed:** Wed Nov 25 2026
**Base:** `main` @ `012d8a7`
**Scope:** `origin/feat/matching-engine` @ `d105661` (merged as `ffac01e`, PR #6) and
`origin/member5/qa-integration-tests` @ `a91e0bb` (merged as `012d8a7`, PR #7)
**Reviewer:** Member 1 (owner of `models/`, `repository/`, `docs/`)

---

## 1. Summary of what was reviewed

| Member | Branch | Commits | Files | Net |
| --- | --- | --- | --- | --- |
| Member 2 | `feat/matching-engine` | 7 | 9 main + 7 test | `matching/` fully implemented (~1 870 lines) |
| Member 5 | `member5/qa-integration-tests` | 1 | `AGENTS.md` only | +1 line (coverage policy) |

Verification run on the merged tree: **`mvn clean verify` → BUILD SUCCESS, 280 tests, 0 failures,
0 errors**. The `≥ 85 %` JaCoCo branch gate ("All coverage checks have been met") is green.

---

## 2. Member 2 — `matching/` (Chain of Responsibility + Strategy)

### 2.1 Verdict: PASS

All contractual `TODO (Member 2)` stubs were replaced in place. No duplicate classes, no renamed
methods, no parallel interfaces — §3.1 of `AGENTS.md` is respected.

### 2.2 Layer 1 — the academic-year rule I contracted in `schema-1`

The rule I added to `docs/integration-contracts.md` §3 was implemented **verbatim**:

```java
// src/main/java/com/researchmatch/matching/EligibilityFilterHandler.java
Integer minYear = opportunity.getMinAcademicYear();
Integer studentYear = student.getAcademicYear();
if (minYear != null && (studentYear == null || studentYear < minYear)) {
    return reject(context, "Requires academic year " + minYear + " or above (student year: "
            + studentYear + ")");
}
```

- Evaluation order is exactly **email → hours → academic year → required skills**; the year check
  precedes skill checks, so a too-junior student is rejected before skills are even considered.
- A `null` `student.getAcademicYear()` is rejected (treated as "not yet reached"), as contracted.
- `minAcademicYear` is `null`-guarded, so the `1` default and the defensive `null` path both behave.
- Rejection goes through `markIneligible(reason)` + `LayerResult.STOP`; the note names **both** the
  required and the actual year, which is what makes the rejection explainable in the UI.
- The message interpolates `studentYear` directly, so a `null` year renders as `(student year: null)`.
  Slightly raw, but never throws — acceptable, see §5.

The `StudentProfile.MIN_ACADEMIC_YEAR` / `MAX_ACADEMIC_YEAR` constants I introduced are **not** used by
`matching/`; Member 2 chose a `null`-guard instead. That is correct — validation of the 1–4 range belongs
to `OpportunityBuilder.build()`, not to a scoring layer.

### 2.3 Remaining layers

| Layer | Behaviour observed | Notes |
| --- | --- | --- |
| `SemanticMatchHandler` | Offline TF-IDF + cosine over student interests/claimed/verified vs opportunity title/description. Smoothed IDF `ln((1+N)/(1+df))+1`, similarity clamped to `[0,1]`, scaled by `matching.semanticWeight` (default `0.30`). | Fully deterministic, no network. Zero-magnitude and empty-doc cases return `0.0` instead of `NaN`. Weight is read through `SystemConfigManager`, not hard-coded. |
| `EvidenceScoreHandler` | Matched/missing split over required ∪ preferred; `2.0` per verified matched skill, capped at `10.0`; recorded under `"evidence"`. | Correctly distinguishes *claimed* (counts as matched, no bonus) from *verified* (matched **and** bonus) — matches the contract wording. Case-insensitive, whitespace-trimmed, `null`-safe. |
| `ExplanationHandler` | Never mutates scores/eligibility; writes a non-blank summary + prep advice for both eligible and ineligible pairs. | For ineligible pairs it emits a neutral sentence and defers to the eligibility note, so the reason survives in `getExplanationSummary()`. |
| Strategies | `balanced` (score ↓, id ↑), `beginner-friendly` (engine pre-filters, then score ↓), `skill-strict` (missing count ↑, score ↓, id ↑). | All three copy into a new `ArrayList` and sort it — input lists are never mutated, as contracted. `null`/empty input returns a new empty list. |
| `MatchingEngine` | Assembles **eligibility → semantic → evidence → explanation**, calls `head.handle(context, null)`, builds the result with `MatchResultBuilder`. | Order and entry point are exactly as the contract specifies. Unknown/null ids throw `ResourceNotFoundException` / `InvalidRequestException`. |

### 2.4 Spot-checked edge cases

- `MatchingEngine` dedupes nothing but builds `strategies` with
  `Collectors.toUnmodifiableMap(RankingStrategy::name, ...)` — a duplicate `name()` bean would fail fast
  at startup rather than silently shadow. Good.
- `rankOpportunities` filters `MatchResult::isEligible` **before** ranking, so ineligible pairs can
  neither be returned nor occupy the `matching.maxResults` cap. Verified by
  `d105661 feat(matching): exclude ineligible pairs from rankOpportunities`.
- `candidates == null` and `limit < 0` are defended even though Spring Data returns empty lists — harmless
  belt-and-braces.

---

## 3. Member 5 — QA & coverage policy

### 3.1 Verdict: PASS, but **the deliverable is smaller than the branch name implies**

The branch `member5/qa-integration-tests` contains **one commit** (`a91e0bb`) that changes **one file**:

```diff
--- a/AGENTS.md
+++ b/AGENTS.md
+- The project must maintain **100 % line coverage with no exceptions**; strive for **100 % branch
+  coverage**, keeping the existing **≥ 85 %** gate as the enforced minimum.
```

**Findings:**

1. **No integration tests were delivered.** `AGENTS.md` §5 assigns Member 5 ownership of
   `src/test/java/com/researchmatch/integration/`. That directory **does not exist**, and no file in the
   repository matches `*Integration*`. The end-to-end coverage described in the branch name is absent —
   the merged suite is entirely unit-level tests inside `matching/`.
2. **The tests that do exist in `matching/` were authored in Member 2's PR**, not Member 5's. The single
   Member 5 commit is a policy line, so PR #7 was effectively a docs-only change.
3. **The new policy is not yet satisfied.** See §4 — line coverage is 97.2 %, not the "100 % with no
   exceptions" the added line now requires.

This is a **process finding, not a code defect**, and nothing in the merged code is wrong because of it.
I am not editing another member's package; the gap is recorded here for the group to assign.

### 3.2 Recommendation to Member 5

- Land the `integration/` package: `@SpringBootTest` flows over the seeded `DataSeeder` data covering
  student → rank → apply → pipeline transition, plus the privacy filter on `discoverCandidates`.
- Then satisfy the coverage line they just added (targets listed in §4.3).

---

## 4. Coverage reality check (contract: `≥ 85 %` branch and enforced minimum; 100 % lines aspired)

Measured with `mvn clean verify` on `012d8a7` and `target/site/jacoco/jacoco.xml`:

| Counter | Covered | Missed | Coverage |
| --- | --- | --- | --- |
| Branch | 496 | 50 | **90.8 %** ✅ (gate 85 %) |
| Line | 1342 | 39 | **97.2 %** ⚠️ (policy asks 100 %) |
| Instruction | 5373 | 151 | 97.3 % |
| Method | 411 | 21 | 95.1 % |

The gate passes. The newly added 100 % line rule does not yet.

### 4.1 Uncovered lines in Member 2's `matching/` (19 lines)

| File | Lines | Why it is uncovered |
| --- | --- | --- |
| `MatchContext.java` | 75–76 | `setTotalScore(...)` setter is never called; every path goes through `addScore(...)`. |
| `MatchingEngine.java` | 69 | Unreachable: the anonymous `link(...)` wrapper's `evaluate` delegates to the handler's own `evaluate`, which is never invoked on the wrapper. Dead code that can be deleted. |
| `MatchingEngine.java` | 142, 154 | Defensive `candidates == null` and `limit < 0` branches — not reachable through Spring Data or a non-negative config. |
| `SemanticMatchHandler.java` | 46 | `config == null` fallback in the constructor; Spring and the no-arg ctor never pass `null`. |
| `SemanticMatchHandler.java` | 77, 88, 120 | `student == null` / `opportunity == null` / `text == null` guards. Unreachable because `MatchContext` already `requireNonNull`s both. |
| `SemanticMatchHandler.java` | 168 | Zero-magnitude vector guard, not hit by the current fixtures. |
| `SkillStrictStrategy.java` | 41 | `missingSkills == null` half of the ternary. `RankingStrategyTest#nullMissingSkillsCountsAsZero` builds a real empty `LinkedHashSet` (`result(...)` maps `null` → `new LinkedHashSet<>()`), so the `null` arm never executes — a test that *looks* like it covers the branch but does not. |

### 4.2 Uncovered lines outside Member 2's and Member 5's areas (report only)

| File | Owner | Lines |
| --- | --- | --- |
| `pipeline/*` (`NewState`, `AbstractApplicationState`, `RecruitmentPipeline`, …) | Member 3 | ~76 — still `TODO (Member 3)` stubs, so the lines are placeholder throws |
| `controllers/*` (`WebController`, `ApiController`, `ResearchMatchFacade`, `OpportunityMatchView`) | Member 4 | ~52 |
| `models/Application.java` 141–142 | Member 1 (mine) | `setUpdatedAt(...)` never called — `@PrePersist`/`@PreUpdate` stamp it |
| `ResearchMatchApplication.java` 10–11 | Member 1 (mine) | `main()` / `SpringApplication.run` body |

I did **not** edit any of these. Per `AGENTS.md` §2 ownership, each is reported to its owner.

---

## 5. Open items (non-blocking)

1. **`EligibilityFilterHandler` renders a `null` year as `(student year: null)`.** Cosmetic only, but an
   "Academic year not set on your profile" message would be clearer. Owner: Member 2.
2. **`MatchingEngine.link(...)` contains an unreachable `evaluate` override.** Removing it shrinks the
   denominator of the 100 % line target for free. Owner: Member 2.
3. **`SkillStrictStrategyTest` gap** — the `null`-missing-skills branch is masked by the test helper.
   Either pass a genuinely `null` set or drop the ternary. Owner: Member 2 (tests for their component),
   coordinated with Member 5.
4. **`integration/` package not delivered.** Owner: Member 5.
5. **Docs drift:** `docs/integration-contracts.md` line 205 still annotates `strategy(...)` as
   `← IMPLEMENTED (null/blank ⇒ balanced)` while `evaluate` / `rankOpportunities` carry no such marker,
   though all three are now implemented. Cosmetic doc cleanup, owner: Member 1, to be folded into a later
   docs pass.

---

## 6. Cross-check against the integration contract

| Contract item | Expected | Observed | ✓ |
| --- | --- | --- | --- |
| Layer 1 academic-year rejection | `minYear != null && (studentYear == null \|\| studentYear < minYear)` → `markIneligible` + `STOP` | Identical | ✅ |
| Chain order | eligibility → semantic → evidence → explanation | Identical | ✅ |
| Entry point | `head.handle(context, null)` | Identical | ✅ |
| `layerName()` values | `"eligibility"`, `"semantic"`, `"evidence"`, `"explanation"` | Identical | ✅ |
| Strategy names | `"balanced"`, `"beginner-friendly"`, `"skill-strict"` | Identical | ✅ |
| `rank()` never mutates input | returns a new list | Copy-then-sort | ✅ |
| `evaluate` / `rankOpportunities` errors | `ResourceNotFoundException` / `InvalidRequestException` | Identical | ✅ |
| `matching.maxResults` cap | ≤ N results | Applied after ranking, ineligible pre-filtered | ✅ |
| `matching.semanticWeight` | read via `SystemConfigManager`, default `0.30` | `getDouble(KEY_SEMANTIC_WEIGHT, 0.30)` | ✅ |
| `matching/` never imports `controllers/` | must not | No such import | ✅ |
| Privacy: faculty-facing discovery | must exclude non-searchable students | Untouched by both; still enforced in `ResearchMatchFacade#discoverCandidates` | ✅ (N/A here) |

---

## 7. Conclusion

Both members' work **integrates cleanly with the `schema-1` model change**. Member 2's `matching/`
implementation is contract-faithful, deterministically testable, and — importantly — honours the very
rule this reviewer added to the contract one PR earlier. Member 5's contribution is a **policy line, not
a test suite**; it should be treated as an open action item, and the new 100 % line rule it introduces is
not yet met (97.2 %).

**No production code was changed in this PR.** This PR exists to record the verdict, the three defects
worth fixing in `matching/`, and the coverage gap so it can be assigned rather than silently absorbed.
