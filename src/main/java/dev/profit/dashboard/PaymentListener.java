package dev.profit.dashboard;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads server payment messages and logs them. Turn on /bettors debug to see the raw lines. */
public final class PaymentListener {
    private PaymentListener() {}

    public static boolean debug = false;

    // Anchored to the start of the line so a player typing "X paid you $5B" in chat can't fake a payment.
    private static final String PRE = "^[^A-Za-z0-9_.]*";
    private static final String NAME = "([.A-Za-z0-9_]{2,20})";
    private static final String AMT = "\\$?\\s?([\\d,]+(?:\\.\\d+)?\\s?[KkMmBbTt]?)";

    private static final Pattern IN = Pattern.compile(PRE + NAME + " paid you " + AMT, Pattern.CASE_INSENSITIVE);
    private static final Pattern IN2 = Pattern.compile(PRE + "You received " + AMT + " from " + NAME, Pattern.CASE_INSENSITIVE);
    private static final Pattern OUT = Pattern.compile(PRE + "You paid " + NAME + " " + AMT, Pattern.CASE_INSENSITIVE);
    private static final Pattern OUT2 = Pattern.compile(PRE + "You sent " + AMT + " to " + NAME, Pattern.CASE_INSENSITIVE);

    public static void handle(String text) {
        if (debug && (text.contains("$") || text.toLowerCase(Locale.ROOT).contains("paid"))) {
            chat("debug raw: " + text, ChatFormatting.GRAY);
        }
        Matcher m;
        if ((m = OUT.matcher(text)).find()) { log(m.group(1), m.group(2), false); return; }
        if ((m = OUT2.matcher(text)).find()) { log(m.group(2), m.group(1), false); return; }
        if ((m = IN.matcher(text)).find()) { log(m.group(1), m.group(2), true); return; }
        if ((m = IN2.matcher(text)).find()) { log(m.group(2), m.group(1), true); }
    }

    private static void log(String player, String amount, boolean incoming) {
        try {
            long amt = Money.parse(amount);
            if (incoming) PaymentLedger.record(player, amt, 0);
            else PaymentLedger.record(player, 0, amt);
            if (debug) chat("logged " + (incoming ? player + " paid you " : "you paid " + player + " ") + Money.format(amt), ChatFormatting.GREEN);
        } catch (Exception e) {
            if (debug) chat("couldn't read amount '" + amount + "'", ChatFormatting.RED);
        }
    }

    public static void announceEnd(PaymentLedger.SessionSummary s) {
        long p = s.profit();
        chat("Session ended after " + PaymentLedger.formatDuration(s.lengthMs())
                + " | received " + Money.format(s.received())
                + " | sent " + Money.format(s.sent())
                + " | you " + (p >= 0 ? "+" : "") + Money.format(p)
                + " | " + s.players() + " players. New session started.", ChatFormatting.GOLD);
    }

    public static void chat(String msg, ChatFormatting color) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("[Bettors] " + msg).withStyle(color));    }}
