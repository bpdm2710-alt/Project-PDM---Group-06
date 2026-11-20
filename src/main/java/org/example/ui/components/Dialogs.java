package org.example.ui.components;

import org.example.Service.ServiceException;

import javax.swing.*;
import java.awt.*;

/** Small helpers so every screen reports service errors the same way. */
public final class Dialogs {

    private Dialogs() {
    }

    public static void error(Component parent, ServiceException e) {
        String title = switch (e.getKind()) {
            case VALIDATION -> "Check your input";
            case CONFLICT -> "Not possible right now";
            case UNAVAILABLE -> "Service unavailable";
            default -> "Something went wrong";
        };
        JOptionPane.showMessageDialog(parent, e.getMessage(), title, JOptionPane.WARNING_MESSAGE);
    }

    public static boolean confirm(Component parent, String title, String message) {
        return JOptionPane.showConfirmDialog(parent, message, title,
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }
}
