import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;

public final class BannerGen {
    public static void main(String[] args) throws Exception {
        File root = new File(args.length > 0 ? args[0] : ".");
        int w = 640, h = 360;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        g.setPaint(new GradientPaint(0, 0, new Color(10, 24, 32), w, h, new Color(4, 8, 12)));
        g.fillRect(0, 0, w, h);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(540, 30), 300,
                new float[]{0f, 1f},
                new Color[]{new Color(69, 224, 176, 40), new Color(69, 224, 176, 0)}));
        g.fillRect(0, 0, w, h);

        // left logo tile
        RoundRectangle2D tile = new RoundRectangle2D.Float(34, 58, 244, 244, 56, 56);
        g.setPaint(new GradientPaint(34, 58, new Color(18, 40, 50), 278, 302, new Color(6, 13, 18)));
        g.fill(tile);
        g.setColor(new Color(69, 224, 176, 55));
        g.setStroke(new BasicStroke(2f));
        g.draw(new RoundRectangle2D.Float(40, 64, 232, 232, 50, 50));

        float cx = 156, cy = 180;
        g.setColor(new Color(69, 224, 176));
        g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(cx - 42, cy - 70, cx - 70, cy - 96));
        g.draw(new Line2D.Float(cx + 42, cy - 70, cx + 70, cy - 96));
        RoundRectangle2D tv = new RoundRectangle2D.Float(cx - 78, cy - 64, 156, 92, 18, 18);
        g.setColor(new Color(7, 17, 22));
        g.fill(tv);
        g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setPaint(new GradientPaint(cx - 78, cy - 64, new Color(69, 224, 176),
                cx + 78, cy + 28, new Color(85, 173, 255)));
        g.draw(tv);
        g.setColor(new Color(69, 224, 176));
        g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(cx, cy + 28, cx, cy + 62));
        g.draw(new Line2D.Float(cx - 32, cy + 66, cx + 32, cy + 66));

        Path2D star = new Path2D.Float();
        star.moveTo(cx, cy - 50);
        star.lineTo(cx + 12, cy - 18);
        star.lineTo(cx + 44, cy - 6);
        star.lineTo(cx + 12, cy + 6);
        star.lineTo(cx, cy + 38);
        star.lineTo(cx - 12, cy + 6);
        star.lineTo(cx - 44, cy - 6);
        star.lineTo(cx - 12, cy - 18);
        star.closePath();
        g.setPaint(new GradientPaint(cx - 44, cy - 50, new Color(190, 255, 236),
                cx + 44, cy + 38, new Color(69, 224, 176)));
        g.fill(star);

        // text
        Font titleFont = pickFont(Font.BOLD, 46);
        Font subFont = pickFont(Font.PLAIN, 20);
        g.setFont(titleFont);
        g.setColor(new Color(242, 247, 250));
        g.drawString("电视自动清理", 320, 168);
        g.setFont(subFont);
        g.setColor(new Color(143, 166, 179));
        g.drawString("内存不足时自动清理后台", 322, 212);
        g.setColor(new Color(69, 224, 176));
        g.fillRoundRect(322, 240, 82, 5, 5, 5);
        g.setColor(new Color(96, 120, 135));
        g.setFont(pickFont(Font.PLAIN, 17));
        g.drawString("轻量 · 不联网 · 遥控器操作", 322, 278);
        g.dispose();

        File out = new File(root, "drawable-xhdpi/tv_banner.png");
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println(out.getPath());
    }

    private static Font pickFont(int style, int size) {
        String[] names = {"PingFang SC", "Hiragino Sans GB", "Heiti SC", "STHeiti", "Microsoft YaHei", "SansSerif"};
        for (String name : names) {
            Font f = new Font(name, style, size);
            if (f.getFamily().equalsIgnoreCase(name) || name.equals("SansSerif")) {
                return f;
            }
        }
        return new Font(Font.SANS_SERIF, style, size);
    }
}
