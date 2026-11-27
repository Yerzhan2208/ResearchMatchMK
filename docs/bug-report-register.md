# Bug report register — ResearchMatch

**Course:** CS3383 AI for Software Engineering — group project
**Report compiled:** Fri 27 Nov 2026
**Prepared by:** Member 1 (owner of `models/`, `repository/`, `config/` exceptions, `docs/`)
**Method:** aligned with the Week 07 tutorial (*Debugging Terminal Snake* → *Reproduce a bug → Issue →
reviewed PR → merged fix*), using the lecture bug-report fields from slide 15
(**Title / Situation / Severity / Steps to reproduce / Expected vs Actual**).

Reference: `CS3383_Week07_Tutorial (1).pptx`, slides 15 (bug-report fields), 21–23 (create and assign the
Issue), 24 (branch per Issue), 25–26 (failing regression test **before** the fix), 29–32 (PR with
`Closes #N`, requested reviewer), 33 (merge closes the Issue).

---

## 0. Headline status

| Measure | Value |
| --- | --- |
| Issues opened against this repository | **14** |
| Issues closed | **14 / 14 (100 %)** |
| Of which closed by a merged PR carrying `Closes #N` | **13** |
| Of which closed by a PR that stated the fix but carried no `Closes` trailer | **1** (#18 — see §5) |
| Pull requests merged | **18** (22 opened) |
| Fix branches created | **6** (`fix/…`, `refactor/…`) |
| Suspected defects retracted during investigation before filing | **2** (see §5.4) |
| Verification on `main` @ `533ba8b` | `mvn clean verify` — **647 tests, 0 failures, 0 errors** |
| Line coverage | **100 %** (1857 / 1857) |
| Branch coverage | **96.26 %** (772 / 802), gate ≥ 85 % |

Every defect listed here was reproduced before it was reported, and re-tested after the fix was merged.

---

## 1. How the reports were produced (lecture process applied)

The tutorial's pipeline was followed for every defect:

1. **Reproduce** the failure from a known starting state and record the exact inputs.
2. **Report** it as a GitHub Issue using the lecture fields (title, situation, severity, STR, expected vs actual).
3. **Assign** a fixer — the member who owns the affected package per `AGENTS.md` §2.
4. **Branch** off `main`, e.g. `fix/adapters-issue-21-25`.
5. **Failing regression test first**, then the minimal fix; unrelated defects left untouched.
6. **PR to `main`** with `Closes #N`, reviewed by a second member.
7. **Merge closes the Issue**; local `main` pulled and the suite re-run.

Two deliberate deviations from the tutorial, both caused by branch protection on `main` ("Changes must be
made through a pull request"; required check `build`):

- **No direct commits to `main`.** Even one-line documentation fixes went through a PR (#28).
- **`Closes #N` could not always carry the whole fix.** Because a PR is opened against `main`, a bug found
  *during* a fix cannot be closed by the PR that fixes it. Those cases are recorded in §5.

### Severity scale used

The tutorial asks for *minor / normal / major / critical*. We mapped it to this project as:

| Severity | Project meaning |
| --- | --- |
| **Critical** | Violates the hard privacy rule (`AGENTS.md` §4), or destroys identity/consent data irreversibly |
| **Major** | Silently produces wrong results for normal use, or exposes data to the wrong audience |
| **Normal** | Incorrect but recoverable behaviour, or a wrong HTTP contract |
| **Minor** | Cosmetic, maintenance, or tooling-only |

**No defect in this register reached Critical severity** — the privacy rule held throughout (§6). #17 (silent
`verifiedSkills` loss) was the nearest call and is rated Major rather than Critical because the field is
**re-derivable** by re-importing, and nothing else was destroyed.

---

## 2. Issue register

All 14 Issues, in the order they were filed. `PR` is the pull request that closed the Issue.

| # | Title | Severity | Filed by | Assignee (owner) | Fix PR | Merged |
| --- | --- | --- | --- | --- | --- | --- |
| [#16](#issue-16) | Faculty candidates endpoint has no role check | Major | Member 1 | `0xZgetsu` (Member 4) | [#34](https://github.com/Yerzhan2208/ResearchMatchMK/pull/34) | 27 Nov 06:21 |
| [#17](#issue-17) | Saving the profile silently wipes imported `verifiedSkills` | Major | Member 1 | `0xZgetsu` (Member 4) | [#34](https://github.com/Yerzhan2208/ResearchMatchMK/pull/34) | 27 Nov 06:21 |
| [#18](#issue-18) | Profile form silently revokes `searchableByFaculty` consent by omission | Major | Member 1 | `0xZgetsu` (Member 4) | [#34](https://github.com/Yerzhan2208/ResearchMatchMK/pull/34) | 27 Nov 06:21 |
| [#19](#issue-19) | Semantic and evidence layers use incomparable score scales (0.30 vs 10.0) | Major | Member 1 | `m11ntt` (Member 2) | [#35](https://github.com/Yerzhan2208/ResearchMatchMK/pull/35) | 27 Nov 06:28 |
| [#20](#issue-20) | `totalScore` has no base term, so equally-eligible results all tie at 0.0 | Major | Member 1 | `m11ntt` (Member 2) | [#35](https://github.com/Yerzhan2208/ResearchMatchMK/pull/35) | 27 Nov 06:28 |
| [#21](#issue-21) | OpenAlex display names are title-cased, mangling acronyms and proper nouns | Normal | Member 1 | `Yerzhan2208` (Member 1) | [#36](https://github.com/Yerzhan2208/ResearchMatchMK/pull/36) | 27 Nov 06:36 |
| [#22](#issue-22) | Re-importing skills duplicates `SkillEvidence` rows without bound | Normal | Member 1 | `0xZgetsu` (Member 4) | [#34](https://github.com/Yerzhan2208/ResearchMatchMK/pull/34) | 27 Nov 06:21 |
| [#23](#issue-23) | Role switcher does not gate the professor view | Normal | Member 1 | `0xZgetsu` (Member 4) | [#34](https://github.com/Yerzhan2208/ResearchMatchMK/pull/34) | 27 Nov 06:21 |
| [#24](#issue-24) | Invalid role parameter returns HTTP 500 instead of 400 | Normal | Member 1 | `0xZgetsu` (Member 4) | [#34](https://github.com/Yerzhan2208/ResearchMatchMK/pull/34) | 27 Nov 06:21 |
| [#25](#issue-25) | GitHub handle parsing keeps query strings and fragments from pasted URLs | Normal | Member 1 | `Yerzhan2208` (Member 1) | [#36](https://github.com/Yerzhan2208/ResearchMatchMK/pull/36) | 27 Nov 06:36 |
| [#26](#issue-26) | Coverage tooling: documented path is wrong and the dashboard exits 1 on success | Minor | Member 1 | `rauaan` (Member 5) | [#28](https://github.com/Yerzhan2208/ResearchMatchMK/pull/28) | 27 Nov 05:16 |
| [#27](#issue-27) | `round(double)` is duplicated in both adapters | Minor | Member 1 | `iamoidn` (Member 3) | [#31](https://github.com/Yerzhan2208/ResearchMatchMK/pull/31) | 27 Nov 05:33 |
| [#29](#issue-29) | Semantic fit score only rewards literal word overlap, so non-NLP skills score 0.00 | Major | Member 5 | `Yerzhan2208` (Member 1) | [#30](https://github.com/Yerzhan2208/ResearchMatchMK/pull/30) | 27 Nov 05:43 |
| [#32](#issue-32) | Fit score is unintuitive (all skills match still ~0.28) and import-skills does not affect the score | Major | Member 5 | `m11ntt` (Member 2) | [#35](https://github.com/Yerzhan2208/ResearchMatchMK/pull/35) | 27 Nov 05:46 |

**By severity:** 7 Major, 5 Normal, 2 Minor, 0 Critical.
**By area:** `controllers/` 6 (#16, #17, #18, #22, #23, #24) · `matching/` 4 (#19, #20, #29, #32) ·
`adapters/` 3 (#21, #25, #27) · tooling 1 (#26).
**By owner:** Member 4 — 6 · Member 2 — 3 · Member 1 — 2 · Member 3 — 1 · Member 5 — 1.

---

## 3. Individual reports

Each entry uses the lecture fields. **Steps** are the exact ordered inputs; **Expected** is the required
behaviour from the integration contract, **Actual** is what the code did before the fix.

### <a id="issue-16"></a>Issue #16 - [GitHub #16](https://github.com/Yerzhan2208/ResearchMatchMK/issues/16)

| Field | Content |
| --- | --- |
| **Title** | Faculty candidates endpoint has no role check |
| **Situation** | ResearchMatch `0.1.0-SNAPSHOT`, Spring Boot 3.3.5, Java 17, Windows 10/11, commit `97e3ee6`. Component: `controllers/ApiController`. |
| **Severity** | Major (enables enumeration by the wrong audience). Not Critical: the facade's privacy filter held — only opt-in students were ever returned. |
| **Steps to reproduce** | 1. `GET /api/session` → `{"role":"STUDENT"}`. 2. `GET /api/faculties/1/candidates`. |
| **Expected result** | The endpoint is scoped to the acting faculty, or refused in a student session — as every other actor-scoped endpoint does via `session.currentStudent()` / `session.currentFaculty()`. |
| **Actual result** | Returned the candidate list for any `facultyId` in the path, from a `STUDENT` session. |
| **Assignment** | `0xZgetsu` — owner of `controllers/`. |
| **Fix** | `50a134c fix(api): scope candidate discovery to the demo faculty` — `PROFESSOR` role required, otherwise `403`. |
| **Status** | **Closed** by PR #34. |

### <a id="issue-17"></a>Issue #17 - [GitHub #17](https://github.com/Yerzhan2208/ResearchMatchMK/issues/17)

| Field | Content |
| --- | --- |
| **Title** | Saving the profile silently wipes imported `verifiedSkills` |
| **Situation** | As #16. Component: `controllers/WebController` → `ResearchMatchFacade#saveStudent`. |
| **Severity** | Major — silent data loss; changes match scores with no error. Not Critical: `verifiedSkills` is re-derivable by re-importing. |
| **Steps to reproduce** | 1. `/profile` → import skills from GitHub (writes `verifiedSkills`). 2. Change only the *major* field and save. |
| **Expected result** | The edit applies to the fields the form owns; existing verified skills are preserved. |
| **Actual result** | The controller built a new detached `StudentProfile`, copied the stale `existing.getVerifiedSkills()`, then overwrote the row — the import was lost. |
| **Assignment** | `0xZgetsu`. |
| **Fix** | `cf8b331 fix(profile): preserve consent and verified evidence on save` — the loaded entity is mutated instead of replaced. |
| **Regression test** | `ProfilePersistenceTest#savesPartialProfileWithoutOverwritingVerifiedSkillsOrImplicitlyChangingConsent` — seeds `verifiedSkills = {Java}`, saves only the *major*, and asserts the skill survives. |
| **Status** | **Closed** by PR #34. |

### <a id="issue-18"></a>Issue #18 - [GitHub #18](https://github.com/Yerzhan2208/ResearchMatchMK/issues/18)

| Field | Content |
| --- | --- |
| **Title** | Profile form silently revokes `searchableByFaculty` consent by omission |
| **Situation** | As #16. Component: `controllers/WebController`. |
| **Severity** | Major — withdraws a privacy setting without the student acting. |
| **Steps to reproduce** | 1. Student opts in (`searchableByFaculty = true`). 2. Submit the profile form with the checkbox absent (unchecked or omitted). |
| **Expected result** | Only an explicit change alters consent; an unchanged form leaves it as it was. |
| **Actual result** | Bound with `defaultValue = "false"`, so an omitted checkbox was indistinguishable from "explicitly false" and consent was revoked. |
| **Note on the hard privacy rule** | This **did not** violate `AGENTS.md` §4: the default stayed `false` at entity, constructor and column level, and nothing defaulted it to `true`. The defect ran the other way — consent could be *lost* by omission. |
| **Assignment** | `0xZgetsu`. |
| **Fix** | `cf8b331` — the checkbox is now a tri-state `Boolean`; `if (searchableByFaculty != null)` applies a change only on an explicit value. |
| **Regression test** | Same test — an omitted checkbox keeps consent `true`; `"false"` explicitly revokes it. |
| **Status** | **Closed** (PR #34). ⚠️ See §5 — PR #34 carried no `Closes` trailer and did not reference #18. |

### <a id="issue-19"></a>Issue #19 - [GitHub #19](https://github.com/Yerzhan2208/ResearchMatchMK/issues/19)

| Field | Content |
| --- | --- |
| **Title** | Semantic and evidence layers use incomparable score scales (0.30 vs 10.0) |
| **Situation** | As #16. Component: `matching/SemanticMatchHandler` + `matching/EvidenceScoreHandler`. |
| **Severity** | Major — the ranking degenerates to "count verified skills". |
| **Steps to reproduce** | 1. Build two eligible results: **A** with similarity `1.0` and no verified skills; **B** with similarity `0.0` and one verified skill. 2. Compare `totalScore`. |
| **Expected result** | A strong semantic match can outrank a single verified skill — the layers sit on a comparable scale. |
| **Actual result** | `B` (2.0) outranked `A` (0.30). Semantic contribution maxed at `1.0 × 0.30 = 0.30` while the evidence bonus alone reached `BONUS_PER_VERIFIED_SKILL = 2.0`, `MAX_BONUS = 10.0` — a ~33× mismatch. |
| **Assignment** | `m11ntt` — owner of `matching/`. |
| **Fix** | `932a566 fix(matching): normalize semantic and evidence scoring scales` — the evidence bonus is normalised to `[0,1]` (`rawBonus / MAX_BONUS`) and the semantic weight raised to `0.70`, so both layers share a 0–1 scale. |
| **Status** | **Closed** by PR #35. |

### <a id="issue-20"></a>Issue #20 - [GitHub #20](https://github.com/Yerzhan2208/ResearchMatchMK/issues/20)

| Field | Content |
| --- | --- |
| **Title** | `totalScore` has no base term, so equally-eligible results all tie at 0.0 |
| **Situation** | As #16. Component: `matching/` chain. |
| **Severity** | Major — the primary user-facing feature returns an unordered list. |
| **Steps to reproduce** | 1. Run the app. 2. `GET /api/discover`. |
| **Expected result** | Eligible results are ordered by fit. |
| **Actual result** | All three results returned `totalScore: 0.0`, ordered `1, 3, 6` — ascending `opportunityId`, i.e. the tie-breaker, not a ranking. |
| **Caveat recorded in the Issue** | Partly an artefact of the seeded demo student, who had empty `interests`, `claimedSkills` and `verifiedSkills`. The missing base term is nonetheless a real design gap for any student with no overlap. |
| **Assignment** | `m11ntt` (primary); seeding crossover to Member 3 noted in the Issue. |
| **Fix** | `932a566` plus `c3d9fc4 feat(demo): seed demo student skills and show fit score as a percentage`. |
| **Status** | **Closed** by PR #35. |

### <a id="issue-21"></a>Issue #21 - [GitHub #21](https://github.com/Yerzhan2208/ResearchMatchMK/issues/21)

| Field | Content |
| --- | --- |
| **Title** | OpenAlex display names are title-cased, mangling acronyms and proper nouns |
| **Situation** | As #16. Component: `adapters/OpenAlexPublicationAdapter#areaOf`. |
| **Severity** | Normal — wrong display text and wrong persisted keys, but no crash. |
| **Steps to reproduce** | 1. Enable OpenAlex (`researchmatch.openalex.enabled=true`). 2. Import an author whose `primary_topic.display_name` is `nlp`, `covid-19` or `phd-level analysis`. |
| **Expected result** | The curated display name is preserved: `NLP`, `COVID-19`, `PhD-level analysis`. |
| **Actual result** | `Nlp`, `Covid-19`, `Phd-level analysis` — `titleCase` upper-cased only the first character. The corrupted spelling reached `SkillEvidence.skillName`, the profile/faculty pages, and the matching key. |
| **Assignment** | `0xZgetsu` → reassigned to `Yerzhan2208`. |
| **Fix** | `861a2d6 fix(adapters): preserve OpenAlex display-name casing` — areas are trimmed and passed through verbatim, including the `concepts` fallback; `titleCase` remains on `GitHubSkillAdapter` where lowercase topics genuinely need it. |
| **Regression tests** | `AdapterInputHandlingTest` — `NLP`, `COVID-19`, `PhD-level analysis`, `e-Government`, `iNaturalist taxonomy`, `3D reconstruction`. **The old tests had pinned the bug** (`titleCasesLowercaseOpenAlexTags` asserted the mangled output); both were corrected. |
| **Status** | **Closed** by PR #36. |

### <a id="issue-22"></a>Issue #22 - [GitHub #22](https://github.com/Yerzhan2208/ResearchMatchMK/issues/22)

| Field | Content |
| --- | --- |
| **Title** | Re-importing skills duplicates `SkillEvidence` rows without bound |
| **Situation** | As #16. Component: `controllers/ResearchMatchFacade#importSkills`. |
| **Severity** | Normal — data and UX defect; scoring unaffected because `verifiedSkills` is a `Set`. |
| **Steps to reproduce** | 1. `/profile` → **Import from GitHub**. 2. Press it again. |
| **Expected result** | The same evidence is not stored twice. |
| **Actual result** | Every row was duplicated per import (`saveAll` with no existing-row lookup); the duplicates were visible in the rendered evidence list. |
| **Assignment** | `0xZgetsu`, with a note that a new repository query may need Member 1 (`repository/` is frozen to its owner). |
| **Fix** | `cf8b331` — existing rows are keyed by `(student, skill, source)` and upserted rather than re-inserted. |
| **Status** | **Closed** by PR #34. |

### <a id="issue-23"></a>Issue #23 - [GitHub #23](https://github.com/Yerzhan2208/ResearchMatchMK/issues/23)

| Field | Content |
| --- | --- |
| **Title** | Role switcher does not gate the professor view |
| **Situation** | As #16. Component: `controllers/WebController#professor`. |
| **Severity** | Normal — the demo's role switch was cosmetic on that page. |
| **Steps to reproduce** | 1. Switch the demo role to `STUDENT`. 2. `GET /professor`. |
| **Expected result** | The route honours the current role. |
| **Actual result** | The professor page rendered regardless; `isStudentView()` was used only for a view flag and the landing redirect. |
| **Assignment** | `0xZgetsu`. |
| **Fix** | `50a134c` — `professor(...)` returns `redirect:/discover` when the session is in student view. |
| **Status** | **Closed** by PR #34. |

### <a id="issue-24"></a>Issue #24 - [GitHub #24](https://github.com/Yerzhan2208/ResearchMatchMK/issues/24)

| Field | Content |
| --- | --- |
| **Title** | Invalid role parameter returns HTTP 500 instead of 400 |
| **Situation** | As #16. Component: `controllers/ApiController#switchRole`. |
| **Severity** | Normal — misreports client error as server fault. |
| **Steps to reproduce** | `POST /api/session/role?role=admin` (and with `role` omitted). |
| **Expected result** | `400 Bad Request` with a message naming the valid roles. |
| **Actual result** | `500 Internal Server Error` — raw `Role.valueOf(...)` on unvalidated input surfaced `IllegalArgumentException`. |
| **Assignment** | `0xZgetsu`. |
| **Fix** | `50a134c` plus the MVC/API error advice: `ApiController#handleIllegalArgument` and `WebController`'s `catch (IllegalArgumentException)` now map the parse failure to `400`. |
| **Status** | **Closed** by PR #34. |

### <a id="issue-25"></a>Issue #25 - [GitHub #25](https://github.com/Yerzhan2208/ResearchMatchMK/issues/25)

| Field | Content |
| --- | --- |
| **Title** | GitHub handle parsing keeps query strings and fragments from pasted URLs |
| **Situation** | As #16. Component: `adapters/GitHubSkillAdapter#normaliseHandle`. |
| **Severity** | Normal — a URL copied from the browser silently matched nothing. |
| **Steps to reproduce** | Call `importEvidence("https://github.com/octocat?tab=repositories")`, and the `#readme` and `/repos` variants. |
| **Expected result** | The bare username `octocat`. |
| **Actual result** | `octocat?tab=repositories` — offline it matched no fixture (an empty result for a handle the user believes is valid); online it went out as a malformed request path. A URL with no username (`https://github.com/?tab=repositories`) was passed through as `?tab=repositories`. |
| **Assignment** | `iamoidn` → reassigned to `Yerzhan2208`. |
| **Fix** | `3ab943a fix(adapters): strip query strings from pasted GitHub URLs` — cut at the first `/`, `?` or `#` after the `github.com/` segment; a leftover bare host or scheme is rejected with `InvalidRequestException`. |
| **Regression tests** | `AdapterInputHandlingTest` — a 15-row URL table plus 4 rejection cases, and an end-to-end check that the parsed handle reaches the mock API as `/users/octocat/repos` with no query string. |
| **Status** | **Closed** by PR #36. |

### <a id="issue-26"></a>Issue #26 - [GitHub #26](https://github.com/Yerzhan2208/ResearchMatchMK/issues/26)

| Field | Content |
| --- | --- |
| **Title** | Coverage tooling: documented path is wrong and the dashboard exits 1 on success |
| **Situation** | As #16. Component: `AGENTS.md` §5 and `scripts/coverage.py`. |
| **Severity** | Minor — tooling only; no product impact. |
| **Steps to reproduce** | 1. From the repo root, run `cmd /c coverage.bat`. 2. Check the exit code of `python scripts\coverage.py`. |
| **Expected result** | The documented command resolves; the dashboard exits `0` when its gated counters pass. |
| **Actual result** | `'coverage.bat' is not recognized` (the file is `scripts/coverage.bat`), and the dashboard exited `1` while printing `PASS` twice, because per-class "gap" rows counted as failures. `bash scripts/coverage.sh` was also unusable on this machine (WSL has no distro). |
| **Assignment** | `rauaan` — QA / tooling owner. |
| **Fix** | `aabe96d docs(tooling): correct coverage script paths and note Windows usage`. |
| **Known limitation** | The dashboard still exits non-zero on healthy runs; `AGENTS.md` §5 documents gap rows as *informational*. Use the `.bat`/Python entry points and read the printed counters. Recorded here rather than re-opened, since the documented behaviour is explicit. |
| **Status** | **Closed** by PR #28. |

### <a id="issue-27"></a>Issue #27 - [GitHub #27](https://github.com/Yerzhan2208/ResearchMatchMK/issues/27)

| Field | Content |
| --- | --- |
| **Title** | `round(double)` is duplicated in both adapters |
| **Situation** | As #16. Component: `adapters/GitHubSkillAdapter` and `adapters/OpenAlexPublicationAdapter`. |
| **Severity** | Minor — maintenance risk: two copies of a formatting rule drift apart silently. |
| **Steps to reproduce** | Read both adapters; the helper is byte-identical in each. |
| **Expected result** | One definition of the confidence rounding policy, with tests. |
| **Actual result** | Two private copies, untested as a policy. |
| **Assignment** | `iamoidn` — owner of `adapters/`. |
| **Fix** | `1c40e74 refactor(adapters): share one confidence rounding policy` — moved to `ImportedEvidence.confidenceFrom(double)`, the record that owns the `confidence ∈ [0,1]` invariant. Behaviour unchanged (existing `0.67`/`0.33`/`0.17` assertions still pass). |
| **Regression tests** | `ImportedEvidenceConfidenceTest` — 18 cases: rounding ties (`0.125 → 0.13`, `0.996 → 1.00`), clamp bounds, constructor invariant, cross-adapter agreement. |
| **Status** | **Closed** by PR #31. |

### <a id="issue-29"></a>Issue #29 - [GitHub #29](https://github.com/Yerzhan2208/ResearchMatchMK/issues/29)

| Field | Content |
| --- | --- |
| **Title** | Semantic fit score only rewards literal word overlap, so non-NLP skills score 0.00 |
| **Situation** | As #16. Component: `matching/SemanticMatchHandler` + `matching/TfIdfSemanticSimilarity`. |
| **Severity** | Major — relevant opportunities scored 0.00 for most seeded students. |
| **Steps to reproduce** | Against the seeded data, score each student against a clearly relevant opportunity (semantic contribution at weight 0.30). |
| **Expected result** | Every clearly relevant pairing scores above zero. |
| **Actual result** | Reported pairs — Arjun (Machine Learning) vs *Transformer Models*: **0.000**; Daniel (HCI) vs *Usability Study of Student Portals*: **0.000**; Mei-Ling (NLP) vs *NLP for Course Feedback*: 0.046. `TfIdfSemanticSimilarity` has no synonym stemming and fragments multi-word interests; the opportunity text excluded `requiredSkills`/`preferredSkills`. |
| **Assignment** | `Yerzhan2208`. |
| **Fix** | `1ba0170 fix(matching): reward skill/synonym overlap in semantic fit score` — synonym expansion plus skill text in the opportunity document. |
| **Status** | **Closed** by PR #30. |

### <a id="issue-32"></a>Issue #32 - [GitHub #32](https://github.com/Yerzhan2208/ResearchMatchMK/issues/32)

| Field | Content |
| --- | --- |
| **Title** | Fit score is unintuitive (all skills match still ~0.28) and import-skills does not affect the score |
| **Situation** | As #16. Components: `matching/SemanticMatchHandler`, `matching/EvidenceScoreHandler`, `src/main/resources/system-config.json`. |
| **Severity** | Major — the score is the headline user-facing number. |
| **Steps to reproduce** | 1. Match an opportunity whose skills the student all holds. 2. Read the fit score. 3. Import skills, re-check. |
| **Expected result** | A full skill match reads as a high fit, and importing evidence can raise it. |
| **Actual result** | ~0.28 at best. Layer 1 can contribute at most `cosine × 0.30`; Layer 2 awards `2.0` per verified skill capped at `10.0` but counts **only verified** skills (`matched.stream().filter(verified::contains)`), so claimed-only matches earned 0 and seeded students earned 0. |
| **Root cause overlap** | The same scale mismatch as #19, from the user-visible side. |
| **Assignment** | `m11ntt`. |
| **Fix** | `932a566` (normalisation + weight `0.70`) and `c3d9fc4` (seeded demo skills, fit shown as a percentage). |
| **Status** | **Closed** by PR #35. |

---

## 4. Fix pull requests

| PR | Branch | Closes | Author | Commits | Merged |
| --- | --- | --- | --- | --- | --- |
| [#28](https://github.com/Yerzhan2208/ResearchMatchMK/pull/28) | `fix/issue-26-coverage-tooling-docs` | #26 | Member 5 | `aabe96d` | 27 Nov 05:16 |
| [#30](https://github.com/Yerzhan2208/ResearchMatchMK/pull/30) | `fix/semantic-synonyms-skill-text` | #29 | Member 5 | `1ba0170` | 27 Nov 05:43 |
| [#31](https://github.com/Yerzhan2208/ResearchMatchMK/pull/31) | `refactor/shared-evidence-confidence` | #27 | Member 1 | `1c40e74` | 27 Nov 05:33 |
| [#34](https://github.com/Yerzhan2208/ResearchMatchMK/pull/34) | `fix/member4-assigned-issues` | #16, #17, #22, #23, #24 | Member 4 | `50a134c`, `cf8b331` | 27 Nov 06:21 |
| [#35](https://github.com/Yerzhan2208/ResearchMatchMK/pull/35) | `fix/scoring-normalization` | #19, #20, #32 | Member 2 | `932a566`, `9dc609a`, `c3d9fc4`, `8357d61`, `06971b8` | 27 Nov 06:28 |
| [#36](https://github.com/Yerzhan2208/ResearchMatchMK/pull/36) | `fix/adapters-issue-21-25` | #21, #25 | Member 1 | `861a2d6`, `3ab943a` | 27 Nov 06:36 |

Each PR's body carried the lecture-aligned description: what broke, the reproduction, the expected vs actual
result, the tests added, the verification output, and an explicit note on ownership so the right member
reviewed it.

### Verification after every merge

| Merge | Tests | Line | Branch |
| --- | --- | --- | --- |
| Baseline at the start of this register (`97e3ee6`) | 484 | 100 % | 94.75 % |
| After PR #31 (`998c4a2`) — measured | 502 | 100 % | 94.75 % |
| **Current `main` @ `533ba8b`** — measured | **647** | **100 %** | **96.26 %** |

The per-merge counts between these points were not recorded, so only measured values are listed rather than
interpolated ones. Coverage was measured with `python scripts\coverage.py` after `mvn clean verify`.

Branch coverage **rose** across the fixes: the removed `OpenAlexPublicationAdapter.titleCase` carried an
unreachable guard that only reflection had been able to reach, so deleting it removed a dead branch.

---

## 5. Honest deviations from the tutorial process

Recorded because a bug report that only lists successes is not a report.

1. **#18 was closed without a `Closes #N` link.** PR #34 fixed it in `cf8b331` and the issue was closed in the
   same minute, but the PR body did not reference #18 or carry a `Closes` trailer — unlike its five
   siblings. The tutorial is explicit that `Closes #NUMBER` in the PR body is what closes the Issue; the
   close here came from someone closing it manually. The code fix is verified present
   (`if (searchableByFaculty != null)` in `WebController`).
2. **#17's fix is covered by a mocked web-layer test, not a live replay.** `ProfilePersistenceTest` posts the
   profile form through `MockMvc` against the real database and asserts that `verifiedSkills` survives a partial
   save and that consent changes only on an explicit value — so the behaviour is pinned. What is missing is the
   tutorial's final step (slide 33): switching to `main`, pulling, and replaying the reproduction in a running
   browser. The regression is guarded; the end-to-end replay is not recorded.
3. **Bugs found while fixing could not use `Closes`.** In #21's fix, the `titleCase` removal also broke a
   reflection-based test and left the concepts path re-casing; both were corrected inside the same PR.
   Because a PR closes an Issue only on merge to the default branch, the PR could not carry a second `Closes`
   for a defect discovered after it was opened.
4. **Two suspected defects were retracted during investigation** and never filed: `round()` being dead code
   (it is called at `GitHubSkillAdapter:205` / `OpenAlexPublicationAdapter:200`), and `forHandle` mishandling
   case. Filing them would have inflated the count with non-bugs.
5. **#16 and #23 are design questions, not crashes, and #16's fix is incomplete.** The brief deliberately has
   no authentication, so whether the faculty view must be scoped is a product decision. They are filed as Major
   with that caveat stated in the Issue rather than presented as confirmed leaks.
   Separately, #16's `403` for a student session is a real gate, but the endpoint still trusts the `facultyId`
   **path variable**. `GET /api/faculties/{other-id}/candidates` from a *professor* session returns that other
   faculty's candidates. Fixing it needs the session-vs-path comparison #16 originally proposed. Not re-opened,
   because #16 as written described the student-session case that is now closed.
6. **The Issue register is not the whole defect history.** The pre-issue review recorded in
   `docs/verdict-member2-member5.md` found three `matching/` defects that were reported directly to Member 2 and
   fixed in code without a GitHub Issue: a `(student year: null)` null-text bug, an unreachable `evaluate`
   override in `MatchingEngine.link(...)`, and a test masking a `null` ternary arm. This register covers Issues
   only, so those are outside its count of 14.

---

## 6. The hard requirement held

`AGENTS.md` §4 requires `StudentProfile.searchableByFaculty` to default to `false` at field, constructor and
column level (`nullable = false`), with `discoverCandidates` filtering through
`StudentRepository#findBySearchableByFacultyTrue`.

Across all 14 Issues, **no defect violated this rule.**

- The closest was #18, which ran in the opposite direction — consent could be *lost* by omission, never gained.
- #16 was an *authorisation* gap, not a privacy leak: the facade's `findBySearchableByFacultyTrue` filter held,
  so a non-opt-in student was never returned. Issue #16 states this explicitly so it is not misread as a leak.
- #22 (duplicate evidence) affected `verifiedSkills` counts as a `Set`, so scoring was not corrupted by it.

---

## 7. What this exercise showed

**The defects clustered where two layers met, not inside either one.** #19/#32 and #29 were both scale and
wording mismatches between the semantic and evidence layers; #21/#25 were both the adapters rewriting text
they did not own. Reading one class alone would have missed every one of them.

**Two tests were actively defending a bug.** `titleCasesLowercaseOpenAlexTags` asserted the mangled output
and the online-payload test fed all-lowercase JSON, so #21 survived review with a green suite. A passing test
is evidence only if someone asked what it should have asserted.

**Reproduction beat reading.** #20's flat `0.0` ranking was only obvious from `GET /api/discover`; #25's
query-string bug was only obvious from running the four URL forms. #25's fix also looked correct twice before a
direct probe showed `https://github.com` returning `https:` — an unexpected input refused the fix and settled it.

**Ownership routing was accurate but uneven.** The git history mapped cleanly onto `AGENTS.md` §2, which is
why every Issue landed with the member who wrote the code. The load, however, was lopsided — Member 4 received
6 of 14. #17, #18 and #22 shared one root cause (a detached-entity rebuild) and were fixed together in one
commit, which is the right shape for a related cluster.

---

## 8. Cross-references

- `AGENTS.md` §1–§2 — ownership area per member (the assignment basis for every Issue).
- `AGENTS.md` §4 — the privacy rule verified in §6.
- `AGENTS.md` §5 — testing and coverage gate.
- `docs/integration-contracts.md` — the "expected result" for every behavioural report.
- `agent-notes/2026-11-27-member4-assigned-issues.md`, `2026-11-27-scoring-normalization.md`,
  `2026-11-27-coverage-tooling-docs.md`, `2026-11-27-semantic-synonyms-skill-text.md` — the fixers'
  hand-off notes.
