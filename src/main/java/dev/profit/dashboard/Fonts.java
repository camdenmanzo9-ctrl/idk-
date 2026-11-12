package dev.profit.dashboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * Sharp text. The Inter font is loaded in real sizes, each prepared at 2x, 3x and 4x
 * (matching Minecraft's GUI Scale). Text is drawn at its real size with no stretching,
 * so every letter lands on whole screen pixels.
 * "scale" keeps the old meaning: 1.0 = normal 8px text.
 */
public final class Fonts {
    private Fonts() {}

    private static final int[] SIZES = {6, 7, 8, 9, 10, 12, 14, 18};
    private static final int[] OVERSAMPLES = {2, 3, 4};
    private static final Style[][] REG = new Style[OVERSAMPLES.length][SIZES.length];
    private static final Style[][] BOLD = new Style[OVERSAMPLES.length][SIZES.length];

    static {
        for (int o = 0; o < OVERSAMPLES.length; o++) {
            String suffix = OVERSAMPLES[o] == 2 ? "" : "_o" + OVERSAMPLES[o];
            for (int i = 0; i < SIZES.length; i++) {
                REG[o][i] = Style.EMPTY.withFont(new FontDescription.Resource(
                        Identifier.fromNamespaceAndPath("bettors_dashboard", "inter_" + SIZES[i] + suffix)));
                BOLD[o][i] = Style.EMPTY.withFont(new FontDescription.Resource(
                        Identifier.fromNamespaceAndPath("bettors_dashboard", "inter_bold_" + SIZES[i] + suffix)));
            }
        }
    }

    /** Which prepared quality matches the current GUI Scale. */
    private static int quality() {
        try {
            int s = (int) Math.round(Minecraft.getInstance().getWindow().getGuiScale());
            if (s <= 2) return 0;
            if (s == 3) return 1;
            return 2;
        } catch (Exception e) {
            return 0;
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
        int q = quality();
        return Component.literal(s).withStyle(bold ? BOLD[q][i] : REG[q][i]);
    }

    public static void draw(GuiGraphicsExtractor g, Font f, String s, int x, int y, int color, float scale, boolean bold) {
        g.text(f, comp(s, pick(scale), bold), x, y, color, false);   // real size, no stretching
    }

    public static int width(Font f, String s, float scale, boolean bold) {
        return f.width(comp(s, pick(scale), bold));
    }

    /** Height of a capital letter (like "U") at this scale. */
    public static float capHeight(float scale) {
        return 0.73f * SIZES[pick(scale)];
    }

    /** Where the top of a capital letter lands, measured down from the y you draw at. */
    public static float capTop(float scale) {
        return 7f - 0.73f * SIZES[pick(scale)];
    }
}
