package client.ui.exam;

import common.entities.Course;
import common.network.Message;
import common.network.Message.Command;
import common.network.StudyBotAnswer;
import common.network.StudyBotQuestionRequest;
import common.network.StudyBotView;

import java.util.ArrayList;
import java.util.List;

/** Testable state for student study bot (scenarios 13–14). */
public class StudyBotStudentSession {

    private final List<Course> courses = new ArrayList<>();
    private final List<StudyBotAnswer> history = new ArrayList<>();
    private int selectedCourseId;
    private StudyBotView botView;
    private StudyBotAnswer lastAnswer;
    private String statusText = "";
    private String lastError;
    private boolean awaitingCourses;
    private boolean awaitingBot;
    private boolean awaitingAsk;
    private boolean awaitingHistory;

    public List<Course> getCourses() {
        return List.copyOf(courses);
    }

    public List<StudyBotAnswer> getHistory() {
        return List.copyOf(history);
    }

    public int getSelectedCourseId() {
        return selectedCourseId;
    }

    public void setSelectedCourseId(int selectedCourseId) {
        this.selectedCourseId = selectedCourseId;
    }

    public StudyBotView getBotView() {
        return botView;
    }

    public StudyBotAnswer getLastAnswer() {
        return lastAnswer;
    }

    public String getStatusText() {
        return statusText;
    }

    public String getLastError() {
        return lastError;
    }

    public Message requestCourses() {
        awaitingCourses = true;
        statusText = "Loading courses…";
        lastError = null;
        return new Message(Command.GET_COURSES);
    }

    public Message requestStudyBot(int courseId) {
        if (courseId <= 0) {
            lastError = "Select or enter a course ID.";
            return null;
        }
        selectedCourseId = courseId;
        awaitingBot = true;
        statusText = "Loading study bot…";
        lastError = null;
        return new Message(Command.GET_STUDY_BOT, courseId);
    }

    public Message requestAsk(int courseId, String question) {
        if (courseId <= 0) {
            lastError = "Select or enter a course ID.";
            return null;
        }
        if (question == null || question.isBlank()) {
            lastError = "Enter a question.";
            return null;
        }
        awaitingAsk = true;
        statusText = "Asking study bot…";
        lastError = null;
        return new Message(Command.ASK_STUDY_BOT,
                new StudyBotQuestionRequest(courseId, question.trim()));
    }

    public Message requestHistory() {
        awaitingHistory = true;
        statusText = "Loading history…";
        lastError = null;
        return new Message(Command.GET_MY_STUDY_BOT_HISTORY);
    }

    @SuppressWarnings("unchecked")
    public void onServerMessage(Message msg) {
        if (msg == null) return;
        switch (msg.getCommand()) {
            case SUCCESS -> {
                Object payload = msg.getPayload();
                if (awaitingBot && payload instanceof StudyBotView view) {
                    awaitingBot = false;
                    botView = view;
                    selectedCourseId = view.getCourseId();
                    statusText = view.getName() + (view.isAvailable() ? " (available)" : " (unavailable)");
                } else if (awaitingAsk && payload instanceof StudyBotAnswer answer) {
                    awaitingAsk = false;
                    lastAnswer = answer;
                    statusText = "Answer received.";
                } else if (payload instanceof List<?> list) {
                    if (!list.isEmpty() && list.get(0) instanceof Course) {
                        awaitingCourses = false;
                        courses.clear();
                        for (Object o : list) {
                            if (o instanceof Course c) courses.add(c);
                        }
                        statusText = courses.size() + " course(s).";
                    } else if (!list.isEmpty() && list.get(0) instanceof StudyBotAnswer) {
                        awaitingHistory = false;
                        history.clear();
                        for (Object o : list) {
                            if (o instanceof StudyBotAnswer a) history.add(a);
                        }
                        statusText = history.size() + " history item(s).";
                    } else if (awaitingCourses) {
                        awaitingCourses = false;
                        courses.clear();
                        statusText = "0 course(s).";
                    } else if (awaitingHistory) {
                        awaitingHistory = false;
                        history.clear();
                        statusText = "0 history item(s).";
                    }
                }
            }
            case ERROR -> {
                awaitingCourses = false;
                awaitingBot = false;
                awaitingAsk = false;
                awaitingHistory = false;
                lastError = String.valueOf(msg.getPayload());
                statusText = "Server error.";
            }
            default -> statusText = "Unexpected: " + msg.getCommand();
        }
    }
}
