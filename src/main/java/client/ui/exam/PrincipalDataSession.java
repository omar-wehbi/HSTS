package client.ui.exam;

import common.network.Message;
import common.network.Message.Command;
import common.network.PrincipalData;
import common.network.PrincipalReadOnlyData;

/** Testable state for principal data view (scenario 11). */
public class PrincipalDataSession {

    private PrincipalData principalData;
    private PrincipalReadOnlyData readOnlyData;
    private String statusText = "";
    private String lastError;
    private boolean awaitingData;
    private boolean awaitingReadOnly;

    public PrincipalData getPrincipalData() {
        return principalData;
    }

    public PrincipalReadOnlyData getReadOnlyData() {
        return readOnlyData;
    }

    public String getStatusText() {
        return statusText;
    }

    public String getLastError() {
        return lastError;
    }

    public Message requestPrincipalData() {
        awaitingData = true;
        statusText = "Loading principal catalog…";
        lastError = null;
        return new Message(Command.GET_PRINCIPAL_DATA);
    }

    public Message requestReadOnlyData() {
        awaitingReadOnly = true;
        statusText = "Loading read-only snapshot…";
        lastError = null;
        return new Message(Command.GET_PRINCIPAL_READ_ONLY);
    }

    public void onServerMessage(Message msg) {
        if (msg == null) return;
        switch (msg.getCommand()) {
            case SUCCESS -> {
                Object payload = msg.getPayload();
                if (awaitingData && payload instanceof PrincipalData data) {
                    awaitingData = false;
                    principalData = data;
                } else if (awaitingReadOnly && payload instanceof PrincipalReadOnlyData data) {
                    awaitingReadOnly = false;
                    readOnlyData = data;
                }
                if (principalData != null && readOnlyData != null) {
                    statusText = "All principal data loaded.";
                } else if (principalData != null) {
                    statusText = "Catalog loaded.";
                } else if (readOnlyData != null) {
                    statusText = "Read-only snapshot loaded.";
                }
            }
            case ERROR -> {
                awaitingData = false;
                awaitingReadOnly = false;
                lastError = String.valueOf(msg.getPayload());
                statusText = "Server error.";
            }
            default -> statusText = "Unexpected: " + msg.getCommand();
        }
    }
}
