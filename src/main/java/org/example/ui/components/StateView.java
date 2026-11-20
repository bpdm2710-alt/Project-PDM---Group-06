package org.example.ui.components;

import org.example.Service.ServiceException;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.example.ui.components.UITheme.*;

/**
 * Wraps a panel's real content with the three other states every data-driven
 * screen needs: loading, empty and error (with Retry).
 *
 * Typical use:
 * <pre>
 *   StateView view = new StateView(contentPanel);
 *   view.load(() -> Services.student().listMyExams(id),
 *             exams -> render(exams),
 *             List::isEmpty, "No exams yet", "Register for an exam from Courses.");
 * </pre>
 */
public class StateView extends JPanel {

    private static final String CONTENT = "content";
    private static final String LOADING = "loading";
    private static final String MESSAGE = "message";

    private final CardLayout cards = new CardLayout();
    private final JPanel messagePanel = new JPanel(new GridBagLayout());
    private final JLabel loadingLabel = label("Loading…", plain(13), MUTED);

    /** Incremented per load so a slow, outdated response never overwrites a newer one. */
    private int generation;

    public StateView(JComponent content) {
        setOpaque(false);
        setLayout(cards);

        content.setOpaque(false);
        add(content, CONTENT);
        add(buildLoadingPanel(), LOADING);

        messagePanel.setOpaque(false);
        add(messagePanel, MESSAGE);

        cards.show(this, CONTENT);
    }

    private JPanel buildLoadingPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(60, 0, 60, 0));

        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        bar.setMaximumSize(new Dimension(220, 6));
        bar.setPreferredSize(new Dimension(220, 6));
        bar.setAlignmentX(Component.CENTER_ALIGNMENT);
        loadingLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel.add(bar);
        panel.add(Box.createVerticalStrut(14));
        panel.add(loadingLabel);
        return panel;
    }

    public void showLoading(String text) {
        loadingLabel.setText(text);
        cards.show(this, LOADING);
    }

    public void showContent() {
        cards.show(this, CONTENT);
    }

    public void showEmpty(String title, String hint) {
        showMessage(title, hint, null, null, MUTED);
    }

    public void showError(ServiceException error, Runnable retry) {
        String title = error.getKind() == ServiceException.Kind.UNAVAILABLE
                ? "Service unavailable" : "Could not load this page";
        showMessage(title, error.getMessage(), "Try again", retry, new Color(205, 55, 55));
    }

    private void showMessage(String title, String text, String buttonText,
                             Runnable action, Color titleColor) {
        messagePanel.removeAll();

        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(new EmptyBorder(60, 20, 60, 20));

        JLabel titleLabel = label(title, bold(16), titleColor);
        JLabel textLabel = label("<html><div style='text-align:center;width:360px'>"
                + escape(text) + "</div></html>", plain(12), MUTED);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        textLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        box.add(titleLabel);
        box.add(Box.createVerticalStrut(8));
        box.add(textLabel);

        if (buttonText != null && action != null) {
            JButton button = primaryButton(buttonText);
            button.setAlignmentX(Component.CENTER_ALIGNMENT);
            button.setPreferredSize(new Dimension(130, 38));
            button.setMaximumSize(new Dimension(130, 38));
            button.addActionListener(e -> action.run());
            box.add(Box.createVerticalStrut(18));
            box.add(button);
        }

        messagePanel.add(box);
        messagePanel.revalidate();
        messagePanel.repaint();
        cards.show(this, MESSAGE);
    }

    /**
     * Loads data in the background, then renders it.
     *
     * @param render    called on the Swing thread with the result
     * @param isEmpty   when true, the empty message is shown instead of the content
     */
    public <T> void load(AsyncTask.Work<T> work, Consumer<T> render, Predicate<T> isEmpty,
                         String emptyTitle, String emptyHint) {
        final int mine = ++generation;
        showLoading("Loading…");
        AsyncTask.run(work,
                result -> {
                    if (mine != generation) {
                        return;
                    }
                    if (isEmpty != null && isEmpty.test(result)) {
                        showEmpty(emptyTitle, emptyHint);
                    } else {
                        render.accept(result);
                        showContent();
                    }
                },
                error -> {
                    if (mine != generation) {
                        return;
                    }
                    showError(error, () -> load(work, render, isEmpty, emptyTitle, emptyHint));
                });
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
