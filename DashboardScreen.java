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
    private enum Tab { DASHBOARD, PLAYERS, PAID_YOU }

    private static Tab tab = Tab.DASHBOARD;
    private static boolean sessionView = false;

    private static final int SIDE = 112;
    private static final int BG = 0xF00A0D10, SIDE_BG = 0xFF0E1215, PANEL = 0xFF14191D, STRIPE = 0xFF191F24,
            LINE = 0xFF262D33, TEXT = 0xFFE6E6E6, MUTED = 0xFF8A9096, GOLD = 0xFFF2C14E, RED = 0xFFE8475A,
            GREEN = 0xFF2E8B57;

    private int scroll = 0;
    private long confirmUntil = 0;
    private Button endButton;

    public DashboardScreen() {
        super(Component.literal("Bettors Dashboard"));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        addTab("DASHBOARD", Tab.DASHBOARD, 40);
        addTab("PLAYERS", Tab.PLAYERS, 62);
        addTab("PAID YOU", Tab.PAID_YOU, 84);

        addRenderableWidget(Button.builder(Component.literal((sessionView ? "> " : "") + "SESSION"),
                b -> { sessionView = true; scroll = 0; rebuildWidgets(); }).bounds(width - 136, 6, 62, 16).build());
        addRenderableWidget(Button.builder(Component.literal((!sessionView ? "> " : "") + "ALL TIME"),
                b -> { sessionView = false; scroll = 0; rebuildWidgets(); }).bounds(width - 70, 6, 62, 16).build());

        endButton = addRenderableWidget(Button.builder(Component.literal("END SESSION"), b -> onEndSession())
                .bounds(8, height - 48, SIDE - 16, 18).build());
        addRenderableWidget(Button.builder(Component.literal("CLOSE MENU"), b -> onClose())
                .bounds(8, height - 26, SIDE - 16, 18).build());
    }

    private void addTab(String label, Tab t, int y) {
        addRenderableWidget(Button.builder(Component.literal((tab == t ? "> " : "") + label),
                b -> { tab = t; scroll = 0; rebuildWidgets(); }).bounds(8, y, SIDE - 16, 18).build());
    }

    private void onEndSession() {
        long now = System.currentTimeMillis();
        if (now > confirmUntil) { // first click asks for confirmation
            confirmUntil = now + 3000;
            endButton.setMessage(Component.literal("CLICK TO CONFIRM"));
            return;
        }
        confirmUntil = 0;
        endButton.setMessage(Component.literal("END SESSION"));
        PaymentListener.announceEnd(PaymentLedger.endSession());
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

        if (confirmUntil != 0 && System.currentTimeMillis() > confirmUntil) {
            confirmUntil = 0;
            endButton.setMessage(Component.literal("END SESSION"));
        }

        // Sidebar
        g.text(font, "BETTORS", 10, 10, GOLD, false);
        g.text(font, "PAYMENTS", 10, 21, MUTED, false);

        int sy = height - 76;
        g.text(font, "SESSION TIME", 10, sy, MUTED, false);
        g.text(font, PaymentLedger.formatDuration(PaymentLedger.sessionMillis()), 10, sy + 11, GOLD, false);
        if (PaymentLedger.lastSessionLength() >= 0 && sy - 26 > 108) {
            long p = PaymentLedger.lastSessionProfit();
            g.text(font, "LAST SESSION", 10, sy - 26, MUTED, false);
            g.text(font, PaymentLedger.formatDurationShort(PaymentLedger.lastSessionLength()) + "  "
                    + (p >= 0 ? "+" : "") + Money.format(p), 10, sy - 15, p >= 0 ? GOLD : RED, false);
        }

        // Header
        int cx = SIDE + 10, cw = width - cx - 8;
        String who = "• " + minecraft.getUser().getName().toUpperCase();
        g.text(font, who, cx, 11, GOLD, false);
        int tx = cx + font.width(who) + 16;
        g.text(font, "SESSION", tx, 11, MUTED, false);
        g.text(font, PaymentLedger.formatDuration(PaymentLedger.sessionMillis()), tx + font.width("SESSION") + 5, 11, TEXT, false);
        g.fill(SIDE + 1, 28, width, 29, LINE);

        List<Entry> list = PaymentLedger.entries(sessionView);
        switch (tab) {
            case DASHBOARD -> drawDashboard(g, list, cx, cw);
            case PLAYERS -> drawLedger(g, list, cx, cw);
            case PAID_YOU -> drawPaidYou(g, list, cx, cw);
        }
    }

    // ---------------- Dashboard tab ----------------

    private void drawDashboard(GuiGraphicsExtractor g, List<Entry> list, int cx, int cw) {
        long paid = 0, sent = 0, up = 0, down = 0;
        for (Entry e : list) {
            paid += e.paidYou;
            sent += e.youPaid;
            long n = e.net();
            if (n > 0) up += n; else down -= n;
        }

        int y = 34, gap = 4, cardW = (cw - gap * 4) / 5;
        card(g, cx, y, cardW, "PLAYERS", String.valueOf(list.size()), sessionView ? "THIS SESSION" : "ALL TRACKED", LINE, GOLD);
        card(g, cx + (cardW + gap), y, cardW, "PAID YOU", Money.format(paid), "RECEIVED", GOLD, GOLD);
        card(g, cx + (cardW + gap) * 2, y, cardW, "YOU PAID", Money.format(sent), "SENT", RED, RED);
        card(g, cx + (cardW + gap) * 3, y, cardW, "TOTAL UP", Money.format(up), "NET THEY WON", GOLD, GOLD);
        card(g, cx + (cardW + gap) * 4, y, cardW, "TOTAL DOWN", Money.format(down), "NET YOU WON", RED, RED);

        int chartY = 78;
        int chartH = Math.max(50, (height - 8 - chartY) * 45 / 100);
        g.fill(cx, chartY, cx + cw, chartY + chartH, PANEL);
        g.fill(cx, chartY, cx + cw, chartY + 1, GREEN);
        g.text(font, "PLAYER NET BY RANK", cx + 6, chartY + 5, TEXT, false);
        g.text(font, "bettors ordered by net", cx + 14 + font.width("PLAYER NET BY RANK"), chartY + 5, MUTED, false);
        drawChart(g, list, cx + 6, chartY + 18, cw - 12, chartH - 24);

        int by = chartY + chartH + 6, bh = height - 8 - by;
        if (bh < 34) return;
        int lw = cw * 3 / 5 - 3, rx = cx + lw + 6, rw = cx + cw - rx;

        // Snapshot (left)
        g.fill(cx, by, cx + lw, by + bh, PANEL);
        g.fill(cx, by, cx + lw, by + 1, LINE);
        g.text(font, "PLAYER NET SNAPSHOT", cx + 6, by + 5, TEXT, false);
        int hy = by + 18, paidX = cx + lw * 55 / 100, right = cx + lw - 6;
        g.text(font, "PLAYER", cx + 6, hy, MUTED, false);
        g.text(font, "PAID YOU", paidX, hy, MUTED, false);
        rightText(g, "NET", right, hy, MUTED);

        List<Entry> byPaid = new ArrayList<>(list);
        byPaid.sort(Comparator.comparingLong((Entry e) -> e.paidYou).reversed());
        int rowsTop = hy + 12, rows = Math.max(0, (by + bh - 4 - rowsTop) / 11);
        for (int i = 0; i < Math.min(rows, byPaid.size()); i++) {
            Entry e = byPaid.get(i);
            int ry = rowsTop + i * 11;
            g.text(font, fit((i + 1) + ". " + e.name, paidX - cx - 12), cx + 6, ry, TEXT, false);
            g.text(font, Money.format(e.paidYou), paidX, ry, GOLD, false);
            long n = e.net();
            rightText(g, (n > 0 ? "+" : "") + Money.format(n), right, ry, netColor(n));
        }

        // Top up / top down (right)
        g.fill(rx, by, rx + rw, by + bh, PANEL);
        g.fill(rx, by, rx + rw, by + 1, LINE);
        int half = rw / 2;
        List<Entry> ups = new ArrayList<>(), downs = new ArrayList<>();
        for (Entry e : list) { if (e.net() > 0) ups.add(e); else if (e.net() < 0) downs.add(e); }
        ups.sort(Comparator.comparingLong(Entry::net).reversed());
        downs.sort(Comparator.comparingLong(Entry::net));
        topList(g, "TOP UP", ups, rx + 6, by + 5, half - 10, bh, GOLD);
        topList(g, "TOP DOWN", downs, rx + half + 4, by + 5, half - 10, bh, RED);
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
        g.fill(x, y, x + w, y + 38, PANEL);
        g.fill(x, y, x + w, y + 2, accent);
        g.text(font, fit(label, w - 10), x + 5, y + 6, MUTED, false);
        g.text(font, fit(value, w - 10), x + 5, y + 17, valueColor, false);
        g.text(font, fit(sub, w - 10), x + 5, y + 28, MUTED, false);
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
        int py = 34, ph = height - 8 - py;
        g.fill(cx, py, cx + cw, py + ph, PANEL);
        g.fill(cx, py, cx + cw, py + 2, GOLD);
        g.text(font, (sessionView ? "SESSION" : "ALL-TIME") + " PLAYER LEDGER", cx + 6, py + 7, GOLD, false);
        rightText(g, list.size() + " PLAYERS TRACKED", cx + cw - 6, py + 7, MUTED);

        int paidX = cx + cw * 50 / 100, sentX = cx + cw * 68 / 100, right = cx + cw - 6, hy = py + 22;
        g.text(font, "PLAYER", cx + 6, hy, MUTED, false);
        g.text(font, "PAID YOU", paidX, hy, MUTED, false);
        g.text(font, "YOU PAID", sentX, hy, MUTED, false);
        rightText(g, "NET ON YOU", right, hy, MUTED);

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
            g.text(font, Money.format(e.paidYou), paidX, ry, GOLD, false);
            g.text(font, Money.format(e.youPaid), sentX, ry, RED, false);
            long n = e.net();
            String net = n > 0 ? "UP " + Money.format(n) : n < 0 ? "DOWN " + Money.format(-n) : "EVEN $0";
            rightText(g, net, right, ry, netColor(n));
        }
        if (s.isEmpty()) g.text(font, "No payments logged yet", cx + 6, rowsTop, MUTED, false);

        String legend = "UP = they received more from you  ·  DOWN = they paid you more";
        int legendW = Math.min(font.width(legend), cw - 12);
        g.text(font, fit(legend, cw - 12), cx + (cw - legendW) / 2, py + ph - 11, MUTED, false);
    }

    // ---------------- Paid You tab ----------------

    private void drawPaidYou(GuiGraphicsExtractor g, List<Entry> list, int cx, int cw) {
        int py = 34, ph = height - 8 - py;
        g.fill(cx, py, cx + cw, py + ph, PANEL);
        g.fill(cx, py, cx + cw, py + 2, GOLD);

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

    // ---------------- helpers ----------------

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY) * 2);
        return true;
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
