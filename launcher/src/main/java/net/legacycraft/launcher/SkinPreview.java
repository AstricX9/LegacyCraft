package net.legacycraft.launcher;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * A small software-rendered player model with an optional cape. Each box
 * face is drawn texel by texel under an orthographic projection, so it stays
 * pixel-crisp at any angle. Drag to rotate; it turns slowly until touched.
 * Model space: y up, the player faces +z (toward the viewer at yaw 0).
 */
final class SkinPreview extends JComponent {
    private BufferedImage skin, cape;
    private boolean slim, outerLayer = true;
    private double yaw = Math.toRadians(-30), pitch = Math.toRadians(12);
    private boolean autoSpin = true;
    private int dragX, dragY;
    private double time;
    private String message;

    /** Maps a model-space point (and direction) for one part: its pose. */
    private interface Pose { double[] apply(double x, double y, double z, boolean direction); }

    SkinPreview() {
        setPreferredSize(new Dimension(300, 360));
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { dragX = e.getX(); dragY = e.getY(); autoSpin = false; }
            @Override public void mouseDragged(MouseEvent e) {
                yaw += (e.getX() - dragX) * 0.012;
                pitch = Math.max(-1.2, Math.min(1.2, pitch + (e.getY() - dragY) * 0.012));
                dragX = e.getX(); dragY = e.getY();
                repaint();
            }
            @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) autoSpin = !autoSpin; }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        setToolTipText("Drag to turn the player, double-click to start or stop spinning");
        new Timer(40, e -> {
            if (!isShowing()) return;
            time += 0.04;
            if (autoSpin) yaw += 0.012;
            repaint();
        }).start();
    }

    void setSkin(BufferedImage skin, boolean slim, String message) {
        this.skin = skin; this.slim = slim; this.message = message;
        repaint();
    }

    void setCape(BufferedImage cape) { this.cape = cape; repaint(); }

    void setOuterLayer(boolean on) { outerLayer = on; repaint(); }

    @Override protected void paintComponent(Graphics raw) {
        Graphics2D g = (Graphics2D) raw.create();
        g.setColor(Theme.PAGE);
        g.fillRect(0, 0, getWidth(), getHeight());
        if (skin == null) {
            g.setColor(Theme.MUTED);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(getFont().deriveFont(Font.PLAIN, 13f));
            String text = message == null ? "No skin" : message;
            int w = g.getFontMetrics().stringWidth(text);
            g.drawString(text, (getWidth() - w) / 2, getHeight() / 2);
            g.dispose();
            return;
        }
        double scale = Math.min(getWidth() / 30.0, getHeight() / 42.0);
        double cx = getWidth() / 2.0, cy = getHeight() / 2.0;
        List<Unit> units = new ArrayList<>();
        double sway = Math.sin(time * 2.2) * 0.05;
        int arm = slim ? 3 : 4;
        Pose still = (x, y, z, dir) -> new double[] {x, y, z};
        Pose rightArm = swing(Math.toRadians(-4) + sway, 22, 0, -4 - arm / 2.0, -1);
        Pose leftArm = swing(Math.toRadians(4) - sway, 22, 0, 4 + arm / 2.0, 1);
        units.add(part(-4, 24, -4, 8, 8, 8, 0, 0, 32, 0, 0.5, still));
        units.add(part(-4, 12, -2, 8, 12, 4, 16, 16, 16, 32, 0.25, still));
        units.add(part(-4 - arm, 12, -2, arm, 12, 4, 40, 16, 40, 32, 0.25, rightArm));
        units.add(part(4, 12, -2, arm, 12, 4, 32, 48, 48, 48, 0.25, leftArm));
        units.add(part(-4, 0, -2, 4, 12, 4, 0, 16, 0, 32, 0.25, still));
        units.add(part(0, 0, -2, 4, 12, 4, 16, 48, 0, 48, 0.25, still));
        if (cape != null) units.add(capeUnit(Math.toRadians(8 + Math.sin(time * 1.6) * 3)));
        units.sort((a, b) -> Double.compare(a.depth, b.depth));
        for (Unit u : units)
            for (Face f : u.faces) f.draw(g, cx, cy, scale);
        g.dispose();
    }

    /** An arm turned outward by {@code angle} around the z axis at the shoulder. */
    private static Pose swing(double angle, double pivotY, double pivotZ, double pivotX, int side) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        return (x, y, z, dir) -> {
            double px = dir ? x : x - pivotX, py = dir ? y : y - pivotY;
            double rx = px * cos - py * sin, ry = px * sin + py * cos;
            return dir ? new double[] {rx, ry, z} : new double[] {rx + pivotX, ry + pivotY, z};
        };
    }

    /**
     * The cape as LayerCape hangs it: a 10x16x1 box behind the body, turned
     * round so its outer face (texture 1,1) faces backwards, tilted back
     * around the shoulders.
     */
    private Unit capeUnit(double tilt) {
        double cos = Math.cos(tilt), sin = Math.sin(tilt);
        Pose pose = (x, y, z, dir) -> {
            // turn 180 degrees about the vertical axis through the cape's middle (z = -2.5)
            double tx = -x, tz = dir ? -z : -5 - z;
            double py = dir ? y : y - 24, pz = dir ? tz : tz + 2;
            double ry = py * cos - pz * sin, rz = py * sin + pz * cos;
            return dir ? new double[] {tx, ry, rz} : new double[] {tx, ry + 24, rz - 2};
        };
        Unit u = new Unit();
        u.depth = view(pose.apply(0, 16, -2.5, false))[2];
        box(u.faces, cape, cape.getWidth() / 64, -5, 8, -3, 10, 16, 1, 0, 0, 0, pose);
        return u;
    }

    private Unit part(double x, double y, double z, int w, int h, int d, int u, int v, int ou, int ov, double grow, Pose pose) {
        Unit unit = new Unit();
        unit.depth = view(pose.apply(x + w / 2.0, y + h / 2.0, z + d / 2.0, false))[2];
        box(unit.faces, skin, 1, x, y, z, w, h, d, u, v, 0, pose);
        if (outerLayer) box(unit.faces, skin, 1, x, y, z, w, h, d, ou, ov, grow, pose);
        return unit;
    }

    private void box(List<Face> out, BufferedImage tex, int texScale, double x, double y, double z, int w, int h, int d,
                     int u, int v, double e, Pose pose) {
        double x0 = x - e, x1 = x + w + e, y0 = y - e, y1 = y + h + e, z0 = z - e, z1 = z + d + e;
        add(out, tex, texScale, pose, x0, y1, z1, x1, y1, z1, x0, y0, z1, u + d, v + d, w, h, 0, 0, 1);          // front
        add(out, tex, texScale, pose, x1, y1, z0, x0, y1, z0, x1, y0, z0, u + 2 * d + w, v + d, w, h, 0, 0, -1); // back
        add(out, tex, texScale, pose, x0, y1, z0, x0, y1, z1, x0, y0, z0, u, v + d, d, h, -1, 0, 0);            // right side
        add(out, tex, texScale, pose, x1, y1, z1, x1, y1, z0, x1, y0, z1, u + d + w, v + d, d, h, 1, 0, 0);     // left side
        add(out, tex, texScale, pose, x0, y1, z0, x1, y1, z0, x0, y1, z1, u + d, v, w, d, 0, 1, 0);             // top
        add(out, tex, texScale, pose, x0, y0, z1, x1, y0, z1, x0, y0, z0, u + d + w, v, w, d, 0, -1, 0);        // bottom
    }

    private void add(List<Face> out, BufferedImage tex, int texScale, Pose pose,
                     double ox, double oy, double oz, double ux, double uy, double uz, double vx, double vy, double vz,
                     int tu, int tv, int tw, int th, double nx, double ny, double nz) {
        double[] n = direction(pose.apply(nx, ny, nz, true));
        if (n[2] <= 1e-6) return;
        Face f = new Face();
        f.tex = tex; f.s = texScale;
        f.o = view(pose.apply(ox, oy, oz, false));
        f.u = view(pose.apply(ux, uy, uz, false));
        f.v = view(pose.apply(vx, vy, vz, false));
        f.tu = tu; f.tv = tv; f.tw = tw; f.th = th;
        out.add(f);
    }

    private double[] view(double[] p) { return direction(new double[] {p[0], p[1] - 16, p[2]}); }

    private double[] direction(double[] p) {
        double x = p[0], y = p[1], z = p[2];
        double x1 = x * Math.cos(yaw) + z * Math.sin(yaw), z1 = -x * Math.sin(yaw) + z * Math.cos(yaw);
        double y2 = y * Math.cos(pitch) - z1 * Math.sin(pitch), z2 = y * Math.sin(pitch) + z1 * Math.cos(pitch);
        return new double[] {x1, y2, z2};
    }

    private static final class Unit {
        final List<Face> faces = new ArrayList<>();
        double depth;
    }

    private static final class Face {
        BufferedImage tex;
        int s;
        double[] o, u, v;
        int tu, tv, tw, th;

        void draw(Graphics2D g, double cx, double cy, double scale) {
            int cols = tw * s, rows = th * s;
            double dux = (u[0] - o[0]) / cols, duy = (u[1] - o[1]) / cols;
            double dvx = (v[0] - o[0]) / rows, dvy = (v[1] - o[1]) / rows;
            Path2D.Double quad = new Path2D.Double();
            for (int j = 0; j < rows; j++)
                for (int i = 0; i < cols; i++) {
                    int argb = tex.getRGB(tu * s + i, tv * s + j);
                    if ((argb >>> 24) == 0) continue;
                    double px = o[0] + dux * i + dvx * j, py = o[1] + duy * i + dvy * j;
                    quad.reset();
                    quad.moveTo(cx + px * scale, cy - py * scale);
                    quad.lineTo(cx + (px + dux) * scale, cy - (py + duy) * scale);
                    quad.lineTo(cx + (px + dux + dvx) * scale, cy - (py + duy + dvy) * scale);
                    quad.lineTo(cx + (px + dvx) * scale, cy - (py + dvy) * scale);
                    quad.closePath();
                    g.setColor(new Color(argb, true));
                    g.fill(quad);
                    if ((argb >>> 24) == 255) g.draw(quad); // closes hairline gaps between texels
                }
        }
    }
}
