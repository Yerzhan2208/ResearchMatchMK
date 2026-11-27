# ResearchMatch — Integration Contracts

**Status: BASELINE FROZEN (Member 1).** These signatures are what Members 2–5 code against. Changing any
public signature, exception type, or bean name listed here requires group approval and a note in this file
(see §9).

Everything below compiles today and is exercised by `ApplicationContextTest`. Behaviour is intentionally
**unimplemented** where it belongs to another member: those entry points throw
`UnsupportedOperationException("TODO (Member X): ...")` so a bug surfaces immediately rather than as a silent
wrong answer.

---

## 1. Layered view

```
controllers/ResearchMatchFacade          (Member 4, Facade)
        │
        ├── matching/MatchingEngine        (Member 2)
        │        ├── MatchLayerHandler chain: Eligibility → Semantic → Evidence → Explanation
        │        ├── RankingStrategy: Balanced | BeginnerFriendly | SkillStrict
        │        └── factory/MatchResultBuilder            (Member 3)
        │
        ├── pipeline/RecruitmentPipeline   (Member 3)
        │        ├── ApplicationStateFactory → New/Shortlisted/Interview/Accepted/RejectedState
        │        └── ApplicationSubject → AuditLogObserver, NotificationObserver
        │
        └── config/ResearchDataSourceRegistry → adapters/ResearchDataAdapter
                 (GitHubSkillAdapter, OpenAlexPublicationAdapter)   (Member 3)
                                     │
                            models/ + repository/               (Member 1, FROZEN)
```

---

## 2. Shared primitives

| Type | Package | Notes |
| --- | --- | --- |
| `ResearchMatchException extends RuntimeException` | `config` | Base of all expected failures. |
| `ResourceNotFoundException` | `config` | Unknown id / missing entity. |
| `DuplicateEntityException` | `config` | Unique constraint (e-mail). |
| `InvalidRequestException` | `config` | Bad input (blank, out of range, unknown enum/strategy). |
| `IllegalStateTransitionException` | `config` | Disallowed recruitment move. |
| `SystemConfigManager` | `repository` | **Singleton.** Use `SystemConfigManager.getInstance()` or inject the bean; both are the same object. |

### SystemConfigManager

```java
SystemConfigManager.getInstance();                      // singleton
String  get(String key);                                // null when absent
String  get(String key, String defaultValue);
int     getInt(String key, int defaultValue);           // non-numeric → default
double  getDouble(String key, double defaultValue);     // NaN/Infinity → default
boolean getBoolean(String key, boolean defaultValue);   // only "true"/"false" (any case)
Map<String,String> asMap();                             // unmodifiable snapshot
```

Keys (`static final String` constants on the class):

| Constant | Key | Default |
| --- | --- | --- |
| `KEY_MIN_BRANCH_COVERAGE` | `coverage.minBranch` | `0.85` |
| `KEY_SEMANTIC_WEIGHT` | `matching.semanticWeight` | `0.70` |
| `KEY_EVIDENCE_WEIGHT` | `matching.evidenceWeight` | `0.30` |
| `KEY_MAX_MATCH_RESULTS` | `matching.maxResults` | `20` |
| `KEY_ALLOW_FACULTY_SEARCH` | `privacy.allowFacultySearch` | `false` |

Resolution order: built-in defaults → `src/main/resources/system-config.json` → `-Dresearchmatch.<key>=…`.

> House style for the team: match weights are **weights**, not raw points; `SemanticMatchHandler` should
> scale by `matching.semanticWeight`.

### Scoring model (Member 2)

`totalScore` is a normalised weighted sum of the two scoring layers, so it stays in `[0, 1]`:

```
totalScore = semanticWeight * semanticScore_norm + evidenceWeight * evidenceScore_norm
```

- `semanticScore_norm = similarity ∈ [0, 1]` — `SemanticMatchHandler` contribution
  `similarity * semanticWeight`.
- `evidenceScore_norm = min(verifiedMatches * BONUS_PER_VERIFIED_SKILL, MAX_BONUS) / MAX_BONUS ∈ [0, 1]` —
  `EvidenceScoreHandler` contribution `normalized * evidenceWeight`. The `2.0`-per-skill / `10.0`-cap scale is
  preserved; only the contribution is normalised.
- Weights are resolved and validated by `matching/ScoringWeights`: both must be **non-negative** and sum to
  `1.0` (tolerance `1e-9`). Invalid configuration logs a warning and falls back to `0.70 / 0.30`.
- Eligibility is a **hard filter**, not a score component — there is deliberately no base eligibility bonus.

---

## 3. Models & repositories (Member 1 — FROZEN)

All ids are `Long`. All collections are initialised non-null. All entities use identity equality
(`id == null` ⇒ only equal to itself).

| Entity | Fields |
| --- | --- |
| `StudentProfile` | `id, name, email, major, academicYear, hoursPerWeek, interests, claimedSkills, verifiedSkills, githubUsername, searchableByFaculty` |
| `FacultyProfile` | `id, name, department, researchAreas, openAlexId` |
| `Opportunity` | `id, facultyId, title, description, requiredSkills, preferredSkills, hoursPerWeek, compensationType, beginnerFriendly, maxApplicants, minAcademicYear` |
| `Application` | `id, studentId, opportunityId, status, statement, evidenceLinks, notes, updatedAt` (`updatedAt` auto-stamped on save) |
| `SkillEvidence` | `id, studentId, skillName, repoName, evidenceSnippet, verifiedByStudent` |
| `MatchResult` | `id, studentId, opportunityId, totalScore, eligible, matchedSkills, missingSkills, explanationSummary, recommendedPrep` |
| `AuditEvent` | `id, entityId, action, details, timestamp` (auto-stamped) |

`Application.Status`: `NEW, SHORTLISTED, INTERVIEW, ACCEPTED, REJECTED` (stored as `STRING`).

### Academic-year fields (eligibility input)

| Field | Type | Range / default | Notes |
| --- | --- | --- | --- |
| `StudentProfile.academicYear` | `Integer` | `1`–`4`; may be `null` if unfilled | Constant bounds: `StudentProfile.MIN_ACADEMIC_YEAR` / `MAX_ACADEMIC_YEAR`. |
| `Opportunity.minAcademicYear` | `Integer` | `1`–`4`; **defaults to `1`** (open to all undergraduates) | `setMinAcademicYear(null)` normalises to `1`. |

`Opportunity` constructors (the 4-arg form is unchanged — it now delegates with `minAcademicYear = 1`):

```java
new Opportunity();                                                        // minAcademicYear = 1
new Opportunity(Long id, Long facultyId, String title, String description);          // minAcademicYear = 1
new Opportunity(Long id, Long facultyId, String title, String description, Integer minAcademicYear);
Integer getMinAcademicYear();  void setMinAcademicYear(Integer);          // null ⇒ 1
```

**Layer 1 eligibility rule (Member 2, `EligibilityFilterHandler`).** A candidate must be rejected when:

```java
opportunity.getMinAcademicYear() != null
    && (student.getAcademicYear() == null || student.getAcademicYear() < opportunity.getMinAcademicYear())
```

On rejection: `context.markIneligible(...)` with a note naming the required/actual year, and return
`MatchLayerHandler.LayerResult.STOP`.

**Privacy rule:** `StudentProfile.searchableByFaculty` is `false` by default and `nullable = false` in the DB.

| Repository | Methods |
| --- | --- |
| `StudentRepository` | `findByEmail(String) : Optional<StudentProfile>`, `findBySearchableByFacultyTrue() : List<StudentProfile>` |
| `FacultyRepository` | `findByOpenAlexId(String) : Optional<FacultyProfile>` |
| `OpportunityRepository` | `findByFacultyId(Long) : List<Opportunity>`, `findByBeginnerFriendlyTrue() : List<Opportunity>` |
| `ApplicationRepository` | `findByStudentId(Long)`, `findByOpportunityId(Long)`, `findByStatus(Status)`, `countByOpportunityIdAndStatus(Long, Status) : long` |
| `SkillEvidenceRepository` | `findByStudentId(Long)`, `findByStudentIdAndVerifiedByStudentTrue(Long)` |
| `AuditEventRepository` | `findByEntityIdOrderByTimestampDesc(Long)` |

All extend `JpaRepository<T, Long>`.

---

## 4. Member 2 — `com.researchmatch.matching`

### `MatchContext` (mutable, one per pair, not thread-safe)

```java
new MatchContext(StudentProfile student, Opportunity opportunity)   // NPE if either is null
StudentProfile getStudent();                       Opportunity getOpportunity();
boolean isEligible();                              void setEligible(boolean);
void markIneligible(String reason);                // sets eligible=false + adds a note
double getTotalScore();                            void setTotalScore(double);
void addScore(String layerName, double score);     // also accumulates totalScore
Map<String,Double> getLayerScores();               // insertion-ordered
Set<String> getMatchedSkills();                    Set<String> getMissingSkills();
void addMatchedSkills(Collection<String>);         void addMissingSkills(Collection<String>);
List<String> getExplanationNotes();                void addExplanationNote(String);   // ignores null/blank
String getExplanationSummary();                    // notes joined with " "; "" when none
String getRecommendedPrep();                       void setRecommendedPrep(String);
Set<String> verifiedSkillSet();                    // copy of student.verifiedSkills
```

### Chain of Responsibility

```java
public interface MatchLayerHandler {
    enum LayerResult { CONTINUE, CHAIN_COMPLETE, STOP }
    String layerName();
    LayerResult evaluate(MatchContext context);
    MatchContext handle(MatchContext context, MatchLayerHandler next);   // default impl
}
```

`handle` semantics: evaluate → `STOP` **or** `context.isEligible() == false` ⇒ stop (return context);
otherwise continue to `next` (null ⇒ return context). `handle` throws `NullPointerException` for a null
context and `IllegalStateException` if a handler returns a null `LayerResult`.

```java
public abstract class AbstractMatchLayerHandler implements MatchLayerHandler {
    protected abstract LayerResult apply(MatchContext context);   // implement this
    public final LayerResult evaluate(MatchContext context);      // null-checks + delegates
}
```

| Bean | `layerName()` |
| --- | --- |
| `EligibilityFilterHandler` | `"eligibility"` |
| `SemanticMatchHandler` | `"semantic"` |
| `EvidenceScoreHandler` | `"evidence"` |
| `ExplanationHandler` | `"explanation"` |

`MatchingEngine` must assemble them in that order and call `head.handle(context, null)`.

### Strategy

```java
public interface RankingStrategy {
    String name();
    List<MatchResult> rank(List<MatchResult> results);   // returns a NEW list; never mutates input
}
```

| Bean name | Class | `name()` |
| --- | --- | --- |
| `balancedMatchStrategy` | `BalancedMatchStrategy` | `"balanced"` |
| `beginnerFriendlyStrategy` | `BeginnerFriendlyStrategy` | `"beginner-friendly"` |
| `skillStrictStrategy` | `SkillStrictStrategy` | `"skill-strict"` |

### `MatchingEngine` (Spring `@Service`)

```java
MatchingEngine(StudentRepository, OpportunityRepository, SystemConfigManager, List<RankingStrategy>);
MatchResult evaluate(Long studentId, Long opportunityId);        // ← IMPLEMENTED
List<MatchResult> rankOpportunities(Long studentId, String strategyName);   // ← IMPLEMENTED
RankingStrategy strategy(String strategyName);        // ← IMPLEMENTED (null/blank ⇒ balanced)
// accessors: getStudentRepository(), getOpportunityRepository(), getConfig(); protected newContext(...)
```

Contracts: `evaluate` / `rankOpportunities` throw `ResourceNotFoundException` for unknown ids and
`InvalidRequestException` for null ids / unknown strategy name. `rankOpportunities` returns at most
`matching.maxResults` entries. `strategy(...)` already works and is unit-tested.

---

## 5. Member 3 — `com.researchmatch.factory`, `adapters`, `pipeline`

### Builders

```java
new MatchResultBuilder()
        .studentId(Long).opportunityId(Long).totalScore(double).eligible(boolean)
        .matchedSkills(Collection<String>).missingSkills(Collection<String>)   // null ⇒ empty
        .explanationSummary(String).recommendedPrep(String)
        .build();                                  // never null; no validation
```

```java
new OpportunityBuilder()
        .facultyId(Long).title(String).description(String)
        .requiredSkills(Collection<String>).preferredSkills(Collection<String>)
        .hoursPerWeek(Integer).compensationType(String)
        .beginnerFriendly(boolean).maxApplicants(Integer)
        .minAcademicYear(Integer)                  // optional; null ⇒ 1
        .build();                                  // InvalidRequestException on bad input
```

`build()` rejects: null `facultyId`; null/blank `title`; `hoursPerWeek <= 0`; `maxApplicants <= 0`;
`minAcademicYear` outside `1`–`4`.

### Adapters

```java
public interface ResearchDataAdapter {
    String OFFLINE_SNIPPET_PREFIX = "Offline fixture (sample data, not fetched): ";
    String platformName();                              // "github", "openalex"
    List<ImportedEvidence> importEvidence(String handle); // never null; empty ⇒ nothing found
}
public record ImportedEvidence(String skillName, String sourceName, String evidenceSnippet, double confidence)
        // skillName non-blank, confidence ∈ [0,1], else IllegalArgumentException
```

**Offline mode (no token / OpenAlex disabled).** Both adapters answer only for a small curated fixture map of
known handles; **every other handle yields an empty list**, which the interface already defines as "nothing
found". No evidence is ever derived from the handle's spelling or hash.

Offline results are labelled as sample data: `sourceName` is prefixed with `FIXTURE_SOURCE_PREFIX`
(`fixture:`), `evidenceSnippet` with `OFFLINE_SNIPPET_PREFIX`, and `ImportedEvidence#isSampleData()` reports
it. `ResearchMatchFacade#importSkills` therefore stores sample rows with `verifiedByStudent = false` and
**never** promotes them into `StudentProfile.verifiedSkills` — only live-lookup rows become verified skills.
The profile page shows the unverified state, and matching does not treat sample data as established skills.

The fixture keys are the handles the demo actually uses: the seeded students (`demo-mei-ling-ho`,
`demo-arjun-patel`, `demo-sze-wing-lau`) and the seeded faculty OpenAlex id (`A2RRCMK3ZDBF7`, Prof. Wei Chan,
whose fixture areas mirror `DataSeeder`). So "Import from GitHub / OpenAlex" works on a fresh checkout with
no token, and the data is visibly marked fictional. `octocat` is kept because it is a real account whose
sample reads correctly; the fixture ids are otherwise ours, not real OpenAlex records.

Lookup (used by the facade): `ResearchDataSourceRegistry` (interface) + `AdapterRegistry` (bean).

```java
List<ResearchDataAdapter> sources();
Optional<ResearchDataAdapter> forSource(String source);   // case-insensitive; InvalidRequestException if blank
```

### State

```java
public interface ApplicationState {
    Application.Status status();
    ApplicationState shortlist(); interview(); accept(); reject();   // IllegalStateTransitionException if not allowed
    String describe();
}
public class ApplicationStateFactory {
    ApplicationStateFactory(List<ApplicationState> stateBeans);  // fails fast on missing/duplicate status
    ApplicationState forStatus(Application.Status status);       // IllegalArgumentException if null
}
```

Transition matrix (implemented by Member 3):

| From \ To | SHORTLISTED | INTERVIEW | ACCEPTED | REJECTED |
| --- | --- | --- | --- | --- |
| NEW | ✅ | ✗ | ✗ | ✅ |
| SHORTLISTED | ✗ | ✅ | ✗ | ✅ |
| INTERVIEW | ✗ | ✗ | ✅ | ✅ |
| ACCEPTED | ✗ | ✗ | ✗ | ✗ |
| REJECTED | ✗ | ✗ | ✗ | ✗ |

```
NEW ──shortlist──▶ SHORTLISTED ──interview──▶ INTERVIEW ──accept──▶ ACCEPTED (terminal)
 │                      │                        │
 └────────reject────────┴───────reject──────────┴──────────────────▶ REJECTED (terminal)
```

### Observer

```java
public interface ApplicationObserver {
    void onStatusChanged(Application application, Application.Status from, Application.Status to, Instant at);
}
public class ApplicationSubject {
    ApplicationSubject(List<ApplicationObserver> initial);   // null entries ignored
    void attach(ApplicationObserver observer);               // IllegalArgumentException if null
    boolean detach(ApplicationObserver observer);
    List<ApplicationObserver> observers();                   // immutable snapshot
    void notifyStatusChanged(Application, Status from, Status to);   // must notify each observer exactly once
}
```

`notifyStatusChanged` must swallow observer failures (log and continue) — a bad observer must never roll
back a transition. An observer must be idempotent.

### `RecruitmentPipeline` (Spring `@Service`)

```java
RecruitmentPipeline(ApplicationRepository, OpportunityRepository, StudentRepository,
                    ApplicationStateFactory, ApplicationSubject);
Application submit(Long studentId, Long opportunityId, String statement);  // status NEW, notifies
Application shortlist(Long applicationId);
Application scheduleInterview(Long applicationId);
Application accept(Long applicationId);
Application reject(Long applicationId);
ApplicationState stateOf(Long applicationId);
```

Every method returns the persisted entity (non-null) and throws `ResourceNotFoundException` for unknown ids
and `IllegalStateTransitionException` for a disallowed move. Exactly one `notifyStatusChanged` per successful
transition.

`StudentRepository` is a constructor dependency because `submit` verifies the student exists before creating
an application; no other collaborator in this graph can answer that query.

`submit` additionally throws `InvalidRequestException` when:

- either id is null;
- the statement exceeds `RecruitmentPipeline.MAX_STATEMENT_LENGTH` (4000) characters;
- **the student already applied to that opportunity.**

**Duplicate-application rule (one application per `(studentId, opportunityId)` pair).** The rule was
previously stated only in `ResearchMatchFacade#submitApplication` (Member 4), whose javadoc attributes it to
this service; it is documented here so both members agree on a single owner. Rationale:

- `Application` carries one `status`, so two live rows for the same pair could sit in different states and
  make "accept" ambiguous.
- `maxApplicants` is measured by `countByOpportunityIdAndStatus`, which a duplicate would inflate.
- Rejecting the request keeps the failure loud instead of silently creating a second row.

The check is implemented over `ApplicationRepository#findByStudentId`; it is linear in the student's
application count. A dedicated finder would be preferable but requires a change to a FROZEN repository.

### DataSeeder (Spring bean, `CommandLineRunner`)

`config/DataSeeder` seeds three CityUHK faculty members, six opportunities and four students **only when
`opportunityRepository.count() == 0`**, so seeding is idempotent and `ApplicationContextTest` can assert
`opportunityRepository.count() > 0` — never assume an empty DB. Its constructor takes
`(FacultyRepository, OpportunityRepository, StudentRepository)`; the student repository is used solely to
seed sample students. Seeded students keep `searchableByFaculty = false`.

Seeded handles are chosen to work offline: the students' GitHub usernames are `demo-mei-ling-ho`,
`demo-arjun-patel`, `demo-sze-wing-lau` (matching the GitHub fixtures) and Prof. Wei Chan's OpenAlex id is
`A2RRCMK3ZDBF7` (matching the OpenAlex fixture that mirrors his research areas). The other two faculty have no
OpenAlex id — a null id is valid and simply means no publication import. All of this identity data is
fictional: the `demo-` prefix on student handles, and an OpenAlex id that is ours rather than a real record,
are deliberate markers. The app never contacts GitHub or OpenAlex to verify them.

---

## 6. Member 4 — `com.researchmatch.controllers.ResearchMatchFacade`

```java
ResearchMatchFacade(StudentRepository, FacultyRepository, OpportunityRepository, ApplicationRepository,
                    SkillEvidenceRepository, MatchingEngine, RecruitmentPipeline, ResearchDataSourceRegistry);

// students
Optional<StudentProfile> findStudent(Long);              // never throws
StudentProfile getStudent(Long);                         // ResourceNotFoundException
StudentProfile saveStudent(StudentProfile);              // InvalidRequest / DuplicateEntity
// skills
List<SkillEvidence> importSkills(Long studentId, String source, String handle);
    // sample-data rows: verifiedByStudent = false, never added to StudentProfile.verifiedSkills
List<SkillEvidence> evidenceFor(Long studentId);
// opportunities
Opportunity getOpportunity(Long);
Opportunity saveOpportunity(Opportunity);                // usually from OpportunityBuilder
List<Opportunity> opportunitiesForFaculty(Long facultyId);
FacultyProfile getFaculty(Long facultyId);
// matching (delegates to MatchingEngine)
MatchResult match(Long studentId, Long opportunityId);
List<MatchResult> recommend(Long studentId, String strategyName);
// applications (delegates to RecruitmentPipeline)
Application submitApplication(Long studentId, Long opportunityId, String statement);
List<Application> applicationsForStudent(Long studentId);
List<StudentProfile> discoverCandidates(Long facultyId); // MUST exclude searchableByFaculty == false
Application shortlist(Long applicationId);
Application scheduleInterview(Long applicationId);
Application accept(Long applicationId);
Application reject(Long applicationId);
// helpers
List<String> importSources();                            // ["github", "openalex"]
```

Controllers must call **only** the facade, never `MatchingEngine`, `RecruitmentPipeline`, or repositories.

---

## 7. Usage examples

### Member 2 — evaluate a pair and build the result

```java
MatchContext ctx = new MatchContext(student, opportunity);
MatchLayerHandler head = eligibility;
MatchContext done = head.handle(ctx, /* next built via your constructor-injected handlers */ null);

MatchResult result = new MatchResultBuilder()
        .studentId(student.getId())
        .opportunityId(opportunity.getId())
        .totalScore(done.getTotalScore())
        .eligible(done.isEligible())
        .matchedSkills(done.getMatchedSkills())
        .missingSkills(done.getMissingSkills())
        .explanationSummary(done.getExplanationSummary())
        .recommendedPrep(done.getRecommendedPrep())
        .build();
```

### Member 3 — drive a transition and notify

```java
Application app = applicationRepository.findById(id).orElseThrow(
        () -> new ResourceNotFoundException("Application " + id + " not found"));
ApplicationState next = stateFactory.forStatus(app.getStatus()).shortlist();
Application.Status from = app.getStatus();
app.setStatus(next.status());
applicationRepository.save(app);
subject.notifyStatusChanged(app, from, app.getStatus());
```

### Member 4 — publish an opportunity and read candidates

```java
Opportunity opportunity = new OpportunityBuilder()
        .facultyId(facultyId).title("NLP for course feedback")
        .requiredSkills(Set.of("Java")).beginnerFriendly(true).maxApplicants(3)
        .minAcademicYear(2)                       // second-year and above
        .build();
facade.saveOpportunity(opportunity);
List<StudentProfile> candidates = facade.discoverCandidates(facultyId);   // privacy-filtered
```

### Member 5 — unit-testing around an unimplemented dependency

```java
@ExtendWith(MockitoExtension.class)
class RecruitmentE2ETest {
    @Mock RecruitmentPipeline pipeline;        // Member 3 not merged yet? Mock it — never reimplement it.

    @Test
    void facadeDelegatesShortlist() {
        when(pipeline.shortlist(1L)).thenReturn(new Application(1L, 1L, 1L));
        // assert facade.shortlist(1L) delegates, without touching Member 3's rules
    }
}
```

### Mocking what does not exist yet

| Consumer | Unimplemented dependency | Mock with |
| --- | --- | --- |
| Member 2 tests | adapters, pipeline | `Mockito.mock(GitHubSkillAdapter.class)` |
| Member 4 tests | `MatchingEngine`, `RecruitmentPipeline` | `Mockito.mock(...)` |
| Member 5 tests | anything not merged | `@Mock` + `@MockBean` in `@SpringBootTest` |

---

## 8. Dependency graph (verified by compilation)

| Consumer | Producer | Status |
| --- | --- | --- |
| `MatchingEngine` → `MatchResultBuilder` | Member 3 | ✅ compiles (builder imported in the contract doc; engine TODO) |
| `ResearchMatchFacade` → `MatchingEngine` | Member 2 | ✅ constructor-injected, bean exists |
| `ResearchMatchFacade` → `RecruitmentPipeline` | Member 3 | ✅ constructor-injected, bean exists |
| `ResearchMatchFacade` → `ResearchDataSourceRegistry` | Member 3 | ✅ interface in `config/`, impl `AdapterRegistry` |
| `RecruitmentPipeline` → `ApplicationStateFactory`, `ApplicationSubject` | Member 3 | ✅ |
| `ApplicationSubject` → `AuditLogObserver`, `NotificationObserver` | Member 3 | ✅ default observers registered |
| Member 5 → `ResearchMatchFacade` | Member 4 | ✅ facade bean injected in `ApplicationContextTest` |

Circular dependency risk: none. `models`/`repository` ← everyone; `matching` ← `controllers`;
`pipeline` ← `controllers`; `adapters` ← `factory`/`controllers`. **Never** import `controllers` from
`matching`/`pipeline`/`adapters`.

---

## 9. Change log for this contract

| Date | Change | Approved by |
| --- | --- | --- |
| baseline | Initial contracts (this file) | Member 1 |
| schema-1 | Added `Opportunity.minAcademicYear` (default `1`, 4-arg constructor preserved) + `OpportunityBuilder.minAcademicYear(Integer)`; documented `StudentProfile.academicYear` (1–4) and the Layer 1 eligibility rule | Member 1 |
| adapters-1 | Offline mode narrowed to a curated fixture map: unknown handles/author ids return empty instead of hash-derived fabricated evidence; added `ResearchDataAdapter.OFFLINE_SNIPPET_PREFIX` / `FIXTURE_SOURCE_PREFIX` and `ImportedEvidence#isSampleData()` so sample data is visibly labelled; fixture keys aligned with `DataSeeder` (seeded students + Prof. Wei Chan's OpenAlex id, areas mirrored) and unused invented ids dropped. **`ResearchMatchFacade#importSkills` no longer promotes sample-data rows into `verifiedSkills`** (Member 4's file, changed with the group's agreement that fixture data must not become a verified credential) | Member 3, **pending group ratification** |
| scoring-1 | Normalised scoring (issues #19, #20): `totalScore = semanticWeight*semantic + evidenceWeight*evidence` with both components in `[0,1]`. `matching.semanticWeight` default raised `0.30 → 0.70`; new key `matching.evidenceWeight` (default `0.30`); evidence bonus normalised by `MAX_BONUS` (keeps `2.0`/`10.0` scale and cap). New `matching/ScoringWeights` validates both weights (non-negative, sum `1.0`) with fallback + warning. No API signature changed. | Member 1 (config) + Member 4 (demo/UI), approved by user |

---

## 10. Member responsibilities

| Member | Owns | Must deliver |
| --- | --- | --- |
| **1** | `models/`, `repository/`, `SystemConfigManager`, `config/` exceptions & registries, build/CI, this doc, `AGENTS.md` | FROZEN schema + repositories; keep the baseline compiling |
| **2** | `matching/` | Handler chain, 3 strategies, `MatchingEngine.evaluate/rankOpportunities` |
| **3** | `adapters/`, `pipeline/`, `factory/`, `config/DataSeeder` | Adapters, state machine, observers, builders, `RecruitmentPipeline`, demo data |
| **4** | `controllers/`, `templates/` | Facade implementations, MVC controllers, Thymeleaf views |
| **5** | `src/test/java/.../integration/` (+ coverage) | End-to-end tests through `ResearchMatchFacade`, coverage coordination |
