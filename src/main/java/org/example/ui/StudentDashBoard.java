

package org.example.ui;

import org.example.ui.panel.CoursesPanel;
import org.example.ui.panel.ExamPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class StudentDashBoard extends JFrame {

    // ===== Color palette =====
    private static final Color PRIMARY = new Color(30, 115, 235);
    private static final Color DARK = new Color(25, 48, 81);
    private static final Color MUTED = new Color(103, 124, 151);
    private static final Color BACKGROUND = new Color(242, 247, 253);
    private static final Color LIGHT_BLUE = new Color(225, 240, 255);
    private static final Color BORDER = new Color(225, 233, 243);
    private static final Color WHITE = Color.WHITE;

    // ===== Layout =====
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(cardLayout);

    private JButton selectedNavButton;

    public StudentDashBoard() {
        setTitle("Examly - Student Workspace");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1920, 1080);
        setMinimumSize(new Dimension(1280, 720));
        setLocationRelativeTo(null);

        setLayout(new BorderLayout());

        add(createSidebar(), BorderLayout.WEST);
        add(createMainArea(), BorderLayout.CENTER);

        contentPanel.add(createOverviewPanel(), "Overview");
        contentPanel.add(new CoursesPanel(), "Courses");
        contentPanel.add(new ExamPanel(), "My exams");
        contentPanel.add(createPlaceholderPanel("Results"), "Results");

        cardLayout.show(contentPanel, "Overview");
    }

    // =====================================================
    // SIDEBAR
    // =====================================================

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(WHITE);
        sidebar.setPreferredSize(new Dimension(240, 0));
        sidebar.setBorder(new EmptyBorder(30, 16, 24, 16));

        // Logo
        JLabel logo = new JLabel("examly.");
        logo.setFont(new Font("SansSerif", Font.BOLD, 32));
        logo.setForeground(PRIMARY);

        JPanel logoPanel = new JPanel(new BorderLayout());
        logoPanel.setBackground(WHITE);
        logoPanel.setBorder(new EmptyBorder(0, 14, 30, 0));
        logoPanel.add(logo, BorderLayout.CENTER);

        sidebar.add(logoPanel, BorderLayout.NORTH);

        // Navigation
        JPanel navigation = new JPanel();
        navigation.setBackground(WHITE);
        navigation.setLayout(new BoxLayout(navigation, BoxLayout.Y_AXIS));

        JLabel workspaceLabel = new JLabel("STUDENT WORKSPACE");
        workspaceLabel.setFont(new Font("SansSerif", Font.BOLD, 10));
        workspaceLabel.setForeground(MUTED);
        workspaceLabel.setBorder(new EmptyBorder(0, 14, 28, 0));
        workspaceLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        navigation.add(workspaceLabel);

        addNavigationButton(navigation, "▦", "Overview");
        addNavigationButton(navigation, "♧", "Courses");
        addNavigationButton(navigation, "▤", "My exams");
        addNavigationButton(navigation, "▥", "Results");

        sidebar.add(navigation, BorderLayout.CENTER);

        // Bottom area
        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));

        RoundedPanel helpCard = new RoundedPanel(14, BACKGROUND);
        helpCard.setLayout(new BoxLayout(helpCard, BoxLayout.Y_AXIS));
        helpCard.setBorder(new EmptyBorder(18, 16, 18, 16));
        helpCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 115));

        JLabel helpTitle = new JLabel("A little help?");
        helpTitle.setFont(new Font("SansSerif", Font.BOLD, 13));
        helpTitle.setForeground(DARK);

        JLabel helpDescription = new JLabel("Find answers in the help center.");
        helpDescription.setFont(new Font("SansSerif", Font.PLAIN, 11));
        helpDescription.setForeground(MUTED);

        JButton helpButton = createTextButton("Visit help center ↗");

        helpCard.add(helpTitle);
        helpCard.add(Box.createVerticalStrut(10));
        helpCard.add(helpDescription);
        helpCard.add(Box.createVerticalStrut(16));
        helpCard.add(helpButton);

        bottom.add(helpCard);
        bottom.add(Box.createVerticalStrut(36));

        JButton logoutButton = createNavButton("⇥", "Log out");
        logoutButton.addActionListener(e -> logout());

        bottom.add(logoutButton);

        sidebar.add(bottom, BorderLayout.SOUTH);

        return sidebar;
    }

    private void addNavigationButton(
            JPanel navigation,
            String icon,
            String name
    ) {
        JButton button = createNavButton(icon, name);

        button.addActionListener(e -> {
            cardLayout.show(contentPanel, name);
            updateSelectedNavigation(button);
        });

        navigation.add(button);
        navigation.add(Box.createVerticalStrut(8));

        if (name.equals("Overview")) {
            updateSelectedNavigation(button);
        }
    }

    private JButton createNavButton(String icon, String name) {
        JButton button = new JButton(icon + "    " + name);

        button.setFont(new Font("SansSerif", Font.PLAIN, 14));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setForeground(MUTED);
        button.setBackground(WHITE);
        button.setBorder(new EmptyBorder(13, 16, 13, 12));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(true);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);

        return button;
    }

    private void updateSelectedNavigation(JButton button) {
        if (selectedNavButton != null) {
            selectedNavButton.setBackground(WHITE);
            selectedNavButton.setForeground(MUTED);
        }

        selectedNavButton = button;
        selectedNavButton.setBackground(LIGHT_BLUE);
        selectedNavButton.setForeground(PRIMARY);
    }

    // =====================================================
    // MAIN AREA
    // =====================================================

    private JPanel createMainArea() {
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(BACKGROUND);

        main.add(createTopBar(), BorderLayout.NORTH);
        main.add(contentPanel, BorderLayout.CENTER);

        return main;
    }

    // =====================================================
    // TOP BAR
    // =====================================================

    private JPanel createTopBar() {
        JPanel topBar = new JPanel(new GridBagLayout());
        topBar.setBackground(WHITE);
        topBar.setPreferredSize(new Dimension(0, 88));
        topBar.setBorder(new EmptyBorder(12, 40, 12, 40));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;

        // Search field
        JTextField searchField = new JTextField(
                "⌕   Search courses, exams, and more"
        );
        searchField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        searchField.setForeground(MUTED);
        searchField.setBackground(BACKGROUND);
        searchField.setBorder(new EmptyBorder(14, 16, 14, 16));
        searchField.setPreferredSize(new Dimension(390, 44));

        gbc.gridx = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        topBar.add(searchField, gbc);

        // Date
        String date = LocalDate.now().format(
                DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy")
        );

        JLabel dateLabel = new JLabel(date);
        dateLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        dateLabel.setForeground(MUTED);

        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(0, 30, 0, 30);
        topBar.add(dateLabel, gbc);

        // Notification
        JButton notificationButton = new JButton("♧");
        notificationButton.setFont(new Font("SansSerif", Font.PLAIN, 23));
        notificationButton.setForeground(MUTED);
        notificationButton.setBorderPainted(false);
        notificationButton.setContentAreaFilled(false);
        notificationButton.setFocusPainted(false);
        notificationButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        notificationButton.addActionListener(e ->
                JOptionPane.showMessageDialog(
                        this,
                        "No new notifications.",
                        "Notifications",
                        JOptionPane.INFORMATION_MESSAGE
                )
        );

        gbc.gridx = 2;
        gbc.insets = new Insets(0, 10, 0, 25);
        topBar.add(notificationButton, gbc);

        // Student information
        JPanel profile = new JPanel();
        profile.setOpaque(false);
        profile.setLayout(new BoxLayout(profile, BoxLayout.Y_AXIS));

        JLabel studentName = new JLabel("Alex Morgan");
        studentName.setFont(new Font("SansSerif", Font.BOLD, 14));
        studentName.setForeground(DARK);

        JLabel studentInfo = new JLabel("Student · CS2024  ⌄");
        studentInfo.setFont(new Font("SansSerif", Font.PLAIN, 11));
        studentInfo.setForeground(MUTED);

        profile.add(studentName);
        profile.add(Box.createVerticalStrut(5));
        profile.add(studentInfo);

        gbc.gridx = 3;
        gbc.insets = new Insets(0, 0, 0, 0);
        topBar.add(profile, gbc);

        return topBar;
    }

    // =====================================================
    // OVERVIEW PAGE
    // =====================================================

    private JPanel createOverviewPanel() {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(BACKGROUND);

        JPanel content = new JPanel();
        content.setBackground(BACKGROUND);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(32, 40, 40, 40));

        // Page heading
        JLabel heading = new JLabel("Overview");
        heading.setFont(new Font("SansSerif", Font.BOLD, 30));
        heading.setForeground(DARK);

        JLabel subtitle = new JLabel(
                "A little preparation. A lot of possibility."
        );
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setForeground(MUTED);

        content.add(heading);
        content.add(Box.createVerticalStrut(6));
        content.add(subtitle);
        content.add(Box.createVerticalStrut(26));

        // Welcome banner
        content.add(createWelcomeBanner());
        content.add(Box.createVerticalStrut(20));

        // Statistics
        content.add(createStatisticsPanel());
        content.add(Box.createVerticalStrut(26));

        // Upcoming exams + latest result
        content.add(createSectionHeader(
                "Upcoming exams",
                "View all exams →",
                "My exams"
        ));
        content.add(Box.createVerticalStrut(16));
        content.add(createUpcomingSection());
        content.add(Box.createVerticalStrut(30));

        // My courses
        content.add(createSectionHeader(
                "My courses",
                "Browse courses →",
                "Courses"
        ));
        content.add(Box.createVerticalStrut(16));
        content.add(createMyCoursesPanel());

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(BACKGROUND);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );

        page.add(scrollPane, BorderLayout.CENTER);

        return page;
    }

    // =====================================================
    // WELCOME BANNER
    // =====================================================

    private JPanel createWelcomeBanner() {
        RoundedPanel banner = new RoundedPanel(16, LIGHT_BLUE);
        banner.setLayout(new BorderLayout(20, 0));
        banner.setBorder(new EmptyBorder(24, 26, 24, 26));
        banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        JLabel tag = createTag("YOUR LEARNING, AT A GLANCE");

        JLabel message = new JLabel(
                "Ready for your next challenge, Alex?"
        );
        message.setFont(new Font("SansSerif", Font.BOLD, 27));
        message.setForeground(DARK);

        JLabel description = new JLabel(
                "You have 2 exams this week. Let's make them count."
        );
        description.setFont(new Font("SansSerif", Font.PLAIN, 14));
        description.setForeground(MUTED);

        left.add(tag);
        left.add(Box.createVerticalStrut(14));
        left.add(message);
        left.add(Box.createVerticalStrut(10));
        left.add(description);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));

        JLabel date = new JLabel(
                LocalDate.now().format(
                        DateTimeFormatter.ofPattern("EEEE, MMM dd")
                ).toUpperCase()
        );
        date.setFont(new Font("SansSerif", Font.BOLD, 11));
        date.setForeground(PRIMARY);
        date.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JButton viewButton = createPrimaryButton("View my exams  →");
        viewButton.setAlignmentX(Component.RIGHT_ALIGNMENT);
        viewButton.addActionListener(e ->
                cardLayout.show(contentPanel, "My exams")
        );

        right.add(date);
        right.add(Box.createVerticalStrut(38));
        right.add(viewButton);

        banner.add(left, BorderLayout.CENTER);
        banner.add(right, BorderLayout.EAST);

        return banner;
    }

    // =====================================================
    // STATISTICS
    // =====================================================

    private JPanel createStatisticsPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 3, 20, 0));
        panel.setOpaque(false);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 112));

        panel.add(createStatisticCard(
                "Enrolled courses",
                "04",
                "All courses active",
                MUTED
        ));

        panel.add(createStatisticCard(
                "Upcoming exams",
                "02",
                "Next: today at 2:00 PM",
                MUTED
        ));

        panel.add(createStatisticCard(
                "Average score",
                "85%",
                "Across 6 attempts",
                new Color(20, 145, 100)
        ));

        return panel;
    }

    private JPanel createStatisticCard(
            String title,
            String value,
            String description,
            Color descriptionColor
    ) {
        RoundedPanel card = new RoundedPanel(16, WHITE);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(18, 20, 18, 20));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        titleLabel.setForeground(MUTED);

        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(new Font("SansSerif", Font.BOLD, 30));
        valueLabel.setForeground(DARK);

        JLabel descriptionLabel = new JLabel(description);
        descriptionLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        descriptionLabel.setForeground(descriptionColor);

        JPanel bottom = new JPanel(new BorderLayout(10, 0));
        bottom.setOpaque(false);
        bottom.add(valueLabel, BorderLayout.WEST);
        bottom.add(descriptionLabel, BorderLayout.CENTER);

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(bottom, BorderLayout.CENTER);

        return card;
    }

    // =====================================================
    // UPCOMING EXAMS
    // =====================================================

    private JPanel createUpcomingSection() {
        JPanel section = new JPanel(new GridBagLayout());
        section.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1;

        // Exam list
        JPanel examList = new JPanel();
        examList.setOpaque(false);
        examList.setLayout(new BoxLayout(examList, BoxLayout.Y_AXIS));

        examList.add(createExamRow(
                "TODAY", "17",
                "Database fundamentals",
                "Midterm examination",
                "2:00 PM  ·  45 min  ·  20 questions",
                "Start exam",
                true
        ));

        examList.add(Box.createVerticalStrut(16));

        examList.add(createExamRow(
                "THU", "19",
                "Object-oriented programming",
                "Unit 3 assessment",
                "10:00 AM  ·  60 min  ·  30 questions",
                "View details",
                false
        ));

        gbc.gridx = 0;
        gbc.weightx = 2.1;
        gbc.insets = new Insets(0, 0, 0, 24);
        section.add(examList, gbc);

        // Latest result
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.insets = new Insets(0, 0, 0, 0);
        section.add(createLatestResultCard(), gbc);

        return section;
    }

    private JPanel createExamRow(
            String day,
            String date,
            String title,
            String examType,
            String details,
            String buttonText,
            boolean primary
    ) {
        RoundedPanel row = new RoundedPanel(16, WHITE);
        row.setLayout(new BorderLayout(18, 0));
        row.setBorder(new EmptyBorder(15, 16, 15, 16));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 95));
        row.setPreferredSize(new Dimension(500, 95));

        // Date box
        RoundedPanel dateCard = new RoundedPanel(12, LIGHT_BLUE);
        dateCard.setPreferredSize(new Dimension(62, 64));
        dateCard.setLayout(new BoxLayout(dateCard, BoxLayout.Y_AXIS));
        dateCard.setBorder(new EmptyBorder(9, 5, 7, 5));

        JLabel dayLabel = new JLabel(day);
        dayLabel.setFont(new Font("SansSerif", Font.BOLD, 9));
        dayLabel.setForeground(PRIMARY);
        dayLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel dateLabel = new JLabel(date);
        dateLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
        dateLabel.setForeground(PRIMARY);
        dateLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        dateCard.add(dayLabel);
        dateCard.add(Box.createVerticalStrut(3));
        dateCard.add(dateLabel);

        row.add(dateCard, BorderLayout.WEST);

        // Exam information
        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));

        JLabel examTitle = new JLabel(title);
        examTitle.setFont(new Font("SansSerif", Font.BOLD, 14));
        examTitle.setForeground(DARK);

        JLabel typeLabel = new JLabel(examType);
        typeLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        typeLabel.setForeground(MUTED);

        JLabel detailsLabel = new JLabel(details);
        detailsLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        detailsLabel.setForeground(MUTED);

        info.add(examTitle);
        info.add(Box.createVerticalStrut(7));
        info.add(typeLabel);
        info.add(Box.createVerticalStrut(6));
        info.add(detailsLabel);

        row.add(info, BorderLayout.CENTER);

        // Action button
        JButton action = primary
                ? createPrimaryButton(buttonText)
                : createSecondaryButton(buttonText);

        action.setPreferredSize(new Dimension(136, 42));

        action.addActionListener(e ->
                cardLayout.show(contentPanel, "My exams"));

        row.add(action, BorderLayout.EAST);

        return row;
    }

    // =====================================================
    // LATEST RESULT
    // =====================================================

    private JPanel createLatestResultCard() {
        RoundedPanel card = new RoundedPanel(16, WHITE);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(20, 22, 20, 22));

        JLabel tag = createTag("LATEST RESULT");

        JLabel score = new JLabel(
                "<html><span style='font-size:48pt; color:#1E73EB;'><b>85</b></span>" +
                        "<span style='font-size:13pt; color:#67809B;'> / 100</span></html>"
        );

        JLabel examName = new JLabel(
                "Database fundamentals · Practice quiz"
        );
        examName.setFont(new Font("SansSerif", Font.PLAIN, 12));
        examName.setForeground(DARK);

        JLabel feedback = new JLabel(
                "Well done! Keep up the momentum."
        );
        feedback.setFont(new Font("SansSerif", Font.PLAIN, 12));
        feedback.setForeground(MUTED);

        card.add(tag);
        card.add(Box.createVerticalStrut(12));
        card.add(score);
        card.add(Box.createVerticalStrut(5));
        card.add(examName);
        card.add(Box.createVerticalStrut(16));
        card.add(feedback);

        return card;
    }

    // =====================================================
    // MY COURSES
    // =====================================================

    private JPanel createMyCoursesPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 3, 20, 0));
        panel.setOpaque(false);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));

        panel.add(createCourseCard(
                "CS201",
                "Database fundamentals",
                "Dr. Sarah Chen"
        ));

        panel.add(createCourseCard(
                "CS202",
                "Object-oriented programming",
                "Prof. James Wilson"
        ));

        panel.add(createCourseCard(
                "CS203",
                "Data structures",
                "Dr. Maya Patel"
        ));

        return panel;
    }

    private JPanel createCourseCard(
            String courseCode,
            String courseName,
            String instructor
    ) {
        RoundedPanel card = new RoundedPanel(16, WHITE);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(16, 18, 16, 18));

        JLabel code = createTag(courseCode);

        JLabel name = new JLabel(courseName);
        name.setFont(new Font("SansSerif", Font.BOLD, 14));
        name.setForeground(DARK);

        JLabel teacher = new JLabel(instructor);
        teacher.setFont(new Font("SansSerif", Font.PLAIN, 11));
        teacher.setForeground(MUTED);

        card.add(code);
        card.add(Box.createVerticalStrut(14));
        card.add(name);
        card.add(Box.createVerticalStrut(8));
        card.add(teacher);

        return card;
    }

    // =====================================================
    // SECTION HEADER
    // =====================================================

    private JPanel createSectionHeader(
            String title,
            String actionText,
            String destination
    ) {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        titleLabel.setForeground(DARK);

        JButton actionButton = createTextButton(actionText);
        actionButton.addActionListener(e ->
                cardLayout.show(contentPanel, destination)
        );

        header.add(titleLabel, BorderLayout.WEST);
        header.add(actionButton, BorderLayout.EAST);

        return header;
    }

    // =====================================================
    // PLACEHOLDER PAGES
    // =====================================================

    private JPanel createPlaceholderPanel(String pageName) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(BACKGROUND);

        JLabel label = new JLabel(
                pageName + " page will be implemented later."
        );
        label.setFont(new Font("SansSerif", Font.BOLD, 22));
        label.setForeground(DARK);

        panel.add(label);

        return panel;
    }

    // =====================================================
    // REUSABLE COMPONENTS
    // =====================================================

    private JLabel createTag(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 10));
        label.setForeground(PRIMARY);
        label.setOpaque(true);
        label.setBackground(new Color(235, 245, 255));
        label.setBorder(new EmptyBorder(6, 11, 6, 11));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        return label;
    }

    private JButton createPrimaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setForeground(WHITE);
        button.setBackground(PRIMARY);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        return button;
    }

    private JButton createSecondaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setForeground(PRIMARY);
        button.setBackground(LIGHT_BLUE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        return button;
    }

    private JButton createTextButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 11));
        button.setForeground(PRIMARY);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        return button;
    }

    // =====================================================
    // LOGOUT
    // =====================================================

    private void logout() {
        int choice = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to log out?",
                "Log out",
                JOptionPane.YES_NO_OPTION
        );

        if (choice == JOptionPane.YES_OPTION) {
            dispose();

            // TODO: Return to LoginFrame after authentication is connected.
            new LoginFrame().setVisible(true);
        }
    }

    // =====================================================
    // ROUNDED PANEL
    // =====================================================

    private static class RoundedPanel extends JPanel {

        private final int radius;
        private final Color backgroundColor;

        public RoundedPanel(int radius, Color backgroundColor) {
            this.radius = radius;
            this.backgroundColor = backgroundColor;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();

            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            g2.setColor(backgroundColor);
            g2.fillRoundRect(
                    0, 0,
                    getWidth(), getHeight(),
                    radius, radius
            );

            g2.dispose();
            super.paintComponent(g);
        }
    }

    // =====================================================
    // MAIN
    // =====================================================

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new StudentDashBoard().setVisible(true);
        });
    }
}


