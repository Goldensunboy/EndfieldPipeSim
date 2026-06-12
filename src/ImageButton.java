import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

public class ImageButton extends JButton {

    private final BufferedImage img;

    /**
     * Creates a tool button that places a new component of the given type.
     *
     * @param type component type represented by this button
     */
    public ImageButton(PipeComponent.ComponentType type) {
        this.img = PipeComponent.GetImgForComponentType(type);
        setContentAreaFilled(true);   // keep normal button background
        setFocusPainted(false);       // optional: remove focus ring
        setBorderPainted(true);       // keep outer button border
        Dimension d = new Dimension(80, 80);
        setPreferredSize(d);
        setMinimumSize(d);
        setMaximumSize(d);
        addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PipeSim.INSTANCE.setSelectedComponent(new PipeComponent(-1, -1, type, PipeComponent.Orientation.NORTH));
            }
        });
        setFocusable(false);
    }

    /**
     * Paints the button background and a scaled preview image of the selected component type.
     *
     * @param g graphics context provided by Swing
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();

        int padding = 6;
        int innerBorder = 3;

        int x = padding;
        int y = padding;
        int w = getWidth()  - padding * 2;
        int h = getHeight() - padding * 2;

        // draw black inner border
        g2.setColor(Color.BLACK);
        g2.fillRect(x, y, w, h);

        // compute area inside black border
        int ix = x + innerBorder;
        int iy = y + innerBorder;
        int iw = w - innerBorder * 2;
        int ih = h - innerBorder * 2;

        g2.setColor(PipeSim.BG_COLOR);
        g2.fillRect(ix, iy, iw, ih);

        // scale image to fit
        double scaleX = iw / (double) img.getWidth();
        double scaleY = ih / (double) img.getHeight();

        AffineTransform at = new AffineTransform();
        at.translate(ix, iy);
        at.scale(scaleX, scaleY);

        g2.drawImage(img, at, null);
        g2.dispose();
    }
}
