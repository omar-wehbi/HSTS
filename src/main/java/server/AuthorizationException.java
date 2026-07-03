package server;

/**
 * Thrown by {@link Authorization#requireRole} when the caller is not logged in or
 * lacks the required role. The server turns it into an {@code ERROR} response.
 */
public class AuthorizationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AuthorizationException(String message) {
        super(message);
    }
}
