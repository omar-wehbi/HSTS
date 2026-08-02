# Session fix report — 2026-08-02

Local build/test unblock on JDK 25 + MySQL. Changes and diagnosis below.

---

## 1. Mockito MockMaker failure on Java 25

**Symptom:** Many unit tests failed with  
`Could not initialize plugin: interface org.mockito.plugins.MockMaker`

**Cause:** Machine runs **OpenJDK 25**. Mockito `5.14.2` pulled Byte Buddy that does not support Java 25 class files. Worse, Hibernate’s transitive **Byte Buddy 1.14.18** won Maven mediation over Mockito’s newer agent jar.

**Fix (`pom.xml`):**
- Bumped `mockito-junit-jupiter` **5.14.2 → 5.23.0**
- Pinned Byte Buddy **1.17.7** via `dependencyManagement` (`byte-buddy` + `byte-buddy-agent`) so Hibernate cannot force the old version

**Verified:** `QuestionServiceTest`, `ResultsServiceTest`, `StudyBotServiceTest`, `ExamServiceTest`, `GradingServiceTest` — 64 tests, 0 failures.

---

## 2. MySQL “Access denied” / `server.properties` not applied in tests

**Symptom:** DAO + integration tests:  
`Access denied for user 'root'@'localhost' (using password: YES)`  
`UserDAOTest` returned null (connection errors swallowed in `UserDAO`).

**Cause:** The file **was** found (`loadSettings from D:\HSTS\server.properties`), but with `db.password=…\!…` the loaded secret did not match MySQL. The same password passed as `-Dhsts.db.password=…` worked. Using a plain mid-value `!` (no backslash) in `server.properties` fixed file-only auth.

**Fix:**
- `ServerConfig.java` — resolve `server.properties` via absolute `user.dir`; log which file `loadSettings()` used
- `server.properties` (local, gitignored) — password written without `\!` escaping

**Override still valid:**  
`.\mvnw.cmd clean package "-Dhsts.db.password=YOUR_PASSWORD"`  
(`hsts.db.*` wins over the file per Phase 0.5 precedence.)

**Verified:** `QuestionDAOAddTest` + `UserDAOTest` green with **no** `-Dhsts.db.password` (file only).

---

## 3. Question-bank fixture blocked by foreign keys

**Symptom:** After DB auth worked — 0 failures, 44 errors:  
`Cannot delete or update a parent row: … examquestions … FOREIGN KEY (question_id) REFERENCES questions(id)`

**Cause:** `QuestionBankTestFixture.wipeQuestions()` ran `DELETE FROM Questions` while seed/demo rows in `ExamQuestions` (and possibly `StudentAnswers`) still referenced those questions.

**Fix (`QuestionBankTestFixture.java`):** wipe children first, then questions:

1. `DELETE FROM StudentAnswers`
2. `DELETE FROM ExamQuestions`
3. `DELETE FROM Questions`
4. `ALTER TABLE Questions AUTO_INCREMENT = 1`

---

## 4. Integration tests connected to port 5555 with no server

**Symptom:** 3 errors left after DB fixes:  
`Connection refused: connect` in `AutoExamIntegrationTest` (2) and `ExamEditIntegrationTest` (1).

**Cause:** Both tests opened an OCSF client to **port 5555** but never started an `HSTSServer`. Other integration tests (`LoginIntegrationTest`, etc.) use `@BeforeAll` / `@AfterAll` on dedicated ports.

**Fix:**
- `AutoExamIntegrationTest.java` — start/stop server on port **5605**; `@BeforeEach` reseeds Complexity/EASY + Sorting/MEDIUM questions (DAO suites wipe the bank)
- `ExamEditIntegrationTest.java` — start/stop server on port **5606**

---

## Files touched this session

| File | Change |
|------|--------|
| `pom.xml` | Mockito 5.23.0, Byte Buddy 1.17.7 pin |
| `src/main/java/server/config/ServerConfig.java` | Stronger external config path + load logging |
| `src/test/java/server/db/QuestionBankTestFixture.java` | FK-safe question wipe |
| `src/test/java/server/AutoExamIntegrationTest.java` | Start embedded server on 5605 |
| `src/test/java/server/ExamEditIntegrationTest.java` | Start embedded server on 5606 |
| `server.properties` (gitignored) | Unescaped local DB password |
| `SESSION_FIX_REPORT.md` | This report |

---

## Suggested local verify

```powershell
.\mvnw.cmd clean package "-Dhsts.db.password=YOUR_PASSWORD"
```

Or, after confirming the log line  
`[ServerConfig] loadSettings from D:\HSTS\server.properties`, a plain:

```powershell
.\mvnw.cmd clean package
```

should pick up `server.properties` without `-D`.
