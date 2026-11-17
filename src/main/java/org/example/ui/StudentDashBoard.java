package org.example.ui;

import org.example.ui.panel.CoursesPanel;
import org.example.ui.panel.StudentOverviewPanel;
import org.example.ui.panel.StudentExamPanel;
import org.example.ui.panel.StudentResultPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class StudentDashBoard extends JFrame {
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentArea = new JPanel(cardLayout);
    private JButton activeNavBtn = null;

    public StudentDashBoard(String displayName) {
        setTitle("Examly - Student Workspace");
        setSize(1400, 900);
        setMinimumSize(new Dimension(1200, 800));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JPanel sidebar = buildSidebar();
        JPanel topBar = buildTopBar(displayName);

        contentArea.setBackground(UiKit.BACKGROUND);
        
        contentArea.add(new StudentOverviewPanel(), "Overview");
        contentArea.add(new CoursesPanel(), "Courses");
        contentArea.add(new StudentExamPanel(), "My exams");
        contentArea.add(new StudentResultPanel(), "Results");

        JPanel rightSide = new JPanel(new BorderLayout());
        rightSide.add(topBar, BorderLayout.NORTH);
        rightSide.add(contentArea, BorderLayout.CENTER);

        add(sidebar, BorderLayout.WEST);
        add(rightSide, BorderLayout.CENTER);

        cardLayout.show(contentArea, "Overview");
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(UiKit.WHITE);
        sidebar.setPreferredSize(new Dimension(260, 0));
        sidebar.setBorder(new EmptyBorder(24, 16, 24, 16));

        JLabel logo = new JLabel("examly.");
        logo.setFont(new Font("SansSerif", Font.BOLD, 32));
        logo.setForeground(UiKit.PRIMARY);
        logo.setBorder(new EmptyBorder(0, 16, 24, 0));
        sidebar.add(logo, BorderLayout.NORTH);

        JPanel navPanel = new JPanel();
        navPanel.setBackground(UiKit.WHITE);
        navPanel.setLayout(new BoxLayout(navPanel, BoxLayout.Y_AXIS));

        JLabel sectionLabel = new JLabel("STUDENT WORKSPACE");
        sectionLabel.setFont(new Font("SansSerif", Font.BOLD, 10));
        sectionLabel.setForeground(UiKit.MUTED);
        sectionLabel.setBorder(new EmptyBorder(0, 16, 20, 0));
        navPanel.add(sectionLabel);

        addNavButton(navPanel, "▦", "Overview");
        addNavButton(navPanel, "♧", "Courses");
        addNavButton(navPanel, "▤", "My exams");
        addNavButton(navPanel, "▥", "Results");

        sidebar.add(navPanel, BorderLayout.CENTER);

        // Bottom Section (Help Card + Logout)
        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));

        JPanel helpCard = new JPanel();
        helpCard.setBackground(UiKit.BACKGROUND);
        helpCard.setLayout(new BoxLayout(helpCard, BoxLayout.Y_AXIS));
        helpCard.setBorder(new EmptyBorder(16, 16, 16, 16));
        helpCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        
        JLabel helpTitle = new JLabel("A little help?");
        helpTitle.setFont(new Font("SansSerif", Font.BOLD, 13));
        helpTitle.setForeground(UiKit.DARK);
        JLabel helpLink = new JLabel("Visit help center ↗");
        helpLink.setFont(new Font("SansSerif", Font.PLAIN, 11));
        helpLink.setForeground(UiKit.PRIMARY);
        
        helpCard.add(helpTitle);
        helpCard.add(Box.createVerticalStrut(8));
        helpCard.add(helpLink);
        
        JButton logoutBtn = UiKit.createNavButton("↪", "Log out");
        logoutBtn.addActionListener(e -> { dispose(); new LoginFrame().setVisible(true); });

        bottom.add(helpCard);
        bottom.add(Box.createVerticalStrut(16));
        bottom.add(logoutBtn);
        
        sidebar.add(bottom, BorderLayout.SOUTH);

        return sidebar;
    }

    private void addNavButton(JPanel navPanel, String icon, String name) {
        JButton btn = UiKit.createNavButton(icon, name);

        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                if (activeNavBtn != btn) btn.setBackground(UiKit.BACKGROUND);
            }
            public void mouseExited(MouseEvent e) {
                if (activeNavBtn != btn) btn.setBackground(UiKit.WHITE);
            }
        });

        btn.addActionListener(e -> {
            if (activeNavBtn != null) UiKit.setInactiveNav(activeNavBtn);
            activeNavBtn = btn;
            UiKit.setActiveNav(btn);
            cardLayout.show(contentArea, name);
        });

        navPanel.add(btn);
        navPanel.add(Box.createVerticalStrut(4));

        if (activeNavBtn == null) btn.doClick(); 
    }

    private JPanel buildTopBar(String displayName) {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(UiKit.WHITE);
        topBar.setPreferredSize(new Dimension(0, 80));
        topBar.setBorder(new EmptyBorder(0, 40, 0, 40));

        JTextField search = new JTextField("🔍  Search courses, exams, and more");
        search.setForeground(UiKit.MUTED);
        search.setBackground(UiKit.BACKGROUND);
        search.setBorder(new EmptyBorder(10, 16, 10, 16));
        search.setPreferredSize(new Dimension(380, 42));
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 19));
        searchPanel.setOpaque(false);
        searchPanel.add(search);
        topBar.add(searchPanel, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 19));
        right.setOpaque(false);

        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy"));
        JLabel date = new JLabel(dateStr);
        date.setFont(new Font("SansSerif", Font.PLAIN, 12));
        date.setForeground(UiKit.MUTED);
        right.add(date);

        JLabel bell = new JLabel("🔔");
        bell.setFont(new Font("SansSerif", Font.PLAIN, 18));
        bell.setForeground(UiKit.MUTED);
        right.add(bell);

        JPanel profile = new JPanel();
        profile.setOpaque(false);
        profile.setLayout(new BoxLayout(profile, BoxLayout.Y_AXIS));
        
        JLabel name = new JLabel(displayName);
        name.setFont(new Font("SansSerif", Font.BOLD, 14));
        name.setForeground(UiKit.DARK);
        
        JLabel role = new JLabel("Student · CS2024 ⌄");
        role.setFont(new Font("SansSerif", Font.PLAIN, 11));
        role.setForeground(UiKit.MUTED);

        profile.add(name);
        profile.add(role);
        right.add(profile);

        topBar.add(right, BorderLayout.EAST);
        return topBar;
    }
}