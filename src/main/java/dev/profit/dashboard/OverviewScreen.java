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
    private static int BG = 0xFF060509, FRAME = 0xFF0D0B15, PANEL = 0xFF12101C, BORDER = 0xFF221E33,
            PURPLE = 0xFF8B5CF6, PURPLE_LIGHT = 0xFFB794F6;   // set from the mod theme
    private static final int TEXT = 0xFFF2F0FA, MUTED = 0xFFADA9C4, DIM = 0xFF8A86A3,
            GOLD = 0xFFE0B24A, BLUE = 0xFF7B8CFF, RED = 0xFFFF5A6E, GREEN = 0xFF4ADE80, AMBER = 0xFFE0A030;

    /** Load the mod theme's colors (Settings > General > Theme). */
    private static void applyPalette() {
        int[] t = Settings.themeColors(Settings.S.modTheme);
        BG = 0xFF000000 | t[0]; FRAME = 0xFF000000 | t[1]; PANEL = 0xFF000000 | t[2]; BORDER = 0xFF000000 | t[3];
        PURPLE = 0xFF000000 | t[4]; PURPLE_LIGHT = 0xFF000000 | t[5];
    }

    /** Text color that reads on top of the accent (dark text on light accents like Mono). */
    private static int onAccent() {
        int r = (PURPLE >> 16) & 0xFF, g = (PURPLE >> 8) & 0xFF, b = PURPLE & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 170 ? 0xFF111111 : TEXT;
    }

    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final String[] RANGE_LABELS = {"TODAY", "7D", "30D", "ALL"};
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
    private long since() {
        if (range == 0)   // TODAY: since midnight, so it only changes when a payment happens
            return java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        return RANGES[range] == 0 ? 0 : now() - RANGES[range];
    }

    // ---------------- font ----------------
    private static final Style INTER = Style.EMPTY.withFont(
            new FontDescription.Resource(Identifier.fromNamespaceAndPath("bettors_dashboard", "inter")));
    private static final Style INTER_BOLD = Style.EMPTY.withFont(
            new FontDescription.Resource(Identifier.fromNamespaceAndPath("bettors_dashboard", "inter_bold")));

    private static Component c(String s, boolean bold) {
        return Component.literal(s).withStyle(bold ? INTER_BOLD : INTER);
    }

    private static final float TEXT_SIZE = 1.25f;   // overall text size

    private void text(GuiGraphicsExtractor g, String s, int x, int y, int color, float scale, boolean bold) {
        Fonts.draw(g, font, s, x, y, color, scale * TEXT_SIZE, bold);
    }

    private int w(String s, float scale) { return Fonts.width(font, s, scale * TEXT_SIZE, false); }
    private int wb(String s, float scale) { return Fonts.width(font, s, scale * TEXT_SIZE, true); }

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
        for (EditBox eb : new EditBox[]{nameBox, qtyBox, secBox})
            if (eb != null && eb.isFocused() && k != GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(event);
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
        return addWidget(Button.builder(Component.literal(label), b -> r.run()).bounds(x, y, bw, 16).build());
    }

    private int contentY() { return fy + 30; }
    private int contentBottom() { return fy + fh - 22; }

    @Override
    protected void init() {
        applyPalette();
        // inset like the reference: dark space all around, and it stops growing on very wide screens
        int mx = Math.max(6, width / 70), my = Math.max(5, height / 60);   // almost edge to edge
        fw = width - mx * 2;
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
        sessionPickButtons.clear();
        clearSessionsBtn = null;
        wlRows.clear();
        wlBack = null;
        itemSearch = null;
        nameBox = null; qtyBox = null; secBox = null;
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
            click.setHeight(36);
            if (tab == 1) addWinLossRowButtons();
        } else if (tab == 4) {
            initAuctions();
        } else if (tab == 5) {
            Panels.load();
            int y = contentY() + 30, rowH = 44, x = fx + 10, rw = fw - 20;
            for (int i = 0; i < 3; i++) {
                final int idx = i;
                panelButtons.add(addWidget(Button.builder(Component.literal(""), b -> {
                    if (idx == 0) Panels.profit = !Panels.profit;
                    else if (idx == 1) Panels.rakeback = !Panels.rakeback;
                    else Panels.balance = !Panels.balance;
                    Panels.save();
                }).bounds(x, y + i * (rowH + 8), rw, rowH).build()));
            }
        } else if (tab == 3) {
            int bw = wb("START SESSION", 0.8f) + 34;
            sessionButton = addWidget(Button.builder(Component.literal(""), b -> onSession())
                    .bounds(fx + 22, sTop() + sCardH() - 30, bw, 20).build());
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

    // ---- sessions page: history clicks, delete buttons ----
    private final java.util.Map<Button, Integer> sessionDelButtons = new java.util.LinkedHashMap<>();
    private final java.util.Map<Button, Integer> sessionPickButtons = new java.util.LinkedHashMap<>();
    private Button clearSessionsBtn;
    private int pendingDel = -2;          // which row is waiting for the 2nd click
    private long pendingUntil = 0;
    private static int pickedSession = -1;   // -1 = the current session
    private static final int S_HIST_ROW = 30;

    private int sTop() { return contentY() + 30; }
    private int sLeftW() { return (fw - 28) / 2; }
    private int sCardH() { return 74; }
    private int[] sHist() {   // x, y, w, h of the history panel
        int rx = fx + 10 + sLeftW() + 8;
        return new int[]{rx, sTop(), fx + fw - 10 - rx, contentBottom() - sTop()};
    }
    private int sHistRowsY() { return sHist()[1] + 30; }
    private int sVisible() { return Math.max(1, (sHist()[1] + sHist()[3] - 6 - sHistRowsY()) / S_HIST_ROW); }

    private void addSessionRowButtons() {
        sessionDelButtons.clear();
        sessionPickButtons.clear();
        clearSessionsBtn = null;
        int[] h = sHist();
        int size = PaymentLedger.history().size();
        if (pickedSession >= size) pickedSession = -1;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, size - sVisible())));
        for (int i = 0; i < sVisible() && scroll + i < size; i++) {
            final int idx = scroll + i;
            int ry = sHistRowsY() + i * S_HIST_ROW;
            Button pick = btn("", h[0] + 8, ry, h[2] - 40, () -> { pickedSession = pickedSession == idx ? -1 : idx; rebuildWidgets(); });
            pick.setHeight(S_HIST_ROW - 4);
            sessionPickButtons.put(pick, idx);
            Button del = btn("", h[0] + h[2] - 28, ry + 5, 16,
                    () -> armOrRun(idx, () -> { PaymentLedger.deleteSession(idx); pickedSession = -1; rebuildWidgets(); }));
            sessionDelButtons.put(del, idx);
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

    private int wlTableY() { return contentY() + 30 + 44; }
    private int wlRowsY() { return wlTableY() + 8 + 15; }
    private static final int WL_ROW_H = 21;
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
            pickedSession = -1;
            rebuildWidgets();
            return;
        }
        long t = System.currentTimeMillis();
        if (t > confirmUntil) { confirmUntil = t + 3000; return; }   // first click asks to confirm
        confirmUntil = 0;
        PaymentLedger.endSession();
        pickedSession = -1;
        rebuildWidgets();   // the ended session shows at the top of the history right away
    }

    private int welcomeY() { return contentY(); }
    private int cardsY() { return welcomeY() + 54; }
    private int cardsH() { return 98; }
    private int[] feedBox() {
        int y = cardsY() + cardsH() + 8, h = contentBottom() - y;
        int bw = (fw - 30) * 31 / 100;   // feed a bit narrower so the chart is bigger
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
        if (fill == PANEL) fill = (0xE6 << 24) | (PANEL & 0xFFFFFF);
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
        cover(g, b, PANEL);
        round(g, x, y, bw, bh, 3, on ? PURPLE : hover ? mixColor(PANEL, PURPLE, 0.45f) : mixColor(PANEL, PURPLE, 0.25f));
        String s = b.getMessage().getString();
        text(g, s, x + (bw - wb(s, sc)) / 2, y + (bh - Math.round(9 * sc)) / 2, on ? onAccent() : hover ? TEXT : MUTED, sc, true);
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
            g.fill(x + px, yy + 2, x + px + 1, y + h + 2, alpha(color, 30));             // filled area under the line
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

    private static int mixColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int gg = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (gg << 8) | bl;
    }

    /** Letter-spaced bold text (tabs). */
    private int spacedW(String s, float sc) {
        int t = 0;
        for (char ch : s.toCharArray()) t += wb(String.valueOf(ch), sc) + 1;
        return Math.max(0, t - 1);
    }

    private void spacedText(GuiGraphicsExtractor g, String s, int x, int y, int color, float sc) {
        for (char ch : s.toCharArray()) {
            String c = String.valueOf(ch);
            text(g, c, x, y, color, sc, true);
            x += wb(c, sc) + 1;
        }
    }

    /** Paint over Minecraft's own button underneath, so its white edge never peeks out at rounded corners. */
    private void cover(GuiGraphicsExtractor g, Button b) { cover(g, b, FRAME); }

    private void cover(GuiGraphicsExtractor g, Button b, int color) {
        g.fill(b.getX(), b.getY(), b.getX() + b.getWidth(), b.getY() + b.getHeight(), color);
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
        // nothing drawn outside the dashboard: your game shows normally around it
        round(g, fx, fy, fw, fh, 6, (0xC0 << 24) | (BORDER & 0xFFFFFF));
        round(g, fx + 1, fy + 1, fw - 2, fh - 2, 5, (0xD8 << 24) | (FRAME & 0xFFFFFF));
    }

    private int lastMouseX, lastMouseY;

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        applyPalette();
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
        text(g, "Display only. Data is read from your chat, nothing is sent to the server.", fx + 10, fy + fh - 10, DIM, 0.7f, false);
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

        for (int i = 0; i < tabButtons.size(); i++) {
            Button b = tabButtons.get(i);
            boolean on = i == tab, hot = over(b, mx, my);
            int fill = on ? PURPLE : hot ? mixColor(PANEL, PURPLE, 0.45f) : mixColor(PANEL, PURPLE, 0.25f);
            cover(g, b);
            round(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), 3, fill);   // solid: Minecraft's button never shows through
            String s = b.getMessage().getString();
            text(g, s, b.getX() + (b.getWidth() - wb(s, tabScale)) / 2, b.getY() + (b.getHeight() - 7) / 2,
                    on ? onAccent() : TEXT, tabScale, true);
        }
        groupBar(g, rangeButtons);
        for (int i = 0; i < rangeButtons.size(); i++) {
            Button b = rangeButtons.get(i);
            boolean on = i == range;
            cover(g, b, PANEL);
            round(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), 3,
                    on ? PURPLE : over(b, mx, my) ? mixColor(PANEL, PURPLE, 0.45f) : mixColor(PANEL, PURPLE, 0.25f));
            String s = b.getMessage().getString();
            text(g, s, b.getX() + (b.getWidth() - wb(s, 0.74f)) / 2, b.getY() + (b.getHeight() - 7) / 2, on ? onAccent() : MUTED, 0.74f, true);
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
        final int MID = PURPLE, LIGHT = PURPLE_LIGHT;
        final int DARK = 0xFF000000 | ((((MID >> 16) & 0xFF) * 6 / 10) << 16) | ((((MID >> 8) & 0xFF) * 6 / 10) << 8) | ((MID & 0xFF) * 6 / 10);
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
        cover(g, b);
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
        text(g, "Welcome, ", nx, wy + 9, TEXT, 1.6f, true);
        String shown = Settings.S.displayName == null || Settings.S.displayName.isBlank() ? me : Settings.S.displayName;
        glowText(g, shown, nx + wb("Welcome, ", 1.6f), wy + 9, PURPLE_LIGHT, 1.6f);
        text(g, "Have a great time on DonutSMP!", nx, wy + 32, MUTED, 0.75f, false);

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

        String since0 = pays.isEmpty() ? "no payments yet" : switch (range) {
            case 0 -> "since midnight";
            case 1 -> "last 7 days";
            case 2 -> "last 30 days";
            default -> "since " + day(pays.get(0).ts);
        };
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
        int rowH = 23, ry = fb[1] + 28, rows = Math.max(0, (fb[1] + fb[3] - 4 - ry) / rowH);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, feed.size() - rows)));
        scrollbar(g, fb[0] + fb[2] - 5, ry - 2, fb[1] + fb[3] - 4 - ry, rows, feed.size());
        for (int i = 0; i < Math.min(rows, feed.size() - scroll); i++) {
            PaymentLedger.Payment p = feed.get(scroll + i);
            boolean in = p.paidYou > 0;
            int y = ry + i * rowH;
            if (i > 0) g.fill(fb[0] + 8, y - 3, fb[0] + fb[2] - 10, y - 2, alpha(BORDER, 200));
            text(g, clock(p.ts), fb[0] + 8, y + 5, DIM, 0.76f, false);
            int ix = fb[0] + 8 + w("00:00 pm", 0.76f) + 6;
            face(g, p.name, ix, y + 2, 13);
            text(g, p.name, ix + 18, y, TEXT, 0.85f, true);
            text(g, in ? "paid you" : "you paid", ix + 18, y + 11, DIM, 0.72f, false);
            String amt = signedMoney(in ? p.paidYou : -p.youPaid);
            text(g, amt, fb[0] + fb[2] - 8 - wb(amt, 0.85f), y + 4, in ? GREEN : RED, 0.85f, true);
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
        int chX = cb[0] + 10, chY = cb[1] + 32, chW = cb[2] - 20, chH = cb[3] - 42;
        for (int dx = 0; dx < chW; dx += 6) {   // faint dashed guide lines top and bottom
            g.fill(chX + dx, chY, chX + Math.min(chW, dx + 3), chY + 1, alpha(BORDER, 200));
            g.fill(chX + dx, chY + chH, chX + Math.min(chW, dx + 3), chY + chH + 1, alpha(BORDER, 200));
        }
        text(g, signedMoney(hiV), chX, chY - 9, DIM, 0.66f, false);
        text(g, signedMoney(loV), chX, chY + chH + 3, DIM, 0.66f, false);
        chart(g, netSeries, chX, chY + 2, chW, chH - 4);
    }

    private static String hms(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    private void card(GuiGraphicsExtractor g, int x, int y, int cw, int ch, String label, String value, String sub,
                      int lineColor, int valueColor, int subColor, long[] series) {
        box(g, x, y, cw, ch, PANEL, BORDER);
        softGlow(g, x + cw * 60 / 100, y + ch / 2, cw / 3, ch / 2 - 4, lineColor, 14);
        g.fill(x + 2, y, x + cw - 2, y + 2, lineColor);                       // colored line on top
        caps(g, label, x + 10, y + 9, MUTED);
        text(g, value, x + 10, y + 28, valueColor, 1.5f, true);
        text(g, sub, x + 10, y + 54, subColor, 0.72f, false);
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
        round(g, ex - 2, ey - 2, 5, 5, 2, ec);   // end dot in the line's own color
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
        int cw = Math.max(capsW(label), wb(value, 0.95f)) + 22;
        box(g, x, y, cw, 36, PANEL, BORDER);
        caps(g, label, x + 11, y + 7, MUTED);
        text(g, value, x + 11, y + 20, valueColor, 0.95f, true);
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
        searchField(g, sbx, y, sbw, 36, searchBox, "Search players");
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
        x += chip(g, x, y, "UP ON YOU", up + " \u00b7 +" + Money.format(upSum), GREEN) + 6;
        chip(g, x, y, "DOWN ON YOU", down + " \u00b7 -" + Money.format(downSum), RED);
        drawSearchFrame(g, y);

        int py = y + 44, ph = contentBottom() - py, pw = fw - 20, px = fx + 10;
        box(g, px, py, pw, ph, PANEL, BORDER);
        int cNum = px + 10, cName = px + 28, cBets = px + pw * 48 / 100, cWag = px + pw * 62 / 100, cNet = px + pw - 14;
        int hy = py + 8;
        caps(g, "#", cNum, hy, DIM);
        caps(g, "PLAYER", cName + 14, hy, DIM);
        caps(g, "BETS", cBets, hy, DIM);
        caps(g, "WAGERED", cWag, hy, DIM);
        caps(g, "NET (THEIR SIDE)", cNet - capsW("NET (THEIR SIDE)"), hy, DIM);
        g.fill(px + 1, hy + 10, px + pw - 1, hy + 11, BORDER);

        int rowH = WL_ROW_H, ry = hy + 15, visible = Math.max(1, (py + ph - 4 - ry) / rowH);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, all.size() - visible)));
        for (int i = 0; i < visible && scroll + i < all.size(); i++) {
            int idx = scroll + i;
            PaymentLedger.Entry e = all.get(idx);
            int ty = ry + i * rowH;
            boolean hot = mx >= px + 4 && mx < px + pw - 8 && my >= ty - 3 && my < ty + rowH - 3;
            if (hot) g.fill(px + 4, ty - 3, px + pw - 8, ty + rowH - 3, 0xFF221D36);
            else if (i > 0) g.fill(px + 8, ty - 3, px + pw - 10, ty - 2, alpha(BORDER, 200));
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

        int py = y + 46, ph = contentBottom() - py, pw = fw - 20, px = fx + 10;
        int rowH = 24, ry = py, visible = Math.max(1, (py + ph - 4 - ry) / rowH);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, losers.size() - visible)));
        for (int i = 0; i < visible && scroll + i < losers.size(); i++) {
            int idx = scroll + i;
            PaymentLedger.Entry e = losers.get(idx);
            long lost = -e.net();
            boolean isReady = lost >= st.rakeMin;
            int ty = ry + i * rowH, rx = px, rw = pw - 8;
            box(g, rx, ty, rw, rowH - 4, PANEL, BORDER);
            text(g, String.valueOf(idx + 1), rx + 10, ty + 5, DIM, 0.7f, false);
            text(g, e.name, rx + 28, ty + 4, TEXT, 0.74f, true);
            String right = isReady ? "READY  owe " + Money.format(lost * st.rakePct / 100)
                    : Money.format(lost) + " / " + Money.format(st.rakeMin);
            text(g, right, rx + rw - 10 - wb(right, 0.74f), ty + 4, isReady ? GREEN : TEXT, 0.74f, true);
            int bx = rx + 28, be = rx + rw - 10, by = ty + rowH - 9;
            g.fill(bx, by, be, by + 2, alpha(BORDER, 255));
            int fill = (int) ((be - bx) * Math.min(1.0, lost / (double) st.rakeMin));
            for (int k = 0; k < fill; k += 2) {   // rainbow: pink -> white -> green along the whole bar
                float t = k / (float) Math.max(1, be - bx);
                int c = t < 0.5f ? mixColor(0xFFF472B6, 0xFFEDE9FE, t * 2f) : mixColor(0xFFEDE9FE, 0xFF4ADE80, (t - 0.5f) * 2f);
                g.fill(bx + k, by, bx + Math.min(fill, k + 2), by + 2, c);
            }
        }
        if (losers.isEmpty()) text(g, q.isEmpty() ? "Nobody has lost to you yet" : "No players match \"" + search.trim() + "\"",
                px + 10, ry + 4, DIM, 0.85f, false);
        scrollbar(g, px + pw - 3, ry, Math.min(ph - 8, visible * rowH), visible, losers.size());
    }

    // ================= SESSIONS =================
    private static String weekday(long ts) {
        return java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US));
    }

    private void drawSessions(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        boolean active = PaymentLedger.isSessionActive();
        if (pendingDel != -2 && System.currentTimeMillis() > pendingUntil) pendingDel = -2;
        List<PaymentLedger.SessionRecord> hist = PaymentLedger.history();
        PaymentLedger.SessionRecord picked = pickedSession >= 0 && pickedSession < hist.size() ? hist.get(pickedSession) : null;

        pageTitle(g, "Sessions", "Start a session when you host. It tracks your profit and every bet until you stop it.");
        int lx = fx + 10, lw = sLeftW(), top = sTop(), ch = sCardH();

        // which session the left side shows
        long from, to, length;
        if (picked != null) { from = picked.start; to = picked.end; length = picked.length(); }
        else { from = PaymentLedger.sessionStartTime(); to = active ? now() : from + PaymentLedger.sessionMillis(); length = PaymentLedger.sessionMillis(); }
        java.util.Map<String, long[]> per = new java.util.LinkedHashMap<>();
        long profit = 0;
        for (PaymentLedger.Payment p : PaymentLedger.paymentsSince(from)) {
            if (p.ts > to) continue;
            long[] a = per.computeIfAbsent(p.name, k -> new long[1]);
            a[0] += p.youPaid - p.paidYou;          // their side
            profit += p.paidYou - p.youPaid;        // your side
        }
        if (picked != null) profit = picked.profit();

        // ---- session card ----
        box(g, lx, top, lw, ch, PANEL, BORDER);
        caps(g, picked != null ? "PAST SESSION" : active ? "SESSION LIVE" : "SESSION", lx + 12, top + 10, picked == null && active ? GREEN : MUTED);
        String when = weekday(from) + "  -  " + clock(from) + " - " + (picked == null && active ? "now" : clock(to));
        text(g, when, lx + 12, top + 21, DIM, 0.66f, false);
        text(g, hms(length), lx + 12, top + 31, TEXT, 1.4f, true);
        caps(g, "PROFIT", lx + lw - 12 - capsW("PROFIT"), top + 10, MUTED);
        String pr = signedMoney(profit);
        text(g, pr, lx + lw - 12 - wb(pr, 1.5f), top + 24, profit > 0 ? GREEN : profit < 0 ? RED : GREEN, 1.5f, true);

        if (sessionButton != null) {
            boolean confirming = confirmUntil > System.currentTimeMillis();
            Button b = sessionButton;
            int bx = b.getX(), by = b.getY(), bw = b.getWidth(), bh = b.getHeight();
            int col = confirming || active ? RED : PURPLE;
            round(g, bx, by, bw, bh, 3, over(b, mouseX, mouseY) ? mixColor(col, 0xFFFFFFFF, 0.12f) : col);
            int ix = bx + 10, iy = by + bh / 2;
            if (confirming || active) g.fill(ix, iy - 3, ix + 6, iy + 3, TEXT);                        // stop square
            else for (int k = 0; k < 4; k++) g.fill(ix + k, iy - 4 + k, ix + k + 1, iy + 4 - k, TEXT);    // play triangle
            String s0 = confirming ? "CONFIRM?" : active ? "END SESSION" : "START SESSION";
            text(g, s0, bx + 22, by + (bh - 7) / 2, TEXT, 0.76f, true);
        }

        // ---- players this session ----
        int py = top + ch + 8, ph = contentBottom() - py;
        box(g, lx, py, lw, ph, PANEL, BORDER);
        caps(g, "PLAYERS THIS SESSION", lx + 12, py + 10, TEXT);
        caps(g, "THEIR SIDE", lx + lw - 12 - capsW("THEIR SIDE"), py + 10, DIM);
        List<java.util.Map.Entry<String, long[]>> players = new ArrayList<>(per.entrySet());
        players.sort((a2, b2) -> Long.compare(b2.getValue()[0], a2.getValue()[0]));
        int prow = 22, pry = py + 30, prows = Math.max(0, (py + ph - 6 - pry) / prow);
        for (int i = 0; i < Math.min(prows, players.size()); i++) {
            var e = players.get(i);
            int ty = pry + i * prow;
            if (i > 0) g.fill(lx + 10, ty - 3, lx + lw - 10, ty - 2, alpha(BORDER, 200));
            face(g, e.getKey(), lx + 12, ty, 10);
            text(g, e.getKey(), lx + 26, ty + 1, TEXT, 0.74f, true);
            long n = e.getValue()[0];
            String v = signedMoney(n);
            text(g, v, lx + lw - 12 - wb(v, 0.74f), ty + 1, n > 0 ? GREEN : n < 0 ? RED : MUTED, 0.74f, true);
        }
        if (players.isEmpty()) text(g, "No bets in this session yet", lx + 12, pry, DIM, 0.74f, false);

        // ---- session history ----
        int[] h = sHist();
        box(g, h[0], h[1], h[2], h[3], PANEL, BORDER);
        int capW = caps(g, "SESSION HISTORY", h[0] + 12, h[1] + 10, TEXT);
        text(g, "click one to see its players", h[0] + 20 + capW, h[1] + 10, DIM, 0.64f, false);
        for (var e : sessionPickButtons.entrySet()) {
            Button b = e.getKey();
            int idx = e.getValue();
            PaymentLedger.SessionRecord r = hist.get(idx);
            boolean sel = idx == pickedSession, hot = over(b, mouseX, mouseY);
            int bx = b.getX(), by = b.getY(), bw = h[2] - 16, bh = b.getHeight();
            round(g, bx, by, bw, bh, 3, sel ? alpha(PURPLE, 40) : hot ? alpha(BORDER, 200) : alpha(BORDER, 110));
            if (sel) g.fill(bx, by + 2, bx + 2, by + bh - 2, PURPLE);
            text(g, weekday(r.start), bx + 10, by + 4, TEXT, 0.74f, true);
            text(g, clock(r.start) + " - " + clock(r.end) + "   " + hms(r.length()), bx + 10, by + 15, DIM, 0.62f, false);
            String p = signedMoney(r.profit());
            text(g, p, bx + bw - 30 - wb(p, 0.8f), by + 8, r.profit() > 0 ? GREEN : r.profit() < 0 ? RED : GREEN, 0.8f, true);
        }
        for (var e : sessionDelButtons.entrySet()) {   // the x / Sure? on each row
            Button b = e.getKey();
            boolean armed = pendingDel == e.getValue(), hot = over(b, mouseX, mouseY);
            int bx = b.getX(), by = b.getY();
            if (armed) {
                String s1 = "Sure?";
                int bw = wb(s1, 0.66f) + 8;
                box(g, bx + 16 - bw, by, bw, b.getHeight(), alpha(RED, 80), RED);
                text(g, s1, bx + 16 - bw + 4, by + (b.getHeight() - 7) / 2, TEXT, 0.66f, true);
            } else {
                int c = hot ? RED : DIM, mx0 = bx + 5, my0 = by + 5;
                for (int k = 0; k < 6; k++) {
                    g.fill(mx0 + k, my0 + k, mx0 + k + 1, my0 + k + 1, c);
                    g.fill(mx0 + 5 - k, my0 + k, mx0 + 6 - k, my0 + k + 1, c);
                }
            }
        }
        scrollbar(g, h[0] + h[2] - 4, sHistRowsY(), Math.min(h[3] - 36, sVisible() * S_HIST_ROW), sVisible(), hist.size());
        if (hist.isEmpty()) text(g, "Ended sessions show up here", h[0] + 12, sHistRowsY() + 2, DIM, 0.74f, false);
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

    // ---- layout shared by click areas and drawing ----
    private EditBox nameBox, qtyBox, secBox;
    private Button nameClick, qtyClick, secClick, svBtn, hueBtn;
    private static float hue = 300f, sat = 0.55f, val = 0.72f;   // color picker
    private static final int CELL = 26;

    private int aTop() { return contentY() + 30; }
    private int aLeftW() { return (fw - 28) * 49 / 100; }
    private int aRingH() { return Math.max(96, Math.min(170, contentBottom() - aTop() - 230)); }   // leaves room for Top bidders
    private int aCtrlY() { return aTop() + aRingH() + 8; }
    private int aFieldsY() { return aCtrlY() + 28; }
    private int aColorY() { return aFieldsY() + 32; }
    private int aSwitchY() { return aColorY() + 46; }
    private int aLastY() { return aSwitchY() + 34; }
    private int[] aGrid() {   // x, y, w, h of the item grid
        int rx = fx + 10 + aLeftW() + 8, rw = fx + fw - 10 - rx;
        return new int[]{rx + 10, aTop() + 48, rw - 24, contentBottom() - 18 - (aTop() + 48)};
    }
    private int[] aFields() {   // name x/w, qty x/w, sec x/w
        int lx = fx + 10, lw = aLeftW();
        int nw = lw * 50 / 100, qw = (lw - nw - 12) / 2;
        return new int[]{lx, nw, lx + nw + 6, qw, lx + nw + 12 + qw, lw - nw - 12 - qw};
    }
    private int[] aPicker() {   // square x/y/w/h, hue bar x, hex box x/w
        int lx = fx + 10, lw = aLeftW();
        int sx = lx + 76, hexW = 64, sw = lw - 76 - 10 - 8 - hexW - 6;
        return new int[]{sx, aColorY() + 10, sw, 30, sx + sw + 4, sx + sw + 18, hexW};
    }

    private static int hsv(float h, float s, float v) {
        float c = v * s, hp = (h % 360f) / 60f, x = c * (1 - Math.abs(hp % 2 - 1)), m = v - c;
        float r, g, b;
        if (hp < 1) { r = c; g = x; b = 0; }
        else if (hp < 2) { r = x; g = c; b = 0; }
        else if (hp < 3) { r = 0; g = c; b = x; }
        else if (hp < 4) { r = 0; g = x; b = c; }
        else if (hp < 5) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return 0xFF000000 | (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }

    private EditBox hiddenBox(String value, int max, java.util.function.Predicate<String> filter, java.util.function.Consumer<String> onChange) {
        final EditBox b = new EditBox(font, -4000, -4000, 200, 12, Component.literal(""));   // hidden: we draw it ourselves
        b.setMaxLength(max);
        b.setValue(value);
        b.setResponder(v -> {
            if (filter != null && !filter.test(v)) {   // numbers only: strip anything else
                b.setValue(v.replaceAll("[^0-9]", ""));
                return;
            }
            onChange.accept(v);
        });
        addWidget(b);
        return b;
    }

    private void initAuctions() {
        colorBtns.clear();
        int lx = fx + 10, lw = aLeftW();
        startBtn = btn("START AUCTION", lx, aCtrlY(), wb("START AUCTION", 0.76f) + 34, () -> {
            if (Auction.running) Auction.endAt = System.currentTimeMillis();   // end now
            else Auction.start();
        });
        startBtn.setHeight(20);
        clearBtn = btn("Clear", startBtn.getX() + startBtn.getWidth() + 8, aCtrlY(), w("Clear", 0.8f) + 22, () -> {
            Auction.clearItem();
            rebuildWidgets();   // empties the NAME / QUANTITY boxes too
        });
        clearBtn.setHeight(20);

        int[] fl = aFields();
        nameBox = hiddenBox(Auction.shortName() == null ? "" : Auction.shortName(), 40, null, v -> Auction.customName = v);
        qtyBox = hiddenBox(String.valueOf(Auction.quantity), 7, v -> v.matches("\\d*"), v -> {
            try { Auction.quantity = Math.max(1, Integer.parseInt(v)); } catch (Exception e) { Auction.quantity = 1; }
        });
        secBox = hiddenBox(String.valueOf(Auction.durationMs / 1000), 4, v -> v.matches("\\d*"), v -> {
            try { if (!Auction.running) Auction.durationMs = Math.max(5, Math.min(3600, Integer.parseInt(v))) * 1000L; } catch (Exception ignored) { }
        });
        int fy0 = aFieldsY() + 9;
        nameClick = btn("", fl[0], fy0, fl[1], () -> pendingFocus = nameBox); nameClick.setHeight(16);
        qtyClick = btn("", fl[2], fy0, fl[3], () -> pendingFocus = qtyBox); qtyClick.setHeight(16);
        secClick = btn("", fl[4], fy0, fl[5], () -> pendingFocus = secBox); secClick.setHeight(16);

        for (int i = 0; i < TIMER_COLORS.length; i++) {   // 2 rows of 4 swatches
            final int col = TIMER_COLORS[i];
            Button cb = btn("", lx + (i % 4) * 16, aColorY() + 10 + (i / 4) * 16, 12, () -> Auction.color = col);
            cb.setHeight(12);
            colorBtns.add(cb);
        }
        int[] pk = aPicker();
        svBtn = btn("", pk[0], pk[1], pk[2], this::pickSV); svBtn.setHeight(pk[3]);
        hueBtn = btn("", pk[4], pk[1], 8, this::pickHue); hueBtn.setHeight(pk[3]);

        switchBtn = btn("", lx, aSwitchY(), lw, () -> Auction.switchOn = !Auction.switchOn);
        switchBtn.setHeight(28);

        int[] gr = aGrid();
        int rx = fx + 10 + aLeftW() + 8, rw = fx + fw - 10 - rx;
        itemSearch = hiddenBox(itemQuery, 32, null, v -> { itemQuery = v; scroll = 0; });
        Button click = btn("", rx + 10, aTop() + 24, rw - 20, () -> pendingFocus = itemSearch);
        click.setHeight(18);
        gridBtn = addWidget(Button.builder(Component.literal(""), b -> pickItemAtMouse())
                .bounds(gr[0], gr[1], gr[2], gr[3]).build());
    }

    private void pickSV() {
        int[] pk = aPicker();
        sat = Math.max(0f, Math.min(1f, (lastMouseX - pk[0]) / (float) Math.max(1, pk[2] - 1)));
        val = Math.max(0f, Math.min(1f, 1f - (lastMouseY - pk[1]) / (float) Math.max(1, pk[3] - 1)));
        Auction.color = hsv(hue, sat, val);
    }

    private void pickHue() {
        int[] pk = aPicker();
        hue = Math.max(0f, Math.min(359f, (lastMouseY - pk[1]) / (float) Math.max(1, pk[3] - 1) * 360f));
        Auction.color = hsv(hue, sat, val);
    }

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
            Auction.customName = Auction.itemName;   // NAME box fills in; you can retype it
            Auction.ended = false;
            rebuildWidgets();
        }
    }

    /** Plain input box drawn like the reference: label above, dark field, your text and a cursor. */
    private void inputField(GuiGraphicsExtractor g, String label, int x, int y, int w, EditBox box, String hint) {
        caps(g, label, x, y, DIM);
        int by = y + 9, bh = 16;
        boolean focused = box != null && box.isFocused();
        box(g, x, by, w, bh, 0xFF0B0A12, focused ? PURPLE : BORDER);
        String v = box == null ? "" : box.getValue();
        int tx = x + 7, ty = by + (bh - 7) / 2;
        if (v.isEmpty()) text(g, hint, tx, ty, DIM, 0.74f, false);
        else {
            String shown = v;
            while (shown.length() > 1 && w(shown, 0.74f) > w - 14) shown = shown.substring(1);
            text(g, shown, tx, ty, TEXT, 0.74f, false);
            tx += w(shown, 0.74f) + 1;
        }
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) g.fill(tx, ty - 1, tx + 1, ty + 8, TEXT);
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
        pageTitle(g, "Auctions", "Pick an item, set the timer and start. Whoever pays you the most before it hits 0 wins.");
        int lx = fx + 10, lw = aLeftW(), top = aTop();
        int acc = Auction.color;

        // ---- ring panel ----
        int rh = aRingH();
        box(g, lx, top, lw, rh, PANEL, BORDER);
        String t = "AUCTIONS";
        caps(g, t, lx + (lw - capsW(t)) / 2, top + 9, acc);
        int ccx = lx + lw / 2, rad = Math.max(30, Math.min(64, rh / 2 - 22)), ccy = top + 20 + rad + 2;
        float frac = Auction.running ? Auction.left() / (float) Auction.durationMs : 1f;
        ring(g, ccx, ccy, rad, 6, frac, acc, Auction.running ? alpha(acc, 60) : acc);
        int inner = (rad - 6) * 2 - 10;
        float iconScale = Math.max(1.2f, Math.min(2f, rad / 30f));
        int iconSize = (int) (16 * iconScale);
        int iconY = ccy - rad + 10;
        if (!Auction.item.isEmpty()) {
            itemIcon(g, Auction.item, ccx - iconSize / 2, iconY, iconScale);
            if (Auction.quantity > 1) {   // quantity badge, beside the item
                String q = "x" + Auction.quantity;
                int qw = wb(q, 0.55f) + 8, qx = ccx + iconSize / 2 - 2, qy = iconY + iconSize - 10;
                round(g, qx, qy, qw, 11, 3, acc);
                text(g, q, qx + 4, qy + 2, onAccentFor(acc), 0.55f, true);
            }
        }
        String big = Auction.running ? String.format("%d:%02d", Auction.left() / 60000, (Auction.left() / 1000) % 60)
                : Auction.ended ? (Auction.winner != null ? "SOLD" : "NO BIDS")
                : Auction.item.isEmpty() ? "PICK" : String.format("%d:%02d", Auction.durationMs / 60000, (Auction.durationMs / 1000) % 60);
        float bs = 1.05f;
        while (bs > 0.6f && wb(big, bs) > inner) bs -= 0.05f;
        int bigY = Auction.item.isEmpty() ? ccy - 8 : iconY + iconSize + 4;
        text(g, big, ccx - wb(big, bs) / 2, bigY, Auction.running && Auction.left() <= 10_000 ? RED : TEXT, bs, true);
        String nm = Auction.item.isEmpty() ? "an item" : Auction.displayName();
        while (nm.length() > 3 && w(nm, 0.52f) > inner - 8) nm = nm.substring(0, nm.length() - 2) + ".";
        text(g, nm, ccx - w(nm, 0.52f) / 2, bigY + 15, MUTED, 0.52f, false);
        String status = Auction.running ? "Bidding is open" : Auction.ended && Auction.winner != null
                ? Auction.winner + " won it  -  " + Money.format(Auction.winAmount)
                : Auction.item.isEmpty() ? "Pick an item on the right" : "Ready when you are";
        text(g, status, ccx - wb(status, 0.7f) / 2, top + rh - 13, Auction.ended && Auction.winner != null ? GREEN : MUTED, 0.7f, true);

        // ---- start + note ----
        {
            Button b = startBtn;
            int x = b.getX(), y = b.getY(), bw = b.getWidth(), bh = b.getHeight();
            boolean disabled = !Auction.running && Auction.item.isEmpty();
            int col = Auction.running ? RED : disabled ? 0xFF3A3550 : PURPLE;
            round(g, x, y, bw, bh, 3, over(b, mx, my) && !disabled ? mixColor(col, 0xFFFFFFFF, 0.12f) : col);
            int ix = x + 10, iy = y + bh / 2;
            if (Auction.running) g.fill(ix, iy - 3, ix + 6, iy + 3, TEXT);
            else for (int i = 0; i < 4; i++) g.fill(ix + i, iy - 4 + i, ix + i + 1, iy + 4 - i, TEXT);
            String s0 = Auction.running ? "END NOW" : "START AUCTION";
            text(g, s0, x + 22, y + (bh - 7) / 2, TEXT, 0.76f, true);
        }
        {   // Clear
            Button b = clearBtn;
            boolean hot = over(b, mx, my);
            box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), hot ? mixColor(PANEL, PURPLE, 0.3f) : PANEL, hot ? PURPLE : BORDER);
            text(g, "Clear", b.getX() + (b.getWidth() - w("Clear", 0.8f)) / 2, b.getY() + (b.getHeight() - 7) / 2, hot ? TEXT : MUTED, 0.8f, false);
        }
        String note = "Biggest single payment wins (bids don't stack)";
        text(g, note, lx + lw - w(note, 0.62f), aCtrlY() + 7, DIM, 0.62f, false);

        // ---- name / quantity / seconds ----
        int[] fl = aFields();
        inputField(g, "NAME", fl[0], aFieldsY(), fl[1], nameBox, "Item name");
        inputField(g, "QUANTITY", fl[2], aFieldsY(), fl[3], qtyBox, "1");
        inputField(g, "SECONDS", fl[4], aFieldsY(), fl[5], secBox, "30");

        // ---- timer color: swatches, picker, hex ----
        caps(g, "TIMER COLOR", lx, aColorY(), DIM);
        for (int i = 0; i < colorBtns.size(); i++) {
            Button b = colorBtns.get(i);
            int x = b.getX(), y = b.getY();
            if (TIMER_COLORS[i] == acc) round(g, x - 1, y - 1, 14, 14, 7, TEXT);
            round(g, x, y, 12, 12, 6, TIMER_COLORS[i]);
        }
        int[] pk = aPicker();
        int step = 4;
        for (int px = 0; px < pk[2]; px += step) {
            for (int py = 0; py < pk[3]; py += 3) {
                float s1 = px / (float) Math.max(1, pk[2] - 1), v1 = 1f - py / (float) Math.max(1, pk[3] - 1);
                g.fill(pk[0] + px, pk[1] + py, pk[0] + Math.min(pk[2], px + step), pk[1] + Math.min(pk[3], py + 3), hsv(hue, s1, v1));
            }
        }
        int kx = pk[0] + Math.round(sat * (pk[2] - 1)), ky = pk[1] + Math.round((1f - val) * (pk[3] - 1));
        ring(g, kx, ky, 3, 1, 1f, 0xFFFFFFFF, 0xFFFFFFFF);
        for (int py = 0; py < pk[3]; py += 2) g.fill(pk[4], pk[1] + py, pk[4] + 8, pk[1] + Math.min(pk[3], py + 2), hsv(py / (float) pk[3] * 360f, 1f, 1f));
        int hy = pk[1] + Math.round(hue / 360f * (pk[3] - 1));
        g.fill(pk[4] - 1, hy - 1, pk[4] + 9, hy + 1, 0xFFFFFFFF);
        round(g, pk[5], pk[1], pk[6], pk[3], 4, acc);
        String hex = Settings.hex(acc & 0xFFFFFF);
        text(g, hex, pk[5] + (pk[6] - w(hex, 0.62f)) / 2, pk[1] + (pk[3] - 7) / 2, onAccentFor(acc), 0.62f, true);

        // ---- auction switch ----
        {
            Button b = switchBtn;
            int x = b.getX(), y = b.getY(), bw = b.getWidth(), bh = b.getHeight();
            box(g, x, y, bw, bh, over(b, mx, my) ? 0xFF181526 : PANEL, BORDER);
            text(g, "Auction switch", x + 10, y + 5, TEXT, 0.76f, true);
            text(g, "On: while an auction runs, money paid to you counts as bids, not bets.", x + 10, y + 16, DIM, 0.6f, false);
            int sw = 24, sh = 12, sx = x + bw - sw - 10, sy = y + (bh - sh) / 2;
            round(g, sx, sy, sw, sh, 6, Auction.switchOn ? PURPLE : 0xFF2A2640);
            round(g, Auction.switchOn ? sx + sw - 10 : sx + 2, sy + 2, 8, 8, 4, Auction.switchOn ? FRAME : 0xFF6B6684);
        }

        // ---- top bidders (left) + last auctions (right) ----
        int ly = aLastY(), lh = contentBottom() - ly;
        if (lh > 24) {
            int half = (lw - 6) / 2, bx2 = lx + half + 6;
            box(g, lx, ly, half, lh, PANEL, BORDER);
            caps(g, "TOP BIDDERS", lx + 10, ly + 8, TEXT);
            List<java.util.Map.Entry<String, Long>> bids = Auction.sortedBids();
            int rowH = 15, ry = ly + 22, rows = Math.max(0, (ly + lh - 4 - ry) / rowH);
            for (int i = 0; i < Math.min(rows, bids.size()); i++) {
                var e = bids.get(i);
                int ty = ry + i * rowH;
                if (i == 0) round(g, lx + 4, ty - 2, half - 8, rowH - 1, 3, alpha(acc, 40));   // the leader
                face(g, e.getKey(), lx + 8, ty, 10);
                String n = e.getKey();
                while (n.length() > 3 && wb(n, 0.62f) > half - 80) n = n.substring(0, n.length() - 2) + ".";
                text(g, n, lx + 22, ty + 1, TEXT, 0.62f, true);
                String amt = Money.format(e.getValue());
                text(g, amt, lx + half - 8 - wb(amt, 0.62f), ty + 1, GREEN, 0.62f, true);
            }
            if (bids.isEmpty()) text(g, Auction.running ? "Waiting for bids..." : "No bids yet", lx + 10, ry, DIM, 0.62f, false);

            box(g, bx2, ly, lw - half - 6, lh, PANEL, BORDER);
            caps(g, "LAST AUCTIONS", bx2 + 10, ly + 8, TEXT);
            int lw2 = lw - half - 6;
            for (int i = 0; i < Math.min(rows, Auction.history.size()); i++) {
                Auction.Past a = Auction.history.get(i);
                int ty = ry + i * rowH;
                String item = (a.qty() > 1 ? a.qty() + "x " : "") + a.name();
                String who = a.winner() == null ? "no bids" : a.winner();
                String line = item + " > " + who;
                while (line.length() > 4 && w(line, 0.58f) > lw2 - 70) line = line.substring(0, line.length() - 2) + ".";
                text(g, line, bx2 + 10, ty + 1, MUTED, 0.58f, false);
                String amt = a.winner() == null ? "-" : Money.format(a.amount());
                text(g, amt, bx2 + lw2 - 8 - wb(amt, 0.6f), ty + 1, a.winner() == null ? DIM : GREEN, 0.6f, true);
            }
            if (Auction.history.isEmpty()) text(g, "Finished auctions show up here", bx2 + 10, ry, DIM, 0.6f, false);
        }

        // ---- pick an item ----
        int rx = fx + 10 + lw + 8, rw = fx + fw - 10 - rx, rpH = contentBottom() - top;
        box(g, rx, top, rw, rpH, PANEL, BORDER);
        caps(g, "PICK AN ITEM", rx + 10, top + 9, TEXT);
        if (!Auction.item.isEmpty()) text(g, Auction.itemName, rx + rw - 10 - wb(Auction.itemName, 0.7f), top + 9, acc, 0.7f, true);
        searchField(g, rx + 10, top + 24, rw - 20, 18, itemSearch, "Search every item and block...");

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
                if (sel) box(g, x, y, CELL - 2, CELL - 2, alpha(acc, 60), acc);
                else if (hot) round(g, x, y, CELL - 2, CELL - 2, 3, 0xFF1E1A2E);
                itemIcon(g, st, x + 2, y + 2, 1.25f);
            }
        }
        scrollbar(g, gr[0] + gr[2] + 4, gr[1], gr[3], rows, totalRows);
        text(g, items.size() + " items", rx + 10, contentBottom() - 12, DIM, 0.64f, false);
    }

    private static int onAccentFor(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 170 ? 0xFF111111 : 0xFFFFFFFF;
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
            round(g, sx, sy, sw, sh, sh / 2, on[i] ? PURPLE : 0xFF2A2640);         // rounded pill track
            int kd = sh - 4, kx = on[i] ? sx + sw - kd - 2 : sx + 2;
            round(g, kx, sy + 2, kd, kd, kd / 2, on[i] ? FRAME : 0xFF6B6684);       // round knob, no white
            String st = on[i] ? "ON" : "OFF";
            text(g, st, sx - 8 - wb(st, 0.75f), sy + 3, on[i] ? PURPLE_LIGHT : DIM, 0.75f, true);
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
