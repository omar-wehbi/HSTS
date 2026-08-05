package client.ui.exam;

import common.network.CheckedExamResult;
import common.network.Message;
import common.network.Message.Command;
import common.network.StudentResultSummary;

import java.util.ArrayList;
import java.util.List;

/** Testable state for student grades (scenario 9). */
public class StudentGradesSession {

    private final List<StudentResultSummary> results = new ArrayList<>();
    private StudentResultSummary selected;
    private CheckedExamResult checkedExam;
    private String statusText = "";
    private String lastError;
    private boolean awaitingResults;
    private boolean awaitingChecked;

    public List<StudentResultSummary> getResults() {
        return List.copyOf(results);
    }

    public StudentResultSummary getSelected() {
        return selected;
    }

    public void setSelected(StudentResultSummary selected) {
        this.selected = selected;
    }

    public CheckedExamResult getCheckedExam() {
        return checkedExam;
    }

    public String getStatusText() {
        return statusText;
    }

    public String getLastError() {
        return lastError;
    }

    public Message requestResults() {
        awaitingResults = true;
        statusText = "Loading your grades…";
        lastError = null;
        return new Message(Command.GET_STUDENT_RESULTS);
    }

    public Message requestCheckedExam(int gradeId) {
        if (gradeId <= 0) {
            lastError = "Select a grade first.";
            return null;
        }
        awaitingChecked = true;
        statusText = "Loading checked exam…";
        lastError = null;
        return new Message(Command.GET_CHECKED_EXAM, gradeId);
    }

    /** Plain-text copy of the loaded checked exam (semester PDF §7.1). */
    public String exportCheckedExamText() {
        if (checkedExam == null) {
            lastError = "Load a checked exam first.";
            return null;
        }
        lastError = null;
        return common.util.CheckedExamExporter.toText(checkedExam);
    }

    /** PDF bytes of the loaded checked exam. */
    public byte[] exportCheckedExamPdf() {
        if (checkedExam == null) {
            lastError = "Load a checked exam first.";
            return null;
        }
        try {
            lastError = null;
            return common.util.CheckedExamExporter.toPdf(checkedExam);
        } catch (Exception e) {
            lastError = "Could not build PDF: " + e.getMessage();
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public void onServerMessage(Message msg) {
        if (msg == null) return;
        switch (msg.getCommand()) {
            case SUCCESS -> {
                Object payload = msg.getPayload();
                if (awaitingChecked && payload instanceof CheckedExamResult checked) {
                    awaitingChecked = false;
                    checkedExam = checked;
                    statusText = "Checked exam loaded.";
                } else if (awaitingResults && payload instanceof List<?> list) {
                    awaitingResults = false;
                    results.clear();
                    for (Object o : list) {
                        if (o instanceof StudentResultSummary s) results.add(s);
                    }
                    statusText = results.size() + " result(s).";
                }
            }
            case ERROR -> {
                awaitingResults = false;
                awaitingChecked = false;
                lastError = String.valueOf(msg.getPayload());
                statusText = "Server error.";
            }
            default -> statusText = "Unexpected: " + msg.getCommand();
        }
    }
}
