package org.example;

import org.example.Service.AuthServiceImpl;
import org.example.Service.InstructorServiceImpl;
import org.example.Service.Services;
import org.example.Service.StudentServiceImpl;
import org.example.ui.LoginFrame;

import javax.swing.*;

/**
 * App entry point. Parts of a service that are not written yet answer
 * "not connected to the database yet" instead of showing fake data.
 */
public class Main {

    public static void main(String[] args) {
        Services.install(new AuthServiceImpl(), new StudentServiceImpl(), new InstructorServiceImpl());
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
