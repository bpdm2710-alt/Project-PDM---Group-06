package org.example.ui.panel;

import org.example.Service.Services;
import org.example.Service.StudentService.AttemptResult;
import org.example.session.Session;
import org.example.ui.components.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

import static org.example.ui.components.UITheme.*;

/** Student "Results": every finished attempt, newest first. */
public class ResultsPanel extends JPanel implements Refreshable {

    private final ScrollableBody body = new ScrollableBody();
    private final StateView state = new StateView(body);

    public ResultsPanel() {
        setLayout(new BorderLayout());
        setBackground(BACKGROUND);
        body.setBorder(new EmptyBorder(32, 40, 40, 40));
        ScrollableBody holder = new ScrollableBody();
        holder.setLayout(new BorderLayout());
        holder.add(state, BorderLayout.CENTER);
        add(ScrollableBody.scroll(holder, BACKGROUND), BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        int studentId = Session.user().userId();
        state.load(() -> Services.student().listResults(studentId), this::render,
                List::isEmpty, "No results yet", "Your scores appear here after you submit an exam.");
    }

    private void render(List<AttemptResult> results) {
        body.removeAll();
        JLabel title = label("Results", bold(30), DARK);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel sub = label(Fmt.plural(results.size(), "attempt") + ", newest first", plain(13), MUTED);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(title);
        body.add(Box.createVerticalStrut(6));
        body.add(sub);
        body.add(Box.createVerticalStrut(24));
        for (AttemptResult r : results) {
            body.add(row(r));
            body.add(Box.createVerticalStrut(12));
        }
        body.revalidate();
        body.repaint();
    }

    private JComponent row(AttemptResult r) {
        RoundedPanel row = new RoundedPanel(16, WHITE, BORDER);
        row.setLayout(new BorderLayout(20, 0));
        row.setBorder(new EmptyBorder(16, 22, 16, 22));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.add(label(r.examTitle(), bold(15), DARK));
        info.add(Box.createVerticalStrut(4));
        info.add(label(r.courseName() + "  ·  attempt " + r.key().attemptNo()
                + "  ·  " + Fmt.dateTime(r.submittedAt()), plain(12), MUTED));
        row.add(info, BorderLayout.CENTER);

        JPanel score = new JPanel();
        score.setOpaque(false);
        score.setLayout(new BoxLayout(score, BoxLayout.Y_AXIS));
        JLabel value = label(Fmt.marks(r.score()) + " / " + Fmt.marks(r.maxScore()), bold(20), PRIMARY);
        value.setAlignmentX(Component.RIGHT_ALIGNMENT);
        score.add(value);
        score.add(Box.createVerticalStrut(4));
        JLabel pct = label(Fmt.percent(r.percent()), plain(12), MUTED);
        pct.setAlignmentX(Component.RIGHT_ALIGNMENT);
        score.add(pct);
        row.add(score, BorderLayout.EAST);

        JPanel west = new JPanel(new GridBagLayout());
        west.setOpaque(false);
        west.add(!r.fullyGraded() ? tag("GRADING") : r.passed() ? greenTag("PASSED") : tag("NOT PASSED"));
        west.setPreferredSize(new Dimension(110, 10));
        row.add(west, BorderLayout.WEST);
        return row;
    }
}
