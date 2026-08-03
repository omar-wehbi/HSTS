package server.db;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/** Ensures the isolated test schema exists once per launcher session. */
public class TestDatabaseBootstrap implements LauncherSessionListener {
    @Override
    public void launcherSessionOpened(LauncherSession session) {
        TestDatabase.ensureReady();
    }
}
