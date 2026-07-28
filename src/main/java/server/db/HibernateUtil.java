package server.db;

import common.entities.User;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import server.config.DbSettings;

/**
 * Hibernate ORM bootstrap (Data tier, Person 1 infrastructure).
 *
 * <p>Builds the single {@link SessionFactory} programmatically — URL and
 * credentials come from {@link DatabaseConfig#getSettings()} (the same
 * {@link DbSettings} plain JDBC uses, dialog-configurable since Phase 0.5) —
 * so no secrets are hard-coded in an XML file and both halves of the Data
 * tier always target the same database.
 *
 * <p><b>For teammates:</b> when you migrate your DAO to Hibernate, annotate your
 * entity with JPA annotations (see {@link common.entities.User} for the example)
 * and register it below with {@code addAnnotatedClass(...)}. Then use
 * {@code HibernateUtil.getSessionFactory().openSession()} in your DAO
 * (see {@code UserDAO} for the usage pattern).
 */
public final class HibernateUtil {

    private static SessionFactory sessionFactory;

    private HibernateUtil() { }

    /** @return the lazily-created singleton session factory. */
    public static synchronized SessionFactory getSessionFactory() {
        if (sessionFactory == null) {
            DbSettings db = DatabaseConfig.getSettings();
            Configuration cfg = new Configuration()
                    .setProperty("hibernate.connection.driver_class", "com.mysql.cj.jdbc.Driver")
                    .setProperty("hibernate.connection.url", db.jdbcUrl())
                    .setProperty("hibernate.connection.username", db.user())
                    .setProperty("hibernate.connection.password", db.password())
                    // The schema is owned by our SQL migration files, not Hibernate.
                    .setProperty("hibernate.hbm2ddl.auto", "none")
                    .setProperty("hibernate.show_sql", "false");

            // ---- mapped entities (add yours here) ----
            cfg.addAnnotatedClass(User.class);
            cfg.addAnnotatedClass(common.entities.Question.class);
            cfg.addAnnotatedClass(common.entities.Exam.class);
            cfg.addAnnotatedClass(common.entities.ExamQuestion.class);
            cfg.addAnnotatedClass(common.entities.ExamRelease.class);
            cfg.addAnnotatedClass(common.entities.ExamSession.class);
            cfg.addAnnotatedClass(common.entities.StudentAnswer.class);
            cfg.addAnnotatedClass(common.entities.Grade.class);
            sessionFactory = cfg.buildSessionFactory();
            System.out.println("[HibernateUtil] SessionFactory initialised (ORM data tier up)");
        }
        return sessionFactory;
    }
}
