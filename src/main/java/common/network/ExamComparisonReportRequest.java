package common.network; import java.io.Serializable;

/** Filters by teacher/course/student and compares individual exam sittings. */
public class ExamComparisonReportRequest implements Serializable { private static final long serialVersionUID=1L; private ReportDimension filterType; private int filterId; private boolean groupByRelease=true;
 public ExamComparisonReportRequest(){} public ExamComparisonReportRequest(ReportDimension t, int id){filterType=t;filterId=id;} public ReportDimension getFilterType(){return filterType;} public void setFilterType(ReportDimension v){filterType=v;} public int getFilterId(){return filterId;} public void setFilterId(int v){filterId=v;} public boolean isGroupByRelease(){return groupByRelease;} public void setGroupByRelease(boolean v){groupByRelease=v;} }