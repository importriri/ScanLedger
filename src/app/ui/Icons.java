package app.ui;

// Adapted from AutoFillSuite src/app/ui/Icons.java — keep behaviour in sync.

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

/**
 * Icons drawn with Graphics2D, never typed as characters.
 *
 * WHY: an explicit Font ("Segoe UI") kills Java's glyph fallback, so ▶ 🔎 ⚙ ◂
 * come out as empty boxes on Windows — the app looks broken. Vector icons cannot
 * miss a glyph, scale cleanly and take the color we hand them.
 */
public final class Icons {

    private Icons() {}

    private abstract static class Base implements Icon {
        final int size;
        final Color color;
        Base(int size, Color color) { this.size = size; this.color = color; }
        @Override public int getIconWidth()  { return size; }
        @Override public int getIconHeight() { return size; }
        @Override public final void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            g2.setColor(color);
            draw(g2);
            g2.dispose();
        }
        abstract void draw(Graphics2D g);
    }

    /** Stop square. */
    public static Icon stop(int size, Color c) {
        return new Base(size, c) {
            @Override void draw(Graphics2D g) {
                int pad = Math.round(size * 0.22f);
                g.fillRect(pad, pad, size - 2 * pad, size - 2 * pad);
            }
        };
    }

    /** Magnifier — verification. */
    public static Icon search(int size, Color c) {
        return new Base(size, c) {
            @Override void draw(Graphics2D g) {
                float d = size * 0.58f;
                g.setStroke(new BasicStroke(Math.max(1.4f, size * 0.11f),
                            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Ellipse2D.Float(size * 0.10f, size * 0.10f, d, d));
                g.drawLine(Math.round(size * 0.63f), Math.round(size * 0.63f),
                           Math.round(size * 0.88f), Math.round(size * 0.88f));
            }
        };
    }

    /** Gear. Hub subtracted via Area: AlphaComposite.Clear paints BLACK on opaque buffers. */
    public static Icon gear(int size, Color c) {
        return new Base(size, c) {
            @Override void draw(Graphics2D g) {
                float cx = size / 2f, cy = size / 2f;
                float rOut = size * 0.46f, rIn = size * 0.31f;
                Area gear = new Area(new Ellipse2D.Float(cx - rIn, cy - rIn, rIn * 2, rIn * 2));
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4;
                    Path2D t = new Path2D.Float();
                    float w = size * 0.09f;
                    t.moveTo(cx + (float) (Math.cos(a) * rIn - Math.sin(a) * w),
                             cy + (float) (Math.sin(a) * rIn + Math.cos(a) * w));
                    t.lineTo(cx + (float) (Math.cos(a) * rOut - Math.sin(a) * w * 0.6),
                             cy + (float) (Math.sin(a) * rOut + Math.cos(a) * w * 0.6));
                    t.lineTo(cx + (float) (Math.cos(a) * rOut + Math.sin(a) * w * 0.6),
                             cy + (float) (Math.sin(a) * rOut - Math.cos(a) * w * 0.6));
                    t.lineTo(cx + (float) (Math.cos(a) * rIn + Math.sin(a) * w),
                             cy + (float) (Math.sin(a) * rIn - Math.cos(a) * w));
                    t.closePath();
                    gear.add(new Area(t));
                }
                float h = size * 0.13f;
                gear.subtract(new Area(new Ellipse2D.Float(cx - h, cy - h, h * 2, h * 2)));
                g.fill(gear);
            }
        };
    }

    /** Chevron: points left (close) or right (open). */
    public static Icon chevron(int size, Color c, boolean left) {
        return new Base(size, c) {
            @Override void draw(Graphics2D g) {
                g.setStroke(new BasicStroke(Math.max(1.5f, size * 0.14f),
                            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int x1 = Math.round(size * (left ? 0.64f : 0.36f));
                int x2 = Math.round(size * (left ? 0.36f : 0.64f));
                g.drawLine(x1, Math.round(size * 0.22f), x2, size / 2);
                g.drawLine(x2, size / 2, x1, Math.round(size * 0.78f));
            }
        };
    }

    /** Circular arrow — retry. */
    public static Icon retry(int size, Color c) {
        return new Base(size, c) {
            @Override void draw(Graphics2D g) {
                g.setStroke(new BasicStroke(Math.max(1.4f, size * 0.12f),
                            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int pad = Math.round(size * 0.16f);
                g.drawArc(pad, pad, size - 2 * pad, size - 2 * pad, 40, 280);
                Path2D head = new Path2D.Float();
                float hx = size * 0.78f, hy = size * 0.26f, s = size * 0.16f;
                head.moveTo(hx, hy - s);
                head.lineTo(hx + s * 0.7f, hy + s * 0.5f);
                head.lineTo(hx - s * 0.7f, hy + s * 0.4f);
                head.closePath();
                g.fill(head);
            }
        };
    }

    /** Check mark. */
    public static Icon check(int size, Color c) {
        return new Base(size, c) {
            @Override void draw(Graphics2D g) {
                g.setStroke(new BasicStroke(Math.max(1.6f, size * 0.15f),
                            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(Math.round(size * 0.2f), Math.round(size * 0.52f),
                           Math.round(size * 0.42f), Math.round(size * 0.74f));
                g.drawLine(Math.round(size * 0.42f), Math.round(size * 0.74f),
                           Math.round(size * 0.8f), Math.round(size * 0.26f));
            }
        };
    }

    /** Restore a deleted row: the circular arrow. */
    public static Icon restore(int size, Color c) {
        return retry(size, c);
    }

    /** Two arrows, opposite ways — swap the two QR fields. */
    public static Icon swap(int size, Color c) {
        return new Base(size, c) {
            @Override void draw(Graphics2D g) {
                g.setStroke(new BasicStroke(Math.max(1.4f, size * 0.12f),
                            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                float pad = size * 0.18f;
                float top = size * 0.34f, bot = size * 0.66f;
                float head = size * 0.15f;
                // top rail points right, bottom rail points left
                g.drawLine(Math.round(pad), Math.round(top),
                           Math.round(size - pad), Math.round(top));
                g.drawLine(Math.round(pad), Math.round(bot),
                           Math.round(size - pad), Math.round(bot));

                Path2D right = new Path2D.Float();
                right.moveTo(size - pad, top);
                right.lineTo(size - pad - head, top - head);
                right.lineTo(size - pad - head, top + head);
                right.closePath();
                g.fill(right);

                Path2D left = new Path2D.Float();
                left.moveTo(pad, bot);
                left.lineTo(pad + head, bot - head);
                left.lineTo(pad + head, bot + head);
                left.closePath();
                g.fill(left);
            }
        };
    }

    /** X — clear the fields. */
    public static Icon cross(int size, Color c) {
        return new Base(size, c) {
            @Override void draw(Graphics2D g) {
                g.setStroke(new BasicStroke(Math.max(1.5f, size * 0.14f),
                            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int a = Math.round(size * 0.26f), b = Math.round(size * 0.74f);
                g.drawLine(a, a, b, b);
                g.drawLine(b, a, a, b);
            }
        };
    }

    /** Curved arrow back — undo the last action. */
    public static Icon undo(int size, Color c) {
        return new Base(size, c) {
            @Override void draw(Graphics2D g) {
                g.setStroke(new BasicStroke(Math.max(1.4f, size * 0.12f),
                            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                float pad = size * 0.18f;
                Path2D arc = new Path2D.Float();
                arc.moveTo(pad + size * 0.08f, size * 0.42f);
                arc.lineTo(size * 0.62f, size * 0.42f);
                arc.curveTo(size * 0.92f, size * 0.42f, size * 0.92f, size * 0.82f,
                            size * 0.62f, size * 0.82f);
                arc.lineTo(size * 0.40f, size * 0.82f);
                g.draw(arc);
                Path2D head = new Path2D.Float();
                float hx = pad, hy = size * 0.42f, s = size * 0.17f;
                head.moveTo(hx, hy);
                head.lineTo(hx + s, hy - s);
                head.lineTo(hx + s, hy + s);
                head.closePath();
                g.fill(head);
            }
        };
    }

    /** Pencil — edit, or "changed by hand". */
    public static Icon pencil(int size, Color c) {
        return new Base(size, c) {
            @Override void draw(Graphics2D g) {
                Path2D body = new Path2D.Float();
                float w = size * 0.13f;
                // a slanted bar from bottom-left to top-right, with a pointed tip
                body.moveTo(size * 0.16f, size * 0.84f);
                body.lineTo(size * 0.22f, size * 0.62f);
                body.lineTo(size * 0.68f - w * 0.3f, size * 0.16f - w * 0.3f);
                body.lineTo(size * 0.84f + w * 0.3f, size * 0.32f + w * 0.3f);
                body.lineTo(size * 0.38f, size * 0.78f);
                body.closePath();
                g.fill(body);
            }
        };
    }

    /** Arrow leaving a tray — export a file. */
    public static Icon export(int size, Color c) {
        return new Base(size, c) {
            @Override void draw(Graphics2D g) {
                g.setStroke(new BasicStroke(Math.max(1.4f, size * 0.12f),
                            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int l = Math.round(size * 0.18f), r = Math.round(size * 0.82f);
                int top = Math.round(size * 0.56f), bot = Math.round(size * 0.84f);
                g.drawLine(l, top, l, bot);
                g.drawLine(l, bot, r, bot);
                g.drawLine(r, bot, r, top);
                int cx = size / 2;
                g.drawLine(cx, Math.round(size * 0.66f), cx, Math.round(size * 0.16f));
                g.drawLine(cx, Math.round(size * 0.16f),
                           Math.round(size * 0.30f), Math.round(size * 0.36f));
                g.drawLine(cx, Math.round(size * 0.16f),
                           Math.round(size * 0.70f), Math.round(size * 0.36f));
            }
        };
    }

    /** Folder. */
    public static Icon folder(int size, Color c) {
        return new Base(size, c) {
            @Override void draw(Graphics2D g) {
                Path2D p = new Path2D.Float();
                float l = size * 0.10f, r = size * 0.90f;
                float top = size * 0.22f, tab = size * 0.32f, bot = size * 0.82f;
                p.moveTo(l, top);
                p.lineTo(size * 0.40f, top);
                p.lineTo(size * 0.48f, tab);
                p.lineTo(r, tab);
                p.lineTo(r, bot);
                p.lineTo(l, bot);
                p.closePath();
                g.fill(p);
            }
        };
    }

    /** Filled dot — a state light. */
    public static Icon dot(int size, Color c) {
        return new Base(size, c) {
            @Override void draw(Graphics2D g) {
                float d = size * 0.56f, o = (size - d) / 2f;
                g.fill(new Ellipse2D.Float(o, o, d, d));
            }
        };
    }
}
