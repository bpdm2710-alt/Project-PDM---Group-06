package org.example.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public final class UiKit {
    public static final Color PRIMARY       = new Color(30, 115, 235);
    public static final Color DARK          = new Color(25, 48, 81);
    public static final Color MUTED         = new Color(103, 124, 151);
    public static final Color BACKGROUND    = new Color(242, 247, 253);
    public static final Color LIGHT_BLUE    = new Color(225, 240, 255);
    public static final Color LIGHT_BLUE_2  = new Color(235, 245, 255);
    public static final Color WHITE         = Color.WHITE;
    public static final Color BORDER        = new Color(225, 233, 243);

    private UiKit() {}

    public static JButton createNavButton(String icon, String text) {
        JButton btn = new JButton(icon + "    " + text);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 14));
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setForeground(UiKit.MUTED);
        btn.setBackground(UiKit.WHITE);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        btn.setBorder(new EmptyBorder(0, 16, 0, 16));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    public static void setActiveNav(JButton btn) {
        btn.setBackground(UiKit.LIGHT_BLUE);
        btn.setForeground(UiKit.PRIMARY);
        btn.setFont(new Font("SansSerif", Font.BOLD, 14));
    }

    public static void setInactiveNav(JButton btn) {
        btn.setBackground(UiKit.WHITE);
        btn.setForeground(UiKit.MUTED);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 14));
    }
    
    public static JLabel tag(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.BOLD, 10));
        l.setForeground(UiKit.PRIMARY);
        l.setOpaque(true);
        l.setBackground(UiKit.LIGHT_BLUE_2);
        l.setBorder(new EmptyBorder(6, 11, 6, 11));
        return l;
    }
}