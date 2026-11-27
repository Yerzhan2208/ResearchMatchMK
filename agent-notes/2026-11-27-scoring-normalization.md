# 2026-11-27 — Scoring normalization (issues #19, #20)

**Status:** done (pending PR review)

## Summary

- Fixed #19 (incomparable scales) and #20 (zero-score ties) with a normalised weighted sum:
  `totalScore = semanticWeight * semantic + evidenceWeight * evidence`, both components in `[0, 1]`.
- Defaults changed to semantic **0.70** / evidence **0.30** (configurable, validated, fallback + warn).
- Evidence bonus stays on its `2.0`-per-skill / `10.0`-cap scale but is normalised by `MAX_BONUS` before
  weighting.
- Demo student now seeded with realistic interests + claimed skills (no faked verified evidence); one seeded
  fixture student has verified skills so the evidence layer is exercised.
- UI shows fit score as a **percentage** (`totalScore * 100` + `%`).

## Root cause (for the record)

- The displayed "demo student" (`DemoRoleSession.createDemoStudent`) was created with **empty** interests and
  skills → semantic text empty → similarity `0.0`; no verified skills → evidence `0.0` → every score `0.00`.
- Adding a **claimed** skill only feeds the semantic layer (a tiny TF-IDF delta ≈ `0.06 × 0.30 ≈ 0.02`),
  hence the observed "~0.02" bump. Only **verified** skills move evidence (and previously by `+2.0`).

## Files touched

**Member 2 (`matching/`):**
- `ScoringWeights.java` (new) — resolves + validates the two weights, fallback `0.70/0.30`, warn on invalid.
- `SemanticMatchHandler.java` — default via `ScoringWeights`, contribution `similarity * semanticWeight`.
- `EvidenceScoreHandler.java` — constructor takes `SystemConfigManager`; contribution
  `min(count*2.0,10.0)/10.0 * evidenceWeight`.
- `MatchingEngine.java` — passes its config to `EvidenceScoreHandler`.

**Member 1 (config, approved):**
- `repository/SystemConfigManager.java` — added `KEY_EVIDENCE_WEIGHT`; semantic default `0.70`, evidence `0.30`.
- `src/main/resources/system-config.json` — `semanticWeight 0.70`, `evidenceWeight 0.30`.
- `repository/SystemConfigManagerTest.java`, `SystemConfigManagerLoadingTest.java` — updated defaults.

**Member 3 (`config/`, approved):**
- `config/DataSeeder.java` — overload seeds verified skills; Mei-Ling Ho now has verified `Python`.

**Member 4 (`controllers/`,`templates/`, approved):**
- `controllers/DemoRoleSession.java` — demo student seeded with interests + claimed skills (verified stays empty).
- `templates/discover.html` — fit score rendered as `totalScore*100` + `%`.

**Docs:**
- `docs/integration-contracts.md` — defaults table, new Scoring model section, `scoring-1` change-log row.

**Tests (matching/ + affected):**
- `ScoringScaleTest.java` (new) — semantic-only, evidence-only, combined, bounds, zero-signal, ineligible,
  ranking + tie-break, and "strong semantic outranks one verified skill".
- `EvidenceScoreHandlerTest`, `SemanticMatchHandlerTest`, `MatchingEngineTest`, `TfIdfSemanticSimilarityTest`
  — updated for normalised contributions / new defaults / paired-weight stubs.
- `DemoRoleSessionTest`, `DataSeederTest` — assert seeded skills & verified fixture.

## Verification

- `mvn -o test -Dtest='EvidenceScoreHandlerTest,SemanticMatchHandlerTest,MatchingEngineTest,ScoringScaleTest,SystemConfigManagerTest,SystemConfigManagerLoadingTest,TfIdfSemanticSimilarityTest,MatchingEngineBranchCoverageTest,MatchingContractsTest'` → green.
- `mvn -o test -Dtest='DemoRoleSessionTest,DataSeederTest,WebController*,ApiController*,ConfigPackageTest,EndToEndWorkflowTest,ApplicationContextTest'` → green.
- `mvn clean test jacoco:report` → **499 tests, 0 failures**.
- `mvn verify` → **BUILD SUCCESS**, "All coverage checks have been met" (≥85% branch gate).
- `ScoringWeights`: 100% line, ~83% branch (remaining branches are the negative-weight short-circuit
  sub-conditions; the invalid/fallback paths are covered by `ScoringWeightsTest`).

## Out of scope / follow-ups

- `docs/integration-contracts.md` and `SystemConfigManager` were edited under explicit user approval on
  behalf of Member 1; group should ratify in the contract change log.
- `pom.xml` / CI untouched.
- Consider tuning `BONUS_PER_VERIFIED_SKILL`/`MAX_BONUS` later if evidence should be more/less sensitive.

## Open questions for the group

- Should the remaining seeded students also carry verified fixtures, or is one enough for the demo?
