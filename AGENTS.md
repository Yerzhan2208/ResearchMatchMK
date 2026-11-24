# AGENTS.md — ResearchMatch

**CS3383 AI for Software Engineering — group project.**

These rules are **binding for every future OpenCode session** (and any AI or human contributor).
Read this file fully before changing anything. If a task conflicts with this file, stop and ask.

---

## 1. Tech stack (non-negotiable)

| Concern | Choice |
| --- | --- |
| Language | **Java 17** (source/target 17 — do not use preview features or newer APIs) |
| Framework | **Spring Boot 3.x** |
| Web / UI | `spring-boot-starter-web` + `spring-boot-starter-thymeleaf` |
| Persistence | `spring-boot-starter-data-jpa` |
| Database | **H2 in-memory** (`jdbc:h2:mem:researchmatch`) — no external DB, no migrations tooling |
| Build | **Maven** (`pom.xml`, root) |
| Unit tests | **JUnit 5** (`org.junit.jupiter`) + AssertJ from `spring-boot-starter-test` |
| Mocks | **Mockito** (via `spring-boot-starter-test`) |
| Coverage | **JaCoCo** — HTML + XML reports in `target/site/jacoco/` |

Do not add new dependencies, change the Spring Boot version, or swap the database without explicit
group approval. Do not modify `pom.xml` unless your task is explicitly about the build.

### Build & run

```bash
mvn clean test jacoco:report     # tests + coverage report
mvn clean package -DskipTests    # builds executable target/researchmatch.jar
run.bat                          # java -jar target/researchmatch.jar
```

---

## 2. Strict folder ownership

Only edit files inside the area you are assigned for the current task. **Never** edit files outside it,
even to "fix" a compile error — report the problem to the owning member instead.

| Folder | Owner | Notes |
| --- | --- | --- |
| `src/main/java/com/researchmatch/models/` | **Member 1** | **FROZEN.** Do not change fields, defaults, or mapping without Member 1 + group sign-off. |
| `src/main/java/com/researchmatch/repository/` | **Member 1** | **FROZEN.** Includes `SystemConfigManager` (Singleton). |
| `src/main/java/com/researchmatch/matching/` | **Member 2** | Chain of Responsibility + Strategy. |
| `src/main/java/com/researchmatch/adapters/` | **Member 3** | Adapter. |
| `src/main/java/com/researchmatch/pipeline/` | **Member 3** | State + Observer. |
| `src/main/java/com/researchmatch/factory/` | **Member 3** | Builder. |
| `src/main/java/com/researchmatch/controllers/` | **Member 4** | Facade + UI controllers. |
| `src/main/resources/templates/` | **Member 4** | Thymeleaf views. |
| `src/test/java/com/researchmatch/` | **Everyone** | Must **mirror** the main package of the code you touched. |

Shared, low-risk files (`pom.xml`, `application.properties`, `AGENTS.md`, `README.md`, CI) require
group agreement; leave them alone unless your task explicitly covers them.

`FROZEN` means read-only for everyone except the owner: match by field/behaviour, never by editing.

---

## 3. Required design patterns & exact class names

Names below are contractual (they may be referenced by tests, docs and the report). Do not rename.

### Chain of Responsibility — `com.researchmatch.matching`

| Class | Responsibility |
| --- | --- |
| `MatchLayerHandler` | Abstract base: holds the next handler, `setNext(...)`, `handle(...)` |
| `EligibilityFilterHandler` | Drops candidates that fail hard eligibility rules |
| `SemanticMatchHandler` | Scores interest/description similarity |
| `EvidenceScoreHandler` | Weighs verified skill evidence |
| `ExplanationHandler` | Produces human-readable explanation summary + prep advice |

### Strategy — `com.researchmatch.matching`

| Class | Responsibility |
| --- | --- |
| `RankingStrategy` | Interface for ordering/scoring match results |
| `BalancedMatchStrategy` | Default weighting |
| `BeginnerFriendlyStrategy` | Favours beginner-friendly opportunities |
| `SkillStrictStrategy` | Penalises missing required skills |

### State & Observer — `com.researchmatch.pipeline`

| Class | Responsibility |
| --- | --- |
| `ApplicationState` | State interface for an application |
| `NewState`, `ShortlistedState`, `InterviewState`, `AcceptedState`, `RejectedState` | Concrete states |
| `ApplicationSubject` | Observer subject (attach/detach/notify) |
| `ApplicationObserver` | Observer interface |
| `AuditLogObserver` | Writes `AuditEvent` rows |
| `NotificationObserver` | Notifies the student |

### Adapter — `com.researchmatch.adapters`

`ResearchDataAdapter` (interface), `OpenAlexPublicationAdapter`, `GitHubSkillAdapter`.

### Builder — `com.researchmatch.factory`

`OpportunityBuilder`, `MatchResultBuilder`.

### Facade & Singleton

`ResearchMatchFacade` (`com.researchmatch.controllers`), `SystemConfigManager`
(`com.researchmatch.repository`, already implemented — extend only via Member 1).

---

## 4. Privacy rule (hard requirement)

`StudentProfile.searchableByFaculty` **MUST default to `false`**, in the field initialiser, in every
constructor, and in the database column (`nullable = false`). Opt-in only.

- Never add a constructor or builder that sets it to `true` by default.
- Every faculty-facing query must be privacy-aware (see `StudentRepository#findBySearchableByFacultyTrue`).
- Any test touching this field must assert the `false` default.

---

## 5. Testing & coverage rules

- Every feature PR **MUST include JUnit 5 tests**.
- New/changed code must reach **≥ 85 % branch coverage** total (`mvn verify` enforces it via the
  JaCoCo `check` goal; `mvn clean test jacoco:report` produces the report).
- Tests mirror main packages: code in `matching/X.java` → test in
  `src/test/java/com/researchmatch/matching/XTest.java`.
- Use Mockito for collaborators (repositories, adapters) rather than hitting H2, except in
  `@SpringBootTest` / persistence tests.
- A PR that lowers branch coverage below 85 % must not be merged.

## 6. Working agreements

- One task = one small, reviewable change; keep commits scoped to your folder.
- Do not reformat or "clean up" files you don't own.
- Update `README.md`/`AGENTS.md` design-pattern lists only if the group agrees.
- If you must deviate from a name or rule above, document the reason in the PR and get review first.
