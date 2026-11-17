package org.example.ui;
import javax.swing.*;

public class MainUI {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}