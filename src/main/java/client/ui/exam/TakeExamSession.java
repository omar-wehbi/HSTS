package client.ui.exam;

import common.entities.ExamSession;
import common.entities.ExamSessionStatus;
import common.entities.StudentAnswer;
import common.network.ExamForm;
import common.network.ExamFormQuestion;
import common.network.Message;
import common.network.Message.Command;
import common.network.SaveAnswersRequest;
import common.network.StartExamRequest;
import common.network.SubmitAnswersRequest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Testable state for taking an exam (scenario 6). */
public class TakeExamSession {

    private ExamForm examForm;
    private final Map<Integer, Integer> answers = new LinkedHashMap<>();
    private String statusText = "";
    private String lastError;
    private boolean submitted;
    private boolean awaitingStart;
    private boolean awaitingSave;
    private boolean awaitingSubmit;

    public ExamForm getExamForm() {
        return examForm;
    }

    public Map<Integer, Integer> getAnswers() {
        return Map.copyOf(answers);
    }

    public String getStatusText() {
        return statusText;
    }

    public String getLastError() {
        return lastError;
    }

    public boolean isSubmitted() {
        return submitted;
    }

    public boolean isAwaitingStart() {
        return awaitingStart;
    }

    public boolean isEditable() {
        return examForm != null && !submitted;
    }

    public Message requestStart(String executionCode, String idNumber) {
        if (executionCode == null || executionCode.isBlank()) {
            lastError = "Execution code is required.";
            return null;
        }
        if (idNumber == null || idNumber.isBlank()) {
            lastError = "ID number is required.";
            return null;
        }
        awaitingStart = true;
        statusText = "Starting exam…";
        lastError = null;
        return new Message(Command.START_EXAM_SESSION,
                new StartExamRequest(executionCode.trim(), idNumber.trim()));
    }

    public void recordAnswer(int questionId, int selectedOption) {
        if (!isEditable()) return;
        if (selectedOption < 1 || selectedOption > 4) return;
        answers.put(questionId, selectedOption);
    }

    public Message requestSave() {
        ExamSession session = currentSession();
        if (session == null) {
            lastError = "No active exam session.";
            return null;
        }
        if (!isEditable()) {
            lastError = "Exam is already submitted.";
            return null;
        }
        awaitingSave = true;
        statusText = "Saving answers…";
        lastError = null;
        return new Message(Command.SAVE_ANSWERS,
                new SaveAnswersRequest(session.getId(), buildAnswerList()));
    }

    public Message requestSubmit() {
        ExamSession session = currentSession();
        if (session == null) {
            lastError = "No active exam session.";
            return null;
        }
        if (!isEditable()) {
            lastError = "Exam is already submitted.";
            return null;
        }
        awaitingSubmit = true;
        statusText = "Submitting exam…";
        lastError = null;
        return new Message(Command.SUBMIT_ANSWERS,
                new SubmitAnswersRequest(session.getId(), buildAnswerList()));
    }

    public long remainingSeconds(LocalDateTime now) {
        ExamSession session = currentSession();
        if (session == null || session.getDeadline() == null || submitted) {
            return 0;
        }
        if (now == null) now = LocalDateTime.now();
        long secs = Duration.between(now, session.getDeadline()).getSeconds();
        return Math.max(0, secs);
    }

    public List<ExamFormQuestion> getQuestions() {
        return examForm == null ? List.of() : examForm.getQuestions();
    }

    public void onServerMessage(Message msg) {
        if (msg == null) return;
        switch (msg.getCommand()) {
            case SUCCESS -> {
                Object payload = msg.getPayload();
                if (payload instanceof ExamForm form) {
                    awaitingStart = false;
                    examForm = form;
                    answers.clear();
                    submitted = false;
                    if (form.getSession() != null && form.getSession().getAnswers() != null) {
                        for (StudentAnswer a : form.getSession().getAnswers()) {
                            answers.put(a.getQuestionId(), a.getSelectedAnswer());
                        }
                    }
                    statusText = "Exam started.";
                } else if (payload instanceof ExamSession session) {
                    boolean wasSubmit = awaitingSubmit;
                    awaitingSave = false;
                    awaitingSubmit = false;
                    mergeSession(session);
                    if (wasSubmit || session.getStatus() == ExamSessionStatus.SUBMITTED
                            || session.getStatus() == ExamSessionStatus.TIMED_OUT) {
                        submitted = true;
                        statusText = "Exam submitted successfully.";
                    } else {
                        statusText = "Answers saved.";
                    }
                }
            }
            case ERROR -> {
                awaitingStart = false;
                awaitingSave = false;
                awaitingSubmit = false;
                lastError = String.valueOf(msg.getPayload());
                statusText = "Server error.";
            }
            default -> statusText = "Unexpected: " + msg.getCommand();
        }
    }

    private ExamSession currentSession() {
        return examForm == null ? null : examForm.getSession();
    }

    private List<StudentAnswer> buildAnswerList() {
        List<StudentAnswer> list = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : answers.entrySet()) {
            list.add(new StudentAnswer(e.getKey(), e.getValue()));
        }
        return list;
    }

    private void mergeSession(ExamSession session) {
        if (examForm != null) {
            examForm.setSession(session);
        }
        if (session.getStatus() == ExamSessionStatus.SUBMITTED
                || session.getStatus() == ExamSessionStatus.TIMED_OUT) {
            submitted = true;
        }
    }
}
