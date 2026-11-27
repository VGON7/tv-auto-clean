import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;

public final class IconGen {
    private static final int S = 1024;

    public static void main(String[] args) throws Exception {
        File root = new File(args.length > 0 ? args[0] : ".");
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        RoundRectangle2D bg = new RoundRectangle2D.Float(54, 54, 916, 916, 220, 220);
        g.setPaint(new GradientPaint(0, 54, new Color(18, 40, 50), 0, 970, new Color(5, 10, 14)));
        g.fill(bg);

        // soft teal glow in the upper-right corner
        g.setPaint(new RadialGradientPaint(new Point2D.Float(790, 220), 430,
                new float[]{0f, 1f},
                new Color[]{new Color(69, 224, 176, 55), new Color(69, 224, 176, 0)}));
        g.fill(bg);

        // thin inner border
        g.setColor(new Color(69, 224, 176, 45));
        g.setStroke(new BasicStroke(5f));
        g.draw(new RoundRectangle2D.Float(70, 70, 884, 884, 205, 205));

        // antenna
        g.setColor(new Color(69, 224, 176));
        g.setStroke(new BasicStroke(28f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(386, 292, 300, 196));
        g.draw(new Line2D.Float(638, 292, 724, 196));

        // TV body
        RoundRectangle2D tv = new RoundRectangle2D.Float(196, 300, 632, 356, 64, 64);
        g.setColor(new Color(7, 17, 22));
        g.fill(tv);
        g.setPaint(new GradientPaint(196, 300, new Color(69, 224, 176),
                828, 656, new Color(44, 151, 209)));
        g.setStroke(new BasicStroke(24f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(tv);

        // stand
        g.setColor(new Color(69, 224, 176));
        g.setStroke(new BasicStroke(24f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(512, 656, 512, 756));
        g.draw(new Line2D.Float(398, 770, 626, 770));

        // central sparkle with a soft halo
        g.setPaint(new RadialGradientPaint(new Point2D.Float(512, 478), 190,
                new float[]{0f, 1f},
                new Color[]{new Color(69, 224, 176, 90), new Color(69, 224, 176, 0)}));
        g.fill(new Ellipse2D.Float(322, 288, 380, 380));
        Path2D sparkle = new Path2D.Float();
        sparkle.moveTo(512, 348);
        sparkle.lineTo(552, 430);
        sparkle.lineTo(634, 470);
        sparkle.lineTo(552, 510);
        sparkle.lineTo(512, 592);
        sparkle.lineTo(472, 510);
        sparkle.lineTo(390, 470);
        sparkle.lineTo(472, 430);
        sparkle.closePath();
        g.setPaint(new GradientPaint(390, 348, new Color(190, 255, 236),
                634, 592, new Color(69, 224, 176)));
        g.fill(sparkle);

        // small blue sparkle
        Path2D blue = new Path2D.Float();
        blue.moveTo(760, 180);
        blue.lineTo(775, 232);
        blue.lineTo(827, 247);
        blue.lineTo(775, 262);
        blue.lineTo(760, 314);
        blue.lineTo(745, 262);
        blue.lineTo(693, 247);
        blue.lineTo(745, 232);
        blue.closePath();
        g.setColor(new Color(85, 173, 255));
        g.fill(blue);

        g.dispose();

        int[] sizes = {48, 72, 96, 144, 192, 512};
        String[] dirs = {"mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi", null};
        for (int i = 0; i < sizes.length; i++) {
            BufferedImage scaled = new BufferedImage(sizes[i], sizes[i], BufferedImage.TYPE_INT_ARGB);
            Graphics2D sg = scaled.createGraphics();
            sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            sg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            sg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            sg.drawImage(img, 0, 0, sizes[i], sizes[i], null);
            sg.dispose();
            File out;
            if (dirs[i] == null) {
                out = new File(root, "ic_launcher_512.png");
            } else {
                out = new File(root, dirs[i] + "/ic_launcher.png");
            }
            if (out.getParentFile() != null) out.getParentFile().mkdirs();
            ImageIO.write(scaled, "png", out);
            System.out.println(out.getPath());
        }
    }
}
