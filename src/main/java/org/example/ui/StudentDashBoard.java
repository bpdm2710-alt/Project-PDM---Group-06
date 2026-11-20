package org.example.ui;

import org.example.Service.AuthService.UserAccount;
import org.example.Service.Services;
import org.example.Service.StudentService;
import org.example.session.Session;
import org.example.ui.components.AsyncTask;
import org.example.ui.components.Dialogs;
import org.example.ui.components.Fmt;
import org.example.ui.panel.ExamSessionPanel;
import org.example.ui.components.Refreshable;
import org.example.ui.panel.MyExamsPanel;
import org.example.ui.panel.ResultsPanel;
import org.example.ui.panel.StudentCoursesPanel;
import org.example.ui.panel.StudentOverviewPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.example.ui.components.UITheme.*;

/**
 * Student shell: sidebar, top bar and the page area. The shell holds no data
 * of its own; the user comes from {@link Session} and every page loads through
 * StudentService.
 */
public class StudentDashBoard extends JFrame implements Navigator {

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<String, JButton> navButtons = new LinkedHashMap<>();
    private final Map<String, Component> pages = new LinkedHashMap<>();
    private JButton selected;

    public StudentDashBoard() {
        UserAccount user = Session.user();

        setTitle("Examly - Student Workspace");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1440, 900);
        setMinimumSize(new Dimension(1180, 720));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        content.setBackground(BACKGROUND);
        addPage(OVERVIEW, new StudentOverviewPanel(this));
        addPage(COURSES, new StudentCoursesPanel(this));
        addPage(MY_EXAMS, new MyExamsPanel(this));
        addPage(RESULTS, new ResultsPanel());

        add(createSidebar(), BorderLayout.WEST);
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(BACKGROUND);
        main.add(createTopBar(user), BorderLayout.NORTH);
        main.add(content, BorderLayout.CENTER);
        add(main, BorderLayout.CENTER);

        show(OVERVIEW);
    }

    private void addPage(String name, Component page) {
        pages.put(name, page);
        content.add(page, name);
    }

    // ---- Navigator --------------------------------------------------------

    @Override
    public void show(String page) {
        cards.show(content, page);
        JButton button = navButtons.get(page);
        if (button != null) {
            select(button);
        }
        Component c = pages.get(page);
        if (c instanceof Refreshable r) {
            r.refresh();
        }
    }

    private static final String EXAM = "Exam";
    private ExamSessionPanel examPanel;

    @Override
    public void openExam(int regisId) {
        AsyncTask.run(() -> Services.student().startAttempt(regisId),
                this::showExam,
                err -> Dialogs.error(this, err));
    }

    private void showExam(StudentService.AttemptSession attempt) {
        closeExam();
        examPanel = new ExamSessionPanel(attempt, this::onExamSubmitted);
        content.add(examPanel, EXAM);
        pages.put(EXAM, examPanel);
        cards.show(content, EXAM);
    }

    private void onExamSubmitted(StudentService.AttemptResult r) {
        closeExam();
        String msg = r.fullyGraded()
                ? "Score: " + Fmt.marks(r.score()) + " / " + Fmt.marks(r.maxScore())
                + " (" + Fmt.percent(r.percent()) + ")\n" + (r.passed() ? "You passed." : "Not passed this time.")
                : "Your exam was submitted. Score so far: " + Fmt.marks(r.score()) + " / "
                + Fmt.marks(r.maxScore()) + "\nSome answers are still being graded.";
        JOptionPane.showMessageDialog(this, msg, "Exam submitted", JOptionPane.INFORMATION_MESSAGE);
        show(RESULTS);
    }

    private void closeExam() {
        if (examPanel != null) {
            examPanel.dispose();
            content.remove(examPanel);
            pages.remove(EXAM);
            examPanel = null;
        }
    }

    // ---- Sidebar ----------------------------------------------------------

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(WHITE);
        sidebar.setPreferredSize(new Dimension(240, 0));
        sidebar.setBorder(new EmptyBorder(30, 16, 24, 16));

        JPanel logoPanel = new JPanel(new BorderLayout());
        logoPanel.setBackground(WHITE);
        logoPanel.setBorder(new EmptyBorder(0, 14, 30, 0));
        logoPanel.add(label("examly.", bold(32), PRIMARY), BorderLayout.CENTER);
        sidebar.add(logoPanel, BorderLayout.NORTH);

        JPanel nav = new JPanel();
        nav.setBackground(WHITE);
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        JLabel workspace = label("STUDENT WORKSPACE", bold(10), MUTED);
        workspace.setBorder(new EmptyBorder(0, 14, 28, 0));
        workspace.setAlignmentX(Component.LEFT_ALIGNMENT);
        nav.add(workspace);

        addNav(nav, "▦", OVERVIEW);
        addNav(nav, "♧", COURSES);
        addNav(nav, "▤", MY_EXAMS);
        addNav(nav, "▥", RESULTS);
        sidebar.add(nav, BorderLayout.CENTER);

        JButton logout = navButton("⇥", "Log out");
        logout.addActionListener(e -> logout());
        sidebar.add(logout, BorderLayout.SOUTH);
        return sidebar;
    }

    private void addNav(JPanel nav, String icon, String page) {
        JButton b = navButton(icon, page);
        b.addActionListener(e -> show(page));
        navButtons.put(page, b);
        nav.add(b);
        nav.add(Box.createVerticalStrut(8));
    }

    private JButton navButton(String icon, String text) {
        JButton b = new JButton(icon + "    " + text);
        b.setFont(plain(14));
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setForeground(MUTED);
        b.setBackground(WHITE);
        b.setBorder(new EmptyBorder(13, 16, 13, 12));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(true);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        return b;
    }

    private void select(JButton button) {
        if (selected != null) {
            selected.setBackground(WHITE);
            selected.setForeground(MUTED);
        }
        selected = button;
        selected.setBackground(LIGHT_BLUE);
        selected.setForeground(PRIMARY);
    }

    // ---- Top bar ----------------------------------------------------------

    private JPanel createTopBar(UserAccount user) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(WHITE);
        bar.setPreferredSize(new Dimension(0, 88));
        bar.setBorder(new EmptyBorder(12, 40, 12, 40));

        JLabel date = label(LocalDate.now().format(
                DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy", Locale.ENGLISH)), plain(12), MUTED);
        bar.add(date, BorderLayout.WEST);

        JPanel profile = new JPanel();
        profile.setOpaque(false);
        profile.setLayout(new BoxLayout(profile, BoxLayout.Y_AXIS));
        JLabel name = label(user.fullName(), bold(14), DARK);
        JLabel role = label("Student", plain(11), MUTED);
        name.setAlignmentX(Component.RIGHT_ALIGNMENT);
        role.setAlignmentX(Component.RIGHT_ALIGNMENT);
        profile.add(name);
        profile.add(Box.createVerticalStrut(5));
        profile.add(role);
        bar.add(profile, BorderLayout.EAST);
        return bar;
    }

    private JPanel placeholder(String name) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(BACKGROUND);
        p.add(label(name + " is being rebuilt on real data.", bold(18), MUTED));
        return p;
    }

    // ---- Logout -----------------------------------------------------------

    private void logout() {
        int choice = JOptionPane.showConfirmDialog(this, "Are you sure you want to log out?",
                "Log out", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            Session.end();
            dispose();
            new LoginFrame().setVisible(true);
        }
    }
}
