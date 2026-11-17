package org.example.ui.panel;

import org.example.ui.components.RoundedPanel;
import org.example.ui.components.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

import static org.example.ui.components.UITheme.*;

/**
 * Instructor "My courses" screen with the three tabs from the reference
 * design. Exams and Statistics are reproduced from images 1586 and 1585.
 * All content is mock data; actions only change local prototype state.
 */
public class InstructorCoursesPanel extends JPanel {

    private final CardLayout tabs = new CardLayout();
    private final JPanel tabContent = new JPanel(tabs);
    private final JButton[] tabButtons = new JButton[3];
    private final String[] tabNames = {"Question bank", "Exams", "Statistics"};

    public InstructorCoursesPanel() {
        setBackground(BACKGROUND);
        setLayout(new BorderLayout());

        JPanel page = new JPanel();
        page.setBackground(BACKGROUND);
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        page.setBorder(new EmptyBorder(30, 40, 32, 40));

        JPanel header = createHeader();
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        leftAlign(header);
        page.add(header);
        page.add(Box.createVerticalStrut(22));
        JPanel tabBar = createTabBar();
        tabBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        page.add(tabBar);
        page.add(Box.createVerticalStrut(22));

        tabContent.setBackground(BACKGROUND);
        tabContent.add(createQuestionBankPanel(), "Question bank");
        tabContent.add(createExamsPanel(), "Exams");
        tabContent.add(createStatisticsPanel(), "Statistics");
        tabContent.setAlignmentX(Component.LEFT_ALIGNMENT);
        tabs.show(tabContent, "Exams");
        tabButtons[1].setBackground(PRIMARY);
        tabButtons[1].setForeground(WHITE);
        page.add(tabContent);

        JScrollPane scroll = new JScrollPane(page);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setHorizontalScrollBarPolicy(
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);
    }

    // ---------------------------------------------------------------------
    // HEADER + TABS
    // ---------------------------------------------------------------------

    private JPanel createHeader() {
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        header.add(label("Database fundamentals", bold(30), DARK));
        JLabel subtitle = label("CS201  /  48 enrolled students", plain(13), MUTED);
        subtitle.setBorder(new EmptyBorder(6, 0, 0, 0));
        header.add(subtitle);
        return header;
    }

    private JPanel createTabBar() {
        JPanel bar = card(12, WHITE);
        bar.setLayout(new BorderLayout());
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        bar.setBorder(new EmptyBorder(0, 6, 0, 22));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        left.setOpaque(false);
        for (int i = 0; i < tabNames.length; i++) {
            JButton b = UITheme.secondaryButton(tabNames[i]);
            b.setFont(bold(12));
            b.setBackground(WHITE);
            b.setForeground(MUTED);
            b.setPreferredSize(new Dimension(150, 40));
            final int index = i;
            b.addActionListener(e -> selectTab(index));
            tabButtons[i] = b;
            left.add(b);
        }

        JButton back = textButton("←  My courses");
        back.setBorder(new EmptyBorder(0, 0, 0, 8));

        bar.add(left, BorderLayout.WEST);
        bar.add(back, BorderLayout.EAST);
        return bar;
    }

    /** BoxLayout centres children by default; the design is left aligned. */
    private static void leftAlign(JPanel panel) {
        for (Component c : panel.getComponents()) {
            ((JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
        }
    }

    private void selectTab(int index) {
        for (int i = 0; i < tabButtons.length; i++) {
            tabButtons[i].setBackground(i == index ? PRIMARY : WHITE);
            tabButtons[i].setForeground(i == index ? WHITE : MUTED);
        }
        tabs.show(tabContent, tabNames[index]);
    }

    // ---------------------------------------------------------------------
    // EXAMS TAB
    // ---------------------------------------------------------------------

    private JPanel createExamsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.weighty = 1;

        gbc.gridx = 0;
        gbc.weightx = 0.36;
        gbc.insets = new Insets(0, 0, 0, 20);
        panel.add(createExamListCard(), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.64;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(createEditExamCard(), gbc);

        return panel;
    }

    private JPanel examList;
    private JLabel examListMeta;
    private JLabel editStatus;
    private JTextField editTitle;
    private JLabel saveStatus;

    private JPanel createExamListCard() {
        RoundedPanel card = card(14, WHITE);
        card.setLayout(new BorderLayout());

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(label("Course exams", bold(17), DARK));
        examListMeta = label("3 published · 1 draft", plain(11), MUTED);
        examListMeta.setBorder(new EmptyBorder(5, 0, 18, 0));
        content.add(examListMeta);

        JButton create = primaryButton("+  Create exam");
        create.setAlignmentX(Component.LEFT_ALIGNMENT);
        create.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        create.setPreferredSize(new Dimension(280, 44));
        create.addActionListener(e -> addDraftExam());
        content.add(create);
        content.add(Box.createVerticalStrut(18));

        examList = new JPanel();
        examList.setOpaque(false);
        examList.setLayout(new BoxLayout(examList, BoxLayout.Y_AXIS));
        content.add(examList);

        addExamItem("Midterm examination", "16 Nov 2026 · 2:00 PM",
                "20 questions · 100 marks", "Published", true);
        addExamItem("Practice quiz", "12 Nov 2026 · 10:00 AM",
                "20 questions · 100 marks", "Completed", false);
        addExamItem("Introduction quiz", "21 Oct 2026 · 10:00 AM",
                "20 questions · 100 marks", "Completed", false);
        addExamItem("Final examination", "Not scheduled",
                "0 questions · 0 marks", "Draft", false);

        leftAlign(content);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private void addExamItem(String title, String date, String detail,
                             String status, boolean selected) {
        RoundedPanel item = new RoundedPanel(10,
                selected ? LIGHT_BLUE : new Color(248, 251, 255), BORDER);
        item.setLayout(new BoxLayout(item, BoxLayout.Y_AXIS));
        item.setBorder(new EmptyBorder(14, 16, 14, 16));
        item.setAlignmentX(Component.LEFT_ALIGNMENT);
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 118));
        item.setCursor(new Cursor(Cursor.HAND_CURSOR));

        item.add(label(title, bold(13), DARK));
        item.add(Box.createVerticalStrut(7));
        item.add(label(date, plain(11), MUTED));
        item.add(Box.createVerticalStrut(4));
        item.add(label(detail, plain(11), MUTED));
        item.add(Box.createVerticalStrut(8));

        JLabel statusLabel = label(status, bold(10),
                status.equals("Published") ? GREEN : MUTED);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        item.add(statusLabel);

        item.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                for (Component c : examList.getComponents()) {
                    if (c instanceof RoundedPanel) {
                        ((RoundedPanel) c).setCardBackground(
                                new Color(248, 251, 255));
                    }
                }
                item.setCardBackground(LIGHT_BLUE);
                editTitle.setText(title);
                editStatus.setText(status);
                editStatus.setForeground(
                        status.equals("Published") ? GREEN : MUTED);
                saveStatus.setText("");
            }
        });

        examList.add(item);
        examList.add(Box.createVerticalStrut(10));
    }

    private void addDraftExam() {
        addExamItem("New draft exam", "Not scheduled",
                "0 questions · 0 marks", "Draft", false);
        examListMeta.setText("3 published · 2 drafts");
        examList.revalidate();
        examList.repaint();
    }

    private JPanel createEditExamCard() {
        RoundedPanel card = card(14, WHITE);
        card.setLayout(new BorderLayout());

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(label("Edit examination", bold(17), DARK),
                BorderLayout.WEST);
        editStatus = greenTag("Published");
        JPanel badgeHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        badgeHolder.setOpaque(false);
        badgeHolder.add(editStatus);
        titleRow.add(badgeHolder, BorderLayout.EAST);
        content.add(titleRow);
        content.add(Box.createVerticalStrut(20));

        content.add(field("Exam title", editTitle = new JTextField(
                "Midterm examination")));
        content.add(Box.createVerticalStrut(14));

        JPanel grid = new JPanel(new GridLayout(2, 3, 14, 14));
        grid.setOpaque(false);
        grid.add(field("Date", new JTextField("16 / 11 / 2026")));
        grid.add(field("Start time", new JTextField("2:00 PM")));
        grid.add(field("Duration (minutes)", new JTextField("45")));
        grid.add(field("Attempts allowed", new JTextField("2")));
        grid.add(field("Passing score (%)", new JTextField("50")));
        grid.add(field("Total marks", new JTextField("100")));
        content.add(grid);
        content.add(Box.createVerticalStrut(22));

        content.add(createPaperPanel());
        leftAlign(content);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private JPanel field(String caption, JTextField field) {
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));

        JLabel title = label(caption, plain(11), MUTED);
        title.setBorder(new EmptyBorder(0, 0, 6, 0));
        wrapper.add(title);

        field.setFont(plain(13));
        field.setForeground(DARK);
        field.setBackground(new Color(247, 250, 254));
        field.setBorder(new EmptyBorder(0, 12, 0, 12));
        field.setPreferredSize(new Dimension(120, 40));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        wrapper.add(field);
        return wrapper;
    }

    private JPanel createPaperPanel() {
        JPanel paper = new JPanel();
        paper.setOpaque(false);
        paper.setLayout(new BoxLayout(paper, BoxLayout.Y_AXIS));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(label("Build your paper", bold(15), DARK), BorderLayout.WEST);
        JButton search = textButton("Search question bank ↗");
        head.add(search, BorderLayout.EAST);
        paper.add(head);

        JLabel sub = label("20 selected from 32 questions", plain(11), MUTED);
        sub.setBorder(new EmptyBorder(5, 0, 14, 0));
        paper.add(sub);

        JPanel table = new JPanel(new GridBagLayout());
        table.setBackground(new Color(248, 251, 255));
        table.setBorder(BorderFactory.createLineBorder(BORDER));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(10, 12, 10, 12);
        gbc.anchor = GridBagConstraints.WEST;

        addCell(table, gbc, 0, 0, 0.08, label("SELECT", bold(9), MUTED));
        addCell(table, gbc, 1, 0, 0.62, label("QUESTION", bold(9), MUTED));
        addCell(table, gbc, 2, 0, 0.18, label("TYPE", bold(9), MUTED));
        addCell(table, gbc, 3, 0, 0.12, label("MARKS", bold(9), MUTED));

        String[][] rows = {
                {"Which key uniquely identifies each record?", "MCQ", "5"},
                {"Which SQL clause filters grouped records?", "MCQ", "5"},
                {"Explain the purpose of normalization.", "Short answer", "5"},
                {"What does ACID stand for?", "MCQ", "5"}
        };
        for (int i = 0; i < rows.length; i++) {
            JCheckBox check = new JCheckBox();
            check.setSelected(true);
            check.setOpaque(false);
            check.setFocusPainted(false);
            check.setCursor(new Cursor(Cursor.HAND_CURSOR));

            addCell(table, gbc, 0, i + 1, 0.08, check);
            addCell(table, gbc, 1, i + 1, 0.62,
                    label(rows[i][0], plain(12), DARK));
            addCell(table, gbc, 2, i + 1, 0.18,
                    label(rows[i][1], plain(11), MUTED));
            addCell(table, gbc, 3, i + 1, 0.12,
                    label(rows[i][2], plain(12), DARK));
        }
        paper.add(table);
        paper.add(Box.createVerticalStrut(12));

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(label("Showing 1–4 of 20 selected", plain(10), MUTED),
                BorderLayout.WEST);
        JPanel pager = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        pager.setOpaque(false);
        pager.add(textButton("‹"));
        pager.add(label("1 / 5", plain(11), MUTED));
        pager.add(textButton("›"));
        footer.add(pager, BorderLayout.EAST);
        paper.add(footer);
        paper.add(Box.createVerticalStrut(18));

        JPanel actions = new JPanel(new BorderLayout());
        actions.setOpaque(false);
        JButton saveDraft = secondaryButton("Save draft");
        saveDraft.setPreferredSize(new Dimension(140, 44));
        actions.add(saveDraft, BorderLayout.WEST);

        saveStatus = label("", plain(11), GREEN);
        JPanel middle = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        middle.setOpaque(false);
        middle.add(saveStatus);
        middle.add(label("20 questions · 100 marks", plain(11), MUTED));
        actions.add(middle, BorderLayout.CENTER);

        JButton save = primaryButton("Save changes");
        save.setPreferredSize(new Dimension(170, 44));
        save.addActionListener(e -> {
            saveStatus.setText("✓ Changes saved   ");
            saveDraft.setEnabled(true);
        });
        actions.add(save, BorderLayout.EAST);
        paper.add(actions);
        leftAlign(paper);

        return paper;
    }

    private void addCell(JPanel panel, GridBagConstraints gbc, int x, int y,
                         double weight, Component c) {
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.weightx = weight;
        panel.add(c, gbc);
    }

    // ---------------------------------------------------------------------
    // STATISTICS TAB
    // ---------------------------------------------------------------------

    private JPanel createStatisticsPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JPanel stats = new JPanel(new GridLayout(1, 3, 18, 0));
        stats.setOpaque(false);
        stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, 104));
        stats.add(statCard("Enrolled students", "48", null));
        stats.add(statCard("Average course score", "82%", PRIMARY));
        stats.add(statCard("Attempted every completed exam", "40 / 48", null));
        panel.add(stats);
        panel.add(Box.createVerticalStrut(20));

        JPanel middle = new JPanel(new GridBagLayout());
        middle.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1;
        gbc.gridx = 0;
        gbc.weightx = 1;
        gbc.insets = new Insets(0, 0, 0, 18);
        middle.add(createTopicCard(), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.insets = new Insets(0, 0, 0, 0);
        middle.add(createSupportCard(), gbc);
        panel.add(middle);
        panel.add(Box.createVerticalStrut(20));

        panel.add(createMarksCard());
        leftAlign(panel);
        return panel;
    }

    private JPanel statCard(String caption, String value, Color valueColor) {
        RoundedPanel card = card(14, WHITE);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.add(label(caption, plain(12), MUTED));
        card.add(Box.createVerticalStrut(16));
        card.add(label(value, bold(30),
                valueColor == null ? DARK : valueColor));
        return card;
    }

    private JPanel createTopicCard() {
        RoundedPanel card = card(14, WHITE);
        card.setLayout(new BorderLayout());

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(label("Average score by topic", bold(16), DARK));
        JLabel sub = label("Based on 2 completed exams", plain(11), MUTED);
        sub.setBorder(new EmptyBorder(5, 0, 20, 0));
        header.add(sub);

        JPanel bars = new JPanel();
        bars.setOpaque(false);
        bars.setLayout(new BoxLayout(bars, BoxLayout.Y_AXIS));
        String[][] data = {
                {"Keys", "88%", "88"},
                {"SQL", "76%", "76"},
                {"Design", "81%", "81"},
                {"Transactions", "83%", "83"}
        };
        for (String[] row : data) {
            JPanel line = new JPanel(new GridBagLayout());
            line.setOpaque(false);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;

            gbc.gridx = 0;
            gbc.weightx = 0.19;
            line.add(label(row[0], plain(12), MUTED), gbc);

            gbc.gridx = 1;
            gbc.weightx = 0.68;
            gbc.insets = new Insets(0, 12, 0, 12);
            line.add(new ScoreBar(Integer.parseInt(row[2])), gbc);

            gbc.gridx = 2;
            gbc.weightx = 0.13;
            gbc.insets = new Insets(0, 0, 0, 0);
            line.add(label(row[1], bold(12), DARK), gbc);

            bars.add(line);
            bars.add(Box.createVerticalStrut(18));
        }

        leftAlign(header);
        leftAlign(bars);
        card.add(header, BorderLayout.NORTH);
        card.add(bars, BorderLayout.CENTER);
        return card;
    }

    private JPanel createSupportCard() {
        RoundedPanel card = card(14, WHITE);
        card.setLayout(new BorderLayout());

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(label("Where students need support", bold(16), DARK));
        JLabel sub = label("Hardest questions · ranked by accuracy",
                plain(11), MUTED);
        sub.setBorder(new EmptyBorder(5, 0, 18, 0));
        header.add(sub);

        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));

        String[][] rows = {
                {"01", "SQL · Grouped query filtering", "42%"},
                {"02", "Design · Normalization", "54%"},
                {"03", "Transactions · Isolation", "58%"}
        };
        for (String[] row : rows) {
            JPanel item = new JPanel(new BorderLayout(14, 0));
            item.setOpaque(false);
            item.setAlignmentX(Component.LEFT_ALIGNMENT);

            item.add(label(row[0], bold(13), PRIMARY), BorderLayout.WEST);

            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.add(label(row[1], bold(12), DARK));
            text.add(Box.createVerticalStrut(5));
            text.add(label("answered correctly", plain(10), MUTED));
            item.add(text, BorderLayout.CENTER);

            item.add(label(row[2], bold(13), DARK), BorderLayout.EAST);
            list.add(item);
            list.add(Box.createVerticalStrut(20));
        }

        leftAlign(header);
        leftAlign(list);
        card.add(header, BorderLayout.NORTH);
        card.add(list, BorderLayout.CENTER);
        return card;
    }

    private JPanel createMarksCard() {
        RoundedPanel card = card(14, WHITE);
        card.setLayout(new BorderLayout());

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(label("Student marks", bold(16), DARK), BorderLayout.WEST);
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filters.setOpaque(false);
        JButton all = UITheme.secondaryButton("All students (48)");
        JButton completed = UITheme.secondaryButton("Completed all exams (40)");
        all.setPreferredSize(new Dimension(150, 34));
        completed.setPreferredSize(new Dimension(205, 34));
        filters.add(all);
        filters.add(completed);
        header.add(filters, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        JPanel table = new JPanel(new GridBagLayout());
        table.setBackground(new Color(248, 251, 255));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(10, 12, 10, 12);
        gbc.anchor = GridBagConstraints.WEST;

        String[] columns = {"STUDENT", "INTRODUCTION QUIZ", "PRACTICE QUIZ",
                "COURSE AVERAGE", "COMPLETION"};
        double[] weights = {0.32, 0.17, 0.15, 0.17, 0.19};
        for (int i = 0; i < columns.length; i++) {
            addCell(table, gbc, i, 0, weights[i],
                    label(columns[i], bold(9), MUTED));
        }

        String[][] students = {
                {"Alex Morgan", "87 / 100", "85 / 100", "86%", "2 / 2 exams"},
                {"Emma Thompson", "80 / 100", "78 / 100", "79%", "2 / 2 exams"},
                {"Liam Anderson", "94 / 100", "90 / 100", "92%", "2 / 2 exams"},
                {"Sophia Martinez", "88 / 100", "82 / 100", "85%", "2 / 2 exams"}
        };
        for (int i = 0; i < students.length; i++) {
            gbc.gridy = i + 1;
            for (int j = 0; j < columns.length; j++) {
                Color color = j == 3 ? PRIMARY : DARK;
                Font font = (j == 0 || j == 3) ? bold(12) : plain(11);
                addCell(table, gbc, j, i + 1, weights[j],
                        label(students[i][j], font, color));
            }
        }
        card.add(table, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(12, 0, 0, 0));
        footer.add(label("Showing 1–4 of 48 students", plain(10), MUTED),
                BorderLayout.WEST);
        JPanel pager = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        pager.setOpaque(false);
        pager.add(textButton("←"));
        pager.add(label("1 / 12", plain(11), MUTED));
        pager.add(textButton("→"));
        footer.add(pager, BorderLayout.EAST);
        card.add(footer, BorderLayout.SOUTH);

        return card;
    }

    // ---------------------------------------------------------------------
    // QUESTION BANK TAB (placeholder - not shown in the reference images)
    // ---------------------------------------------------------------------

    private JPanel createQuestionBankPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(BACKGROUND);
        panel.add(label("Question bank will be implemented later.",
                bold(18), MUTED));
        return panel;
    }

    /** Horizontal progress bar used by the Statistics tab. */
    private static class ScoreBar extends JPanel {
        private final int value;

        ScoreBar(int value) {
            this.value = value;
            setOpaque(false);
            setPreferredSize(new Dimension(200, 12));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            int h = getHeight();
            g2.setColor(new Color(229, 236, 247));
            g2.fillRoundRect(0, 0, getWidth(), h, h, h);
            g2.setColor(PRIMARY);
            g2.fillRoundRect(0, 0, (int) (getWidth() * value / 100.0),
                    h, h, h);
            g2.dispose();
        }
    }
}
