package common.network;

import java.io.Serializable;

/** Inclusive score interval and its number of grades. */
public class HistogramBin implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int minimum;
    private final int maximum;
    private final int count;

    public HistogramBin(int minimum, int maximum, int count) {
        this.minimum = minimum;
        this.maximum = maximum;
        this.count = count;
    }

    public int getMinimum() { return minimum; }
    public int getMaximum() { return maximum; }
    public int getCount() { return count; }
    public String getLabel() { return minimum + "-" + maximum; }
}
