package org.example.ui;

import org.example.Service.AuthService;
import org.example.Service.ServiceException;
import org.example.Service.Services;
import org.example.session.Session;
import org.example.ui.components.AsyncTask;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class LoginFrame extends JFrame {

    // ===== Color palette =====
    private static final Color BACKGROUND = new Color(242, 247, 253);
    private static final Color PRIMARY = new Color(30, 115, 235);
    private static final Color DARK = new Color(25, 48, 81);
    private static final Color MUTED = new Color(103, 124, 151);
    private static final Color LIGHT_BLUE = new Color(220, 237, 255);
    private static final Color BORDER = new Color(220, 230, 242);

    // ===== Components =====
    private JTextField emailField;
    private JPasswordField passwordField;
    private JButton showPasswordButton;
    private JButton signInButton;
    private JLabel errorLabel;

    private boolean passwordVisible = false;

    public LoginFrame() {
        setTitle("Examly - Sign In");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1920, 1080);
        setMinimumSize(new Dimension(1280, 720));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(BACKGROUND);
        root.setBorder(new EmptyBorder(24, 24, 24, 24));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;

        // Left panel
        gbc.gridx = 0;
        gbc.weightx = 0.48;
        gbc.insets = new Insets(0, 0, 0, 20);
        root.add(createIntroductionPanel(), gbc);

        // Right panel
        gbc.gridx = 1;
        gbc.weightx = 0.52;
        gbc.insets = new Insets(0, 20, 0, 0);
        root.add(createLoginPanel(), gbc);

        setContentPane(root);
    }

    // =====================================================
    // LEFT PANEL
    // =====================================================

    private JPanel createIntroductionPanel() {
        RoundedPanel panel = new RoundedPanel(24, LIGHT_BLUE);
        panel.setLayout(new BorderLayout(0, 0));
        panel.setBorder(new EmptyBorder(40, 46, 40, 46));

        JLabel logo = new JLabel("examly.");
        logo.setFont(new Font("SansSerif", Font.BOLD, 34));
        logo.setForeground(PRIMARY);
        panel.add(logo, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(Box.createVerticalStrut(65));

        JLabel category = createTag("ONLINE EXAMINATION");
        content.add(category);

        content.add(Box.createVerticalStrut(28));

        JLabel headline = new JLabel(
                "<html>A clearer path<br>" +
                        "to your next<br>" +
                        "achievement.</html>"
        );
        headline.setFont(new Font("SansSerif", Font.BOLD, 52));
        headline.setForeground(DARK);
        headline.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(headline);
        content.add(Box.createVerticalStrut(16));

        JLabel description = new JLabel(
                "<html>Prepare with confidence. Take exams with focus.<br>" +
                        "See every step of your progress in one place.</html>"
        );
        description.setFont(new Font("SansSerif", Font.PLAIN, 17));
        description.setForeground(MUTED);
        description.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(description);
        content.add(Box.createVerticalStrut(65));

        content.add(createExamCard());

        panel.add(content, BorderLayout.CENTER);

        JPanel footer = new JPanel();
        footer.setOpaque(false);
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));

        JLabel footerTitle = new JLabel("ONE SPACE. EVERY ASSESSMENT.");
        footerTitle.setFont(new Font("SansSerif", Font.BOLD, 11));
        footerTitle.setForeground(PRIMARY);

        JLabel footerText = new JLabel("Built for students. Made for educators.");
        footerText.setFont(new Font("SansSerif", Font.PLAIN, 14));
        footerText.setForeground(MUTED);

        footer.add(footerTitle);
        footer.add(Box.createVerticalStrut(14));
        footer.add(footerText);

        panel.add(footer, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createExamCard() {
        RoundedPanel card = new RoundedPanel(18, Color.WHITE);
        card.setLayout(new BorderLayout());
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 280));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(new EmptyBorder(24, 24, 20, 24));

        JLabel tag = createTag("YOUR NEXT CHALLENGE");
        top.add(tag);
        top.add(Box.createVerticalStrut(20));

        JLabel examName = new JLabel("Database fundamentals");
        examName.setFont(new Font("SansSerif", Font.BOLD, 24));
        examName.setForeground(DARK);

        JLabel examDescription = new JLabel("Midterm examination · CS201");
        examDescription.setFont(new Font("SansSerif", Font.PLAIN, 13));
        examDescription.setForeground(MUTED);

        top.add(examName);
        top.add(Box.createVerticalStrut(8));
        top.add(examDescription);

        card.add(top, BorderLayout.CENTER);

        JPanel stats = new JPanel(new GridLayout(1, 3, 15, 0));
        stats.setOpaque(false);
        stats.setBorder(new EmptyBorder(22, 24, 30, 24));

        stats.add(createStat("20", "Questions"));
        stats.add(createStat("45 min", "Duration"));
        stats.add(createStat("100", "Total marks"));

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER));
        bottom.add(stats, BorderLayout.CENTER);

        card.add(bottom, BorderLayout.SOUTH);

        return card;
    }

    private JPanel createStat(String value, String description) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(new Font("SansSerif", Font.BOLD, 25));
        valueLabel.setForeground(DARK);

        JLabel descriptionLabel = new JLabel(description);
        descriptionLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        descriptionLabel.setForeground(MUTED);

        panel.add(valueLabel);
        panel.add(Box.createVerticalStrut(6));
        panel.add(descriptionLabel);

        return panel;
    }

    // =====================================================
    // RIGHT PANEL
    // =====================================================

    private JPanel createLoginPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setPreferredSize(new Dimension(490, 540));

        JLabel welcome = new JLabel("WELCOME BACK");
        welcome.setFont(new Font("SansSerif", Font.BOLD, 11));
        welcome.setForeground(PRIMARY);

        JLabel title = new JLabel("Sign in to examly.");
        title.setFont(new Font("SansSerif", Font.BOLD, 36));
        title.setForeground(DARK);

        JLabel subtitle = new JLabel("Your next chapter starts here.");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 15));
        subtitle.setForeground(MUTED);

        form.add(welcome);
        form.add(Box.createVerticalStrut(24));
        form.add(title);
        form.add(Box.createVerticalStrut(8));
        form.add(subtitle);
        form.add(Box.createVerticalStrut(52));

        form.add(createFieldLabel("Email address"));
        form.add(Box.createVerticalStrut(10));

        emailField = new JTextField();
        styleTextField(emailField);
        form.add(emailField);

        form.add(Box.createVerticalStrut(26));

        form.add(createFieldLabel("Password"));
        form.add(Box.createVerticalStrut(10));

        JPanel passwordPanel = createPasswordPanel();
        form.add(passwordPanel);

        form.add(Box.createVerticalStrut(16));

        errorLabel = new JLabel("Email or password is incorrect. Please try again.");
        errorLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        errorLabel.setForeground(new Color(205, 55, 55));
        errorLabel.setOpaque(true);
        errorLabel.setBackground(new Color(255, 239, 239));
        errorLabel.setBorder(new EmptyBorder(14, 14, 14, 14));
        errorLabel.setVisible(false);

        form.add(errorLabel);

        form.add(Box.createVerticalStrut(24));

        signInButton = new JButton("Sign in  →");
        signInButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        signInButton.setForeground(Color.WHITE);
        signInButton.setBackground(PRIMARY);
        signInButton.setFocusPainted(false);
        signInButton.setBorderPainted(false);
        signInButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        signInButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        signInButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        signInButton.setPreferredSize(new Dimension(490, 44));

        signInButton.addActionListener(e -> handleLogin());
        SwingUtilities.invokeLater(() -> getRootPane().setDefaultButton(signInButton));

        form.add(signInButton);

        form.add(Box.createVerticalStrut(24));

        JLabel note = new JLabel(
                "Your account role opens the right workspace automatically."
        );
        note.setFont(new Font("SansSerif", Font.PLAIN, 12));
        note.setForeground(MUTED);

        form.add(note);

        JPanel footer = new JPanel();
        footer.setOpaque(false);
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));

        JLabel copyright = new JLabel(
                "© 2026 examly.  All rights reserved."
        );
        copyright.setFont(new Font("SansSerif", Font.PLAIN, 12));
        copyright.setForeground(MUTED);

        JLabel security = new JLabel(
                "Secure access for students and instructors"
        );
        security.setFont(new Font("SansSerif", Font.PLAIN, 11));
        security.setForeground(MUTED);

        footer.add(copyright);
        footer.add(Box.createVerticalStrut(10));
        footer.add(security);

        JPanel container = new JPanel(new BorderLayout());
        container.setOpaque(false);
        container.setBorder(new EmptyBorder(30, 0, 0, 0));
        container.add(form, BorderLayout.CENTER);
        container.add(footer, BorderLayout.SOUTH);

        panel.add(container);

        return panel;
    }

    private JPanel createPasswordPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                new EmptyBorder(0, 14, 0, 10)
        ));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        panel.setPreferredSize(new Dimension(490, 54));

        passwordField = new JPasswordField();
        passwordField.setFont(new Font("SansSerif", Font.PLAIN, 14));
        passwordField.setForeground(DARK);
        passwordField.setBorder(null);
        passwordField.setOpaque(false);

        showPasswordButton = new JButton("Show");
        showPasswordButton.setFont(new Font("SansSerif", Font.PLAIN, 12));
        showPasswordButton.setForeground(MUTED);
        showPasswordButton.setBorderPainted(false);
        showPasswordButton.setContentAreaFilled(false);
        showPasswordButton.setFocusPainted(false);
        showPasswordButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        showPasswordButton.addActionListener(e -> togglePassword());

        panel.add(passwordField, BorderLayout.CENTER);
        panel.add(showPasswordButton, BorderLayout.EAST);

        return panel;
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private JLabel createTag(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 10));
        label.setForeground(PRIMARY);
        label.setOpaque(true);
        label.setBackground(new Color(235, 245, 255));
        label.setBorder(new EmptyBorder(7, 11, 7, 11));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        return label;
    }

    private JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 13));
        label.setForeground(DARK);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        return label;
    }

    private void styleTextField(JTextField field) {
        field.setFont(new Font("SansSerif", Font.PLAIN, 14));
        field.setForeground(DARK);
        field.setBackground(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                new EmptyBorder(0, 14, 0, 14)
        ));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        field.setPreferredSize(new Dimension(490, 54));
    }

    private void togglePassword() {
        passwordVisible = !passwordVisible;

        if (passwordVisible) {
            passwordField.setEchoChar((char) 0);
            showPasswordButton.setText("Hide");
        } else {
            passwordField.setEchoChar('•');
            showPasswordButton.setText("Show");
        }
    }

    private void handleLogin() {
        String email = emailField.getText().trim().toLowerCase();
        String password = new String(passwordField.getPassword());

        if (email.isEmpty() || password.isEmpty()) {
            showError("Please enter both email and password.");
            return;
        }
        if (!email.contains("@")) {
            showError("Please enter a valid email address.");
            return;
        }

        setBusy(true);
        AsyncTask.run(
                () -> Services.auth().login(email, password),
                this::onLoginSuccess,
                this::onLoginFailure);
    }

    private void onLoginSuccess(AuthService.UserAccount user) {
        Session.start(user);
        dispose();
        if (user.role() == AuthService.Role.INSTRUCTOR) {
            new InstructorDashBoard().setVisible(true);
        } else {
            new StudentDashBoard().setVisible(true);
        }
    }

    private void onLoginFailure(ServiceException error) {
        setBusy(false);
        passwordField.setText("");
        showError(error.getKind() == ServiceException.Kind.UNAUTHORIZED
                ? "Email or password is incorrect. Please try again."
                : error.getMessage());
    }

    private void setBusy(boolean busy) {
        signInButton.setEnabled(!busy);
        signInButton.setText(busy ? "Signing in…" : "Sign in  →");
        emailField.setEnabled(!busy);
        passwordField.setEnabled(!busy);
        if (busy) {
            errorLabel.setVisible(false);
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        revalidate();
        repaint();
    }

    // =====================================================
    // CUSTOM ROUNDED PANEL
    // =====================================================

    private static class RoundedPanel extends JPanel {

        private final int radius;
        private final Color backgroundColor;

        public RoundedPanel(int radius, Color backgroundColor) {
            this.radius = radius;
            this.backgroundColor = backgroundColor;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();

            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            g2.setColor(backgroundColor);
            g2.fillRoundRect(
                    0, 0,
                    getWidth(), getHeight(),
                    radius, radius
            );

            g2.dispose();
            super.paintComponent(g);
        }
    }

    // =====================================================
    // MAIN
    // =====================================================

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LoginFrame frame = new LoginFrame();
            frame.setVisible(true);
        });
    }
}