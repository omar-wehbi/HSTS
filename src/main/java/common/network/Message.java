package common.network;

import java.io.Serializable;

/**
 * Wire protocol envelope exchanged between client and server over OCSF.
 *
 * <p>Every exchange is a {@code Message}: a {@link Command} verb plus an optional
 * {@code payload}. Whatever is placed in the payload MUST be {@link Serializable}.
 */
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Protocol verbs understood by both tiers. */
    public enum Command {
        // ----- Question bank: client -> server -----
        GET_COURSES,            // payload: null            -> SUCCESS: List<Course>
        GET_QUESTIONS,          // payload: null            -> SUCCESS: List<Question> (current bank)
        GET_QUESTIONS_BY_COURSE,// payload: Integer courseId-> SUCCESS: List<Question>
        GET_QUESTION_HISTORY,   // payload: Integer baseId  -> SUCCESS: List<Question>
        ADD_QUESTION,           // payload: Question        -> SUCCESS: List<Question> (refreshed bank)
        UPDATE_QUESTION,        // payload: Question        -> SUCCESS: List<Question>
        DELETE_QUESTION,        // payload: Integer baseId  -> SUCCESS: List<Question>

        // ----- server -> client -----
        SUCCESS,
        ERROR
    }

    private Command command;
    private Object  payload;

    public Message() { }

    public Message(Command command) { this.command = command; }

    public Message(Command command, Object payload) {
        this.command = command;
        this.payload = payload;
    }

    public Command getCommand() { return command; }
    public void setCommand(Command command) { this.command = command; }

    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }

    @Override
    public String toString() {
        return "Message{command=" + command + ", payload=" + payload + '}';
    }
}
