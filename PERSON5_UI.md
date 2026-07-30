# Person 5 — Client UI (scenarios 1–14)

JavaFX screens + testable session helpers. Server protocol is owned by Persons 1–4;
this layer speaks `Message.Command` and DTOs only.

## Pattern

- **Session** in `client.ui.exam` — builds requests, holds state, parses SUCCESS/ERROR (unit-tested, no JavaFX).
- **View** + FXML — `loader.setController(this)`, `@Subscribe` on `ServerMessageEvent`, back to `HomeView`.
- **FakeClientConnection** — scripted replies for UI/protocol tests without a live server.

## Role menus (`HomeView`)

| Role | Screens |
|------|---------|
| Teacher | Question Bank, Build Exams, Release Exams, Grade Exams, Exam Results, Study Bot |
| Coordinator | Approve Exams |
| Principal | View Data, Reports |
| Student | Take Exam, My Grades, Study Bot |

Demo logins (password `1234`): `teacher`, `coord`, `principal`, `maya`, `noa`.

## Scenario map

| # | Screen | Main commands |
|---|--------|----------------|
| 1 | Login / Home | LOGIN, LOGOUT |
| 2 | QuestionsView | GET/ADD/UPDATE/DELETE_QUESTION… |
| 3–4 | ExamListView, ExamBuilderView, ExamApprovalView | CREATE/UPDATE/DELETE/GET_MY_EXAMS, GENERATE_EXAM_AUTO, SUBMIT/APPROVE/REJECT |
| 5, 7 | ExamReleaseView | RELEASE_EXAM, GET_RELEASED_EXAMS, EXTEND_EXAM_TIME, GET_EXECUTION_SUMMARY |
| 6 | TakeExamView | START_EXAM_SESSION, SAVE_ANSWERS, SUBMIT_ANSWERS |
| 8 | GradeExamsView | GET_RELEASE_SESSIONS, GET_RELEASE_GRADES, GRADE_EXAM_AUTO, APPROVE_GRADE, OVERRIDE_GRADE |
| 9–10 | StudentGradesView, ExamResultsView | GET_STUDENT_RESULTS, GET_CHECKED_EXAM, GET_EXAM_STATISTICS |
| 11–12 | PrincipalDataView, PrincipalReportsView | GET_PRINCIPAL_DATA, GET_PRINCIPAL_READ_ONLY, GET_REPORT |
| 13–14 | StudyBotStudentView, StudyBotTeacherView | CREATE/ASK/GET_STUDY_BOT*, sources, usage |

## Local setup

1. Seed DB: `Get-Content src\main\resources\seed_test_scenarios.sql | mysql -u root -p`
2. Also OK: run Person 4 `schema/V*.sql` in order on an existing DB.
3. Package: `.\mvnw.cmd clean package -DskipTests` (or run `.\mvnw.cmd "-Dtest=client.ui.**" test`).
4. Run `target\G12_Server.jar` then `target\G12_Client.jar`.

Study bot answers need `HSTS_BOT_API_URL` (optional `HSTS_BOT_API_KEY`) on the server process — see `PERSON4_NOTES.md`.

## Recent additions

### Delete exam (ExamListView)

Teachers can delete draft or rejected exams from the exam list. A confirmation
dialog prevents accidental deletion. Once an exam has been submitted for
approval the delete button is disabled.

- Command: `DELETE_EXAM` (payload: Integer examId → SUCCESS: Integer deletedId)
- Server: `ExamService.delete` checks ownership and editable status; `ExamDAO.delete` removes questions then exam in one transaction.
- Session: `ExamListSession.requestDelete` / `canDeleteSelected`.

### Course name on exam edit (ExamBuilderView)

When editing a draft the course combo box now shows the real course name instead
of `Course #<id>`. The `Exam` entity carries a transient `courseName` field
populated by `ExamDAO` when loading exams.

### Questions visible on exam edit

Opening the builder for an existing draft no longer sends `GET_COURSES` (which
dropped the connection). Only `requestBank()` is sent, so the question bank
loads and the selected-questions list shows question text immediately.

### Release Exams UX + open-only scheduling (ExamReleaseView)

- Selected approved exam is shown as **Selected: {title} ({duration} min)**.
- Open date (`DatePicker`) and open time (`HH:mm`) are separate fields.
- Close time is **auto-computed** as open + exam duration (read-only preview).
- Released list shows exam title · code · release id.
- Approved exams that already have a release are annotated **already released** (still selectable for another sitting).

### Grade Exams sessions list (GradeExamsView)

Selecting a release loads execution summary counts **and** the list of student
sessions, then existing grades for that release. Teacher selects a session and
clicks **Grade selected session** (no manual session-ID typing). Auto-graded
rows appear in the Grades list with score and status (`AUTO_GRADED` / approved /
overridden).

A selection/`setAll` feedback loop that repeatedly re-fetched summary+sessions
was fixed with a syncing flag so list refreshes no longer re-fire load requests.

## Cross-person / Person 4 territory touched

These server/common changes were required for the UI above to work:

| Area | Change |
|------|--------|
| `ExecutionReportDAO.summary` | Fix `Timestamp` → `LocalDateTime` cast (summary was always failing) |
| `ExamRelease` | Transient `examTitle`; populated in `ExamReleaseDAO` list/getById |
| `ExamReleaseService` | Rejects release when close ≠ open + exam duration |
| `Message.Command` | New `GET_RELEASE_SESSIONS`, `GET_RELEASE_GRADES` |
| `ExamSessionDAO` | `getByRelease(releaseId)` |
| `GradeDAO` | `getByRelease(releaseId)` (includes AUTO_GRADED) |
| `ExamExecutionService` | `sessionsForRelease` (same auth as summary) |
| `GradingService` | `listByRelease` for teacher grade list |
| `HSTSServer` | Routes `GET_RELEASE_SESSIONS`, `GET_RELEASE_GRADES` |

## Exam display id

Until a 6-digit exam codec ships, lists show `#` + `baseId` via `ExamStatusLabel.displayId`.
