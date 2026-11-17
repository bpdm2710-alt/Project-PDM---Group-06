package org.example.ui.panel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CoursesPanel extends JPanel {

    private static final Color BG = new Color(241, 246, 253);
    private static final Color WHITE = Color.WHITE;
    private static final Color PRIMARY = new Color(37, 99, 235);
    private static final Color PRIMARY_LIGHT = new Color(229, 240, 255);
    private static final Color TEXT = new Color(30, 52, 85);
    private static final Color MUTED = new Color(100, 121, 153);
    private static final Color GREEN = new Color(0, 130, 80);
    private static final Color GREEN_BG = new Color(230, 246, 239);

    private static class Course {
        String code, name, instructor, subject, semester;
        int students, upcomingExams;
        boolean enrolled;

        Course(String code, String name, String instructor, int students,
               String subject, String semester, boolean enrolled, int upcomingExams) {
            this.code = code;
            this.name = name;
            this.instructor = instructor;
            this.students = students;
            this.subject = subject;
            this.semester = semester;
            this.enrolled = enrolled;
            this.upcomingExams = upcomingExams;
        }
    }

    private final List<Course> courses = new ArrayList<>();
    private JTextField searchField;
    private JComboBox<String> subjectFilter;
    private JComboBox<String> semesterFilter;
    private JButton myCoursesTab;
    private JButton exploreTab;
    private JLabel sectionTitle;
    private JPanel courseGrid;
    private JPanel discoverSection;
    private boolean showingEnrolled = true;

    public CoursesPanel() {
        setLayout(new BorderLayout());
        setBackground(BG);

        initializeData();
        buildUI();
        refreshCourses();
    }

    private void initializeData() {
        courses.add(new Course("CS201", "Database fundamentals",
                "Dr. Sarah Chen", 48, "Computer Science",
                "This semester", true, 1));

        courses.add(new Course("CS202", "Object-oriented programming",
                "Prof. James Wilson", 42, "Computer Science",
                "This semester", true, 1));

        courses.add(new Course("CS203", "Data structures",
                "Dr. Maya Patel", 56, "Computer Science",
                "This semester", true, 0));

        courses.add(new Course("CS204", "Computer networks",
                "Prof. David Kim", 38, "Computer Science",
                "This semester", true, 0));

        courses.add(new Course("CS205", "Software engineering",
                "Dr. Olivia Reed", 0, "Software Engineering",
                "This semester", false, 0));
    }

    private void buildUI() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(BG);
        content.setBorder(new EmptyBorder(32, 30, 30, 30));

        content.add(createHeader());
        content.add(Box.createVerticalStrut(24));
        content.add(createTabs());
        content.add(Box.createVerticalStrut(22));
        content.add(createFilters());
        content.add(Box.createVerticalStrut(28));

        sectionTitle = new JLabel("Enrolled courses");
        styleSectionTitle(sectionTitle);
        content.add(sectionTitle);
        content.add(Box.createVerticalStrut(18));

        courseGrid = new JPanel(new GridLayout(0, 2, 22, 20));
        courseGrid.setBackground(BG);
        courseGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        courseGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        content.add(courseGrid);

        content.add(Box.createVerticalStrut(40));

        discoverSection = createDiscoverSection();
        content.add(discoverSection);

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.setBackground(BG);
        scrollPane.getViewport().setBackground(BG);
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        scrollPane.setHorizontalScrollBarPolicy(
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createHeader() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));

        JLabel title = new JLabel("Courses");
        title.setFont(new Font("SansSerif", Font.BOLD, 29));
        title.setForeground(TEXT);

        JLabel subtitle = new JLabel(
                "Explore subjects and keep your learning on track.");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 14));
        subtitle.setForeground(MUTED);

        panel.add(title);
        panel.add(Box.createVerticalStrut(5));
        panel.add(subtitle);
        return panel;
    }

    private JPanel createTabs() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 7));
        panel.setBackground(WHITE);
        panel.setBorder(new EmptyBorder(0, 5, 0, 5));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        panel.setPreferredSize(new Dimension(900, 58));

        myCoursesTab = createTabButton("My courses (4)", true);
        exploreTab = createTabButton("Explore courses", false);

        myCoursesTab.addActionListener(e -> {
            showingEnrolled = true;
            updateTabs();
            refreshCourses();
        });
        exploreTab.addActionListener(e -> {
            showingEnrolled = false;
            updateTabs();
            refreshCourses();
        });

        panel.add(myCoursesTab);
        panel.add(exploreTab);
        return panel;
    }

    private JButton createTabButton(String text, boolean active) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 13));
        button.setForeground(active ? WHITE : MUTED);
        button.setBackground(active ? PRIMARY : WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(157, 42));
        return button;
    }

    private void updateTabs() {
        myCoursesTab.setBackground(showingEnrolled ? PRIMARY : WHITE);
        myCoursesTab.setForeground(showingEnrolled ? WHITE : MUTED);
        exploreTab.setBackground(showingEnrolled ? WHITE : PRIMARY);
        exploreTab.setForeground(showingEnrolled ? MUTED : WHITE);
    }

    private JPanel createFilters() {
        JPanel panel = new JPanel(new GridLayout(1, 3, 20, 0));
        panel.setBackground(BG);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        panel.setPreferredSize(new Dimension(900, 45));

        searchField = new JTextField();
        searchField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        searchField.setForeground(TEXT);
        searchField.setBackground(WHITE);
        searchField.setBorder(new EmptyBorder(0, 14, 0, 14));
        searchField.setToolTipText("Search by course name or code");

        subjectFilter = new JComboBox<>(new String[]{
                "All subjects", "Computer Science", "Software Engineering"
        });
        semesterFilter = new JComboBox<>(new String[]{
                "This semester", "All semesters"
        });

        styleComboBox(subjectFilter);
        styleComboBox(semesterFilter);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refreshCourses(); }
            public void removeUpdate(DocumentEvent e) { refreshCourses(); }
            public void changedUpdate(DocumentEvent e) { refreshCourses(); }
        });
        subjectFilter.addActionListener(e -> refreshCourses());
        semesterFilter.addActionListener(e -> refreshCourses());

        panel.add(searchField);
        panel.add(subjectFilter);
        panel.add(semesterFilter);
        return panel;
    }

    private void styleComboBox(JComboBox<String> combo) {
        combo.setFont(new Font("SansSerif", Font.PLAIN, 13));
        combo.setForeground(MUTED);
        combo.setBackground(WHITE);
        combo.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        combo.setFocusable(false);
    }

    private void styleSectionTitle(JLabel label) {
        label.setFont(new Font("SansSerif", Font.BOLD, 20));
        label.setForeground(TEXT);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private void refreshCourses() {
        if (courseGrid == null) return;

        courseGrid.removeAll();

        String keyword = searchField.getText().trim().toLowerCase();
        String subject = (String) subjectFilter.getSelectedItem();
        String semester = (String) semesterFilter.getSelectedItem();

        List<Course> filtered = new ArrayList<>();
        for (Course course : courses) {
            if (course.enrolled != showingEnrolled) continue;

            boolean matchesSearch =
                    course.name.toLowerCase().contains(keyword)
                            || course.code.toLowerCase().contains(keyword);
            boolean matchesSubject =
                    "All subjects".equals(subject)
                            || course.subject.equals(subject);
            boolean matchesSemester =
                    "All semesters".equals(semester)
                            || course.semester.equals(semester);

            if (matchesSearch && matchesSubject && matchesSemester) {
                filtered.add(course);
            }
        }

        sectionTitle.setText(showingEnrolled
                ? "Enrolled courses" : "Explore courses");
        discoverSection.setVisible(showingEnrolled);

        for (Course course : filtered) {
            courseGrid.add(createCourseCard(course));
        }

        if (filtered.isEmpty()) {
            JLabel empty = new JLabel("No courses found.");
            empty.setFont(new Font("SansSerif", Font.PLAIN, 14));
            empty.setForeground(MUTED);
            courseGrid.add(empty);
        }

        revalidate();
        repaint();
    }

    private JPanel createCourseCard(Course course) {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setBackground(WHITE);
        card.setBorder(new EmptyBorder(18, 20, 18, 20));
        card.setPreferredSize(new Dimension(400, 168));

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(WHITE);

        JLabel code = createTag(course.code);
        JLabel status = new JLabel(course.enrolled ? "Enrolled" : "Available");
        status.setFont(new Font("SansSerif", Font.BOLD, 11));
        status.setForeground(GREEN);
        status.setOpaque(true);
        status.setBackground(GREEN_BG);
        status.setBorder(new EmptyBorder(6, 12, 6, 12));

        top.add(code, BorderLayout.WEST);
        top.add(status, BorderLayout.EAST);

        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setBackground(WHITE);

        JLabel name = new JLabel(course.name);
        name.setFont(new Font("SansSerif", Font.BOLD, 19));
        name.setForeground(TEXT);

        JLabel instructor = new JLabel(
                course.instructor + "  ·  " + course.students + " students");
        instructor.setFont(new Font("SansSerif", Font.PLAIN, 12));
        instructor.setForeground(MUTED);

        info.add(name);
        info.add(Box.createVerticalStrut(7));
        info.add(instructor);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(WHITE);

        if (course.enrolled) {
            JLabel exams = new JLabel(course.upcomingExams == 0
                    ? "No upcoming exams"
                    : course.upcomingExams + " upcoming exam");
            exams.setFont(new Font("SansSerif", Font.PLAIN, 12));
            exams.setForeground(PRIMARY);

            JButton viewExams = new JButton("View exams →");
            styleTextButton(viewExams);
            viewExams.addActionListener(e ->
                    JOptionPane.showMessageDialog(this,
                            "Exam list will be connected later.",
                            "View exams", JOptionPane.INFORMATION_MESSAGE));

            bottom.add(exams, BorderLayout.WEST);
            bottom.add(viewExams, BorderLayout.EAST);
        } else {
            JButton enroll = new JButton("+ Enroll");
            stylePrimaryButton(enroll);
            enroll.addActionListener(e -> {
                course.enrolled = true;
                refreshCourses();
            });
            bottom.add(enroll, BorderLayout.EAST);
        }

        card.add(top, BorderLayout.NORTH);
        card.add(info, BorderLayout.CENTER);
        card.add(bottom, BorderLayout.SOUTH);
        return card;
    }

    private JPanel createDiscoverSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBackground(BG);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel title = new JLabel("Discover something new");
        styleSectionTitle(title);
        section.add(title);
        section.add(Box.createVerticalStrut(18));

        for (Course course : courses) {
            if (!course.enrolled) {
                section.add(createDiscoverCard(course));
                section.add(Box.createVerticalStrut(15));
            }
        }
        return section;
    }

    private JPanel createDiscoverCard(Course course) {
        JPanel card = new JPanel(new BorderLayout(25, 0));
        card.setBackground(WHITE);
        card.setBorder(new EmptyBorder(18, 20, 18, 20));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 106));
        card.setPreferredSize(new Dimension(900, 106));

        JLabel code = createTag(course.code);

        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setBackground(WHITE);

        JLabel name = new JLabel(course.name);
        name.setFont(new Font("SansSerif", Font.BOLD, 17));
        name.setForeground(TEXT);

        JLabel instructor = new JLabel(
                course.instructor + " · Software design, testing, and development methods");
        instructor.setFont(new Font("SansSerif", Font.PLAIN, 13));
        instructor.setForeground(MUTED);

        info.add(name);
        info.add(Box.createVerticalStrut(10));
        info.add(instructor);

        JButton enroll = new JButton("+ Enroll");
        stylePrimaryButton(enroll);
        enroll.addActionListener(e -> {
            course.enrolled = true;
            refreshCourses();
        });

        card.add(code, BorderLayout.WEST);
        card.add(info, BorderLayout.CENTER);
        card.add(enroll, BorderLayout.EAST);
        return card;
    }

    private JLabel createTag(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 11));
        label.setForeground(PRIMARY);
        label.setOpaque(true);
        label.setBackground(PRIMARY_LIGHT);
        label.setBorder(new EmptyBorder(6, 11, 6, 11));
        return label;
    }

    private void stylePrimaryButton(JButton button) {
        button.setBackground(PRIMARY);
        button.setForeground(WHITE);
        button.setFont(new Font("SansSerif", Font.BOLD, 13));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(140, 42));
    }

    private void styleTextButton(JButton button) {
        button.setForeground(PRIMARY);
        button.setBackground(WHITE);
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }
}

