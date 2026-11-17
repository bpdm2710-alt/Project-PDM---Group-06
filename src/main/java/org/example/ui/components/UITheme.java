package org.example.ui.components;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;

/**
 * Shared design tokens and small factory helpers for the Examly UI.
 *
 * The colours are tuned to the reference screens: the blue used on the
 * primary actions/buttons, the very light blue page background and the
 * muted slate text colour.
 */
public final class UITheme {

    public static final Color PRIMARY = new Color(37, 99, 235);        // #2563EB
    public static final Color PRIMARY_HOVER = new Color(29, 86, 216);
    public static final Color DARK = new Color(30, 48, 81);            // #1E3051
    public static final Color MUTED = new Color(100, 121, 153);        // #64799B
    public static final Color FAINT = new Color(139, 158, 183);
    public static final Color BACKGROUND = new Color(244, 248, 254);   // #F4F8FE
    public static final Color LIGHT_BLUE = new Color(232, 241, 255);   // #E8F1FF
    public static final Color TAG_BLUE = new Color(225, 239, 255);     // #E1EFFF
    public static final Color BORDER = new Color(226, 233, 245);       // #E2E9F5
    public static final Color WHITE = Color.WHITE;

    public static final Color GREEN = new Color(21, 132, 89);
    public static final Color GREEN_BG = new Color(228, 246, 237);
    public static final Color AMBER_TEXT = new Color(176, 118, 17);
    public static final Color AMBER_BG = new Color(255, 244, 216);

    public static final Font FONT = new Font("SansSerif", Font.PLAIN, 13);

    private UITheme() {
    }

    public static Font plain(int size) {
        return FONT.deriveFont(Font.PLAIN, size);
    }

    public static Font bold(int size) {
        return FONT.deriveFont(Font.BOLD, size);
    }

    public static JLabel label(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    /** Small rounded tag, e.g. "ATTEMPT 1 OF 2" or "MULTIPLE CHOICE". */
    public static JLabel tag(String text) {
        JLabel tag = label(text, bold(10), PRIMARY);
        tag.setOpaque(true);
        tag.setBackground(TAG_BLUE);
        tag.setBorder(new EmptyBorder(6, 11, 6, 11));
        return tag;
    }

    public static JLabel greenTag(String text) {
        JLabel tag = label(text, bold(10), GREEN);
        tag.setOpaque(true);
        tag.setBackground(GREEN_BG);
        tag.setBorder(new EmptyBorder(6, 11, 6, 11));
        return tag;
    }

    public static JButton primaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(bold(13));
        button.setForeground(WHITE);
        button.setBackground(PRIMARY);
        button.setUI(new BasicButtonUI());
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }

    public static JButton secondaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(bold(12));
        button.setForeground(PRIMARY);
        button.setBackground(LIGHT_BLUE);
        button.setUI(new BasicButtonUI());
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }

    public static JButton textButton(String text) {
        JButton button = new JButton(text);
        button.setFont(bold(12));
        button.setForeground(PRIMARY);
        button.setUI(new BasicButtonUI());
        button.setOpaque(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }

    /** A flat card background used by the dashboard shells. */
    public static RoundedPanel card() {
        return card(14, WHITE);
    }

    public static RoundedPanel card(int radius, Color background) {
        RoundedPanel panel = new RoundedPanel(radius, background);
        panel.setBorder(new EmptyBorder(20, 22, 20, 22));
        return panel;
    }
}
