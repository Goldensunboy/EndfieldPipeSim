import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public class AssetFactory {

    public static AssetFactory INSTANCE;

    public BufferedImage PIPE1, PIPE2, BRIDGE, SPLITTER, CONVERGER, TANK;

    /**
     * Loads all image assets used by pipe components and stores them on this singleton instance.
     */
    public AssetFactory() {
        INSTANCE = this;
        try {
            PIPE1 = ImageIO.read(getClass().getResource("/assets/Pipe1.png"));
            PIPE2 = ImageIO.read(getClass().getResource("/assets/Pipe2.png"));
            BRIDGE = ImageIO.read(getClass().getResource("/assets/Bridge.png"));
            SPLITTER = ImageIO.read(getClass().getResource("/assets/Splitter.png"));
            CONVERGER = ImageIO.read(getClass().getResource("/assets/Converger.png"));
            TANK = ImageIO.read(getClass().getResource("/assets/Tank.png"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
