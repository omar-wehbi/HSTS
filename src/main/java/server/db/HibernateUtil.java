package server.db;

import common.entities.User;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import server.config.ServerConfig;
import server.config.ServerConfig.Credentials;

/**
 * Hibernate ORM bootstrap (Data tier, Person 1 infrastructure).
 *
 * <p>Builds the single {@link SessionFactory} programmatically — connection URL
 * from {@link DatabaseConfig}, credentials from {@code server.properties} via
 * {@link ServerConfig} — so no secrets are hard-coded in an XML file.
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
            Credentials creds = ServerConfig.load();
            Configuration cfg = new Configuration()
                    .setProperty("hibernate.connection.driver_class", "com.mysql.cj.jdbc.Driver")
                    .setProperty("hibernate.connection.url", DatabaseConfig.URL)
                    .setProperty("hibernate.connection.username", creds.user())
                    .setProperty("hibernate.connection.password", creds.password())
                    // The schema is owned by our SQL migration files, not Hibernate.
                    .setProperty("hibernate.hbm2ddl.auto", "none")
                    .setProperty("hibernate.show_sql", "false");

            // ---- mapped entities (add yours here) ----
            cfg.addAnnotatedClass(User.class);

            sessionFactory = cfg.buildSessionFactory();
            System.out.println("[HibernateUtil] SessionFactory initialised (ORM data tier up)");
        }
        return sessionFactory;
    }
}
