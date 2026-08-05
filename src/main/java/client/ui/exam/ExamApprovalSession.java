package client.ui.exam;

import common.entities.Course;
import common.entities.Exam;
import common.entities.ExamStatus;
import common.entities.Subject;
import common.network.ExamRejectionRequest;
import common.network.Message;
import common.network.Message.Command;
import common.network.PendingExamFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Coordinator pending-exam session (scenario 4).
 */
public class ExamApprovalSession {

    public static final String ALL_LABEL = "All";

    private final List<Exam> pending = new ArrayList<>();
    private final List<Subject> mySubjects = new ArrayList<>();
    private final List<Course> allCourses = new ArrayList<>();
    private Exam selected;
    private Integer filterSubjectId; // null = All
    private Integer filterCourseId;  // null = All
    private String statusText = "";
    private String lastError;
    private boolean awaitingList;
    private boolean awaitingAction;
    private boolean awaitingSubjects;
    private boolean awaitingCourses;

    public List<Exam> getPending() {
        return List.copyOf(pending);
    }

    public List<Subject> getMySubjects() {
        return List.copyOf(mySubjects);
    }

    public List<Course> getAllCourses() {
        return List.copyOf(allCourses);
    }

    /** Courses under the selected subject, or empty when subject is All. */
    public List<Course> coursesForSelectedSubject() {
        if (filterSubjectId == null) {
            return List.of();
        }
        List<Course> out = new ArrayList<>();
        for (Course c : allCourses) {
            if (filterSubjectId.equals(c.getSubjectId())) {
                out.add(c);
            }
        }
        return out;
    }

    public Exam getSelected() {
        return selected;
    }

    public void setSelected(Exam selected) {
        this.selected = selected;
    }

    public Integer getFilterSubjectId() {
        return filterSubjectId;
    }

    public Integer getFilterCourseId() {
        return filterCourseId;
    }

    public void setFilterSubjectId(Integer subjectId) {
        this.filterSubjectId = subjectId;
        this.filterCourseId = null;
    }

    public void setFilterCourseId(Integer courseId) {
        this.filterCourseId = courseId;
    }

    public String getStatusText() {
        return statusText;
    }

    public String getLastError() {
        return lastError;
    }

    public Message requestMySubjects() {
        awaitingSubjects = true;
        lastError = null;
        return new Message(Command.GET_MY_SUBJECTS);
    }

    public Message requestCourses() {
        awaitingCourses = true;
        lastError = null;
        return new Message(Command.GET_COURSES);
    }

    public Message requestPending() {
        awaitingList = true;
        statusText = "Loading pending exams…";
        lastError = null;
        PendingExamFilter filter = new PendingExamFilter(filterSubjectId, filterCourseId);
        return new Message(Command.GET_PENDING_EXAMS, filter);
    }

    public Message requestApprove() {
        if (selected == null) {
            lastError = "Select an exam first.";
            return null;
        }
        if (selected.getStatus() != ExamStatus.PENDING_APPROVAL) {
            lastError = "Only pending exams can be approved.";
            return null;
        }
        awaitingAction = true;
        lastError = null;
        statusText = "Approving…";
        return new Message(Command.APPROVE_EXAM, selected.getId());
    }

    public Message requestReject(String reason) {
        String invalid = ExamFormValidator.validateRejectionReason(reason);
        if (invalid != null) {
            lastError = invalid;
            return null;
        }
        if (selected == null) {
            lastError = "Select an exam first.";
            return null;
        }
        awaitingAction = true;
        lastError = null;
        statusText = "Rejecting…";
        return new Message(Command.REJECT_EXAM,
                new ExamRejectionRequest(selected.getId(), 0, reason.trim()));
    }

    public void onServerMessage(Message msg) {
        if (msg == null) return;
        switch (msg.getCommand()) {
            case SUCCESS -> {
                Object payload = msg.getPayload();
                if (awaitingAction && payload instanceof Exam updated) {
                    awaitingAction = false;
                    pending.removeIf(e -> e.getId() == updated.getId()
                            || (e.getBaseId() > 0 && e.getBaseId() == updated.getBaseId()));
                    selected = null;
                    statusText = updated.getStatus() == ExamStatus.APPROVED
                            ? "Exam approved."
                            : "Exam rejected.";
                } else if (payload instanceof List<?> list) {
                    if (awaitingSubjects) {
                        awaitingSubjects = false;
                        mySubjects.clear();
                        for (Object o : list) {
                            if (o instanceof Subject s) mySubjects.add(s);
                        }
                    } else if (awaitingCourses) {
                        awaitingCourses = false;
                        allCourses.clear();
                        for (Object o : list) {
                            if (o instanceof Course c) allCourses.add(c);
                        }
                    } else {
                        awaitingList = false;
                        pending.clear();
                        for (Object o : list) {
                            if (o instanceof Exam e) pending.add(e);
                        }
                        statusText = pending.size() + " pending.";
                    }
                }
            }
            case ERROR -> {
                awaitingList = false;
                awaitingAction = false;
                awaitingSubjects = false;
                awaitingCourses = false;
                lastError = String.valueOf(msg.getPayload());
                statusText = "Server error.";
            }
            default -> { }
        }
    }
}
