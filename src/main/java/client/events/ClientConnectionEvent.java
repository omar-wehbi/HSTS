package client.events;

/**
 * Published when the OCSF client socket closes or hits a read/write failure.
 */
public final class ClientConnectionEvent {

    private final String reason;

    public ClientConnectionEvent(String reason) {
        this.reason = reason == null || reason.isBlank()
                ? "Connection to the server was lost. The server may have crashed or needs a restart."
                : reason;
    }

    public String getReason() {
        return reason;
    }
}
