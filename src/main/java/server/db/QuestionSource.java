package server.db;

import common.entities.Question;

import java.util.List;

/**
 * Read-only view of the question bank (Person 2, Phase 3).
 *
 * <p><b>Pattern: Interface Segregation / Dependency Inversion</b> — modules that
 * only <em>consume</em> questions depend on this interface instead of the full
 * {@link QuestionDAO}:
 * <ul>
 *   <li>Person 3's exam builder pulls candidate pools per topic/difficulty
 *       (scenario 3.4 auto-build) without gaining the ability to mutate the bank;</li>
 *   <li>the future study bot (scenarios 13–14) reads a course's current questions
 *       as knowledge-source material (R-047) the same way.</li>
 * </ul>
 * Both can develop today against an in-memory fake of this interface and receive
 * the real DAO at integration with zero rework. Retired versions never leak
 * through any of these queries.
 */
public interface QuestionSource {

    /** Current questions of one course (latest version of each family). */
    List<Question> getByCourse(int courseId);

    /**
     * Current questions of one course narrowed by topic and/or difficulty.
     * Null or blank means "no filter on that axis".
     */
    List<Question> getByCourseFiltered(int courseId, String topic, String difficulty);

    /** All versions of one question family, oldest first. */
    List<Question> getHistory(int baseId);
}
