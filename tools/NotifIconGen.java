import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;

public final class NotifIconGen {
    public static void main(String[] args) throws Exception {
        File root = new File(args.length > 0 ? args[0] : ".");
        int[] sizes = {24, 36, 48, 72, 96};
        String[] dirs = {"drawable-mdpi", "drawable-hdpi", "drawable-xhdpi", "drawable-xxhdpi", "drawable-xxxhdpi"};
        for (int i = 0; i < sizes.length; i++) {
            int s = sizes[i];
            BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            float u = s / 24f;
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1.7f * u, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            RoundRectangle2D tv = new RoundRectangle2D.Float(3.2f * u, 5f * u, 17.6f * u, 11.7f * u, 1.6f * u, 1.6f * u);
            g.draw(tv);
            g.draw(new Line2D.Float(12f * u, 16.7f * u, 12f * u, 20f * u));
            g.draw(new Line2D.Float(8.8f * u, 20f * u, 15.2f * u, 20f * u));
            Path2D star = new Path2D.Float();
            star.moveTo(12f * u, 7.9f * u);
            star.lineTo(13.3f * u, 10.9f * u);
            star.lineTo(16.3f * u, 12.2f * u);
            star.lineTo(13.3f * u, 13.5f * u);
            star.lineTo(12f * u, 16.5f * u);
            star.lineTo(10.7f * u, 13.5f * u);
            star.lineTo(7.7f * u, 12.2f * u);
            star.lineTo(10.7f * u, 10.9f * u);
            star.closePath();
            g.fill(star);
            g.dispose();
            File out = new File(root, dirs[i] + "/ic_notification.png");
            out.getParentFile().mkdirs();
            ImageIO.write(img, "png", out);
            System.out.println(out.getPath());
        }
    }
}
