package org.example.ui.panel;

import org.example.Service.InstructorService.*;
import org.example.Service.ServiceException;
import org.example.Service.Services;
import org.example.ui.components.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

import static org.example.ui.components.UITheme.*;

/** Instructor course tab "Exams": list, create/edit, delete, publish and build a paper. */
public class InstructorExamsTab extends JPanel implements Refreshable {

    private final int courseId;
    private final ScrollableBody body = new ScrollableBody();
    private final StateView state = new StateView(body);

    public InstructorExamsTab(int courseId) {
        this.courseId = courseId;
        setLayout(new BorderLayout());
        setBackground(BACKGROUND);
        body.setBorder(new EmptyBorder(20, 12, 30, 12));
        ScrollableBody holder = new ScrollableBody();
        holder.setLayout(new BorderLayout());
        holder.add(state, BorderLayout.CENTER);
        add(ScrollableBody.scroll(holder, BACKGROUND), BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        state.load(() -> Services.instructor().listExams(courseId), this::render, null, null, null);
    }

    private void render(List<ExamRow> exams) {
        body.removeAll();
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        bar.add(label(Fmt.plural(exams.size(), "exam"), bold(16), DARK), BorderLayout.WEST);
        JButton add = primaryButton("+ New exam");
        add.setPreferredSize(new Dimension(130, 40));
        add.addActionListener(e -> editExam(null));
        bar.add(add, BorderLayout.EAST);
        body.add(bar);
        body.add(Box.createVerticalStrut(16));

        if (exams.isEmpty()) {
            JLabel empty = label("No exams yet. Create the first one.", plain(13), MUTED);
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(empty);
        }
        for (ExamRow e : exams) {
            body.add(row(e));
            body.add(Box.createVerticalStrut(12));
        }
        body.revalidate();
        body.repaint();
    }

    private JComponent row(ExamRow e) {
        RoundedPanel row = new RoundedPanel(16, WHITE, BORDER);
        row.setLayout(new BorderLayout(16, 0));
        row.setBorder(new EmptyBorder(16, 20, 16, 20));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        JPanel tagRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tagRow.setOpaque(false);
        tagRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        tagRow.add(e.status() == ExamStatus.PUBLISHED ? greenTag("PUBLISHED")
                : tag(e.status() == ExamStatus.DRAFT ? "DRAFT" : "COMPLETED"));
        info.add(tagRow);
        info.add(Box.createVerticalStrut(7));
        JLabel title = label(e.title(), bold(15), DARK);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(title);
        info.add(Box.createVerticalStrut(5));
        JLabel meta = label(Fmt.dateTime(e.examDate()) + "  ·  " + Fmt.plural(e.questionCount(), "question")
                + "  ·  " + Fmt.marks(e.totalMarks()) + " marks", plain(12), MUTED);
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(meta);
        row.add(info, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton paper = secondaryButton("Build paper");
        paper.addActionListener(x -> buildPaper(e));
        JButton edit = secondaryButton("Edit");
        edit.addActionListener(x -> editExam(e.examId()));
        JButton publish = secondaryButton(e.status() == ExamStatus.DRAFT ? "Publish" : "Unpublish");
        publish.setEnabled(e.status() != ExamStatus.COMPLETED);
        publish.addActionListener(x -> setStatus(e));
        JButton delete = textButton("Delete");
        delete.setForeground(new Color(205, 55, 55));
        delete.addActionListener(x -> delete(e));
        for (JButton b : new JButton[]{paper, edit, publish, delete}) {
            actions.add(b);
        }
        JPanel east = new JPanel(new GridBagLayout());
        east.setOpaque(false);
        east.add(actions);
        row.add(east, BorderLayout.EAST);
        return row;
    }

    // ---- actions ----------------------------------------------------------

    private void setStatus(ExamRow e) {
        ExamStatus target = e.status() == ExamStatus.DRAFT ? ExamStatus.PUBLISHED : ExamStatus.DRAFT;
        run(() -> {
            Services.instructor().setExamStatus(e.examId(), target);
            return Boolean.TRUE;
        });
    }

    private void delete(ExamRow e) {
        if (!Dialogs.confirm(this, "Delete exam", "Delete \"" + e.title() + "\"? This cannot be undone.")) {
            return;
        }
        run(() -> {
            Services.instructor().deleteExam(e.examId());
            return Boolean.TRUE;
        });
    }

    private void buildPaper(ExamRow e) {
        new PaperBuilderDialog(this, courseId, e.examId(), e.title(), this::refresh).setVisible(true);
    }

    private void run(AsyncTask.Work<Boolean> work) {
        AsyncTask.run(work, ok -> refresh(), (ServiceException err) -> Dialogs.error(this, err));
    }

    private void editExam(Integer examId) {
        if (examId == null) {
            new ExamFormDialog(this, courseId, null, this::refresh).setVisible(true);
            return;
        }
        AsyncTask.run(() -> Services.instructor().getExam(examId),
                detail -> new ExamFormDialog(this, courseId, detail, this::refresh).setVisible(true),
                err -> Dialogs.error(this, err));
    }

    // ---- create / edit dialog --------------------------------------------

    static final class ExamFormDialog extends JDialog {

        private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        ExamFormDialog(Component parent, int courseId, ExamDetail existing, Runnable onSaved) {
            super(SwingUtilities.getWindowAncestor(parent),
                    existing == null ? "New exam" : "Edit exam", ModalityType.APPLICATION_MODAL);
            JTextField title = new JTextField(existing == null ? "" : existing.title(), 24);
            JTextField date = new JTextField(existing == null || existing.examDate() == null
                    ? "" : existing.examDate().format(FORMAT), 24);
            JTextField duration = new JTextField(existing == null ? "60" : String.valueOf(existing.durationMin()), 24);
            JTextField pass = new JTextField(existing == null ? "" : Fmt.marks(existing.passMark()), 24);
            JTextField attempts = new JTextField(existing == null ? "1" : String.valueOf(existing.maxAttempts()), 24);

            JPanel form = new JPanel(new GridBagLayout());
            form.setBorder(new EmptyBorder(20, 24, 12, 24));
            addRow(form, 0, "Title", title);
            addRow(form, 1, "Date & time (yyyy-MM-dd HH:mm, blank = not scheduled)", date);
            addRow(form, 2, "Duration (minutes)", duration);
            addRow(form, 3, "Pass mark (% of total marks, 0-100)", pass);
            addRow(form, 4, "Max attempts", attempts);

            JButton save = primaryButton("Save");
            JButton cancel = secondaryButton("Cancel");
            cancel.addActionListener(e -> dispose());
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
            buttons.add(cancel);
            buttons.add(save);

            save.addActionListener(e -> {
                try {
                    String t = title.getText().trim();
                    if (t.isEmpty()) {
                        throw new IllegalArgumentException("Title is required.");
                    }
                    LocalDateTime when = date.getText().isBlank() ? null
                            : LocalDateTime.parse(date.getText().trim(), FORMAT);
                    int mins = Integer.parseInt(duration.getText().trim());
                    BigDecimal pm = new BigDecimal(pass.getText().trim());
                    int max = Integer.parseInt(attempts.getText().trim());
                    ExamDraft draft = new ExamDraft(existing == null ? null : existing.examId(),
                            courseId, t, when, mins, pm, max);
                    save.setEnabled(false);
                    AsyncTask.run(() -> Services.instructor().saveExam(draft),
                            id -> {
                                dispose();
                                onSaved.run();
                            },
                            err -> {
                                save.setEnabled(true);
                                Dialogs.error(this, err);
                            });
                } catch (DateTimeParseException ex) {
                    JOptionPane.showMessageDialog(this, "Use the date format yyyy-MM-dd HH:mm, e.g. 2026-12-01 09:00.");
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Duration, pass mark and max attempts must be numbers.");
                } catch (IllegalArgumentException ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage());
                }
            });

            setLayout(new BorderLayout());
            add(form, BorderLayout.CENTER);
            add(buttons, BorderLayout.SOUTH);
            pack();
            setLocationRelativeTo(parent);
        }

        private static void addRow(JPanel p, int row, String text, JComponent field) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row;
            c.anchor = GridBagConstraints.WEST;
            c.insets = new Insets(6, 0, 6, 14);
            p.add(label(text, plain(12), MUTED), c);
            c.gridx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            p.add(field, c);
        }
    }
}
