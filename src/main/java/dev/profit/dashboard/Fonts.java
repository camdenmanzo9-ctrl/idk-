package dev.profit.dashboard;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * Sharp text: the Inter font is loaded in several real sizes, and each piece of text uses
 * the closest size instead of stretching one size (stretching is what makes text blurry).
 * "scale" keeps the old meaning: 1.0 = normal 8px text.
 */
public final class Fonts {
    private Fonts() {}

    private static final int[] SIZES = {6, 7, 8, 9, 10, 12, 14, 18};
    private static final Style[] REG = new Style[SIZES.length], BOLD = new Style[SIZES.length];

    static {
        for (int i = 0; i < SIZES.length; i++) {
            REG[i] = Style.EMPTY.withFont(new FontDescription.Resource(
                    Identifier.fromNamespaceAndPath("bettors_dashboard", "inter_" + SIZES[i])));
            BOLD[i] = Style.EMPTY.withFont(new FontDescription.Resource(
                    Identifier.fromNamespaceAndPath("bettors_dashboard", "inter_bold_" + SIZES[i])));
        }
    }

    private static int pick(float scale) {
        float target = 8f * scale;
        int best = 0;
        for (int i = 1; i < SIZES.length; i++)
            if (Math.abs(SIZES[i] - target) < Math.abs(SIZES[best] - target)) best = i;
        return best;
    }

    private static Component comp(String s, int i, boolean bold) {
        return Component.literal(s).withStyle(bold ? BOLD[i] : REG[i]);
    }

    public static void draw(GuiGraphicsExtractor g, Font f, String s, int x, int y, int color, float scale, boolean bold) {
        int i = pick(scale);
        float rest = 8f * scale / SIZES[i];          // tiny leftover adjustment, close to 1.0
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        if (Math.abs(rest - 1f) > 0.02f) g.pose().scale(rest, rest);
        g.text(f, comp(s, i, bold), 0, 0, color, false);
        g.pose().popMatrix();
    }

    /** Height of a capital letter (like "U") when drawn at this scale. */
    public static float capHeight(float scale) {
        int i = pick(scale);
        return 0.73f * SIZES[i] * (8f * scale / SIZES[i]);
    }

    /**
     * Where the top of a capital letter lands, measured down from the y you draw at.
     * Minecraft anchors fonts at their baseline, so big text sits higher than small text.
     */
    public static float capTop(float scale) {
        int i = pick(scale);
        float rest = 8f * scale / SIZES[i];
        return (7.5f - 0.73f * SIZES[i]) * rest;
    }

    public static int width(Font f, String s, float scale, boolean bold) {
        int i = pick(scale);
        float rest = 8f * scale / SIZES[i];
        return (int) Math.ceil(f.width(comp(s, i, bold)) * rest);
    }
}
