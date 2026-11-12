package dev.profit.dashboard;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The main dashboard page: welcome with your skin, Balance / Playtime / To Profit cards, payment feed and profit chart. */
public class OverviewScreen extends Screen {
    // purple theme
    private static final int BG = 0xFF060509, FRAME = 0xFF0D0B15, PANEL = 0xFF12101C, BORDER = 0xFF221E33,
            TEXT = 0xFFF2F0FA, MUTED = 0xFF8C88A3, DIM = 0xFF5F5B75, PURPLE = 0xFF8B5CF6, PURPLE_LIGHT = 0xFFB794F6,
            GOLD = 0xFFE0B24A, BLUE = 0xFF7B8CFF, RED = 0xFFFF5A6E, GREEN = 0xFF4ADE80, AMBER = 0xFFE0A030;

    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final String[] RANGE_LABELS = {"SESSION", "24H", "7D", "30D", "ALL"};
    private static final long[] RANGES = {-1, DAY, 7 * DAY, 30 * DAY, 0};   // -1 = current session
    private static int range = 2;           // 7D like the picture
    private static int filter = 0;          // 0 all, 1 in, 2 out
    private static boolean paused = false;
    private static long frozenAt = 0;

    private final List<Button> rangeButtons = new ArrayList<>();
    private final List<Button> filterButtons = new ArrayList<>();
    private Button pauseButton, settingsButton, sessionButton;
    private long confirmUntil = 0;

    public OverviewScreen() {
        super(Component.literal("Dashboard"));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private long now() { return paused ? frozenAt : System.currentTimeMillis(); }
    private long since() {
        long r = RANGES[range];
        if (r < 0) return PaymentLedger.sessionStartTime();
        return r == 0 ? 0 : now() - r;
    }

    private void togglePause() {
        paused = !paused;
        if (paused) frozenAt = System.currentTimeMillis();
    }

    /** 1-4 change the range, Space pauses, Esc closes (like the hints in the header). */
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int k = event.key();
        if (k >= GLFW.GLFW_KEY_1 && k <= GLFW.GLFW_KEY_5) { range = k - GLFW.GLFW_KEY_1; return true; }
        if (k == GLFW.GLFW_KEY_SPACE) { togglePause(); return true; }
        return super.keyPressed(event);
    }

    // ---------------- layout ----------------
    private int fx, fy, fw, fh;   // outer frame

    @Override
    protected void init() {
        fx = 8; fy = 6; fw = width - 16; fh = height - 12;
        rangeButtons.clear();
        filterButtons.clear();

        // right side of the header: [24H 7D 30D ALL]  [PAUSE]  [MORE] [SETTINGS]
        int bx = fx + fw - 10;
        settingsButton = btn("SETTINGS", bx - small("SETTINGS"), fy + 7, () -> {
            if (minecraft != null) minecraft.setScreen(new SettingsScreen(this));
        });
        bx -= small("SETTINGS") + 10;
        pauseButton = btn("PAUSE", bx - small("PAUSE"), fy + 7, this::togglePause);
        bx -= small("PAUSE") + 10;
        for (int i = RANGE_LABELS.length - 1; i >= 0; i--) {
            final int r = i;
            int w = small(RANGE_LABELS[i]);
            rangeButtons.add(0, btn(RANGE_LABELS[i], bx - w, fy + 7, () -> range = r));
            bx -= w + 2;
        }

        // START / END SESSION, right after "DATA LIVE"
        int sx = fx + 23 + capsW("DASHBOARD") + 16 + capsW("DATA LIVE") + 16;
        sessionButton = addRenderableWidget(Button.builder(Component.literal(""), b -> onSession())
                .bounds(sx, fy + 7, w("END SESSION", 0.75f) + 16, 12).build());

        // feed filter pills
        int[] feed = feedBox();
        int px = feed[0] + feed[2] - 8;
        String[] f = {"OUT", "IN", "ALL"};
        for (int i = 0; i < 3; i++) {
            final int idx = 2 - i;
            int w = small(f[i]);
            filterButtons.add(0, btn(f[i], px - w, feed[1] + 6, () -> filter = idx));
            px -= w + 2;
        }
    }

    private int capsW(String s) {
        int t = 0;
        for (char ch : s.toCharArray()) t += wb(String.valueOf(ch), 0.7f) + 1;
        return t;
    }

    private void onSession() {
        if (!PaymentLedger.isSessionActive()) {
            PaymentLedger.startSession();
            confirmUntil = 0;
            return;
        }
        long t = System.currentTimeMillis();
        if (t > confirmUntil) { confirmUntil = t + 3000; return; }   // first click asks to confirm
        confirmUntil = 0;
        PaymentLedger.endSession();
    }

    private int small(String s) { return w(s, 0.75f) + 12; }

    private Button btn(String label, int x, int y, Runnable r) {
        return addRenderableWidget(Button.builder(Component.literal(label), b -> r.run()).bounds(x, y, small(label), 12).build());
    }

    private int welcomeY() { return fy + 28; }
    private int cardsY() { return welcomeY() + 52; }
    private int cardsH() { return 62; }
    private int[] feedBox() {   // x, y, w, h
        int y = cardsY() + cardsH() + 8, h = fy + fh - 16 - y;
        int w = (fw - 30) * 36 / 100;
        return new int[]{fx + 10, y, w, h};
    }
    private int[] chartBox() {
        int[] f = feedBox();
        int x = f[0] + f[2] + 8;
        return new int[]{x, f[1], fx + fw - 10 - x, f[3]};
    }

    // ---------------- drawing helpers ----------------
    private void box(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + w - 1, y, x + w, y + h, border);
    }

    // Smooth Inter font bundled with the mod (assets/bettors_dashboard/font)
    private static final Style INTER = Style.EMPTY.withFont(
            new FontDescription.Resource(Identifier.fromNamespaceAndPath("bettors_dashboard", "inter")));
    private static final Style INTER_BOLD = Style.EMPTY.withFont(
            new FontDescription.Resource(Identifier.fromNamespaceAndPath("bettors_dashboard", "inter_bold")));

    private static Component c(String s, boolean bold) {
        return Component.literal(s).withStyle(bold ? INTER_BOLD : INTER);
    }

    private void text(GuiGraphicsExtractor g, String s, int x, int y, int color, float scale, boolean bold) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(font, c(s, bold), 0, 0, color, false);
        g.pose().popMatrix();
    }

    private int w(String s, float scale) { return (int) Math.ceil(font.width(c(s, false)) * scale); }
    private int wb(String s, float scale) { return (int) Math.ceil(font.width(c(s, true)) * scale); }

    /** Small spaced capitals like "PAYMENT FEED". */
    private int caps(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        int cx = x;
        for (char c : s.toCharArray()) {
            String t = String.valueOf(c);
            text(g, t, cx, y, color, 0.7f, true);
            cx += wb(t, 0.7f) + 1;
        }
        return cx - x;
    }

    private static boolean over(Button b, int mx, int my) {
        return mx >= b.getX() && mx < b.getX() + b.getWidth() && my >= b.getY() && my < b.getY() + b.getHeight();
    }

    private void pill(GuiGraphicsExtractor g, Button b, boolean on, int onColor, int mx, int my) {
        int x = b.getX(), y = b.getY(), bw = b.getWidth(), bh = b.getHeight();
        boolean hover = over(b, mx, my);
        if (on) g.fill(x, y, x + bw, y + bh, onColor);
        else box(g, x, y, bw, bh, hover ? 0xFF1E1A2E : PANEL, BORDER);
        String s = b.getMessage().getString();
        text(g, s, x + (bw - w(s, 0.75f)) / 2, y + 3, on ? TEXT : MUTED, 0.75f, false);
    }

    private static int alpha(int c, int a) { return (a << 24) | (c & 0xFFFFFF); }

    /** Soft round-ish glow: stacked see-through rectangles, strongest in the middle. */
    private void glow(GuiGraphicsExtractor g, int cx, int cy, int rx, int ry, int color, int strength) {
        // many very faint layers so there are no visible steps; corners trimmed so it reads as round
        int steps = 28;
        int per = Math.max(1, Math.round(strength / (float) steps));
        for (int i = steps; i >= 1; i--) {
            float t = i / (float) steps;
            int w = Math.round(rx * t), h = Math.round(ry * t);
            int cw = Math.round(w * 0.71f), ch = Math.round(h * 0.71f);
            g.fill(cx - cw, cy - h, cx + cw, cy + h, alpha(color, per));
            g.fill(cx - w, cy - ch, cx - cw, cy + ch, alpha(color, per));
            g.fill(cx + cw, cy - ch, cx + w, cy + ch, alpha(color, per));
        }
    }

    /** Small line chart with a soft fill under it. */
    private void spark(GuiGraphicsExtractor g, long[] vals, int x, int y, int w, int h, int color) {
        if (vals.length < 2) {
            g.fill(x, y + h - 1, x + w, y + h, alpha(color, 60));
            return;
        }
        long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        for (long v : vals) { min = Math.min(min, v); max = Math.max(max, v); }
        if (max == min) { max++; min--; }
        int last = -1;
        for (int px = 0; px < w; px++) {
            double t = (double) px * (vals.length - 1) / Math.max(1, w - 1);
            int i0 = (int) t, i1 = Math.min(vals.length - 1, i0 + 1);
            double v = vals[i0] + (vals[i1] - vals[i0]) * (t - i0);
            int yy = y + (int) Math.round((max - v) / (double) (max - min) * (h - 2)) + 1;
            g.fill(x + px, yy, x + px + 1, y + h, alpha(color, 28));
            int from = last < 0 ? yy : last;
            g.fill(x + px, Math.min(yy, from), x + px + 1, Math.max(yy, from) + 1, color);
            last = yy;
        }
    }

    private static String clock(long ts) {
        return java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", Locale.US)).toLowerCase(Locale.US);
    }

    private static String day(long ts) {
        return java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("MMM d", Locale.US));
    }

    private static String signedMoney(long v) {
        return (v > 0 ? "+" : v < 0 ? "-" : "") + Money.format(Math.abs(v));
    }

    private String rangeWord() {
        return switch (range) { case 0 -> "this session"; case 1 -> "today"; case 2 -> "this week"; case 3 -> "this month"; default -> "all time"; };
    }

    private void face(GuiGraphicsExtractor g, String name, int x, int y, int size) {
        try {
            var conn = minecraft == null ? null : minecraft.getConnection();
            var info = conn == null ? null : conn.getPlayerInfo(name);
            if (info != null) {
                PlayerFaceExtractor.extractRenderState(g, info.getSkin(), x, y, size);
                return;
            }
        } catch (Exception ignored) { }
        // player not online / no skin: plain placeholder head
        g.fill(x, y, x + size, y + size, 0xFF3A3450);
        g.fill(x + size / 4, y + size / 2, x + size / 4 + Math.max(1, size / 8), y + size / 2 + Math.max(1, size / 8), 0xFFDDD6F5);
        g.fill(x + size * 5 / 8, y + size / 2, x + size * 5 / 8 + Math.max(1, size / 8), y + size / 2 + Math.max(1, size / 8), 0xFFDDD6F5);
    }

    // ---------------- render ----------------
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, BG);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);

        // frame
        box(g, fx, fy, fw, fh, FRAME, BORDER);
        glow(g, fx + 60, welcomeY() + 22, 120, 34, PURPLE, 40);

        // ---- header ----
        int hy = fy + 9;
        // ring logo
        int lx = fx + 10;
        g.fill(lx + 1, hy - 1, lx + 7, hy, PURPLE);
        g.fill(lx + 1, hy + 8, lx + 7, hy + 9, PURPLE);
        g.fill(lx, hy, lx + 1, hy + 8, PURPLE);
        g.fill(lx + 7, hy, lx + 8, hy + 8, PURPLE);
        g.fill(lx + 3, hy + 3, lx + 5, hy + 5, PURPLE_LIGHT);
        int tx = lx + 13 + caps(g, "DASHBOARD", lx + 13, hy + 1, TEXT) + 10;
        g.fill(tx, hy + 2, tx + 3, hy + 5, paused ? AMBER : GREEN);
        caps(g, paused ? "PAUSED" : "DATA LIVE", tx + 6, hy + 1, MUTED);

        for (int i = 0; i < rangeButtons.size(); i++) pill(g, rangeButtons.get(i), i == range, PURPLE, mouseX, mouseY);
        // pause: amber outline like the picture
        {
            Button b = pauseButton;
            int x = b.getX(), y = b.getY(), bw = b.getWidth(), bh = b.getHeight();
            box(g, x, y, bw, bh, paused ? alpha(AMBER, 60) : PANEL, AMBER);
            String s = paused ? "RESUME" : "PAUSE";
            text(g, s, x + (bw - w(s, 0.75f)) / 2, y + 3, AMBER, 0.75f, false);
        }
        pill(g, settingsButton, false, PURPLE, mouseX, mouseY);
        {
            boolean active = PaymentLedger.isSessionActive();
            boolean confirming = confirmUntil > System.currentTimeMillis();
            Button b = sessionButton;
            int x = b.getX(), y = b.getY(), bw = b.getWidth(), bh = b.getHeight();
            int col = active ? RED : GREEN;
            box(g, x, y, bw, bh, confirming ? alpha(RED, 90) : over(b, mouseX, mouseY) ? 0xFF1E1A2E : PANEL, col);
            String s = confirming ? "CONFIRM?" : active ? "END SESSION" : "START SESSION";
            text(g, s, x + (bw - w(s, 0.75f)) / 2, y + 3, col, 0.75f, false);
            String timer = (active ? "" : "last ") + PaymentLedger.formatDuration(PaymentLedger.sessionMillis());
            text(g, timer, x + bw + 6, y + 3, active ? MUTED : DIM, 0.75f, false);
        }
        g.fill(fx + 1, fy + 25, fx + fw - 1, fy + 26, BORDER);

        // ---- welcome ----
        String me = minecraft != null ? minecraft.getUser().getName() : "you";
        int wy = welcomeY();
        g.fill(fx + 10, wy, fx + 54, wy + 44, alpha(PURPLE, 30));       // soft glow behind the skin
        face(g, me, fx + 14, wy + 4, 36);
        int nx = fx + 64;
        text(g, "Welcome, ", nx, wy + 10, TEXT, 1.6f, true);
        text(g, me, nx + wb("Welcome, ", 1.6f), wy + 10, PURPLE_LIGHT, 1.6f, true);
        text(g, "Have a great time on DonutSMP!", nx, wy + 28, MUTED, 0.75f, false);

        // ---- data for this range ----
        long since = since(), now = now();
        List<PaymentLedger.Payment> pays = new ArrayList<>();
        for (PaymentLedger.Payment p : PaymentLedger.paymentsSince(since)) if (p.ts <= now) pays.add(p);
        long net = 0;
        long[] netSeries = new long[pays.size() + 1];
        for (int i = 0; i < pays.size(); i++) {
            net += pays.get(i).paidYou - pays.get(i).youPaid;
            netSeries[i + 1] = net;
        }

        // ---- three cards ----
        int cy = cardsY(), ch = cardsH(), gap = 8, cw = (fw - 20 - gap * 2) / 3;
        int c1 = fx + 10, c2 = c1 + cw + gap, c3 = c2 + cw + gap;

        // Balance
        List<long[]> bal = PaymentLedger.balanceSince(since);
        long[] balVals = new long[bal.size()];
        for (int i = 0; i < bal.size(); i++) balVals[i] = bal.get(i)[1];
        long b = PaymentLedger.balance();
        String balSub = b < 0 ? "type /bal to load"
                : (balVals.length > 1 ? (balVals[balVals.length - 1] >= balVals[0] ? "^ " : "v ")
                + signedMoney(balVals[balVals.length - 1] - balVals[0]) + " " + rangeWord() : "updated from /bal");
        card(g, c1, cy, cw, ch, "BALANCE", b < 0 ? "$---" : Money.format(b), balSub, GOLD,
                balVals.length > 1 && balVals[balVals.length - 1] < balVals[0] ? RED : GREEN, balVals);

        // Playtime
        int days = switch (range) { case 0, 1 -> 1; case 2 -> 7; default -> 30; };
        long[] play = PaymentLedger.playLastDays(Math.max(2, days));
        long inRange = 0;
        for (int i = play.length - days; i < play.length; i++) if (i >= 0) inRange += play[i];
        String total = (PaymentLedger.playTotal() / 3_600_000L) + "h";
        String playSub = "+" + (inRange / 3_600_000L) + "h " + (inRange % 3_600_000L / 60_000L) + "m " + rangeWord();
        card(g, c2, cy, cw, ch, "PLAYTIME", total, playSub, BLUE, GREEN, play);

        // To profit
        String since0 = pays.isEmpty() ? "no payments yet" : "since " + day(pays.get(0).ts);
        card(g, c3, cy, cw, ch, "TO PROFIT", signedMoney(net), since0, net >= 0 ? GREEN : RED, MUTED, netSeries);

        // ---- payment feed ----
        int[] fb = feedBox();
        box(g, fb[0], fb[1], fb[2], fb[3], PANEL, BORDER);
        caps(g, "PAYMENT FEED", fb[0] + 8, fb[1] + 9, TEXT);
        for (int i = 0; i < filterButtons.size(); i++) pill(g, filterButtons.get(i), i == filter, PURPLE, mouseX, mouseY);

        List<PaymentLedger.Payment> feed = new ArrayList<>();
        for (int i = pays.size() - 1; i >= 0; i--) {
            PaymentLedger.Payment p = pays.get(i);
            boolean in = p.paidYou > 0;
            if (filter == 0 || (filter == 1 && in) || (filter == 2 && !in)) feed.add(p);
        }
        int rowH = 17, ry = fb[1] + 24, rows = Math.max(0, (fb[1] + fb[3] - 4 - ry) / rowH);
        for (int i = 0; i < Math.min(rows, feed.size()); i++) {
            PaymentLedger.Payment p = feed.get(i);
            boolean in = p.paidYou > 0;
            int y = ry + i * rowH;
            if (i % 2 == 0) g.fill(fb[0] + 4, y - 2, fb[0] + fb[2] - 4, y + rowH - 3, 0xFF181526);
            text(g, clock(p.ts), fb[0] + 8, y + 3, DIM, 0.75f, false);
            int ix = fb[0] + 8 + w("00:00 pm", 0.75f) + 6;
            face(g, p.name, ix, y, 11);
            text(g, p.name, ix + 15, y - 1, TEXT, 0.85f, false);
            text(g, in ? "paid you" : "you paid", ix + 15, y + 7, DIM, 0.65f, false);
            String amt = signedMoney(in ? p.paidYou : -p.youPaid);
            text(g, amt, fb[0] + fb[2] - 8 - wb(amt, 0.85f), y + 2, in ? GREEN : RED, 0.85f, true);
        }
        if (feed.isEmpty()) text(g, "No payments in this range", fb[0] + 8, ry + 2, DIM, 0.85f, false);

        // ---- profit / loss chart ----
        int[] cb = chartBox();
        box(g, cb[0], cb[1], cb[2], cb[3], PANEL, BORDER);
        glow(g, cb[0] + cb[2] * 3 / 4, cb[1] + cb[3] / 3, cb[2] / 4, cb[3] / 3, net >= 0 ? GREEN : RED, 30);
        int capW = caps(g, "PROFIT / LOSS", cb[0] + 8, cb[1] + 9, TEXT);
        text(g, "* " + (range == 0 ? "this session" : RANGE_LABELS[range].toLowerCase(Locale.US) + " of data"), cb[0] + 14 + capW, cb[1] + 9, DIM, 0.65f, false);
        String big = signedMoney(net);
        text(g, big, cb[0] + cb[2] - 8 - wb(big, 1.4f), cb[1] + 6, net >= 0 ? GREEN : RED, 1.4f, true);
        chart(g, netSeries, cb[0] + 8, cb[1] + 26, cb[2] - 16, cb[3] - 34);

        String foot = "Display only - data from your own chat";
        text(g, foot, fx + 10, fy + fh - 10, DIM, 0.65f, false);
        String esc = "1-5 range   SPACE pause   ESC close";
        text(g, esc, fx + fw - 10 - w(esc, 0.65f), fy + fh - 10, DIM, 0.65f, false);
    }

    private void card(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, String value, String sub,
                      int lineColor, int subColor, long[] series) {
        box(g, x, y, w, h, PANEL, BORDER);
        glow(g, x + w * 3 / 4, y + h / 3, w / 3, h / 2, lineColor, 36);
        g.fill(x, y, x + w, y + 1, lineColor);                        // colored top edge
        g.fill(x + w / 3, y, x + w * 2 / 3, y + 3, alpha(lineColor, 60)); // soft glow under it
        caps(g, label, x + 8, y + 7, MUTED);
        text(g, value, x + 8, y + 17, label.equals("TO PROFIT") ? lineColor : TEXT, 1.6f, true);
        text(g, sub, x + 8, y + 32, subColor, 0.65f, false);
        spark(g, series, x + 6, y + h - 20, w - 12, 16, lineColor);
    }

    /** Big profit/loss line: red below zero, green above, filled down to the zero line. */
    private void chart(GuiGraphicsExtractor g, long[] vals, int x, int y, int w, int h) {
        if (vals.length < 2) {
            text(g, "No payments in this range yet", x + 4, y + h / 2 - 4, DIM, 0.85f, false);
            return;
        }
        long min = 0, max = 0;
        for (long v : vals) { min = Math.min(min, v); max = Math.max(max, v); }
        long pad = Math.max(1, (max - min) / 12);
        final long hi = max + pad, lo = min - pad;
        int zy = y + (int) Math.round((double) hi / (hi - lo) * (h - 1));
        g.fill(x, zy, x + w, zy + 1, BORDER);
        int last = -1, lastX = x;
        for (int px = 0; px < w; px++) {
            double t = (double) px * (vals.length - 1) / Math.max(1, w - 1);
            int i0 = (int) t, i1 = Math.min(vals.length - 1, i0 + 1);
            double v = vals[i0] + (vals[i1] - vals[i0]) * (t - i0);
            int yy = y + (int) Math.round((hi - v) / (double) (hi - lo) * (h - 1));
            int col = v >= 0 ? GREEN : RED;
            g.fill(x + px, Math.min(yy, zy), x + px + 1, Math.max(yy, zy), alpha(col, 34));
            int from = last < 0 ? yy : last;
            g.fill(x + px, Math.min(yy, from), x + px + 1, Math.max(yy, from) + 1, col);
            last = yy;
            lastX = x + px;
        }
        int ec = vals[vals.length - 1] >= 0 ? GREEN : RED;
        g.fill(lastX - 1, last - 1, lastX + 2, last + 2, ec);
    }
}
