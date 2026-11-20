package org.example.ui.panel;

import org.example.Service.QuestionType;
import org.example.Service.ServiceException;
import org.example.Service.Services;
import org.example.Service.StudentService.*;
import org.example.ui.components.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.example.ui.components.UITheme.*;

/**
 * Taking an exam. All data comes from the AttemptSession returned by the
 * service: questions, saved answers and the server-computed time left.
 * Every change is saved immediately (saveAnswer is idempotent); the timer
 * submits automatically at zero. The correct answer is never available here.
 */
public class ExamSessionPanel extends JPanel {

    private final AttemptSession session;
    private final Consumer<AttemptResult> onSubmitted;
    private final List<ExamQuestion> questions;
    private final Map<Integer, AnswerInput> answers = new HashMap<>();
    private final long deadlineNanos;

    private int index;
    private boolean finished;
    private final JLabel timerLabel = label("", bold(18), PRIMARY);
    private final JLabel progressLabel = label("", plain(12), MUTED);
    private final JPanel questionArea = new JPanel(new BorderLayout());
    private final JPanel palette = new JPanel(new GridLayout(0, 5, 8, 8));
    private final JButton prev = secondaryButton("← Previous");
    private final JButton next = primaryButton("Next →");
    private final Timer ticker;

    public ExamSessionPanel(AttemptSession session, Consumer<AttemptResult> onSubmitted) {
        this.session = session;
        this.onSubmitted = onSubmitted;
        this.questions = session.questions();
        this.answers.putAll(session.savedAnswers());
        this.deadlineNanos = System.nanoTime() + session.secondsRemaining() * 1_000_000_000L;

        setLayout(new BorderLayout());
        setBackground(BACKGROUND);
        add(header(), BorderLayout.NORTH);
        add(center(), BorderLayout.CENTER);
        add(sidebar(), BorderLayout.EAST);

        ticker = new Timer(1000, e -> tick());
        ticker.start();
        tick();
        show(0);
    }

    // ---- layout -----------------------------------------------------------

    private JComponent header() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(WHITE);
        bar.setBorder(new EmptyBorder(16, 32, 16, 32));
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(label(session.examTitle(), bold(20), DARK));
        text.add(Box.createVerticalStrut(4));
        text.add(label(session.courseName() + "  ·  attempt " + session.key().attemptNo()
                + " of " + session.maxAttempts(), plain(12), MUTED));
        bar.add(text, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 0));
        right.setOpaque(false);
        right.add(label("Time left", plain(12), MUTED));
        right.add(timerLabel);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JComponent center() {
        JPanel wrap = new JPanel(new BorderLayout(0, 16));
        wrap.setOpaque(false);
        wrap.setBorder(new EmptyBorder(24, 32, 24, 16));
        questionArea.setOpaque(false);
        wrap.add(ScrollableBody.scroll(questionArea, BACKGROUND), BorderLayout.CENTER);
        JPanel nav = new JPanel(new BorderLayout());
        nav.setOpaque(false);
        prev.setPreferredSize(new Dimension(140, 42));
        next.setPreferredSize(new Dimension(140, 42));
        prev.addActionListener(e -> show(index - 1));
        next.addActionListener(e -> show(index + 1));
        nav.add(prev, BorderLayout.WEST);
        nav.add(next, BorderLayout.EAST);
        wrap.add(nav, BorderLayout.SOUTH);
        return wrap;
    }

    private JComponent sidebar() {
        RoundedPanel side = new RoundedPanel(16, WHITE, BORDER);
        side.setLayout(new BorderLayout(0, 14));
        side.setBorder(new EmptyBorder(20, 20, 20, 20));
        side.add(label("Questions", bold(15), DARK), BorderLayout.NORTH);
        palette.setOpaque(false);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(palette, BorderLayout.NORTH);
        side.add(top, BorderLayout.CENTER);
        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        progressLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottom.add(progressLabel);
        bottom.add(Box.createVerticalStrut(12));
        JButton submit = primaryButton("Submit exam");
        submit.setAlignmentX(Component.LEFT_ALIGNMENT);
        submit.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        submit.addActionListener(e -> confirmSubmit());
        bottom.add(submit);
        side.add(bottom, BorderLayout.SOUTH);

        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.setBorder(new EmptyBorder(24, 0, 24, 32));
        holder.setPreferredSize(new Dimension(290, 0));
        holder.add(side, BorderLayout.CENTER);
        return holder;
    }

    // ---- question view ----------------------------------------------------

    private void show(int i) {
        index = Math.max(0, Math.min(questions.size() - 1, i));
        ExamQuestion q = questions.get(index);

        JPanel card = new RoundedPanel(16, WHITE, BORDER);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(24, 28, 24, 28));
        JPanel meta = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        meta.setOpaque(false);
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);
        meta.add(tag("QUESTION " + (index + 1) + " OF " + questions.size()));
        meta.add(tag(q.type() == QuestionType.MCQ ? "MULTIPLE CHOICE" : "SHORT ANSWER"));
        meta.add(tag(Fmt.marks(q.marks()) + (q.marks() != null && q.marks().intValue() == 1 ? " MARK" : " MARKS")));
        card.add(meta);
        card.add(Box.createVerticalStrut(16));
        JTextArea content = new JTextArea(q.content());
        content.setFont(plain(16));
        content.setForeground(DARK);
        content.setLineWrap(true);
        content.setWrapStyleWord(true);
        content.setEditable(false);
        content.setOpaque(false);
        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(content);
        card.add(Box.createVerticalStrut(18));
        card.add(q.type() == QuestionType.MCQ ? mcq(q) : shortAnswer(q));

        questionArea.removeAll();
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(card, BorderLayout.NORTH);
        questionArea.add(top, BorderLayout.CENTER);
        questionArea.revalidate();
        questionArea.repaint();

        prev.setEnabled(index > 0);
        next.setEnabled(index < questions.size() - 1);
        refreshPalette();
    }

    private JComponent mcq(ExamQuestion q) {
        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setAlignmentX(Component.LEFT_ALIGNMENT);
        ButtonGroup group = new ButtonGroup();
        AnswerInput saved = answers.get(q.questionId());
        for (ExamOption o : q.options()) {
            JRadioButton r = new JRadioButton(o.content());
            r.setFont(plain(14));
            r.setForeground(DARK);
            r.setOpaque(true);
            r.setBackground(BACKGROUND);
            r.setBorder(new EmptyBorder(12, 14, 12, 14));
            r.setFocusPainted(false);
            r.setAlignmentX(Component.LEFT_ALIGNMENT);
            r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
            r.setSelected(saved != null && saved.selectedOptionNo() != null
                    && saved.selectedOptionNo() == o.optionNo());
            r.addActionListener(e -> answer(q, new AnswerInput(o.optionNo(), null)));
            group.add(r);
            list.add(r);
            list.add(Box.createVerticalStrut(8));
        }
        return list;
    }

    private JComponent shortAnswer(ExamQuestion q) {
        JTextArea area = new JTextArea(6, 40);
        area.setFont(plain(14));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(new EmptyBorder(10, 12, 10, 12));
        AnswerInput saved = answers.get(q.questionId());
        if (saved != null && saved.text() != null) {
            area.setText(saved.text());
        }
        Timer debounce = new Timer(600, e -> answer(q, new AnswerInput(null, area.getText())));
        debounce.setRepeats(false);
        area.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { debounce.restart(); }
            public void removeUpdate(DocumentEvent e) { debounce.restart(); }
            public void changedUpdate(DocumentEvent e) { debounce.restart(); }
        });
        // Leaving the box must not lose the last keystrokes.
        area.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) {
                if (debounce.isRunning()) {
                    debounce.stop();
                    answer(q, new AnswerInput(null, area.getText()));
                }
            }
        });
        JScrollPane sp = new JScrollPane(area);
        sp.setBorder(BorderFactory.createLineBorder(BORDER));
        sp.setAlignmentX(Component.LEFT_ALIGNMENT);
        return sp;
    }

    private void answer(ExamQuestion q, AnswerInput input) {
        if (finished) {
            return;
        }
        answers.put(q.questionId(), input);
        refreshPalette();
        AsyncTask.run(() -> {
            Services.student().saveAnswer(session.key(), q.questionId(), input);
            return Boolean.TRUE;
        }, ok -> { }, err -> {
            if (!finished) {
                Dialogs.error(this, err);
            }
        });
    }

    private boolean isAnswered(ExamQuestion q) {
        AnswerInput a = answers.get(q.questionId());
        if (a == null) {
            return false;
        }
        return q.type() == QuestionType.MCQ ? a.selectedOptionNo() != null
                : a.text() != null && !a.text().isBlank();
    }

    private void refreshPalette() {
        palette.removeAll();
        int done = 0;
        for (int i = 0; i < questions.size(); i++) {
            boolean answered = isAnswered(questions.get(i));
            done += answered ? 1 : 0;
            JButton b = new JButton(String.valueOf(i + 1));
            b.setFont(bold(12));
            b.setMargin(new Insets(0, 0, 0, 0));
            b.setPreferredSize(new Dimension(40, 36));
            b.setFocusPainted(false);
            b.setBorderPainted(false);
            b.setOpaque(true);
            b.setUI(new javax.swing.plaf.basic.BasicButtonUI());
            b.setBackground(i == index ? PRIMARY : answered ? GREEN_BG : LIGHT_BLUE);
            b.setForeground(i == index ? WHITE : answered ? GREEN : PRIMARY);
            b.setCursor(new Cursor(Cursor.HAND_CURSOR));
            int target = i;
            b.addActionListener(e -> show(target));
            palette.add(b);
        }
        progressLabel.setText(done + " of " + questions.size() + " answered");
        palette.revalidate();
        palette.repaint();
    }

    // ---- timer and submit -------------------------------------------------

    private long secondsLeft() {
        return Math.max(0, (deadlineNanos - System.nanoTime()) / 1_000_000_000L);
    }

    private void tick() {
        long left = secondsLeft();
        timerLabel.setText(Fmt.clock(left));
        timerLabel.setForeground(left <= 300 ? new Color(205, 55, 55) : PRIMARY);
        if (left <= 0 && !finished) {
            submit(true);
        }
    }

    private void confirmSubmit() {
        int unanswered = 0;
        for (ExamQuestion q : questions) {
            unanswered += isAnswered(q) ? 0 : 1;
        }
        String msg = unanswered == 0 ? "Submit your exam now? You cannot change answers afterwards."
                : "You have " + Fmt.plural(unanswered, "unanswered question")
                + ". Submit anyway? You cannot change answers afterwards.";
        if (Dialogs.confirm(this, "Submit exam", msg)) {
            submit(false);
        }
    }

    private void submit(boolean timeUp) {
        if (finished) {
            return;
        }
        finished = true;
        ticker.stop();
        setEnabled(false);
        if (timeUp) {
            JOptionPane.showMessageDialog(this, "Time is up. Your exam is being submitted.",
                    "Time is up", JOptionPane.INFORMATION_MESSAGE);
        }
        AsyncTask.run(() -> Services.student().submitAttempt(session.key()),
                onSubmitted,
                (ServiceException err) -> {
                    finished = false;
                    if (!timeUp) {
                        ticker.start();
                    }
                    Dialogs.error(this, err);
                    if (timeUp) {
                        submit(true);
                    }
                });
    }

    /** Stops the clock when the panel is thrown away. */
    public void dispose() {
        ticker.stop();
    }
}
