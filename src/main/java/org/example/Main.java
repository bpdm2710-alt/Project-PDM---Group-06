package org.example;

import org.example.ui.LoginFrame;

import javax.swing.*;

/**
 * App entry point. While the data layer is not written, no service is
 * installed, so every screen shows "Service unavailable" instead of data.
 * When the DAO/Service implementations exist, install them here:
 * {@code Services.install(new AuthServiceImpl(), new StudentServiceImpl(), new InstructorServiceImpl());}
 */
public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
