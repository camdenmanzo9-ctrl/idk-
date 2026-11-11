package dev.profit.dashboard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Stores all-time and current-session totals per player in config/bettors-dashboard.json. */
public final class PaymentLedger {
    private PaymentLedger() {}

    public static final class Entry {
        public String name;
        public long paidYou;  // money this player sent you
        public long youPaid;  // money you sent this player
        public int payments;  // how many payments went back and forth

        /** Positive = player is UP on you, negative = player is DOWN (you won). */
        public long net() { return youPaid - paidYou; }
        public long volume() { return paidYou + youPaid; }
    }

    /** One payment with its time, kept for about a month for the player details page. */
    public static final class Payment {
        public String name;
        public long paidYou;
        public long youPaid;
        public long ts;
    }

    /** One finished session, kept for the Sessions tab. */
    public static final class SessionRecord {
        public long start;
        public long end;
        public long received;
        public long sent;
        public int players;
        public int payments;
        public long profit() { return received - sent; }
        public long length() { return end - start; }
    }

    private static final class Data {
        Map<String, Entry> allTime = new LinkedHashMap<>();
        Map<String, Entry> session = new LinkedHashMap<>();
        long sessionStart = System.currentTimeMillis();
        long sessionEnd = 0;
        boolean sessionActive = true;
        long lastSessionLength = -1;
        long lastSessionProfit = 0;
        List<SessionRecord> history = new ArrayList<>();
        String theme = "classic";
        List<Payment> log = new ArrayList<>();
    }

    private static final long KEEP_MS = 35L * 24 * 60 * 60 * 1000;   // ~a month

    public record SessionSummary(long lengthMs, long received, long sent, int players) {
        public long profit() { return received - sent; }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("bettors-dashboard.json");
    private static Data data = new Data();

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Data d = GSON.fromJson(Files.readString(FILE), Data.class);
                if (d != null) data = d;
            }
        } catch (Exception e) {
            System.err.println("[Bettors] Could not read " + FILE + ": " + e);
        }
        if (data.allTime == null) data.allTime = new LinkedHashMap<>();
        if (data.session == null) data.session = new LinkedHashMap<>();
        if (data.history == null) data.history = new ArrayList<>();
        if (data.log == null) data.log = new ArrayList<>();
        if (data.sessionStart <= 0) data.sessionStart = System.currentTimeMillis();
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(data));
        } catch (Exception e) {
            System.err.println("[Bettors] Could not save " + FILE + ": " + e);
        }
    }

    /** Logs a payment to the all-time ledger, and to the session if one is running. */
    public static void record(String player, long paidYou, long youPaid) {
        add(data.allTime, player, paidYou, youPaid, true);
        if (data.sessionActive) add(data.session, player, paidYou, youPaid, true);

        Payment pay = new Payment();
        pay.name = player;
        pay.paidYou = paidYou;
        pay.youPaid = youPaid;
        pay.ts = System.currentTimeMillis();
        data.log.add(pay);
        long cutoff = pay.ts - KEEP_MS;
        data.log.removeIf(x -> x.ts < cutoff);
        while (data.log.size() > 20000) data.log.remove(0);
        save();
    }

    /** All payments since a time, oldest first. */
    public static List<Payment> paymentsSince(long since) {
        List<Payment> out = new ArrayList<>();
        for (Payment x : data.log) if (x.ts >= since) out.add(x);
        return out;
    }

    /** Payments with one player since a time, newest first. */
    public static List<Payment> paymentsFor(String player, long since) {
        List<Payment> out = new ArrayList<>();
        for (int i = data.log.size() - 1; i >= 0; i--) {
            Payment x = data.log.get(i);
            if (x.ts >= since && x.name.equalsIgnoreCase(player)) out.add(x);
        }
        return out;
    }

    /** All-time totals for one player (includes imported history). */
    public static Entry allTimeFor(String player) {
        return data.allTime.get(player.toLowerCase(Locale.ROOT));
    }

    /** Adds old history to the all-time ledger only (doesn't count toward the session). */
    public static void importAllTime(String player, long paidYou, long youPaid) {
        add(data.allTime, player, paidYou, youPaid, false);
        save();
    }

    private static void add(Map<String, Entry> map, String player, long paidYou, long youPaid, boolean isPayment) {
        Entry e = map.computeIfAbsent(player.toLowerCase(Locale.ROOT), k -> new Entry());
        e.name = player;
        e.paidYou += paidYou;
        e.youPaid += youPaid;
        if (isPayment) e.payments++;
    }

    public static List<Entry> entries(boolean session) {
        return new ArrayList<>((session ? data.session : data.allTime).values());
    }

    public static boolean isSessionActive() { return data.sessionActive; }
    public static long sessionStartTime() { return data.sessionStart; }
    public static boolean isChristmas() { return "christmas".equals(data.theme); }

    public static void setChristmas(boolean on) {
        data.theme = on ? "christmas" : "classic";
        save();
    }

    public static List<SessionRecord> history() { return new ArrayList<>(data.history); }

    public static void deleteSession(int index) {
        if (index >= 0 && index < data.history.size()) {
            data.history.remove(index);
            save();
        }
    }

    public static void clearHistory() {
        data.history.clear();
        data.lastSessionLength = -1;
        data.lastSessionProfit = 0;
        save();
    }

    /** Running time while active; frozen at the final length once ended. */
    public static long sessionMillis() {
        return (data.sessionActive ? System.currentTimeMillis() : data.sessionEnd) - data.sessionStart;
    }
    public static long lastSessionLength() { return data.lastSessionLength; }
    public static long lastSessionProfit() { return data.lastSessionProfit; }

    /** Stops the session: freezes the timer and keeps its numbers visible until a new one starts. */
    public static SessionSummary endSession() {
        long in = 0, out = 0;
        for (Entry e : data.session.values()) { in += e.paidYou; out += e.youPaid; }
        data.sessionEnd = System.currentTimeMillis();
        data.sessionActive = false;
        SessionSummary s = new SessionSummary(sessionMillis(), in, out, data.session.size());
        data.lastSessionLength = s.lengthMs();
        data.lastSessionProfit = s.profit();

        SessionRecord r = new SessionRecord();
        r.start = data.sessionStart;
        r.end = data.sessionEnd;
        r.received = in;
        r.sent = out;
        r.players = s.players();
        int count = 0;
        for (Entry e : data.session.values()) count += e.payments;
        r.payments = count;
        data.history.add(0, r);                       // newest first
        while (data.history.size() > 100) data.history.remove(data.history.size() - 1);

        save();
        return s;
    }

    /** Starts a fresh session with an empty ledger and a new timer. */
    public static void startSession() {
        data.session = new LinkedHashMap<>();
        data.sessionStart = System.currentTimeMillis();
        data.sessionEnd = 0;
        data.sessionActive = true;
        save();
    }

    public static String formatDuration(long ms) {
        long sec = Math.max(0, ms / 1000);
        long h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
        return h > 0 ? String.format("%dh %02dm %02ds", h, m, s) : String.format("%dm %02ds", m, s);
    }

    public static String formatDurationShort(long ms) {
        long min = Math.max(0, ms / 60000);
        return min >= 60 ? (min / 60) + "h " + (min % 60) + "m" : min + "m";
    }
}
