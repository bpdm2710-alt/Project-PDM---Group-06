package org.example.ui.components;

import javax.swing.*;
import java.awt.*;

/**
 * JPanel with anti-aliased rounded corners.
 *
 * The project already had a private copy of this class inside LoginFrame and
 * StudentDashBoard. It is extracted here so the new screens can share it
 * without touching the existing code.
 */
public class RoundedPanel extends JPanel {

    private final int radius;
    private Color backgroundColor;
    private final Color borderColor;

    public RoundedPanel(int radius, Color backgroundColor) {
        this(radius, backgroundColor, null);
    }

    public RoundedPanel(int radius, Color backgroundColor, Color borderColor) {
        this.radius = radius;
        this.backgroundColor = backgroundColor;
        this.borderColor = borderColor;
        setOpaque(false);
    }

    /** Changes the painted fill (unlike setBackground on a non-opaque panel). */
    public void setCardBackground(Color color) {
        this.backgroundColor = color;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        int w = getWidth();
        int h = getHeight();

        g2.setColor(backgroundColor);
        g2.fillRoundRect(0, 0, w, h, radius, radius);

        if (borderColor != null) {
            g2.setColor(borderColor);
            g2.drawRoundRect(0, 0, w - 1, h - 1, radius, radius);
        }

        g2.dispose();
        super.paintComponent(g);
    }
}
