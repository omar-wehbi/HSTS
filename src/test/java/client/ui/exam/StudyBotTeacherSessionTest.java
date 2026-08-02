package client.ui.exam;

import common.network.CreateStudyBotRequest;
import common.network.Message;
import common.network.Message.Command;
import common.network.SetStudyBotAvailabilityRequest;
import common.network.StudyBotSourceRequest;
import common.network.StudyBotSourceView;
import common.network.StudyBotUsageReport;
import common.network.StudyBotView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StudyBotTeacherSessionTest {

    private StudyBotTeacherSession session;

    @BeforeEach
    void setUp() {
        session = new StudyBotTeacherSession();
    }

    @Test
    void createValidatesInputs() {
        assertThat(session.requestCreate(0, "Bot", false)).isNull();
        assertThat(session.requestCreate(1, " ", false)).isNull();

        Message m = session.requestCreate(1, "Algo Bot", true);
        assertThat(m.getCommand()).isEqualTo(Command.CREATE_STUDY_BOT);
        CreateStudyBotRequest req = (CreateStudyBotRequest) m.getPayload();
        assertThat(req.isIncludeQuestionBank()).isTrue();
    }

    @Test
    void availabilityRequestBuilt() {
        Message m = session.requestSetAvailability(2, true);
        assertThat(((SetStudyBotAvailabilityRequest) m.getPayload()).isAvailable()).isTrue();
    }

    @Test
    void addSourceRequiresContent() {
        assertThat(session.requestAddSource(1, "Notes", "")).isNull();
        Message m = session.requestAddSource(1, "Notes", "Chapter 1");
        assertThat(m.getCommand()).isEqualTo(Command.ADD_STUDY_BOT_SOURCE);
        assertThat(((StudyBotSourceRequest) m.getPayload()).getTitle()).isEqualTo("Notes");
    }

    @Test
    void sourcesAndUsageStored() {
        session.requestSources(1);
        StudyBotSourceView source = new StudyBotSourceView(
                5, 1, "Notes", "Text", 2, LocalDateTime.now());
        session.onServerMessage(new Message(Command.SUCCESS, List.of(source)));
        assertThat(session.getSources()).hasSize(1);

        session.requestUsage(1);
        StudyBotUsageReport usage = new StudyBotUsageReport(1, 10, 4, List.of());
        session.onServerMessage(new Message(Command.SUCCESS, usage));
        assertThat(session.getUsageReport().getTotalQuestions()).isEqualTo(10);
    }

    @Test
    void botViewStoredAfterCreate() {
        session.requestCreate(1, "Bot", false);
        StudyBotView view = new StudyBotView(1, "Bot", false, false, 2,
                LocalDateTime.now(), 0);
        session.onServerMessage(new Message(Command.SUCCESS, view));
        assertThat(session.getBotView().isAvailable()).isFalse();
    }

    @Test
    void errorMessageSetsLastError() {
        session.requestCreate(1, "Bot", false);
        session.onServerMessage(new Message(Command.ERROR, "Not enrolled as teacher."));
        assertThat(session.getLastError()).isEqualTo("Not enrolled as teacher.");
        assertThat(session.getStatusText()).isEqualTo("Server error.");
        assertThat(session.getBotView()).isNull();
    }

    @Test
    void emptySourcesListClearsPrior() {
        session.requestSources(1);
        StudyBotSourceView source = new StudyBotSourceView(
                5, 1, "Notes", "Text", 2, LocalDateTime.now());
        session.onServerMessage(new Message(Command.SUCCESS, List.of(source)));
        assertThat(session.getSources()).hasSize(1);

        session.requestSources(1);
        session.onServerMessage(new Message(Command.SUCCESS, List.of()));
        assertThat(session.getSources()).isEmpty();
        assertThat(session.getStatusText()).contains("0 source");
    }

    @Test
    void deleteSourceRemovesFromListOnSuccess() {
        assertThat(session.requestDeleteSource(0)).isNull();
        assertThat(session.getLastError()).contains("Select a source");

        session.requestSources(1);
        StudyBotSourceView source = new StudyBotSourceView(
                5, 1, "Notes", "Text", 2, LocalDateTime.now());
        session.onServerMessage(new Message(Command.SUCCESS, List.of(source)));

        Message del = session.requestDeleteSource(5);
        assertThat(del.getCommand()).isEqualTo(Command.DELETE_STUDY_BOT_SOURCE);
        assertThat(del.getPayload()).isEqualTo(5);

        session.onServerMessage(new Message(Command.SUCCESS, 5));
        assertThat(session.getSources()).isEmpty();
        assertThat(session.getStatusText()).contains("deleted");
    }

    @Test
    void availabilityRejectsNonPositiveCourseId() {
        assertThat(session.requestSetAvailability(0, true)).isNull();
        assertThat(session.getLastError()).contains("Course ID");
    }
}
