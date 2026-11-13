package dev.profit.dashboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
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
        if (Settings.S.streakShow) renderStreak(g, f, mc);
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
        int x = ((s.hudCorner % 2 == 0) ? 6 : sw - w - 6) + s.hudOffX;
        int y = ((s.hudCorner < 2) ? 6 : sh - h - 40) + s.hudOffY;
        lastProfitRect = new int[]{(int) (x * sc), (int) (y * sc), (int) (w * sc), (int) (h * sc)};

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
        Fonts.draw(g, f, s, x, y, color, scale, bold);
    }

    private static int iw(Font f, String s, float scale, boolean bold) {
        return Fonts.width(f, s, scale, bold);
    }

    private static void centered(GuiGraphicsExtractor g, Font f, String s, int cx, int y, int color, float scale, boolean bold) {
        itext(g, f, s, cx - iw(f, s, scale, bold) / 2, y, color, scale, bold);
    }

    /** Text with a soft dark shadow so it reads over the world. */
    private static void shadowed(GuiGraphicsExtractor g, Font f, String s, int cx, int y, int color, float scale, boolean bold) {
        int w = iw(f, s, scale, bold), x = cx - w / 2;
        itext(g, f, s, x + 1, y + 1, 0xA0000000, scale, bold);
        itext(g, f, s, x, y, color, scale, bold);
    }

    /** Auction floating in front of you: item name, the item big, timer, top bidder. No box. */
    private static void renderAuctionBox(GuiGraphicsExtractor g, Font f, Minecraft mc) {
        long now = System.currentTimeMillis();
        boolean justEnded = Auction.ended && now - Auction.endAt < 6000;
        if ((!Auction.running && !justEnded) || Auction.item.isEmpty()) return;

        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        int cx = sw / 2 + Settings.S.auctionOffX;
        int top = sh / 2 + 14 + Settings.S.auctionOffY;    // a little below the crosshair

        // item name
        shadowed(g, f, Auction.itemName, cx, top, 0xFFFFFFFF, 1.1f, true);

        // the item, 3x, gently bobbing
        int bob = (int) Math.round(Math.sin(now / 400.0) * 2);
        int size = 48;
        g.pose().pushMatrix();
        g.pose().translate(cx - size / 2f, top + 12 + bob);
        g.pose().scale(3f, 3f);
        g.item(Auction.item, 0, 0);
        g.pose().popMatrix();

        // timer (turns red in the last 10 seconds)
        int ty = top + 12 + size + 4;
        String t = Auction.running ? String.format("%d:%02d", Auction.left() / 60000, (Auction.left() / 1000) % 60)
                : (Auction.winner != null ? "SOLD" : "ENDED");
        int tc = !Auction.running ? 0xFFFFD25A : Auction.left() <= 10_000 ? 0xFFFF5A6E : Auction.color;
        shadowed(g, f, t, cx, ty, tc, 1.2f, true);

        // top bidder / winner
        String name = null;
        long amt = 0;
        if (Auction.running) {
            var bids = Auction.sortedBids();
            if (!bids.isEmpty()) { name = bids.get(0).getKey(); amt = bids.get(0).getValue(); }
        } else if (Auction.winner != null) {
            name = Auction.winner;
            amt = Auction.winAmount;
        }
        int by = ty + 14;
        if (name != null) {
            String amount = Money.format(amt);
            String gap = "  ";
            int nw = iw(f, name, 0.85f, true), gw = iw(f, gap, 0.85f, false), aw = iw(f, amount, 0.85f, true);
            int x0 = cx - (nw + gw + aw) / 2;
            itext(g, f, name, x0 + 1, by + 1, 0xA0000000, 0.85f, true);
            itext(g, f, name, x0, by, 0xFFFFFFFF, 0.85f, true);
            itext(g, f, amount, x0 + nw + gw + 1, by + 1, 0xA0000000, 0.85f, true);
            itext(g, f, amount, x0 + nw + gw, by, 0xFF4ADE80, 0.85f, true);
            shadowed(g, f, Auction.running ? "is winning" : "won!", cx, by + 11, 0xFFD0D3DE, 0.72f, false);
        } else {
            shadowed(g, f, Auction.running ? "No bids yet" : "No bids", cx, by, 0xFFD0D3DE, 0.8f, false);
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

    public static int[] lastProfitRect = {0, 0, 0, 0};

    // ---------------- win streak box ----------------
    public static int[] streakRect(Minecraft mc) {
        Settings s = Settings.S;
        int w = (int) (110 * s.streakSize), h = (int) (40 * s.streakSize);
        if (!s.streakSeparate && Settings.S.hudShow) {   // tucked under the profit box
            return new int[]{lastProfitRect[0], lastProfitRect[1] + lastProfitRect[3] + 4, w, h};
        }
        return new int[]{s.streakX, s.streakY, w, h};
    }

    private static void renderStreak(GuiGraphicsExtractor g, Font f, Minecraft mc) {
        Settings s = Settings.S;
        int[] r = streakRect(mc);
        int x = r[0], y = r[1];
        float sc = s.streakSize;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(sc, sc);
        int w = 110, h = 40;
        int bg = argb(0x0B0B14, Math.max(0, Math.min(255, s.streakOpacity * 255 / 100)));
        g.fill(0, 0, w, h, bg);
        if (s.streakBorder) outline(g, 0, 0, w, h, 0x40FFFFFF);
        int col = s.streak > 0 ? 0xFF4ADE80 : s.streak < 0 ? 0xFFFF5A6E : 0xFFFFFFFF;
        if (s.streakAccent) g.fill(0, 0, 2, h, col);
        itext(g, f, "WIN STREAK", 8, 6, 0xFFB4B8C8, 0.72f, true);
        String big = s.streak > 0 ? s.streak + (s.streak == 1 ? " WIN" : " WINS")
                : s.streak < 0 ? (-s.streak) + (s.streak == -1 ? " LOSS" : " LOSSES") : "0";
        itext(g, f, big, 8, 16, col, 1.3f, true);
        String tot = "W " + s.wins + "  -  L " + s.losses;
        itext(g, f, tot, w - 8 - iw(f, tot, 0.7f, false), 8, 0xFF9AA0AD, 0.7f, false);
        g.pose().popMatrix();
    }

    public static void addWin() {
        Settings s = Settings.S;
        s.wins++;
        s.streak = s.streak >= 0 ? s.streak + 1 : 1;
        Settings.save();
    }

    public static void addLoss() {
        Settings s = Settings.S;
        s.losses++;
        s.streak = s.streak <= 0 ? s.streak - 1 : -1;
        Settings.save();
    }

    public static void resetStreak() {
        Settings s = Settings.S;
        s.wins = 0; s.losses = 0; s.streak = 0;
        Settings.save();
    }

    // ---------------- big-payment popup (Popup alert + Popup style) ----------------
    private static void renderPopup(GuiGraphicsExtractor g, Font f, Minecraft mc) {
        Settings s = Settings.S;
        long now = System.currentTimeMillis();
        long age = now - current.start;
        long total = (long) (s.popupDuration * 1000);
        float in = Math.min(1f, age / 250f), out = Math.min(1f, Math.max(0f, (total + 300 - age) / 300f));
        float easeIn = 1f - (1f - in) * (1f - in) * (1f - in);
        float vis = s.popupAnim == 2 ? Math.min(in, out) : Math.min(Math.min(1f, in * 2f), out);
        if (vis <= 0) return;

        float sc = s.popupSize;
        int sw = (int) (mc.getWindow().getGuiScaledWidth() / sc);
        boolean auctionShowing = (Auction.running || (Auction.ended && now - Auction.endAt < 6000)) && !Auction.item.isEmpty();
        int baseY = 24;

        // count-up amount
        float countP = s.popupCountUp ? Math.min(1f, age / 700f) : 1f;
        countP = 1f - (1f - countP) * (1f - countP);
        long shown = (long) (current.amount * countP);
        String amt = (s.popupPlus ? "+" : "") + Money.format(shown);
        String amtFinal = (s.popupPlus ? "+" : "") + Money.format(current.amount);
        String line = current.name + " paid you";
        int faceW = s.popupFace ? 30 : 0;
        int w = Math.max(190, Math.max(iw(f, amtFinal, 2f, true) + 34 + faceW, iw(f, line, 0.85f, true) + 34 + faceW)), h = 58;
        int x = (sw - w) / 2, y = baseY;
        if (s.popupAnim == 1) y -= (int) ((1f - easeIn) * 30);   // slide down

        g.pose().pushMatrix();
        g.pose().scale(sc, sc);
        if (s.popupAnim == 0) {   // pop: grows in with a little overshoot
            float pop = in < 1f ? 0.8f + 0.25f * (float) Math.sin(in * Math.PI * 0.75) + 0.0f * in : 1f;
            pop = Math.min(pop, 1.05f);
            if (in >= 1f) pop = 1f;
            g.pose().translate(x + w / 2f, y + h / 2f);
            g.pose().scale(pop, pop);
            g.pose().translate(-(x + w / 2f), -(y + h / 2f));
        }
        int a = (int) (vis * 255);
        double t = now / 1500.0;

        if (s.popupGlow && s.popupGlowPct > 0) {
            int gc = s.popupRainbow ? rainbow(t) : argb(s.popupBorder, 255);
            int strength = (int) (vis * 22 * s.popupGlowPct / 100f);
            for (int i = 4; i >= 1; i--) g.fill(x - i * 2, y - i * 2, x + w + i * 2, y + h + i * 2, argb(gc, strength));
        }
        g.fill(x, y, x + w, y + h, argb(s.popupBg, (int) (vis * 255 * s.popupOpacity / 100f)));

        // border (width from Popup style)
        int bwid = Math.max(1, Math.min(4, s.popupBorderW));
        if (s.popupRainbow) {
            for (int px = 0; px < w; px += 3) {
                int c = rainbow(t + px / (double) w);
                g.fill(x + px, y, x + Math.min(w, px + 3), y + bwid, argb(c, a));
                g.fill(x + px, y + h - bwid, x + Math.min(w, px + 3), y + h, argb(c, a));
            }
        } else {
            g.fill(x, y, x + w, y + bwid, argb(s.popupBorder, a));
            g.fill(x, y + h - bwid, x + w, y + h, argb(s.popupBorder, a));
        }
        for (int py = 0; py < h; py += 3) {
            int c1 = s.popupRainbow ? rainbow(t + py / (double) h * 0.3) : argb(s.popupBorder, 255);
            int c2 = s.popupRainbow ? rainbow(t + 0.5 + py / (double) h * 0.3) : argb(s.popupBorder, 255);
            g.fill(x, y + py, x + bwid, y + Math.min(h, py + 3), argb(c1, a));
            g.fill(x + w - bwid, y + py, x + w, y + Math.min(h, py + 3), argb(c2, a));
        }

        if (s.popupShine) {
            int sx = x + (int) ((age % 1400) / 1400.0 * (w + 40)) - 20;
            for (int i = 0; i < 14; i++) {
                int col = Math.max(x + bwid, Math.min(x + w - bwid - 1, sx + i));
                g.fill(col, y + bwid, col + 1, y + h - bwid, argb(0xFFFFFF, (int) (vis * (14 - Math.abs(i - 7) * 2))));
            }
        }

        int tx = x + 12;
        if (s.popupFace) {
            int bounce = s.popupFaceBounce ? (int) Math.round(Math.abs(Math.sin(age / 160.0)) * 3 * Math.max(0, 1 - age / 1500.0)) : 0;
            try {
                var conn = mc.getConnection();
                var info = conn == null ? null : conn.getPlayerInfo(current.name);
                if (info != null) PlayerFaceExtractor.extractRenderState(g, info.getSkin(), x + 10, y + 16 - bounce, 24);
                else g.fill(x + 10, y + 16 - bounce, x + 34, y + 40 - bounce, argb(0x8B5CF6, a));
            } catch (Exception ignored) { }
            tx = x + 42;
        }
        itext(g, f, "BIG PAYMENT", tx, y + 8, argb(0x9AA0AD, a), 0.7f, true);
        itext(g, f, current.name, tx, y + 19, argb(s.popupName, a), 0.85f, true);
        itext(g, f, " paid you", tx + iw(f, current.name, 0.85f, true), y + 19, argb(0xFFFFFF, a), 0.85f, false);
        itext(g, f, amt, tx, y + 31, argb(s.popupAmount, a), 2f, true);

        // timer bar
        if (s.popupTimerBar) {
            float left = Math.max(0f, 1f - age / (float) total);
            g.fill(x + bwid, y + h - bwid - 2, x + bwid + (int) ((w - 2 * bwid) * left), y + h - bwid, argb(s.popupTimerColor, a));
        }

        if (s.popupSparkles) {
            int n = Math.max(0, Math.min(30, s.popupSparkleCount));
            for (int i = 0; i < n; i++) {
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
            if (s.taxPct > 0) {
                try {
                    long tax = Math.round(Money.parse(p.amountText()) * (s.taxPct / 100.0));
                    Style taxStyle = Style.EMPTY.withColor(TextColor.fromRgb(s.colorTax ? s.taxColor : 0xAAAAAA));
                    out.append(Component.literal(" (+" + Money.format(tax) + " tax)").withStyle(taxStyle));
                } catch (Exception ignored) { }
            }
        }
        return out;
    }
}
