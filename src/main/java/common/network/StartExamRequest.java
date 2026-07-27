package common.network;
import java.io.Serializable;

/** Student credentials required by scenario 6 after login. */
public class StartExamRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    private String executionCode;
    private String idNumber;
    public StartExamRequest() { }
    public StartExamRequest(String executionCode, String idNumber) { this.executionCode = executionCode; this.idNumber = idNumber; }
    public String getExecutionCode() { return executionCode; } public void setExecutionCode(String executionCode) { this.executionCode = executionCode; }
    public String getIdNumber() { return idNumber; } public void setIdNumber(String idNumber) { this.idNumber = idNumber; }
}
