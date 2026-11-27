# AGENTS.md — ResearchMatch

**CS3383 AI for Software Engineering — group project.**

These rules are **binding for every future OpenCode session** (and any AI or human contributor).
Read this file fully, plus [`docs/integration-contracts.md`](docs/integration-contracts.md), before changing
anything. If a task conflicts with these files, stop and ask.

Session hand-off notes live in [`agent-notes/`](agent-notes/) (one Markdown file per task, see its
[README](agent-notes/README.md)); add a note there at the end of each task. Coverage can be inspected with
`scripts/coverage.sh` (macOS/Linux) or `scripts\coverage.bat` (Windows) — see §5.

---

## 1. Tech stack (non-negotiable)

| Concern | Choice |
| --- | --- |
| Language | **Java 17** (source/target 17 — no preview features, no newer APIs) |
| Framework | **Spring Boot 3.x** |
| Web / UI | `spring-boot-starter-web` + `spring-boot-starter-thymeleaf` + `spring-boot-starter-validation` |
| Persistence | `spring-boot-starter-data-jpa` |
| Database | **H2 in-memory** (`jdbc:h2:mem:researchmatch`) — no external DB, no migration tooling |
| Build | **Maven** (`pom.xml` at root) |
| Unit tests | **JUnit 5** (`org.junit.jupiter`) + AssertJ |
| Mocks | **Mockito** (via `spring-boot-starter-test`) |
| Coverage | **JaCoCo** — HTML + XML in `target/site/jacoco/`; **≥ 85 % branch coverage** required on feature PRs |

Do not add dependencies, change the Spring Boot version, or swap the database without group approval.

### Build & run

```bash
mvn clean test jacoco:report     # tests + coverage report
mvn clean package                # tests + executable target/researchmatch.jar
mvn verify                       # adds the 85 % branch coverage gate
run.bat                          # java -jar target/researchmatch.jar
```

---

## 2. Strict folder & file ownership

Only edit files inside **your** area for the current task. Never edit another member's files — not even to
fix a compile error. Report it instead.

| Area | Owner | Notes |
| --- | --- | --- |
| `models/` | **Member 1** | **FROZEN.** |
| `repository/` incl. `SystemConfigManager` | **Member 1** | **FROZEN.** |
| `config/` exceptions & `ResearchDataSourceRegistry` | **Member 1** | **FROZEN.** |
| `config/DataSeeder.java` + `config/AdapterRegistry.java` | **Member 3** | Only these two files. |
| `matching/` | **Member 2** | Chain of Responsibility + Strategy. |
| `adapters/`, `pipeline/`, `factory/` | **Member 3** | Adapter, State, Observer, Builder. |
| `controllers/`, `src/main/resources/templates/` | **Member 4** | Facade + UI. |
| `src/test/java/com/researchmatch/` | Everyone | Mirrors main packages; see §5. |
| `pom.xml`, `application.properties`, `AGENTS.md`, `docs/`, CI | Group | Change only with approval. |

`FROZEN` means read-only for everyone except the owner.

---

## 3. Collaboration rules

1. **Replace the `TODO` stubs in place.** Do not create duplicate classes, parallel interfaces, or
   differently-named methods. The baseline exists precisely so all five branches compile against each other.
2. Every unimplemented entry point throws `UnsupportedOperationException("TODO (Member X): ...")`. Keep that
   pattern: **never** return a fake successful result, an empty "success", or silently swallow input.
3. **Public contracts in `docs/integration-contracts.md` must not be modified without group approval.**
   Adding a method is a contract change too. Record approved changes in the doc's change log.
4. **Mock what is not yours.** If a dependency is not merged or still a `TODO`, mock it
   (`Mockito.mock(...)`, `@Mock`, `@MockBean`). Never implement another member's business logic to unblock
   yourself.
5. Business rules live in exactly one place: matching rules in `matching/`, recruitment rules in
   `pipeline/`, orchestration in `controllers/`.
6. `controllers/` may import `matching/`, `pipeline/`, `adapters/`, `models/`, `repository/`, `config/`.
   `matching/`, `pipeline/`, `adapters/`, `factory/` must **never** import `controllers/`.
7. **Commit incrementally as you work — do not batch a whole task into one final commit.** After each
   complete, verified step (a merge, a passing test suite, a coverage milestone, a tooling change, …), make
   a small, scoped commit so no progress is ever lost. Each commit should leave the build green; never
   commit secrets, and do not push unless asked. One task per PR.
8. **Use Conventional Commits for the message subject:** `<type>[scope]: <subject>`, optionally followed by a
   body and footer(s):

   ```
   <type>[scope]: <subject>

   [optional body]

   [optional footer(s)]
   ```

   Common `<type>`s: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `build`, `ci`. Keep the subject
   imperative and under ~72 characters; use the body for *why*, and footers for things like
   `Co-authored-by:` or `Refs:`.

---

## 4. Privacy rule (hard requirement)

`StudentProfile.searchableByFaculty` **MUST default to `false`** — field initialiser, every constructor, and
the DB column (`nullable = false`).

- No constructor or builder may default it to `true`.
- Any faculty-facing query must be privacy-aware (`StudentRepository#findBySearchableByFacultyTrue`), and
  `ResearchMatchFacade#discoverCandidates` must exclude non-searchable students.
- Every test touching this field must assert the `false` default.

---

## 5. Testing & coverage rules

- Every feature PR **MUST include JUnit 5 tests**; new/changed code must keep the bundle at
  **≥ 85 % branch coverage** (`mvn verify` enforces it).
- The project must maintain **100 % line coverage with no exceptions**; strive for **100 % branch coverage**, keeping the existing **≥ 85 %** gate as the enforced minimum.
- Tests mirror main packages: `matching/X.java` → `src/test/java/com/researchmatch/matching/XTest.java`.
- Each member owns the tests for their components. **Member 5** owns end-to-end integration tests
  (`src/test/java/com/researchmatch/integration/`) and coordinates coverage improvements — coordinate
  before touching another member's test package.
- Use Mockito for collaborators; use `@SpringBootTest` only for wiring/integration.
- `ApplicationContextTest` is the baseline wiring check: if it fails, the branch does not integrate.
- `config/DataSeeder` runs on startup, so the test DB is **not empty**. Do not assert `count() == 0`.
- View coverage any time with **`scripts/coverage.sh`** (macOS/Linux) or **`scripts\coverage.bat`**
  (Windows); both wrap `scripts/coverage.py`. It prints overall line/branch % with PASS/FAIL, a per-class
  gap table, `--html` for a dashboard and `--open` for the JaCoCo HTML report. New code that lacks tests
  shows up as a `gap` row. Gap rows are **informational**: the exit code is 0 when the gated counters pass,
  and only `--fail-line`/`--fail-branch` make it fail. The `.sh` wrappers need bash (WSL or Git Bash).

---

## 6. Required design patterns & exact names

Names are contractual — see `docs/integration-contracts.md` for full signatures.

| Pattern | Classes | Package |
| --- | --- | --- |
| Chain of Responsibility | `MatchLayerHandler`, `AbstractMatchLayerHandler`, `EligibilityFilterHandler`, `SemanticMatchHandler`, `EvidenceScoreHandler`, `ExplanationHandler` | `matching` |
| Strategy | `RankingStrategy`, `BalancedMatchStrategy`, `BeginnerFriendlyStrategy`, `SkillStrictStrategy` | `matching` |
| Service | `MatchingEngine`, `MatchContext` | `matching` |
| Builder | `OpportunityBuilder`, `MatchResultBuilder` | `factory` |
| Adapter | `ResearchDataAdapter`, `GitHubSkillAdapter`, `OpenAlexPublicationAdapter`, `ImportedEvidence` | `adapters` |
| State | `ApplicationState`, `AbstractApplicationState`, `NewState`, `ShortlistedState`, `InterviewState`, `AcceptedState`, `RejectedState`, `ApplicationStateFactory` | `pipeline` |
| Observer | `ApplicationSubject`, `ApplicationObserver`, `AuditLogObserver`, `NotificationObserver` | `pipeline` |
| Service | `RecruitmentPipeline` | `pipeline` |
| Facade | `ResearchMatchFacade` | `controllers` |
| Singleton | `SystemConfigManager` (`getInstance()`; the Spring bean is the same instance) | `repository` |
