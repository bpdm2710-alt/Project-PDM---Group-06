package org.example.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class LoginFrame extends JFrame {
    private JTextField emailField;
    private JPasswordField passwordField;

    public LoginFrame() {
        setTitle("Examly - Sign In");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(UiKit.BACKGROUND);
        root.setBorder(new EmptyBorder(40, 40, 40, 40));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0; gbc.weighty = 1; gbc.fill = GridBagConstraints.BOTH;

        // LEFT SIDE (Blue Promo)
        gbc.gridx = 0; gbc.weightx = 0.45; gbc.insets = new Insets(0, 0, 0, 20);
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(new Color(220, 237, 255));
        leftPanel.setBorder(new EmptyBorder(40, 40, 40, 40));
        
        JLabel logoLeft = new JLabel("examly.");
        logoLeft.setFont(new Font("SansSerif", Font.BOLD, 28));
        logoLeft.setForeground(UiKit.PRIMARY);
        leftPanel.add(logoLeft, BorderLayout.NORTH);

        JPanel promoContent = new JPanel();
        promoContent.setOpaque(false);
        promoContent.setLayout(new BoxLayout(promoContent, BoxLayout.Y_AXIS));
        JLabel headline = new JLabel("<html>A clearer path<br>to your next<br>achievement.</html>");
        headline.setFont(new Font("SansSerif", Font.BOLD, 40));
        headline.setForeground(UiKit.DARK);
        promoContent.add(Box.createVerticalStrut(60));
        promoContent.add(headline);
        leftPanel.add(promoContent, BorderLayout.CENTER);
        root.add(leftPanel, gbc);

        // RIGHT SIDE (Login Form)
        gbc.gridx = 1; gbc.weightx = 0.55; gbc.insets = new Insets(0, 20, 0, 0);
        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setOpaque(false);
        
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setOpaque(false);
        form.setPreferredSize(new Dimension(360, 400));

        JLabel title = new JLabel("Sign in to examly.");
        title.setFont(new Font("SansSerif", Font.BOLD, 32));
        title.setForeground(UiKit.DARK);

        emailField = new JTextField();
        emailField.setPreferredSize(new Dimension(360, 44));
        emailField.setMaximumSize(new Dimension(360, 44));
        
        passwordField = new JPasswordField();
        passwordField.setPreferredSize(new Dimension(360, 44));
        passwordField.setMaximumSize(new Dimension(360, 44));

        JButton loginBtn = new JButton("Sign in  →");
        loginBtn.setPreferredSize(new Dimension(360, 44));
        loginBtn.setMaximumSize(new Dimension(360, 44));
        loginBtn.setBackground(UiKit.PRIMARY);
        loginBtn.setForeground(Color.WHITE);
        loginBtn.setFocusPainted(false);
        loginBtn.addActionListener(e -> handleLogin());

        form.add(title);
        form.add(Box.createVerticalStrut(40));
        form.add(new JLabel("Email (type 'chen' for Instructor)"));
        form.add(Box.createVerticalStrut(5));
        form.add(emailField);
        form.add(Box.createVerticalStrut(15));
        form.add(new JLabel("Password"));
        form.add(Box.createVerticalStrut(5));
        form.add(passwordField);
        form.add(Box.createVerticalStrut(30));
        form.add(loginBtn);
        
        rightPanel.add(form);
        root.add(rightPanel, gbc);

        setContentPane(root);
    }

    private void handleLogin() {
        String email = emailField.getText().toLowerCase().trim();
        boolean isInstructor = email.contains("chen") || email.contains("instructor");

        dispose();
        SwingUtilities.invokeLater(() -> {
            if (isInstructor) {
                new InstructorDashBoard("Dr. Sarah Chen").setVisible(true);
            } else {
                new StudentDashBoard("Alex Morgan").setVisible(true);
            }
        });
    }
}