package dev.profit.dashboard;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Auctions: pick an item, start a timer, the biggest single payment before it hits 0 wins. */
public final class Auction {
    private Auction() {}

    public static boolean switchOn = true;            // payments during an auction count as bids, not bets
    public static boolean running = false;
    public static long endAt = 0, durationMs = 15_000;
    public static ItemStack item = ItemStack.EMPTY;
    public static String itemName = "";
    public static final Map<String, Long> bids = new LinkedHashMap<>();
    public static String winner = null;
    public static long winAmount = 0;
    public static boolean ended = false;
    public static int color = 0xFF8B5CF6;

    public static boolean capturing() { return running && switchOn; }

    public static void start() {
        if (item.isEmpty()) return;
        bids.clear();
        winner = null;
        winAmount = 0;
        ended = false;
        running = true;
        endAt = System.currentTimeMillis() + durationMs;
    }

    public static void clear() {
        running = false;
        ended = false;
        bids.clear();
        winner = null;
        winAmount = 0;
    }

    public static void bid(String name, long amount) {
        String key = name;
        for (String k : bids.keySet()) if (k.equalsIgnoreCase(name)) { key = k; break; }
        bids.merge(key, amount, Math::max);   // bids don't stack: a player's biggest single payment is their bid
    }

    public static long left() { return Math.max(0, endAt - System.currentTimeMillis()); }

    /** Called every tick: ends the auction when the timer runs out. */
    public static void tick() {
        if (!running || System.currentTimeMillis() < endAt) return;
        running = false;
        ended = true;
        winner = null;
        winAmount = 0;
        for (Map.Entry<String, Long> e : bids.entrySet())
            if (e.getValue() > winAmount) { winner = e.getKey(); winAmount = e.getValue(); }
    }

    /** Bids, biggest first. */
    public static List<Map.Entry<String, Long>> sortedBids() {
        List<Map.Entry<String, Long>> l = new ArrayList<>(bids.entrySet());
        l.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return l;
    }
}
