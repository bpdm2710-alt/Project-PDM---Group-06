package org.example.ui.panel;

import org.example.Service.InstructorService.*;
import org.example.Service.Page;
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

/** Instructor course tab "Statistics": topic averages, hardest questions, student marks. */
public class StatisticsTab extends JPanel implements Refreshable {

    private static final int PAGE_SIZE = 15;

    private final int courseId;
    private final ScrollableBody body = new ScrollableBody();
    private final StateView state = new StateView(body);

    private final JPanel marksHolder = new JPanel(new BorderLayout());
    private final JLabel pageLabel = label("", plain(12), MUTED);
    private final JButton prev = secondaryButton("← Prev");
    private final JButton next = secondaryButton("Next →");
    private final JButton allBtn = tabBtn();
    private final JButton doneBtn = tabBtn();
    private boolean completedOnly;
    private int page = 1;

    public StatisticsTab(int courseId) {
        this.courseId = courseId;
        setLayout(new BorderLayout());
        setBackground(BACKGROUND);
        body.setBorder(new EmptyBorder(20, 12, 30, 12));
        ScrollableBody holder = new ScrollableBody();
        holder.setLayout(new BorderLayout());
        holder.add(state, BorderLayout.CENTER);
        add(ScrollableBody.scroll(holder, BACKGROUND), BorderLayout.CENTER);
    }

    private static JButton tabBtn() {
        JButton b = primaryButton("");
        b.setPreferredSize(new Dimension(200, 36));
        return b;
    }

    @Override
    public void refresh() {
        state.load(() -> Services.instructor().getStatistics(courseId), this::render, null, null, null);
    }

    private void render(CourseStatistics s) {
        body.removeAll();
        body.add(left(label(Fmt.plural(s.completedExamCount(), "completed exam"), bold(16), DARK)));
        body.add(Box.createVerticalStrut(18));

        body.add(left(section("Average score by topic")));
        body.add(Box.createVerticalStrut(10));
        if (s.topicAverages().isEmpty()) {
            body.add(left(label("No graded attempts yet.", plain(12), MUTED)));
        }
        for (TopicAverage t : s.topicAverages()) {
            body.add(left(bar(t.topic(), t.averagePercent())));
            body.add(Box.createVerticalStrut(8));
        }
        body.add(Box.createVerticalStrut(18));

        body.add(left(section("Hardest questions")));
        body.add(Box.createVerticalStrut(10));
        if (s.hardestQuestions().isEmpty()) {
            body.add(left(label("Not enough data yet.", plain(12), MUTED)));
        }
        for (HardQuestion q : s.hardestQuestions()) {
            body.add(left(hardRow(q)));
            body.add(Box.createVerticalStrut(8));
        }
        body.add(Box.createVerticalStrut(18));

        body.add(left(section("Student marks")));
        body.add(Box.createVerticalStrut(10));
        body.add(left(marksSection()));
        completedOnly = false;
        page = 1;
        loadMarks();

        body.revalidate();
        body.repaint();
    }

    private JComponent marksSection() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tabs.setOpaque(false);
        allBtn.addActionListener(e -> {
            completedOnly = false;
            page = 1;
            loadMarks();
        });
        doneBtn.addActionListener(e -> {
            completedOnly = true;
            page = 1;
            loadMarks();
        });
        tabs.add(allBtn);
        tabs.add(doneBtn);
        p.add(tabs, BorderLayout.NORTH);
        marksHolder.setOpaque(false);
        marksHolder.setPreferredSize(new Dimension(600, 360));
        p.add(marksHolder, BorderLayout.CENTER);
        JPanel pager = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        pager.setOpaque(false);
        prev.addActionListener(e -> {
            page--;
            loadMarks();
        });
        next.addActionListener(e -> {
            page++;
            loadMarks();
        });
        pager.add(prev);
        pager.add(pageLabel);
        pager.add(next);
        p.add(pager, BorderLayout.SOUTH);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 480));
        return p;
    }

    private void loadMarks() {
        boolean only = completedOnly;
        int p = page;
        AsyncTask.run(() -> Services.instructor().listStudentMarks(courseId, only, p, PAGE_SIZE),
                this::renderMarks,
                err -> {
                    marksHolder.removeAll();
                    marksHolder.add(label(err.getMessage(), plain(12), new Color(205, 55, 55)), BorderLayout.NORTH);
                    marksHolder.revalidate();
                    marksHolder.repaint();
                });
    }

    private void renderMarks(StudentMarkTable t) {
        allBtn.setText("All students (" + t.totalStudents() + ")");
        doneBtn.setText("Completed all exams (" + t.completedAllStudents() + ")");
        styleTab(allBtn, !completedOnly);
        styleTab(doneBtn, completedOnly);

        List<String> cols = new ArrayList<>();
        cols.add("Student");
        for (ExamColumn c : t.exams()) {
            cols.add(c.title());
        }
        cols.add("Average");
        cols.add("Done");
        DefaultTableModel model = new DefaultTableModel(cols.toArray(), 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        for (StudentMarkRow r : t.rows().items()) {
            List<Object> row = new ArrayList<>();
            row.add(r.name());
            for (ExamScore s : r.scores()) {
                row.add(s == null || s.score() == null ? "—"
                        : Fmt.marks(s.score()) + " / " + Fmt.marks(s.maxScore()));
            }
            row.add(Fmt.percent(r.averagePercent()));
            row.add(r.completedCount() + "/" + r.totalCount());
            model.addRow(row.toArray());
        }
        JTable table = new JTable(model);
        table.setRowHeight(32);
        table.setFont(plain(12));
        table.getTableHeader().setFont(bold(12));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        marksHolder.removeAll();
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(BORDER));
        marksHolder.add(sp, BorderLayout.CENTER);
        marksHolder.revalidate();
        marksHolder.repaint();

        Page<StudentMarkRow> pg = t.rows();
        pageLabel.setText("Page " + pg.page() + " of " + pg.totalPages());
        prev.setEnabled(pg.page() > 1);
        next.setEnabled(pg.page() < pg.totalPages());
    }

    private static void styleTab(JButton b, boolean active) {
        b.setBackground(active ? PRIMARY : LIGHT_BLUE);
        b.setForeground(active ? WHITE : PRIMARY);
    }

    // ---- small widgets ----------------------------------------------------

    private static JLabel section(String text) {
        return label(text, bold(18), DARK);
    }

    private static JComponent bar(String name, BigDecimal percent) {
        RoundedPanel row = new RoundedPanel(12, WHITE, BORDER);
        row.setLayout(new BorderLayout(14, 0));
        row.setBorder(new EmptyBorder(10, 16, 10, 16));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        JLabel n = label(name, plain(13), DARK);
        n.setPreferredSize(new Dimension(200, 22));
        row.add(n, BorderLayout.WEST);
        int value = percent == null ? 0 : Math.max(0, Math.min(100, percent.intValue()));
        JComponent track = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int h = 10;
                int y = (getHeight() - h) / 2;
                g2.setColor(LIGHT_BLUE);
                g2.fillRoundRect(0, y, getWidth(), h, h, h);
                g2.setColor(PRIMARY);
                g2.fillRoundRect(0, y, getWidth() * value / 100, h, h, h);
                g2.dispose();
            }
        };
        row.add(track, BorderLayout.CENTER);
        JLabel v = label(Fmt.percent(percent), bold(13), PRIMARY);
        v.setPreferredSize(new Dimension(50, 22));
        v.setHorizontalAlignment(SwingConstants.RIGHT);
        row.add(v, BorderLayout.EAST);
        return row;
    }

    private static JComponent hardRow(HardQuestion q) {
        RoundedPanel row = new RoundedPanel(12, WHITE, BORDER);
        row.setLayout(new BorderLayout(14, 0));
        row.setBorder(new EmptyBorder(10, 16, 10, 16));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        String text = q.content().length() > 120 ? q.content().substring(0, 120) + "…" : q.content();
        row.add(label("<html>" + text.replace("&", "&amp;").replace("<", "&lt;") + "</html>", plain(13), DARK),
                BorderLayout.CENTER);
        row.add(label(Fmt.percent(q.correctPercent()) + " correct", bold(12), new Color(205, 55, 55)),
                BorderLayout.EAST);
        return row;
    }

    private static <T extends JComponent> T left(T c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }
}
