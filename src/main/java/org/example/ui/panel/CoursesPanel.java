package org.example.ui.panel;

import org.example.ui.UiKit;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class CoursesPanel extends JPanel {
    public CoursesPanel() {
        setLayout(new BorderLayout());
        setBackground(UiKit.BACKGROUND);
        
        JLabel title = new JLabel("Overview");
        title.setFont(new Font("SansSerif", Font.BOLD, 30));
        title.setForeground(UiKit.DARK);
        title.setBorder(new EmptyBorder(40, 40, 40, 40));
        
        add(title, BorderLayout.NORTH);
    }
}