package server.db;

import org.junit.jupiter.api.BeforeEach;

/**
 * Shared lifecycle for Person 4 execution DAO tests: wipe P4-owned rows before each test.
 */
public abstract class ExecutionDaoTestBase {

    @BeforeEach
    void resetExecutionTables() {
        ExecutionTestFixture.wipeExecutionData();
    }
}
