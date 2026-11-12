package dev.profit.dashboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The bet board: IGN, maximum, and games grouped by minimum bet. */
public final class Board {
    private Board() {}

    private static final int GOLD = 0xFFE8B04A, WHITE = 0xFFFFFFFF;

    private static int argb(int rgb, int a) { return (a << 24) | (rgb & 0xFFFFFF); }

    /** Letter-spaced text; returns its width. */
    private static int spaced(GuiGraphicsExtractor g, Font f, String s, int x, int y, int color, int gap, boolean bold) {
        int cx = x;
        for (char c : s.toCharArray()) {
            String t = String.valueOf(c);
            g.text(f, t, cx, y, color, false);
            if (bold) g.text(f, t, cx + 1, y, color, false);
            cx += f.width(t) + gap + (bold ? 1 : 0);
        }
        return cx - x;
    }

    private static int spacedWidth(Font f, String s, int gap, boolean bold) {
        int w = 0;
        for (char c : s.toCharArray()) w += f.width(String.valueOf(c)) + gap + (bold ? 1 : 0);
        return w;
    }

    private static void scaled(GuiGraphicsExtractor g, Font f, String s, int x, int y, float sc, int color, boolean bold) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(sc, sc);
        g.text(f, s, 0, 0, color, false);
        if (bold) g.text(f, s, 1, 0, color, false);
        g.pose().popMatrix();
    }

    // little drawn icons (no images needed)
    private static void icon(GuiGraphicsExtractor g, Font f, String kind, int x, int y) {
        String k = kind == null ? "" : kind;
        switch (k) {
            case "cards" -> {
                g.fill(x + 1, y, x + 8, y + 10, 0xFFF4F4F4);
                g.fill(x + 3, y + 3, x + 6, y + 7, 0xFFD23B3B);
            }
            case "roulette" -> {
                g.fill(x + 1, y + 1, x + 9, y + 9, 0xFFD23B3B);
                g.fill(x + 3, y + 3, x + 7, y + 7, 0xFF1E1E1E);
                g.fill(x + 4, y + 4, x + 6, y + 6, 0xFF2ECC71);
            }
            case "paper" -> {
                g.fill(x + 1, y + 1, x + 9, y + 9, 0xFFEDE6D6);
                g.fill(x + 2, y + 3, x + 8, y + 4, 0xFF9A9080);
                g.fill(x + 2, y + 6, x + 7, y + 7, 0xFF9A9080);
            }
            case "wheel" -> {
                g.fill(x + 1, y + 1, x + 9, y + 9, 0xFF6A3FC8);
                g.fill(x + 3, y + 3, x + 7, y + 7, 0xFFB58CFF);
            }
            case "arrows" -> {
                g.fill(x + 1, y + 4, x + 4, y + 5, 0xFF2ECC71);
                g.fill(x + 2, y + 2, x + 3, y + 9, 0xFF2ECC71);
                g.fill(x + 6, y + 6, x + 9, y + 7, 0xFFE74C3C);
                g.fill(x + 7, y + 1, x + 8, y + 8, 0xFFE74C3C);
            }
            case "horse" -> {
                g.fill(x + 1, y + 4, x + 7, y + 8, 0xFF3ADFFF);
                g.fill(x + 6, y + 1, x + 9, y + 5, 0xFF3ADFFF);
                g.fill(x + 1, y + 8, x + 2, y + 10, 0xFF3ADFFF);
                g.fill(x + 6, y + 8, x + 7, y + 10, 0xFF3ADFFF);
            }
            default -> g.text(f, k, x, y + 1, 0xFFBFC4D0, false);   // any short text, like "67"
        }
    }

    public static void render(GuiGraphicsExtractor g, Font f, Minecraft mc) {
        Settings s = Settings.S;
        if (!s.boardShow || s.boardTiers == null) return;

        // measure
        int rowH = 15, w = 0, h = 0;
        w = Math.max(w, spacedWidth(f, "IGN", 3, false) + 8 + (int) (f.width(s.boardIgn) * 2.2f) + 4);
        w = Math.max(w, f.width("Maximum: ") + 2 + spacedWidth(f, s.boardMax, 2, true));
        h += 24 + 14;
        for (Settings.BoardTier t : s.boardTiers) {
            h += 20;
            for (Settings.BoardGame gm : t.games) {
                int rw = 22 + f.width(gm.name) + 2 + (gm.note == null || gm.note.isEmpty() ? 0 : (int) (f.width(gm.note) * 0.75f) + 8) + 8;
                w = Math.max(w, rw);
                h += rowH + 2;
            }
            h += 4;
        }
        w += 4;

        float sc = s.boardSize;
        int sw = (int) (mc.getWindow().getGuiScaledWidth() / sc), sh = (int) (mc.getWindow().getGuiScaledHeight() / sc);
        int x = switch (s.boardPos) { case 1, 3, 5 -> sw - w - 8; default -> 8; };
        int y = switch (s.boardPos) { case 0, 1 -> 8; case 2, 3 -> sh - h - 40; default -> (sh - h) / 2; };

        g.pose().pushMatrix();
        g.pose().scale(sc, sc);

        // IGN  Name
        int cy = y;
        spaced(g, f, "IGN", x, cy + 6, GOLD, 3, false);
        scaled(g, f, s.boardIgn, x + spacedWidth(f, "IGN", 3, false) + 8, cy, 2.2f, WHITE, true);
        cy += 24;

        // Maximum: 2 BILLION MAX
        g.text(f, "Maximum:", x, cy, WHITE, false);
        g.text(f, "Maximum:", x + 1, cy, WHITE, false);
        spaced(g, f, s.boardMax, x + f.width("Maximum: ") + 3, cy, GOLD, 2, true);
        cy += 14;

        int bg = argb(0x0A0E14, Math.max(0, Math.min(255, s.boardOpacity * 255 / 100)));
        for (Settings.BoardTier t : s.boardTiers) {
            cy += 4;
            int mw = spaced(g, f, "MINIMUM", x, cy + 4, WHITE, 3, true);
            scaled(g, f, t.min, x + mw + 6, cy, 1.6f, GOLD, true);
            cy += 16;
            for (Settings.BoardGame gm : t.games) {
                g.fill(x, cy, x + w, cy + rowH, bg);
                g.fill(x, cy, x + 2, cy + rowH, GOLD);
                icon(g, f, gm.icon, x + 6, cy + 2);
                int nx = x + 20;
                g.text(f, gm.name, nx, cy + 4, WHITE, false);
                g.text(f, gm.name, nx + 1, cy + 4, WHITE, false);
                if (gm.note != null && !gm.note.isEmpty())
                    scaled(g, f, gm.note, nx + f.width(gm.name) + 6, cy + 5, 0.75f, GOLD, false);
                cy += rowH + 2;
            }
        }
        g.pose().popMatrix();
    }
}
