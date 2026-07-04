# HSTS Foundation — what's on the `amjad` branch and how to build on it

*Person 1 (Amjad): authentication, sessions, test infrastructure, EventBus, ORM,
migrations, seed data. Everything below is implemented, tested (37 tests green)
and pushed.*

---

## 0. Get running in 3 commands

```bash
git checkout amjad
mysql -u root -p < src/main/resources/seed_test_scenarios.sql   # builds hsts_a3_db + demo data
mvnw clean package                                               # 37 tests + target/G12_Server.jar + G12_Client.jar
```

Then set **your** MySQL password in `server.properties` (this file's changes are
not committed — each member keeps their own).

Run: `java -jar target/G12_Server.jar`, then `java -jar target/G12_Client.jar`.

**Login accounts** (password `1234` for all):

| username | role |
|---|---|
| `teacher` | TEACHER |
| `coord` | COORDINATOR |
| `principal` | PRINCIPAL |
| `maya`, `noa` | STUDENT (enrolled in courses 1–2, **not** 3 — a negative case for testing) |

---

## 1. Authentication & sessions (scenario 1 — done)

- Commands: `LOGIN` (payload `Credentials`) → `SUCCESS` with a `User` (id, username,
  role, displayName, idNumber — **never** the password), `LOGOUT`, `GET_CURRENT_USER`.
- **Single-session rule** enforced: second login of the same user is rejected
  (tested under a 4-way race). Disconnect frees the username automatically.
- Passwords stored as **SHA-256 hashes** (`PasswordHasher`); seed uses `SHA2('1234',256)`.
- After login the client shows a **role-based home menu** (`HomeView`). Your feature's
  button is there as a disabled placeholder — wire it to your screen when ready.
- `ScreenManager.getCurrentUser()` gives any screen the logged-in user.

## 2. Guarding your handlers (use this!)

At the top of your `HSTSServer` handler:

```java
User caller = sessions.getUser(client);
Authorization.requireRole(caller, Role.TEACHER);              // roles
Authorization.requireEnrollment(caller, courseId, userDAO::isEnrolled);  // scenario 14
Authorization.requireCourseAccess(caller, courseId, userDAO::isEnrolled); // staff free, students if enrolled
```

Unauthorized callers automatically get a clean `ERROR` reply — you don't catch anything.

## 3. EventBus (Pub/Sub — course requirement, done)

`HSTSClient` publishes **every** server message as a `ServerMessageEvent` on
`ClientEventBus`. To receive responses in your screen:

```java
import org.greenrobot.eventbus.Subscribe;
import client.events.ServerMessageEvent;

@Subscribe
public void onServerMessage(ServerMessageEvent e) {
    Message msg = e.getMessage();
    // arrives already on the JavaFX thread — update UI freely
}
```

`ScreenManager` registers/unregisters your screen automatically on navigation —
you do nothing. **Working example: `LoginView`.**
(The old `setServerMessageHandler` still works — `QuestionsView` still uses it —
migrate when convenient.)

## 4. ORM — Hibernate (course requirement, done)

The data tier runs on Hibernate. To migrate **your** DAO:

1. Annotate your entity with JPA (**example: `common/entities/User.java`**).
   Keep fields that shouldn't reach the client (e.g. secrets) **unmapped**.
2. Register it in `server/db/HibernateUtil.java` → `addAnnotatedClass(YourEntity.class)`.
3. Rewrite your DAO body using `HibernateUtil.getSessionFactory().openSession()`
   (**example: `UserDAO`** — same public API before/after, so nothing else changes).

Hibernate is **server-side only** (excluded from `G12_Client.jar`). The
SessionFactory boots at server start (fail-fast).

## 5. Database migrations & seed

- Add your tables as a **numbered file**: `src/main/resources/schema/V3_exams.sql`,
  `V4_execution.sql`, … (`CREATE TABLE IF NOT EXISTS`, DDL only). See `schema/README.md`.
- Add your **demo rows** to `seed_test_scenarios.sql` (deterministic: fixed ids,
  wipe-then-insert) so one command keeps rebuilding the whole demo DB.

## 6. Tests & coverage (please keep it green)

- Deps ready: **JUnit 5, Mockito, AssertJ, H2, TestFX** (TestFX = UI tests, P5).
- Put tests in `src/test/java`; run `mvnw test` **before every push**.
- Coverage report auto-generates: open `target/site/jacoco/index.html`.
- Current state: **37 tests, 0 failures**. Examples to copy:
  `AuthorizationTest` (pure unit), `UserDAOTest` (DAO), `LoginIntegrationTest` /
  `ConcurrencyIntegrationTest` (server integration with a real OCSF client).

## 7. Protocol conventions

Add your commands to `Message.Command` with a comment stating payload → response,
e.g. `CREATE_EXAM, // payload: Exam -> SUCCESS: List<Exam>`. One request in, one
response out. New wire classes go in `common/**` and must be `Serializable`.

## 8. Heads-up: files I touched outside my lane (integration only)

- `QuestionsView` (fxml + controller): added a **← Menu** header button (navigation only).
- `app.css`: added the `header-button` style.
- `ConnectView`: connect now leads to the **login screen** (scenario 1) instead of
  straight to the question bank.
No feature logic changed — restyle/move freely.

## 9. Still open (team)

- Scenarios 3–14 (exams, execution, grading, reports, bot) — per the division doc.
- Word doc `G12_Assignment3`: each member adds their **responsibilities** +
  **acceptance-test rows**; submission zip needs the doc + both jars (+ the two
  `.properties` files beside the jars to run).
- A **two-machine run** of `G12_Server.jar`/`G12_Client.jar` before the defense
  (edit `client.properties` → server's IP; works over LAN or ngrok).
- Defense: **12/7/26** or **6/8/26** — decide which we're aiming for.
