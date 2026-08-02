package client.ui.exam;

import common.network.Message;
import common.network.Message.Command;
import common.network.PrincipalData;
import common.network.PrincipalDataItem;
import common.network.PrincipalReadOnlyData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PrincipalDataSessionTest {

    private PrincipalDataSession session;

    @BeforeEach
    void setUp() {
        session = new PrincipalDataSession();
    }

    @Test
    void requestCommands() {
        assertThat(session.requestPrincipalData().getCommand()).isEqualTo(Command.GET_PRINCIPAL_DATA);
        assertThat(session.requestReadOnlyData().getCommand()).isEqualTo(Command.GET_PRINCIPAL_READ_ONLY);
    }

    @Test
    void principalDataStored() {
        session.requestPrincipalData();
        PrincipalData data = new PrincipalData(
                List.of(new PrincipalDataItem(1, "T1")),
                List.of(new PrincipalDataItem(2, "C1")),
                List.of(new PrincipalDataItem(3, "S1")),
                12);
        session.onServerMessage(new Message(Command.SUCCESS, data));

        assertThat(session.getPrincipalData().getGradedAttempts()).isEqualTo(12);
        assertThat(session.getPrincipalData().getTeachers()).hasSize(1);
    }

    @Test
    void readOnlyDataStored() {
        session.requestReadOnlyData();
        PrincipalReadOnlyData ro = new PrincipalReadOnlyData(
                List.of(), List.of(), List.of(), List.of(), List.of());
        session.onServerMessage(new Message(Command.SUCCESS, ro));
        assertThat(session.getReadOnlyData()).isSameAs(ro);
    }

    @Test
    void errorMessageSetsLastError() {
        session.requestPrincipalData();
        session.onServerMessage(new Message(Command.ERROR, "Principals only."));
        assertThat(session.getLastError()).isEqualTo("Principals only.");
        assertThat(session.getStatusText()).isEqualTo("Server error.");
        assertThat(session.getPrincipalData()).isNull();
    }

    @Test
    void nullServerMessageIsIgnored() {
        session.onServerMessage(null);
        assertThat(session.getLastError()).isNull();
        assertThat(session.getPrincipalData()).isNull();
        assertThat(session.getReadOnlyData()).isNull();
    }

    @Test
    void emptyCatalogStored() {
        session.requestPrincipalData();
        PrincipalData empty = new PrincipalData(List.of(), List.of(), List.of(), 0);
        session.onServerMessage(new Message(Command.SUCCESS, empty));
        assertThat(session.getPrincipalData().getTeachers()).isEmpty();
        assertThat(session.getPrincipalData().getGradedAttempts()).isZero();
        assertThat(session.getStatusText()).contains("Catalog");
    }
}
