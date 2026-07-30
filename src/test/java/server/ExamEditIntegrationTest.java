package server;

import common.entities.Exam;
import common.network.Credentials;
import common.network.Message;
import common.network.Message.Command;
import common.network.QuestionFilter;
import ocsf.client.AbstractClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Simulates opening the exam builder to edit an existing draft.
 */
class ExamEditIntegrationTest {

    private static final int PORT = 5555;

    private static class TestClient extends AbstractClient {
        final BlockingQueue<Message> responses = new LinkedBlockingQueue<>();

        TestClient() {
            super("localhost", PORT);
        }

        @Override
        protected void handleMessageFromServer(Object msg) {
            if (msg instanceof Message m) {
                responses.add(m);
            }
        }

        Message call(Message request) throws Exception {
            sendToServer(request);
            Message r = responses.poll(5, TimeUnit.SECONDS);
            assertNotNull(r, "no response for " + request.getCommand());
            return r;
        }
    }

    @Test
    void openBuilderForDraftLoadsCoursesAndBank() throws Exception {
        TestClient client = new TestClient();
        client.openConnection();
        try {
            assertEquals(Command.SUCCESS,
                    client.call(new Message(Command.LOGIN, new Credentials("teacher", "1234"))).getCommand());

            Message examsMsg = client.call(new Message(Command.GET_MY_EXAMS));
            assertEquals(Command.SUCCESS, examsMsg.getCommand());
            assertTrue(examsMsg.getPayload() instanceof List<?>);
            @SuppressWarnings("unchecked")
            List<Exam> exams = (List<Exam>) examsMsg.getPayload();
            assertFalse(exams.isEmpty(), "teacher should have draft exams in seed data");

            Exam draft = exams.stream()
                    .filter(e -> e.getStatus().name().equals("DRAFT"))
                    .findFirst()
                    .orElse(exams.get(0));

            assertEquals(Command.SUCCESS,
                    client.call(new Message(Command.GET_COURSES)).getCommand());

            Message bankMsg = client.call(new Message(Command.GET_QUESTIONS_FILTERED,
                    new QuestionFilter(draft.getCourseId(), null, null)));
            assertEquals(Command.SUCCESS, bankMsg.getCommand());
            assertTrue(bankMsg.getPayload() instanceof List<?>);
        } finally {
            client.closeConnection();
        }
    }
}
