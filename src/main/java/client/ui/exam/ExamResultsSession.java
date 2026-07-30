package client.ui.exam;

import common.entities.ExamRelease;
import common.network.HistogramBin;
import common.network.Message;
import common.network.Message.Command;
import common.network.TeacherExamResults;

import java.util.ArrayList;
import java.util.List;

/** Testable state for teacher exam results (scenarios 9–10). */
public class ExamResultsSession {

    private final List<ExamRelease> releases = new ArrayList<>();
    private ExamRelease selectedRelease;
    private TeacherExamResults results;
    private String statusText = "";
    private String lastError;
    private boolean awaitingReleases;
    private boolean awaitingStatistics;

    public List<ExamRelease> getReleases() {
        return List.copyOf(releases);
    }

    public ExamRelease getSelectedRelease() {
        return selectedRelease;
    }

    public void setSelectedRelease(ExamRelease selectedRelease) {
        this.selectedRelease = selectedRelease;
    }

    public TeacherExamResults getResults() {
        return results;
    }

    public String getStatusText() {
        return statusText;
    }

    public String getLastError() {
        return lastError;
    }

    public Message requestReleasedExams() {
        awaitingReleases = true;
        statusText = "Loading releases…";
        lastError = null;
        return new Message(Command.GET_RELEASED_EXAMS);
    }

    public Message requestStatistics(int releaseId) {
        if (releaseId <= 0) {
            lastError = "Select a release first.";
            return null;
        }
        awaitingStatistics = true;
        statusText = "Loading statistics…";
        lastError = null;
        return new Message(Command.GET_EXAM_STATISTICS, releaseId);
    }

    public static String formatHistogram(List<HistogramBin> bins) {
        if (bins == null || bins.isEmpty()) return "(no data)";
        int max = bins.stream().mapToInt(HistogramBin::getCount).max().orElse(1);
        if (max == 0) max = 1;
        StringBuilder sb = new StringBuilder();
        for (HistogramBin bin : bins) {
            sb.append(String.format("%5s ", bin.getLabel()));
            int barLen = (int) Math.round(20.0 * bin.getCount() / max);
            sb.append("#".repeat(Math.max(0, barLen)));
            sb.append(" (").append(bin.getCount()).append(")\n");
        }
        return sb.toString().trim();
    }

    @SuppressWarnings("unchecked")
    public void onServerMessage(Message msg) {
        if (msg == null) return;
        switch (msg.getCommand()) {
            case SUCCESS -> {
                Object payload = msg.getPayload();
                if (awaitingStatistics && payload instanceof TeacherExamResults r) {
                    awaitingStatistics = false;
                    results = r;
                    statusText = r.getResultCount() + " graded result(s).";
                } else if (awaitingReleases && payload instanceof List<?> list) {
                    awaitingReleases = false;
                    releases.clear();
                    for (Object o : list) {
                        if (o instanceof ExamRelease release) releases.add(release);
                    }
                    statusText = releases.size() + " release(s).";
                }
            }
            case ERROR -> {
                awaitingReleases = false;
                awaitingStatistics = false;
                lastError = String.valueOf(msg.getPayload());
                statusText = "Server error.";
            }
            default -> statusText = "Unexpected: " + msg.getCommand();
        }
    }
}
