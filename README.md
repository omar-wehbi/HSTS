# HSTS — High School Test System

A monolithic **3-tier desktop application** built on the **Thin Client / Fat Server**
paradigm. A JavaFX client talks to a fat Java server over OCSF; the server owns all
business rules and persists to MySQL via Hibernate.

**Stack:** Java 17 (source) · JavaFX 17 · native OCSF · Hibernate 6.6 · MySQL 8 ·
Maven wrapper · JUnit 5 + Mockito + AssertJ + JaCoCo.

Deliverables: `G12_Server.jar` and `G12_Client.jar` (built under `target/`).

---

## Requirements

| Need | Notes |
|------|--------|
| **JDK 17+** to build | Suite must also run on **JDK 21** and **JDK 26** |
| **MySQL 8** | Demo DB `hsts_a3_db`; tests use isolated `hsts_a3_test` |
| **Maven Wrapper** | `mvnw` / `mvnw.cmd` — no global Maven required |
| **Windows: `JAVA_HOME`** | `mvnw.cmd` requires `JAVA_HOME`; it does not fall back to `java` on `PATH` |

---

## Quick Start

### 1. Seed the demo database (one-time)

```bash
mysql -u root -p < src/main/resources/seed_test_scenarios.sql
```

PowerShell:

```powershell
Get-Content src/main/resources/seed_test_scenarios.sql | mysql -u root -p
```

This creates `hsts_a3_db`, tables, and demo users/scenarios.

### 2. Build

```bat
set "JAVA_HOME=C:\Program Files\Java\jdk-19"
mvnw.cmd clean package -DskipTests
```

Or on Unix: `./mvnw clean package -DskipTests`.

After the build, `target/` contains:

| Artifact | Purpose |
|----------|---------|
| `G12_Server.jar` | Fat server — OCSF listener + Hibernate/MySQL |
| `G12_Client.jar` | JavaFX thin client (**OS-specific — see below**) |
| `server.properties` | DB credentials template (copied beside jars) |
| `client.properties` | Server host/port template |

### 3. Run (server first, then client)

```bash
java -jar target/G12_Server.jar
java -jar target/G12_Client.jar
```

Demo login: `teacher` / `1234` (other seed users are documented in the seed script).

### Client jar is platform-specific

`pom.xml` declares JavaFX **without** a classifier, so Maven resolves natives for the
**OS that runs the build**. A client jar built on Linux/WSL contains `.so` libraries and
will **not** run on Windows. Build the client on the operating system you will demo on.
The server jar has no native dependencies and is portable.

Do **not** pin `<classifier>win</classifier>` in the pom — that would break teammates on
macOS/Linux.

### Configuration

Both jars load an external properties file from the **same directory as the JAR**, then
bundled defaults, then hard-coded fallbacks.

**`server.properties`:**

```properties
db.host=localhost
db.port=3306
db.name=hsts_a3_db
db.user=root
db.password=root
```

**`client.properties`:**

```properties
server.host=localhost
server.port=5555
```

System properties `hsts.db.*` / `hsts.server.port` override the file. Skip the server
connection window with `--no-gui`:

```bash
java -Dhsts.db.password=pw -jar target/G12_Server.jar --no-gui
```

---

## Running tests

```bash
./mvnw test
# Windows:
mvnw.cmd test
```

- Surefire points tests at **`hsts_a3_test`** via `-Dhsts.db.name=hsts_a3_test`.
- A `LauncherSessionListener` creates/seeds that schema from `seed_test_scenarios.sql`
  once per JVM. The demo database **`hsts_a3_db` is never wiped by the suite**.
- Destructive fixtures refuse to run unless the resolved DB name ends with `_test`.
- Coverage report: `target/site/jacoco/index.html` (after `mvn test`).
- Byte Buddy is loaded as a surefire `-javaagent` so Mockito works on JDK 26 and on
  Windows paths that contain non-ASCII characters.

---

## What the system covers

| Area | Highlights |
|------|------------|
| Auth & sessions | Login, roles (teacher / student / principal), OCSF sessions |
| Question bank | CRUD, images, topics/difficulty, Hibernate DAOs |
| Exam build & approve | Compose exams from questions; approval workflow |
| Release & execution | Timed releases, execution codes, student attempts, extensions |
| Grading & results | Auto-grade, teacher approve/override, student-visible results |
| Principal reports | Read-only reports and execution statistics |
| Study bot | Course bots, sources (text/PDF), student Q&A, usage reports |

---

## Architecture

| Tier | Responsibility | Key packages |
|------|----------------|--------------|
| **Presentation** | JavaFX UI, no business rules | `client.ui.*`, `client.network.*` |
| **Logic (fat server)** | Commands, validation, services | `server.*`, `server.bot.*` |
| **Data** | Hibernate + JDBC config | `server.db.*`, `server.config.*` |
| **Common** | Shared entities & wire protocol | `common.entities.*`, `common.network.*` |
| **OCSF** | Vendored socket framework | `ocsf.*` |

### Design patterns

| Pattern | Where | Purpose |
|---------|-------|---------|
| **Adapter** | `IClientConnection` / `HSTSClient` | Hide OCSF from UI |
| **DAO** | `server.db.*DAO` | Isolate persistence |
| **Singleton** | `ScreenManager`, `HibernateUtil` | Shared stage / SessionFactory |
| **Template Method** | `AbstractScreenUI` | Screen lifecycle |
| **Pub/Sub** | GreenRobot EventBus | Network → UI events on the client |

### Concurrency

OCSF reads on a background thread; JavaFX updates go through `Platform.runLater` in
`HSTSClient.handleMessageFromServer`.

### Security

- Clients never hold DB credentials; the fat server is the only MySQL gatekeeper.
- Commands are type-checked and routed; unknown commands return `ERROR`.
- Hibernate parameterized queries / native queries with bind parameters — no string-concat SQL
  for user input.
- Passwords stored hashed (`PasswordHasher`).

---

## Project structure

```
HSTS/
├── pom.xml
├── mvnw / mvnw.cmd
├── client.properties          # deployment template → copied to target/
├── README.md
└── src/
    ├── main/
    │   ├── java/
    │   │   ├── client/        # UI, network adapter, config
    │   │   ├── common/        # entities + Message/Command protocol
    │   │   ├── ocsf/          # vendored OCSF
    │   │   └── server/        # ServerMain, HSTSServer, db, services, bot
    │   └── resources/
    │       ├── seed_test_scenarios.sql
    │       ├── fxml/ css/ branding/
    │       └── client.properties / server.properties (bundled defaults)
    └── test/                  # JUnit 5 + fixtures; TestDatabase bootstrap
```

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `MockMaker` / Byte Buddy agent errors on JDK 26 | Self-attach blocked or mangled non-ASCII `.m2` path | Build via `mvnw test` so surefire loads `-javaagent` |
| Demo questions disappear after `mvn test` | Old build without test DB isolation | Re-pull; suite must use `hsts_a3_test` |
| Client jar fails to start (missing natives) | Built on a different OS | Rebuild client on the demo OS |
| `mvnw.cmd` cannot find Java | `JAVA_HOME` unset | `set JAVA_HOME=...` then retry |
| Port 5555 already in use | Stale server / old jar still running | Kill the process holding the port; use a freshly built `G12_Server.jar` |
| JaCoCo report shows 0% | Surefire used `${argLine}` instead of `@{argLine}` | Keep `@{argLine}` in surefire so JaCoCo’s agent is preserved |
| FK / constraint failure surfaces as `LogicalConnection... is closed` | Unfixed DAO rollback after failed commit | Catch blocks must use `Transactions.rollbackQuietly` |
