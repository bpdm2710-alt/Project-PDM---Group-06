package org.example.ui.panel;

import org.example.Service.InstructorService.*;
import org.example.Service.Page;
import org.example.Service.ServiceException;
import org.example.Service.Services;
import org.example.ui.components.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.example.ui.components.UITheme.*;

/**
 * Build the question paper of an exam: search the course question bank on the
 * left, pick questions with marks on the right, order them, save.
 */
public class PaperBuilderDialog extends JDialog {

    private static final class Pick {
        final int questionId;
        final String content;
        BigDecimal marks;

        Pick(int questionId, String content, BigDecimal marks) {
            this.questionId = questionId;
            this.content = content;
            this.marks = marks;
        }
    }

    private final int courseId;
    private final int examId;
    private final Runnable onSaved;
    private final DefaultListModel<QuestionBankItem> bankModel = new DefaultListModel<>();
    private final DefaultListModel<Pick> pickModel = new DefaultListModel<>();
    private final JTextField search = new JTextField();
    private final JLabel total = label("", bold(13), DARK);
    private final JLabel status = label("", plain(12), MUTED);
    private Integer paperKey; // null = new paper

    public PaperBuilderDialog(Component parent, int courseId, int examId, String examTitle, Runnable onSaved) {
        super(SwingUtilities.getWindowAncestor(parent), "Build paper - " + examTitle,
                ModalityType.APPLICATION_MODAL);
        this.courseId = courseId;
        this.examId = examId;
        this.onSaved = onSaved;
        setSize(980, 620);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(0, 8));
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(16, 20, 12, 20));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, bankSide(), paperSide());
        split.setResizeWeight(0.5);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(status, BorderLayout.WEST);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton cancel = secondaryButton("Close");
        cancel.addActionListener(e -> dispose());
        JButton save = primaryButton("Save paper");
        save.addActionListener(e -> save(save));
        btns.add(cancel);
        btns.add(save);
        bottom.add(btns, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        loadExistingPaper();
        searchBank();
    }

    private JComponent bankSide() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.add(label("Question bank", bold(15), DARK), BorderLayout.NORTH);
        JPanel mid = new JPanel(new BorderLayout(0, 8));
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.add(search, BorderLayout.CENTER);
        JButton go = secondaryButton("Search");
        go.addActionListener(e -> searchBank());
        search.addActionListener(e -> searchBank());
        bar.add(go, BorderLayout.EAST);
        mid.add(bar, BorderLayout.NORTH);
        JList<QuestionBankItem> list = new JList<>(bankModel);
        list.setCellRenderer((l, v, i, sel, f) -> {
            JLabel lb = label("<html><b>" + (v.type() == org.example.Service.QuestionType.MCQ ? "MCQ" : "Short")
                    + "</b> · " + esc(v.topic()) + " · level " + v.difficulty() + "<br>" + esc(v.content()) + "</html>",
                    plain(12), DARK);
            lb.setOpaque(true);
            lb.setBackground(sel ? LIGHT_BLUE : WHITE);
            lb.setBorder(new EmptyBorder(6, 8, 6, 8));
            return lb;
        });
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    addPick(list.getSelectedValue());
                }
            }
        });
        mid.add(new JScrollPane(list), BorderLayout.CENTER);
        p.add(mid, BorderLayout.CENTER);
        JButton add = primaryButton("Add selected →");
        add.addActionListener(e -> addPick(list.getSelectedValue()));
        p.add(add, BorderLayout.SOUTH);
        p.setBorder(new EmptyBorder(0, 0, 0, 10));
        return p;
    }

    private JComponent paperSide() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        JPanel head = new JPanel(new BorderLayout());
        head.add(label("Paper", bold(15), DARK), BorderLayout.WEST);
        head.add(total, BorderLayout.EAST);
        p.add(head, BorderLayout.NORTH);
        JList<Pick> list = new JList<>(pickModel);
        list.setCellRenderer((l, v, i, sel, f) -> {
            JLabel lb = label("<html>" + (i + 1) + ". " + esc(v.content) + "<br><b>"
                    + Fmt.marks(v.marks) + " marks</b></html>", plain(12), DARK);
            lb.setOpaque(true);
            lb.setBackground(sel ? LIGHT_BLUE : WHITE);
            lb.setBorder(new EmptyBorder(6, 8, 6, 8));
            return lb;
        });
        p.add(new JScrollPane(list), BorderLayout.CENTER);
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton up = secondaryButton("↑");
        JButton down = secondaryButton("↓");
        JButton marks = secondaryButton("Set marks");
        JButton remove = secondaryButton("Remove");
        up.addActionListener(e -> move(list, -1));
        down.addActionListener(e -> move(list, 1));
        remove.addActionListener(e -> {
            int i = list.getSelectedIndex();
            if (i >= 0) {
                pickModel.remove(i);
                updateTotal();
            }
        });
        marks.addActionListener(e -> {
            Pick pick = list.getSelectedValue();
            if (pick == null) {
                return;
            }
            String in = JOptionPane.showInputDialog(this, "Marks for this question:", Fmt.marks(pick.marks));
            if (in == null) {
                return;
            }
            try {
                BigDecimal m = new BigDecimal(in.trim());
                if (m.signum() <= 0) {
                    throw new NumberFormatException();
                }
                pick.marks = m;
                list.repaint();
                updateTotal();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Marks must be a number greater than 0.");
            }
        });
        for (JButton b : new JButton[]{up, down, marks, remove}) {
            tools.add(b);
        }
        p.add(tools, BorderLayout.SOUTH);
        p.setBorder(new EmptyBorder(0, 10, 0, 0));
        updateTotal();
        return p;
    }

    private void move(JList<Pick> list, int delta) {
        int i = list.getSelectedIndex();
        int j = i + delta;
        if (i < 0 || j < 0 || j >= pickModel.size()) {
            return;
        }
        Pick a = pickModel.get(i);
        pickModel.set(i, pickModel.get(j));
        pickModel.set(j, a);
        list.setSelectedIndex(j);
    }

    private void addPick(QuestionBankItem q) {
        if (q == null) {
            return;
        }
        for (int i = 0; i < pickModel.size(); i++) {
            if (pickModel.get(i).questionId == q.questionId()) {
                status.setText("That question is already in the paper.");
                return;
            }
        }
        pickModel.addElement(new Pick(q.questionId(), q.content(), BigDecimal.ONE));
        status.setText(" ");
        updateTotal();
    }

    private void updateTotal() {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < pickModel.size(); i++) {
            sum = sum.add(pickModel.get(i).marks);
        }
        total.setText(pickModel.size() + " questions · " + Fmt.marks(sum) + " marks");
    }

    // ---- data -------------------------------------------------------------

    private void loadExistingPaper() {
        AsyncTask.run(() -> Services.instructor().getExam(examId), detail -> {
            if (!detail.papers().isEmpty()) {
                PaperDetail paper = detail.papers().get(0);
                paperKey = paper.paperKey();
                for (PaperQuestion q : paper.questions()) {
                    pickModel.addElement(new Pick(q.questionId(), q.content(), q.marks()));
                }
                updateTotal();
            }
        }, err -> status.setText(err.getMessage()));
    }

    private void searchBank() {
        String kw = search.getText().trim();
        QuestionBankFilter filter = new QuestionBankFilter(kw.isEmpty() ? null : kw, null, null, null);
        AsyncTask.run(() -> Services.instructor().searchQuestionBank(courseId, filter, 1, 50),
                (Page<QuestionBankItem> page) -> {
                    bankModel.clear();
                    page.items().forEach(bankModel::addElement);
                    if (page.items().isEmpty()) {
                        status.setText("No questions match.");
                    }
                },
                err -> status.setText(err.getMessage()));
    }

    private void save(JButton button) {
        if (pickModel.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Add at least one question to the paper.");
            return;
        }
        List<PaperQuestionDraft> qs = new ArrayList<>();
        for (int i = 0; i < pickModel.size(); i++) {
            Pick p = pickModel.get(i);
            qs.add(new PaperQuestionDraft(p.questionId, i + 1, p.marks));
        }
        PaperDraft draft = new PaperDraft(paperKey, examId, qs);
        button.setEnabled(false);
        AsyncTask.run(() -> Services.instructor().savePaper(draft), key -> {
            dispose();
            onSaved.run();
        }, (ServiceException err) -> {
            button.setEnabled(true);
            Dialogs.error(this, err);
        });
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        String t = s.length() > 110 ? s.substring(0, 110) + "…" : s;
        return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
