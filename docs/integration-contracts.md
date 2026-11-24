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
| `KEY_SEMANTIC_WEIGHT` | `matching.semanticWeight` | `0.30` |
| `KEY_MAX_MATCH_RESULTS` | `matching.maxResults` | `20` |
| `KEY_ALLOW_FACULTY_SEARCH` | `privacy.allowFacultySearch` | `false` |

Resolution order: built-in defaults → `src/main/resources/system-config.json` → `-Dresearchmatch.<key>=…`.

> House style for the team: match weights are **weights**, not raw points; `SemanticMatchHandler` should
> scale by `matching.semanticWeight`.

---

## 3. Models & repositories (Member 1 — FROZEN)

All ids are `Long`. All collections are initialised non-null. All entities use identity equality
(`id == null` ⇒ only equal to itself).

| Entity | Fields |
| --- | --- |
| `StudentProfile` | `id, name, email, major, academicYear, hoursPerWeek, interests, claimedSkills, verifiedSkills, githubUsername, searchableByFaculty` |
| `FacultyProfile` | `id, name, department, researchAreas, openAlexId` |
| `Opportunity` | `id, facultyId, title, description, requiredSkills, preferredSkills, hoursPerWeek, compensationType, beginnerFriendly, maxApplicants` |
| `Application` | `id, studentId, opportunityId, status, statement, evidenceLinks, notes, updatedAt` (`updatedAt` auto-stamped on save) |
| `SkillEvidence` | `id, studentId, skillName, repoName, evidenceSnippet, verifiedByStudent` |
| `MatchResult` | `id, studentId, opportunityId, totalScore, eligible, matchedSkills, missingSkills, explanationSummary, recommendedPrep` |
| `AuditEvent` | `id, entityId, action, details, timestamp` (auto-stamped) |

`Application.Status`: `NEW, SHORTLISTED, INTERVIEW, ACCEPTED, REJECTED` (stored as `STRING`).

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
MatchResult evaluate(Long studentId, Long opportunityId);
List<MatchResult> rankOpportunities(Long studentId, String strategyName);
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
        .build();                                  // InvalidRequestException on bad input
```

`build()` rejects: null `facultyId`; null/blank `title`; `hoursPerWeek <= 0`; `maxApplicants <= 0`.

### Adapters

```java
public interface ResearchDataAdapter {
    String platformName();                              // "github", "openalex"
    List<ImportedEvidence> importEvidence(String handle); // never null; empty ⇒ nothing found
}
public record ImportedEvidence(String skillName, String sourceName, String evidenceSnippet, double confidence)
        // skillName non-blank, confidence ∈ [0,1], else IllegalArgumentException
```

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
RecruitmentPipeline(ApplicationRepository, OpportunityRepository, ApplicationStateFactory, ApplicationSubject);
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

### DataSeeder (Spring bean, `CommandLineRunner`)

`config/DataSeeder` inserts one faculty member and two opportunities **only when `opportunityRepository.count() == 0`**.
`ApplicationContextTest` therefore asserts `opportunityRepository.count() > 0` — never assume an empty DB.

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

---

## 10. Member responsibilities

| Member | Owns | Must deliver |
| --- | --- | --- |
| **1** | `models/`, `repository/`, `SystemConfigManager`, `config/` exceptions & registries, build/CI, this doc, `AGENTS.md` | FROZEN schema + repositories; keep the baseline compiling |
| **2** | `matching/` | Handler chain, 3 strategies, `MatchingEngine.evaluate/rankOpportunities` |
| **3** | `adapters/`, `pipeline/`, `factory/`, `config/DataSeeder` | Adapters, state machine, observers, builders, `RecruitmentPipeline`, demo data |
| **4** | `controllers/`, `templates/` | Facade implementations, MVC controllers, Thymeleaf views |
| **5** | `src/test/java/.../integration/` (+ coverage) | End-to-end tests through `ResearchMatchFacade`, coverage coordination |
