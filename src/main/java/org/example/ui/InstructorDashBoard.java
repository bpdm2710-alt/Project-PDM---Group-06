package org.example.ui;

import org.example.ui.components.RoundedPanel;
import org.example.ui.panel.InstructorCoursesPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;

import static org.example.ui.components.UITheme.*;

/**
 * Instructor workspace shell. The reference images (1585 / 1586) only show
 * the "My courses" section, so that is the single navigation entry here.
 */
public class InstructorDashBoard extends JFrame {

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);

    public InstructorDashBoard() {
        setTitle("Examly - Instructor Workspace");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1440, 1000);
        setMinimumSize(new Dimension(1180, 720));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        add(createSidebar(), BorderLayout.WEST);

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(BACKGROUND);
        main.add(createTopBar(), BorderLayout.NORTH);

        content.setBackground(BACKGROUND);
        content.add(new InstructorCoursesPanel(), "My courses");
        main.add(content, BorderLayout.CENTER);
        add(main, BorderLayout.CENTER);
    }

    // ---------------------------------------------------------------------
    // SIDEBAR
    // ---------------------------------------------------------------------

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(WHITE);
        sidebar.setPreferredSize(new Dimension(240, 0));
        sidebar.setBorder(new EmptyBorder(30, 16, 24, 16));

        JLabel logo = label("examly.", bold(32), PRIMARY);
        JPanel logoPanel = new JPanel(new BorderLayout());
        logoPanel.setBackground(WHITE);
        logoPanel.setBorder(new EmptyBorder(0, 14, 30, 0));
        logoPanel.add(logo, BorderLayout.CENTER);
        sidebar.add(logoPanel, BorderLayout.NORTH);

        JPanel nav = new JPanel();
        nav.setOpaque(false);
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));

        JLabel caption = label("INSTRUCTOR WORKSPACE", bold(10), MUTED);
        caption.setBorder(new EmptyBorder(0, 14, 28, 0));
        caption.setAlignmentX(Component.LEFT_ALIGNMENT);
        nav.add(caption);

        JButton courses = navButton("My courses", true);
        courses.addActionListener(e -> cards.show(content, "My courses"));
        nav.add(courses);

        sidebar.add(nav, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));

        RoundedPanel help = card(14, BACKGROUND);
        help.setLayout(new BoxLayout(help, BoxLayout.Y_AXIS));
        help.setMaximumSize(new Dimension(Integer.MAX_VALUE, 115));
        help.add(label("A little help?", bold(13), DARK));
        help.add(Box.createVerticalStrut(10));
        help.add(label("Find answers in the help center.", plain(11), MUTED));
        help.add(Box.createVerticalStrut(16));
        JButton helpLink = textButton("Visit help center ↗");
        helpLink.setAlignmentX(Component.LEFT_ALIGNMENT);
        help.add(helpLink);

        bottom.add(help);
        bottom.add(Box.createVerticalStrut(36));
        JButton logout = navButton("Log out", false);
        logout.addActionListener(e -> logout());
        bottom.add(logout);

        sidebar.add(bottom, BorderLayout.SOUTH);
        return sidebar;
    }

    private JButton navButton(String text, boolean active) {
        JButton button = new JButton("▦    " + text);
        button.setFont(plain(14));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setForeground(active ? PRIMARY : MUTED);
        button.setBackground(active ? LIGHT_BLUE : WHITE);
        button.setBorder(new EmptyBorder(13, 16, 13, 12));
        button.setUI(new BasicButtonUI());
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }

    // ---------------------------------------------------------------------
    // TOP BAR
    // ---------------------------------------------------------------------

    private JPanel createTopBar() {
        JPanel bar = new JPanel(new GridBagLayout());
        bar.setBackground(WHITE);
        bar.setPreferredSize(new Dimension(0, 88));
        bar.setBorder(new EmptyBorder(12, 40, 12, 40));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;

        JTextField search = new JTextField("⌕   Search courses, exams, and more");
        search.setFont(plain(13));
        search.setForeground(MUTED);
        search.setBackground(BACKGROUND);
        search.setBorder(new EmptyBorder(14, 16, 14, 16));
        search.setPreferredSize(new Dimension(390, 44));
        gbc.gridx = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        bar.add(search, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(0, 30, 0, 30);
        bar.add(label("Monday, 16 November 2026", plain(12), MUTED), gbc);

        JButton bell = new JButton("♧");
        bell.setFont(plain(23));
        bell.setForeground(MUTED);
        bell.setBorderPainted(false);
        bell.setContentAreaFilled(false);
        bell.setFocusPainted(false);
        bell.setCursor(new Cursor(Cursor.HAND_CURSOR));
        bell.addActionListener(e -> JOptionPane.showMessageDialog(
                this, "No new notifications.",
                "Notifications", JOptionPane.INFORMATION_MESSAGE));
        gbc.gridx = 2;
        gbc.insets = new Insets(0, 10, 0, 25);
        bar.add(bell, gbc);

        JPanel profile = new JPanel();
        profile.setOpaque(false);
        profile.setLayout(new BoxLayout(profile, BoxLayout.Y_AXIS));
        profile.add(label("Dr. Sarah Chen", bold(14), DARK));
        profile.add(Box.createVerticalStrut(5));
        profile.add(label("Instructor  ⌄", plain(11), MUTED));
        gbc.gridx = 3;
        gbc.insets = new Insets(0, 0, 0, 0);
        bar.add(profile, gbc);

        return bar;
    }

    private void logout() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to log out?", "Log out",
                JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            dispose();
            new LoginFrame().setVisible(true);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new InstructorDashBoard().setVisible(true));
    }
}
