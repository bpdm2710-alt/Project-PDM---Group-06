package org.example.ui.panel;

import org.example.Service.ServiceException;
import org.example.Service.Services;
import org.example.Service.StudentService.*;
import org.example.session.Session;
import org.example.ui.Navigator;
import org.example.ui.components.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

import static org.example.ui.components.UITheme.*;

/**
 * Student "Courses": My courses (exam registered) / Explore (the rest), with
 * keyword, topic and semester filters. "View exams" opens the course's exams,
 * where the student can register.
 */
public class StudentCoursesPanel extends JPanel implements Refreshable {

    private static final String ALL = "All";

    private final Navigator nav;
    private CourseScope scope = CourseScope.MY;
    private final JButton myTab = tabButton("My courses");
    private final JButton exploreTab = tabButton("Explore");
    private final JTextField search = new JTextField();
    private final JComboBox<String> topicBox = new JComboBox<>(new String[]{ALL});
    private final JComboBox<String> semesterBox = new JComboBox<>(new String[]{ALL});
    private final JPanel grid = new JPanel(new GridLayout(0, 2, 20, 20));
    private final StateView state;
    private boolean filtersLoaded;
    private boolean updatingFilters;

    public StudentCoursesPanel(Navigator nav) {
        this.nav = nav;
        setLayout(new BorderLayout());
        setBackground(BACKGROUND);

        ScrollableBody body = new ScrollableBody();
        body.setBorder(new EmptyBorder(32, 40, 40, 40));
        body.add(left(label("Courses", bold(30), DARK)));
        body.add(Box.createVerticalStrut(18));
        body.add(left(tabs()));
        body.add(Box.createVerticalStrut(16));
        body.add(left(filterBar()));
        body.add(Box.createVerticalStrut(20));

        grid.setOpaque(false);
        JPanel gridHolder = new JPanel(new BorderLayout());
        gridHolder.setOpaque(false);
        gridHolder.add(grid, BorderLayout.NORTH);
        state = new StateView(gridHolder);
        state.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(state);

        add(ScrollableBody.scroll(body, BACKGROUND), BorderLayout.CENTER);
        selectTab(CourseScope.MY, false);
    }

    @Override
    public void refresh() {
        if (!filtersLoaded) {
            AsyncTask.run(() -> Services.student().getCourseFilterOptions(), this::fillFilters, e -> { });
        }
        load();
    }

    // ---- filters ----------------------------------------------------------

    private void fillFilters(CourseFilterOptions o) {
        updatingFilters = true;
        for (String t : o.topics()) {
            topicBox.addItem(t);
        }
        for (String s : o.semesters()) {
            semesterBox.addItem(s);
        }
        updatingFilters = false;
        filtersLoaded = true;
    }

    private JComponent tabs() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        p.setOpaque(false);
        myTab.addActionListener(e -> selectTab(CourseScope.MY, true));
        exploreTab.addActionListener(e -> selectTab(CourseScope.EXPLORE, true));
        p.add(myTab);
        p.add(exploreTab);
        return p;
    }

    private void selectTab(CourseScope s, boolean reload) {
        scope = s;
        style(myTab, s == CourseScope.MY);
        style(exploreTab, s == CourseScope.EXPLORE);
        if (reload) {
            load();
        }
    }

    private static void style(JButton b, boolean active) {
        b.setBackground(active ? PRIMARY : WHITE);
        b.setForeground(active ? WHITE : MUTED);
    }

    private static JButton tabButton(String text) {
        JButton b = primaryButton(text);
        b.setPreferredSize(new Dimension(130, 38));
        return b;
    }

    private JComponent filterBar() {
        JPanel bar = new JPanel(new GridBagLayout());
        bar.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 0, 12);
        c.gridx = 0;
        c.weightx = 1;
        search.setPreferredSize(new Dimension(300, 38));
        search.setToolTipText("Search by course name");
        search.addActionListener(e -> load());
        bar.add(search, c);
        c.gridx = 1;
        c.weightx = 0;
        topicBox.setPreferredSize(new Dimension(190, 38));
        topicBox.addActionListener(e -> {
            if (!updatingFilters) {
                load();
            }
        });
        bar.add(topicBox, c);
        c.gridx = 2;
        semesterBox.setPreferredSize(new Dimension(170, 38));
        semesterBox.addActionListener(e -> {
            if (!updatingFilters) {
                load();
            }
        });
        bar.add(semesterBox, c);
        c.gridx = 3;
        c.insets = new Insets(0, 0, 0, 0);
        JButton go = secondaryButton("Search");
        go.setPreferredSize(new Dimension(100, 38));
        go.addActionListener(e -> load());
        bar.add(go, c);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        return bar;
    }

    private CourseFilter currentFilter() {
        String t = (String) topicBox.getSelectedItem();
        String s = (String) semesterBox.getSelectedItem();
        return new CourseFilter(search.getText().trim(),
                ALL.equals(t) ? null : t, ALL.equals(s) ? null : s);
    }

    // ---- data -------------------------------------------------------------

    private void load() {
        int studentId = Session.user().userId();
        CourseScope sc = scope;
        CourseFilter f = currentFilter();
        state.load(() -> Services.student().listCourses(studentId, sc, f), this::render,
                List::isEmpty,
                sc == CourseScope.MY ? "No courses yet" : "No courses found",
                sc == CourseScope.MY
                        ? "Register for an exam in Explore to see its course here."
                        : "Try a different keyword, topic or semester.");
    }

    private void render(List<CourseCard> courses) {
        grid.removeAll();
        for (CourseCard c : courses) {
            grid.add(card(c));
        }
        grid.revalidate();
        grid.repaint();
    }

    private JComponent card(CourseCard c) {
        RoundedPanel card = new RoundedPanel(16, WHITE, BORDER);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(20, 22, 20, 22));

        card.add(left(tag(c.credits() + (c.credits() == 1 ? " CREDIT" : " CREDITS"))));
        card.add(Box.createVerticalStrut(12));
        card.add(left(label(c.name(), bold(17), DARK)));
        card.add(Box.createVerticalStrut(6));
        card.add(left(label(c.instructors().isEmpty() ? "No instructor assigned"
                : String.join(", ", c.instructors()), plain(12), MUTED)));
        card.add(Box.createVerticalStrut(4));
        if (!c.topics().isEmpty()) {
            card.add(left(label("Topics: " + String.join(", ", c.topics()), plain(11), MUTED)));
            card.add(Box.createVerticalStrut(4));
        }
        card.add(left(label((c.semester() == null ? "" : c.semester() + "  ·  ")
                + Fmt.plural(c.studentCount(), "student") + "  ·  "
                + Fmt.plural(c.upcomingExamCount(), "upcoming exam"), plain(11), MUTED)));
        card.add(Box.createVerticalStrut(16));
        JButton view = primaryButton("View exams");
        view.setAlignmentX(Component.LEFT_ALIGNMENT);
        view.setMaximumSize(new Dimension(150, 40));
        view.addActionListener(e -> openExams(c));
        card.add(view);
        return card;
    }

    // ---- exams dialog -----------------------------------------------------

    private void openExams(CourseCard course) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, course.name() + " - exams", Dialog.ModalityType.APPLICATION_MODAL);
        ScrollableBody list = new ScrollableBody();
        list.setBorder(new EmptyBorder(20, 24, 24, 24));
        StateView view = new StateView(list);
        JScrollPane scroll = ScrollableBody.scroll(view, BACKGROUND);
        dialog.setContentPane(scroll);
        dialog.setSize(760, 560);
        dialog.setLocationRelativeTo(owner);

        Runnable[] reload = new Runnable[1];
        reload[0] = () -> {
            int studentId = Session.user().userId();
            view.load(() -> Services.student().listCourseExams(studentId, course.courseId()),
                    exams -> renderExams(list, exams, dialog, reload[0]),
                    List::isEmpty, "No exams yet", "This course has no exams scheduled.");
        };
        reload[0].run();
        dialog.setVisible(true);
    }

    private void renderExams(ScrollableBody list, List<ExamCard> exams, JDialog dialog, Runnable reload) {
        list.removeAll();
        for (ExamCard e : exams) {
            list.add(ExamCardView.create(e,
                    examId -> register(examId, dialog, reload),
                    regisId -> {
                        dialog.dispose();
                        nav.openExam(regisId);
                    }));
            list.add(Box.createVerticalStrut(12));
        }
        list.revalidate();
        list.repaint();
    }

    private void register(int examId, JDialog dialog, Runnable reload) {
        int studentId = Session.user().userId();
        AsyncTask.run(() -> Services.student().registerForExam(studentId, examId),
                ok -> reload.run(),
                (ServiceException err) -> Dialogs.error(dialog, err));
    }

    private static <T extends JComponent> T left(T c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }
}
