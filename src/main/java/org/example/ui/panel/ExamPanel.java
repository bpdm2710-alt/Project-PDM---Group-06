package org.example.ui.panel;

import org.example.ui.components.RoundedPanel;
import org.example.ui.components.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;

import static org.example.ui.components.UITheme.*;

/**
 * Student "My exams" screen: an exam in progress.
 *
 * Covers two reference screens:
 *  - multiple choice question (image 1583, including the submit modal)
 *  - short answer question (image 1584)
 *
 * Everything is a front-end prototype: questions/answers are mock data and
 * no backend call is made.
 */
public class ExamPanel extends JPanel {

    private static final int TOTAL_QUESTIONS = 20;
    private static final int START_SECONDS = 38 * 60 + 42;

    private static final class Question {
        final String type;
        final String text;
        final String instruction;
        final String[] options;
        final int marks;

        Question(String type, String text, String instruction,
                 String[] options, int marks) {
            this.type = type;
            this.text = text;
            this.instruction = instruction;
            this.options = options;
            this.marks = marks;
        }
    }

    private final Question[] questions = buildQuestions();
    private final String[] answers = new String[TOTAL_QUESTIONS];
    private final boolean[] answered = new boolean[TOTAL_QUESTIONS];
    private final JButton[] navigatorButtons = new JButton[TOTAL_QUESTIONS];

    private int current = 2;              // Question 3 of 20
    private int secondsLeft = START_SECONDS;
    private boolean submitted = false;
    private boolean updating = false;

    private JPanel questionCard;
    private JPanel navigationRow;
    private JLabel questionCounter;
    private JLabel answeredCounter;
    private JProgressBar progress;
    private JLabel timerLabel;
    private JLabel savedLabel;
    private JLabel submitHint;

    private final Timer timer;

    public ExamPanel() {
        setBackground(BACKGROUND);
        setLayout(new BorderLayout());

        // A few answers are pre-filled so the prototype opens in a realistic
        // "in progress" state, matching the reference screens.
        answers[0] = "A"; answered[0] = true;
        answers[1] = "B"; answered[1] = true;
        answers[2] = "C"; answered[2] = true;
        answers[6] = "A"; answered[6] = true;
        answers[7] = "Normalization organizes data into related tables to "
                + "reduce redundancy and improve data integrity. It helps "
                + "prevent insertion, update, and deletion anomalies.";
        answered[7] = true;

        add(createHeader(), BorderLayout.NORTH);

        JPanel page = new JPanel();
        page.setBackground(BACKGROUND);
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        page.setBorder(new EmptyBorder(24, 40, 36, 40));

        page.add(createMetaBar());
        page.add(Box.createVerticalStrut(22));
        page.add(createContentRow());
        page.add(Box.createVerticalStrut(20));
        page.add(createNavigationRow());

        JScrollPane scroll = new JScrollPane(page);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setHorizontalScrollBarPolicy(
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);

        timer = new Timer(1000, e -> tick());
        timer.setRepeats(true);
        timer.start();

        refreshQuestion();
    }

    // ---------------------------------------------------------------------
    // HEADER + META BAR
    // ---------------------------------------------------------------------

    private JPanel createHeader() {
        JPanel header = new JPanel();
        header.setBackground(BACKGROUND);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(new EmptyBorder(30, 40, 0, 40));

        JLabel title = label("Database fundamentals", bold(30), DARK);
        JLabel subtitle = label("Midterm examination  /  CS201", plain(13), MUTED);
        subtitle.setBorder(new EmptyBorder(6, 0, 0, 0));

        header.add(title);
        header.add(subtitle);
        return header;
    }

    private JPanel createMetaBar() {
        RoundedPanel bar = card(14, WHITE);
        bar.setLayout(new BorderLayout());
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 78));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 22, 0));
        left.setOpaque(false);
        left.add(tag("ATTEMPT 1 OF 2"));

        savedLabel = label("●  All changes saved", plain(12), GREEN);
        left.add(savedLabel);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));

        JPanel timerRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        timerRow.setOpaque(false);

        JLabel timerCaption = label("TIME REMAINING", bold(10), MUTED);
        timerCaption.setAlignmentY(Component.CENTER_ALIGNMENT);

        timerLabel = label(formatTime(secondsLeft), bold(26), PRIMARY);
        timerLabel.setAlignmentY(Component.CENTER_ALIGNMENT);

        timerRow.add(timerCaption);
        timerRow.add(timerLabel);
        right.add(timerRow);

        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // ---------------------------------------------------------------------
    // QUESTION + NAVIGATOR
    // ---------------------------------------------------------------------

    private JPanel createContentRow() {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.weighty = 1;

        gbc.gridx = 0;
        gbc.weightx = 2.05;
        gbc.insets = new Insets(0, 0, 0, 20);
        questionCard = createQuestionCard();
        row.add(questionCard, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.insets = new Insets(0, 0, 0, 0);
        row.add(createNavigatorCard(), gbc);

        return row;
    }

    private JPanel createQuestionCard() {
        RoundedPanel card = card(14, WHITE);
        card.setLayout(new BorderLayout());
        return card;
    }

    private void renderQuestion() {
        questionCard.removeAll();

        Question q = questions[current];

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(24, 26, 22, 26));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        topRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        topRow.add(tag(q.type.toUpperCase()), BorderLayout.WEST);
        topRow.add(label(q.marks + " marks", bold(12), PRIMARY), BorderLayout.EAST);

        content.add(topRow);
        content.add(Box.createVerticalStrut(20));
        content.add(label("Question " + (current + 1) + " of " + TOTAL_QUESTIONS,
                bold(19), DARK));
        content.add(Box.createVerticalStrut(18));
        JTextArea questionText = new JTextArea(q.text);
        questionText.setFont(bold(20));
        questionText.setForeground(DARK);
        questionText.setOpaque(false);
        questionText.setLineWrap(true);
        questionText.setWrapStyleWord(true);
        questionText.setEditable(false);
        questionText.setFocusable(false);
        questionText.setBorder(null);
        questionText.setAlignmentX(Component.LEFT_ALIGNMENT);
        questionText.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        content.add(questionText);

        content.add(Box.createVerticalStrut(10));
        content.add(label(q.instruction, plain(13), MUTED));
        content.add(Box.createVerticalStrut(20));

        if (q.options != null) {
            content.add(createOptions(q));
        } else {
            content.add(createAnswerArea());
        }

        content.add(Box.createVerticalGlue());
        content.add(Box.createVerticalStrut(16));
        content.add(label("Answer saved automatically", plain(11), MUTED));

        leftAlign(content);

        questionCard.add(content, BorderLayout.CENTER);
        questionCard.revalidate();
        questionCard.repaint();
    }

    private JPanel createOptions(Question q) {
        JPanel options = new JPanel();
        options.setOpaque(false);
        options.setLayout(new BoxLayout(options, BoxLayout.Y_AXIS));

        ButtonGroup group = new ButtonGroup();
        for (String option : q.options) {
            String letter = option.substring(0, 1);
            JRadioButton radio = new JRadioButton();
            radio.setOpaque(false);
            radio.setFocusPainted(false);
            radio.setCursor(new Cursor(Cursor.HAND_CURSOR));
            radio.setSelected(letter.equals(answers[current]));
            group.add(radio);

            OptionPanel row = new OptionPanel(radio, letter.equals(answers[current]));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
            row.setPreferredSize(new Dimension(640, 54));

            radio.addActionListener(e -> {
                answers[current] = letter;
                answered[current] = true;
                for (Component c : options.getComponents()) {
                    if (c instanceof OptionPanel) {
                        ((OptionPanel) c).setSelected(
                                radio.isSelected()
                                        && ((OptionPanel) c).radio == radio);
                    }
                }
                savedLabel.setText("●  All changes saved");
                refreshNavigator();
            });

            row.add(radio);
            row.add(label(option, plain(14), DARK));
            options.add(row);
            options.add(Box.createVerticalStrut(12));
        }
        return options;
    }

    private JTextArea createAnswerArea() {
        JTextArea area = new JTextArea();
        area.setFont(plain(14));
        area.setForeground(DARK);
        area.setBackground(new Color(248, 251, 255));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                new EmptyBorder(16, 16, 16, 16)));
        area.setPreferredSize(new Dimension(640, 170));
        area.setText(answers[current] == null ? "" : answers[current]);
        area.setEditable(!submitted);

        area.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { save(area); }
            public void removeUpdate(DocumentEvent e) { save(area); }
            public void changedUpdate(DocumentEvent e) { save(area); }

            private void save(JTextArea source) {
                if (updating) return;
                answers[current] = source.getText();
                answered[current] = !source.getText().trim().isEmpty();
                savedLabel.setText("●  All changes saved");
                refreshNavigator();
            }
        });
        return area;
    }

    private JPanel createNavigatorCard() {
        RoundedPanel card = card(14, WHITE);
        card.setLayout(new BorderLayout());

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(label("Question navigator", bold(15), DARK));
        content.add(Box.createVerticalStrut(6));

        answeredCounter = label("", plain(12), MUTED);
        content.add(answeredCounter);
        content.add(Box.createVerticalStrut(12));

        progress = new JProgressBar(0, TOTAL_QUESTIONS);
        progress.setForeground(PRIMARY);
        progress.setBackground(new Color(232, 237, 246));
        progress.setBorderPainted(false);
        progress.setPreferredSize(new Dimension(10, 6));
        content.add(progress);
        content.add(Box.createVerticalStrut(20));

        JPanel grid = new JPanel(new GridLayout(4, 5, 10, 10));
        grid.setOpaque(false);
        for (int i = 0; i < TOTAL_QUESTIONS; i++) {
            JButton b = new JButton(String.valueOf(i + 1));
            b.setFont(bold(13));
            b.setUI(new BasicButtonUI());
            b.setOpaque(true);
            b.setContentAreaFilled(true);
            b.setFocusPainted(false);
            b.setBorderPainted(false);
            b.setCursor(new Cursor(Cursor.HAND_CURSOR));
            final int index = i;
            b.addActionListener(e -> goTo(index));
            navigatorButtons[i] = b;
            grid.add(b);
        }
        content.add(grid);
        content.add(Box.createVerticalStrut(18));

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        legend.setOpaque(false);
        legend.add(label("■  Answered", plain(11), MUTED));
        legend.add(label("□  Not answered", plain(11), MUTED));
        content.add(legend);
        content.add(Box.createVerticalStrut(22));

        content.add(label(
                "<html><body style='width:210px'>Your exam submits automatically"
                        + "<br>when the timer reaches zero.</body></html>",
                plain(11), MUTED));
        content.add(Box.createVerticalStrut(18));

        JButton submit = primaryButton("Submit exam");
        submit.setPreferredSize(new Dimension(280, 46));
        submit.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        submit.setAlignmentX(Component.LEFT_ALIGNMENT);
        submit.addActionListener(e -> openSubmitDialog());
        submit.setEnabled(!submitted);
        content.add(submit);

        submitHint = label("You'll confirm before submitting.", plain(10), FAINT);
        submitHint.setBorder(new EmptyBorder(9, 0, 0, 0));
        content.add(submitHint);

        leftAlign(content);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private JPanel createNavigationRow() {
        navigationRow = new JPanel(new BorderLayout());
        navigationRow.setOpaque(false);
        navigationRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        JButton previous = secondaryButton("←  Previous");
        previous.setPreferredSize(new Dimension(150, 44));
        previous.addActionListener(e -> goTo(current - 1));
        previous.setEnabled(!submitted);

        questionCounter = label("", plain(12), MUTED);
        questionCounter.setHorizontalAlignment(SwingConstants.CENTER);

        JButton next = primaryButton("Next question  →");
        next.setPreferredSize(new Dimension(180, 44));
        next.addActionListener(e -> goTo(current + 1));
        next.setEnabled(!submitted);

        navigationRow.add(previous, BorderLayout.WEST);
        navigationRow.add(questionCounter, BorderLayout.CENTER);
        navigationRow.add(next, BorderLayout.EAST);
        return navigationRow;
    }

    // ---------------------------------------------------------------------
    // STATE + INTERACTIONS
    // ---------------------------------------------------------------------

    /** BoxLayout centres children by default; the design is left aligned. */
    private static void leftAlign(JPanel panel) {
        for (Component c : panel.getComponents()) {
            ((JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
        }
    }

    private void refreshQuestion() {
        updating = true;
        renderQuestion();
        updating = false;
        refreshNavigator();
    }

    private void refreshNavigator() {
        int count = 0;
        for (boolean b : answered) if (b) count++;

        answeredCounter.setText(count + " of " + TOTAL_QUESTIONS + " answered");
        progress.setValue(count);
        questionCounter.setText("Question " + (current + 1) + " of " + TOTAL_QUESTIONS);

        for (int i = 0; i < TOTAL_QUESTIONS; i++) {
            JButton b = navigatorButtons[i];
            boolean isCurrent = i == current;
            b.setBackground(isCurrent || answered[i] ? PRIMARY : LIGHT_BLUE);
            b.setForeground(isCurrent || answered[i] ? WHITE : MUTED);
            b.setEnabled(!submitted);
        }
    }

    private void goTo(int index) {
        if (index < 0 || index >= TOTAL_QUESTIONS || submitted) return;
        current = index;
        refreshQuestion();
    }

    private void tick() {
        if (submitted || secondsLeft <= 0) return;
        secondsLeft--;
        timerLabel.setText(formatTime(secondsLeft));
        if (secondsLeft == 0) openSubmitDialog();
    }

    private String formatTime(int total) {
        return String.format("%02d:%02d", total / 60, total % 60);
    }

    private void openSubmitDialog() {
        if (submitted) return;
        Window parent = SwingUtilities.getWindowAncestor(this);
        SubmitDialog dialog = new SubmitDialog(
                parent instanceof Frame ? (Frame) parent : null,
                formatTime(secondsLeft),
                countAnswered(),
                this::submitExam
        );
        dialog.setVisible(true);
    }

    private int countAnswered() {
        int count = 0;
        for (boolean b : answered) if (b) count++;
        return count;
    }

    private void submitExam() {
        submitted = true;
        timer.stop();
        savedLabel.setText("●  Submitted");
        submitHint.setText("Exam submitted.");
        refreshNavigator();

        removeAll();
        RoundedPanel done = card(16, WHITE);
        done.setLayout(new BoxLayout(done, BoxLayout.Y_AXIS));
        done.setAlignmentX(Component.CENTER_ALIGNMENT);
        done.setMaximumSize(new Dimension(560, 300));

        done.add(label("Exam submitted", bold(26), DARK));
        done.add(Box.createVerticalStrut(14));
        done.add(label(
                "Your answers have been recorded. Your score will be "
                        + "available shortly.",
                plain(14), MUTED));
        done.add(Box.createVerticalStrut(28));

        JButton back = primaryButton("Back to workspace");
        back.setAlignmentX(Component.LEFT_ALIGNMENT);
        back.setPreferredSize(new Dimension(190, 44));
        back.addActionListener(e -> resetPrototype());
        done.add(back);

        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBackground(BACKGROUND);
        wrapper.add(done);
        add(wrapper, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    /** Lets the prototype be replayed without restarting the application. */
    private void resetPrototype() {
        submitted = false;
        secondsLeft = START_SECONDS;
        timerLabel.setText(formatTime(secondsLeft));
        for (int i = 0; i < TOTAL_QUESTIONS; i++) answered[i] = false;
        answers[2] = null;
        savedLabel.setText("●  All changes saved");
        submitHint.setText("You'll confirm before submitting.");
        current = 0;

        removeAll();
        setLayout(new BorderLayout());
        add(createHeader(), BorderLayout.NORTH);

        JPanel page = new JPanel();
        page.setBackground(BACKGROUND);
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        page.setBorder(new EmptyBorder(24, 40, 36, 40));
        page.add(createMetaBar());
        page.add(Box.createVerticalStrut(22));
        page.add(createContentRow());
        page.add(Box.createVerticalStrut(20));
        page.add(createNavigationRow());

        JScrollPane scroll = new JScrollPane(page);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BACKGROUND);
        scroll.setHorizontalScrollBarPolicy(
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);

        refreshQuestion();
        timer.start();
        revalidate();
        repaint();
    }

    // ---------------------------------------------------------------------
    // CUSTOM COMPONENTS
    // ---------------------------------------------------------------------

    /** Rounded radio row with a highlighted border when selected. */
    private static class OptionPanel extends JPanel {
        private final JRadioButton radio;
        private boolean selected;

        OptionPanel(JRadioButton radio, boolean selected) {
            this.radio = radio;
            this.selected = selected;
            setOpaque(false);
            setLayout(new FlowLayout(FlowLayout.LEFT, 14, 12));
            setCursor(new Cursor(Cursor.HAND_CURSOR));
        }

        void setSelected(boolean value) {
            selected = value;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(selected ? LIGHT_BLUE : WHITE);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g2.setColor(selected ? PRIMARY : BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Modal confirmation dialog shown over a dimmed background. */
    private static class SubmitDialog extends JDialog {
        SubmitDialog(Frame owner, String time, int answered,
                     Runnable onConfirm) {
            super(owner, true);
            setUndecorated(true);
            setSize(owner == null ? new Dimension(1000, 700) : owner.getSize());
            setLocationRelativeTo(owner);

            JPanel overlay = new JPanel(new GridBagLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    g.setColor(new Color(20, 40, 75, 85));
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
            };
            overlay.setOpaque(false);
            setContentPane(overlay);

            RoundedPanel card = card(16, WHITE);
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setPreferredSize(new Dimension(530, 378));
            card.setBorder(new EmptyBorder(28, 30, 22, 30));

            JLabel badge = new JLabel("BEFORE YOU SUBMIT") {
                @Override
                protected void paintComponent(Graphics g) {
                    // background painted by the opaque label below
                    super.paintComponent(g);
                }
            };
            badge.setFont(bold(10));
            badge.setForeground(AMBER_TEXT);
            badge.setOpaque(true);
            badge.setBackground(AMBER_BG);
            badge.setBorder(new EmptyBorder(7, 11, 7, 11));
            badge.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(badge);

            card.add(Box.createVerticalStrut(20));
            card.add(centered(label("Ready to submit?", bold(27), DARK)));
            card.add(Box.createVerticalStrut(14));

            JPanel message = new JPanel();
            message.setOpaque(false);
            message.setLayout(new BoxLayout(message, BoxLayout.Y_AXIS));
            JLabel line1 = label("You have answered " + answered
                    + " of 20 questions.", plain(13), MUTED);
            JLabel line2 = label((20 - answered)
                    + " unanswered questions will receive zero marks.",
                    plain(13), MUTED);
            line1.setAlignmentX(Component.CENTER_ALIGNMENT);
            line2.setAlignmentX(Component.CENTER_ALIGNMENT);
            message.add(line1);
            message.add(Box.createVerticalStrut(5));
            message.add(line2);
            message.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(centered(message));
            card.add(Box.createVerticalStrut(20));

            RoundedPanel note = card(10, new Color(244, 247, 253));
            note.setLayout(new BoxLayout(note, BoxLayout.Y_AXIS));
            note.setAlignmentX(Component.LEFT_ALIGNMENT);
            note.setMaximumSize(new Dimension(Integer.MAX_VALUE, 78));
            note.setBorder(new EmptyBorder(14, 16, 14, 16));
            note.add(label("You cannot change your answers after submission.",
                    plain(12), MUTED));
            note.add(Box.createVerticalStrut(6));
            note.add(label("Your score will be available immediately.",
                    plain(12), MUTED));
            card.add(note);

            card.add(Box.createVerticalStrut(24));

            JPanel buttons = new JPanel(new GridLayout(1, 2, 12, 0));
            buttons.setOpaque(false);
            buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
            buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));

            JButton keep = secondaryButton("Keep working");
            keep.setPreferredSize(new Dimension(220, 46));
            keep.addActionListener(e -> dispose());

            JButton confirm = primaryButton("Submit exam");
            confirm.addActionListener(e -> {
                dispose();
                onConfirm.run();
            });

            buttons.add(keep);
            buttons.add(confirm);
            card.add(buttons);

            card.add(Box.createVerticalStrut(16));
            card.add(centered(label(
                    time + " remaining - The timer is still running.",
                    plain(11), FAINT)));

            add(card);
            pack();
            setSize(owner == null ? new Dimension(1000, 700) : owner.getSize());
            setLocationRelativeTo(owner);
        }

        private static Component centered(Component c) {
            JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            wrapper.setOpaque(false);
            wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
            wrapper.add(c);
            return wrapper;
        }
    }

    private static Question[] buildQuestions() {
        String[] mcq = {"A", "B", "C", "D"};
        return new Question[]{
                new Question("Multiple choice",
                        "Which key uniquely identifies each record?",
                        "Choose one answer.",
                        new String[]{"A  Foreign key", "B  Composite key",
                                "C  Primary key", "D  Index"}, 5),
                new Question("Multiple choice",
                        "Which SQL clause filters grouped records?",
                        "Choose one answer.",
                        new String[]{"A  WHERE", "B  HAVING",
                                "C  ORDER BY", "D  GROUP BY"}, 5),
                new Question("Multiple choice",
                        "Which key uniquely identifies each record "
                                + "in a relational table?",
                        "Choose one answer.",
                        new String[]{"A  Foreign key", "B  Composite key",
                                "C  Primary key", "D  Index"}, 5),
                new Question("Multiple choice",
                        "What does ACID stand for?",
                        "Choose one answer.",
                        new String[]{"A  Atomicity, Consistency, Isolation, "
                                + "Durability", "B  Accuracy, Consistency, "
                                + "Integrity, Durability",
                                "C  Atomicity, Commit, Isolation, Data",
                                "D  Access, Consistency, Index, Durability"}, 5),
                new Question("Multiple choice",
                        "Which normal form removes transitive dependencies?",
                        "Choose one answer.",
                        new String[]{"A  1NF", "B  2NF", "C  3NF", "D  BCNF"}, 5),
                new Question("Multiple choice",
                        "Which join returns all rows from the left table?",
                        "Choose one answer.",
                        new String[]{"A  INNER JOIN", "B  LEFT JOIN",
                                "C  CROSS JOIN", "D  FULL JOIN"}, 5),
                new Question("Multiple choice",
                        "Which command removes a table structure?",
                        "Choose one answer.",
                        new String[]{"A  DELETE", "B  TRUNCATE",
                                "C  DROP", "D  REMOVE"}, 5),
                new Question("Short answer",
                        "Explain the purpose of normalization "
                                + "in a relational database.",
                        "Write your answer in the space below.",
                        null, 5),
                new Question("Short answer",
                        "Describe what a transaction is and why it matters.",
                        "Write your answer in the space below.",
                        null, 5),
                new Question("Multiple choice",
                        "Which index type allows duplicate values?",
                        "Choose one answer.",
                        new String[]{"A  Unique index", "B  Clustered index",
                                "C  Non-unique index", "D  Primary index"}, 5),
                new Question("Short answer",
                        "Explain the difference between a primary and a "
                                + "foreign key.",
                        "Write your answer in the space below.",
                        null, 5),
                new Question("Multiple choice",
                        "Which isolation level prevents dirty reads?",
                        "Choose one answer.",
                        new String[]{"A  READ UNCOMMITTED",
                                "B  READ COMMITTED",
                                "C  SERIALIZABLE", "D  All of the above"}, 5),
                new Question("Short answer",
                        "What is a view and when would you use one?",
                        "Write your answer in the space below.",
                        null, 5),
                new Question("Multiple choice",
                        "Which SQL statement creates a new table?",
                        "Choose one answer.",
                        new String[]{"A  MAKE TABLE", "B  NEW TABLE",
                                "C  CREATE TABLE", "D  ADD TABLE"}, 5),
                new Question("Short answer",
                        "Describe an insertion anomaly.",
                        "Write your answer in the space below.",
                        null, 5),
                new Question("Multiple choice",
                        "What does the GROUP BY clause do?",
                        "Choose one answer.",
                        new String[]{"A  Sorts rows", "B  Groups rows",
                                "C  Filters rows", "D  Joins tables"}, 5),
                new Question("Short answer",
                        "Explain referential integrity.",
                        "Write your answer in the space below.",
                        null, 5),
                new Question("Multiple choice",
                        "Which data type stores exact decimal values?",
                        "Choose one answer.",
                        new String[]{"A  FLOAT", "B  DOUBLE",
                                "C  DECIMAL", "D  REAL"}, 5),
                new Question("Short answer",
                        "What is a deadlock and how can it be avoided?",
                        "Write your answer in the space below.",
                        null, 5),
                new Question("Multiple choice",
                        "Which clause limits the number of returned rows?",
                        "Choose one answer.",
                        new String[]{"A  TOP", "B  LIMIT",
                                "C  ROWNUM", "D  FETCH"}, 5)
        };
    }
}
