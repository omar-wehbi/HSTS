package common.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Statistics for one teacher, course, or student in a principal report. */
public class ReportGroup implements Serializable {
    private static final long serialVersionUID = 1L;

    private int entityId;
    private String label;
    private int count;
    private double mean;
    private double median;
    private Integer minimum;
    private Integer maximum;
    private List<Double> deciles;

    public ReportGroup() { deciles = new ArrayList<>(); }
    public ReportGroup(int entityId, String label, int count, double mean, double median,
                       Integer minimum, Integer maximum, List<Double> deciles) {
        this.entityId = entityId; this.label = label; this.count = count; this.mean = mean;
        this.median = median; this.minimum = minimum; this.maximum = maximum;
        this.deciles = deciles == null ? new ArrayList<>() : new ArrayList<>(deciles);
    }
    public int getEntityId() { return entityId; }
    public void setEntityId(int entityId) { this.entityId = entityId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
    public double getMean() { return mean; }
    public void setMean(double mean) { this.mean = mean; }
    public double getMedian() { return median; }
    public void setMedian(double median) { this.median = median; }
    public Integer getMinimum() { return minimum; }
    public void setMinimum(Integer minimum) { this.minimum = minimum; }
    public Integer getMaximum() { return maximum; }
    public void setMaximum(Integer maximum) { this.maximum = maximum; }
    public List<Double> getDeciles() { return new ArrayList<>(deciles == null ? List.of() : deciles); }
    public void setDeciles(List<Double> deciles) { this.deciles = deciles == null ? new ArrayList<>() : new ArrayList<>(deciles); }
}
