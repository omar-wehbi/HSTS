package common.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Table rows and chart-ready statistics for one teacher-authored exam. */
public class TeacherExamResults implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int releaseId;
    private final int examId;
    private final String examTitle;
    private final List<TeacherResultRow> rows;
    private final List<HistogramBin> histogram;
    private final int resultCount;
    private final double mean;
    private final double median;
    private final Integer minimum;
    private final Integer maximum;

    public TeacherExamResults(int examId, String examTitle,
                              List<TeacherResultRow> rows,
                              List<HistogramBin> histogram,
                              int resultCount, double mean, double median,
                              Integer minimum, Integer maximum) {
        this(0, examId, examTitle, rows, histogram, resultCount, mean, median, minimum, maximum);
    }

    public TeacherExamResults(int releaseId, int examId, String examTitle,
                              List<TeacherResultRow> rows,
                              List<HistogramBin> histogram,
                              int resultCount, double mean, double median,
                              Integer minimum, Integer maximum) {
        this.releaseId = releaseId;
        this.examId = examId;
        this.examTitle = examTitle;
        this.rows = rows == null ? new ArrayList<>() : new ArrayList<>(rows);
        this.histogram = histogram == null ? new ArrayList<>() : new ArrayList<>(histogram);
        this.resultCount = resultCount;
        this.mean = mean;
        this.median = median;
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public int getReleaseId() { return releaseId; }
    public int getExamId() { return examId; }
    public String getExamTitle() { return examTitle; }
    public List<TeacherResultRow> getRows() { return new ArrayList<>(rows); }
    public List<HistogramBin> getHistogram() { return new ArrayList<>(histogram); }
    public int getResultCount() { return resultCount; }
    public double getMean() { return mean; }
    public double getMedian() { return median; }
    public Integer getMinimum() { return minimum; }
    public Integer getMaximum() { return maximum; }
}
