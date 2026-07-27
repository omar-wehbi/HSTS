# Person 4 — Scenarios 5, 6 and 7

## SQL order
Run `schema/V3_exams.sql`, then `schema/V4_exam_releases.sql`, then `schema/V6_exam_execution.sql`.

## Scenario 6
`START_EXAM_SESSION` accepts `StartExamRequest(code, idNumber)`. The server verifies the logged-in user is a student, the ID matches, the release is currently open, the student is enrolled, and no earlier attempt exists. It returns an `ExamForm` that deliberately omits every question's correct answer.

The deadline is server-controlled and is the earlier of `start + exam duration` and the release close time. `SUBMIT_ANSWERS` validates ownership, question IDs, duplicates and options 1–4. A late submission is stored as `TIMED_OUT`, which prevents the client clock from bypassing the limit.

`ExamExecutionService.isBotLockedFor(student)` is ready for scenarios 13–14: the study-bot handler must reject requests while an `IN_PROGRESS` unexpired session exists.

## Scenario 7
`EXTEND_EXAM_TIME` accepts `ExtendExamTimeRequest(releaseId, extraMinutes)`. Only the teacher who released the exam can use it. It extends every active, non-expired session for that release and records cumulative extension minutes. Allowed extension: 1–180 minutes per request.

## Version 0.2 — Scenario 8 grading

Added server-side computerized grading with three commands:

- `GRADE_EXAM_AUTO` — grades one submitted/timed-out session using the exact question versions and exam points.
- `APPROVE_GRADE` — the authoring teacher accepts the automatic score.
- `OVERRIDE_GRADE` — the authoring teacher changes the score with a mandatory written justification.

Run `src/main/resources/schema/V7_grading.sql` after `V6_exam_execution.sql`.
The student-results visibility rule is prepared through `Grade.isVisibleToStudent()` and will be used in Scenario 9.

## Scenarios 9-10 — Results (version 0.3)

### Protocol
- `GET_STUDENT_RESULTS` — student receives only approved/overridden grades belonging to the logged-in account.
- `GET_CHECKED_EXAM` — payload is a grade ID; returns the checked form only to its student and only after approval.
- `GET_EXAM_STATISTICS` — payload is an exam ID; only the authoring teacher receives the result table and histogram.

### Data returned
- `StudentResultSummary`: exam title, effective score, approval state and submission time.
- `CheckedExamResult`: selected answer, correct answer, correctness and question points; it cannot expose another student's attempt.
- `TeacherExamResults`: rows for computerized grades plus chart-ready bins `0-9` through `90-100`, mean, median, minimum and maximum.

No schema migration is needed for scenarios 9-10; they read the `Grades`, `ExamSessions`, `StudentAnswers`, `Exams`, `ExamQuestions`, and `Questions` tables created by earlier migrations.

## 0.3.1 correctness fixes

- Late submissions are rejected; answers sent after the authoritative deadline are not saved.
- Overdue `IN_PROGRESS` sessions are synchronized to `TIMED_OUT` before execution, grading, result, extension, and bot-lock operations.
- Teachers may read only sessions belonging to exams they authored.
- Extension requests are accepted only while the release window is live.
- Old prebuilt JARs were removed because they did not contain the current source. Rebuild with `mvn clean package`.

Fixed package version: HTST-person4-0.3.1

## Scenarios 11–12 — Principal access and reports

Commands:
- `GET_PRINCIPAL_DATA` returns a read-only catalog of teachers, courses, students, and the approved-result count.
- `GET_REPORT` accepts `PrincipalReportRequest` and compares `TEACHER`, `COURSE`, or `STUDENT` groups.

Only `PRINCIPAL` users are authorized. Reports use approved/overridden grades only and return count, mean, median, minimum, maximum, and deciles D1–D9. Percentiles use linear interpolation at index `(n - 1) * p`. No database migration is required.

## Scenarios 11-12 hardening (0.4.1)
- Report entity IDs are checked against the principal catalog; unknown IDs return a clear protocol error.
- Valid teachers, courses, and students with no approved attempts are included with count 0, mean/median 0, null min/max, and zero deciles.
- Report count means number of approved/overridden graded attempts, not number of unique students.
- DAO failures now log the full server-side exception while returning a safe generic message to the client.
- Query-result mapping rejects missing IDs, names, and scores instead of displaying the literal string "null".

## Scenarios 13–14 — Study bot (version 0.5)

Migration: `src/main/resources/schema/V8_study_bot.sql`.

Implemented server commands:
- `CREATE_STUDY_BOT`
- `ADD_STUDY_BOT_SOURCE`
- `UPDATE_STUDY_BOT_SOURCE`
- `GET_STUDY_BOT_SOURCES`
- `ASK_STUDY_BOT`
- `GET_MY_STUDY_BOT_HISTORY`
- `GET_STUDY_BOT_USAGE`

Rules enforced:
- Teachers may create/edit sources only for courses assigned through `CourseTeachers`.
- The V8 migration initializes `CourseTeachers` from existing exam authorship.
- Co-teachers assigned to the same course can edit the same bot sources.
- Students must be enrolled in the course.
- Bot use is blocked while the student has an active exam session.
- Students receive only their own Q&A history.
- Teachers receive aggregate usage with totals and questions/timestamps, but no student IDs or names.
- Answers come through `ExternalStudyBotApiAdapter`; no LLM is implemented locally.

External API configuration:
- `HSTS_BOT_API_URL` (required)
- `HSTS_BOT_API_KEY` (optional bearer token)

The adapter sends JSON fields `question` and `context`, and accepts an `answer` or `response` string in the API response.

## Study-bot completion and hardening (version 0.5.1)

The study-bot implementation now matches the project specification more closely:

- Bot creation uses `CreateStudyBotRequest(courseId, name, includeQuestionBank)` and stores the bot name.
- Bots are created inactive; assigned teachers explicitly activate/deactivate them with `SET_STUDY_BOT_AVAILABILITY`.
- `GET_STUDY_BOT` returns name, course, state, creator, creation time, question-bank setting and source count.
- Sources support free text and uploaded PDF/DOC/DOCX documents. Files are limited to 5 MB and extracted text to 200,000 characters.
- The current course question bank can be included automatically in API context.
- Bot access is blocked only by an active exam in the same course.
- Source/history inserts use `LAST_INSERT_ID()` in the same database session, avoiding latest-row race conditions.
- External JSON uses Jackson, context is capped at 50,000 characters, and raw API errors are logged but not exposed to students.
- Failed/no-answer attempts are stored in personal history with status `NO_ANSWER`.
- Teacher usage includes anonymous recent questions and top common questions.
- Assigned teachers may delete obsolete sources.
- Bot creation no longer grants teacher-course membership. `CourseTeachers` must be populated from the external user-management source; exam authors are only bootstrapped for prototype compatibility.

Fresh databases should run the final `V8_study_bot.sql`. Databases that already ran the original V8 should run `V9_study_bot_upgrade.sql` once.

## PDF-alignment upgrade (Scenarios 5–12)
Run `V10_scenarios_5_12_pdf_alignment.sql` after V9. It adds alphanumeric execution codes, immutable released-question snapshots, answer autosave, persisted actual duration, teacher feedback, per-release execution summaries/statistics, complete principal read-only projections, and exam-by-exam principal comparison reports.
