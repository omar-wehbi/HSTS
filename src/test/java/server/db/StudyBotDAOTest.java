package server.db;

import common.network.StudyBotAnswer;
import common.network.StudyBotSourceType;
import common.network.StudyBotSourceView;
import common.network.StudyBotUsageReport;
import common.network.StudyBotView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StudyBotDAOTest extends ExecutionDaoTestBase {

    private final StudyBotDAO dao = new StudyBotDAO();

    @Test
    void createBotSourcesHistoryAndUsage() {
        QuestionBankTestFixture.ensureCourses();
        int teacherId = ExecutionTestFixture.userId("teacher");
        int maya = ExecutionTestFixture.userId("maya");
        int course = ExecutionTestFixture.COURSE_ALGORITHMS;
        ExecutionTestFixture.ensureCourseTeacher(course, teacherId);

        assertThat(dao.courseExists(course)).isTrue();
        assertThat(dao.teacherCanEdit(teacherId, course)).isTrue();

        StudyBotView bot = dao.createBot(course, "Algo Bot", true, teacherId);
        assertThat(bot.getName()).isEqualTo("Algo Bot");
        assertThat(dao.getBot(course).getCourseId()).isEqualTo(course);
        assertThat(dao.botAvailable(course)).isFalse();

        StudyBotView open = dao.setAvailability(course, true);
        assertThat(open.isAvailable()).isTrue();
        assertThat(dao.botAvailable(course)).isTrue();

        StudyBotSourceView src = dao.addSource(course, "Notes", "Big-O basics", teacherId);
        assertThat(src.getTitle()).isEqualTo("Notes");
        assertThat(dao.getSources(course)).hasSize(1);

        StudyBotSourceView updated = dao.updateSource(src.getId(), "Notes v2", "Updated", teacherId);
        assertThat(updated.getTitle()).isEqualTo("Notes v2");

        StudyBotSourceView fileSrc = dao.addSource(course, "PDF", "extracted", StudyBotSourceType.PDF, "a.pdf", teacherId);
        assertThat(fileSrc.getOriginalFileName()).isEqualTo("a.pdf");

        StudyBotAnswer ans = dao.saveHistory(course, maya, "What is O(n)?", "Linear", "ANSWERED");
        assertThat(ans.getQuestion()).contains("O(n)");
        assertThat(dao.personalHistory(maya)).isNotEmpty();

        StudyBotUsageReport usage = dao.usage(course);
        assertThat(usage.getTotalQuestions()).isEqualTo(1);
        assertThat(usage.getUniqueStudents()).isEqualTo(1);
        assertThat(usage.getRecentQuestions()).isNotEmpty();

        assertThat(dao.deleteSource(fileSrc.getId())).isTrue();
        assertThat(dao.getSources(course)).hasSize(1);
        assertThat(dao.getSource(src.getId()).getTitle()).isEqualTo("Notes v2");
    }

    @Test
    void getQuestionBankContextReadsCurrentQuestions() {
        QuestionBankTestFixture.ensureCourses();
        QuestionBankTestFixture.wipeQuestions();
        ExecutionTestFixture.wipeExecutionData();
        new QuestionDAO().add(QuestionBankTestFixture.sample(ExecutionTestFixture.COURSE_ALGORITHMS, "Bank ctx"));
        assertThat(dao.getQuestionBankContext(ExecutionTestFixture.COURSE_ALGORITHMS)).isNotEmpty();
    }
}
