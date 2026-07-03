# Database migrations — how to add your tables

Each feature owner adds tables in a **numbered migration file** in this folder, so we
never edit the same file and merges stay conflict-free. (Layout owned by Person 1.)

## The convention

1. Take the **next free number** and name the file after your module:
   - `V1_question_bank.sql` — Courses, Questions (Person 2)
   - `V2_auth.sql` — Users, Enrollments (Person 1)
   - `V3_exams.sql` — exams, exam_questions, approval… (**Person 3 — yours**)
   - `V4_execution.sql` — releases, sessions, answers, grades… (**Person 4 — yours**)
   - `V5_...` — and so on.
2. Migrations contain **DDL only** (`CREATE TABLE IF NOT EXISTS …`) — **no data** —
   so they are idempotent and safe to run repeatedly in number order.
3. Reference other modules' tables with foreign keys freely (e.g. your
   `exam_questions.question_id → Questions.id`); higher numbers may depend on
   lower ones, never the reverse.
4. **Also add your demo data** to `../seed_test_scenarios.sql` (keep it
   deterministic: fixed ids, wipe-then-insert) so the one-command seed keeps
   covering every scenario.

## Setting up a machine from zero

```
mysql -u root -p < src/main/resources/seed_test_scenarios.sql
```

creates the database, every table, and deterministic demo data (login accounts:
teacher / coord / principal / maya / noa — password `1234`). Re-run any time to
reset. Then run the tests: `mvnw test` (needs your password in `server.properties`).
