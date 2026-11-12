package dev.profit.dashboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayDeque;
import java.util.Deque;

/** Everything that shows up outside the dashboard: popup alert, profit box, sounds, chat colors. */
public final class Alerts {
    private Alerts() {}

    // ---------------- sounds ----------------
    public static final String[] SOUND_NAMES = {"Arrow Ding", "XP Orb", "Level Up", "Pling", "Bubble"};
    private static SoundEvent soundFor(int i) {
        return switch (Math.floorMod(i, SOUND_NAMES.length)) {
            case 0 -> SoundEvents.ARROW_HIT_PLAYER;
            case 1 -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case 2 -> SoundEvents.PLAYER_LEVELUP;
            case 3 -> SoundEvents.AMETHYST_BLOCK_CHIME;
            default -> SoundEvents.BUBBLE_POP;
        };
    }

    private static int repeatsLeft = 0, repeatSound = 0;
    private static long nextRepeat = 0;

    public static void playSound(int index, int times) {
        Settings s = Settings.S;
        Minecraft mc = Minecraft.getInstance();
        float vol = Math.max(0, Math.min(1, s.soundVolume / 100f));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(soundFor(index), s.soundPitch, vol));
        repeatsLeft = Math.max(0, times - 1);
        repeatSound = index;
        nextRepeat = System.currentTimeMillis() + 250;
    }

    public static void playConfirmSound() {
        if (Settings.S.soundConfirm) playSound(Settings.S.confirmSoundIndex, 1);
    }

    /** Called every client tick: handles sound repeats and the popup queue. */
    public static void tick() {
        long now = System.currentTimeMillis();
        if (repeatsLeft > 0 && now >= nextRepeat) {
            Settings s = Settings.S;
            float vol = Math.max(0, Math.min(1, s.soundVolume / 100f));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(soundFor(repeatSound), s.soundPitch, vol));
            repeatsLeft--;
            nextRepeat = now + 250;
        }
        if (current != null && now - current.start > (long) (Settings.S.popupDuration * 1000) + 300) current = null;
        if (current == null && !queue.isEmpty()) {
            current = queue.poll();
            current = new Popup(current.name, current.amount, now);
            if (Settings.S.soundPopup) playSound(Settings.S.soundIndex, Settings.S.soundRepeat);
        }
    }

    // ---------------- popup alert ----------------
    private record Popup(String name, long amount, long start) {}
    private static final Deque<Popup> queue = new ArrayDeque<>();
    private static Popup current = null;

    public static void onPayment(String name, long amount, boolean incoming) {
        Settings s = Settings.S;
        if (!incoming || !s.popupShow || amount < s.popupMin) return;
        if (current != null && s.soundPopup && s.soundWhenQueued) playSound(s.soundIndex, 1);
        queue.add(new Popup(name, amount, 0));
    }

    public static void testPopup() {
        queue.add(new Popup("ExamplePlayer", Math.max(Settings.S.popupMin, 5_000_000_000L), 0));
    }

    // ---------------- drawing (called from the HUD layer) ----------------
    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        Font f = mc.font;
        if (Settings.S.hudShow) renderProfitBox(g, f, mc);
        renderAuctionBox(g, f, mc);
        if (current != null) renderPopup(g, f, mc);
    }

    private static int argb(int rgb, int alpha) { return (alpha << 24) | (rgb & 0xFFFFFF); }

    private static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    private static void renderProfitBox(GuiGraphicsExtractor g, Font f, Minecraft mc) {
        Settings s = Settings.S;

        // Today's profit (since midnight) - works without sessions
        long midnight = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        long profit = 0;
        int count = 0;
        for (PaymentLedger.Payment p : PaymentLedger.paymentsSince(midnight)) { profit += p.paidYou - p.youPaid; count++; }
        String value = (profit > 0 ? "+" : profit < 0 ? "-" : "") + Money.format(Math.abs(profit));
        int vc = profit > 0 ? s.hudGain : profit < 0 ? s.hudLoss : s.hudZero;
        String sub = "today  " + count + (count == 1 ? " payment" : " payments");
        boolean active = true;

        int w = Math.max(110, Math.max(f.width(value) * 2 + 20, f.width(sub) + 20)), h = 44;
        float sc = s.hudSize;
        int sw = (int) (mc.getWindow().getGuiScaledWidth() / sc), sh = (int) (mc.getWindow().getGuiScaledHeight() / sc);
        int x = (s.hudCorner % 2 == 0) ? 6 : sw - w - 6;
        int y = (s.hudCorner < 2) ? 6 : sh - h - 40;

        g.pose().pushMatrix();
        g.pose().scale(sc, sc);
        int alpha = Math.max(0, Math.min(255, s.hudOpacity * 255 / 100));
        g.fill(x, y, x + w, y + h, argb(s.hudBg, alpha));
        if (s.hudBorder) outline(g, x, y, w, h, 0x40FFFFFF);
        if (s.hudAccentBar) g.fill(x, y, x + 2, y + h, argb(s.hudAccentColor, 255));
        g.text(f, "PROFIT", x + 8, y + 6, argb(s.hudTitle, 255), false);
        g.pose().pushMatrix();
        g.pose().translate(x + 8, y + 17);
        g.pose().scale(2f, 2f);
        g.text(f, value, 0, 0, argb(vc, 255), false);
        g.pose().popMatrix();
        g.text(f, sub, x + 8, y + h - 10, 0xFF9AA0AD, false);
        g.pose().popMatrix();
    }

    // ---------------- auction box (shows while an auction runs) ----------------
    private static final Style INTER = Style.EMPTY.withFont(
            new FontDescription.Resource(Identifier.fromNamespaceAndPath("bettors_dashboard", "inter")));
    private static final Style INTER_BOLD = Style.EMPTY.withFont(
            new FontDescription.Resource(Identifier.fromNamespaceAndPath("bettors_dashboard", "inter_bold")));

    private static void itext(GuiGraphicsExtractor g, Font f, String s, int x, int y, int color, float scale, boolean bold) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(f, Component.literal(s).withStyle(bold ? INTER_BOLD : INTER), 0, 0, color, false);
        g.pose().popMatrix();
    }

    private static int iw(Font f, String s, float scale, boolean bold) {
        return (int) Math.ceil(f.width(Component.literal(s).withStyle(bold ? INTER_BOLD : INTER)) * scale);
    }

    private static void centered(GuiGraphicsExtractor g, Font f, String s, int cx, int y, int color, float scale, boolean bold) {
        itext(g, f, s, cx - iw(f, s, scale, bold) / 2, y, color, scale, bold);
    }

    private static void renderAuctionBox(GuiGraphicsExtractor g, Font f, Minecraft mc) {
        long now = System.currentTimeMillis();
        boolean justEnded = Auction.ended && now - Auction.endAt < 6000;
        if ((!Auction.running && !justEnded) || Auction.item.isEmpty()) return;

        int w = 104, h = 96;
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        int x = sw - w - 8, y = sh / 2 - h / 2;
        int cx = x + w / 2;

        // soft rounded panel
        int bg = 0xD80E0C16, bd = 0x40FFFFFF;
        g.fill(x + 2, y, x + w - 2, y + h, bg);
        g.fill(x, y + 2, x + w, y + h - 2, bg);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
        g.fill(x + 2, y, x + w - 2, y + 1, bd);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, bd);
        g.fill(x, y + 2, x + 1, y + h - 2, bd);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, bd);

        // spaced title
        String title = "AUCTION";
        int tw = 0;
        for (char c : title.toCharArray()) tw += iw(f, String.valueOf(c), 0.7f, true) + 2;
        int tx = cx - tw / 2;
        for (char c : title.toCharArray()) {
            String ch = String.valueOf(c);
            itext(g, f, ch, tx, y + 6, 0xFFE8E6F0, 0.7f, true);
            tx += iw(f, ch, 0.7f, true) + 2;
        }

        // ring: lit part = time left, in the chosen timer color
        int ccy = y + 40, rad = 20;
        float frac = Auction.running ? Auction.left() / (float) Auction.durationMs : 0f;
        for (int a = 0; a < 360; a += 2) {
            double ang = Math.toRadians(a - 90);
            int px = cx + (int) Math.round(Math.cos(ang) * rad), py = ccy + (int) Math.round(Math.sin(ang) * rad);
            boolean lit = a < frac * 360;
            g.fill(px - 1, py - 1, px + 2, py + 2, lit ? Auction.color : 0x50FFFFFF);
        }
        g.item(Auction.item, cx - 8, ccy - 13);
        String t = Auction.running ? String.format("%d:%02d", Auction.left() / 60000, (Auction.left() / 1000) % 60)
                : (Auction.winner != null ? "SOLD" : "ENDED");
        centered(g, f, t, cx, ccy + 5, 0xFFFFFFFF, 0.8f, true);

        // leader / winner
        String name = null;
        long amt = 0;
        if (Auction.running) {
            var bids = Auction.sortedBids();
            if (!bids.isEmpty()) { name = bids.get(0).getKey(); amt = bids.get(0).getValue(); }
        } else if (Auction.winner != null) {
            name = Auction.winner;
            amt = Auction.winAmount;
        }
        if (name != null) {
            centered(g, f, name + " " + Money.format(amt), cx, y + 68, 0xFF4ADE80, 0.8f, true);
            centered(g, f, Auction.running ? "is winning" : "won!", cx, y + 80, 0xFF9AA0AD, 0.65f, false);
        } else {
            centered(g, f, Auction.running ? "No bids yet" : "No bids", cx, y + 72, 0xFF9AA0AD, 0.7f, false);
        }
    }

    private static int rainbow(double t) {
        double h = (t % 1 + 1) % 1 * 6;
        int i = (int) h;
        double fr = h - i;
        int q = (int) (255 * (1 - fr)), tt = (int) (255 * fr);
        int r, gg, b;
        switch (i) {
            case 0 -> { r = 255; gg = tt; b = 0; }
            case 1 -> { r = q; gg = 255; b = 0; }
            case 2 -> { r = 0; gg = 255; b = tt; }
            case 3 -> { r = 0; gg = q; b = 255; }
            case 4 -> { r = tt; gg = 0; b = 255; }
            default -> { r = 255; gg = 0; b = q; }
        }
        return 0xFF000000 | (r << 16) | (gg << 8) | b;
    }

    private static void renderPopup(GuiGraphicsExtractor g, Font f, Minecraft mc) {
        Settings s = Settings.S;
        long now = System.currentTimeMillis();
        long age = now - current.start;
        long total = (long) (s.popupDuration * 1000);
        float in = Math.min(1f, age / 200f), out = Math.min(1f, Math.max(0f, (total + 300 - age) / 300f));
        float vis = Math.min(in, out);
        if (vis <= 0) return;

        float sc = s.popupSize;
        int sw = (int) (mc.getWindow().getGuiScaledWidth() / sc);
        String amt = "+" + Money.format(current.amount);
        String line = current.name + " paid you";
        int w = Math.max(180, Math.max(f.width(amt) * 2 + 30, f.width(line) + 30)), h = 54;
        int x = (sw - w) / 2, y = 24 - (int) ((1 - in) * 14);

        g.pose().pushMatrix();
        g.pose().scale(sc, sc);
        int a = (int) (vis * 255);
        double t = now / 1500.0;

        if (s.popupGlow) {
            int gc = s.popupRainbow ? rainbow(t) : argb(s.popupBorder, 255);
            for (int i = 4; i >= 1; i--) g.fill(x - i * 2, y - i * 2, x + w + i * 2, y + h + i * 2, argb(gc, (int) (vis * 18)));
        }
        g.fill(x, y, x + w, y + h, argb(s.popupBg, (int) (vis * 235)));

        // border: rainbow runs around the edge, or one solid color
        for (int px = 0; px < w; px++) {
            int c = s.popupRainbow ? rainbow(t + px / (double) w) : argb(s.popupBorder, 255);
            g.fill(x + px, y, x + px + 1, y + 1, argb(c, a));
            g.fill(x + px, y + h - 1, x + px + 1, y + h, argb(c, a));
        }
        for (int py = 0; py < h; py++) {
            int c1 = s.popupRainbow ? rainbow(t + py / (double) h * 0.3) : argb(s.popupBorder, 255);
            int c2 = s.popupRainbow ? rainbow(t + 0.5 + py / (double) h * 0.3) : argb(s.popupBorder, 255);
            g.fill(x, y + py, x + 1, y + py + 1, argb(c1, a));
            g.fill(x + w - 1, y + py, x + w, y + py + 1, argb(c2, a));
        }

        if (s.popupShine) {
            int sx = x + (int) ((age % 1400) / 1400.0 * (w + 40)) - 20;
            for (int i = 0; i < 14; i++) {
                int col = Math.max(x + 1, Math.min(x + w - 1, sx + i));
                g.fill(col, y + 1, col + 1, y + h - 1, argb(0xFFFFFF, (int) (vis * (14 - Math.abs(i - 7) * 2))));
            }
        }

        g.text(f, "BIG PAYMENT", x + 12, y + 8, argb(0x9AA0AD, a), false);
        g.text(f, current.name, x + 12, y + 20, argb(s.popupName, a), false);
        g.text(f, " paid you", x + 12 + f.width(current.name), y + 20, argb(0xFFFFFF, a), false);
        g.pose().pushMatrix();
        g.pose().translate(x + 12, y + 32);
        g.pose().scale(2f, 2f);
        g.text(f, amt, 0, 0, argb(s.popupAmount, a), false);
        g.pose().popMatrix();

        if (s.popupSparkles) {
            for (int i = 0; i < 14; i++) {
                int hx = (i * 73856093) ^ 0x5bd1e995, hy = (i * 19349663) ^ 0x27d4eb2d;
                int px = x - 6 + Math.abs(hx % (w + 12)), py = y - 6 + Math.abs(hy % (h + 12));
                double tw = Math.sin(now / 180.0 + i * 1.7);
                if (tw > 0.3) {
                    int c = argb(0xFFFFFF, (int) (vis * 220 * tw));
                    g.fill(px, py - 1, px + 1, py + 2, c);
                    g.fill(px - 1, py, px + 2, py + 1, c);
                }
            }
        }
        g.pose().popMatrix();
    }

    // ---------------- chat colors ----------------
    public static Component recolor(Component message) {
        Settings s = Settings.S;
        if (!s.chatColors) return message;
        PaymentListener.Parsed p = PaymentListener.parse(message.getString());
        if (p == null) return message;

        Style nameStyle = Style.EMPTY.withColor(TextColor.fromRgb(s.nameColor)).withBold(s.boldNames);
        Style amtStyle = Style.EMPTY.withColor(TextColor.fromRgb(p.incoming() ? s.receivedColor : s.sentColor))
                .withBold(s.boldAmounts);
        String amount = "$" + p.amountText().trim();
        MutableComponent out = Component.literal("");
        if (p.incoming()) {
            out.append(Component.literal(p.name()).withStyle(nameStyle));
            out.append(Component.literal(" paid you "));
            out.append(Component.literal(amount).withStyle(amtStyle));
        } else {
            out.append(Component.literal("You paid "));
            out.append(Component.literal(p.name()).withStyle(nameStyle));
            out.append(Component.literal(" "));
            out.append(Component.literal(amount).withStyle(amtStyle));
        }
        return out;
    }
}
