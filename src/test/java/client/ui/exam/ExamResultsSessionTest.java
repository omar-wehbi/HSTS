package client.ui.exam;

import common.entities.ExamRelease;
import common.network.HistogramBin;
import common.network.Message;
import common.network.Message.Command;
import common.network.TeacherExamResults;
import common.network.TeacherResultRow;
import common.entities.ExamSessionStatus;
import common.entities.GradeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExamResultsSessionTest {

    private ExamResultsSession session;

    @BeforeEach
    void setUp() {
        session = new ExamResultsSession();
    }

    @Test
    void statisticsUsesReleaseId() {
        assertThat(session.requestStatistics(0)).isNull();
        Message m = session.requestStatistics(7);
        assertThat(m.getCommand()).isEqualTo(Command.GET_EXAM_STATISTICS);
        assertThat(m.getPayload()).isEqualTo(7);
    }

    @Test
    void successStoresTeacherResults() {
        session.requestStatistics(7);
        TeacherExamResults results = new TeacherExamResults(
                7, 1, "Final",
                List.of(new TeacherResultRow(1, 2, 3, 80, null,
                        GradeStatus.APPROVED, ExamSessionStatus.SUBMITTED, LocalDateTime.now())),
                List.of(new HistogramBin(0, 9, 0), new HistogramBin(80, 89, 2)),
                1, 80.0, 80.0, 80, 80);
        session.onServerMessage(new Message(Command.SUCCESS, results));

        assertThat(session.getResults()).isSameAs(results);
        assertThat(session.getResults().getResultCount()).isEqualTo(1);
    }

    @Test
    void formatHistogramBuildsTextBars() {
        String text = ExamResultsSession.formatHistogram(
                List.of(new HistogramBin(0, 9, 1), new HistogramBin(90, 100, 3)));
        assertThat(text).contains("0-9").contains("90-100").contains("#");
    }

    @Test
    void releasesLoaded() {
        session.requestReleasedExams();
        ExamRelease r = new ExamRelease(1, 2, "1111",
                LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        r.setId(4);
        session.onServerMessage(new Message(Command.SUCCESS, List.of(r)));
        assertThat(session.getReleases()).hasSize(1);
    }

    @Test
    void errorMessageSetsLastError() {
        session.requestStatistics(7);
        session.onServerMessage(new Message(Command.ERROR, "Not the exam author."));
        assertThat(session.getLastError()).isEqualTo("Not the exam author.");
        assertThat(session.getStatusText()).isEqualTo("Server error.");
        assertThat(session.getResults()).isNull();
    }

    @Test
    void emptyReleasesList() {
        session.requestReleasedExams();
        session.onServerMessage(new Message(Command.SUCCESS, List.of()));
        assertThat(session.getReleases()).isEmpty();
        assertThat(session.getStatusText()).contains("0 release");
    }

    @Test
    void formatHistogramNullOrEmpty() {
        assertThat(ExamResultsSession.formatHistogram(null)).isEqualTo("(no data)");
        assertThat(ExamResultsSession.formatHistogram(List.of())).isEqualTo("(no data)");
    }

    @Test
    void nullServerMessageIsIgnored() {
        session.onServerMessage(null);
        assertThat(session.getLastError()).isNull();
    }
}
