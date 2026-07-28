package client.ui.exam;

import common.entities.Exam;
import common.entities.ExamStatus;
import common.network.ExamRejectionRequest;
import common.network.Message;
import common.network.Message.Command;

import java.util.ArrayList;
import java.util.List;

/**
 * Coordinator pending-exam session (scenario 4).
 */
public class ExamApprovalSession {

    private final List<Exam> pending = new ArrayList<>();
    private Exam selected;
    private String statusText = "";
    private String lastError;
    private boolean awaitingList;
    private boolean awaitingAction;

    public List<Exam> getPending() {
        return List.copyOf(pending);
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

    public Message requestPending() {
        awaitingList = true;
        statusText = "Loading pending exams…";
        lastError = null;
        return new Message(Command.GET_PENDING_EXAMS);
    }

    public Message requestApprove() {
        if (selected == null) {
            lastError = "Select an exam first.";
            return null;
        }
        if (selected.getStatus() != ExamStatus.PENDING_APPROVAL) {
            lastError = "Only pending exams can be approved.";
            return null;
        }
        awaitingAction = true;
        lastError = null;
        statusText = "Approving…";
        return new Message(Command.APPROVE_EXAM, selected.getId());
    }

    public Message requestReject(String reason) {
        String invalid = ExamFormValidator.validateRejectionReason(reason);
        if (invalid != null) {
            lastError = invalid;
            return null;
        }
        if (selected == null) {
            lastError = "Select an exam first.";
            return null;
        }
        awaitingAction = true;
        lastError = null;
        statusText = "Rejecting…";
        return new Message(Command.REJECT_EXAM,
                new ExamRejectionRequest(selected.getId(), 0, reason.trim()));
    }

    public void onServerMessage(Message msg) {
        if (msg == null) return;
        switch (msg.getCommand()) {
            case SUCCESS -> {
                Object payload = msg.getPayload();
                if (awaitingAction && payload instanceof Exam updated) {
                    awaitingAction = false;
                    pending.removeIf(e -> e.getId() == updated.getId()
                            || (e.getBaseId() > 0 && e.getBaseId() == updated.getBaseId()));
                    selected = null;
                    statusText = updated.getStatus() == ExamStatus.APPROVED
                            ? "Exam approved."
                            : "Exam rejected.";
                } else if (payload instanceof List<?> list) {
                    awaitingList = false;
                    pending.clear();
                    for (Object o : list) {
                        if (o instanceof Exam e) pending.add(e);
                    }
                    statusText = pending.size() + " pending.";
                }
            }
            case ERROR -> {
                awaitingList = false;
                awaitingAction = false;
                lastError = String.valueOf(msg.getPayload());
                statusText = "Server error.";
            }
            default -> { }
        }
    }
}
