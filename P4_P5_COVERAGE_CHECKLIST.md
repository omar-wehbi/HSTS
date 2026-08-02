# P4 / P5 coverage checklist

Track this together. Goal: raise Person 4 and Person 5 instruction coverage without duplicating work.

**Latest:** 2026-08-02 JaCoCo after P4+P5 push — see `COVERAGE_BY_PERSON.md`  
**Suite:** `mvn test` green — **427 tests, 0 failures**.  
**Results:** P4 **78.2%** instr (was ~33%); P5 **31.9%** (was ~27%); overall **53.1%** (was ~40%).

---

## Rules of engagement

| Layer | Owner | Pattern |
|-------|--------|---------|
| DAO + SQL | **Person 4** | JUnit + real MySQL (`ExecutionTestFixture` / `ExecutionDaoTestBase`) |
| Service (mocked DAOs) | **Person 4** | Fill ERROR / edge branches on existing `*ServiceTest`s |
| Integration (`HSTSServer` + OCSF) | **Person 4** (P5 reviews message sequence) | Dedicated test port; `@BeforeAll` / `@AfterAll` like `LoginIntegrationTest` |
| Session + `FakeClientConnection` | **Person 5** | Extend `client.ui.exam.*SessionTest` — no live server |
| View / FXML | **Person 5** (optional) | Prefer moving `if`s out of `*View` into `*Session` / helpers; TestFX only if course requires UI coverage |

**Sync rule:** freeze `Message.Command` + ERROR payload strings for a scenario *before* either person codes tests for it.

**Do not:** rewrite each other’s modules; add TestFX for every screen just to chase JaCoCo on FXML glue.

---

## Checklist

| # | Scenario | Person 4 — add | Person 5 — add | Seed / contract | Done |
|---|----------|----------------|----------------|-----------------|------|
| **5** | Release exam | `ExamReleaseDAOTest`; release via fixture | `ExamReleaseSessionTest`: ERROR, release/extend SUCCESS, empty lists | Approved exam + open/close + 4-digit code | ✅ |
| **6** | Take exam | `ExamSessionDAOTest`; start/submit via E2E | `TakeExamSessionTest`: ERROR, TIMED_OUT, remainingSeconds, save/submit guards | Live release + enrolled student (`maya`) | ✅ |
| **7** | Extend time | Session DAO extend covered in `ExamSessionDAOTest` | `ExamReleaseSessionTest` extend SUCCESS + ERROR | In-progress session under teacher’s release | ✅ |
| **8** | Grade | `GradeDAOTest`; E2E auto-grade + approve | `GradeExamsSessionTest`: ERROR, id≤0, empty lists, summary, replaceGrade | Submitted session | ✅ |
| **9** | Student grades | E2E `GET_STUDENT_RESULTS` after approve | `StudentGradesSessionTest`: ERROR + empty list | Approved grades for `maya` | ✅ |
| **10** | Teacher results | `ExecutionReportDAOTest` summary/histogram | `ExamResultsSessionTest`: ERROR, empty, histogram | Graded exam owned by `teacher` | ✅ |
| **11** | Principal data | `PrincipalReadOnlyDAOTest` | `PrincipalDataSessionTest`: SUCCESS + ERROR + empty | `principal` user | ✅ |
| **12** | Principal reports | `PrincipalReportDAOTest` | `PrincipalReportsSessionTest`: empty + ERROR | Grades for buckets | ✅ |
| **13** | Create study bot | `StudyBotDAOTest` create/sources/edit | `StudyBotTeacherSessionTest`: create, delete source, ERROR, empty | Course teacher link | ✅ |
| **14** | Use study bot | Lockout integration + DAO history | `StudyBotStudentSessionTest`: lockout/unavailable ERROR, ask SUCCESS | Bot + sources; live exam session | ✅ |
| **E2E-A** | Joint | `TakeAndGradeIntegrationTest` port **5607** | Message order matches sessions | `ExecutionTestFixture` seed | ✅ |
| **E2E-B** | Joint | `StudyBotLockoutIntegrationTest` port **5608** | Lockout copy asserted | In-progress exam + study bot | ✅ |

---

## Existing tests (don’t redo — extend)

### Person 4 (server)
- `ExamReleaseServiceTest`, `ExamExecutionServiceTest`, `GradingServiceTest`
- `ResultsServiceTest`, `PrincipalReportServiceTest`, `StudyBotServiceTest`
- DAO: `ExamSessionDAOTest`, `ExamReleaseDAOTest`, `GradeDAOTest`, `StudyBotDAOTest`, `ExamSnapshotDAOTest`, `ExecutionReportDAOTest`, `Principal*DAOTest`
- Integration: `TakeAndGradeIntegrationTest`, `StudyBotLockoutIntegrationTest`
- `StudyBotDocumentExtractorTest`, harness: `ExecutionTestFixture` / `ExecutionDaoTestBase`

### Person 5 (client sessions)
- `TakeExamSessionTest`, `ExamReleaseSessionTest`, `GradeExamsSessionTest`
- `StudentGradesSessionTest`, `ExamResultsSessionTest`
- `PrincipalDataSessionTest`, `PrincipalReportsSessionTest`
- `StudyBotStudentSessionTest`, `StudyBotTeacherSessionTest`
- `ClientConfigTest`, `FakeClientConnectionTest`, `HomeViewNavigationTest`

### Remaining gaps (optional next)
- **P4:** `ExternalStudyBotApiAdapter` / live API edges; deeper StudyBotService ask paths.
- **P5:** `client.ui.*View` ~2% (FXML). Sessions already ~83% — don’t chase Views unless TestFX is required.

---

## Definition of done (per scenario row)

- [x] ERROR strings used on both sides for covered scenarios  
- [x] P4 tests green against local MySQL (`server.properties`)  
- [x] P5 session tests green without live server  
- [x] Integration tests: unique ports **5607** / **5608**, start/stop server  
- [x] `mvn test` green on the shared branch  

---

## How to re-check coverage

```powershell
.\mvnw.cmd clean test
start target\site\jacoco\index.html
```

Update `COVERAGE_BY_PERSON.md` after a meaningful P4/P5 test push.
