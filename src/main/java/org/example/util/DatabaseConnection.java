package org.example.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Database access through a HikariCP connection pool.
 *
 * Opening a new TLS connection to Aiven for every click costs a few hundred milliseconds; the pool
 * keeps a few connections open and hands them out again. Callers do not change: they still write
 * {@code try (Connection c = DatabaseConnection.getConnection()) { ... }} – close() returns the
 * connection to the pool (and HikariCP resets autocommit if a transaction changed it).
 *
 * Every pooled connection runs {@code SET time_zone = <db.timezone>} (default +07:00), so NOW() in
 * SQL and Db.now() in the services use the database clock in Vietnam time – the same time zone as the
 * stored Exam_date / StartTime values – whatever the clock or time zone of the user's PC.
 *
 * application.properties keys:
 *   db.url, db.username, db.password                  (required)
 *   db.ssl.truststore, db.ssl.truststore.password     (Aiven; leave out for a local MySQL)
 *   db.timezone   (optional, default +07:00)          db.pool.size (optional, default 5)
 *
 * Where the settings come from (first one found):
 *   1. the file named by -Dapp.config=<path> (or the --config=<path> argument of Main)
 *   2. application.properties in the folder of the jar (the submitted / shared build)
 *   3. application.properties on the classpath (src/main/Sources, used when running from IntelliJ)
 * The packaged jar does not contain application.properties (see pom.xml), so a jar never carries a
 * password: whoever runs it puts their own application.properties next to it. A relative
 * db.ssl.truststore path in a file is resolved against that file's folder.
 */
public final class DatabaseConnection {

    private static final Properties PROPERTIES = new Properties();
    private static final Pattern TIME_ZONE = Pattern.compile("[+-](0\\d|1[0-4]):[0-5]\\d");

    private static volatile HikariDataSource pool;

    /** Folder of the settings file (null when read from the classpath). */
    private static Path configDir;
    private static String configSource;

    static {
        try {
            load();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private DatabaseConnection() {
    }

    private static void load() throws IOException {
        String explicit = System.getProperty("app.config");
        if (explicit != null && !explicit.isBlank()) {
            Path f = Paths.get(explicit.trim()).toAbsolutePath();
            if (!Files.isRegularFile(f)) {
                throw new IllegalStateException("app.config file not found: " + f);
            }
            loadFile(f);
            return;
        }
        Path besideJar = jarFolder();
        if (besideJar != null && Files.isRegularFile(besideJar.resolve("application.properties"))) {
            loadFile(besideJar.resolve("application.properties"));
            return;
        }
        try (InputStream input = DatabaseConnection.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (input == null) {
                throw new IllegalStateException("application.properties not found. Put it "
                        + (besideJar != null ? "next to the jar (" + besideJar + ")" : "in src/main/Sources")
                        + " or start with -Dapp.config=<path>. Copy application.properties.example.");
            }
            PROPERTIES.load(input);
            configSource = "classpath:application.properties";
        }
    }

    private static void loadFile(Path f) throws IOException {
        try (InputStream input = Files.newInputStream(f)) {
            PROPERTIES.load(input);
        }
        configDir = f.getParent();
        configSource = f.toString();
    }

    /** The folder of the running jar, or null when not started from a jar (IntelliJ, tests). */
    private static Path jarFolder() {
        try {
            var src = DatabaseConnection.class.getProtectionDomain().getCodeSource();
            if (src == null) {
                return null;
            }
            Path p = Paths.get(src.getLocation().toURI());
            return Files.isRegularFile(p) && p.toString().endsWith(".jar") ? p.getParent() : null;
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
    }

    /** Where the settings were read from (shown by Main --check). */
    public static String configSource() {
        return configSource;
    }

    /** db.url without the password (shown by Main --check). */
    public static String url() {
        return PROPERTIES.getProperty("db.url", "").trim();
    }

    public static String username() {
        return PROPERTIES.getProperty("db.username", "").trim();
    }

    /** A connection from the pool. Always close it (try-with-resources) to give it back. */
    public static Connection getConnection() throws SQLException {
        return pool().getConnection();
    }

    /** The time zone every connection uses, e.g. "+07:00". */
    public static String timeZone() {
        String tz = PROPERTIES.getProperty("db.timezone", "+07:00").trim();
        if (!TIME_ZONE.matcher(tz).matches()) {
            throw new IllegalStateException("db.timezone must look like +07:00, got: " + tz);
        }
        return tz;
    }

    /** Closes every pooled connection (called automatically when the app exits). */
    public static synchronized void shutdown() {
        if (pool != null) {
            pool.close();
            pool = null;
        }
    }

    private static HikariDataSource pool() throws SQLException {
        HikariDataSource p = pool;
        if (p != null) {
            return p;
        }
        synchronized (DatabaseConnection.class) {
            if (pool == null) {
                pool = createPool(); // stays null when it fails, so the next call tries again
                Runtime.getRuntime().addShutdownHook(new Thread(DatabaseConnection::shutdown, "db-pool-shutdown"));
            }
            return pool;
        }
    }

    private static HikariDataSource createPool() throws SQLException {
        String url = PROPERTIES.getProperty("db.url");
        if (url == null || url.isBlank()) {
            throw new SQLException("db.url is missing in application.properties");
        }

        // Aiven needs the truststore (sslMode=VERIFY_CA); a local MySQL without SSL does not.
        String trustStore = PROPERTIES.getProperty("db.ssl.truststore");
        if (trustStore != null && !trustStore.isBlank()) {
            Path ts = Paths.get(trustStore.trim());
            if (!ts.isAbsolute() && configDir != null) {
                ts = configDir.resolve(ts);       // e.g. db.ssl.truststore=aiven-truststore.p12 next to the jar
            }
            System.setProperty("javax.net.ssl.trustStore", ts.toString());
            System.setProperty("javax.net.ssl.trustStorePassword",
                    PROPERTIES.getProperty("db.ssl.truststore.password", "").trim());
            System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");
        }

        HikariConfig config = new HikariConfig();
        config.setPoolName("online-exam");
        config.setJdbcUrl(url.trim());
        config.setUsername(PROPERTIES.getProperty("db.username"));
        config.setPassword(PROPERTIES.getProperty("db.password", ""));
        int size = parsePoolSize(PROPERTIES.getProperty("db.pool.size", "5"));
        config.setMaximumPoolSize(size);
        config.setMinimumIdle(Math.min(2, size));
        config.setConnectionTimeout(10_000);   // wait at most 10 s for a free connection
        config.setIdleTimeout(300_000);        // close connections idle for 5 min
        config.setMaxLifetime(1_200_000);      // renew every 20 min (before the server drops them)
        config.setConnectionInitSql("SET time_zone = '" + timeZone() + "'");
        try {
            return new HikariDataSource(config); // fails fast on a wrong URL / password
        } catch (RuntimeException e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            throw new SQLException("Cannot connect to the database: " + root.getMessage(), e);
        }
    }

    private static int parsePoolSize(String value) {
        try {
            int n = Integer.parseInt(value.trim());
            return Math.max(1, Math.min(n, 20));
        } catch (NumberFormatException e) {
            return 5;
        }
    }
}
