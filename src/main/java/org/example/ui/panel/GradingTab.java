package org.example.ui.panel;

import org.example.Service.InstructorService.PendingAnswer;
import org.example.Service.ServiceException;
import org.example.Service.Services;
import org.example.ui.components.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.example.ui.components.UITheme.*;

/**
 * Instructor course tab "Grading": short answers that did not match the answer key wait here
 * (ANSWER.MarkAwarded IS NULL) until the instructor gives a mark. The student's result shows
 * "grading" until every answer of the attempt has a mark.
 */
public class GradingTab extends JPanel implements Refreshable {

    private final int courseId;
    private final JPanel body = new JPanel(new BorderLayout(0, 14));
    private final StateView state = new StateView(body);

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Submitted", "Student", "Exam", "Question", "Max"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(model);
    private final JLabel countLabel = label("", bold(15), DARK);
    private final JTextArea questionArea = readOnlyArea();
    private final JTextArea answerArea = readOnlyArea();
    private final JTextArea keyArea = readOnlyArea();
    private final JTextField markField = new JTextField(6);
    private final JLabel maxLabel = label("", plain(12), MUTED);
    private final JButton fullBtn = secondaryButton("Full marks");
    private final JButton zeroBtn = secondaryButton("0");
    private final JButton saveBtn = primaryButton("Save mark");

    private List<PendingAnswer> rows = new ArrayList<>();

    public GradingTab(int courseId) {
        this.courseId = courseId;
        setLayout(new BorderLayout());
        setBackground(BACKGROUND);
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(20, 12, 20, 12));

        table.setRowHeight(28);
        table.setFont(plain(12));
        table.getTableHeader().setFont(bold(12));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelected();
            }
        });
        int[] widths = {130, 150, 200, 420, 60};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(900, 230));

        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setOpaque(false);
        top.add(countLabel, BorderLayout.NORTH);
        top.add(tableScroll, BorderLayout.CENTER);

        body.add(top, BorderLayout.NORTH);
        body.add(detailPanel(), BorderLayout.CENTER);
        add(state, BorderLayout.CENTER);

        fullBtn.addActionListener(e -> {
            PendingAnswer p = selected();
            if (p != null) {
                markField.setText(p.maxMarks().stripTrailingZeros().toPlainString());
            }
        });
        zeroBtn.addActionListener(e -> markField.setText("0"));
        saveBtn.addActionListener(e -> save());
        markField.addActionListener(e -> save());
        showSelected();
    }

    private JComponent detailPanel() {
        RoundedPanel card = card();
        card.setLayout(new GridBagLayout());
        card.setBorder(new EmptyBorder(16, 18, 16, 18));
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.fill = GridBagConstraints.BOTH;
        g.weightx = 1;
        g.insets = new Insets(0, 0, 6, 0);
        int y = 0;
        g.gridy = y++;
        card.add(label("Question", bold(12), MUTED), g);
        g.gridy = y++;
        g.weighty = 0.3;
        card.add(new JScrollPane(questionArea), g);
        g.weighty = 0;
        g.gridy = y++;
        card.add(label("Student's answer", bold(12), MUTED), g);
        g.gridy = y++;
        g.weighty = 0.5;
        card.add(new JScrollPane(answerArea), g);
        g.weighty = 0;
        g.gridy = y++;
        card.add(label("Answer key (reference)", bold(12), MUTED), g);
        g.gridy = y++;
        g.weighty = 0.2;
        card.add(new JScrollPane(keyArea), g);
        g.weighty = 0;

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.add(label("Mark:", bold(13), DARK));
        markField.setFont(plain(13));
        actions.add(markField);
        actions.add(maxLabel);
        actions.add(fullBtn);
        actions.add(zeroBtn);
        actions.add(saveBtn);
        g.gridy = y;
        g.insets = new Insets(8, 0, 0, 0);
        card.add(actions, g);
        return card;
    }

    private static JTextArea readOnlyArea() {
        JTextArea a = new JTextArea(3, 60);
        a.setEditable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setFont(plain(13));
        return a;
    }

    @Override
    public void refresh() {
        state.load(() -> Services.instructor().listPendingGrading(courseId), this::render,
                List::isEmpty, "Nothing to grade",
                "Short answers that do not match the answer key appear here after students submit.");
    }

    private void render(List<PendingAnswer> list) {
        rows = list;
        model.setRowCount(0);
        for (PendingAnswer p : list) {
            model.addRow(new Object[]{Fmt.dateTime(p.submittedAt()), p.studentName(), p.examTitle(),
                    p.question(), Fmt.marks(p.maxMarks())});
        }
        countLabel.setText(Fmt.plural(list.size(), "answer") + " waiting for a mark");
        if (!list.isEmpty()) {
            table.setRowSelectionInterval(0, 0);
        }
        showSelected();
    }

    private PendingAnswer selected() {
        int i = table.getSelectedRow();
        return i >= 0 && i < rows.size() ? rows.get(table.convertRowIndexToModel(i)) : null;
    }

    private void showSelected() {
        PendingAnswer p = selected();
        boolean has = p != null;
        questionArea.setText(has ? p.question() : "");
        answerArea.setText(has ? p.answerText() : "");
        keyArea.setText(has ? p.answerKey() : "");
        maxLabel.setText(has ? "/ " + Fmt.marks(p.maxMarks()) : "");
        markField.setText("");
        for (JComponent c : new JComponent[]{markField, fullBtn, zeroBtn, saveBtn}) {
            c.setEnabled(has);
        }
    }

    private void save() {
        PendingAnswer p = selected();
        if (p == null) {
            return;
        }
        BigDecimal mark;
        try {
            mark = new BigDecimal(markField.getText().trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            Dialogs.error(this, new ServiceException(ServiceException.Kind.VALIDATION,
                    "Please enter a number, e.g. 7.5"));
            return;
        }
        saveBtn.setEnabled(false);
        AsyncTask.run(() -> {
                    Services.instructor().gradeAnswer(p.ref(), mark);
                    return Boolean.TRUE;
                },
                ok -> refresh(),
                err -> {
                    saveBtn.setEnabled(true);
                    Dialogs.error(this, err);
                });
    }
}
