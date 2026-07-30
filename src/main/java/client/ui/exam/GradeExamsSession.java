package client.ui.exam;

import common.entities.ExamRelease;
import common.entities.ExamSession;
import common.entities.Grade;
import common.network.ApproveGradeRequest;
import common.network.ExamExecutionSummary;
import common.network.Message;
import common.network.Message.Command;
import common.network.OverrideGradeRequest;

import java.util.ArrayList;
import java.util.List;

/** Testable state for grading exams (scenario 8). */
public class GradeExamsSession {

    private final List<ExamRelease> releases = new ArrayList<>();
    private final List<ExamSession> sessions = new ArrayList<>();
    private final List<Grade> grades = new ArrayList<>();
    private ExamRelease selectedRelease;
    private ExamSession selectedSession;
    private ExamExecutionSummary executionSummary;
    private Grade selectedGrade;
    private String statusText = "";
    private String lastError;
    private boolean awaitingReleases;
    private boolean awaitingSummary;
    private boolean awaitingSessions;
    private boolean awaitingGrades;
    private boolean awaitingGrade;
    private boolean awaitingApprove;
    private boolean awaitingOverride;

    public List<ExamRelease> getReleases() {
        return List.copyOf(releases);
    }

    public List<ExamSession> getSessions() {
        return List.copyOf(sessions);
    }

    public List<Grade> getGrades() {
        return List.copyOf(grades);
    }

    public ExamRelease getSelectedRelease() {
        return selectedRelease;
    }

    public void setSelectedRelease(ExamRelease selectedRelease) {
        this.selectedRelease = selectedRelease;
    }

    public ExamSession getSelectedSession() {
        return selectedSession;
    }

    public void setSelectedSession(ExamSession selectedSession) {
        this.selectedSession = selectedSession;
    }

    public ExamExecutionSummary getExecutionSummary() {
        return executionSummary;
    }

    public Grade getSelectedGrade() {
        return selectedGrade;
    }

    public void setSelectedGrade(Grade selectedGrade) {
        this.selectedGrade = selectedGrade;
    }

    public String getStatusText() {
        return statusText;
    }

    public String getLastError() {
        return lastError;
    }

    public boolean isAwaitingSessions() {
        return awaitingSessions;
    }

    public Message requestReleasedExams() {
        awaitingReleases = true;
        statusText = "Loading releases…";
        lastError = null;
        return new Message(Command.GET_RELEASED_EXAMS);
    }

    public Message requestExecutionSummary(int releaseId) {
        if (releaseId <= 0) {
            lastError = "Select a release first.";
            return null;
        }
        awaitingSummary = true;
        statusText = "Loading execution summary…";
        lastError = null;
        return new Message(Command.GET_EXECUTION_SUMMARY, releaseId);
    }

    public Message requestSessions(int releaseId) {
        if (releaseId <= 0) {
            lastError = "Select a release first.";
            return null;
        }
        awaitingSessions = true;
        statusText = "Loading sessions…";
        lastError = null;
        return new Message(Command.GET_RELEASE_SESSIONS, releaseId);
    }

    public Message requestGrades(int releaseId) {
        if (releaseId <= 0) {
            lastError = "Select a release first.";
            return null;
        }
        awaitingGrades = true;
        statusText = "Loading grades…";
        lastError = null;
        return new Message(Command.GET_RELEASE_GRADES, releaseId);
    }

    public Message requestGradeAuto(int sessionId) {
        if (sessionId <= 0) {
            lastError = "Select a session to grade.";
            return null;
        }
        awaitingGrade = true;
        statusText = "Grading session " + sessionId + "…";
        lastError = null;
        return new Message(Command.GRADE_EXAM_AUTO, sessionId);
    }

    public Message requestApprove(int gradeId, String comment) {
        if (gradeId <= 0) {
            lastError = "Select or grade a session first.";
            return null;
        }
        awaitingApprove = true;
        statusText = "Approving grade…";
        lastError = null;
        if (comment != null && !comment.isBlank()) {
            return new Message(Command.APPROVE_GRADE,
                    new ApproveGradeRequest(gradeId, comment.trim()));
        }
        return new Message(Command.APPROVE_GRADE, gradeId);
    }

    public Message requestOverride(int gradeId, int newScore, String justification) {
        if (gradeId <= 0) {
            lastError = "Select or grade a session first.";
            return null;
        }
        if (newScore < 0 || newScore > 100) {
            lastError = "Score must be between 0 and 100.";
            return null;
        }
        if (justification == null || justification.isBlank()) {
            lastError = "Override justification is required.";
            return null;
        }
        awaitingOverride = true;
        statusText = "Overriding grade…";
        lastError = null;
        return new Message(Command.OVERRIDE_GRADE,
                new OverrideGradeRequest(gradeId, newScore, justification.trim()));
    }

    public void onServerMessage(Message msg) {
        if (msg == null) return;
        switch (msg.getCommand()) {
            case SUCCESS -> {
                Object payload = msg.getPayload();
                if (awaitingGrade && payload instanceof Grade grade) {
                    awaitingGrade = false;
                    replaceGrade(grade);
                    selectedGrade = grade;
                    statusText = "Auto-graded: " + grade.getAutoScore() + " points.";
                } else if ((awaitingApprove || awaitingOverride) && payload instanceof Grade grade) {
                    awaitingApprove = false;
                    awaitingOverride = false;
                    replaceGrade(grade);
                    selectedGrade = grade;
                    statusText = "Grade updated (" + grade.getStatus() + ").";
                } else if (awaitingSummary && payload instanceof ExamExecutionSummary summary) {
                    awaitingSummary = false;
                    executionSummary = summary;
                    statusText = "Execution summary loaded.";
                } else if (awaitingSessions && payload instanceof List<?> list
                        && (list.isEmpty() || list.get(0) instanceof ExamSession)) {
                    awaitingSessions = false;
                    sessions.clear();
                    for (Object o : list) {
                        if (o instanceof ExamSession s) sessions.add(s);
                    }
                    if (selectedSession != null) {
                        int keep = selectedSession.getId();
                        selectedSession = sessions.stream()
                                .filter(s -> s.getId() == keep)
                                .findFirst()
                                .orElse(null);
                    }
                    statusText = sessions.size() + " session(s).";
                } else if (awaitingGrades && payload instanceof List<?> list
                        && (list.isEmpty() || list.get(0) instanceof Grade)) {
                    awaitingGrades = false;
                    grades.clear();
                    for (Object o : list) {
                        if (o instanceof Grade g) grades.add(g);
                    }
                    if (selectedGrade != null) {
                        int keep = selectedGrade.getId();
                        selectedGrade = grades.stream()
                                .filter(g -> g.getId() == keep)
                                .findFirst()
                                .orElse(null);
                    }
                    statusText = grades.size() + " grade(s).";
                } else if (awaitingReleases && payload instanceof List<?> list) {
                    awaitingReleases = false;
                    releases.clear();
                    for (Object o : list) {
                        if (o instanceof ExamRelease r) releases.add(r);
                    }
                    statusText = releases.size() + " release(s).";
                }
            }
            case ERROR -> {
                awaitingReleases = false;
                awaitingSummary = false;
                awaitingSessions = false;
                awaitingGrades = false;
                awaitingGrade = false;
                awaitingApprove = false;
                awaitingOverride = false;
                lastError = String.valueOf(msg.getPayload());
                statusText = "Server error.";
            }
            default -> statusText = "Unexpected: " + msg.getCommand();
        }
    }

    private void replaceGrade(Grade grade) {
        for (int i = 0; i < grades.size(); i++) {
            if (grades.get(i).getId() == grade.getId()
                    || grades.get(i).getSessionId() == grade.getSessionId()) {
                grades.set(i, grade);
                return;
            }
        }
        grades.add(grade);
    }
}
