import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;

public class PipeComponent {

    public enum ComponentType {
        PIPE,
        CURVED_PIPE,
        BRIDGE,
        SPLITTER,
        CONVERGER,
        TANK
    }

    /**
     * Returns the base sprite for a component type.
     *
     * @param type component type
     * @return image associated with the type
     */
    public static BufferedImage GetImgForComponentType(ComponentType type) {
        AssetFactory assets = AssetFactory.INSTANCE;
        return switch (type) {
            case PIPE -> assets.PIPE1;
            case CURVED_PIPE -> assets.PIPE2;
            case BRIDGE -> assets.BRIDGE;
            case SPLITTER -> assets.SPLITTER;
            case CONVERGER -> assets.CONVERGER;
            case TANK -> assets.TANK;
        };
    };

    public enum Orientation {
        NORTH,
        SOUTH,
        EAST,
        WEST,
        NORTH_M,
        SOUTH_M,
        EAST_M,
        WEST_M
    }

    public static Orientation[] STANDARD_ORIENTATIONS = new Orientation[] {
            Orientation.NORTH,
            Orientation.EAST,
            Orientation.SOUTH,
            Orientation.WEST,
    };

    public static Orientation[] CURVED_PIPE_ORIENTATIONS = new Orientation[] {
            Orientation.NORTH,
            Orientation.EAST,
            Orientation.SOUTH,
            Orientation.WEST,
            Orientation.EAST_M,
            Orientation.SOUTH_M,
            Orientation.WEST_M,
            Orientation.NORTH_M
    };

    // Variables defining the component's properties
    public int X, Y;
    public ComponentType type;
    public Orientation orientation;
    public int tankAmount = 0;

    // Variables used by the editor
    public int tempX, tempY;
    public boolean selected = false;

    // Variables pertaining to simulation
    public int state = 0;
    public int amountSim = 0;
    public int amountBridge = 0; // used for East/West pipe capacity

    /**
     * Creates a component at a grid position.
     *
     * @param X x-grid coordinate
     * @param Y y-grid coordinate
     * @param type component type
     * @param orientation component orientation
     */
    public PipeComponent(int X, int Y, ComponentType type, Orientation orientation) {
        this.X = X;
        this.Y = Y;
        this.type = type;
        this.orientation = orientation;
    }

    /**
     * Rotates the component to the next orientation supported by its type.
     */
    public void nextOrientation() {
        if(type != ComponentType.BRIDGE) {
            Orientation[] orientations = type == ComponentType.CURVED_PIPE ?
                    CURVED_PIPE_ORIENTATIONS : STANDARD_ORIENTATIONS;
            int idx;
            for(idx = 0; orientations[idx] != orientation; ++idx);
            if(++idx >= orientations.length) {
                idx = 0;
            }
            orientation = orientations[idx];
        }
    }

    /**
     * Rotates the component to the previous orientation supported by its type.
     */
    public void prevOrientation() {
        if(type != ComponentType.BRIDGE) {
            Orientation[] orientations = type == ComponentType.CURVED_PIPE ?
                    CURVED_PIPE_ORIENTATIONS : STANDARD_ORIENTATIONS;
            int idx;
            for(idx = 0; orientations[idx] != orientation; ++idx);
            if(--idx <= 0) {
                idx = orientations.length - 1;
            }
            orientation = orientations[idx];
        }
    }

    /**
     * Opens the edit dialog for this component.
     */
    public void editComponent() {
        if(type == ComponentType.TANK) {
            String input = JOptionPane.showInputDialog(null, "Configure amount");
            try {
                tankAmount = amountSim = Integer.parseInt(input);
            } catch(NumberFormatException e) {
                JOptionPane.showMessageDialog(null, "Invalid amount", "Warning", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    /**
     * Draws this component in the editor with orientation, selection, and simulation overlays.
     *
     * @param g graphics context
     */
    public void draw(Graphics2D g) {
        BufferedImage asset = GetImgForComponentType(this.type);

        int _X = selected && tempX != X ? tempX : X;
        int _Y = selected && tempY != Y ? tempY : Y;
        int drawX = _X * (PipeSim.PANEL_SIZE - 1) / PipeSim.GRID_SIZE;
        int drawY = _Y * (PipeSim.PANEL_SIZE - 1) / PipeSim.GRID_SIZE;
        int drawWidth  = (_X + 1) * (PipeSim.PANEL_SIZE - 1) / PipeSim.GRID_SIZE - drawX;
        int drawHeight = (_Y + 1) * (PipeSim.PANEL_SIZE - 1) / PipeSim.GRID_SIZE - drawY;

        double scaleX = (double) drawWidth  / asset.getWidth();
        double scaleY = (double) drawHeight / asset.getHeight();

        double rotation = switch (orientation) {
            case NORTH, NORTH_M -> 0;
            case WEST,  WEST_M  -> Math.toRadians(270);
            case SOUTH, SOUTH_M -> Math.toRadians(180);
            case EAST,  EAST_M  -> Math.toRadians(90);
        };

        double cx = asset.getWidth()  / 2.0;
        double cy = asset.getHeight() / 2.0;

        AffineTransform at = new AffineTransform();
        at.translate(drawX, drawY);
        at.scale(scaleX, scaleY);
        at.translate(cx, cy);
        at.rotate(rotation);
        switch (orientation) {
            case NORTH_M, SOUTH_M, EAST_M,  WEST_M -> at.scale(-1, 1);
        }
        at.translate(-cx, -cy);

        if(selected) {
            g.setColor(Color.black);
            g.fillRect(drawX, drawY, drawWidth, drawHeight);
            float[] scales = { -1f, -1f, -1f, 1f };
            float[] offsets = { 255f, 255f, 255f, 0f };
            RescaleOp op = new RescaleOp(scales, offsets, null);
            asset = op.filter(asset, null);
        }

        g.drawImage(asset, at, null);

        if(type == ComponentType.TANK) {
            String text = amountSim + "";
            FontMetrics fm = g.getFontMetrics();
            int textWidth  = fm.stringWidth(text);
            int textHeight = fm.getAscent();
            int x = drawX + (drawWidth  - textWidth)  / 2;
            int y = drawY + (drawHeight - textHeight) / 2 + fm.getAscent();
            g.setColor(Color.black);
            g.drawString(text, x, y);
        } else {
            if(amountSim + amountBridge > 0) {
                String text = (amountSim + amountBridge) + "";
                FontMetrics fm = g.getFontMetrics();
                int textWidth  = fm.stringWidth(text);
                int textHeight = fm.getAscent();
                g.setColor(Color.CYAN);
                g.fillRect(drawX + 2, drawY + 2, textWidth + 4, textHeight);
                g.setColor(Color.black);
                g.drawString(text, drawX + 4, drawY + textHeight);
            }
        }
    }

    /**
     * Creates a shallow copy for editor placement.
     *
     * @return cloned component with position, type, and orientation
     */
    @Override
    public PipeComponent clone() {
        return new PipeComponent(X, Y, type, orientation);
    }

    /**
     * Compares two components by structural editor fields.
     *
     * @param o object to compare
     * @return true when key editor fields are equal
     */
    @Override
    public boolean equals(Object o) {
        if(o instanceof PipeComponent pc) {
            return pc.X == X && pc.Y == Y && pc.type == type && pc.orientation == orientation && pc.tankAmount == tankAmount;
        } else {
            return false;
        }
    }
}
