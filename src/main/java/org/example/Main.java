package org.example;

import org.example.Service.AuthServiceImpl;
import org.example.Service.Services;
import org.example.Service.StudentServiceImpl;
import org.example.ui.LoginFrame;

import javax.swing.*;

/**
 * App entry point. Services that are not installed yet (instructor) make their
 * screens show "Service unavailable" instead of fake data.
 */
public class Main {

    public static void main(String[] args) {
        Services.install(new AuthServiceImpl(), new StudentServiceImpl(), null);
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
