package client.ui.exam;

import common.entities.Course;
import common.entities.Exam;
import common.entities.ExamQuestion;
import common.entities.ExamStatus;
import common.entities.Question;
import common.network.AutoExamRequest;
import common.network.Message;
import common.network.Message.Command;
import common.network.QuestionFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Testable builder state for manual + automatic exam creation (Person 5).
 */
public class ExamBuilderSession {

    private final List<Course> courses = new ArrayList<>();
    private final List<Question> bank = new ArrayList<>();
    private final List<ExamQuestion> selectedQuestions = new ArrayList<>();

    private Integer editingExamId;
    private Integer editingBaseId;
    private int courseId;
    private int teacherId;
    private String title = "";
    private int durationMinutes = 60;
    private String studentInstructions = "";
    private String teacherNotes = "";

    private String statusText = "";
    private String lastError;
    private boolean awaitingCourses;
    private boolean awaitingBank;
    private boolean awaitingSave;
    private boolean awaitingAuto;
    private Exam lastSaved;

    public void setTeacherId(int teacherId) {
        this.teacherId = teacherId;
    }

    public int getTeacherId() {
        return teacherId;
    }

    public void setCourseId(int courseId) {
        this.courseId = courseId;
    }

    public int getCourseId() {
        return courseId;
    }

    public void setTitle(String title) {
        this.title = title == null ? "" : title;
    }

    public String getTitle() {
        return title;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setStudentInstructions(String studentInstructions) {
        this.studentInstructions = studentInstructions == null ? "" : studentInstructions;
    }

    public String getStudentInstructions() {
        return studentInstructions;
    }

    public void setTeacherNotes(String teacherNotes) {
        this.teacherNotes = teacherNotes == null ? "" : teacherNotes;
    }

    public String getTeacherNotes() {
        return teacherNotes;
    }

    public List<Course> getCourses() {
        return List.copyOf(courses);
    }

    public List<Question> getBank() {
        return List.copyOf(bank);
    }

    public List<ExamQuestion> getSelectedQuestions() {
        return List.copyOf(selectedQuestions);
    }

    public int getPointsTotal() {
        return ExamFormValidator.totalPoints(selectedQuestions);
    }

    public String pointsBadgeText() {
        return getPointsTotal() + " / " + ExamFormValidator.REQUIRED_TOTAL_POINTS;
    }

    public String getStatusText() {
        return statusText;
    }

    public String getLastError() {
        return lastError;
    }

    public Exam getLastSaved() {
        return lastSaved;
    }

    public boolean isEditing() {
        return editingBaseId != null;
    }

    public Integer getEditingExamId() {
        return editingExamId;
    }

    public boolean isAwaitingSave() {
        return awaitingSave;
    }

    public boolean isAwaitingAuto() {
        return awaitingAuto;
    }

    public boolean isAwaitingBank() {
        return awaitingBank;
    }

    public boolean isAwaitingCourses() {
        return awaitingCourses;
    }

    /** Prefill from an existing exam (edit / new version). */
    public void loadExam(Exam exam) {
        if (exam == null) return;
        editingExamId = exam.getId();
        editingBaseId = exam.getBaseId() > 0 ? exam.getBaseId() : exam.getId();
        courseId = exam.getCourseId();
        teacherId = exam.getTeacherId();
        title = exam.getTitle() == null ? "" : exam.getTitle();
        durationMinutes = exam.getDurationMinutes();
        studentInstructions = exam.getStudentInstructions() == null
                ? "" : exam.getStudentInstructions();
        teacherNotes = exam.getTeacherNotes() == null ? "" : exam.getTeacherNotes();
        selectedQuestions.clear();
        selectedQuestions.addAll(exam.getQuestions());
        statusText = "Editing " + ExamStatusLabel.displayId(exam);
    }

    public Message requestCourses() {
        awaitingCourses = true;
        return new Message(Command.GET_COURSES);
    }

    public Message requestBank() {
        awaitingBank = true;
        statusText = "Loading question bank…";
        if (courseId > 0) {
            return new Message(Command.GET_QUESTIONS_FILTERED,
                    new QuestionFilter(courseId, null, null));
        }
        return new Message(Command.GET_QUESTIONS);
    }

    public void addQuestion(Question question, int points) {
        if (question == null) return;
        for (ExamQuestion eq : selectedQuestions) {
            if (eq.getQuestionId() == question.getId()) {
                lastError = "That question is already on the exam.";
                return;
            }
        }
        lastError = null;
        selectedQuestions.add(new ExamQuestion(question.getId(), points,
                selectedQuestions.size() + 1));
        reindexPositions();
    }

    public void removeQuestionAt(int index) {
        if (index < 0 || index >= selectedQuestions.size()) return;
        selectedQuestions.remove(index);
        reindexPositions();
    }

    public void setPointsAt(int index, int points) {
        if (index < 0 || index >= selectedQuestions.size()) return;
        selectedQuestions.get(index).setPoints(points);
    }

    private void reindexPositions() {
        for (int i = 0; i < selectedQuestions.size(); i++) {
            selectedQuestions.get(i).setPosition(i + 1);
        }
    }

    public Exam buildExamPayload() {
        Exam exam = new Exam();
        exam.setCourseId(courseId);
        exam.setTeacherId(teacherId);
        exam.setTitle(title.trim());
        exam.setDurationMinutes(durationMinutes);
        exam.setStudentInstructions(blankToNull(studentInstructions));
        exam.setTeacherNotes(blankToNull(teacherNotes));
        exam.setStatus(ExamStatus.DRAFT);
        exam.setQuestions(new ArrayList<>(selectedQuestions));
        if (editingBaseId != null) {
            exam.setBaseId(editingBaseId);
        }
        if (editingExamId != null) {
            exam.setId(editingExamId);
        }
        return exam;
    }

    /** Validates and returns CREATE_EXAM or UPDATE_EXAM, or null on client error. */
    public Message requestSave() {
        Exam exam = buildExamPayload();
        String invalid = ExamFormValidator.validateManualExam(exam);
        if (invalid != null) {
            lastError = invalid;
            return null;
        }
        lastError = null;
        awaitingCourses = false;
        awaitingBank = false;
        awaitingSave = true;
        awaitingAuto = false;
        statusText = isEditing() ? "Saving new version…" : "Creating exam…";
        return isEditing()
                ? new Message(Command.UPDATE_EXAM, exam)
                : new Message(Command.CREATE_EXAM, exam);
    }

    public Message requestAutoGenerate(AutoExamRequest request) {
        String invalid = ExamFormValidator.validateAutoRequest(request);
        if (invalid != null) {
            lastError = invalid;
            return null;
        }
        lastError = null;
        // Drop stale bank/course waits so their responses/errors cannot swallow the
        // auto-generate result (or clear awaitingAuto before it arrives).
        awaitingCourses = false;
        awaitingBank = false;
        awaitingSave = false;
        awaitingAuto = true;
        statusText = "Generating exam…";
        return new Message(Command.GENERATE_EXAM_AUTO, request);
    }

    /** Clears in-flight request flags after a socket failure. */
    public void onConnectionLost(String reason) {
        awaitingCourses = false;
        awaitingBank = false;
        awaitingSave = false;
        awaitingAuto = false;
        lastError = reason;
        statusText = "Connection lost.";
    }

    public void onServerMessage(Message msg) {
        if (msg == null) return;
        switch (msg.getCommand()) {
            case SUCCESS -> handleSuccess(msg.getPayload());
            case ERROR -> handleError(msg.getPayload());
            default -> { }
        }
    }

    private void handleError(Object payload) {
        lastError = String.valueOf(payload);
        if (awaitingAuto) {
            awaitingAuto = false;
            statusText = "Auto-generate failed.";
        } else if (awaitingSave) {
            awaitingSave = false;
            statusText = "Save failed.";
        } else if (awaitingBank) {
            awaitingBank = false;
            statusText = "Could not load question bank.";
        } else if (awaitingCourses) {
            awaitingCourses = false;
            statusText = "Could not load courses.";
        } else {
            statusText = "Server error.";
        }
    }

    private void handleSuccess(Object payload) {
        if (awaitingCourses && payload instanceof List<?> list) {
            awaitingCourses = false;
            courses.clear();
            for (Object o : list) {
                if (o instanceof Course c) courses.add(c);
            }
            if (courseId <= 0 && !courses.isEmpty()) {
                courseId = courses.get(0).getId();
            }
            return;
        }
        if (awaitingBank && payload instanceof List<?> list) {
            awaitingBank = false;
            bank.clear();
            for (Object o : list) {
                if (o instanceof Question q) bank.add(q);
            }
            statusText = "Bank: " + bank.size() + " questions.";
            return;
        }
        if ((awaitingSave || awaitingAuto) && payload instanceof Exam saved) {
            boolean wasAuto = awaitingAuto;
            awaitingSave = false;
            awaitingAuto = false;
            lastSaved = saved;
            loadExam(saved);
            statusText = wasAuto ? "Exam generated." : "Exam saved.";
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }
}
