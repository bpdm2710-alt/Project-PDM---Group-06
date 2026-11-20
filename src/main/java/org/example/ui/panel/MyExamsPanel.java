package org.example.ui.panel;

import org.example.Service.Services;
import org.example.Service.StudentService.ExamCard;
import org.example.session.Session;
import org.example.ui.Navigator;
import org.example.ui.components.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

import static org.example.ui.components.UITheme.*;

/** Student "My exams": every exam the student registered for. */
public class MyExamsPanel extends JPanel implements Refreshable {

    private final Navigator nav;
    private final ScrollableBody body = new ScrollableBody();
    private final StateView state = new StateView(body);

    public MyExamsPanel(Navigator nav) {
        this.nav = nav;
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
        state.load(() -> Services.student().listMyExams(studentId), this::render,
                List::isEmpty, "No registered exams",
                "Open Courses, pick a course and register for one of its exams.");
    }

    private void render(List<ExamCard> exams) {
        body.removeAll();
        JLabel title = label("My exams", bold(30), DARK);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel sub = label(Fmt.plural(exams.size(), "registered exam"), plain(13), MUTED);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(title);
        body.add(Box.createVerticalStrut(6));
        body.add(sub);
        body.add(Box.createVerticalStrut(24));
        for (ExamCard e : exams) {
            body.add(ExamCardView.create(e, id -> { }, nav::openExam));
            body.add(Box.createVerticalStrut(14));
        }
        body.revalidate();
        body.repaint();
    }
}
