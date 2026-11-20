package org.example.ui.panel;

import org.example.Service.Services;
import org.example.Service.StudentService.AttemptResult;
import org.example.Service.StudentService.ExamCard;
import org.example.Service.StudentService.ExamState;
import org.example.Service.StudentService.StudentOverview;
import org.example.session.Session;
import org.example.ui.Navigator;
import org.example.ui.components.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static org.example.ui.components.UITheme.*;

/** Student "Overview" page. Every number comes from StudentService.getOverview. */
public class StudentOverviewPanel extends JPanel implements Refreshable {

    private final Navigator nav;
    private final ScrollableBody body = new ScrollableBody();
    private final StateView state = new StateView(body);

    public StudentOverviewPanel(Navigator nav) {
        this.nav = nav;
        setLayout(new BorderLayout());
        setBackground(BACKGROUND);
        body.setBorder(new EmptyBorder(32, 40, 40, 40));
        add(ScrollableBody.scroll(wrap(state), BACKGROUND), BorderLayout.CENTER);
    }

    /** The StateView must follow the viewport width too. */
    private static JComponent wrap(StateView view) {
        ScrollableBody holder = new ScrollableBody();
        holder.setLayout(new BorderLayout());
        holder.add(view, BorderLayout.CENTER);
        return holder;
    }

    @Override
    public void refresh() {
        int studentId = Session.user().userId();
        state.load(() -> Services.student().getOverview(studentId),
                this::render, null, null, null);
    }

    // ---------------------------------------------------------------------

    private void render(StudentOverview data) {
        body.removeAll();

        body.add(left(label("Overview", bold(30), DARK)));
        body.add(Box.createVerticalStrut(6));
        body.add(left(label("A little preparation. A lot of possibility.", plain(13), MUTED)));
        body.add(Box.createVerticalStrut(26));

        body.add(banner(data));
        body.add(Box.createVerticalStrut(20));
        body.add(statistics(data));
        body.add(Box.createVerticalStrut(26));

        body.add(sectionHeader("Upcoming exams", "View all exams →"));
        body.add(Box.createVerticalStrut(16));
        body.add(upcomingSection(data));

        body.revalidate();
        body.repaint();
    }

    private JComponent banner(StudentOverview data) {
        RoundedPanel banner = new RoundedPanel(18, PRIMARY);
        banner.setLayout(new BorderLayout());
        banner.setBorder(new EmptyBorder(26, 30, 26, 30));
        banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
        banner.setAlignmentX(Component.LEFT_ALIGNMENT);

        String firstName = data.profile().fullName().split(" ")[0];
        int n = data.upcomingExamCount();

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(label("Welcome back, " + firstName, bold(24), WHITE));
        text.add(Box.createVerticalStrut(8));
        text.add(label(n == 0 ? "You have no upcoming exams."
                        : "You have " + Fmt.plural(n, "upcoming exam") + ".",
                plain(13), new Color(214, 229, 255)));

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        JLabel date = label(LocalDate.now().format(
                DateTimeFormatter.ofPattern("EEEE, MMM dd", Locale.ENGLISH)).toUpperCase(Locale.ENGLISH),
                bold(11), new Color(214, 229, 255));
        date.setAlignmentX(Component.RIGHT_ALIGNMENT);
        JButton view = secondaryButton("View my exams  →");
        view.setAlignmentX(Component.RIGHT_ALIGNMENT);
        view.setPreferredSize(new Dimension(170, 40));
        view.setMaximumSize(new Dimension(170, 40));
        view.addActionListener(e -> nav.show(Navigator.MY_EXAMS));
        right.add(date);
        right.add(Box.createVerticalStrut(24));
        right.add(view);

        banner.add(text, BorderLayout.CENTER);
        banner.add(right, BorderLayout.EAST);
        return banner;
    }

    private JComponent statistics(StudentOverview data) {
        JPanel row = new JPanel(new GridLayout(1, 3, 20, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 112));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        row.add(statCard("Enrolled courses", String.format("%02d", data.courseCount()),
                data.courseCount() == 0 ? "Register for an exam to join a course"
                        : "Courses with a registered exam", MUTED));

        String next = data.upcomingExams().isEmpty() ? "Nothing scheduled"
                : "Next: " + Fmt.dayLabel(data.upcomingExams().get(0).examDate()).toLowerCase(Locale.ENGLISH)
                + " at " + Fmt.time(data.upcomingExams().get(0).examDate());
        row.add(statCard("Upcoming exams", String.format("%02d", data.upcomingExamCount()), next, MUTED));

        boolean hasAvg = data.averagePercent() != null;
        row.add(statCard("Average score", Fmt.percent(data.averagePercent()),
                hasAvg ? "Across " + Fmt.plural(data.gradedAttemptCount(), "attempt")
                        : "No graded attempts yet",
                hasAvg ? GREEN : MUTED));
        return row;
    }

    private static JComponent statCard(String title, String value, String caption, Color captionColor) {
        RoundedPanel card = new RoundedPanel(16, WHITE);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(18, 22, 18, 22));
        card.add(left(label(title, plain(12), MUTED)));
        card.add(Box.createVerticalStrut(8));
        card.add(left(label(value, bold(30), DARK)));
        card.add(Box.createVerticalStrut(6));
        card.add(left(label(caption, plain(11), captionColor)));
        return card;
    }

    private JComponent sectionHeader(String title, String action) {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(label(title, bold(20), DARK), BorderLayout.WEST);
        JButton button = textButton(action);
        button.addActionListener(e -> nav.show(Navigator.MY_EXAMS));
        header.add(button, BorderLayout.EAST);
        return header;
    }

    private JComponent upcomingSection(StudentOverview data) {
        JPanel section = new JPanel(new GridBagLayout());
        section.setOpaque(false);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.weighty = 1;

        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        if (data.upcomingExams().isEmpty()) {
            RoundedPanel empty = new RoundedPanel(16, WHITE);
            empty.setBorder(new EmptyBorder(30, 24, 30, 24));
            empty.setLayout(new BorderLayout());
            empty.add(label("No upcoming exams. Register for one from Courses.", plain(13), MUTED));
            list.add(empty);
        }
        for (ExamCard exam : data.upcomingExams()) {
            list.add(examRow(exam));
            list.add(Box.createVerticalStrut(16));
        }

        gbc.gridx = 0;
        gbc.weightx = 2.1;
        gbc.insets = new Insets(0, 0, 0, 24);
        section.add(list, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.insets = new Insets(0, 0, 0, 0);
        section.add(latestResultCard(data.latestResult()), gbc);
        return section;
    }

    private JComponent examRow(ExamCard exam) {
        RoundedPanel row = new RoundedPanel(16, WHITE);
        row.setLayout(new BorderLayout(18, 0));
        row.setBorder(new EmptyBorder(15, 16, 15, 16));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 95));
        row.setPreferredSize(new Dimension(500, 95));

        RoundedPanel dateCard = new RoundedPanel(12, LIGHT_BLUE);
        dateCard.setPreferredSize(new Dimension(62, 64));
        dateCard.setLayout(new BoxLayout(dateCard, BoxLayout.Y_AXIS));
        dateCard.setBorder(new EmptyBorder(9, 5, 7, 5));
        JLabel day = label(Fmt.dayLabel(exam.examDate()), bold(9), PRIMARY);
        JLabel num = label(Fmt.dayOfMonth(exam.examDate()), bold(24), PRIMARY);
        day.setAlignmentX(Component.CENTER_ALIGNMENT);
        num.setAlignmentX(Component.CENTER_ALIGNMENT);
        dateCard.add(day);
        dateCard.add(Box.createVerticalStrut(3));
        dateCard.add(num);
        row.add(dateCard, BorderLayout.WEST);

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.add(label(exam.courseName(), bold(14), DARK));
        info.add(Box.createVerticalStrut(7));
        info.add(label(exam.title(), plain(12), MUTED));
        info.add(Box.createVerticalStrut(6));
        info.add(label(Fmt.time(exam.examDate()) + "  ·  " + exam.durationMin() + " min  ·  "
                + Fmt.plural(exam.questionCount(), "question"), plain(11), MUTED));
        row.add(info, BorderLayout.CENTER);

        boolean canStart = (exam.state() == ExamState.OPEN || exam.state() == ExamState.IN_PROGRESS)
                && exam.regisId() != null;
        JButton action = canStart
                ? primaryButton(exam.state() == ExamState.IN_PROGRESS ? "Resume exam" : "Start exam")
                : secondaryButton("View details");
        action.setPreferredSize(new Dimension(136, 42));
        action.addActionListener(e -> {
            if (canStart) {
                nav.openExam(exam.regisId());
            } else {
                nav.show(Navigator.MY_EXAMS);
            }
        });
        row.add(action, BorderLayout.EAST);
        return row;
    }

    private JComponent latestResultCard(AttemptResult result) {
        RoundedPanel card = new RoundedPanel(16, WHITE);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(20, 22, 20, 22));

        card.add(left(tag("LATEST RESULT")));
        card.add(Box.createVerticalStrut(12));

        if (result == null) {
            card.add(left(label("No results yet", bold(18), DARK)));
            card.add(Box.createVerticalStrut(8));
            card.add(left(label("Your score appears here after you submit an exam.", plain(12), MUTED)));
            return card;
        }

        card.add(left(label("<html><span style='font-size:44pt; color:#2563EB;'><b>"
                + Fmt.marks(result.score()) + "</b></span><span style='font-size:13pt; color:#64799B;'> / "
                + Fmt.marks(result.maxScore()) + "</span></html>", plain(13), DARK)));
        card.add(Box.createVerticalStrut(5));
        card.add(left(label(result.courseName() + " · " + result.examTitle(), plain(12), DARK)));
        card.add(Box.createVerticalStrut(16));
        String feedback = !result.fullyGraded() ? "Some answers are still being graded."
                : result.passed() ? "Well done! You passed." : "Not passed this time. Keep practising.";
        card.add(left(label(feedback, plain(12), MUTED)));
        return card;
    }

    private static <T extends JComponent> T left(T c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }
}
