package org.example;

import org.example.Service.AuthServiceImpl;
import org.example.Service.InstructorServiceImpl;
import org.example.Service.Services;
import org.example.Service.StudentServiceImpl;
import org.example.ui.LoginFrame;
import org.example.util.DatabaseConnection;
import org.example.util.SqlScriptRunner;

import javax.swing.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * App entry point.
 *
 * With no argument it opens the login window. With one of the commands below it works on the
 * database from the command line and exits (used by the .bat files of the submission folder):
 * <pre>
 *   --check                connection test: settings file, database, user, DB clock, table count
 *   --setup-demo           (re)create the "DEMO - ..." exams    = database/demo/03_demo_seed.sql
 *   --cleanup-demo         remove the "DEMO - ..." exams        = database/demo/03_demo_cleanup.sql
 *   --add-sample-data      extra data for courses 2-15          = database/demo/04_extra_sample_data.sql
 *   --cleanup-sample-data  remove that extra data               = database/demo/04_extra_sample_data_cleanup.sql
 *   --config=PATH          (before a command or alone) read the settings from PATH
 * </pre>
 */
public class Main {

    public static void main(String[] args) {
        String command = null;
        for (String a : args) {
            if (a.startsWith("--config=")) {
                System.setProperty("app.config", a.substring("--config=".length()));
            } else {
                command = a;
            }
        }
        if (command == null) {
            try {
                DatabaseConnection.configSource(); // reads the settings now, so a missing file is told clearly
            } catch (Throwable e) {
                Throwable t = e.getCause() != null ? e.getCause() : e;
                JOptionPane.showMessageDialog(null, t.getMessage(), "Database settings", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
            Services.install(new AuthServiceImpl(), new StudentServiceImpl(), new InstructorServiceImpl());
            SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
            return;
        }
        int code = runCommand(command);
        try {
            DatabaseConnection.shutdown();
        } catch (Throwable ignored) {
            // the settings could not be read: there is no pool to close
        }
        System.exit(code);
    }

    private static int runCommand(String command) {
        try {
            switch (command) {
                case "--check" -> check();
                case "--setup-demo" -> {
                    SqlScriptRunner.runResource("03_demo_seed.sql", System.out);
                    System.out.println("Demo ready: 'DEMO - Open now' is open for 180 minutes from now (sv101-sv103).");
                }
                case "--cleanup-demo" -> {
                    SqlScriptRunner.runResource("03_demo_cleanup.sql", System.out);
                    System.out.println("DEMO exams removed.");
                }
                case "--add-sample-data" -> SqlScriptRunner.runResource("04_extra_sample_data.sql", System.out);
                case "--cleanup-sample-data" -> {
                    SqlScriptRunner.runResource("04_extra_sample_data_cleanup.sql", System.out);
                    System.out.println("Sample data removed.");
                }
                case "--help", "-h" -> help();
                default -> {
                    System.err.println("Unknown command: " + command);
                    help();
                    return 2;
                }
            }
            return 0;
        } catch (Exception | ExceptionInInitializerError e) {
            Throwable t = e instanceof ExceptionInInitializerError && e.getCause() != null ? e.getCause() : e;
            System.err.println("ERROR: " + t.getMessage());
            return 1;
        }
    }

    private static void check() throws Exception {
        System.out.println("Settings : " + DatabaseConnection.configSource());
        System.out.println("URL      : " + DatabaseConnection.url());
        System.out.println("User     : " + DatabaseConnection.username());
        try (Connection c = DatabaseConnection.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT DATABASE(), NOW(), @@session.time_zone, "
                     + "(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                     + "AND table_type = 'BASE TABLE'), "
                     + "(SELECT COUNT(*) FROM EXAMINATION WHERE Title LIKE 'DEMO - %')")) {
            rs.next();
            System.out.println("Database : " + rs.getString(1) + " (" + rs.getInt(4) + " tables, "
                    + rs.getInt(5) + " DEMO exams)");
            System.out.println("DB clock : " + rs.getString(2) + " (time zone " + rs.getString(3) + ")");
        }
        System.out.println("Connection OK.");
    }

    private static void help() {
        System.out.println("""
                Usage: java -jar Online_Examination_System.jar [--config=PATH] [command]
                  (no command)           open the app
                  --check                test the database connection
                  --setup-demo           create / reset the DEMO exams (start a demo session)
                  --cleanup-demo         remove the DEMO exams
                  --add-sample-data      add sample data for courses 2-15
                  --cleanup-sample-data  remove the sample data""");
    }
}
