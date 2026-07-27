package common.network; import java.io.Serializable;

public class ReleaseStatisticsRequest implements Serializable { private static final long serialVersionUID=1L; private int releaseId; public ReleaseStatisticsRequest(){} public ReleaseStatisticsRequest(int id){releaseId=id;} public int getReleaseId(){return releaseId;} public void setReleaseId(int v){releaseId=v;} }