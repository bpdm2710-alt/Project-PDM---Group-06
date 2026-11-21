package org.example.ui.panel;

import org.example.Service.InstructorService.TeachingCourse;
import org.example.Service.Services;
import org.example.session.Session;
import org.example.ui.components.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

import static org.example.ui.components.UITheme.*;

/** Instructor "My courses": the courses they teach; opening one shows its workspace. */
public class InstructorCoursesPage extends JPanel implements Refreshable {

    private static final String LIST = "list";
    private static final String COURSE = "course";

    private final CardLayout cards = new CardLayout();
    private final ScrollableBody body = new ScrollableBody();
    private final StateView state = new StateView(body);
    private final JPanel workspaceHolder = new JPanel(new BorderLayout());

    public InstructorCoursesPage() {
        setLayout(cards);
        setBackground(BACKGROUND);
        body.setBorder(new EmptyBorder(32, 40, 40, 40));
        ScrollableBody holder = new ScrollableBody();
        holder.setLayout(new BorderLayout());
        holder.add(state, BorderLayout.CENTER);
        add(ScrollableBody.scroll(holder, BACKGROUND), LIST);
        workspaceHolder.setBackground(BACKGROUND);
        add(workspaceHolder, COURSE);
        cards.show(this, LIST);
    }

    @Override
    public void refresh() {
        int id = Session.user().userId();
        state.load(() -> Services.instructor().listTeachingCourses(id), this::render,
                List::isEmpty, "No courses assigned",
                "You are not assigned to teach any course yet.");
    }

    private void render(List<TeachingCourse> courses) {
        body.removeAll();
        JLabel title = label("My courses", bold(30), DARK);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel sub = label(Fmt.plural(courses.size(), "course") + " you teach", plain(13), MUTED);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(title);
        body.add(Box.createVerticalStrut(6));
        body.add(sub);
        body.add(Box.createVerticalStrut(24));

        JPanel grid = new JPanel(new GridLayout(0, 2, 20, 20));
        grid.setOpaque(false);
        for (TeachingCourse c : courses) {
            grid.add(card(c));
        }
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrap.add(grid, BorderLayout.NORTH);
        body.add(wrap);
        body.revalidate();
        body.repaint();
    }

    private JComponent card(TeachingCourse c) {
        RoundedPanel card = new RoundedPanel(16, WHITE, BORDER);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(20, 22, 20, 22));
        card.add(left(tag(c.credits() + (c.credits() == 1 ? " CREDIT" : " CREDITS"))));
        card.add(Box.createVerticalStrut(12));
        card.add(left(label(c.name(), bold(17), DARK)));
        card.add(Box.createVerticalStrut(6));
        card.add(left(label((c.semester() == null ? "" : c.semester() + "  ·  ")
                + Fmt.plural(c.studentCount(), "student") + "  ·  "
                + Fmt.plural(c.examCount(), "exam"), plain(12), MUTED)));
        card.add(Box.createVerticalStrut(16));
        JButton open = primaryButton("Open course");
        open.setAlignmentX(Component.LEFT_ALIGNMENT);
        open.setMaximumSize(new Dimension(150, 40));
        open.addActionListener(e -> openCourse(c));
        card.add(open);
        return card;
    }

    private void openCourse(TeachingCourse c) {
        workspaceHolder.removeAll();
        workspaceHolder.add(new CourseWorkspace(c, () -> {
            cards.show(this, LIST);
            refresh();
        }), BorderLayout.CENTER);
        workspaceHolder.revalidate();
        workspaceHolder.repaint();
        cards.show(this, COURSE);
    }

    private static <T extends JComponent> T left(T c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    /** One course: Exams, Question bank, Grading, Statistics and Reports tabs. */
    static final class CourseWorkspace extends JPanel {

        CourseWorkspace(TeachingCourse course, Runnable onBack) {
            setLayout(new BorderLayout());
            setBackground(BACKGROUND);

            JPanel head = new JPanel(new BorderLayout());
            head.setOpaque(false);
            head.setBorder(new EmptyBorder(28, 40, 12, 40));
            JButton back = textButton("← My courses");
            back.addActionListener(e -> onBack.run());
            JPanel titles = new JPanel();
            titles.setOpaque(false);
            titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
            back.setAlignmentX(Component.LEFT_ALIGNMENT);
            titles.add(back);
            titles.add(Box.createVerticalStrut(6));
            JLabel t = label(course.name(), bold(28), DARK);
            t.setAlignmentX(Component.LEFT_ALIGNMENT);
            titles.add(t);
            head.add(titles, BorderLayout.WEST);
            add(head, BorderLayout.NORTH);

            JTabbedPane tabs = new JTabbedPane();
            tabs.setFont(bold(13));
            tabs.setBackground(BACKGROUND);
            tabs.setBorder(new EmptyBorder(0, 28, 0, 28));
            InstructorExamsTab exams = new InstructorExamsTab(course.courseId());
            QuestionBankTab bank = new QuestionBankTab(course.courseId());
            StatisticsTab stats = new StatisticsTab(course.courseId());
            GradingTab grading = new GradingTab(course.courseId());
            ReportsTab reports = new ReportsTab(course.courseId());
            tabs.addTab("Exams", exams);
            tabs.addTab("Question bank", bank);
            tabs.addTab("Grading", grading);
            tabs.addTab("Statistics", stats);
            tabs.addTab("Reports", reports);
            tabs.addChangeListener(e -> {
                Component c = tabs.getSelectedComponent();
                if (c instanceof Refreshable r) {
                    r.refresh();
                }
            });
            add(tabs, BorderLayout.CENTER);
            exams.refresh();
        }
    }
}
