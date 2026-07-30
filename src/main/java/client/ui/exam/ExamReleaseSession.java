package client.ui.exam;

import common.entities.Exam;
import common.entities.ExamRelease;
import common.entities.ExamStatus;
import common.network.ExamExecutionSummary;
import common.network.ExamReleaseRequest;
import common.network.ExtendExamTimeRequest;
import common.network.Message;
import common.network.Message.Command;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Testable state for releasing exams and extending live sessions (scenarios 5, 7). */
public class ExamReleaseSession {

    private final List<Exam> approvedExams = new ArrayList<>();
    private final List<ExamRelease> releases = new ArrayList<>();
    private Exam selectedExam;
    private ExamRelease selectedRelease;
    private ExamExecutionSummary executionSummary;
    private String statusText = "";
    private String lastError;
    private boolean awaitingApproved;
    private boolean awaitingReleases;
    private boolean awaitingRelease;
    private boolean awaitingExtend;
    private boolean awaitingSummary;

    public List<Exam> getApprovedExams() {
        return List.copyOf(approvedExams);
    }

    public List<ExamRelease> getReleases() {
        return List.copyOf(releases);
    }

    public Exam getSelectedExam() {
        return selectedExam;
    }

    public void setSelectedExam(Exam selectedExam) {
        this.selectedExam = selectedExam;
    }

    public ExamRelease getSelectedRelease() {
        return selectedRelease;
    }

    public void setSelectedRelease(ExamRelease selectedRelease) {
        this.selectedRelease = selectedRelease;
    }

    public ExamExecutionSummary getExecutionSummary() {
        return executionSummary;
    }

    public String getStatusText() {
        return statusText;
    }

    public String getLastError() {
        return lastError;
    }

    public boolean isAwaitingApproved() {
        return awaitingApproved;
    }

    public boolean isAwaitingReleases() {
        return awaitingReleases;
    }

    public Message requestApprovedExams() {
        awaitingApproved = true;
        statusText = "Loading approved exams…";
        lastError = null;
        return new Message(Command.GET_MY_EXAMS);
    }

    public Message requestReleasedExams() {
        awaitingReleases = true;
        statusText = "Loading released exams…";
        lastError = null;
        return new Message(Command.GET_RELEASED_EXAMS);
    }

    public Message requestRelease(int examId, String code,
                                  LocalDateTime open, int durationMinutes) {
        if (examId <= 0) {
            lastError = "Select an approved exam.";
            return null;
        }
        if (durationMinutes <= 0) {
            lastError = "Exam duration must be positive.";
            return null;
        }
        if (open == null) {
            lastError = "Open date and time are required.";
            return null;
        }
        LocalDateTime close = open.plusMinutes(durationMinutes);
        String validation = validateRelease(code, open, close);
        if (validation != null) {
            lastError = validation;
            return null;
        }
        awaitingRelease = true;
        statusText = "Releasing exam…";
        lastError = null;
        return new Message(Command.RELEASE_EXAM,
                new ExamReleaseRequest(examId, code.trim(), open, close));
    }

    /** Computes close = open + durationMinutes, or null if inputs are invalid. */
    public static LocalDateTime computeClose(LocalDateTime open, int durationMinutes) {
        if (open == null || durationMinutes <= 0) return null;
        return open.plusMinutes(durationMinutes);
    }

    public boolean isExamAlreadyReleased(int examId) {
        for (ExamRelease r : releases) {
            if (r.getExamId() == examId) return true;
        }
        return false;
    }

    public Message requestExtend(int releaseId, int extraMinutes) {
        if (releaseId <= 0) {
            lastError = "Select a release first.";
            return null;
        }
        if (extraMinutes < 1 || extraMinutes > 180) {
            lastError = "Extension must be between 1 and 180 minutes.";
            return null;
        }
        awaitingExtend = true;
        statusText = "Extending exam time…";
        lastError = null;
        return new Message(Command.EXTEND_EXAM_TIME,
                new ExtendExamTimeRequest(releaseId, extraMinutes));
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

    static String validateRelease(String code, LocalDateTime open, LocalDateTime close) {
        if (code == null || !code.trim().matches("[A-Za-z0-9]{4}")) {
            return "Execution code must be exactly 4 letters or digits.";
        }
        if (open == null || close == null) {
            return "Open and close times are required.";
        }
        if (!close.isAfter(open)) {
            return "Close time must be after open time.";
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public void onServerMessage(Message msg) {
        if (msg == null) return;
        switch (msg.getCommand()) {
            case SUCCESS -> {
                Object payload = msg.getPayload();
                if (awaitingRelease && payload instanceof ExamRelease release) {
                    awaitingRelease = false;
                    releases.add(release);
                    selectedRelease = release;
                    statusText = "Exam released with code " + release.getExecutionCode() + ".";
                } else if (awaitingExtend && payload instanceof Number n) {
                    awaitingExtend = false;
                    statusText = "Extended " + n.intValue() + " active session(s).";
                } else if (awaitingSummary && payload instanceof ExamExecutionSummary summary) {
                    awaitingSummary = false;
                    executionSummary = summary;
                    statusText = "Execution summary loaded.";
                } else if (payload instanceof List<?> list) {
                    if (!list.isEmpty() && list.get(0) instanceof Exam) {
                        awaitingApproved = false;
                        approvedExams.clear();
                        for (Object o : list) {
                            if (o instanceof Exam e && e.getStatus() == ExamStatus.APPROVED) {
                                approvedExams.add(e);
                            }
                        }
                        statusText = approvedExams.size() + " approved exam(s).";
                    } else if (!list.isEmpty() && list.get(0) instanceof ExamRelease) {
                        awaitingReleases = false;
                        releases.clear();
                        for (Object o : list) {
                            if (o instanceof ExamRelease r) releases.add(r);
                        }
                        statusText = releases.size() + " release(s).";
                        if (selectedRelease != null) {
                            int keep = selectedRelease.getId();
                            selectedRelease = releases.stream()
                                    .filter(r -> r.getId() == keep)
                                    .findFirst()
                                    .orElse(null);
                        }
                    } else if (awaitingApproved) {
                        awaitingApproved = false;
                        approvedExams.clear();
                        statusText = "0 approved exam(s).";
                    } else if (awaitingReleases) {
                        awaitingReleases = false;
                        releases.clear();
                        statusText = "0 release(s).";
                    }
                } else if (payload instanceof ExamRelease release) {
                    awaitingReleases = false;
                    awaitingRelease = false;
                    replaceRelease(release);
                    selectedRelease = release;
                    statusText = "Release updated.";
                }
            }
            case ERROR -> {
                awaitingApproved = false;
                awaitingReleases = false;
                awaitingRelease = false;
                awaitingExtend = false;
                awaitingSummary = false;
                lastError = String.valueOf(msg.getPayload());
                statusText = "Server error.";
            }
            default -> statusText = "Unexpected: " + msg.getCommand();
        }
    }

    private void replaceRelease(ExamRelease release) {
        for (int i = 0; i < releases.size(); i++) {
            if (releases.get(i).getId() == release.getId()) {
                releases.set(i, release);
                return;
            }
        }
        releases.add(release);
    }
}
