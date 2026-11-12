package dev.profit.dashboard;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Snowy's Dashboard: Overview, Win / Loss, Rakeback and Sessions tabs in the purple style. */
public class OverviewScreen extends Screen {
    // ---------------- theme ----------------
    private static final int BG = 0xFF060509, FRAME = 0xFF0D0B15, PANEL = 0xFF12101C, BORDER = 0xFF221E33,
            TEXT = 0xFFF2F0FA, MUTED = 0xFF8C88A3, DIM = 0xFF5F5B75, PURPLE = 0xFF8B5CF6, PURPLE_LIGHT = 0xFFB794F6,
            GOLD = 0xFFE0B24A, BLUE = 0xFF7B8CFF, RED = 0xFFFF5A6E, GREEN = 0xFF4ADE80, AMBER = 0xFFE0A030;

    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final String[] RANGE_LABELS = {"24H", "7D", "30D", "ALL"};
    private static final long[] RANGES = {DAY, 7 * DAY, 30 * DAY, 0};
    private static final String[] TAB_LABELS = {"OVERVIEW", "WIN / LOSS", "RAKEBACK", "SESSIONS", "PROFIT PANEL"};

    private static int tab = 0;
    private static int range = 1;           // 7D like the picture
    private static int filter = 0;          // 0 all, 1 in, 2 out
    private static boolean paused = false;
    private static long frozenAt = 0;

    private final List<Button> rangeButtons = new ArrayList<>();
    private final List<Button> tabButtons = new ArrayList<>();
    private final List<Button> filterButtons = new ArrayList<>();
    private Button settingsButton, sessionButton, headerSettings;
    private EditBox searchBox;
    private static String search = "";
    private final List<Button> panelButtons = new ArrayList<>();
    private long confirmUntil = 0;
    private static int scroll = 0;

    private int fx, fy, fw, fh, escX;

    public OverviewScreen() {
        super(Component.literal("Snowy's Dashboard"));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private long now() { return paused ? frozenAt : System.currentTimeMillis(); }
    private long since() { return RANGES[range] == 0 ? 0 : now() - RANGES[range]; }

    // ---------------- font ----------------
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

    private int caps(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        int cx = x;
        for (char ch : s.toCharArray()) {
            String t = String.valueOf(ch);
            text(g, t, cx, y, color, 0.7f, true);
            cx += wb(t, 0.7f) + 1;
        }
        return cx - x;
    }

    private int capsW(String s) {
        int t = 0;
        for (char ch : s.toCharArray()) t += wb(String.valueOf(ch), 0.7f) + 1;
        return t;
    }

    // ---------------- keys ----------------
    private void togglePause() {
        paused = !paused;
        if (paused) frozenAt = System.currentTimeMillis();
    }

    /** 1-4 change the range, Space pauses, Tab switches pages, Esc closes. */
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int k = event.key();
        if (searchBox != null && searchBox.isFocused() && k != GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(event);
        if (k >= GLFW.GLFW_KEY_1 && k <= GLFW.GLFW_KEY_4) { range = k - GLFW.GLFW_KEY_1; return true; }
        if (k == GLFW.GLFW_KEY_SPACE) { togglePause(); return true; }
        if (k == GLFW.GLFW_KEY_TAB) { tab = (tab + 1) % TAB_LABELS.length; scroll = 0; rebuildWidgets(); return true; }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY) * 3);
        return true;
    }

    // ---------------- layout ----------------
    private int small(String s) { return w(s, 0.75f) + 12; }

    private Button btn(String label, int x, int y, int bw, Runnable r) {
        return addRenderableWidget(Button.builder(Component.literal(label), b -> r.run()).bounds(x, y, bw, 12).build());
    }

    private int contentY() { return fy + 30; }
    private int contentBottom() { return fy + fh - 14; }

    @Override
    protected void init() {
        fx = 8; fy = 6; fw = width - 16; fh = height - 12;
        rangeButtons.clear();
        tabButtons.clear();
        filterButtons.clear();
        panelButtons.clear();
        sessionButton = null;

        // right: [24H 7D 30D ALL]
        int bx = fx + fw - 10;
        for (int i = RANGE_LABELS.length - 1; i >= 0; i--) {
            final int r = i;
            int bw = small(RANGE_LABELS[i]);
            rangeButtons.add(0, btn(RANGE_LABELS[i], bx - bw, fy + 8, bw, () -> range = r));
            bx -= bw + 3;
        }
        // SETTINGS button (opens the green settings screen), then "ESC close" to its left
        int setW = small("SETTINGS");
        headerSettings = btn("SETTINGS", bx - 4 - setW, fy + 8, setW, () -> {
            if (minecraft != null) minecraft.setScreen(new SettingsScreen(this));
        });
        escX = bx - 4 - setW - 8;   // "ESC close" is drawn here
        settingsButton = null;
        searchBox = null;

        // center: tabs
        int tabsW = 0;
        for (String t : TAB_LABELS) tabsW += small(t) + 4;
        int tx = fx + (fw - tabsW) / 2;
        for (int i = 0; i < TAB_LABELS.length; i++) {
            final int t = i;
            int bw = small(TAB_LABELS[i]);
            tabButtons.add(btn(TAB_LABELS[i], tx, fy + 8, bw, () -> { tab = t; scroll = 0; rebuildWidgets(); }));
            tx += bw + 4;
        }

        if (tab == 0) {
            // session badge on the right of the welcome row
            int bw = w("SESSION LIVE  0:00:00", 0.75f) + 20;
            sessionButton = btn("", fx + fw - 10 - bw, welcomeY() + 14, bw, this::onSession);
            // feed filter pills
            int[] feed = feedBox();
            int px = feed[0] + feed[2] - 8;
            String[] f = {"OUT", "IN", "ALL"};
            for (int i = 0; i < 3; i++) {
                final int idx = 2 - i;
                int pw = small(f[i]);
                filterButtons.add(0, btn(f[i], px - pw, feed[1] + 6, pw, () -> filter = idx));
                px -= pw + 3;
            }
        } else if (tab == 1 || tab == 2) {
            int sbw = Math.min(200, fw / 3), sbx = fx + fw - 10 - sbw, sby = contentY() + 26;
            searchBox = new EditBox(font, sbx + 18, sby + 8, sbw - 24, 12, Component.literal("Search"));
            searchBox.setBordered(false);
            searchBox.setMaxLength(32);
            searchBox.setValue(search);
            searchBox.setHint(Component.literal("Search players"));
            searchBox.setResponder(v -> { search = v; scroll = 0; });
            addRenderableWidget(searchBox);
        } else if (tab == 4) {
            Panels.load();
            int sbw = small("OPEN SETTINGS") + 10;
            settingsButton = btn("OPEN SETTINGS", fx + fw - 10 - sbw, contentY() + 2, sbw, () -> {
                if (minecraft != null) minecraft.setScreen(new SettingsScreen(this));
            });
            int y = contentY() + 30, rowH = 44, x = fx + 10, rw = fw - 20;
            for (int i = 0; i < 3; i++) {
                final int idx = i;
                panelButtons.add(addRenderableWidget(Button.builder(Component.literal(""), b -> {
                    if (idx == 0) Panels.profit = !Panels.profit;
                    else if (idx == 1) Panels.rakeback = !Panels.rakeback;
                    else Panels.balance = !Panels.balance;
                    Panels.save();
                }).bounds(x, y + i * (rowH + 8), rw, rowH).build()));
            }
        } else if (tab == 3) {
            int bw = w("START SESSION", 0.85f) + 24;
            sessionButton = addRenderableWidget(Button.builder(Component.literal(""), b -> onSession())
                    .bounds(fx + fw - 10 - bw - 10, contentY() + 18, bw, 16).build());
        }
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

    private int welcomeY() { return contentY(); }
    private int cardsY() { return welcomeY() + 48; }
    private int cardsH() { return 64; }
    private int[] feedBox() {
        int y = cardsY() + cardsH() + 8, h = contentBottom() - y;
        int bw = (fw - 30) * 36 / 100;
        return new int[]{fx + 10, y, bw, h};
    }
    private int[] chartBox() {
        int[] f = feedBox();
        int x = f[0] + f[2] + 8;
        return new int[]{x, f[1], fx + fw - 10 - x, f[3]};
    }

    // ---------------- drawing helpers ----------------
    private void box(GuiGraphicsExtractor g, int x, int y, int bw, int bh, int fill, int border) {
        g.fill(x, y, x + bw, y + bh, fill);
        g.fill(x, y, x + bw, y + 1, border);
        g.fill(x, y + bh - 1, x + bw, y + bh, border);
        g.fill(x, y, x + 1, y + bh, border);
        g.fill(x + bw - 1, y, x + bw, y + bh, border);
    }

    private static int alpha(int col, int a) { return (a << 24) | (col & 0xFFFFFF); }

    private void glow(GuiGraphicsExtractor g, int cx, int cy, int rx, int ry, int color, int strength) {
        int steps = 28;
        int per = Math.max(1, Math.round(strength / (float) steps));
        for (int i = steps; i >= 1; i--) {
            float t = i / (float) steps;
            int gw = Math.round(rx * t), gh = Math.round(ry * t);
            int cw = Math.round(gw * 0.71f), ch = Math.round(gh * 0.71f);
            g.fill(cx - cw, cy - gh, cx + cw, cy + gh, alpha(color, per));
            g.fill(cx - gw, cy - ch, cx - cw, cy + ch, alpha(color, per));
            g.fill(cx + cw, cy - ch, cx + gw, cy + ch, alpha(color, per));
        }
    }

    private static boolean over(Button b, int mx, int my) {
        return b != null && mx >= b.getX() && mx < b.getX() + b.getWidth() && my >= b.getY() && my < b.getY() + b.getHeight();
    }

    private void pill(GuiGraphicsExtractor g, Button b, boolean on, int mx, int my) {
        int x = b.getX(), y = b.getY(), bw = b.getWidth(), bh = b.getHeight();
        boolean hover = over(b, mx, my);
        if (on) g.fill(x, y, x + bw, y + bh, PURPLE);
        else box(g, x, y, bw, bh, hover ? 0xFF1E1A2E : PANEL, BORDER);
        String s = b.getMessage().getString();
        text(g, s, x + (bw - wb(s, 0.7f)) / 2, y + 3, on ? TEXT : MUTED, 0.7f, true);
    }

    private void spark(GuiGraphicsExtractor g, long[] vals, int x, int y, int sw, int sh, int color) {
        if (vals.length < 2) {
            g.fill(x, y + sh - 1, x + sw, y + sh, alpha(color, 60));
            return;
        }
        long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        for (long v : vals) { min = Math.min(min, v); max = Math.max(max, v); }
        if (max == min) { max++; min--; }
        int last = -1;
        for (int px = 0; px < sw; px++) {
            double t = (double) px * (vals.length - 1) / Math.max(1, sw - 1);
            int i0 = (int) t, i1 = Math.min(vals.length - 1, i0 + 1);
            double v = vals[i0] + (vals[i1] - vals[i0]) * (t - i0);
            int yy = y + (int) Math.round((max - v) / (double) (max - min) * (sh - 2)) + 1;
            g.fill(x + px, yy, x + px + 1, y + sh, alpha(color, 34));
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

    private static String dayTime(long ts) {
        return java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US));
    }

    private static String signedMoney(long v) {
        return (v > 0 ? "+" : v < 0 ? "-" : "") + Money.format(Math.abs(v));
    }

    private String rangeWord() {
        return switch (range) { case 0 -> "today"; case 1 -> "this week"; case 2 -> "this month"; default -> "all time"; };
    }

    private void face(GuiGraphicsExtractor g, String name, int x, int y, int size) {
        if (Settings.S.skinFaces) try {
            var conn = minecraft == null ? null : minecraft.getConnection();
            var info = conn == null ? null : conn.getPlayerInfo(name);
            if (info != null) {
                PlayerFaceExtractor.extractRenderState(g, info.getSkin(), x, y, size);
                return;
            }
        } catch (Exception ignored) { }
        // not online: purple circle-ish badge with the first letter, like the picture
        g.fill(x + 1, y, x + size - 1, y + size, PURPLE);
        g.fill(x, y + 1, x + size, y + size - 1, PURPLE);
        String l = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.US);
        float sc = size / 14f;
        text(g, l, x + (size - wb(l, sc)) / 2, y + (int) (size * 0.22f), TEXT, sc, true);
    }

    /** Payments in the current range, oldest first. */
    private List<PaymentLedger.Payment> rangePayments() {
        long since = since(), now = now();
        List<PaymentLedger.Payment> out = new ArrayList<>();
        for (PaymentLedger.Payment p : PaymentLedger.paymentsSince(since)) if (p.ts <= now) out.add(p);
        return out;
    }

    /** Your net per player in the current range (+ = you won from them). */
    private Map<String, long[]> perPlayer(List<PaymentLedger.Payment> pays) {
        Map<String, long[]> m = new LinkedHashMap<>();   // name -> {net, paidYou, youPaid, count}
        for (PaymentLedger.Payment p : pays) {
            long[] a = m.computeIfAbsent(p.name, k -> new long[4]);
            a[0] += p.paidYou - p.youPaid;
            a[1] += p.paidYou;
            a[2] += p.youPaid;
            a[3]++;
        }
        return m;
    }

    // ---------------- render ----------------
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, BG);
        box(g, fx, fy, fw, fh, FRAME, BORDER);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        drawHeader(g, mouseX, mouseY);
        switch (tab) {
            case 0 -> drawOverview(g, mouseX, mouseY);
            case 1 -> drawWinLoss(g);
            case 2 -> drawRakeback(g);
            case 3 -> drawSessions(g, mouseX, mouseY);
            default -> drawPanels(g, mouseX, mouseY);
        }
        String foot = "1-4 range   SPACE pause   TAB next page   ESC close";
        text(g, foot, fx + fw - 10 - w(foot, 0.65f), fy + fh - 10, DIM, 0.65f, false);
        text(g, "Display only - data from your own chat", fx + 10, fy + fh - 10, DIM, 0.65f, false);
    }

    private void drawHeader(GuiGraphicsExtractor g, int mx, int my) {
        int hy = fy + 10;
        int lx = fx + 10;
        g.fill(lx + 1, hy - 1, lx + 7, hy, PURPLE);
        g.fill(lx + 1, hy + 8, lx + 7, hy + 9, PURPLE);
        g.fill(lx, hy, lx + 1, hy + 8, PURPLE);
        g.fill(lx + 7, hy, lx + 8, hy + 8, PURPLE);
        g.fill(lx + 3, hy + 3, lx + 5, hy + 5, PURPLE_LIGHT);
        int x = lx + 13 + caps(g, "DASHBOARD", lx + 13, hy + 1, TEXT) + 6;
        text(g, "v1.0", x, hy + 2, DIM, 0.65f, false);
        x += w("v1.0", 0.65f) + 10;
        // DATA LIVE badge
        String live = paused ? "PAUSED" : "DATA LIVE";
        int bw = capsW(live) + 16;
        box(g, x, hy - 3, bw, 13, PANEL, BORDER);
        g.fill(x + 5, hy + 2, x + 8, hy + 5, paused ? AMBER : GREEN);
        caps(g, live, x + 11, hy + 1, MUTED);

        for (int i = 0; i < tabButtons.size(); i++) pill(g, tabButtons.get(i), i == tab, mx, my);
        for (int i = 0; i < rangeButtons.size(); i++) pill(g, rangeButtons.get(i), i == range, mx, my);
        text(g, "ESC close", escX - w("ESC close", 0.62f), fy + 11, DIM, 0.62f, false);
        if (headerSettings != null) pill(g, headerSettings, false, mx, my);
        g.fill(fx + 1, fy + 26, fx + fw - 1, fy + 27, BORDER);
    }

    // ================= OVERVIEW =================
    private void drawOverview(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        String me = minecraft != null ? minecraft.getUser().getName() : "you";
        int wy = welcomeY();
        glow(g, fx + 90, wy + 22, 110, 26, PURPLE, 24);
        if (minecraft != null && minecraft.player != null && Settings.S.skinFaces) {
            // Standing still and facing straight ahead: the "look at" point is the middle of its own box
            int bx1 = fx + 14, by1 = wy, bx2 = fx + 50, by2 = wy + 46;
            glow(g, (bx1 + bx2) / 2, (by1 + by2) / 2, 26, 26, PURPLE, 34);
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, bx1, by1, bx2, by2, 20, 0.0625f,
                    (bx1 + bx2) / 2f, (by1 + by2) / 2f, minecraft.player);
        } else {
            face(g, me, fx + 16, wy + 4, 34);
        }
        int nx = fx + 60;
        text(g, "Welcome, ", nx, wy + 9, TEXT, 1.6f, true);
        text(g, me, nx + wb("Welcome, ", 1.6f), wy + 9, PURPLE_LIGHT, 1.6f, true);
        text(g, "Have a great time on DonutSMP!", nx, wy + 27, MUTED, 0.75f, false);

        // session badge
        if (sessionButton != null) {
            boolean active = PaymentLedger.isSessionActive();
            boolean confirming = confirmUntil > System.currentTimeMillis();
            Button b = sessionButton;
            int x = b.getX(), y = b.getY(), bw = b.getWidth(), bh = b.getHeight();
            int col = confirming ? RED : active ? GREEN : DIM;
            box(g, x, y, bw, bh, alpha(col, over(b, mouseX, mouseY) ? 50 : 26), col);
            String s = confirming ? "CLICK AGAIN TO END" : active
                    ? "SESSION LIVE  " + hms(PaymentLedger.sessionMillis()) : "START SESSION";
            text(g, s, x + (bw - wb(s, 0.7f)) / 2, y + 3, col == DIM ? MUTED : col, 0.7f, true);
        }

        List<PaymentLedger.Payment> pays = rangePayments();
        long net = 0;
        long[] netSeries = new long[pays.size() + 1];
        for (int i = 0; i < pays.size(); i++) {
            net += pays.get(i).paidYou - pays.get(i).youPaid;
            netSeries[i + 1] = net;
        }

        // cards
        int cy = cardsY(), ch = cardsH(), gap = 8, cw = (fw - 20 - gap * 2) / 3;
        int c1 = fx + 10, c2 = c1 + cw + gap, c3 = c2 + cw + gap;

        List<long[]> bal = PaymentLedger.balanceSince(since());
        long[] balVals = new long[bal.size()];
        for (int i = 0; i < bal.size(); i++) balVals[i] = bal.get(i)[1];
        long bv = PaymentLedger.balance();
        boolean balDown = balVals.length > 1 && balVals[balVals.length - 1] < balVals[0];
        String balSub = bv < 0 ? "type /bal to load"
                : balVals.length > 1 ? (balDown ? "v " : "^ ") + signedMoney(balVals[balVals.length - 1] - balVals[0]) + " " + rangeWord()
                : "updated from /bal";
        card(g, c1, cy, cw, ch, "BALANCE", bv < 0 ? "$---" : Money.format(bv), balSub, GOLD, TEXT, balDown ? RED : GREEN, balVals);

        int days = switch (range) { case 0 -> 1; case 1 -> 7; default -> 30; };
        long[] play = PaymentLedger.playLastDays(Math.max(2, days));
        long inRange = 0;
        for (int i = Math.max(0, play.length - days); i < play.length; i++) inRange += play[i];
        String playSub = "+" + (inRange / 3_600_000L) + "h " + (inRange % 3_600_000L / 60_000L) + "m " + rangeWord();
        card(g, c2, cy, cw, ch, "PLAYTIME", (PaymentLedger.playTotal() / 3_600_000L) + "h", playSub, BLUE, TEXT, GREEN, play);

        String since0 = pays.isEmpty() ? "no payments yet" : "since " + day(pays.get(0).ts);
        int pc = net >= 0 ? GREEN : RED;
        card(g, c3, cy, cw, ch, RANGE_LABELS[range] + " PROFIT", signedMoney(net), since0, pc, pc, MUTED, netSeries);

        // feed
        int[] fb = feedBox();
        box(g, fb[0], fb[1], fb[2], fb[3], PANEL, BORDER);
        caps(g, "PAYMENT FEED", fb[0] + 8, fb[1] + 9, TEXT);
        for (int i = 0; i < filterButtons.size(); i++) pill(g, filterButtons.get(i), i == filter, mouseX, mouseY);
        List<PaymentLedger.Payment> feed = new ArrayList<>();
        for (int i = pays.size() - 1; i >= 0; i--) {
            PaymentLedger.Payment p = pays.get(i);
            boolean in = p.paidYou > 0;
            if (filter == 0 || (filter == 1 && in) || (filter == 2 && !in)) feed.add(p);
        }
        int rowH = 17, ry = fb[1] + 24, rows = Math.max(0, (fb[1] + fb[3] - 4 - ry) / rowH);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, feed.size() - rows)));
        scrollbar(g, fb[0] + fb[2] - 5, ry - 2, fb[1] + fb[3] - 4 - ry, rows, feed.size());
        for (int i = 0; i < Math.min(rows, feed.size() - scroll); i++) {
            PaymentLedger.Payment p = feed.get(scroll + i);
            boolean in = p.paidYou > 0;
            int y = ry + i * rowH;
            if (i % 2 == 0) g.fill(fb[0] + 4, y - 2, fb[0] + fb[2] - 4, y + rowH - 3, 0xFF161323);
            text(g, clock(p.ts), fb[0] + 8, y + 3, DIM, 0.7f, false);
            int ix = fb[0] + 8 + w("00:00 pm", 0.7f) + 6;
            face(g, p.name, ix, y, 11);
            text(g, p.name, ix + 15, y - 1, TEXT, 0.85f, true);
            text(g, in ? "paid you" : "you paid", ix + 15, y + 7, DIM, 0.62f, false);
            String amt = signedMoney(in ? p.paidYou : -p.youPaid);
            text(g, amt, fb[0] + fb[2] - 8 - wb(amt, 0.85f), y + 2, in ? GREEN : RED, 0.85f, true);
        }
        if (feed.isEmpty()) text(g, "No payments in this range", fb[0] + 8, ry + 2, DIM, 0.85f, false);

        // chart
        int[] cb = chartBox();
        box(g, cb[0], cb[1], cb[2], cb[3], PANEL, BORDER);
        glow(g, cb[0] + cb[2] * 3 / 4, cb[1] + cb[3] / 3, cb[2] / 4, cb[3] / 3, pc, 30);
        int capW = caps(g, "PROFIT / LOSS", cb[0] + 8, cb[1] + 9, TEXT);
        long spanDays = pays.isEmpty() ? 0 : Math.max(1, (now() - pays.get(0).ts + DAY - 1) / DAY);
        text(g, spanDays + (spanDays == 1 ? " day of data" : " days of data"), cb[0] + 14 + capW, cb[1] + 9, DIM, 0.62f, false);
        String big = signedMoney(net);
        text(g, big, cb[0] + cb[2] - 8 - wb(big, 1.4f), cb[1] + 6, pc, 1.4f, true);
        chart(g, netSeries, cb[0] + 8, cb[1] + 26, cb[2] - 16, cb[3] - 34);
    }

    private static String hms(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    private void card(GuiGraphicsExtractor g, int x, int y, int cw, int ch, String label, String value, String sub,
                      int lineColor, int valueColor, int subColor, long[] series) {
        box(g, x, y, cw, ch, PANEL, BORDER);
        glow(g, x + cw * 3 / 4, y + ch / 3, cw / 3, ch / 2, lineColor, 36);
        g.fill(x, y, x + cw, y + 1, lineColor);
        caps(g, label, x + 8, y + 7, MUTED);
        text(g, value, x + 8, y + 17, valueColor, 1.6f, true);
        text(g, sub, x + 8, y + 32, subColor, 0.62f, false);
        spark(g, series, x + 6, y + ch - 22, cw - 12, 18, lineColor);
    }

    private void chart(GuiGraphicsExtractor g, long[] vals, int x, int y, int cw, int ch) {
        if (vals.length < 2) {
            text(g, "No payments in this range yet", x + 4, y + ch / 2 - 4, DIM, 0.85f, false);
            return;
        }
        long min = 0, max = 0;
        for (long v : vals) { min = Math.min(min, v); max = Math.max(max, v); }
        long pad = Math.max(1, (max - min) / 12);
        final long hi = max + pad, lo = min - pad;
        int zy = y + (int) Math.round((double) hi / (hi - lo) * (ch - 1));
        g.fill(x, zy, x + cw, zy + 1, BORDER);
        text(g, signedMoney(max), x, y - 1, DIM, 0.6f, false);
        text(g, signedMoney(min), x, y + ch - 6, DIM, 0.6f, false);
        int last = -1, lastX = x;
        for (int px = 0; px < cw; px++) {
            double t = (double) px * (vals.length - 1) / Math.max(1, cw - 1);
            int i0 = (int) t, i1 = Math.min(vals.length - 1, i0 + 1);
            double v = vals[i0] + (vals[i1] - vals[i0]) * (t - i0);
            int yy = y + (int) Math.round((hi - v) / (double) (hi - lo) * (ch - 1));
            int col = v >= 0 ? GREEN : RED;
            g.fill(x + px, Math.min(yy, zy), x + px + 1, Math.max(yy, zy), alpha(col, 44));
            int from = last < 0 ? yy : last;
            g.fill(x + px, Math.min(yy, from), x + px + 1, Math.max(yy, from) + 1, col);
            last = yy;
            lastX = x + px;
        }
        int ec = vals[vals.length - 1] >= 0 ? GREEN : RED;
        g.fill(lastX - 1, last - 1, lastX + 2, last + 2, TEXT);
        g.fill(lastX, last, lastX + 1, last + 1, ec);
    }

    // ================= shared bits for the list pages =================
    private static final int PINK = 0xFFF472B6;

    private int pageTitle(GuiGraphicsExtractor g, String title, String sub) {
        int x = fx + 10, y = contentY();
        text(g, title, x, y, TEXT, 1.4f, true);
        text(g, sub, x, y + 15, MUTED, 0.62f, false);
        return y + 26;
    }

    /** Little stat box like "PLAYERS 175". Returns its width. */
    private int chip(GuiGraphicsExtractor g, int x, int y, String label, String value, int valueColor) {
        int cw = Math.max(capsW(label), wb(value, 0.85f)) + 16;
        box(g, x, y, cw, 26, PANEL, BORDER);
        caps(g, label, x + 8, y + 5, DIM);
        text(g, value, x + 8, y + 14, valueColor, 0.85f, true);
        return cw;
    }

    private void scrollbar(GuiGraphicsExtractor g, int x, int y, int h, int visible, int total) {
        if (total <= visible) return;
        g.fill(x, y, x + 2, y + h, 0xFF1C1830);
        int bh = Math.max(12, h * visible / total);
        int by = y + (h - bh) * scroll / Math.max(1, total - visible);
        g.fill(x, by, x + 2, by + bh, 0xFF5F5B75);
    }

    private void drawSearchFrame(GuiGraphicsExtractor g, int y) {
        if (searchBox == null) return;
        int sbw = Math.min(200, fw / 3), sbx = fx + fw - 10 - sbw, sby = y;
        int bc = searchBox.isFocused() ? PURPLE : 0xFF4A3F7A;
        g.fill(sbx, sby, sbx + sbw, sby + 1, bc);
        g.fill(sbx, sby + 25, sbx + sbw, sby + 26, bc);
        g.fill(sbx, sby, sbx + 1, sby + 26, bc);
        g.fill(sbx + sbw - 1, sby, sbx + sbw, sby + 26, bc);
        int mx0 = sbx + 7, my0 = sby + 8;
        g.fill(mx0 + 1, my0, mx0 + 5, my0 + 1, MUTED);
        g.fill(mx0 + 1, my0 + 5, mx0 + 5, my0 + 6, MUTED);
        g.fill(mx0, my0 + 1, mx0 + 1, my0 + 5, MUTED);
        g.fill(mx0 + 5, my0 + 1, mx0 + 6, my0 + 5, MUTED);
        g.fill(mx0 + 5, my0 + 6, mx0 + 6, my0 + 7, MUTED);
        g.fill(mx0 + 6, my0 + 7, mx0 + 8, my0 + 9, MUTED);
    }

    // ================= WIN / LOSS =================
    private void drawWinLoss(GuiGraphicsExtractor g) {
        List<PaymentLedger.Entry> all = new ArrayList<>();   // everyone, all time
        String q = search.trim().toLowerCase(Locale.US);
        for (PaymentLedger.Entry e : PaymentLedger.entries(false))
            if (q.isEmpty() || e.name.toLowerCase(Locale.US).contains(q)) all.add(e);
        all.sort(Comparator.comparingLong(PaymentLedger.Entry::net).reversed());   // won most off you first
        int up = 0, down = 0;
        long upSum = 0, downSum = 0;
        for (PaymentLedger.Entry e : all) {
            long n = e.net();
            if (n > 0) { up++; upSum += n; } else if (n < 0) { down++; downSum -= n; }
        }

        int y = pageTitle(g, "Win / Loss",
                "Everyone who has gambled with you. Top = won the most off you, bottom = lost the most.");
        int x = fx + 10;
        x += chip(g, x, y, "PLAYERS", String.valueOf(all.size()), TEXT) + 6;
        x += chip(g, x, y, "UP ON YOU", up + "  +" + Money.format(upSum), GREEN) + 6;
        chip(g, x, y, "DOWN ON YOU", down + "  -" + Money.format(downSum), RED);
        if (searchBox != null) {
            int sbw = Math.min(200, fw / 3), sbx = fx + fw - 10 - sbw, sby = y;
            int bc = searchBox.isFocused() ? PURPLE : 0xFF4A3F7A;
            g.fill(sbx, sby, sbx + sbw, sby + 1, bc);
            g.fill(sbx, sby + 25, sbx + sbw, sby + 26, bc);
            g.fill(sbx, sby, sbx + 1, sby + 26, bc);
            g.fill(sbx + sbw - 1, sby, sbx + sbw, sby + 26, bc);
            // magnifier
            int mx0 = sbx + 7, my0 = sby + 8;
            g.fill(mx0 + 1, my0, mx0 + 5, my0 + 1, MUTED);
            g.fill(mx0 + 1, my0 + 5, mx0 + 5, my0 + 6, MUTED);
            g.fill(mx0, my0 + 1, mx0 + 1, my0 + 5, MUTED);
            g.fill(mx0 + 5, my0 + 1, mx0 + 6, my0 + 5, MUTED);
            g.fill(mx0 + 5, my0 + 6, mx0 + 6, my0 + 7, MUTED);
            g.fill(mx0 + 6, my0 + 7, mx0 + 8, my0 + 9, MUTED);
        }

        int py = y + 34, ph = contentBottom() - py, pw = fw - 20, px = fx + 10;
        box(g, px, py, pw, ph, PANEL, BORDER);
        int cNum = px + 10, cName = px + 28, cBets = px + pw * 48 / 100, cWag = px + pw * 62 / 100, cNet = px + pw - 14;
        int hy = py + 8;
        caps(g, "#", cNum, hy, DIM);
        caps(g, "PLAYER", cName + 14, hy, DIM);
        caps(g, "BETS", cBets, hy, DIM);
        caps(g, "WAGERED", cWag, hy, DIM);
        caps(g, "NET (THEIR SIDE)", cNet - capsW("NET (THEIR SIDE)"), hy, DIM);
        g.fill(px + 1, hy + 10, px + pw - 1, hy + 11, BORDER);

        int rowH = 16, ry = hy + 15, visible = Math.max(1, (py + ph - 4 - ry) / rowH);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, all.size() - visible)));
        for (int i = 0; i < visible && scroll + i < all.size(); i++) {
            int idx = scroll + i;
            PaymentLedger.Entry e = all.get(idx);
            int ty = ry + i * rowH;
            if (i % 2 == 0) g.fill(px + 4, ty - 3, px + pw - 8, ty + rowH - 3, 0xFF161323);
            text(g, String.valueOf(idx + 1), cNum, ty + 1, DIM, 0.7f, false);
            face(g, e.name, cName, ty - 1, 10);
            text(g, e.name, cName + 14, ty, TEXT, 0.8f, true);
            text(g, String.valueOf(e.payments), cBets, ty + 1, MUTED, 0.75f, false);
            text(g, Money.format(e.paidYou), cWag, ty + 1, MUTED, 0.75f, false);
            long n = e.net();
            String v = signedMoney(n);
            text(g, v, cNet - wb(v, 0.8f), ty, n > 0 ? GREEN : n < 0 ? RED : MUTED, 0.8f, true);
        }
        if (all.isEmpty()) text(g, "No players yet", cName, ry, DIM, 0.85f, false);
        scrollbar(g, px + pw - 5, ry - 3, py + ph - ry, visible, all.size());
    }

    // ================= RAKEBACK =================
    private void drawRakeback(GuiGraphicsExtractor g) {
        Settings st = Settings.S;
        List<PaymentLedger.Entry> losers = new ArrayList<>();
        int ready = 0;
        long owe = 0;
        String q = search.trim().toLowerCase(Locale.US);
        for (PaymentLedger.Entry e : PaymentLedger.entries(false)) {   // everyone, all time
            long lost = -e.net();   // what they lost to you
            if (lost > 0 && (q.isEmpty() || e.name.toLowerCase(Locale.US).contains(q))) {
                losers.add(e);
                if (lost >= st.rakeMin) { ready++; owe += lost * st.rakePct / 100; }
            }
        }
        losers.sort(Comparator.comparingLong(PaymentLedger.Entry::net));   // closest to (or over) the target first

        int y = pageTitle(g, "Rakeback", st.rakePct + "% back once a player has lost " + Money.format(st.rakeMin)
                + ". Everyone, closest to " + Money.format(st.rakeMin) + " first.");
        int x = fx + 10;
        x += chip(g, x, y, "PLAYERS", String.valueOf(losers.size()), TEXT) + 6;
        x += chip(g, x, y, "READY", String.valueOf(ready), GREEN) + 6;
        chip(g, x, y, "YOU OWE", Money.format(owe), TEXT);
        drawSearchFrame(g, y);

        int py = y + 34, ph = contentBottom() - py, pw = fw - 20, px = fx + 10;
        box(g, px, py, pw, ph, PANEL, BORDER);
        int rowH = 22, ry = py + 8, visible = Math.max(1, (py + ph - 4 - ry) / rowH);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, losers.size() - visible)));
        for (int i = 0; i < visible && scroll + i < losers.size(); i++) {
            int idx = scroll + i;
            PaymentLedger.Entry e = losers.get(idx);
            long lost = -e.net();
            boolean isReady = lost >= st.rakeMin;
            int ty = ry + i * rowH, rx = px + 6, rw = pw - 18;
            box(g, rx, ty, rw, rowH - 3, 0xFF15121F, 0xFF1C1830);
            text(g, String.valueOf(idx + 1), rx + 8, ty + 4, DIM, 0.7f, false);
            text(g, e.name, rx + 26, ty + 3, TEXT, 0.8f, true);
            String right = isReady ? "READY  owe " + Money.format(lost * st.rakePct / 100)
                    : Money.format(lost) + " / " + Money.format(st.rakeMin);
            text(g, right, rx + rw - 8 - wb(right, 0.8f), ty + 3, isReady ? GREEN : TEXT, 0.8f, true);
            int bx = rx + 26, be = rx + rw - 8, by = ty + rowH - 8;
            g.fill(bx, by, be, by + 2, 0xFF2A2640);
            int fill = (int) ((be - bx) * Math.min(1.0, lost / (double) st.rakeMin));
            if (fill > 0) g.fill(bx, by, bx + fill, by + 2, isReady ? GREEN : PINK);
        }
        if (losers.isEmpty()) text(g, q.isEmpty() ? "Nobody has lost to you yet" : "No players match \"" + search.trim() + "\"",
                px + 10, ry + 4, DIM, 0.85f, false);
        scrollbar(g, px + pw - 6, ry, py + ph - 8 - ry, visible, losers.size());
    }

    // ================= SESSIONS =================
    private void drawSessions(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        boolean active = PaymentLedger.isSessionActive();
        long in = 0, out = 0;
        int pays = 0;
        for (PaymentLedger.Entry e : PaymentLedger.entries(true)) { in += e.paidYou; out += e.youPaid; pays += e.payments; }
        long profit = in - out;

        int y = contentY(), ch = 54, x = fx + 10, cw = fw - 20;
        box(g, x, y, cw, ch, PANEL, BORDER);
        glow(g, x + cw / 5, y + ch / 2, cw / 5, ch / 2, active ? GREEN : PURPLE, 34);
        g.fill(x, y, x + cw, y + 1, active ? GREEN : DIM);
        caps(g, active ? "SESSION LIVE" : "NO SESSION RUNNING", x + 8, y + 7, active ? GREEN : MUTED);
        text(g, hms(PaymentLedger.sessionMillis()), x + 8, y + 18, TEXT, 1.6f, true);
        text(g, active ? "started " + dayTime(PaymentLedger.sessionStartTime()) : "last session", x + 8, y + 37, MUTED, 0.62f, false);
        int sx = x + cw / 3;
        stat(g, "RECEIVED", Money.format(in), sx, y + 10, TEXT);
        stat(g, "SENT", Money.format(out), sx + 70, y + 10, TEXT);
        stat(g, "PROFIT", signedMoney(profit), sx + 140, y + 10, profit >= 0 ? GREEN : RED);
        stat(g, "PAYMENTS", String.valueOf(pays), sx + 210, y + 10, TEXT);

        if (sessionButton != null) {
            boolean confirming = confirmUntil > System.currentTimeMillis();
            Button b = sessionButton;
            int bx = b.getX(), by = b.getY(), bw = b.getWidth(), bh = b.getHeight();
            int col = confirming ? RED : active ? RED : GREEN;
            box(g, bx, by, bw, bh, alpha(col, over(b, mouseX, mouseY) ? 60 : 30), col);
            String s = confirming ? "CONFIRM?" : active ? "END SESSION" : "START SESSION";
            text(g, s, bx + (bw - wb(s, 0.85f)) / 2, by + 4, col, 0.85f, true);
        }

        // past sessions
        int py = y + ch + 8, ph = contentBottom() - py;
        box(g, x, py, cw, ph, PANEL, BORDER);
        caps(g, "PAST SESSIONS", x + 8, py + 9, TEXT);
        List<PaymentLedger.SessionRecord> hist = PaymentLedger.history();
        int c1 = x + 8, c2 = x + cw * 26 / 100, c3 = x + cw * 42 / 100, c4 = x + cw * 58 / 100, c5 = x + cw * 74 / 100, cr = x + cw - 8;
        int hy = py + 24;
        text(g, "STARTED", c1, hy, DIM, 0.62f, true);
        text(g, "LENGTH", c2, hy, DIM, 0.62f, true);
        text(g, "RECEIVED", c3, hy, DIM, 0.62f, true);
        text(g, "SENT", c4, hy, DIM, 0.62f, true);
        text(g, "PAYMENTS", c5, hy, DIM, 0.62f, true);
        text(g, "PROFIT", cr - wb("PROFIT", 0.62f), hy, DIM, 0.62f, true);
        int rowH = 14, ry = hy + 12, rows = Math.max(0, (py + ph - 4 - ry) / rowH);
        for (int i = 0; i < Math.min(rows, hist.size()); i++) {
            PaymentLedger.SessionRecord r = hist.get(i);
            int ty = ry + i * rowH;
            if (i % 2 == 0) g.fill(x + 4, ty - 2, x + cw - 4, ty + rowH - 2, 0xFF161323);
            text(g, dayTime(r.start), c1, ty, TEXT, 0.75f, false);
            text(g, hms(r.length()), c2, ty, TEXT, 0.75f, false);
            text(g, Money.format(r.received), c3, ty, TEXT, 0.75f, false);
            text(g, Money.format(r.sent), c4, ty, TEXT, 0.75f, false);
            text(g, r.payments > 0 ? String.valueOf(r.payments) : "-", c5, ty, TEXT, 0.75f, false);
            String p = signedMoney(r.profit());
            text(g, p, cr - wb(p, 0.75f), ty, r.profit() >= 0 ? GREEN : RED, 0.75f, true);
        }
        if (hist.isEmpty()) text(g, "Ended sessions show up here", c1, ry, DIM, 0.85f, false);
    }

    // ================= PROFIT PANEL =================
    private void drawPanels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = fx + 10, y = contentY();
        caps(g, "PROFIT PANEL", x, y + 4, TEXT);
        text(g, "Turn the Balance HUD panels on or off. They appear and disappear on your screen right away.",
                x, y + 16, DIM, 0.62f, false);
        String[] names = {"PROFIT TRACKER", "RAKEBACK", "BALANCE"};
        String[] about = {"The big profit panel with the chart, top winners / losers and recent bets",
                "Who is close to rakeback and how much you owe them",
                "Your balance box with your name"};
        boolean[] on = {Panels.profit, Panels.rakeback, Panels.balance};
        int[] colors = {GREEN, GOLD, PURPLE};
        for (int i = 0; i < panelButtons.size(); i++) {
            Button b = panelButtons.get(i);
            int bx = b.getX(), by = b.getY(), bw = b.getWidth(), bh = b.getHeight();
            boolean hover = over(b, mouseX, mouseY);
            box(g, bx, by, bw, bh, hover ? 0xFF181526 : PANEL, BORDER);
            if (on[i]) glow(g, bx + bw / 6, by + bh / 2, bw / 6, bh / 2, colors[i], 30);
            g.fill(bx, by, bx + 2, by + bh, on[i] ? colors[i] : BORDER);
            caps(g, names[i], bx + 14, by + 10, on[i] ? TEXT : MUTED);
            text(g, about[i], bx + 14, by + 24, DIM, 0.62f, false);
            // switch
            int sw = 30, sh = 14, sx = bx + bw - sw - 14, sy = by + (bh - sh) / 2;
            g.fill(sx, sy, sx + sw, sy + sh, on[i] ? colors[i] : 0xFF2A2640);
            int kx = on[i] ? sx + sw - 12 : sx + 2;
            g.fill(kx, sy + 2, kx + 10, sy + sh - 2, TEXT);
            String st = on[i] ? "ON" : "OFF";
            text(g, st, sx - 8 - wb(st, 0.75f), sy + 3, on[i] ? colors[i] : DIM, 0.75f, true);
        }
        if (settingsButton != null) pill(g, settingsButton, false, mouseX, mouseY);
        int ny = panelButtons.isEmpty() ? y + 40 : panelButtons.get(panelButtons.size() - 1).getY() + 56;
        text(g, "These switches control the Balance HUD mod, so it needs its latest update installed too.",
                x, ny, DIM, 0.62f, false);
    }

    private void stat(GuiGraphicsExtractor g, String label, String value, int x, int y, int color) {
        caps(g, label, x, y, DIM);
        text(g, value, x, y + 12, color, 1.1f, true);
    }
}
