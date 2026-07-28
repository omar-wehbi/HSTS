package common.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Request for a principal report, optionally restricted to selected entity IDs. */
public class PrincipalReportRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private ReportDimension dimension;
    private List<Integer> entityIds;

    public PrincipalReportRequest() {
        this.entityIds = new ArrayList<>();
    }

    public PrincipalReportRequest(ReportDimension dimension, List<Integer> entityIds) {
        this.dimension = dimension;
        this.entityIds = entityIds == null ? new ArrayList<>() : new ArrayList<>(entityIds);
    }

    public ReportDimension getDimension() { return dimension; }
    public void setDimension(ReportDimension dimension) { this.dimension = dimension; }
    public List<Integer> getEntityIds() { return new ArrayList<>(entityIds == null ? List.of() : entityIds); }
    public void setEntityIds(List<Integer> entityIds) {
        this.entityIds = entityIds == null ? new ArrayList<>() : new ArrayList<>(entityIds);
    }
}
