# HSTS — Design Patterns Catalog

*Living document. Rule for the whole team: when your code introduces or leans on a
pattern, name it in the class Javadoc (`Pattern: DAO — ...`) **and** add/extend a row
here in the same commit. The course grades reuse & design patterns explicitly
(required-features NFR 20), and this file is where we prove it at the defense.*

*Maintained by: Person 2 (Naji). Started 2026-07-04.*

---

## Patterns currently in the codebase

| Pattern | Where | Why it helps us work in parallel |
|---|---|---|
| **Layered architecture (3-tier)** | `client/**` (Presentation) · `server/**` (Logic) · `server/db/**` (Data) · `common/**` (shared wire model) | Each person can own a vertical slice without stepping on the other tiers; the client never touches SQL, the DAO never touches sockets. |
| **DAO (Data Access Object)** | `QuestionDAO`, `UserDAO`, `CourseDAO` (`server/db`) | The *only* classes that run SQL/HQL. Swapping JDBC → Hibernate (`UserDAO`, then `QuestionDAO` in Phase 7) changed nothing outside the class because callers see the same public API — proven by the 40+ DAO characterization tests staying green through the rewrite. Testable in isolation. |
| **DTO / Value Object** | `common/entities/*` (`Question`, `User`, `Course`), `common/network/Credentials` | Dumb serializable data that travels client↔server. No behaviour = no logic drift between tiers; field names are the team's stable contract (TEAM_DIVISION §3.2). |
| **Command (protocol envelope)** | `common/network/Message` + `Message.Command` enum | One wire format for every feature. Adding a feature = adding an enum verb + one `case` in the server switch; nobody edits anyone else's verbs. Each verb documents `payload -> response`. |
| **Facade / Fat Server gatekeeper** | `server/HSTSServer` | Single entry point that validates, authorizes, routes to DAOs and replies. Clients stay thin; all business rules live in one reviewable place. |
| **Adapter** | `client/network/IClientConnection` (real impl: `HSTSClient`) | UI code depends on the small interface, not on OCSF. Person 5 tests every screen with a `FakeClientConnection`; the real socket is swapped in at runtime. |
| **Observer / Publish-Subscribe** | `ClientEventBus` + `ServerMessageEvent` (+ `@Subscribe` in screens, e.g. `LoginView`) | Server pushes arrive as events already on the JavaFX thread. Screens don't poll and don't know about the network layer; `ScreenManager` auto-registers/unregisters on navigation. |
| **Singleton** | `server/db/HibernateUtil` (SessionFactory), `ClientEventBus` | Exactly one expensive SessionFactory / one bus per process; fail-fast at server start. |
| **Guard / Interceptor** | `server/Authorization` (`requireRole`, `requireEnrollment`, `requireCourseAccess`) + `AuthorizationException` caught centrally in `HSTSServer` | One-line authorization at the top of any handler; unauthorized callers get a clean `ERROR` reply without per-handler try/catch. Every person reuses the same guard instead of re-implementing security. |
| **Template Method (tests)** | `LoginIntegrationTest` / `ConcurrencyIntegrationTest` setup style; formalized by `QuestionDaoTestBase` (Person 2, Phase 1) | Shared wipe/reseed lifecycle in a base class; each test class only writes its scenario steps. |

## Patterns being added by Person 2 (question bank work)

| Pattern | Where (planned) | Why |
|---|---|---|
| **Value Object (validated config)** | `server/config/DbSettings` *(landed — Phase 0.5)* | Immutable, validated-at-construction connection profile. Invalid settings are impossible to construct, and JDBC + Hibernate both build from the same instance, so the whole Data tier always points at one database. Resolution is a layered per-key fallback — dialog → `hsts.db.*` system properties → `server.properties` → defaults — with the pure merge function (`ServerConfig.resolve`) unit-tested in isolation. |
| **Facade (service extraction)** | `server/QuestionService` *(landed — Phase 5)*: all bank rules behind one class; `HSTSServer` only resolves the caller and delegates | Handlers become unit-testable with Mockito (mock `SessionManager` + DAO) without opening a socket; keeps the OCSF class thin. |
| **Strategy (validation)** | `server/QuestionValidator` *(landed — Phase 5)*: stateless rule set returning error-or-null, shared by ADD and UPDATE | Same rules reused by ADD and UPDATE paths (and by the future bot module when it references bank questions); tested as a pure unit. |
| **Interface Segregation / Dependency Inversion** | `server/db/QuestionSource` *(landed — Phase 3)*: read-only view of `QuestionDAO` (`getByCourse`, `getByCourseFiltered`, `getHistory`) | Person 3's exam auto-builder and the future study bot (scenarios 13–14; R-047 lists *bank questions* as bot knowledge sources) consume the interface, not the DAO — they can develop against an in-memory fake today and get the real DAO at integration with zero rework. |
| **DTO** | `common/network/QuestionFilter` *(landed — Phase 3)*: courseId + optional topic/difficulty, `GET_QUESTIONS_FILTERED` payload | Serializable filter criteria for `GET_QUESTIONS_FILTERED`; the same DTO serves exam auto-build pools and bot source selection. |
| **Lazy loading (protocol level)** | `GET_QUESTION_IMAGE` *(landed — Phase 4)*: fetch one illustration on demand; list replies carry only `imagePath` | Keeps bank-list traffic small (NFR 18 — efficiency, no wasteful transfers); the UI shows a progress indicator while the image loads (NFR 21). |

## Recommended (not yet built) — reserved designs

| Pattern | For | Sketch |
|---|---|---|
| **Adapter (external bot API)** | Scenarios 13–14 (Person 4, or Person 2 if reassigned) | One `BotApiAdapter` interface (`ask(question, sources) -> answer`) + a vendor-specific implementation, mirroring how `IClientConnection` hides OCSF. Swapping bot providers = one new class. |
| **Proxy / Guard (bot lockout)** | "Bot unavailable during exam" rule (scenarios 6 & 14) | Wrap `BotApiAdapter` in a guard that consults the live-exam session registry before delegating; returns the standard "בוט לא זמין" error otherwise. No screen-level checks needed. |
| **Strategy (reports)** | Scenario 12 principal reports (Person 4) | `ReportRequest` → pluggable report generators (mean/median/deciles, per-teacher/course/student) so "adding new report types requires minimal development" (R-067). |
