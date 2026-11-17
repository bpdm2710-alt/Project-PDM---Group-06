package org.example.ui.panel;

import org.example.ui.UiKit;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class StudentOverviewPanel extends JPanel {
    public StudentOverviewPanel() {
        setLayout(new BorderLayout());
        setBackground(UiKit.BACKGROUND);
        
        JPanel content = new JPanel();
        content.setBackground(UiKit.BACKGROUND);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(30, 40, 40, 40));

        // Header
        JLabel title = new JLabel("Overview");
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(UiKit.DARK);
        
        JLabel sub = new JLabel("A little preparation. A lot of possibility.");
        sub.setFont(new Font("SansSerif", Font.PLAIN, 13));
        sub.setForeground(UiKit.MUTED);
        
        content.add(title);
        content.add(Box.createVerticalStrut(5));
        content.add(sub);
        content.add(Box.createVerticalStrut(25));

        // Welcome Banner
        JPanel banner = new JPanel(new BorderLayout());
        banner.setBackground(new Color(220, 237, 255));
        banner.setBorder(new EmptyBorder(24, 24, 24, 24));
        banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));
        
        JPanel bannerLeft = new JPanel();
        bannerLeft.setOpaque(false);
        bannerLeft.setLayout(new BoxLayout(bannerLeft, BoxLayout.Y_AXIS));
        
        bannerLeft.add(UiKit.tag("YOUR LEARNING, AT A GLANCE"));
        bannerLeft.add(Box.createVerticalStrut(12));
        
        JLabel welcomeText = new JLabel("Ready for your next challenge, Alex?");
        welcomeText.setFont(new Font("SansSerif", Font.BOLD, 24));
        welcomeText.setForeground(UiKit.DARK);
        bannerLeft.add(welcomeText);
        bannerLeft.add(Box.createVerticalStrut(8));
        
        JLabel descText = new JLabel("You have 2 exams this week. Let's make them count.");
        descText.setFont(new Font("SansSerif", Font.PLAIN, 13));
        descText.setForeground(UiKit.MUTED);
        bannerLeft.add(descText);
        
        JButton viewExams = new JButton("View my exams →");
        viewExams.setBackground(UiKit.PRIMARY);
        viewExams.setForeground(Color.WHITE);
        viewExams.setFocusPainted(false);
        
        banner.add(bannerLeft, BorderLayout.CENTER);
        banner.add(viewExams, BorderLayout.EAST);
        
        content.add(banner);
        content.add(Box.createVerticalStrut(20));

        // Stats Row
        JPanel stats = new JPanel(new GridLayout(1, 3, 20, 0));
        stats.setOpaque(false);
        stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        stats.add(createStatCard("Enrolled courses", "04", "All courses active"));
        stats.add(createStatCard("Upcoming exams", "02", "Next: today at 2:00 PM"));
        stats.add(createStatCard("Average score", "85%", "Across 6 attempts"));
        content.add(stats);
        
        JScrollPane sp = new JScrollPane(content);
        sp.setBorder(null);
        sp.getViewport().setBackground(UiKit.BACKGROUND);
        add(sp, BorderLayout.CENTER);
    }
    
    private JPanel createStatCard(String title, String value, String desc) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(UiKit.WHITE);
        p.setBorder(new EmptyBorder(15, 20, 15, 20));
        
        JLabel t = new JLabel(title);
        t.setFont(new Font("SansSerif", Font.PLAIN, 11));
        t.setForeground(UiKit.MUTED);
        
        JLabel v = new JLabel(value);
        v.setFont(new Font("SansSerif", Font.BOLD, 28));
        v.setForeground(UiKit.DARK);
        
        JLabel d = new JLabel(desc);
        d.setFont(new Font("SansSerif", Font.PLAIN, 10));
        d.setForeground(UiKit.MUTED);
        
        p.add(t, BorderLayout.NORTH);
        p.add(v, BorderLayout.WEST);
        p.add(d, BorderLayout.EAST);
        return p;
    }
}
