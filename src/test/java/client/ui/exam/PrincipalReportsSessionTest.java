package client.ui.exam;

import common.network.Message;
import common.network.Message.Command;
import common.network.PrincipalReport;
import common.network.PrincipalReportRequest;
import common.network.ReportDimension;
import common.network.ReportGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PrincipalReportsSessionTest {

    private PrincipalReportsSession session;

    @BeforeEach
    void setUp() {
        session = new PrincipalReportsSession();
    }

    @Test
    void parseEntityIdsSkipsBlanksAndInvalid() {
        assertThat(PrincipalReportsSession.parseEntityIds("1, 2 ,,x,3"))
                .containsExactly(1, 2, 3);
        assertThat(PrincipalReportsSession.parseEntityIds("")).isEmpty();
    }

    @Test
    void requestReportBuildsPayload() {
        Message m = session.requestReport(ReportDimension.COURSE, "10,11");
        assertThat(m.getCommand()).isEqualTo(Command.GET_REPORT);
        PrincipalReportRequest req = (PrincipalReportRequest) m.getPayload();
        assertThat(req.getDimension()).isEqualTo(ReportDimension.COURSE);
        assertThat(req.getEntityIds()).containsExactly(10, 11);
    }

    @Test
    void reportStoredOnSuccess() {
        session.requestReport(ReportDimension.TEACHER, "");
        ReportGroup group = new ReportGroup(1, "Alice", 5, 70, 72, 50, 90,
                List.of(55.0, 60.0));
        PrincipalReport report = new PrincipalReport(ReportDimension.TEACHER, List.of(group));
        session.onServerMessage(new Message(Command.SUCCESS, report));

        assertThat(session.getReport().getGroups()).hasSize(1);
        assertThat(PrincipalReportsSession.formatGroup(group)).contains("Alice");
        assertThat(session.fullReportText()).contains("Dimension: TEACHER").contains("Alice");
    }

    @Test
    void errorMessageSetsLastError() {
        session.requestReport(ReportDimension.COURSE, "1");
        session.onServerMessage(new Message(Command.ERROR, "Invalid report dimension."));
        assertThat(session.getLastError()).isEqualTo("Invalid report dimension.");
        assertThat(session.getStatusText()).isEqualTo("Server error.");
        assertThat(session.getReport()).isNull();
    }

    @Test
    void emptyReportStored() {
        session.requestReport(ReportDimension.STUDENT, null);
        PrincipalReport empty = new PrincipalReport(ReportDimension.STUDENT, List.of());
        session.onServerMessage(new Message(Command.SUCCESS, empty));
        assertThat(session.getReport().getGroups()).isEmpty();
        assertThat(session.getStatusText()).contains("0 group");
    }

    @Test
    void nullDimensionRejected() {
        assertThat(session.requestReport(null, "1")).isNull();
        assertThat(session.getLastError()).contains("dimension");
    }

    @Test
    void formatGroupNullReturnsEmpty() {
        assertThat(PrincipalReportsSession.formatGroup(null)).isEmpty();
    }

    @Test
    void parseEntityIdsNullReturnsEmpty() {
        assertThat(PrincipalReportsSession.parseEntityIds(null)).isEmpty();
    }
}
