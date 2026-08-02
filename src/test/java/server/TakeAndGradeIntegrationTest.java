package server;

import common.entities.Exam;
import common.entities.ExamRelease;
import common.entities.ExamSession;
import common.entities.Grade;
import common.entities.GradeStatus;
import common.entities.StudentAnswer;
import common.network.Credentials;
import common.network.ExamForm;
import common.network.Message;
import common.network.Message.Command;
import common.network.StartExamRequest;
import common.network.SubmitAnswersRequest;
import ocsf.client.AbstractClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.db.ExamSessionDAO;
import server.db.ExecutionTestFixture;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** End-to-end: student takes exam → teacher grades → student sees approved result. */
class TakeAndGradeIntegrationTest {

    private static final int PORT = 5607;
    private static HSTSServer server;

    @BeforeAll
    static void startServer() throws Exception {
        server = new HSTSServer(PORT);
        server.listen();
    }

    @AfterAll
    static void stopServer() throws Exception {
        server.close();
    }

    @BeforeEach
    void seed() {
        ExecutionTestFixture.wipeExecutionData();
    }

    private static class TestClient extends AbstractClient {
        final BlockingQueue<Message> responses = new LinkedBlockingQueue<>();

        TestClient() { super("localhost", PORT); }

        @Override
        protected void handleMessageFromServer(Object msg) {
            if (msg instanceof Message m) responses.add(m);
        }

        Message call(Message request) throws Exception {
            sendToServer(request);
            Message r = responses.poll(8, TimeUnit.SECONDS);
            assertThat(r).as("no response for " + request.getCommand()).isNotNull();
            return r;
        }
    }

    @Test
    void studentTakesExamTeacherGradesStudentSeesResult() throws Exception {
        Exam exam = ExecutionTestFixture.ensureApprovedExam();
        ExamRelease release = ExecutionTestFixture.createOpenRelease(exam, "7701");

        TestClient student = new TestClient();
        student.openConnection();
        try {
            assertThat(student.call(new Message(Command.LOGIN, new Credentials("maya", "1234")))
                    .getCommand()).isEqualTo(Command.SUCCESS);

            Message started = student.call(new Message(Command.START_EXAM_SESSION,
                    new StartExamRequest("7701", "207570227")));
            assertThat(started.getCommand()).isEqualTo(Command.SUCCESS);
            ExamForm form = (ExamForm) started.getPayload();
            int sessionId = form.getSession().getId();
            int qid = form.getQuestions().get(0).getQuestionId();

            // Correct answer is 2 from QuestionBankTestFixture.sample
            Message submitted = student.call(new Message(Command.SUBMIT_ANSWERS,
                    new SubmitAnswersRequest(sessionId, List.of(new StudentAnswer(qid, 2)))));
            assertThat(submitted.getCommand()).isEqualTo(Command.SUCCESS);
        } finally {
            student.closeConnection();
        }

        TestClient teacher = new TestClient();
        teacher.openConnection();
        int gradeId;
        try {
            assertThat(teacher.call(new Message(Command.LOGIN, new Credentials("teacher", "1234")))
                    .getCommand()).isEqualTo(Command.SUCCESS);

            // Find session via release list then grade
            ExamSession session = new ExamSessionDAO()
                    .getByReleaseAndStudent(release.getId(), ExecutionTestFixture.userId("maya"));
            assertThat(session).isNotNull();

            Message graded = teacher.call(new Message(Command.GRADE_EXAM_AUTO, session.getId()));
            assertThat(graded.getCommand()).isEqualTo(Command.SUCCESS);
            Grade grade = (Grade) graded.getPayload();
            gradeId = grade.getId();

            Message approved = teacher.call(new Message(Command.APPROVE_GRADE, gradeId));
            assertThat(approved.getCommand()).isEqualTo(Command.SUCCESS);
            assertThat(((Grade) approved.getPayload()).getStatus()).isEqualTo(GradeStatus.APPROVED);
        } finally {
            teacher.closeConnection();
        }

        TestClient student2 = new TestClient();
        student2.openConnection();
        try {
            assertThat(student2.call(new Message(Command.LOGIN, new Credentials("maya", "1234")))
                    .getCommand()).isEqualTo(Command.SUCCESS);
            Message results = student2.call(new Message(Command.GET_STUDENT_RESULTS, null));
            assertThat(results.getCommand()).isEqualTo(Command.SUCCESS);
            assertThat((List<?>) results.getPayload()).isNotEmpty();
        } finally {
            student2.closeConnection();
        }
    }
}
