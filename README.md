# ResearchMatch

CS3383 AI for Software Engineering group project.

Java 17 · Spring Boot 3 (Web, Thymeleaf, Data JPA, H2 in-memory) · Maven · JUnit 5 · Mockito · JaCoCo.

See [`AGENTS.md`](AGENTS.md) for folder ownership, required design patterns and contribution rules.

## Build, test, run

```bash
mvn clean test jacoco:report     # run tests + generate target/site/jacoco/index.html
mvn clean package -DskipTests    # produces target/researchmatch.jar
run.bat                          # java -jar target/researchmatch.jar  (http://localhost:8080)
```

CI (`.github/workflows/ci.yml`) runs `mvn clean test jacoco:report` on every push/PR and uploads
`target/site/jacoco` as the `jacoco-report` artifact.

## Coverage dashboard

After running the tests, view coverage without parsing Maven output:

```bash
scripts/coverage.sh              # overall line/branch % + per-class gap table (auto-runs tests if no report)
scripts/coverage.sh --run --html # run tests, then write target/site/jacoco-dashboard.html
scripts/coverage.sh --open       # open the JaCoCo HTML report in a browser
```

On Windows use `scripts\coverage.bat`. The tool wraps `scripts/coverage.py` (Python 3, no extra deps),
works from any directory, and flags any class still missing tests. Project policy: **100% line coverage**,
**≥ 85% branch** (see [`AGENTS.md`](AGENTS.md) §5).

> **JDK 17 required.** The project targets Java 17; Mockito's inline mock maker fails on newer JDKs
> (e.g. 21/27) with `Could not modify all classes`. `scripts/coverage.sh` auto-selects a JDK 17 (an
> existing `JAVA_HOME`, or macOS `/usr/libexec/java_home -v 17`) and gives a clear message if none is
> found. If you run `mvn` yourself, set `JAVA_HOME` to a JDK 17 first.

## Agent notes

Session hand-off notes (what was done, decisions, follow-ups) live in [`agent-notes/`](agent-notes/).


## Layout

```
src/main/java/com/researchmatch/
├── ResearchMatchApplication.java
├── models/        FROZEN  – domain entities (Member 1)
├── repository/    FROZEN  – Spring Data JPA + SystemConfigManager singleton (Member 1)
├── matching/              – Chain of Responsibility + Strategy (Member 2)
├── adapters/              – ResearchDataAdapter impls (Member 3)
├── pipeline/              – State + Observer (Member 3)
├── factory/               – OpportunityBuilder, MatchResultBuilder (Member 3)
└── controllers/           – ResearchMatchFacade + UI (Member 4)
src/main/resources/templates/   – Thymeleaf views (Member 4)
```

## Design patterns

Chain of Responsibility (`MatchLayerHandler` → `EligibilityFilterHandler` → `SemanticMatchHandler` →
`EvidenceScoreHandler` → `ExplanationHandler`), Strategy (`RankingStrategy` with
`BalancedMatchStrategy`, `BeginnerFriendlyStrategy`, `SkillStrictStrategy`), State
(`ApplicationState` + `NewState`/`ShortlistedState`/`InterviewState`/`AcceptedState`/`RejectedState`),
Observer (`ApplicationSubject`, `ApplicationObserver`, `AuditLogObserver`, `NotificationObserver`),
Adapter (`ResearchDataAdapter` + `OpenAlexPublicationAdapter`, `GitHubSkillAdapter`), Builder
(`OpportunityBuilder`, `MatchResultBuilder`), Facade (`ResearchMatchFacade`), Singleton
(`SystemConfigManager`).

## Privacy

`StudentProfile.searchableByFaculty` defaults to **`false`**; students opt in explicitly.

## H2 console (dev)

`http://localhost:8080/h2-console` — JDBC URL `jdbc:h2:mem:researchmatch`, user `sa`, empty password.
