package server.db;

import org.junit.jupiter.api.BeforeEach;

/**
 * Shared lifecycle for every {@code QuestionDAO} test class (Person 2, Phase 1).
 *
 * <p><b>Pattern: Template Method</b> — the base class owns the wipe/reseed
 * lifecycle; subclasses only write scenario steps against a bank that is
 * guaranteed empty, with courses 1–3 present, before every single test.
 *
 * <p><b>Prerequisite: a running MySQL</b> with the {@code hsts_a3_db} schema
 * (build it once with {@code seed_test_scenarios.sql}) reachable via the
 * settings resolved by {@link DatabaseConfig} — exactly like Person 1's
 * {@code UserDAOTest}. Point the suite at another instance with
 * {@code -Dhsts.db.host=... -Dhsts.db.password=...} (Phase 0.5 precedence).
 *
 * <p><b>Why not H2 in-memory?</b> The schema is MySQL-specific where it counts
 * for these tests — the {@code ENUM('EASY','MEDIUM','HARD')} column,
 * {@code ALTER TABLE ... AUTO_INCREMENT} resets, and MySQL FK error codes the
 * DAO's failure paths depend on. H2's MySQL compatibility mode emulates none of
 * these faithfully, so a green H2 run would not prove the code works on the
 * database the course demo actually uses. Testing against real MySQL keeps the
 * suite honest; the Phase 0.5 settings make "which MySQL" flexible.
 */
public abstract class QuestionDaoTestBase {

    /** The DAO under test; fresh per test class instance. */
    protected final QuestionDAO dao = new QuestionDAO();

    @BeforeEach
    void resetQuestionBank() {
        QuestionBankTestFixture.ensureCourses();
        QuestionBankTestFixture.wipeQuestions();
    }
}
