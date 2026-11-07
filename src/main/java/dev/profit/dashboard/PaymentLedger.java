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

        /** Positive = player is UP on you, negative = player is DOWN (you won). */
        public long net() { return youPaid - paidYou; }
        public long volume() { return paidYou + youPaid; }
    }

    private static final class Data {
        Map<String, Entry> allTime = new LinkedHashMap<>();
        Map<String, Entry> session = new LinkedHashMap<>();
        long sessionStart = System.currentTimeMillis();
        long lastSessionLength = -1;
        long lastSessionProfit = 0;
    }

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

    /** Logs a payment to both the all-time ledger and the current session. */
    public static void record(String player, long paidYou, long youPaid) {
        add(data.allTime, player, paidYou, youPaid);
        add(data.session, player, paidYou, youPaid);
        save();
    }

    /** Adds old history to the all-time ledger only (doesn't count toward the session). */
    public static void importAllTime(String player, long paidYou, long youPaid) {
        add(data.allTime, player, paidYou, youPaid);
        save();
    }

    private static void add(Map<String, Entry> map, String player, long paidYou, long youPaid) {
        Entry e = map.computeIfAbsent(player.toLowerCase(Locale.ROOT), k -> new Entry());
        e.name = player;
        e.paidYou += paidYou;
        e.youPaid += youPaid;
    }

    public static List<Entry> entries(boolean session) {
        return new ArrayList<>((session ? data.session : data.allTime).values());
    }

    public static long sessionMillis() { return System.currentTimeMillis() - data.sessionStart; }
    public static long lastSessionLength() { return data.lastSessionLength; }
    public static long lastSessionProfit() { return data.lastSessionProfit; }

    /** Ends the current session, keeps a short summary, and starts a fresh timer. */
    public static SessionSummary endSession() {
        long in = 0, out = 0;
        for (Entry e : data.session.values()) { in += e.paidYou; out += e.youPaid; }
        SessionSummary s = new SessionSummary(sessionMillis(), in, out, data.session.size());
        data.lastSessionLength = s.lengthMs();
        data.lastSessionProfit = s.profit();
        data.session = new LinkedHashMap<>();
        data.sessionStart = System.currentTimeMillis();
        save();
        return s;
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
