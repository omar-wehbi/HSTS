# Question Bank — what Person 2 (Naji) built and how to use it

*Companion to `FOUNDATION.md` (Person 1). Everything below is implemented,
tested (the suite is 144 green) and pushed on `naji-question-bank`.
Scenario 2 (עריכת מאגר שאלות) is complete end-to-end, plus the infrastructure
pieces other people's features consume.*

---

## 0. Demo scenario 2 in 60 seconds

```
mysql -u root -p < src/main/resources/seed_test_scenarios.sql   # rebuild demo data
mvnw clean package
java -jar target/G12_Server.jar     # a CONNECTION WINDOW opens — see §1
java -jar target/G12_Client.jar     # login: teacher / 1234 → Question Bank
```

Then: **2.1** add a question (attach an illustration with *Choose image…*);
**2.2** edit it → old version stays (click **History** — the seed already ships
question `00011` with v1→v2→v3); **2.3** browse/⟳ Refresh; **2.4** Delete
removes the whole family. Question 2 comes pre-seeded with an illustration.

## 1. Server connection window (start here on any machine)

The server no longer hardcodes the DB. On startup a Swing dialog shows
host / port / database / user / password / OCSF port, prefilled from
`server.properties`, with **Test Connection** and **Save as defaults**.
Headless/scripts: `--no-gui` (uses `server.properties` / `-Dhsts.db.*`
system properties). Precedence: dialog → sysprops → file → defaults.
Config lives in one validated `DbSettings` value object used by **both**
JDBC and Hibernate.

## 2. Security: every bank command is guarded now

`QuestionService` (the Facade in front of the `HSTSServer` switch) enforces:
**no login → no command at all** (even reads); mutations are **TEACHER-only**;
payloads pass `QuestionValidator` (4 answers, correct 1..4, one course,
image ≤ 2 MB…) before any DAO call. Unauthorized callers get the standard
`ERROR` via `AuthorizationException`. **If your screen talks to the bank, the
user must be logged in** (the normal Connect → Login → Home flow guarantees it).

## 3. Commands you can call (payload → response)

```
GET_COURSES                → List<Course>
GET_QUESTIONS              → List<Question>          (full bank; initial load / refresh)
GET_QUESTIONS_BY_COURSE    Integer courseId → List<Question>
GET_QUESTIONS_FILTERED     QuestionFilter → List<Question>   ← exam auto-build pools (P3)
GET_QUESTION_HISTORY       Integer baseId → List<Question>   (all versions, oldest first)
GET_QUESTION_IMAGE         Integer question id → byte[]      (lazy; null = no image)
ADD_QUESTION               Question → Question               (saved; NO image-byte echo)
UPDATE_QUESTION            Question → Question               (new current version)
DELETE_QUESTION            Integer baseId → Integer          (removed baseId)
```

**Mutation replies are surgical (NFR 18)** — you get the affected question /
deleted id, never the whole bank. Update your row in place; only an explicit
`GET_QUESTIONS` transfers everything.

## 4. For Person 3 (exams) — your question pools

Depend on the **`QuestionSource` interface** (`server/db`), not on
`QuestionDAO`: `getByCourse`, `getByCourseFiltered(courseId, topic, difficulty)`
(null/blank = no filter; retired versions never leak), `getHistory`,
`getImage`. Develop against an in-memory fake; the real DAO drops in at
integration. Over the wire it's `GET_QUESTIONS_FILTERED` with a
`QuestionFilter`. Seed: course 1 has 22 current questions
(12 EASY / 7 MEDIUM / 3 HARD, topics Complexity / Sorting / Data Structures) —
enough for the auto-build demo; course 3 has only 2 — use it for the
"not enough questions → no exam" negative test.

## 5. For Person 4/5 (execution & UI) — showing questions to students

- Lists never carry image bytes; when you render a question with
  `imagePath != null`, fetch `GET_QUESTION_IMAGE(question.id)` and build
  `new Image(new ByteArrayInputStream(bytes))`. Works for any version id.
- The 5-digit display id (`10017` style) comes from `common/util/DisplayId`
  (`format(baseId, courseId)` — course id doubles as course code for the demo,
  see `schema/README.md`).
- `QuestionsView` is now a full **EventBus** example (`@Subscribe`, lazy image
  routing, surgical list updates) — copy from it; the legacy
  `setServerMessageHandler` has no users left.

## 6. Illustration semantics (if you ever send UPDATE_QUESTION)

The (`imagePath`, `imageData`) pair encodes intent:
path+bytes = new image · **path only = keep the previous version's image** ·
no path = no image. Old versions keep their own illustration.

## 7. Testing infrastructure you can reuse

- `QuestionDaoTestBase` (+ `QuestionBankTestFixture`): every test starts with
  an empty bank and courses 1–3 present — extend it like `QuestionDAO*Test` do.
  Runs against the DB `DatabaseConfig` resolves (`-Dhsts.db.*` to point elsewhere).
- `QuestionServiceTest` shows the Mockito pattern for handler-level rules
  (mock DAO + plain `User` caller — no sockets).
- `QuestionFilterIntegrationTest` / `QuestionImageIntegrationTest` show the
  real-OCSF-client pattern *with the Phase-5 login step*.

## 8. A war story for the defense (and the Word doc)

The Hibernate migration (`QuestionDAO`, same public API) was caught red-handed
by the TDD suite: with try-with-resources the session closes **before**
`catch`, so `tx.rollback()` hit a dead session and the pooled connection went
back with the failed transaction still open — a later operation then
implicitly committed a half-done edit, leaving a family with **no current
version**. Manual session lifecycle (rollback before close) fixed it;
`QuestionDAOUpdateTest.failedUpdateRollsBackAndTheOldVersionStaysCurrent`
guards it forever. Also note `Question.image_data` is deliberately **unmapped**
in JPA (`@Transient` field + native SQL) so ORM list queries can never haul
BLOBs — same principle as the unmapped password on `User`.

## 9. Files I touched outside my lane (integration only)

- `ServerMain` (connection dialog + `--no-gui`), `ServerConfig`/`DatabaseConfig`
  (runtime `DbSettings`), `HibernateUtil` (reads the same settings; registered
  `Question`). Person 1's `ServerConfig.load()`/`Credentials` API now has **no
  callers** — Amjad, decide if you want it removed (kept + tested meanwhile).
- `.gitignore` (internal planning docs), `DESIGN_PATTERNS.md` (new, repo root —
  team rule inside), README (server run instructions).

## 10. Still open on my side

- Word doc rows for scenario 2 (acceptance table) — drafted separately.
- Two-machine LAN run + visual smoke pass (needs a display).
- Team decisions: broadcast changed rows to other live clients or not
  (`sendToAllClients`); drop `ServerConfig.load()`; defense date.
