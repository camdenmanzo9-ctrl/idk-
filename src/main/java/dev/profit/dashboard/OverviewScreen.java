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
            TEXT = 0xFFF2F0FA, MUTED = 0xFFADA9C4, DIM = 0xFF8A86A3, PURPLE = 0xFF8B5CF6, PURPLE_LIGHT = 0xFFB794F6,
            GOLD = 0xFFE0B24A, BLUE = 0xFF7B8CFF, RED = 0xFFFF5A6E, GREEN = 0xFF4ADE80, AMBER = 0xFFE0A030;

    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final String[] RANGE_LABELS = {"24H", "7D", "30D", "ALL"};
    private static final long[] RANGES = {DAY, 7 * DAY, 30 * DAY, 0};
    private static final String[] TAB_LABELS = {"OVERVIEW", "WIN / LOSS", "RAKEBACK", "SESSIONS", "AUCTIONS", "PROFIT PANEL"};

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
    private Object pendingFocus;   // the search box to focus on the next frame
    private static String search = "";
    private final List<Button> panelButtons = new ArrayList<>();
    private long confirmUntil = 0;
    private static int scroll = 0;

    private int fx, fy, fw, fh, escX;
    private boolean showEsc = true;
    private float tabScale = 0.76f;
    private int rangeGroupX, tabGroupX, tabGroupEnd;

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
        Fonts.draw(g, font, s, x, y, color, scale, bold);
    }

    private int w(String s, float scale) { return Fonts.width(font, s, scale, false); }
    private int wb(String s, float scale) { return Fonts.width(font, s, scale, true); }

    private int caps(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        int cx = x;
        for (char ch : s.toCharArray()) {
            String t = String.valueOf(ch);
            text(g, t, cx, y, color, 0.76f, true);
            cx += wb(t, 0.76f);
        }
        return cx - x;
    }

    private int capsW(String s) {
        int t = 0;
        for (char ch : s.toCharArray()) t += wb(String.valueOf(ch), 0.76f);
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
        if (itemSearch != null && itemSearch.isFocused() && k != GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(event);
        if (k >= GLFW.GLFW_KEY_1 && k <= GLFW.GLFW_KEY_4) { range = k - GLFW.GLFW_KEY_1; return true; }
        if (k == GLFW.GLFW_KEY_SPACE) { togglePause(); return true; }
        if (k == GLFW.GLFW_KEY_TAB) { tab = (tab + 1) % TAB_LABELS.length; scroll = 0; rebuildWidgets(); return true; }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY) * 3);
        if (tab == 3 || (tab == 1 && wlPlayer == null)) rebuildWidgets();
        return true;
    }

    // ---------------- layout ----------------
    private int small(String s) { return w(s, 0.75f) + 12; }

    private Button btn(String label, int x, int y, int bw, Runnable r) {
        return addRenderableWidget(Button.builder(Component.literal(label), b -> r.run()).bounds(x, y, bw, 16).build());
    }

    private int contentY() { return fy + 30; }
    private int contentBottom() { return fy + fh - 22; }

    @Override
    protected void init() {
        // inset like the reference: dark space all around, and it stops growing on very wide screens
        int mx = Math.max(14, width / 26), my = Math.max(10, height / 22);
        fw = Math.min(width - mx * 2, 980);
        fh = height - my * 2;
        fx = (width - fw) / 2;
        fy = my;
        rangeButtons.clear();
        tabButtons.clear();
        filterButtons.clear();
        panelButtons.clear();
        sessionButton = null;

        // far right: gear (settings)
        int gearX = fx + fw - 10 - 20;
        headerSettings = btn("", gearX, fy + 8, 20, () -> {
            if (minecraft != null) minecraft.setScreen(new SettingsScreen(this));
        });
        // ranges: one rounded bar [24H 7D 30D ALL]
        int bx = gearX - 8 - 2;
        for (int i = RANGE_LABELS.length - 1; i >= 0; i--) {
            final int r = i;
            int bw = wb(RANGE_LABELS[i], 0.74f) + 12;
            rangeButtons.add(0, btn(RANGE_LABELS[i], bx - bw, fy + 8, bw, () -> range = r));
            bx -= bw + 1;
        }
        rangeGroupX = bx - 1;
        escX = rangeGroupX - 10;   // tabs end here
        settingsButton = null;
        searchBox = null;
        sessionDelButtons.clear();
        clearSessionsBtn = null;
        wlRows.clear();
        wlBack = null;
        itemSearch = null;
        auctionButtons.clear();

        // tabs: one rounded bar, measured in bold, shrunk if needed so nothing is ever cut off
        int leftEnd = fx + 27 + capsW("DASHBOARD") + 6 + w("v1.0", 0.74f) + 10 + capsW("DATA LIVE") + 20 + 12;
        int rightEnd = escX;
        float[] scales = {0.76f, 0.72f, 0.66f, 0.6f};
        int[] pads = {14, 12, 10, 8};
        int pad = 14;
        tabScale = 0.76f;
        for (int attempt = 0; attempt < scales.length; attempt++) {
            tabScale = scales[attempt];
            pad = pads[attempt];
            int tw = 4;
            for (String t : TAB_LABELS) tw += wb(t, tabScale) + pad + 1;
            if (tw <= rightEnd - leftEnd) break;
        }
        int tabsW = 0;
        for (String t : TAB_LABELS) tabsW += wb(t, tabScale) + pad + 1;
        int tx = Math.max(leftEnd + 2, rightEnd - tabsW - 2);   // sits next to the range bar, like the design
        tabGroupX = tx - 2;
        for (int i = 0; i < TAB_LABELS.length; i++) {
            final int t = i;
            int bw = wb(TAB_LABELS[i], tabScale) + pad;
            tabButtons.add(btn(TAB_LABELS[i], tx, fy + 8, bw, () -> { tab = t; scroll = 0; wlPlayer = null; rebuildWidgets(); }));
            tx += bw + 1;
        }
        tabGroupEnd = tx + 1;

        if (tab == 0) {
            // session badge on the right of the welcome row
            int bw = wb("Click again to end", 0.8f) + 30;
            sessionButton = btn("", fx + fw - 10 - bw, welcomeY() + 12, bw, this::onSession);
            sessionButton.setHeight(20);
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
        } else if (tab == 1 && wlPlayer != null) {
            int bw = w("All players", 0.8f) + 26;
            wlBack = btn("All players", fx + 10, contentY() + 4, bw, () -> { wlPlayer = null; scroll = 0; rebuildWidgets(); });
            wlBack.setHeight(18);
        } else if (tab == 1 || tab == 2) {
            int sbw = Math.min(200, fw / 3), sbx = fx + fw - 10 - sbw, sby = contentY() + 30;
            searchBox = new EditBox(font, -4000, -4000, 200, 12, Component.literal("Search"));   // hidden: we draw it ourselves
            searchBox.setMaxLength(32);
            searchBox.setValue(search);
            searchBox.setResponder(v -> { search = v; scroll = 0; });
            addWidget(searchBox);
            Button click = btn("", sbx, sby, sbw, () -> pendingFocus = searchBox);
            click.setHeight(26);
            if (tab == 1) addWinLossRowButtons();
        } else if (tab == 4) {
            initAuctions();
        } else if (tab == 5) {
            Panels.load();
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
                    .bounds(fx + fw - 10 - bw - 10, contentY() + 20, bw, 18).build());
            addSessionRowButtons();
        }
    }

    // Display copy of you (empty hands, no cape), made once per world
    private static PreviewPlayer previewPlayer;
    private static Object previewLevel;

    private PreviewPlayer preview() {
        if (previewPlayer == null || previewLevel != minecraft.level) {
            previewPlayer = new PreviewPlayer(minecraft.level, minecraft.player.getGameProfile());
            previewLevel = minecraft.level;
        }
        previewPlayer.refreshParts();
        return previewPlayer;
    }

    // ---- past-session delete buttons (same layout math as drawSessions) ----
    private final java.util.Map<Button, Integer> sessionDelButtons = new java.util.LinkedHashMap<>();
    private Button clearSessionsBtn;
    private int pendingDel = -2;          // which row is waiting for the 2nd click (-1 = clear all)
    private long pendingUntil = 0;
    private static final int S_CARD_H = 60, S_ROW_H = 18;

    private int sPanelY() { return contentY() + S_CARD_H + 8; }
    private int sRowsY() { return sPanelY() + 44; }
    private int sVisible() { return Math.max(1, (contentBottom() - 6 - sRowsY()) / S_ROW_H); }

    private void addSessionRowButtons() {
        sessionDelButtons.clear();
        int x = fx + 10, cw = fw - 20;
        int size = PaymentLedger.history().size();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, size - sVisible())));
        clearSessionsBtn = null;   // no Clear all button: delete sessions one at a time with the x
        for (int i = 0; i < sVisible() && scroll + i < size; i++) {
            final int idx = scroll + i;
            Button b = btn("", x + cw - 26, sRowsY() + i * S_ROW_H - 1, 16,
                    () -> armOrRun(idx, () -> { PaymentLedger.deleteSession(idx); rebuildWidgets(); }));
            sessionDelButtons.put(b, idx);
        }
    }

    /** First click arms it (turns red, "Sure?"), a second click within 3 seconds does it. */
    private void armOrRun(int key, Runnable action) {
        long now = System.currentTimeMillis();
        if (pendingDel == key && now < pendingUntil) { pendingDel = -2; action.run(); }
        else { pendingDel = key; pendingUntil = now + 3000; }
    }

    // ---- Win / Loss: click a player to open their page ----
    private static String wlPlayer = null;
    private Button wlBack;
    private final java.util.Map<Button, String> wlRows = new java.util.LinkedHashMap<>();

    /** The Win / Loss list in display order (search applied), shared by drawing and click areas. */
    private List<PaymentLedger.Entry> winLossList() {
        List<PaymentLedger.Entry> all = new ArrayList<>();
        String q = search.trim().toLowerCase(Locale.US);
        for (PaymentLedger.Entry e : PaymentLedger.entries(false))
            if (q.isEmpty() || e.name.toLowerCase(Locale.US).contains(q)) all.add(e);
        all.sort(Comparator.comparingLong(PaymentLedger.Entry::net).reversed());
        return all;
    }

    private int wlTableY() { return contentY() + 30 + 34; }
    private int wlRowsY() { return wlTableY() + 8 + 15; }
    private static final int WL_ROW_H = 16;
    private int wlVisible() { return Math.max(1, (contentBottom() - 4 - wlRowsY()) / WL_ROW_H); }

    private void addWinLossRowButtons() {
        wlRows.clear();
        List<PaymentLedger.Entry> all = winLossList();
        int px = fx + 10, pw = fw - 20;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, all.size() - wlVisible())));
        for (int i = 0; i < wlVisible() && scroll + i < all.size(); i++) {
            String name = all.get(scroll + i).name;
            Button b = btn("", px + 4, wlRowsY() + i * WL_ROW_H - 3, pw - 12, () -> { wlPlayer = name; scroll = 0; rebuildWidgets(); });
            b.setHeight(WL_ROW_H);
            wlRows.put(b, name);
        }
    }

    /** Esc on a player page goes back to the list instead of closing. */
    @Override
    public void onClose() {
        if (tab == 1 && wlPlayer != null) { wlPlayer = null; scroll = 0; rebuildWidgets(); return; }
        super.onClose();
    }

    private static String ago(long ts) {
        long m = Math.max(0, (System.currentTimeMillis() - ts) / 60000);
        if (m < 1) return "just now";
        if (m < 60) return m + "m ago";
        long h = m / 60;
        if (h < 24) return h + "h ago";
        return (h / 24) + "d ago";
    }

    private void drawPlayerPage(GuiGraphicsExtractor g, int mx, int my) {
        String name = wlPlayer;
        PaymentLedger.Entry all = PaymentLedger.allTimeFor(name);
        List<PaymentLedger.Payment> pays = PaymentLedger.paymentsFor(name, 0);   // newest first
        List<PaymentLedger.Payment> oldest = new ArrayList<>(pays);
        java.util.Collections.reverse(oldest);

        // back button
        {
            Button b = wlBack;
            boolean hot = over(b, mx, my);
            box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), hot ? 0xFF1E1A2E : PANEL, hot ? PURPLE : BORDER);
            int ax = b.getX() + 8, ay = b.getY() + b.getHeight() / 2;
            for (int i = 0; i < 4; i++) {   // little left arrow
                g.fill(ax + i, ay - i, ax + i + 1, ay - i + 1, hot ? TEXT : MUTED);
                g.fill(ax + i, ay + i, ax + i + 1, ay + i + 1, hot ? TEXT : MUTED);
            }
            text(g, "All players", ax + 9, b.getY() + (b.getHeight() - 7) / 2, hot ? TEXT : MUTED, 0.8f, false);
        }

        // header: face, name, first / last bet
        int hx = fx + 10, hy = contentY() + 30;
        face(g, name, hx, hy, 26);
        text(g, name, hx + 34, hy + 2, TEXT, 1.4f, true);
        String sub = pays.isEmpty() ? "No dated payments yet"
                : "first bet " + day(oldest.get(0).ts) + "   -   last bet " + ago(pays.get(0).ts);
        text(g, sub, hx + 34, hy + 18, DIM, 0.72f, false);

        // stat boxes
        long theirNet = all == null ? 0 : all.net();
        long paidYou = all == null ? 0 : all.paidYou, youPaid = all == null ? 0 : all.youPaid;
        int bets = all == null ? pays.size() : all.payments;
        long biggest = 0;
        for (PaymentLedger.Payment p : pays) biggest = Math.max(biggest, p.youPaid);
        int cy = hy + 34, gap = 8, cw = (fw - 20 - gap * 4) / 5;
        int[] cols = {fx + 10, 0, 0, 0, 0};
        for (int i = 1; i < 5; i++) cols[i] = cols[i - 1] + cw + gap;
        statBox(g, cols[0], cy, cw, "THEIR NET", signedMoney(theirNet), theirNet > 0 ? GREEN : theirNet < 0 ? RED : TEXT,
                theirNet > 0 ? "up on you" : theirNet < 0 ? "down to you" : "even");
        statBox(g, cols[1], cy, cw, "PAID YOU", Money.format(paidYou), TEXT, "their bets");
        statBox(g, cols[2], cy, cw, "YOU PAID", Money.format(youPaid), TEXT, "their wins");
        statBox(g, cols[3], cy, cw, "BETS", String.valueOf(bets), TEXT, "payments");
        statBox(g, cols[4], cy, cw, "BIGGEST WIN", Money.format(biggest), biggest > 0 ? GREEN : TEXT, "one payout");

        // chart (left) + history (right)
        int py = cy + 50, ph = contentBottom() - py;
        int lw = (fw - 28) * 58 / 100, rx = fx + 10 + lw + 8, rw = fx + fw - 10 - rx;
        box(g, fx + 10, py, lw, ph, PANEL, BORDER);
        caps(g, "THEIR NET OVER TIME", fx + 20, py + 10, TEXT);
        long[] series = new long[oldest.size() + 1];
        long run = 0;
        for (int i = 0; i < oldest.size(); i++) {
            run += oldest.get(i).youPaid - oldest.get(i).paidYou;   // their side
            series[i + 1] = run;
        }
        String now = signedMoney(run);
        text(g, now, fx + 10 + lw - 12 - wb(now, 1.1f), py + 8, run > 0 ? GREEN : run < 0 ? RED : MUTED, 1.1f, true);
        chart(g, series, fx + 20, py + 28, lw - 30, ph - 38);

        box(g, rx, py, rw, ph, PANEL, BORDER);
        caps(g, "PAYMENTS", rx + 10, py + 10, TEXT);
        if (!pays.isEmpty()) text(g, String.valueOf(pays.size()), rx + 16 + capsW("PAYMENTS"), py + 10, DIM, 0.72f, false);
        int rowH = 18, ry = py + 28, rows = Math.max(0, (py + ph - 6 - ry) / rowH);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, pays.size() - rows)));
        for (int i = 0; i < rows && scroll + i < pays.size(); i++) {
            PaymentLedger.Payment p = pays.get(scroll + i);
            boolean in = p.paidYou > 0;
            int ty = ry + i * rowH;
            if (i % 2 == 0) g.fill(rx + 6, ty - 3, rx + rw - 6, ty + rowH - 3, 0xFF15121F);
            text(g, day(p.ts) + "  " + clock(p.ts), rx + 12, ty + 2, DIM, 0.74f, false);
            text(g, in ? "bet" : "won", rx + rw * 52 / 100, ty + 2, MUTED, 0.74f, false);
            String amt = in ? "+" + Money.format(p.paidYou) : "-" + Money.format(p.youPaid);
            text(g, amt, rx + rw - 12 - wb(amt, 0.8f), ty + 1, in ? GREEN : RED, 0.8f, true);
        }
        if (pays.isEmpty()) text(g, "No dated payments with " + name + " yet", rx + 12, ry + 2, DIM, 0.8f, false);
        scrollbar(g, rx + rw - 4, ry - 3, py + ph - 6 - ry, rows, pays.size());
    }

    private void statBox(GuiGraphicsExtractor g, int x, int y, int bw, String label, String value, int valueColor, String sub) {
        box(g, x, y, bw, 42, PANEL, BORDER);
        caps(g, label, x + 10, y + 8, MUTED);
        text(g, value, x + 10, y + 19, valueColor, 1.15f, true);
        text(g, sub, x + 10, y + 32, DIM, 0.66f, false);
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
    private int cardsH() { return 80; }
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
    /** Rounded box: border color first, then the fill inset by 1px. */
    private void box(GuiGraphicsExtractor g, int x, int y, int bw, int bh, int fill, int border) {
        int r = Math.max(1, Math.min(4, Math.min(bw, bh) / 5));
        round(g, x, y, bw, bh, r, border);
        round(g, x + 1, y + 1, bw - 2, bh - 2, Math.max(0, r - 1), fill);
    }

    /** Filled rectangle with rounded corners of radius r. */
    static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) { g.fill(x, y, x + w, y + h, color); return; }
        g.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }

    private static int alpha(int col, int a) { return (a << 24) | (col & 0xFFFFFF); }

    private static final boolean GLOWS = false;   // flat look: cleaner and lighter to draw

    private void glow(GuiGraphicsExtractor g, int cx, int cy, int rx, int ry, int color, int strength) {
        if (!GLOWS) return;
        int steps = 10;   // light: 10 soft layers (looks the same, far less to draw)
        int per = Math.max(1, Math.round(strength / (float) steps));
        for (int i = steps; i >= 1; i--) {
            float t = i / (float) steps;
            int gw = Math.round(rx * t), gh = Math.round(ry * t);
            int cw = Math.round(gw * 0.71f);
            g.fill(cx - cw, cy - gh, cx + cw, cy + gh, alpha(color, per));
            g.fill(cx - gw, cy - Math.round(gh * 0.71f), cx - cw, cy + Math.round(gh * 0.71f), alpha(color, per));
            g.fill(cx + cw, cy - Math.round(gh * 0.71f), cx + gw, cy + Math.round(gh * 0.71f), alpha(color, per));
        }
    }

    private static boolean over(Button b, int mx, int my) {
        return b != null && mx >= b.getX() && mx < b.getX() + b.getWidth() && my >= b.getY() && my < b.getY() + b.getHeight();
    }

    private void pill(GuiGraphicsExtractor g, Button b, boolean on, int mx, int my) {
        pill(g, b, on, mx, my, 0.76f);
    }

    private void pill(GuiGraphicsExtractor g, Button b, boolean on, int mx, int my, float sc) {
        int x = b.getX(), y = b.getY(), bw = b.getWidth(), bh = b.getHeight();
        boolean hover = over(b, mx, my);
        if (on) round(g, x, y, bw, bh, 3, PURPLE);
        else if (hover) round(g, x, y, bw, bh, 3, 0xFF221D36);
        String s = b.getMessage().getString();
        text(g, s, x + (bw - wb(s, sc)) / 2, y + (bh - Math.round(9 * sc)) / 2, on ? TEXT : hover ? TEXT : MUTED, sc, true);
    }

    /** Thin, smooth, wavy line (no fill), soft edges. */
    private void smoothLine(GuiGraphicsExtractor g, long[] vals, int x, int y, int w, int h, int color) {
        if (vals.length < 2) { g.fill(x, y + h - 2, x + w, y + h, alpha(color, 90)); return; }
        long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        for (long v : vals) { min = Math.min(min, v); max = Math.max(max, v); }
        if (max == min) { max++; min--; }
        int prev = Integer.MIN_VALUE;
        for (int px = 0; px < w; px++) {
            double t = (double) px * (vals.length - 1) / Math.max(1, w - 1);
            int i0 = (int) t, i1 = Math.min(vals.length - 1, i0 + 1);
            double f = t - i0, e = (1 - Math.cos(Math.PI * f)) / 2;   // rounded waves between points
            double v = vals[i0] + (vals[i1] - vals[i0]) * e;
            int yy = y + (int) Math.round((max - v) / (double) (max - min) * (h - 2));
            int a = prev == Integer.MIN_VALUE ? yy : prev;
            g.fill(x + px, Math.min(a, yy), x + px + 1, Math.max(a, yy) + 2, color);   // solid, 2 px
            prev = yy;
        }
    }

    /** One bar per day, today on the right and brightest. */
    private void bars(GuiGraphicsExtractor g, long[] vals, int x, int y, int bw, int bh, int color) {
        int n = vals.length;
        long max = 1;
        for (long v : vals) max = Math.max(max, v);
        int gap = n > 14 ? 1 : 3;
        float each = (bw - gap * (n - 1)) / (float) n;
        for (int i = 0; i < n; i++) {
            int x0 = x + Math.round(i * (each + gap)), x1 = x + Math.round(i * (each + gap) + each);
            int h = vals[i] <= 0 ? 1 : Math.max(2, Math.round(bh * vals[i] / (float) max));
            boolean today = i == n - 1;
            g.fill(x0, y + bh - h, Math.max(x0 + 1, x1), y + bh, today ? color : alpha(color, vals[i] <= 0 ? 50 : 130));
        }
    }

    /** Rounded bar drawn behind a row of pills. */
    private void groupBar(GuiGraphicsExtractor g, List<Button> btns) {
        if (btns.isEmpty()) return;
        Button a = btns.get(0), z = btns.get(btns.size() - 1);
        box(g, a.getX() - 2, a.getY() - 2, z.getX() + z.getWidth() - a.getX() + 4, a.getHeight() + 4, PANEL, BORDER);
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
        for (int px = 0; px < sw; px += 2) {
            double t = (double) px * (vals.length - 1) / Math.max(1, sw - 1);
            int i0 = (int) t, i1 = Math.min(vals.length - 1, i0 + 1);
            double v = vals[i0] + (vals[i1] - vals[i0]) * (t - i0);
            int yy = y + (int) Math.round((max - v) / (double) (max - min) * (sh - 2)) + 1;
            g.fill(x + px, yy, x + px + 2, y + sh, alpha(color, 34));
            int from = last < 0 ? yy : last;
            g.fill(x + px, Math.min(yy, from), x + px + 2, Math.max(yy, from) + 1, color);
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
        float sc = size / 12f;
        int ty = y + Math.round((size - Fonts.capHeight(sc)) / 2f - Fonts.capTop(sc));   // letter centered top-to-bottom
        text(g, l, x + (size - wb(l, sc)) / 2, ty, TEXT, sc, true);
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
        round(g, fx, fy, fw, fh, 6, BORDER);
        round(g, fx + 1, fy + 1, fw - 2, fh - 2, 5, FRAME);
    }

    private int lastMouseX, lastMouseY;

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (pendingFocus instanceof EditBox eb) { setFocused(eb); pendingFocus = null; }
        super.extractRenderState(g, mouseX, mouseY, delta);
        drawHeader(g, mouseX, mouseY);
        switch (tab) {
            case 0 -> drawOverview(g, mouseX, mouseY);
            case 1 -> { if (wlPlayer != null) drawPlayerPage(g, mouseX, mouseY); else drawWinLoss(g, mouseX, mouseY); }
            case 2 -> drawRakeback(g);
            case 3 -> drawSessions(g, mouseX, mouseY);
            case 4 -> drawAuctions(g, mouseX, mouseY);
            default -> drawPanels(g, mouseX, mouseY);
        }
        g.fill(fx + 1, fy + fh - 16, fx + fw - 1, fy + fh - 15, BORDER);
        String foot = "1-4 range   SPACE pause   TAB next page   ESC close";
        text(g, foot, fx + fw - 10 - w(foot, 0.74f), fy + fh - 10, DIM, 0.74f, false);
        text(g, "Display only - data from your own chat", fx + 10, fy + fh - 10, DIM, 0.74f, false);
    }

    private void drawHeader(GuiGraphicsExtractor g, int mx, int my) {
        int hy = fy + 10;
        int lx = fx + 10;
        donutRing(g, lx + 5, hy + 4);
        int x = lx + 17 + caps(g, "DASHBOARD", lx + 17, hy + 1, TEXT) + 6;
        text(g, "v1.0", x, hy + 2, DIM, 0.74f, false);
        x += w("v1.0", 0.74f) + 10;
        // DATA LIVE badge
        String live = paused ? "PAUSED" : "DATA LIVE";
        int bw = capsW(live) + 20;
        int by0 = fy + 8;
        box(g, x, by0, bw, 16, PANEL, BORDER);
        g.fill(x + 6, by0 + 6, x + 10, by0 + 10, paused ? AMBER : GREEN);
        caps(g, live, x + 14, by0 + 4, MUTED);

        groupBar(g, tabButtons);
        for (int i = 0; i < tabButtons.size(); i++) pill(g, tabButtons.get(i), i == tab, mx, my, tabScale);
        groupBar(g, rangeButtons);
        for (int i = 0; i < rangeButtons.size(); i++) {
            Button b = rangeButtons.get(i);
            boolean on = i == range;
            if (on) round(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), 3, 0xFF2A2440);
            else if (over(b, mx, my)) round(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), 3, 0xFF1E1A2E);
            String s = b.getMessage().getString();
            text(g, s, b.getX() + (b.getWidth() - wb(s, 0.74f)) / 2, b.getY() + (b.getHeight() - 7) / 2, on ? TEXT : MUTED, 0.74f, true);
        }
        if (headerSettings != null) gear(g, headerSettings, mx, my);
        g.fill(fx + 1, fy + 26, fx + fw - 1, fy + 27, BORDER);
    }

    /** Little pixel donut logo: golden dough, pink frosting, sprinkles, a hole in the middle. */
    private static void donut(GuiGraphicsExtractor g, int cx, int cy) {
        final int DOUGH = 0xFFD99A4E, DOUGH_DARK = 0xFFB0763A, FROST = 0xFFF472B6, FROST_LIGHT = 0xFFF9A8D4;
        double R = 5.6, HOLE = 1.9;
        for (int dy = -6; dy <= 6; dy++) {
            for (int dx = -6; dx <= 6; dx++) {
                double d = Math.sqrt((dx + 0.5) * (dx + 0.5) + (dy + 0.5) * (dy + 0.5));
                if (d > R || d < HOLE) continue;
                int col;
                boolean frosting = d < R - 1.2 && (dy < 1 || (dy == 1 && (dx + 7) % 3 != 0));   // frosting with little drips
                if (frosting) col = (dx < -1 && dy < -1) ? FROST_LIGHT : FROST;
                else col = dy > 2 ? DOUGH_DARK : DOUGH;
                g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, col);
            }
        }
        // sprinkles on the frosting
        g.fill(cx - 3, cy - 4, cx - 2, cy - 3, 0xFFFFFFFF);
        g.fill(cx + 2, cy - 4, cx + 3, cy - 3, 0xFFFDE047);
        g.fill(cx + 3, cy - 1, cx + 4, cy, 0xFF60A5FA);
        g.fill(cx - 4, cy - 1, cx - 3, cy, 0xFF4ADE80);
        g.fill(cx, cy - 5, cx + 1, cy - 4, 0xFF60A5FA);
    }

    /** Sharp purple donut: thick ring, clear hole, light top-left, shaded bottom-right. No soft pixels. */
    private static void donutRing(GuiGraphicsExtractor g, int cx, int cy) {
        final int LIGHT = 0xFFC4A5FF, MID = 0xFF8B5CF6, DARK = 0xFF5B3BB0;
        double R = 8.0, HOLE = 3.2;
        for (int dy = -9; dy <= 9; dy++) {
            for (int dx = -9; dx <= 9; dx++) {
                double px = dx + 0.5, py = dy + 0.5;
                double d = Math.sqrt(px * px + py * py);
                if (d > R || d < HOLE) continue;
                double light = -(px + py) / Math.max(0.001, d);           // + toward the top-left
                double inner = (d - HOLE) / (R - HOLE);                   // 0 at the hole, 1 at the edge
                int col = light > 0.55 && inner > 0.25 ? LIGHT : light < -0.45 ? DARK : MID;
                g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, col);
            }
        }
    }

    /** Text with a soft glow around the letters. */
    private void glowText(GuiGraphicsExtractor g, String s, int x, int y, int color, float scale) {
        // soft light BEHIND the name; the letters themselves stay sharp
        int tw = wb(s, scale), th = Math.round(9 * scale);
        softGlow(g, x + tw / 2, y + th / 2, tw / 2 + 10, th / 2 + 6, PURPLE, 40);
        text(g, s, x, y, color, scale, true);
    }

    /** Soft round glow: stacked rounded shapes, very faint each. */
    private static void softGlow(GuiGraphicsExtractor g, int cx, int cy, int rw, int rh, int color, int strength) {
        int layers = 8, per = Math.max(1, strength / layers);
        for (int i = layers; i >= 1; i--) {
            int w = rw * i / layers, h = rh * i / layers;
            round(g, cx - w, cy - h, w * 2, h * 2, Math.min(Math.min(w, h), 12), alpha(color, per));
        }
    }

    /** Settings gear button. */
    private void gear(GuiGraphicsExtractor g, Button b, int mx, int my) {
        boolean hot = over(b, mx, my);
        box(g, b.getX() - 2, b.getY() - 2, b.getWidth() + 4, b.getHeight() + 4, hot ? 0xFF1E1A2E : PANEL, hot ? PURPLE : BORDER);
        int cx = b.getX() + b.getWidth() / 2, cy = b.getY() + b.getHeight() / 2, c = hot ? TEXT : MUTED;
        for (int k = 0; k < 8; k++) {   // teeth
            double a = k * Math.PI / 4;
            int tx = cx + (int) Math.round(Math.cos(a) * 5), ty = cy + (int) Math.round(Math.sin(a) * 5);
            g.fill(tx - 1, ty - 1, tx + 1, ty + 1, c);
        }
        for (int dy = -4; dy <= 4; dy++) for (int dx = -4; dx <= 4; dx++) {   // ring
            int d2 = dx * dx + dy * dy;
            if (d2 <= 16 && d2 >= 5) g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, c);
        }
    }

    // ================= OVERVIEW =================
    private void drawOverview(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        String me = minecraft != null ? minecraft.getUser().getName() : "you";
        int wy = welcomeY();
        glow(g, fx + 90, wy + 22, 110, 26, PURPLE, 24);
        if (minecraft != null && minecraft.player != null && Settings.S.skinFaces) {
            // Standing still and facing straight ahead: the "look at" point is the middle of its own box
            int bx1 = fx + 14, by1 = wy, bx2 = fx + 50, by2 = wy + 46;
            // your skin stands on its own, no tile behind it
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, bx1, by1, bx2, by2, 20, 0.0625f,
                    (bx1 + bx2) / 2f, (by1 + by2) / 2f, preview());
        } else {
            face(g, me, fx + 16, wy + 4, 34);
        }
        int nx = fx + 60;
        text(g, "Welcome back, ", nx, wy + 9, TEXT, 1.6f, true);
        String shown = Settings.S.displayName == null || Settings.S.displayName.isBlank() ? me : Settings.S.displayName;
        glowText(g, shown, nx + wb("Welcome back, ", 1.6f), wy + 9, PURPLE_LIGHT, 1.6f);
        text(g, "Have a great time on DonutSMP!", nx, wy + 27, MUTED, 0.75f, false);

        // session badge
        if (sessionButton != null) {
            boolean active = PaymentLedger.isSessionActive();
            boolean confirming = confirmUntil > System.currentTimeMillis();
            Button b = sessionButton;
            int x = b.getX(), y = b.getY(), bw = b.getWidth(), bh = b.getHeight();
            int col = confirming ? RED : GREEN;
            int bg = confirming ? 0xFF2A0F16 : 0xFF0F1F16, bd = confirming ? 0xFF7A2E3A : 0xFF2E6B45;
            box(g, x, y, bw, bh, over(b, mouseX, mouseY) ? (confirming ? 0xFF3A1520 : 0xFF143024) : bg, bd);
            String s = confirming ? "Click again to end" : active ? "Live  " + hms(PaymentLedger.sessionMillis()) : "Start session";
            int tw = wb(s, 0.8f) + 10;
            int sx0 = x + (bw - tw) / 2;
            boolean dotOn = !active || (System.currentTimeMillis() / 600) % 2 == 0;   // blinks while live
            if (dotOn) round(g, sx0, y + bh / 2 - 2, 4, 4, 2, col);
            text(g, s, sx0 + 10, y + (bh - 7) / 2, col, 0.8f, true);
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
                : balVals.length > 1 ? (balDown ? "\u2193 " : "\u2191 ") + signedMoney(balVals[balVals.length - 1] - balVals[0]) + " " + rangeWord()
                : "updated from /bal";
        card(g, c1, cy, cw, ch, "BALANCE", bv < 0 ? "$---" : Money.format(bv), balSub, GOLD, TEXT, balDown ? RED : GREEN, balVals);

        int days = switch (range) { case 0 -> 1; case 1 -> 7; default -> 30; };
        long[] play = PaymentLedger.playLastDays(Math.max(7, days));   // at least a week of bars
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
        groupBar(g, filterButtons);
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
            text(g, clock(p.ts), fb[0] + 8, y + 3, DIM, 0.76f, false);
            int ix = fb[0] + 8 + w("00:00 pm", 0.76f) + 6;
            face(g, p.name, ix, y, 11);
            text(g, p.name, ix + 15, y - 1, TEXT, 0.85f, true);
            text(g, in ? "paid you" : "you paid", ix + 15, y + 7, DIM, 0.72f, false);
            String amt = signedMoney(in ? p.paidYou : -p.youPaid);
            text(g, amt, fb[0] + fb[2] - 8 - wb(amt, 0.85f), y + 2, in ? GREEN : RED, 0.85f, true);
        }
        if (feed.isEmpty()) text(g, "No payments in this range", fb[0] + 8, ry + 2, DIM, 0.85f, false);

        // chart
        int[] cb = chartBox();
        box(g, cb[0], cb[1], cb[2], cb[3], PANEL, BORDER);
        glow(g, cb[0] + cb[2] * 3 / 4, cb[1] + cb[3] / 3, cb[2] / 4, cb[3] / 3, pc, 30);
        int capW = caps(g, "PROFIT / LOSS", cb[0] + 10, cb[1] + 10, TEXT);
        long spanDays = pays.isEmpty() ? 0 : Math.max(1, (now() - pays.get(0).ts + DAY - 1) / DAY);
        text(g, spanDays + (spanDays == 1 ? " day of data" : " days of data"), cb[0] + 18 + capW, cb[1] + 10, DIM, 0.72f, false);
        String big = signedMoney(net);
        text(g, big, cb[0] + cb[2] - 10 - wb(big, 1.4f), cb[1] + 8, pc, 1.4f, true);
        long hiV = 0, loV = 0;
        for (long v : netSeries) { hiV = Math.max(hiV, v); loV = Math.min(loV, v); }
        int hx = cb[0] + 10, hy2 = cb[1] + 24;
        text(g, "High", hx, hy2, DIM, 0.72f, false);
        hx += w("High", 0.72f) + 4;
        String hs = signedMoney(hiV);
        text(g, hs, hx, hy2, GREEN, 0.72f, true);
        hx += wb(hs, 0.72f) + 12;
        text(g, "Low", hx, hy2, DIM, 0.72f, false);
        hx += w("Low", 0.72f) + 4;
        text(g, signedMoney(loV), hx, hy2, RED, 0.72f, true);
        chart(g, netSeries, cb[0] + 10, cb[1] + 38, cb[2] - 20, cb[3] - 46);
    }

    private static String hms(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    private void card(GuiGraphicsExtractor g, int x, int y, int cw, int ch, String label, String value, String sub,
                      int lineColor, int valueColor, int subColor, long[] series) {
        box(g, x, y, cw, ch, PANEL, BORDER);
        softGlow(g, x + cw * 55 / 100, y + ch / 2, cw / 3, ch / 2 - 4, lineColor, 12);
        caps(g, label, x + 10, y + 9, MUTED);
        round(g, x + cw - 14, y + 10, 5, 5, 2, lineColor);
        text(g, value, x + 10, y + 25, valueColor, 1.5f, true);
        text(g, sub, x + 10, y + 43, subColor, 0.72f, false);
        if (series != null) smoothLine(g, series, x + 10, y + ch - 22, cw - 20, 16, lineColor);
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
        final double span = (double) (hi - lo);
        double zyF = y + (double) hi / span * (ch - 1);
        int zy = (int) Math.round(zyF);
        for (int dx = 0; dx < cw; dx += 8) g.fill(x + dx, zy, x + Math.min(cw, dx + 4), zy + 1, 0xFF3A3358);

        // sharp line: one column at a time, solid 2 px, connected through drops
        int prevI = Integer.MIN_VALUE;
        double lastY = zyF;
        for (int px = 0; px < cw; px++) {
            double t = (double) px * (vals.length - 1) / Math.max(1, cw - 1);
            int i0 = (int) t, i1 = Math.min(vals.length - 1, i0 + 1);
            double v = vals[i0] + (vals[i1] - vals[i0]) * (t - i0);
            double fy = y + (hi - v) / span * (ch - 1);
            int yy = (int) Math.round(fy);
            int col = v >= 0 ? GREEN : RED;
            int cx0 = x + px;
            if (yy < zy) g.fill(cx0, yy + 1, cx0 + 1, zy, alpha(col, 38));        // even fill above zero
            else if (yy > zy) g.fill(cx0, zy + 1, cx0 + 1, yy, alpha(col, 38));   // even fill below zero
            int a = prevI == Integer.MIN_VALUE ? yy : prevI;
            g.fill(cx0, Math.min(a, yy), cx0 + 1, Math.max(a, yy) + 2, col);
            prevI = yy;
            lastY = fy;
        }
        int ex = x + cw - 1, ey = (int) Math.round(lastY);
        int ec = vals[vals.length - 1] >= 0 ? GREEN : RED;
        round(g, ex - 2, ey - 2, 5, 5, 2, TEXT);
        g.fill(ex - 1, ey - 1, ex + 2, ey + 2, ec);
    }

    // ================= shared bits for the list pages =================
    private static final int PINK = 0xFFF472B6;

    private int pageTitle(GuiGraphicsExtractor g, String title, String sub) {
        int x = fx + 10, y = contentY() + 4;
        text(g, title, x, y, TEXT, 1.4f, true);
        text(g, sub, x, y + 14, MUTED, 0.72f, false);
        return y + 26;
    }

    /** Little stat box like "PLAYERS 175". Returns its width. */
    private int chip(GuiGraphicsExtractor g, int x, int y, String label, String value, int valueColor) {
        int cw = Math.max(capsW(label), wb(value, 0.95f)) + 18;
        box(g, x, y, cw, 26, PANEL, BORDER);
        caps(g, label, x + 9, y + 5, MUTED);
        text(g, value, x + 9, y + 14, valueColor, 0.95f, true);
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
        int sbw = Math.min(200, fw / 3), sbx = fx + fw - 10 - sbw;
        searchField(g, sbx, y, sbw, 26, searchBox, "Search players");
    }

    /** Clean search field: dark fill, purple outline when typing, magnifier, your text and a blinking cursor. */
    private void searchField(GuiGraphicsExtractor g, int x, int y, int bw, int bh, EditBox box, String hint) {
        boolean focused = box != null && box.isFocused();
        String value = box == null ? "" : box.getValue();
        int bc = focused ? PURPLE : 0xFF3A3358;
        g.fill(x, y, x + bw, y + bh, 0xFF0E0C17);
        g.fill(x, y, x + bw, y + 1, bc);
        g.fill(x, y + bh - 1, x + bw, y + bh, bc);
        g.fill(x, y, x + 1, y + bh, bc);
        g.fill(x + bw - 1, y, x + bw, y + bh, bc);
        int mx0 = x + 8, my0 = y + (bh - 9) / 2;
        int mc = focused ? TEXT : MUTED;
        g.fill(mx0 + 1, my0, mx0 + 5, my0 + 1, mc);
        g.fill(mx0 + 1, my0 + 5, mx0 + 5, my0 + 6, mc);
        g.fill(mx0, my0 + 1, mx0 + 1, my0 + 5, mc);
        g.fill(mx0 + 5, my0 + 1, mx0 + 6, my0 + 5, mc);
        g.fill(mx0 + 5, my0 + 6, mx0 + 6, my0 + 7, mc);
        g.fill(mx0 + 6, my0 + 7, mx0 + 8, my0 + 9, mc);
        int tx = x + 22, ty = y + (bh - 7) / 2, maxW = bw - 30;
        if (value.isEmpty()) {
            text(g, hint, tx, ty, DIM, 0.8f, false);
        } else {
            String shown = value;
            while (shown.length() > 1 && w(shown, 0.8f) > maxW) shown = shown.substring(1);   // keep the end visible
            text(g, shown, tx, ty, TEXT, 0.8f, false);
            tx += w(shown, 0.8f) + 1;
        }
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) g.fill(tx, ty - 1, tx + 1, ty + 8, TEXT);
    }

    // ================= WIN / LOSS =================
    private void drawWinLoss(GuiGraphicsExtractor g, int mx, int my) {
        List<PaymentLedger.Entry> all = winLossList();   // everyone, all time, won most off you first
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
        drawSearchFrame(g, y);

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
            boolean hot = mx >= px + 4 && mx < px + pw - 8 && my >= ty - 3 && my < ty + rowH - 3;
            if (hot) g.fill(px + 4, ty - 3, px + pw - 8, ty + rowH - 3, 0xFF221D36);
            else if (i % 2 == 0) g.fill(px + 4, ty - 3, px + pw - 8, ty + rowH - 3, 0xFF161323);
            if (hot) g.fill(px + 4, ty - 3, px + 6, ty + rowH - 3, PURPLE);
            text(g, String.valueOf(idx + 1), cNum, ty + 1, DIM, 0.76f, false);
            face(g, e.name, cName, ty - 1, 10);
            text(g, e.name, cName + 14, ty, hot ? PURPLE_LIGHT : TEXT, 0.8f, true);
            text(g, String.valueOf(e.payments), cBets, ty + 1, MUTED, 0.75f, false);
            text(g, Money.format(e.paidYou), cWag, ty + 1, MUTED, 0.75f, false);
            long n = e.net();
            String v = signedMoney(n);
            text(g, v, cNet - wb(v, 0.8f), ty, n > 0 ? GREEN : n < 0 ? RED : MUTED, 0.8f, true);
        }
        if (all.isEmpty()) text(g, "No players yet", cName, ry, DIM, 0.85f, false);
        else text(g, "click a player to see their history", cName + 14 + capsW("PLAYER") + 10, hy, DIM, 0.66f, false);
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
            text(g, String.valueOf(idx + 1), rx + 8, ty + 4, DIM, 0.76f, false);
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
        if (pendingDel != -2 && System.currentTimeMillis() > pendingUntil) pendingDel = -2;

        // ---- current session card ----
        int y = contentY(), ch = S_CARD_H, x = fx + 10, cw = fw - 20;
        box(g, x, y, cw, ch, PANEL, BORDER);
        g.fill(x, y, x + cw, y + 1, active ? GREEN : BORDER);
        caps(g, active ? "SESSION LIVE" : "NO SESSION RUNNING", x + 12, y + 10, active ? GREEN : MUTED);
        text(g, hms(PaymentLedger.sessionMillis()), x + 12, y + 23, TEXT, 1.4f, true);
        text(g, active ? "started " + dayTime(PaymentLedger.sessionStartTime()) : "last session", x + 12, y + 42, DIM, 0.72f, false);
        int sx = x + cw * 30 / 100, step = Math.max(64, cw * 13 / 100);
        stat(g, "RECEIVED", Money.format(in), sx, y + 14, TEXT);
        stat(g, "SENT", Money.format(out), sx + step, y + 14, TEXT);
        stat(g, "PROFIT", signedMoney(profit), sx + step * 2, y + 14, profit >= 0 ? GREEN : RED);
        stat(g, "PAYMENTS", String.valueOf(pays), sx + step * 3, y + 14, TEXT);

        if (sessionButton != null) {
            boolean confirming = confirmUntil > System.currentTimeMillis();
            Button b = sessionButton;
            int bx = b.getX(), by = b.getY(), bw = b.getWidth(), bh = b.getHeight();
            int col = confirming || active ? RED : GREEN;
            box(g, bx, by, bw, bh, PANEL, col);
            if (over(b, mouseX, mouseY)) g.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, alpha(col, 30));
            String s = confirming ? "CONFIRM?" : active ? "END SESSION" : "START SESSION";
            text(g, s, bx + (bw - wb(s, 0.8f)) / 2, by + (bh - 7) / 2, col, 0.8f, true);
        }

        // ---- past sessions ----
        int py = sPanelY(), ph = contentBottom() - py;
        box(g, x, py, cw, ph, PANEL, BORDER);
        caps(g, "PAST SESSIONS", x + 12, py + 11, TEXT);
        List<PaymentLedger.SessionRecord> hist = PaymentLedger.history();
        if (!hist.isEmpty()) text(g, hist.size() + (hist.size() == 1 ? " session" : " sessions"), x + 18 + capsW("PAST SESSIONS"), py + 11, DIM, 0.72f, false);
        if (clearSessionsBtn != null) {
            Button b = clearSessionsBtn;
            boolean armed = pendingDel == -1;
            boolean hot = over(b, mouseX, mouseY);
            box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), armed ? alpha(RED, 70) : hot ? 0xFF1E1A2E : PANEL, armed || hot ? RED : BORDER);
            String s = armed ? "Sure?" : "Clear all";
            text(g, s, b.getX() + (b.getWidth() - w(s, 0.76f)) / 2, b.getY() + (b.getHeight() - 7) / 2, armed || hot ? RED : MUTED, 0.76f, false);
        }

        int c1 = x + 12, c2 = x + cw * 26 / 100, c3 = x + cw * 42 / 100, c4 = x + cw * 57 / 100, c5 = x + cw * 71 / 100, cr = x + cw - 38;
        int hy = py + 28;
        text(g, "STARTED", c1, hy, DIM, 0.72f, true);
        text(g, "LENGTH", c2, hy, DIM, 0.72f, true);
        text(g, "RECEIVED", c3, hy, DIM, 0.72f, true);
        text(g, "SENT", c4, hy, DIM, 0.72f, true);
        text(g, "PAYMENTS", c5, hy, DIM, 0.72f, true);
        text(g, "PROFIT", cr - wb("PROFIT", 0.72f), hy, DIM, 0.72f, true);
        g.fill(x + 6, hy + 10, x + cw - 6, hy + 11, BORDER);

        int ry = sRowsY(), visible = sVisible();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, hist.size() - visible)));
        for (int i = 0; i < visible && scroll + i < hist.size(); i++) {
            int idx = scroll + i;
            PaymentLedger.SessionRecord r = hist.get(idx);
            int ty = ry + i * S_ROW_H;
            if (i % 2 == 0) g.fill(x + 6, ty - 3, x + cw - 6, ty + S_ROW_H - 3, 0xFF15121F);
            int tty = ty + (S_ROW_H - 7) / 2 - 3;
            text(g, dayTime(r.start), c1, tty, TEXT, 0.8f, false);
            text(g, hms(r.length()), c2, tty, MUTED, 0.8f, false);
            text(g, Money.format(r.received), c3, tty, MUTED, 0.8f, false);
            text(g, Money.format(r.sent), c4, tty, MUTED, 0.8f, false);
            text(g, r.payments > 0 ? String.valueOf(r.payments) : "-", c5, tty, MUTED, 0.8f, false);
            String p = signedMoney(r.profit());
            text(g, p, cr - wb(p, 0.8f), tty, r.profit() > 0 ? GREEN : r.profit() < 0 ? RED : MUTED, 0.8f, true);
        }
        for (var e : sessionDelButtons.entrySet()) {   // the x / Sure? on each row
            Button b = e.getKey();
            boolean armed = pendingDel == e.getValue(), hot = over(b, mouseX, mouseY);
            int bx = b.getX(), by = b.getY();
            if (armed) {
                String s = "Sure?";
                int bw = wb(s, 0.7f) + 8;
                box(g, bx + 16 - bw, by, bw, b.getHeight(), alpha(RED, 80), RED);
                text(g, s, bx + 16 - bw + 4, by + (b.getHeight() - 7) / 2, TEXT, 0.7f, true);
            } else {
                int c = hot ? RED : DIM, mx0 = bx + 5, my0 = by + 5;
                for (int k = 0; k < 6; k++) {   // little x
                    g.fill(mx0 + k, my0 + k, mx0 + k + 1, my0 + k + 1, c);
                    g.fill(mx0 + 5 - k, my0 + k, mx0 + 6 - k, my0 + k + 1, c);
                }
            }
        }
        scrollbar(g, x + cw - 4, ry - 3, py + ph - 6 - ry, visible, hist.size());
        if (hist.isEmpty()) text(g, "Ended sessions show up here", c1, ry, DIM, 0.85f, false);
    }

    // ================= AUCTIONS =================
    private EditBox itemSearch;
    private static String itemQuery = "";
    private final java.util.Map<Button, Runnable> auctionButtons = new java.util.LinkedHashMap<>();
    private Button startBtn, clearBtn, switchBtn, gridBtn;
    private final List<Button> durationBtns = new ArrayList<>();
    private final List<Button> colorBtns = new ArrayList<>();
    private static final long[] DURATIONS = {15_000, 30_000, 45_000, 60_000, 120_000, 300_000};
    private static final String[] DURATION_LABELS = {"15s", "30s", "45s", "1m", "2m", "5m"};
    private static final int[] TIMER_COLORS = {GOLD, PURPLE, GREEN, RED, 0xFF5B8CFF, 0xFFF472B6, 0xFF3ADFDF, 0xFFF2F0FA};

    /** Every item in the game with its name, built once. */
    private static List<net.minecraft.world.item.ItemStack> allItems;
    private static List<String> allNames;

    private static void loadItems() {
        if (allItems != null) return;
        allItems = new ArrayList<>();
        allNames = new ArrayList<>();
        for (net.minecraft.world.item.Item it : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            net.minecraft.world.item.ItemStack st = new net.minecraft.world.item.ItemStack(it);
            if (st.isEmpty()) continue;
            allItems.add(st);
            allNames.add(st.getHoverName().getString());
        }
    }

    private static String cachedQuery = null;
    private static List<Integer> cachedItems = null;

    private List<Integer> filteredItems() {
        loadItems();
        if (itemQuery.equals(cachedQuery) && cachedItems != null) return cachedItems;
        String q = itemQuery.trim().toLowerCase(Locale.US);
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < allItems.size(); i++)
            if (q.isEmpty() || allNames.get(i).toLowerCase(Locale.US).contains(q)) out.add(i);
        cachedQuery = itemQuery;
        cachedItems = out;
        return out;
    }

    // layout shared by buttons and drawing
    private int aTop() { return contentY() + 30; }
    private int aLeftW() { return (fw - 28) * 49 / 100; }
    private int aRingH() { return Math.max(96, Math.min(150, (contentBottom() - aTop()) * 42 / 100)); }
    private int aCtrlY() { return aTop() + aRingH() + 6; }
    private int aColorY() { return aCtrlY() + 24; }
    private int aSwitchY() { return aColorY() + 18; }
    private int aBidsY() { return aSwitchY() + 34; }
    private int[] aGrid() {   // x, y, w, h of the item grid
        int rx = fx + 10 + aLeftW() + 8, rw = fx + fw - 10 - rx;
        return new int[]{rx + 8, aTop() + 46, rw - 16, contentBottom() - 16 - (aTop() + 46)};
    }

    private void initAuctions() {
        durationBtns.clear();
        colorBtns.clear();
        int lx = fx + 10, lw = aLeftW();
        startBtn = btn("START AUCTION", lx, aCtrlY(), w("START AUCTION", 0.75f) + 30, () -> {
            if (Auction.running) Auction.endAt = System.currentTimeMillis();   // end now
            else Auction.start();
        });
        startBtn.setHeight(16);
        clearBtn = btn("Clear", startBtn.getX() + startBtn.getWidth() + 8, aCtrlY(), w("Clear", 0.8f) + 18, Auction::clear);
        int dx = lx + lw;
        for (int i = DURATION_LABELS.length - 1; i >= 0; i--) {
            final long d = DURATIONS[i];
            int bw = small(DURATION_LABELS[i]);
            durationBtns.add(0, btn(DURATION_LABELS[i], dx - bw, aCtrlY(), bw, () -> { if (!Auction.running) Auction.durationMs = d; }));
            dx -= bw + 3;
        }
        int cx = lx + capsW("TIMER COLOR") + 12;
        for (int i = 0; i < TIMER_COLORS.length; i++) {
            final int col = TIMER_COLORS[i];
            Button cb = btn("", cx + i * 17, aColorY(), 12, () -> Auction.color = col);
            cb.setHeight(12);
            colorBtns.add(cb);
        }
        switchBtn = btn("", lx, aSwitchY(), lw, () -> Auction.switchOn = !Auction.switchOn);
        switchBtn.setHeight(28);

        int[] gr = aGrid();
        int rx = fx + 10 + aLeftW() + 8, rw = fx + fw - 10 - rx;
        itemSearch = new EditBox(font, -4000, -4000, 200, 12, Component.literal("Search items"));   // hidden: we draw it ourselves
        itemSearch.setMaxLength(32);
        itemSearch.setValue(itemQuery);
        itemSearch.setResponder(v -> { itemQuery = v; scroll = 0; });
        addWidget(itemSearch);
        Button click = btn("", rx + 8, aTop() + 22, rw - 16, () -> pendingFocus = itemSearch);
        click.setHeight(18);
        gridBtn = addRenderableWidget(Button.builder(Component.literal(""), b -> pickItemAtMouse())
                .bounds(gr[0], gr[1], gr[2], gr[3]).build());
    }

    private static final int CELL = 20;

    private void pickItemAtMouse() {
        int[] gr = aGrid();
        int cols = Math.max(1, gr[2] / CELL);
        int col = (lastMouseX - gr[0]) / CELL, row = (lastMouseY - gr[1]) / CELL;
        if (col < 0 || col >= cols || row < 0) return;
        List<Integer> items = filteredItems();
        int idx = (scroll + row) * cols + col;
        if (idx >= 0 && idx < items.size() && !Auction.running) {
            Auction.item = allItems.get(items.get(idx)).copy();
            Auction.itemName = allNames.get(items.get(idx));
            Auction.ended = false;
        }
    }

    /** Smooth ring: lit part (from the top, clockwise) shows the time left. */
    static void ring(GuiGraphicsExtractor g, int cx, int cy, int r, int thick, float frac, int litColor, int restColor) {
        float ro = r, ri = r - thick;
        int lim = r + 1;
        for (int dy = -lim; dy <= lim; dy++) {
            int runStart = 0, runColor = 0;
            for (int dx = -lim; dx <= lim + 1; dx++) {
                int col = 0;
                float cover = 0f;
                if (dx <= lim) {
                    float d = (float) Math.sqrt((dx + 0.5f) * (dx + 0.5f) + (dy + 0.5f) * (dy + 0.5f));
                    cover = Math.max(0f, Math.min(1f, ro - d + 0.5f)) * Math.max(0f, Math.min(1f, d - ri + 0.5f));
                    if (cover > 0f) {
                        double ang = Math.toDegrees(Math.atan2(dx + 0.5, -(dy + 0.5)));   // 0 at the top, clockwise
                        if (ang < 0) ang += 360;
                        col = (litColor != 0 && ang < frac * 360) ? litColor : restColor;
                    }
                }
                boolean solid = cover >= 0.99f;
                int runKey = solid ? col : 0;
                if (runKey != runColor) {   // close the previous solid run
                    if (runColor != 0) g.fill(cx + runStart, cy + dy, cx + dx, cy + dy + 1, runColor);
                    runStart = dx;
                    runColor = runKey;
                }
                if (!solid && cover > 0f) {   // soft edge pixel
                    int a = (int) (((col >>> 24) & 0xFF) * cover);
                    g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, (a << 24) | (col & 0xFFFFFF));
                }
            }
        }
    }

    private void itemIcon(GuiGraphicsExtractor g, net.minecraft.world.item.ItemStack st, int x, int y, float scale) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.item(st, 0, 0);
        g.pose().popMatrix();
    }

    private void drawAuctions(GuiGraphicsExtractor g, int mx, int my) {
        pageTitle(g, "Auctions", "Pick an item, set the timer and start. The biggest single payment before it hits 0 wins.");
        int lx = fx + 10, lw = aLeftW(), top = aTop();

        // ---- ring panel ----
        int rh = aRingH();
        box(g, lx, top, lw, rh, PANEL, BORDER);
        String t = "AUCTIONS";
        caps(g, t, lx + (lw - capsW(t)) / 2, top + 8, TEXT);
        int ccx = lx + lw / 2, ccy = top + rh / 2 + 1, rad = Math.max(30, Math.min(64, rh / 2 - 20));   // room above for the title, below for the winner
        float frac = Auction.running ? Auction.left() / (float) Auction.durationMs : 0f;
        ring(g, ccx, ccy, rad, 5, frac, Auction.running ? Auction.color : 0, 0xFF3C3854);
        int inner = (rad - 5) * 2 - 12;   // room inside the ring for text
        if (!Auction.item.isEmpty()) itemIcon(g, Auction.item, ccx - 12, ccy - rad / 2 - 10, 1.5f);
        String big = Auction.running ? String.format("%d:%02d", Auction.left() / 60000, (Auction.left() / 1000) % 60)
                : Auction.ended ? (Auction.winner != null ? "SOLD" : "NO BIDS") : Auction.item.isEmpty() ? "PICK" : "READY";
        float bs = 1.2f;
        while (bs > 0.7f && wb(big, bs) > inner) bs -= 0.05f;
        int bigY = Auction.item.isEmpty() ? ccy - 8 : ccy + 1;
        text(g, big, ccx - wb(big, bs) / 2, bigY, Auction.running ? Auction.color : TEXT, bs, true);
        String nm = Auction.item.isEmpty() ? "an item" : Auction.itemName;
        float ns = 0.72f;
        while (nm.length() > 3 && w(nm, ns) > inner) nm = nm.substring(0, nm.length() - 2) + ".";   // keep it inside the ring
        text(g, nm, ccx - w(nm, ns) / 2, bigY + 15, MUTED, ns, false);
        if (Auction.ended && Auction.winner != null) {
            String win = Auction.winner + " won the auction!  -  " + Money.format(Auction.winAmount);
            text(g, win, ccx - wb(win, 0.76f) / 2, top + rh - 12, GREEN, 0.76f, true);
        }

        // ---- controls ----
        {
            Button b = startBtn;
            int x = b.getX(), y = b.getY(), bw = b.getWidth(), bh = b.getHeight();
            boolean disabled = !Auction.running && Auction.item.isEmpty();
            int col = Auction.running ? RED : disabled ? 0xFF3A3550 : PURPLE;
            g.fill(x, y, x + bw, y + bh, over(b, mx, my) && !disabled ? alpha(col, 230) : col);
            int tx0 = x + 9, ty0 = y + (bh - 8) / 2;
            if (Auction.running) g.fill(tx0, ty0, tx0 + 7, ty0 + 7, TEXT);
            else for (int i = 0; i < 4; i++) g.fill(tx0 + i, ty0 + i, tx0 + i + 1, ty0 + 8 - i, TEXT);
            String s = Auction.running ? "END NOW" : "START AUCTION";
            text(g, s, x + 22, y + (bh - 7) / 2, TEXT, 0.76f, true);
        }
        {
            Button b = clearBtn;
            box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), over(b, mx, my) ? 0xFF1E1A2E : PANEL, BORDER);
            String c = "Clear";
            text(g, c, b.getX() + (b.getWidth() - w(c, 0.8f)) / 2, b.getY() + (b.getHeight() - 7) / 2,
                    over(b, mx, my) ? TEXT : MUTED, 0.8f, false);
        }
        groupBar(g, durationBtns);
        for (int i = 0; i < durationBtns.size(); i++) pill(g, durationBtns.get(i), DURATIONS[i] == Auction.durationMs, mx, my);

        caps(g, "TIMER COLOR", lx, aColorY() + 3, MUTED);
        for (int i = 0; i < colorBtns.size(); i++) {
            Button b = colorBtns.get(i);
            int x = b.getX(), y = b.getY();
            boolean sel = TIMER_COLORS[i] == Auction.color;
            if (sel) { g.fill(x - 1, y, x + 13, y + 12, TEXT); }
            g.fill(x + 1, y + 1, x + 11, y + 11, TIMER_COLORS[i]);
            g.fill(x + 2, y, x + 10, y + 12, TIMER_COLORS[i]);
            g.fill(x, y + 2, x + 12, y + 10, TIMER_COLORS[i]);
        }

        // ---- auction switch ----
        {
            Button b = switchBtn;
            int x = b.getX(), y = b.getY(), bw = b.getWidth(), bh = b.getHeight();
            box(g, x, y, bw, bh, over(b, mx, my) ? 0xFF181526 : PANEL, BORDER);
            text(g, "Auction switch", x + 10, y + 5, TEXT, 0.8f, true);
            text(g, "On: while an auction runs, money paid to you counts as bids, not bets.", x + 10, y + 17, DIM, 0.72f, false);
            int sw = 24, sh = 12, sx = x + bw - sw - 10, sy = y + (bh - sh) / 2;
            g.fill(sx, sy, sx + sw, sy + sh, Auction.switchOn ? PURPLE : 0xFF2A2640);
            int kx = Auction.switchOn ? sx + sw - 10 : sx + 2;
            g.fill(kx, sy + 2, kx + 8, sy + sh - 2, TEXT);
        }

        // ---- bids ----
        int by = aBidsY(), bh = contentBottom() - by;
        if (bh > 24) {
            box(g, lx, by, lw, bh, PANEL, BORDER);
            caps(g, "BIDS", lx + 8, by + 7, TEXT);
            List<java.util.Map.Entry<String, Long>> bids = Auction.sortedBids();
            int rowH = 14, ry = by + 20, rows = Math.max(0, (by + bh - 4 - ry) / rowH);
            for (int i = 0; i < Math.min(rows, bids.size()); i++) {
                var e = bids.get(i);
                int ty = ry + i * rowH;
                face(g, e.getKey(), lx + 8, ty, 10);
                text(g, e.getKey(), lx + 22, ty + 1, TEXT, 0.8f, true);
                String amt = Money.format(e.getValue());
                text(g, amt, lx + lw - 8 - wb(amt, 0.8f), ty + 1, GREEN, 0.8f, true);
            }
            if (bids.isEmpty()) text(g, Auction.running ? "Waiting for bids..." : "No bids yet", lx + 8, ry, DIM, 0.75f, false);
        }

        // ---- pick an item ----
        int rx = fx + 10 + lw + 8, rw = fx + fw - 10 - rx, rpH = contentBottom() - top;
        box(g, rx, top, rw, rpH, PANEL, BORDER);
        caps(g, "PICK AN ITEM", rx + 8, top + 8, TEXT);
        if (!Auction.item.isEmpty()) text(g, Auction.itemName, rx + rw - 8 - wb(Auction.itemName, 0.76f), top + 8, TEXT, 0.76f, true);
        searchField(g, rx + 8, top + 22, rw - 16, 18, itemSearch, "Search items");

        int[] gr = aGrid();
        List<Integer> items = filteredItems();
        int cols = Math.max(1, gr[2] / CELL), rows = Math.max(1, gr[3] / CELL);
        int totalRows = (items.size() + cols - 1) / cols;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, totalRows - rows)));
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = (scroll + r) * cols + c;
                if (idx >= items.size()) break;
                int x = gr[0] + c * CELL, y = gr[1] + r * CELL;
                var st = allItems.get(items.get(idx));
                boolean hot = mx >= x && mx < x + CELL && my >= y && my < y + CELL;
                boolean sel = !Auction.item.isEmpty() && Auction.item.getItem() == st.getItem();
                if (sel) box(g, x, y, CELL - 2, CELL - 2, alpha(PURPLE, 70), PURPLE);
                else if (hot) g.fill(x, y, x + CELL - 2, y + CELL - 2, 0xFF1E1A2E);
                itemIcon(g, st, x + 1, y + 1, 1f);
            }
        }
        scrollbar(g, gr[0] + gr[2] + 2, gr[1], gr[3], rows, totalRows);
        text(g, items.size() + " items", rx + 8, contentBottom() - 11, DIM, 0.72f, false);
    }

    // ================= PROFIT PANEL =================
    private void drawPanels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = fx + 10, y = contentY();
        caps(g, "PROFIT PANEL", x, y + 4, TEXT);
        text(g, "Turn the Balance HUD panels on or off. They appear and disappear on your screen right away.",
                x, y + 16, DIM, 0.72f, false);
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
            text(g, about[i], bx + 14, by + 24, DIM, 0.72f, false);
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
                x, ny, DIM, 0.72f, false);
    }

    private void stat(GuiGraphicsExtractor g, String label, String value, int x, int y, int color) {
        caps(g, label, x, y, MUTED);
        text(g, value, x, y + 13, color, 1.15f, true);
    }
}
