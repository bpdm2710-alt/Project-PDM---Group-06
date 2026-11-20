package org.example.ui.components;

import javax.swing.*;
import java.awt.*;

/** Page body that follows the scroll pane's width, so content never gets a horizontal scrollbar. */
public class ScrollableBody extends JPanel implements Scrollable {

    public ScrollableBody() {
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    }

    /** Wraps a component in a vertically scrolling pane without a border. */
    public static JScrollPane scroll(JComponent view, Color background) {
        JScrollPane pane = new JScrollPane(view);
        pane.setBorder(null);
        pane.getViewport().setBackground(background);
        pane.getVerticalScrollBar().setUnitIncrement(16);
        pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return pane;
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? r.height - 32 : r.width;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
