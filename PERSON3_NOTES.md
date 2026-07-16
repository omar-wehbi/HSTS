# Exam Module — Person 3 (Hasan)

This module implements **Scenarios 3 and 4** of the HSTS project:
exam building, automatic generation and coordinator approval.

---

## 1. What was implemented

### Exam entities

- `Exam`
- `ExamQuestion`
- `ExamStatus`

These classes represent an exam, its questions, points, order, status and version.

### Exam building

Teachers can:

- Create an exam manually
- Generate an exam automatically
- Select questions by topic and difficulty
- Set exam duration
- Add student instructions
- Add teacher-only notes
- Edit an existing exam

Editing an exam creates a **new version** while the previous version remains stored.

### Automatic generation rules

The automatic generator:

- Uses only current questions
- Selects questions from the requested course
- Filters by topic and difficulty
- Prevents duplicate questions
- Requires the total score to equal 100
- Returns an error when the question pool is too small

### Approval workflow

A teacher can submit a draft exam for approval.

A coordinator can:

- Approve the exam
- Reject the exam
- Store a written rejection reason

---

## 2. Main files

### Server logic

- `ExamService.java`
- `ExamValidator.java`
- `AutoExamGenerator.java`

### Data layer

- `ExamDAO.java`
- `schema/V3_exams.sql`

### Network DTOs

- `AutoExamRequest.java`
- `AutoExamRequirement.java`
- `ExamRejectionRequest.java`

### Integration changes

- Exam commands added to `Message.java`
- Exam routing added to `HSTSServer.java`
- Exam entities registered in `HibernateUtil.java`
- Uses the `QuestionSource` interface from Person 2

---

## 3. Tests

Implemented:

- `ExamValidatorTest`
- `AutoExamGeneratorTest`
- `ExamServiceTest`

The tests cover:

- Input validation
- 100-point rule
- Duplicate questions
- Topic and difficulty filtering
- Insufficient question pool
- Teacher and coordinator permissions
- Exam creation and versioning
- Submission, approval and rejection

---

## 4. Verification

```text
Tests run: 201
Failures: 0
Errors: 0

BUILD SUCCESS
```

---

## 5. Notes for the team

- Only teachers can create, edit and submit exams.
- Only coordinators can approve or reject exams.
- Rejection reasons are stored.
- Automatic generation fails cleanly if there are not enough matching questions.
- The module is ready for the JavaFX screens implemented by Person 5.