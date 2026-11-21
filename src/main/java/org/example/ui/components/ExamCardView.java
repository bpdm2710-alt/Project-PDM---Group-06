package org.example.ui.components;

import org.example.Service.StudentService.ExamCard;
import org.example.Service.StudentService.ExamState;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;

import static org.example.ui.components.UITheme.*;

/**
 * One exam as a card with the right action for its state. Shared by the
 * Courses dialog and the exams page so both behave identically.
 */
public final class ExamCardView {

    private ExamCardView() {
    }

    /**
     * @param onRegister called with the exam id when the student presses Register
     * @param onOpen     called with the registration id for Start / Resume
     */
    public static JComponent create(ExamCard e, Consumer<Integer> onRegister, Consumer<Integer> onOpen) {
        RoundedPanel row = new RoundedPanel(16, WHITE, BORDER);
        row.setLayout(new BorderLayout(18, 0));
        row.setBorder(new EmptyBorder(16, 20, 16, 20));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 124));

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        top.setOpaque(false);
        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(statusTag(e));
        info.add(top);
        info.add(Box.createVerticalStrut(8));
        info.add(left(label(e.title(), bold(15), DARK)));
        info.add(Box.createVerticalStrut(3));
        info.add(left(label(e.courseName(), plain(12), MUTED)));
        info.add(Box.createVerticalStrut(7));
        info.add(left(label(Fmt.dateTime(e.examDate()) + "  ·  " + e.durationMin() + " min  ·  "
                + Fmt.plural(e.questionCount(), "question") + "  ·  " + Fmt.marks(e.totalMarks())
                + " marks  ·  pass " + Fmt.marks(e.passMark()) + "%", plain(11), MUTED)));
        if (e.regisId() != null) {
            info.add(Box.createVerticalStrut(3));
            info.add(left(label("Attempts used: " + e.attemptsUsed() + " / " + e.maxAttempts(),
                    plain(11), MUTED)));
        }
        row.add(info, BorderLayout.CENTER);

        JButton action = actionButton(e, onRegister, onOpen);
        if (action != null) {
            JPanel east = new JPanel(new GridBagLayout());
            east.setOpaque(false);
            east.add(action);
            row.add(east, BorderLayout.EAST);
        }
        return row;
    }

    private static JButton actionButton(ExamCard e, Consumer<Integer> onRegister, Consumer<Integer> onOpen) {
        JButton b;
        switch (e.state()) {
            case NOT_REGISTERED -> {
                b = primaryButton("Register");
                b.addActionListener(x -> onRegister.accept(e.examId()));
            }
            case OPEN -> {
                b = primaryButton("Start exam");
                b.addActionListener(x -> onOpen.accept(e.regisId()));
            }
            case IN_PROGRESS -> {
                b = primaryButton("Resume exam");
                b.addActionListener(x -> onOpen.accept(e.regisId()));
            }
            default -> {
                return null;
            }
        }
        b.setPreferredSize(new Dimension(136, 42));
        return b;
    }

    private static JLabel statusTag(ExamCard e) {
        return switch (e.state()) {
            case NOT_REGISTERED -> tag("NOT REGISTERED");
            case UPCOMING -> tag("REGISTERED · UPCOMING");
            case OPEN -> greenTag("OPEN NOW");
            case IN_PROGRESS -> greenTag("IN PROGRESS");
            case COMPLETED -> tag("COMPLETED");
        };
    }

    private static <T extends JComponent> T left(T c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }
}
