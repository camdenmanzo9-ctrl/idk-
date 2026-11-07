package dev.profit.dashboard;

import dev.profit.dashboard.PaymentLedger.Entry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Bettors payment dashboard: Dashboard / Players / Paid You, Session vs All Time, session timer + End Session. */
public class DashboardScreen extends Screen {
    private enum Tab { DASHBOARD, PLAYERS, PAID_YOU, SESSIONS }

    private static Tab tab = Tab.DASHBOARD;
    private static boolean sessionView = false;

    private static final int SIDE = 118;
    private static final int BG = 0xFF0B0E11, SIDE_BG = 0xFF0F1317, PANEL = 0xFF13181C, STRIPE = 0xFF171D22,
            LINE = 0xFF232A31, HOVER = 0xFF1A2026, SELECT = 0xFF1E252C, TEXT = 0xFFE8E8E8, MUTED = 0xFF7F868D,
            GOLD = 0xFFF2C14E, RED = 0xFFE8475A, GREEN = 0xFF3DBE7A, DARK = 0xFF0B0E11;

    private int scroll = 0;
    private long confirmUntil = 0;
    private Button endButton, closeButton, sessionScope, allScope;
    private final java.util.Map<Button, Tab> tabButtons = new java.util.LinkedHashMap<>();
    private final java.util.Map<Button, Integer> deleteButtons = new java.util.LinkedHashMap<>();
    private Button clearAllButton;
    private int pendingDelete = -2;      // history index waiting for a 2nd click, -1 = "clear all"
    private long pendingUntil = 0;

    public DashboardScreen() {
        super(Component.literal("Bettors Dashboard"));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        tabButtons.clear();
        addTab("Dashboard", Tab.DASHBOARD, 0);
        addTab("Players", Tab.PLAYERS, 1);
        addTab("Paid You", Tab.PAID_YOU, 2);
        addTab("Sessions", Tab.SESSIONS, 3);

        sessionScope = addRenderableWidget(Button.builder(Component.literal("Session"),
                b -> { sessionView = true; scroll = 0; }).bounds(width - 136, 6, 64, 18).build());
        allScope = addRenderableWidget(Button.builder(Component.literal("All Time"),
                b -> { sessionView = false; scroll = 0; }).bounds(width - 72, 6, 64, 18).build());

        endButton = addRenderableWidget(Button.builder(Component.literal(sessionLabel()), b -> onEndSession())
                .bounds(6, height - 52, SIDE - 12, 20).build());
        closeButton = addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(6, height - 28, SIDE - 12, 20).build());

        deleteButtons.clear();
        clearAllButton = null;
        if (tab == Tab.SESSIONS) addSessionButtons();
    }

    // Same layout math as drawSessions(), so the buttons line up with the rows.
    private void addSessionButtons() {
        int cx = SIDE + 10, cw = width - cx - 8;
        int py = 90, ph = height - 8 - py, hy = py + 22, rowsTop = hy + 13, rowH = 12;
        int visible = Math.max(1, (py + ph - 6 - rowsTop) / rowH);
        int size = PaymentLedger.history().size();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, size - visible)));

        if (size > 0) {
            int bx = cx + 6 + font.width("PAST SESSIONS") + 10;
            clearAllButton = addRenderableWidget(Button.builder(Component.literal("Clear all"),
                    b -> confirmThen(-1, PaymentLedger::clearHistory)).bounds(bx, py + 4, 52, 13).build());
        }
        for (int i = 0; i < visible && scroll + i < size; i++) {
            final int idx = scroll + i;
            Button b = addRenderableWidget(Button.builder(Component.literal("Del"),
                    btn -> confirmThen(idx, () -> PaymentLedger.deleteSession(idx)))
                    .bounds(cx + cw - 38, rowsTop + i * rowH - 2, 32, 12).build());
            deleteButtons.put(b, idx);
        }
    }

    /** First click arms it for 3 seconds, second click does it. */
    private void confirmThen(int key, Runnable action) {
        long now = System.currentTimeMillis();
        if (pendingDelete == key && now < pendingUntil) {
            pendingDelete = -2;
            action.run();
            rebuildWidgets();
        } else {
            pendingDelete = key;
            pendingUntil = now + 3000;
        }
    }

    private void addTab(String label, Tab t, int i) {
        Button b = addRenderableWidget(Button.builder(Component.literal(label),
                btn -> { tab = t; scroll = 0; pendingDelete = -2; rebuildWidgets(); }).bounds(6, 44 + i * 24, SIDE - 12, 20).build());
        tabButtons.put(b, t);
    }

    private static String sessionLabel() {
        return PaymentLedger.isSessionActive() ? "End Session" : "Start Session";
    }

    private void onEndSession() {
        if (!PaymentLedger.isSessionActive()) {
            PaymentLedger.startSession();
            endButton.setMessage(Component.literal(sessionLabel()));
            return;
        }
        long now = System.currentTimeMillis();
        if (now > confirmUntil) { // first click asks for confirmation
            confirmUntil = now + 3000;
            endButton.setMessage(Component.literal("Click to confirm"));
            return;
        }
        confirmUntil = 0;
        PaymentLedger.endSession();
        endButton.setMessage(Component.literal(sessionLabel()));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, BG);
        g.fill(0, 0, SIDE, height, SIDE_BG);
        g.fill(SIDE, 0, SIDE + 1, height, LINE);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);

        if (pendingDelete != -2 && System.currentTimeMillis() > pendingUntil) pendingDelete = -2;
        if (confirmUntil != 0 && System.currentTimeMillis() > confirmUntil) {
            confirmUntil = 0;
            endButton.setMessage(Component.literal(sessionLabel()));
        }

        // Flat buttons drawn over the vanilla ones (the vanilla ones still handle the clicks)
        g.fill(0, 0, SIDE, height, SIDE_BG);
        g.text(font, "BETTORS", 12, 12, GOLD, false);
        g.text(font, "Payments", 12, 23, MUTED, false);
        for (var e : tabButtons.entrySet()) navButton(g, e.getKey(), e.getValue() == tab, mouseX, mouseY);

        boolean active = PaymentLedger.isSessionActive();
        boolean confirming = confirmUntil != 0;
        actionButton(g, endButton, active ? RED : GREEN, confirming, mouseX, mouseY);
        actionButton(g, closeButton, MUTED, false, mouseX, mouseY);

        int sy = height - 82;
        g.text(font, active ? "SESSION" : "SESSION ENDED", 12, sy, active ? MUTED : RED, false);
        g.text(font, PaymentLedger.formatDuration(PaymentLedger.sessionMillis()), 12, sy + 11, active ? TEXT : MUTED, false);
        if (PaymentLedger.lastSessionLength() >= 0 && sy - 28 > 44 + 4 * 24 + 4) {
            long p = PaymentLedger.lastSessionProfit();
            g.text(font, "LAST SESSION", 12, sy - 28, MUTED, false);
            g.text(font, PaymentLedger.formatDurationShort(PaymentLedger.lastSessionLength()) + "  "
                    + (p >= 0 ? "+" : "") + Money.format(p), 12, sy - 17, p >= 0 ? GREEN : RED, false);
        }
        g.fill(SIDE, 0, SIDE + 1, height, LINE);

        // Top bar
        int cx = SIDE + 10, cw = width - cx - 8;
        g.fill(SIDE + 1, 0, width, 30, BG);
        g.fill(cx, 12, cx + 4, 16, active ? GREEN : MUTED);
        String who = minecraft.getUser().getName().toUpperCase();
        g.text(font, who, cx + 9, 10, GOLD, false);
        int tx = cx + 9 + font.width(who) + 14;
        String sText = (active ? "Session " : "Session ended ") + PaymentLedger.formatDuration(PaymentLedger.sessionMillis());
        g.text(font, sText, tx, 10, active ? MUTED : RED, false);
        toggle(g, sessionScope, allScope, sessionView, mouseX, mouseY);
        g.fill(SIDE + 1, 30, width, 31, LINE);

        List<Entry> list = PaymentLedger.entries(sessionView);
        switch (tab) {
            case DASHBOARD -> drawDashboard(g, list, cx, cw);
            case PLAYERS -> drawLedger(g, list, cx, cw);
            case PAID_YOU -> drawPaidYou(g, list, cx, cw);
            case SESSIONS -> drawSessions(g, cx, cw, mouseX, mouseY);
        }
    }

    // ---------------- flat button styles ----------------

    private static boolean over(Button b, int mx, int my) {
        return mx >= b.getX() && mx < b.getX() + b.getWidth() && my >= b.getY() && my < b.getY() + b.getHeight();
    }

    private void navButton(GuiGraphicsExtractor g, Button b, boolean selected, int mx, int my) {
        int x = b.getX(), y = b.getY(), w = b.getWidth(), h = b.getHeight();
        boolean hover = over(b, mx, my);
        g.fill(x, y, x + w, y + h, selected ? SELECT : hover ? HOVER : SIDE_BG);
        if (selected) g.fill(x, y, x + 2, y + h, GOLD);
        g.text(font, b.getMessage().getString(), x + 10, y + (h - 8) / 2, selected || hover ? TEXT : MUTED, false);
    }

    private void actionButton(GuiGraphicsExtractor g, Button b, int color, boolean filled, int mx, int my) {
        int x = b.getX(), y = b.getY(), w = b.getWidth(), h = b.getHeight();
        boolean hover = over(b, mx, my);
        g.fill(x, y, x + w, y + h, filled ? color : hover ? HOVER : SIDE_BG);
        outline(g, x, y, w, h, color);
        String s = b.getMessage().getString();
        g.text(font, s, x + (w - font.width(s)) / 2, y + (h - 8) / 2, filled ? DARK : color, false);
    }

    private void toggle(GuiGraphicsExtractor g, Button left, Button right, boolean leftOn, int mx, int my) {
        int x = left.getX(), y = left.getY(), w = right.getX() + right.getWidth() - x, h = left.getHeight();
        g.fill(x, y, x + w, y + h, PANEL);
        outline(g, x, y, w, h, LINE);
        for (Button b : new Button[]{left, right}) {
            boolean on = (b == left) == leftOn;
            int bx = b.getX(), bw = b.getWidth();
            if (on) g.fill(bx + 1, y + 1, bx + bw - 1, y + h - 1, SELECT);
            else if (over(b, mx, my)) g.fill(bx + 1, y + 1, bx + bw - 1, y + h - 1, HOVER);
            String s = b.getMessage().getString();
            g.text(font, s, bx + (bw - font.width(s)) / 2, y + (h - 8) / 2, on ? GOLD : MUTED, false);
        }
    }

    private void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent) {
        g.fill(x, y, x + w, y + h, PANEL);
        outline(g, x, y, w, h, LINE);
        if (accent != 0) g.fill(x, y, x + w, y + 2, accent);
    }

    private void bigText(GuiGraphicsExtractor g, String s, int x, int y, int color, int maxW) {
        float sc = font.width(s) * 1.5f <= maxW ? 1.5f : 1.0f;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(sc, sc);
        g.text(font, s, 0, 0, color, false);
        g.pose().popMatrix();
    }

    private void emptyState(GuiGraphicsExtractor g, int x, int y, int w, int h, String title, String line1, String line2) {
        panel(g, x, y, w, h, 0);
        int my = y + h / 2 - 16;
        g.text(font, title, x + (w - font.width(title)) / 2, my, TEXT, false);
        g.text(font, line1, x + (w - font.width(line1)) / 2, my + 14, MUTED, false);
        g.text(font, line2, x + (w - font.width(line2)) / 2, my + 26, MUTED, false);
    }

    // ---------------- Dashboard tab ----------------

    private void drawDashboard(GuiGraphicsExtractor g, List<Entry> list, int cx, int cw) {
        // Everything here is from YOUR side: + means you won from that player, - means you lost to them.
        long paid = 0, sent = 0, up = 0, down = 0;
        int upCount = 0, downCount = 0;
        for (Entry e : list) {
            paid += e.paidYou;
            sent += e.youPaid;
            long yours = -e.net();
            if (yours > 0) { up += yours; upCount++; }
            else if (yours < 0) { down -= yours; downCount++; }
        }
        long net = up - down;

        int y = 38, gap = 6, cardW = (cw - gap * 4) / 5;
        card(g, cx, y, cardW, "PLAYERS", String.valueOf(list.size()), sessionView ? "this session" : "all time", 0, TEXT);
        card(g, cx + (cardW + gap), y, cardW, "PAID YOU", Money.format(paid), "received", 0, TEXT);
        card(g, cx + (cardW + gap) * 2, y, cardW, "YOU PAID", Money.format(sent), "sent", 0, TEXT);
        card(g, cx + (cardW + gap) * 3, y, cardW, "YOU'RE UP", Money.format(up),
                "from " + upCount + (upCount == 1 ? " player" : " players"), 0, GREEN);
        card(g, cx + (cardW + gap) * 4, y, cardW, "YOU'RE DOWN", Money.format(down),
                "net " + signed(net), 0, RED);

        int by = 90, bh = height - 8 - by;
        if (list.isEmpty()) {
            emptyState(g, cx, by, cw, bh, "No payments yet",
                    "Payments show up here automatically when someone pays you or you pay them.",
                    "Type /bettors debug to check they're being picked up.");
            return;
        }
        if (bh < 40) return;

        int lw = cw * 58 / 100, rx = cx + lw + 6, rw = cx + cw - rx;

        // Left: every player's net as a bar (green right = you won, red left = you lost)
        panel(g, cx, by, lw, bh, 0);
        g.text(font, "PLAYERS", cx + 8, by + 8, TEXT, false);
        g.text(font, "your net per player", cx + 14 + font.width("PLAYERS"), by + 8, MUTED, false);

        List<Entry> s = new ArrayList<>(list);
        s.sort(Comparator.comparingLong(Entry::net)); // biggest wins for you first
        long maxAbs = 1;
        for (Entry e : s) maxAbs = Math.max(maxAbs, Math.abs(e.net()));

        int rowsTop = by + 26, rowH = 14, visible = Math.max(1, (by + bh - 6 - rowsTop) / rowH);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, s.size() - visible)));
        int nameW = lw * 30 / 100, valW = 64;
        int barL = cx + 8 + nameW, barR = cx + lw - 12 - valW, mid = (barL + barR) / 2, half = (barR - barL) / 2;
        int shown = Math.min(visible, s.size() - scroll);
        g.fill(mid, rowsTop - 3, mid + 1, rowsTop + shown * rowH - 4, LINE);
        for (int i = 0; i < shown; i++) {
            Entry e = s.get(scroll + i);
            int ry = rowsTop + i * rowH;
            long yours = -e.net();
            g.text(font, fit(e.name, nameW - 6), cx + 8, ry, TEXT, false);
            int len = (int) Math.max(yours == 0 ? 0 : 1, half * (double) Math.abs(yours) / maxAbs);
            if (yours > 0) g.fill(mid + 1, ry, mid + 1 + len, ry + 7, GREEN);
            else if (yours < 0) g.fill(mid - len, ry, mid, ry + 7, RED);
            rightText(g, signed(yours), cx + lw - 8, ry, yourColor(yours));
        }

        // Right: top 5 each way
        int top = bh / 2;
        List<Entry> won = new ArrayList<>(), lost = new ArrayList<>();
        for (Entry e : list) { if (e.net() < 0) won.add(e); else if (e.net() > 0) lost.add(e); }
        won.sort(Comparator.comparingLong(Entry::net));
        lost.sort(Comparator.comparingLong(Entry::net).reversed());
        listSection(g, "YOU WON MOST FROM", won, rx, by, rw, top - 3, GREEN);
        listSection(g, "YOU LOST MOST TO", lost, rx, by + top + 3, rw, bh - top - 3, RED);
    }

    private void listSection(GuiGraphicsExtractor g, String title, List<Entry> list, int x, int y, int w, int h, int color) {
        panel(g, x, y, w, h, 0);
        g.text(font, title, x + 8, y + 8, color, false);
        int rows = Math.max(0, Math.min(5, (h - 28) / 12));
        if (list.isEmpty()) { g.text(font, "Nobody yet", x + 8, y + 24, MUTED, false); return; }
        for (int i = 0; i < Math.min(rows, list.size()); i++) {
            Entry e = list.get(i);
            int ry = y + 24 + i * 12;
            String amt = Money.format(Math.abs(e.net()));
            g.text(font, fit(e.name, w - 24 - font.width(amt)), x + 8, ry, TEXT, false);
            rightText(g, amt, x + w - 8, ry, color);
        }
    }

    private void topList(GuiGraphicsExtractor g, String title, List<Entry> list, int x, int y, int w, int h, int color) {
        g.text(font, title, x, y, color, false);
        int rows = Math.max(0, (h - 22) / 11);
        for (int i = 0; i < Math.min(rows, list.size()); i++) {
            Entry e = list.get(i);
            int ry = y + 16 + i * 11;
            String amt = Money.format(Math.abs(e.net()));
            g.text(font, fit((i + 1) + ". " + e.name, w - font.width(amt) - 6), x, ry, TEXT, false);
            rightText(g, amt, x + w, ry, color);
        }
    }

    private void card(GuiGraphicsExtractor g, int x, int y, int w, String label, String value, String sub, int accent, int valueColor) {
        panel(g, x, y, w, 46, 0);
        g.text(font, fit(label, w - 12), x + 6, y + 7, MUTED, false);
        bigText(g, fit(value, w - 12), x + 6, y + 19, valueColor, w - 12);
        g.text(font, fit(sub, w - 12), x + 6, y + 35, MUTED, false);
    }

    private void drawChart(GuiGraphicsExtractor g, List<Entry> list, int x, int y, int w, int h) {
        if (list.isEmpty()) {
            g.text(font, "No payments yet", x, y + h / 2 - 4, MUTED, false);
            return;
        }
        List<Entry> s = new ArrayList<>(list);
        s.sort(Comparator.comparingLong(Entry::net));
        long min = Math.min(0, s.get(0).net()), max = Math.max(0, s.get(s.size() - 1).net());
        if (min == max) max = min + 1;

        String maxL = Money.format(max), minL = Money.format(min);
        int labelW = Math.max(font.width(maxL), font.width(minL)) + 6;
        g.text(font, maxL, x, y, MUTED, false);
        g.text(font, minL, x, y + h - 8, MUTED, false);

        int lx = x + labelW, pw = w - labelW;
        final long fMin = min, fMax = max;
        java.util.function.LongToIntFunction yOf = v -> y + (int) Math.round((double) (fMax - v) / (fMax - fMin) * (h - 1));
        int zy = yOf.applyAsInt(0);
        g.fill(lx, zy, lx + pw, zy + 1, LINE);
        g.fill(lx, y, lx + 1, y + h, LINE);

        int n = s.size();
        if (n == 1) {
            int px = lx + pw / 2, py = yOf.applyAsInt(s.get(0).net());
            g.fill(px - 1, py - 1, px + 2, py + 2, netColor(s.get(0).net()));
            return;
        }
        int prevX = lx, prevY = yOf.applyAsInt(s.get(0).net()), lastY = prevY;
        for (int i = 1; i < n; i++) {
            int nx = lx + (int) ((long) i * (pw - 1) / (n - 1));
            int ny = yOf.applyAsInt(s.get(i).net());
            long a = s.get(i - 1).net(), b = s.get(i).net();
            for (int xx = prevX; xx <= nx; xx++) {
                double t = nx == prevX ? 1.0 : (double) (xx - prevX) / (nx - prevX);
                int yy = (int) Math.round(prevY + (ny - prevY) * t);
                double v = a + (b - a) * t;
                g.fill(xx, Math.min(yy, lastY), xx + 1, Math.max(yy, lastY) + 1, v > 0 ? GOLD : v < 0 ? RED : MUTED);
                lastY = yy;
            }
            prevX = nx;
            prevY = ny;
        }
    }

    // ---------------- Players tab ----------------

    private void drawLedger(GuiGraphicsExtractor g, List<Entry> list, int cx, int cw) {
        int py = 38, ph = height - 8 - py;
        panel(g, cx, py, cw, ph, GOLD);
        g.text(font, (sessionView ? "SESSION" : "ALL-TIME") + " PLAYER LEDGER", cx + 6, py + 7, GOLD, false);
        rightText(g, list.size() + " PLAYERS TRACKED", cx + cw - 6, py + 7, MUTED);

        int paidX = cx + cw * 50 / 100, sentX = cx + cw * 68 / 100, right = cx + cw - 6, hy = py + 22;
        g.text(font, "PLAYER", cx + 6, hy, MUTED, false);
        g.text(font, "PAID YOU", paidX, hy, MUTED, false);
        g.text(font, "YOU PAID", sentX, hy, MUTED, false);
        rightText(g, "YOUR NET", right, hy, MUTED);

        List<Entry> s = new ArrayList<>(list);
        s.sort(Comparator.comparingLong(Entry::volume).reversed());
        int rowsTop = hy + 13, rowH = 12, visible = Math.max(1, (py + ph - 16 - rowsTop) / rowH);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, s.size() - visible)));

        for (int i = 0; i < visible && scroll + i < s.size(); i++) {
            int idx = scroll + i;
            Entry e = s.get(idx);
            int ry = rowsTop + i * rowH;
            if (i % 2 == 0) g.fill(cx + 4, ry - 2, cx + cw - 4, ry + 10, STRIPE);
            g.text(font, fit((idx + 1) + ". " + e.name, paidX - cx - 12), cx + 6, ry, TEXT, false);
            g.text(font, Money.format(e.paidYou), paidX, ry, TEXT, false);
            g.text(font, Money.format(e.youPaid), sentX, ry, TEXT, false);
            long yours = -e.net();
            rightText(g, signed(yours), right, ry, yourColor(yours));
        }
        if (s.isEmpty()) g.text(font, "No payments logged yet", cx + 6, rowsTop, MUTED, false);

        String legend = "+ you won from them   ·   - you lost to them";
        int legendW = Math.min(font.width(legend), cw - 12);
        g.text(font, fit(legend, cw - 12), cx + (cw - legendW) / 2, py + ph - 11, MUTED, false);
    }

    // ---------------- Paid You tab ----------------

    private void drawPaidYou(GuiGraphicsExtractor g, List<Entry> list, int cx, int cw) {
        int py = 38, ph = height - 8 - py;
        panel(g, cx, py, cw, ph, GOLD);

        List<Entry> s = new ArrayList<>();
        long total = 0, top = 1;
        for (Entry e : list) if (e.paidYou > 0) { s.add(e); total += e.paidYou; top = Math.max(top, e.paidYou); }
        s.sort(Comparator.comparingLong((Entry e) -> e.paidYou).reversed());

        g.text(font, "PAID YOU" + (sessionView ? " (SESSION)" : " (ALL TIME)"), cx + 6, py + 7, GOLD, false);
        rightText(g, "TOTAL " + Money.format(total), cx + cw - 6, py + 7, GOLD);

        int barX = cx + cw * 38 / 100, barEnd = cx + cw * 78 / 100, right = cx + cw - 6;
        int rowsTop = py + 24, rowH = 13, visible = Math.max(1, (py + ph - 6 - rowsTop) / rowH);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, s.size() - visible)));

        for (int i = 0; i < visible && scroll + i < s.size(); i++) {
            int idx = scroll + i;
            Entry e = s.get(idx);
            int ry = rowsTop + i * rowH;
            g.text(font, fit((idx + 1) + ". " + e.name, barX - cx - 12), cx + 6, ry, TEXT, false);
            g.fill(barX, ry, barEnd, ry + 8, STRIPE);
            int bw = (int) Math.max(1, (barEnd - barX) * (double) e.paidYou / top);
            g.fill(barX, ry, barX + bw, ry + 8, GOLD);
            int pct = total > 0 ? (int) Math.round(100.0 * e.paidYou / total) : 0;
            rightText(g, Money.format(e.paidYou) + "  " + pct + "%", right, ry, GOLD);
        }
        if (s.isEmpty()) g.text(font, "Nobody has paid you yet", cx + 6, rowsTop, MUTED, false);
    }

    // ---------------- Sessions tab ----------------

    private static final java.time.format.DateTimeFormatter WHEN =
            java.time.format.DateTimeFormatter.ofPattern("MMM d  h:mm a", java.util.Locale.US);

    private static String when(long millis) {
        return java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).format(WHEN);
    }

    private void drawSessions(GuiGraphicsExtractor g, int cx, int cw, int mouseX, int mouseY) {
        boolean active = PaymentLedger.isSessionActive();
        List<Entry> cur = PaymentLedger.entries(true);
        long in = 0, out = 0;
        for (Entry e : cur) { in += e.paidYou; out += e.youPaid; }
        long profit = in - out;

        // Current session cards
        int y = 38, gap = 6, cardW = (cw - gap * 4) / 5;
        card(g, cx, y, cardW, "STATUS", active ? "RUNNING" : "ENDED",
                "since " + when(PaymentLedger.sessionStartTime()), active ? GREEN : RED, active ? GREEN : RED);
        card(g, cx + (cardW + gap), y, cardW, "LENGTH", PaymentLedger.formatDuration(PaymentLedger.sessionMillis()),
                active ? "live" : "final", LINE, TEXT);
        card(g, cx + (cardW + gap) * 2, y, cardW, "RECEIVED", Money.format(in), cur.size() + (cur.size() == 1 ? " player" : " players"), 0, TEXT);
        card(g, cx + (cardW + gap) * 3, y, cardW, "SENT", Money.format(out), "paid out", 0, TEXT);
        card(g, cx + (cardW + gap) * 4, y, cardW, "YOUR PROFIT", (profit >= 0 ? "+" : "") + Money.format(profit),
                "this session", 0, profit >= 0 ? GREEN : RED);

        // History table
        List<PaymentLedger.SessionRecord> hist = PaymentLedger.history();
        int py = 90, ph = height - 8 - py;
        panel(g, cx, py, cw, ph, GOLD);
        g.text(font, "PAST SESSIONS", cx + 6, py + 7, GOLD, false);

        long total = 0, best = Long.MIN_VALUE;
        for (PaymentLedger.SessionRecord r : hist) { total += r.profit(); best = Math.max(best, r.profit()); }
        String summary = hist.isEmpty() ? "NONE YET" : hist.size() + " SESSIONS  ·  TOTAL "
                + (total >= 0 ? "+" : "") + Money.format(total) + "  ·  BEST +" + Money.format(Math.max(0, best));
        int titleEnd = cx + 6 + font.width("PAST SESSIONS") + 10 + (hist.isEmpty() ? 0 : 62);
        rightText(g, fit(summary, cx + cw - 6 - titleEnd), cx + cw - 6, py + 7, MUTED);

        int lenX = cx + cw * 30 / 100, inX = cx + cw * 46 / 100, outX = cx + cw * 62 / 100,
                plX = cx + cw * 76 / 100, right = cx + cw - 44, hy = py + 22;
        g.text(font, "STARTED", cx + 6, hy, MUTED, false);
        g.text(font, "LENGTH", lenX, hy, MUTED, false);
        g.text(font, "RECEIVED", inX, hy, MUTED, false);
        g.text(font, "SENT", outX, hy, MUTED, false);
        g.text(font, "PLAYERS", plX, hy, MUTED, false);
        rightText(g, "PROFIT", right, hy, MUTED);

        int rowsTop = hy + 13, rowH = 12, visible = Math.max(1, (py + ph - 6 - rowsTop) / rowH);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, hist.size() - visible)));
        for (int i = 0; i < visible && scroll + i < hist.size(); i++) {
            PaymentLedger.SessionRecord r = hist.get(scroll + i);
            int ry = rowsTop + i * rowH;
            if (i % 2 == 0) g.fill(cx + 4, ry - 2, cx + cw - 4, ry + 10, STRIPE);
            g.text(font, fit(when(r.start), lenX - cx - 10), cx + 6, ry, TEXT, false);
            g.text(font, PaymentLedger.formatDuration(r.length()), lenX, ry, TEXT, false);
            g.text(font, Money.format(r.received), inX, ry, GOLD, false);
            g.text(font, Money.format(r.sent), outX, ry, RED, false);
            g.text(font, String.valueOf(r.players), plX, ry, TEXT, false);
            long p = r.profit();
            rightText(g, (p >= 0 ? "+" : "") + Money.format(p), right, ry, p >= 0 ? GREEN : RED);
        }
        if (hist.isEmpty()) g.text(font, "Ended sessions show up here", cx + 6, rowsTop, MUTED, false);

        for (var e : deleteButtons.entrySet()) smallButton(g, e.getKey(), pendingDelete == e.getValue(), mouseX, mouseY);
        if (clearAllButton != null) smallButton(g, clearAllButton, pendingDelete == -1, mouseX, mouseY);
    }

    private void smallButton(GuiGraphicsExtractor g, Button b, boolean armed, int mx, int my) {
        int x = b.getX(), y = b.getY(), w = b.getWidth(), h = b.getHeight();
        boolean hover = over(b, mx, my);
        g.fill(x, y, x + w, y + h, armed ? RED : hover ? HOVER : PANEL);
        outline(g, x, y, w, h, armed || hover ? RED : LINE);
        String s = armed ? "Sure?" : b.getMessage().getString();
        g.text(font, s, x + (w - font.width(s)) / 2, y + (h - 8) / 2 + 1, armed ? DARK : hover ? RED : MUTED, false);
    }

    // ---------------- helpers ----------------

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY) * 2);
        if (tab == Tab.SESSIONS) rebuildWidgets();
        return true;
    }

    private static int yourColor(long v) { return v > 0 ? GREEN : v < 0 ? RED : MUTED; }

    private static String signed(long v) {
        return v == 0 ? "$0" : (v > 0 ? "+" : "-") + Money.format(Math.abs(v));
    }

    private static int netColor(long n) { return n > 0 ? GOLD : n < 0 ? RED : MUTED; }

    private void rightText(GuiGraphicsExtractor g, String s, int rightX, int y, int color) {
        g.text(font, s, rightX - font.width(s), y, color, false);
    }

    private String fit(String s, int w) {
        if (w <= 0) return "";
        if (font.width(s) <= w) return s;
        return font.plainSubstrByWidth(s, Math.max(0, w - font.width("..."))) + "...";
    }
}
