package org.example.ui.panel;

import org.example.Service.InstructorService.*;
import org.example.Service.Page;
import org.example.Service.QuestionType;
import org.example.Service.ServiceException;
import org.example.Service.Services;
import org.example.session.Session;
import org.example.ui.components.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.example.ui.components.UITheme.*;

/** Instructor course tab "Question bank": search, create, edit, delete questions. */
public class QuestionBankTab extends JPanel implements Refreshable {

    private static final int PAGE_SIZE = 10;

    private final int courseId;
    private final JTextField keyword = new JTextField();
    private final JComboBox<Object> topicBox = new JComboBox<>(new Object[]{"All topics"});
    private final JComboBox<Object> typeBox = new JComboBox<>(new Object[]{"All types", QuestionType.MCQ, QuestionType.SHORT_ANSWER});
    private final JComboBox<Object> levelBox = new JComboBox<>(new Object[]{"All levels", 1, 2, 3, 4, 5});
    private final ScrollableBody body = new ScrollableBody();
    private final StateView state = new StateView(body);
    private final JLabel pageLabel = label("", plain(12), MUTED);
    private final JButton prev = secondaryButton("← Prev");
    private final JButton next = secondaryButton("Next →");
    private int page = 1;
    private boolean topicsLoaded;
    private boolean updating;

    public QuestionBankTab(int courseId) {
        this.courseId = courseId;
        setLayout(new BorderLayout(0, 12));
        setBackground(BACKGROUND);
        setBorder(new EmptyBorder(20, 12, 20, 12));
        add(toolbar(), BorderLayout.NORTH);
        body.setBorder(new EmptyBorder(0, 0, 10, 0));
        add(ScrollableBody.scroll(wrap(), BACKGROUND), BorderLayout.CENTER);
        add(pager(), BorderLayout.SOUTH);
    }

    private JComponent wrap() {
        ScrollableBody holder = new ScrollableBody();
        holder.setLayout(new BorderLayout());
        holder.add(state, BorderLayout.CENTER);
        return holder;
    }

    private JComponent toolbar() {
        JPanel bar = new JPanel(new GridBagLayout());
        bar.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 0, 10);
        c.weightx = 1;
        keyword.setPreferredSize(new Dimension(220, 38));
        keyword.addActionListener(e -> search());
        bar.add(keyword, c);
        c.weightx = 0;
        for (JComboBox<?> b : new JComboBox<?>[]{topicBox, typeBox, levelBox}) {
            b.setPreferredSize(new Dimension(150, 38));
            b.addActionListener(e -> {
                if (!updating) {
                    search();
                }
            });
            bar.add(b, c);
        }
        JButton go = secondaryButton("Search");
        go.setPreferredSize(new Dimension(90, 38));
        go.addActionListener(e -> search());
        bar.add(go, c);
        JButton add = primaryButton("+ New question");
        add.setPreferredSize(new Dimension(150, 38));
        add.addActionListener(e -> edit(null));
        c.insets = new Insets(0, 0, 0, 0);
        bar.add(add, c);
        return bar;
    }

    private JComponent pager() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        p.setOpaque(false);
        prev.addActionListener(e -> {
            page--;
            load();
        });
        next.addActionListener(e -> {
            page++;
            load();
        });
        p.add(prev);
        p.add(pageLabel);
        p.add(next);
        return p;
    }

    @Override
    public void refresh() {
        if (!topicsLoaded) {
            AsyncTask.run(() -> Services.instructor().listTopics(courseId), topics -> {
                updating = true;
                for (TopicOption t : topics) {
                    topicBox.addItem(t);
                }
                updating = false;
                topicsLoaded = true;
            }, e -> { });
        }
        load();
    }

    private void search() {
        page = 1;
        load();
    }

    private QuestionBankFilter filter() {
        String kw = keyword.getText().trim();
        Object t = topicBox.getSelectedItem();
        Object ty = typeBox.getSelectedItem();
        Object lv = levelBox.getSelectedItem();
        return new QuestionBankFilter(kw.isEmpty() ? null : kw,
                t instanceof TopicOption o ? o.topicId() : null,
                ty instanceof QuestionType q ? q : null,
                lv instanceof Integer i ? i : null);
    }

    private void load() {
        QuestionBankFilter f = filter();
        int p = page;
        state.load(() -> Services.instructor().searchQuestionBank(courseId, f, p, PAGE_SIZE),
                this::render, r -> r.items().isEmpty() && p == 1,
                "No questions found", "Create a question or change the filters.");
    }

    private void render(Page<QuestionBankItem> result) {
        body.removeAll();
        for (QuestionBankItem q : result.items()) {
            body.add(row(q));
            body.add(Box.createVerticalStrut(10));
        }
        pageLabel.setText("Page " + result.page() + " of " + result.totalPages()
                + "  ·  " + Fmt.plural((int) result.totalItems(), "question"));
        prev.setEnabled(result.page() > 1);
        next.setEnabled(result.page() < result.totalPages());
        body.revalidate();
        body.repaint();
    }

    private JComponent row(QuestionBankItem q) {
        RoundedPanel row = new RoundedPanel(14, WHITE, BORDER);
        row.setLayout(new BorderLayout(14, 0));
        row.setBorder(new EmptyBorder(14, 18, 14, 18));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        JLabel meta = label(q.type().label() + "  ·  " + q.topic() + "  ·  level " + q.difficulty(),
                bold(11), PRIMARY);
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);
        String text = q.content().length() > 140 ? q.content().substring(0, 140) + "…" : q.content();
        JLabel content = label("<html>" + text.replace("&", "&amp;").replace("<", "&lt;") + "</html>",
                plain(13), DARK);
        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(meta);
        info.add(Box.createVerticalStrut(6));
        info.add(content);
        row.add(info, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton edit = secondaryButton("Edit");
        edit.addActionListener(e -> edit(q.questionId()));
        JButton del = textButton("Delete");
        del.setForeground(new Color(205, 55, 55));
        del.addActionListener(e -> delete(q));
        actions.add(edit);
        actions.add(del);
        JPanel east = new JPanel(new GridBagLayout());
        east.setOpaque(false);
        east.add(actions);
        row.add(east, BorderLayout.EAST);
        return row;
    }

    private void delete(QuestionBankItem q) {
        if (!Dialogs.confirm(this, "Delete question", "Delete this question? This cannot be undone.")) {
            return;
        }
        AsyncTask.run(() -> {
            Services.instructor().deleteQuestion(q.questionId());
            return Boolean.TRUE;
        }, ok -> load(), (ServiceException e) -> Dialogs.error(this, e));
    }

    private void edit(Integer questionId) {
        AsyncTask.run(() -> {
            List<TopicOption> topics = Services.instructor().listTopics(courseId);
            QuestionDraft draft = questionId == null ? null : Services.instructor().getQuestion(questionId);
            return new Object[]{topics, draft};
        }, data -> {
            @SuppressWarnings("unchecked")
            List<TopicOption> topics = (List<TopicOption>) data[0];
            new QuestionFormDialog(this, topics, (QuestionDraft) data[1], this::load).setVisible(true);
        }, e -> Dialogs.error(this, e));
    }

    // ---- create / edit dialog --------------------------------------------

    static final class QuestionFormDialog extends JDialog {

        private final JComboBox<TopicOption> topic;
        private final JComboBox<QuestionType> type = new JComboBox<>(QuestionType.values());
        private final JSpinner level = new JSpinner(new SpinnerNumberModel(1, 1, 5, 1));
        private final JTextArea content = new JTextArea(4, 40);
        private final JTextField answerKey = new JTextField();
        private final JPanel optionList = new JPanel();
        private final List<JTextField> optionFields = new ArrayList<>();
        private final ButtonGroup correctGroup = new ButtonGroup();
        private final List<JRadioButton> correctButtons = new ArrayList<>();
        private final JPanel mcqPanel = new JPanel(new BorderLayout());
        private final JPanel shortPanel = new JPanel(new BorderLayout());

        QuestionFormDialog(Component parent, List<TopicOption> topics, QuestionDraft existing, Runnable onSaved) {
            super(SwingUtilities.getWindowAncestor(parent),
                    existing == null ? "New question" : "Edit question", ModalityType.APPLICATION_MODAL);
            topic = new JComboBox<>(topics.toArray(new TopicOption[0]));
            topic.setRenderer((l, v, i, s, f) -> new JLabel(v == null ? "" : v.name()));

            JPanel form = new JPanel();
            form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
            form.setBorder(new EmptyBorder(18, 22, 8, 22));
            form.add(line("Topic", topic));
            form.add(line("Type", type));
            form.add(line("Difficulty (1-5)", level));
            JLabel cl = label("Question", plain(12), MUTED);
            cl.setAlignmentX(Component.LEFT_ALIGNMENT);
            form.add(cl);
            content.setLineWrap(true);
            content.setWrapStyleWord(true);
            JScrollPane cs = new JScrollPane(content);
            cs.setAlignmentX(Component.LEFT_ALIGNMENT);
            form.add(cs);
            form.add(Box.createVerticalStrut(10));

            optionList.setLayout(new BoxLayout(optionList, BoxLayout.Y_AXIS));
            mcqPanel.add(label("Options (select the correct one)", plain(12), MUTED), BorderLayout.NORTH);
            mcqPanel.add(optionList, BorderLayout.CENTER);
            JButton addOpt = secondaryButton("+ Add option");
            addOpt.addActionListener(e -> {
                addOption("", false);
                revalidate();
                pack();
            });
            JPanel addWrap = new JPanel(new FlowLayout(FlowLayout.LEFT));
            addWrap.add(addOpt);
            mcqPanel.add(addWrap, BorderLayout.SOUTH);
            mcqPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            shortPanel.add(label("Model answer (used for grading)", plain(12), MUTED), BorderLayout.NORTH);
            shortPanel.add(answerKey, BorderLayout.CENTER);
            shortPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            form.add(mcqPanel);
            form.add(shortPanel);

            if (existing != null) {
                for (int i = 0; i < topics.size(); i++) {
                    if (topics.get(i).topicId() == existing.topicId()) {
                        topic.setSelectedIndex(i);
                    }
                }
                type.setSelectedItem(existing.type());
                type.setEnabled(false);
                level.setValue(existing.difficulty());
                content.setText(existing.content());
                if (existing.type() == QuestionType.MCQ) {
                    existing.options().forEach(o -> addOption(o.content(), o.correct()));
                } else {
                    answerKey.setText(existing.answerKey() == null ? "" : existing.answerKey());
                }
            }
            if (optionFields.isEmpty()) {
                for (int i = 0; i < 4; i++) {
                    addOption("", false);
                }
            }
            type.addActionListener(e -> toggleType());
            toggleType();

            JButton save = primaryButton("Save");
            JButton cancel = secondaryButton("Cancel");
            cancel.addActionListener(e -> dispose());
            save.addActionListener(e -> save(existing, save, onSaved));
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
            buttons.add(cancel);
            buttons.add(save);

            setLayout(new BorderLayout());
            add(form, BorderLayout.CENTER);
            add(buttons, BorderLayout.SOUTH);
            setMinimumSize(new Dimension(560, 0));
            pack();
            setLocationRelativeTo(parent);
        }

        private static JComponent line(String text, JComponent field) {
            JPanel p = new JPanel(new BorderLayout(10, 0));
            p.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.setBorder(new EmptyBorder(0, 0, 8, 0));
            JLabel l = label(text, plain(12), MUTED);
            l.setPreferredSize(new Dimension(110, 28));
            p.add(l, BorderLayout.WEST);
            p.add(field, BorderLayout.CENTER);
            p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            return p;
        }

        private void addOption(String text, boolean correct) {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setBorder(new EmptyBorder(3, 0, 3, 0));
            JRadioButton r = new JRadioButton();
            r.setSelected(correct);
            correctGroup.add(r);
            correctButtons.add(r);
            JTextField f = new JTextField(text);
            optionFields.add(f);
            row.add(r, BorderLayout.WEST);
            row.add(f, BorderLayout.CENTER);
            optionList.add(row);
        }

        private void toggleType() {
            boolean mcq = type.getSelectedItem() == QuestionType.MCQ;
            mcqPanel.setVisible(mcq);
            shortPanel.setVisible(!mcq);
            pack();
        }

        private void save(QuestionDraft existing, JButton save, Runnable onSaved) {
            QuestionType t = (QuestionType) type.getSelectedItem();
            String text = content.getText().trim();
            if (text.isEmpty() || topic.getSelectedItem() == null) {
                JOptionPane.showMessageDialog(this, "Choose a topic and write the question.");
                return;
            }
            List<OptionDraft> options = new ArrayList<>();
            String key = null;
            if (t == QuestionType.MCQ) {
                int correct = 0;
                for (int i = 0; i < optionFields.size(); i++) {
                    String o = optionFields.get(i).getText().trim();
                    if (o.isEmpty()) {
                        continue;
                    }
                    boolean c = correctButtons.get(i).isSelected();
                    correct += c ? 1 : 0;
                    options.add(new OptionDraft(o, c));
                }
                if (options.size() < 2 || correct != 1) {
                    JOptionPane.showMessageDialog(this, "A multiple choice question needs at least 2 options and exactly one correct option.");
                    return;
                }
            } else {
                key = answerKey.getText().trim();
                if (key.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Enter the model answer.");
                    return;
                }
            }
            QuestionDraft draft = new QuestionDraft(existing == null ? null : existing.questionId(),
                    ((TopicOption) topic.getSelectedItem()).topicId(), t, text,
                    (Integer) level.getValue(), options, key);
            int instructorId = Session.user().userId();
            save.setEnabled(false);
            AsyncTask.run(() -> Services.instructor().saveQuestion(instructorId, draft),
                    id -> {
                        dispose();
                        onSaved.run();
                    },
                    err -> {
                        save.setEnabled(true);
                        Dialogs.error(this, err);
                    });
        }
    }
}
