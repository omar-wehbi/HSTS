package client.ui.exam;

import common.network.CreateStudyBotRequest;
import common.network.Message;
import common.network.Message.Command;
import common.network.SetStudyBotAvailabilityRequest;
import common.network.StudyBotSourceRequest;
import common.network.StudyBotSourceView;
import common.network.StudyBotUsageReport;
import common.network.StudyBotView;

import java.util.ArrayList;
import java.util.List;

/** Testable state for teacher study bot management. */
public class StudyBotTeacherSession {

    private final List<StudyBotSourceView> sources = new ArrayList<>();
    private int courseId;
    private StudyBotView botView;
    private StudyBotUsageReport usageReport;
    private String statusText = "";
    private String lastError;
    private boolean awaitingCreate;
    private boolean awaitingAvailability;
    private boolean awaitingAddSource;
    private boolean awaitingSources;
    private boolean awaitingDelete;
    private boolean awaitingUsage;
    private boolean awaitingBot;

    public List<StudyBotSourceView> getSources() {
        return List.copyOf(sources);
    }

    public int getCourseId() {
        return courseId;
    }

    public void setCourseId(int courseId) {
        this.courseId = courseId;
    }

    public StudyBotView getBotView() {
        return botView;
    }

    public StudyBotUsageReport getUsageReport() {
        return usageReport;
    }

    public String getStatusText() {
        return statusText;
    }

    public String getLastError() {
        return lastError;
    }

    public Message requestCreate(int courseId, String name, boolean includeQuestionBank) {
        if (courseId <= 0) {
            lastError = "Course ID is required.";
            return null;
        }
        if (name == null || name.isBlank()) {
            lastError = "Bot name is required.";
            return null;
        }
        this.courseId = courseId;
        awaitingCreate = true;
        statusText = "Creating study bot…";
        lastError = null;
        return new Message(Command.CREATE_STUDY_BOT,
                new CreateStudyBotRequest(courseId, name.trim(), includeQuestionBank));
    }

    public Message requestSetAvailability(int courseId, boolean available) {
        if (courseId <= 0) {
            lastError = "Course ID is required.";
            return null;
        }
        this.courseId = courseId;
        awaitingAvailability = true;
        statusText = available ? "Activating bot…" : "Deactivating bot…";
        lastError = null;
        return new Message(Command.SET_STUDY_BOT_AVAILABILITY,
                new SetStudyBotAvailabilityRequest(courseId, available));
    }

    public Message requestAddSource(int courseId, String title, String content) {
        if (courseId <= 0) {
            lastError = "Course ID is required.";
            return null;
        }
        if (title == null || title.isBlank()) {
            lastError = "Source title is required.";
            return null;
        }
        if (content == null || content.isBlank()) {
            lastError = "Source content is required.";
            return null;
        }
        this.courseId = courseId;
        awaitingAddSource = true;
        statusText = "Adding source…";
        lastError = null;
        return new Message(Command.ADD_STUDY_BOT_SOURCE,
                new StudyBotSourceRequest(courseId, title.trim(), content.trim()));
    }

    public Message requestSources(int courseId) {
        if (courseId <= 0) {
            lastError = "Course ID is required.";
            return null;
        }
        this.courseId = courseId;
        awaitingSources = true;
        statusText = "Loading sources…";
        lastError = null;
        return new Message(Command.GET_STUDY_BOT_SOURCES, courseId);
    }

    public Message requestDeleteSource(int sourceId) {
        if (sourceId <= 0) {
            lastError = "Select a source to delete.";
            return null;
        }
        awaitingDelete = true;
        statusText = "Deleting source…";
        lastError = null;
        return new Message(Command.DELETE_STUDY_BOT_SOURCE, sourceId);
    }

    public Message requestUsage(int courseId) {
        if (courseId <= 0) {
            lastError = "Course ID is required.";
            return null;
        }
        this.courseId = courseId;
        awaitingUsage = true;
        statusText = "Loading usage report…";
        lastError = null;
        return new Message(Command.GET_STUDY_BOT_USAGE, courseId);
    }

    public Message requestStudyBot(int courseId) {
        if (courseId <= 0) {
            lastError = "Course ID is required.";
            return null;
        }
        this.courseId = courseId;
        awaitingBot = true;
        statusText = "Loading bot info…";
        lastError = null;
        return new Message(Command.GET_STUDY_BOT, courseId);
    }

    @SuppressWarnings("unchecked")
    public void onServerMessage(Message msg) {
        if (msg == null) return;
        switch (msg.getCommand()) {
            case SUCCESS -> {
                Object payload = msg.getPayload();
                if ((awaitingCreate || awaitingAvailability || awaitingBot)
                        && payload instanceof StudyBotView view) {
                    awaitingCreate = false;
                    awaitingAvailability = false;
                    awaitingBot = false;
                    botView = view;
                    courseId = view.getCourseId();
                    statusText = view.getName() + " — "
                            + (view.isAvailable() ? "active" : "inactive");
                } else if (awaitingAddSource && payload instanceof StudyBotSourceView source) {
                    awaitingAddSource = false;
                    sources.add(source);
                    statusText = "Source added.";
                } else if (awaitingSources && payload instanceof List<?> list) {
                    awaitingSources = false;
                    sources.clear();
                    for (Object o : list) {
                        if (o instanceof StudyBotSourceView s) sources.add(s);
                    }
                    statusText = sources.size() + " source(s).";
                } else if (awaitingDelete) {
                    awaitingDelete = false;
                    if (payload instanceof Integer deletedId) {
                        sources.removeIf(s -> s.getId() == deletedId);
                        statusText = "Source deleted.";
                    } else if (payload instanceof StudyBotSourceView) {
                        statusText = "Source deleted.";
                    } else {
                        statusText = "Source deleted.";
                    }
                } else if (awaitingUsage && payload instanceof StudyBotUsageReport report) {
                    awaitingUsage = false;
                    usageReport = report;
                    statusText = report.getTotalQuestions() + " question(s) logged.";
                }
            }
            case ERROR -> {
                awaitingCreate = false;
                awaitingAvailability = false;
                awaitingAddSource = false;
                awaitingSources = false;
                awaitingDelete = false;
                awaitingUsage = false;
                awaitingBot = false;
                lastError = String.valueOf(msg.getPayload());
                statusText = "Server error.";
            }
            default -> statusText = "Unexpected: " + msg.getCommand();
        }
    }
}
