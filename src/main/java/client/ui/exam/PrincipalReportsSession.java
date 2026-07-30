package client.ui.exam;

import common.network.Message;
import common.network.Message.Command;
import common.network.PrincipalReport;
import common.network.PrincipalReportRequest;
import common.network.ReportDimension;
import common.network.ReportGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Testable state for principal reports (scenario 12). */
public class PrincipalReportsSession {

    private ReportDimension dimension = ReportDimension.TEACHER;
    private PrincipalReport report;
    private String statusText = "";
    private String lastError;
    private boolean awaitingReport;

    public ReportDimension getDimension() {
        return dimension;
    }

    public void setDimension(ReportDimension dimension) {
        if (dimension != null) this.dimension = dimension;
    }

    public PrincipalReport getReport() {
        return report;
    }

    public String getStatusText() {
        return statusText;
    }

    public String getLastError() {
        return lastError;
    }

    public Message requestReport(ReportDimension dim, String entityIdsCsv) {
        if (dim == null) {
            lastError = "Select a report dimension.";
            return null;
        }
        dimension = dim;
        List<Integer> ids = parseEntityIds(entityIdsCsv);
        awaitingReport = true;
        statusText = "Generating report…";
        lastError = null;
        return new Message(Command.GET_REPORT,
                new PrincipalReportRequest(dim, ids));
    }

    static List<Integer> parseEntityIds(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        List<Integer> ids = new ArrayList<>();
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            try {
                ids.add(Integer.parseInt(trimmed));
            } catch (NumberFormatException ignored) {
                // skip invalid tokens
            }
        }
        return ids;
    }

    public static String formatGroup(ReportGroup g) {
        if (g == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(g.getLabel()).append(" (id ").append(g.getEntityId()).append(")\n");
        sb.append("  count: ").append(g.getCount()).append("\n");
        sb.append(String.format("  mean: %.1f  median: %.1f%n", g.getMean(), g.getMedian()));
        sb.append("  min: ").append(g.getMinimum()).append("  max: ").append(g.getMaximum()).append("\n");
        List<Double> deciles = g.getDeciles();
        if (!deciles.isEmpty()) {
            sb.append("  deciles: ");
            sb.append(deciles.stream()
                    .map(d -> String.format("%.1f", d))
                    .collect(Collectors.joining(", ")));
        }
        return sb.toString();
    }

    public void onServerMessage(Message msg) {
        if (msg == null) return;
        switch (msg.getCommand()) {
            case SUCCESS -> {
                if (awaitingReport && msg.getPayload() instanceof PrincipalReport r) {
                    awaitingReport = false;
                    report = r;
                    statusText = r.getGroups().size() + " group(s) in report.";
                }
            }
            case ERROR -> {
                awaitingReport = false;
                lastError = String.valueOf(msg.getPayload());
                statusText = "Server error.";
            }
            default -> statusText = "Unexpected: " + msg.getCommand();
        }
    }
}
