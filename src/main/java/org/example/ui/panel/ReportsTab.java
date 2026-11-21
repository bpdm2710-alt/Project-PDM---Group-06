package org.example.ui.panel;

import org.example.Service.InstructorService.ExamRow;
import org.example.Service.InstructorService.ReportKind;
import org.example.Service.InstructorService.ReportParams;
import org.example.Service.InstructorService.ReportTable;
import org.example.Service.ServiceException;
import org.example.Service.Services;
import org.example.ui.components.AsyncTask;
import org.example.ui.components.Dialogs;
import org.example.ui.components.Refreshable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

import static org.example.ui.components.UITheme.*;

/**
 * Instructor course tab "Reports": runs the analytical queries of PDMProjectQuery.sql (Q1..Q11)
 * through InstructorService.runReport and shows the result in a table.
 */
public class ReportsTab extends JPanel implements Refreshable {

    /** Item of the exam combo boxes. */
    private record ExamItem(int examId, String title) {
        @Override
        public String toString() {
            return "#" + examId + "  " + title;
        }
    }

    private final int courseId;
    private final JComboBox<ReportKind> kindBox = new JComboBox<>(ReportKind.values());
    private final JLabel description = label("", plain(12), MUTED);
    private final JLabel thresholdLabel = label("N:", bold(12), DARK);
    private final JTextField thresholdField = new JTextField(5);
    private final JLabel examsLabel = label("Exam A / B:", bold(12), DARK);
    private final JComboBox<ExamItem> examA = new JComboBox<>();
    private final JComboBox<ExamItem> examB = new JComboBox<>();
    private final JButton runBtn = primaryButton("Run");
    private final JLabel status = label(" ", plain(12), MUTED);
    private final DefaultTableModel model = new DefaultTableModel() {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private boolean examsLoaded;

    public ReportsTab(int courseId) {
        this.courseId = courseId;
        setLayout(new BorderLayout(0, 10));
        setBackground(BACKGROUND);
        setBorder(new EmptyBorder(20, 12, 20, 12));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        controls.setOpaque(false);
        kindBox.setFont(plain(13));
        controls.add(label("Query:", bold(13), DARK));
        controls.add(kindBox);
        controls.add(thresholdLabel);
        controls.add(thresholdField);
        controls.add(examsLabel);
        controls.add(examA);
        controls.add(examB);
        controls.add(runBtn);

        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.setOpaque(false);
        top.add(controls, BorderLayout.NORTH);
        top.add(description, BorderLayout.CENTER);
        top.add(status, BorderLayout.SOUTH);

        JTable table = new JTable(model);
        table.setRowHeight(26);
        table.setFont(plain(12));
        table.getTableHeader().setFont(bold(12));
        table.setAutoCreateRowSorter(true);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        kindBox.addActionListener(e -> updateControls());
        runBtn.addActionListener(e -> run());
        thresholdField.addActionListener(e -> run());
        updateControls();
    }

    @Override
    public void refresh() {
        if (!examsLoaded) {
            AsyncTask.run(() -> Services.instructor().listExams(courseId), this::fillExams, err -> { });
        }
    }

    private void fillExams(List<ExamRow> exams) {
        examsLoaded = true;
        examA.removeAllItems();
        examB.removeAllItems();
        for (ExamRow r : exams) {
            examA.addItem(new ExamItem(r.examId(), r.title()));
            examB.addItem(new ExamItem(r.examId(), r.title()));
        }
        if (examB.getItemCount() > 1) {
            examB.setSelectedIndex(1);
        }
    }

    private ReportKind kind() {
        return (ReportKind) kindBox.getSelectedItem();
    }

    private void updateControls() {
        ReportKind k = kind();
        description.setText(k.description());
        thresholdLabel.setVisible(k.usesThreshold());
        thresholdField.setVisible(k.usesThreshold());
        thresholdField.setText(k.usesThreshold() ? String.valueOf(k.defaultThreshold()) : "");
        thresholdLabel.setText(k == ReportKind.HIGH_ERROR_QUESTIONS ? "Error % >" : "At least:");
        examsLabel.setVisible(k.usesTwoExams());
        examA.setVisible(k.usesTwoExams());
        examB.setVisible(k.usesTwoExams());
        revalidate();
    }

    private void run() {
        ReportKind k = kind();
        Integer threshold = null;
        if (k.usesThreshold() && !thresholdField.getText().isBlank()) {
            try {
                threshold = Integer.valueOf(thresholdField.getText().trim());
            } catch (NumberFormatException ex) {
                Dialogs.error(this, new ServiceException(ServiceException.Kind.VALIDATION, "Please enter a whole number."));
                return;
            }
        }
        ExamItem a = (ExamItem) examA.getSelectedItem();
        ExamItem b = (ExamItem) examB.getSelectedItem();
        ReportParams p = new ReportParams(courseId, threshold, a == null ? null : a.examId(),
                b == null ? null : b.examId());
        runBtn.setEnabled(false);
        status.setText("Running…");
        AsyncTask.run(() -> Services.instructor().runReport(k, p), this::show, err -> {
            runBtn.setEnabled(true);
            status.setText(" ");
            Dialogs.error(this, err);
        });
    }

    private void show(ReportTable t) {
        runBtn.setEnabled(true);
        model.setDataVector(t.rows().stream().map(List::toArray).toArray(Object[][]::new),
                t.columns().toArray());
        status.setText(t.rows().size() + (t.rows().size() == 1 ? " row" : " rows"));
    }
}
