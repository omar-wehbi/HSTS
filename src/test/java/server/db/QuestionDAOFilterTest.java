package server.db;

import common.entities.Question;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static server.db.QuestionBankTestFixture.COURSE_ALGORITHMS;
import static server.db.QuestionBankTestFixture.COURSE_DATABASES;

/**
 * Phase 3 — topic/difficulty filtering (Person 2).
 *
 * <p>{@code getByCourseFiltered} is the pool query behind Person 3's automatic
 * exam builder ("after setting total count, breakdown by topic and difficulty",
 * scenario 3.4) and doubles as the study bot's source-material query
 * (scenarios 13–14, R-047). Null topic/difficulty mean "no filter on that axis".
 */
class QuestionDAOFilterTest extends QuestionDaoTestBase {

    private Question algebraEasy;
    private Question algebraHard;
    private Question geometryEasy;

    @BeforeEach
    void seedPool() {
        algebraEasy  = dao.add(question(COURSE_ALGORITHMS, "algebra easy",  "Algebra",  "EASY"));
        algebraHard  = dao.add(question(COURSE_ALGORITHMS, "algebra hard",  "Algebra",  "HARD"));
        geometryEasy = dao.add(question(COURSE_ALGORITHMS, "geometry easy", "Geometry", "EASY"));
        dao.add(question(COURSE_DATABASES, "other course", "Algebra", "EASY"));
    }

    private static Question question(int courseId, String text, String topic, String difficulty) {
        Question q = QuestionBankTestFixture.sample(courseId, text);
        q.setTopic(topic);
        q.setDifficulty(difficulty);
        return q;
    }

    @Test
    void topicOnlyFilter() {
        assertThat(dao.getByCourseFiltered(COURSE_ALGORITHMS, "Algebra", null))
                .extracting(Question::getId)
                .containsExactlyInAnyOrder(algebraEasy.getId(), algebraHard.getId());
    }

    @Test
    void difficultyOnlyFilter() {
        assertThat(dao.getByCourseFiltered(COURSE_ALGORITHMS, null, "EASY"))
                .extracting(Question::getId)
                .containsExactlyInAnyOrder(algebraEasy.getId(), geometryEasy.getId());
    }

    @Test
    void topicAndDifficultyCombined() {
        assertThat(dao.getByCourseFiltered(COURSE_ALGORITHMS, "Algebra", "EASY"))
                .extracting(Question::getId)
                .containsExactly(algebraEasy.getId());
    }

    @Test
    void noFiltersBehavesLikeGetByCourse() {
        assertThat(dao.getByCourseFiltered(COURSE_ALGORITHMS, null, null))
                .extracting(Question::getId)
                .containsExactlyInAnyOrderElementsOf(
                        dao.getByCourse(COURSE_ALGORITHMS).stream().map(Question::getId).toList());
    }

    @Test
    void blankFiltersAreTreatedAsNoFilter() {
        assertThat(dao.getByCourseFiltered(COURSE_ALGORITHMS, "  ", ""))
                .hasSize(3);
    }

    @Test
    void impossibleCombinationReturnsEmptyListNotNull() {
        assertThat(dao.getByCourseFiltered(COURSE_ALGORITHMS, "Geometry", "HARD"))
                .isNotNull()
                .isEmpty();
    }

    @Test
    void otherCoursesNeverLeakIn() {
        assertThat(dao.getByCourseFiltered(COURSE_ALGORITHMS, "Algebra", "EASY"))
                .extracting(Question::getCourseId)
                .containsOnly(COURSE_ALGORITHMS);
    }

    @Test
    void retiredVersionsAreExcluded() {
        Question edit = question(COURSE_ALGORITHMS, "algebra easy v2", "Algebra", "EASY");
        edit.setBaseId(algebraEasy.getBaseId());
        dao.update(edit);

        assertThat(dao.getByCourseFiltered(COURSE_ALGORITHMS, "Algebra", "EASY"))
                .as("only the current version of the family may match")
                .hasSize(1)
                .first().extracting(Question::getQuestionText).isEqualTo("algebra easy v2");
    }

    @Test
    void daoIsUsableThroughTheReadOnlyQuestionSourceSeam() {
        // Person 3's exam builder and the future bot depend on the interface,
        // not on QuestionDAO — this proves the DAO actually satisfies it.
        QuestionSource source = dao;
        assertThat(source.getByCourseFiltered(COURSE_ALGORITHMS, "Algebra", null)).hasSize(2);
        assertThat(source.getByCourse(COURSE_ALGORITHMS)).hasSize(3);
        assertThat(source.getHistory(algebraEasy.getBaseId())).isNotEmpty();
    }
}
