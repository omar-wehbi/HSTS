package client.ui.exam;

import common.entities.Exam;
import common.entities.ExamStatus;
import common.network.Message;
import common.network.Message.Command;

import java.util.ArrayList;
import java.util.List;

/**
 * Testable state + protocol helpers for the teacher exam list (Person 5).
 * The JavaFX {@code ExamListView} delegates here so logic is unit-tested
 * without a scene graph.
 */
public class ExamListSession {

    private final List<Exam> exams = new ArrayList<>();
    private Exam selected;
    private String statusText = "";
    private String lastError;
    private boolean awaitingList;
    private boolean awaitingSubmit;

    public List<Exam> getExams() {
        return List.copyOf(exams);
    }

    public Exam getSelected() {
        return selected;
    }

    public void setSelected(Exam selected) {
        this.selected = selected;
    }

    public String getStatusText() {
        return statusText;
    }

    public String getLastError() {
        return lastError;
    }

    public boolean isAwaitingList() {
        return awaitingList;
    }

    public boolean isAwaitingSubmit() {
        return awaitingSubmit;
    }

    /** Message to load the teacher's exams. */
    public Message requestMyExams() {
        awaitingList = true;
        statusText = "Loading your exams…";
        lastError = null;
        return new Message(Command.GET_MY_EXAMS);
    }

    /** Message to submit the selected exam (must be DRAFT or REJECTED). */
    public Message requestSubmit() {
        if (selected == null) {
            lastError = "Select an exam first.";
            return null;
        }
        if (selected.getStatus() != ExamStatus.DRAFT
                && selected.getStatus() != ExamStatus.REJECTED) {
            lastError = "Only draft or rejected exams can be submitted.";
            return null;
        }
        awaitingSubmit = true;
        statusText = "Submitting for approval…";
        lastError = null;
        return new Message(Command.SUBMIT_EXAM_FOR_APPROVAL, selected.getId());
    }

    public boolean canSubmitSelected() {
        return selected != null
                && (selected.getStatus() == ExamStatus.DRAFT
                || selected.getStatus() == ExamStatus.REJECTED);
    }

    public boolean canEditSelected() {
        return selected != null
                && (selected.getStatus() == ExamStatus.DRAFT
                || selected.getStatus() == ExamStatus.REJECTED);
    }

    /** Handles a SUCCESS / ERROR {@link Message} for this screen. */
    @SuppressWarnings("unchecked")
    public void onServerMessage(Message msg) {
        if (msg == null) return;
        switch (msg.getCommand()) {
            case SUCCESS -> {
                Object payload = msg.getPayload();
                if (awaitingSubmit && payload instanceof Exam updated) {
                    awaitingSubmit = false;
                    replaceOrAdd(updated);
                    selected = updated;
                    statusText = "Submitted for approval.";
                } else if (payload instanceof List<?> list) {
                    awaitingList = false;
                    exams.clear();
                    for (Object o : list) {
                        if (o instanceof Exam e) exams.add(e);
                    }
                    statusText = exams.size() + (exams.size() == 1 ? " exam." : " exams.");
                    if (selected != null) {
                        int keep = selected.getBaseId() > 0 ? selected.getBaseId() : selected.getId();
                        selected = exams.stream()
                                .filter(e -> (e.getBaseId() > 0 ? e.getBaseId() : e.getId()) == keep)
                                .findFirst()
                                .orElse(null);
                    }
                } else if (payload instanceof Exam single) {
                    awaitingList = false;
                    awaitingSubmit = false;
                    replaceOrAdd(single);
                    selected = single;
                    statusText = "Exam updated.";
                }
            }
            case ERROR -> {
                awaitingList = false;
                awaitingSubmit = false;
                lastError = String.valueOf(msg.getPayload());
                statusText = "Server error.";
            }
            default -> statusText = "Unexpected: " + msg.getCommand();
        }
    }

    private void replaceOrAdd(Exam exam) {
        for (int i = 0; i < exams.size(); i++) {
            Exam existing = exams.get(i);
            int a = existing.getBaseId() > 0 ? existing.getBaseId() : existing.getId();
            int b = exam.getBaseId() > 0 ? exam.getBaseId() : exam.getId();
            if (a == b) {
                exams.set(i, exam);
                return;
            }
        }
        exams.add(exam);
    }
}
