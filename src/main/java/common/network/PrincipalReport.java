package common.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Comparison report returned to a principal. */
public class PrincipalReport implements Serializable {
    private static final long serialVersionUID = 1L;

    private ReportDimension dimension;
    private List<ReportGroup> groups;

    public PrincipalReport() { groups = new ArrayList<>(); }
    public PrincipalReport(ReportDimension dimension, List<ReportGroup> groups) {
        this.dimension = dimension;
        this.groups = groups == null ? new ArrayList<>() : new ArrayList<>(groups);
    }
    public ReportDimension getDimension() { return dimension; }
    public void setDimension(ReportDimension dimension) { this.dimension = dimension; }
    public List<ReportGroup> getGroups() { return new ArrayList<>(groups == null ? List.of() : groups); }
    public void setGroups(List<ReportGroup> groups) { this.groups = groups == null ? new ArrayList<>() : new ArrayList<>(groups); }
}
