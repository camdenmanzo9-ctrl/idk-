package dev.profit.dashboard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** All the options from the Settings screen, saved in config/bettors-settings.json. */
public final class Settings {
    // General
    public int accent = 0x7CFF3A;

    // Chat
    public boolean chatColors = true, boldNames = false, boldAmounts = false;
    public int nameColor = 0x8EC5FF, receivedColor = 0x4DA3FF, sentColor = 0xFF4040;

    // Profit HUD
    public boolean hudShow = true, hudBorder = true, hudAccentBar = true, hudShowPaused = true;
    public float hudSize = 1.0f;
    public int hudOpacity = 75, hudCorner = 3;   // 0 top left, 1 top right, 2 bottom left, 3 bottom right
    public int hudTitle = 0x8EC5FF, hudGain = 0x4ADE80, hudLoss = 0xFF4040, hudZero = 0xFFFFFF,
            hudBg = 0x0B1220, hudAccentColor = 0x4DA3FF;

    // Popup alert
    public boolean popupShow = true, popupRainbow = false, popupGlow = true, popupSparkles = true, popupShine = true;
    public long popupMin = 1_000_000_000L;
    public float popupDuration = 2.0f, popupSize = 1.0f;
    public int popupAmount = 0x4DA3FF, popupName = 0x8EC5FF, popupBorder = 0x4DA3FF, popupBg = 0x0B1428;

    // Sounds
    public boolean soundPopup = true, soundWhenQueued = true, soundConfirm = true;
    public int soundIndex = 0, confirmSoundIndex = 3, soundVolume = 60, soundRepeat = 1;
    public float soundPitch = 1.0f;

    // Pay confirm
    public boolean payAsk = true, payShowTax = true, payRainbow = false;
    public long payFrom = 1_000_000_000L;
    public float payDelay = 0.5f, payTax = 2.0f;
    public int payDim = 55;
    public int payAccent = 0x4DA3FF, payName = 0x8EC5FF, payAmount = 0xFF5555,
            payConfirm = 0x2F7BFF, payCancel = 0xE74C3C, payBg = 0x0C1424;

    // Bet board (the games / limits list on your screen)
    public boolean boardShow = false;   // in-game board removed from the menu; kept off
    public int boardPos = 4;          // 0 TL, 1 TR, 2 BL, 3 BR, 4 left middle, 5 right middle
    public float boardSize = 1.0f;
    public int boardOpacity = 60;
    public String boardIgn = "2amsnowy";
    public String boardMax = "2 BILLION MAX";
    public List<BoardTier> boardTiers = defaultTiers();

    public static class BoardGame {
        public String icon, name, note;
        public BoardGame() {}
        public BoardGame(String icon, String name, String note) { this.icon = icon; this.name = name; this.note = note; }
    }

    public static class BoardTier {
        public String min;
        public List<BoardGame> games = new ArrayList<>();
        public BoardTier() {}
        public BoardTier(String min, BoardGame... games) { this.min = min; this.games = new ArrayList<>(List.of(games)); }
    }

    public static List<BoardTier> defaultTiers() {
        List<BoardTier> t = new ArrayList<>();
        t.add(new BoardTier("150M",
                new BoardGame("cards", "Blackjack Multihand", "Per Hand")));
        t.add(new BoardTier("100M",
                new BoardGame("cards", "Blackjack", "Tie = DD"),
                new BoardGame("roulette", "Russian Roulette", ""),
                new BoardGame("cards", "Baccarat", "Tie = Loss")));
        t.add(new BoardTier("50M",
                new BoardGame("paper", "Paper Game", "Tie = Loss"),
                new BoardGame("67", "Odd or Even", "Creeper = Loss"),
                new BoardGame("wheel", "45/45/10", ""),
                new BoardGame("cards", "MexicanJack", "Tie = DD"),
                new BoardGame("arrows", "Higher & Lower", "Tie = Loss"),
                new BoardGame("horse", "Horse game", "x2.5")));
        return t;
    }

    // ---------------------------------------------------------------------------------

    public static Settings S = new Settings();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("bettors-settings.json");

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Settings s = GSON.fromJson(Files.readString(FILE), Settings.class);
                if (s != null) S = s;
                // If up / down / even are all one color you can't tell them apart: put them back
                if (S.hudGain == S.hudLoss || S.hudGain == S.hudZero || S.hudLoss == S.hudZero) {
                    S.hudGain = 0x4ADE80;
                    S.hudLoss = 0xFF4040;
                    S.hudZero = 0xFFFFFF;
                    save();
                }
            }
        } catch (Exception e) {
            System.err.println("[Bettors] Could not read " + FILE + ": " + e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(S));
        } catch (Exception e) {
            System.err.println("[Bettors] Could not save " + FILE + ": " + e);
        }
    }

    public static void resetAll() {
        S = new Settings();
        save();
    }

    // Choices the < > pickers step through
    public static final long[] MONEY_STEPS = {
            1_000_000L, 10_000_000L, 50_000_000L, 100_000_000L, 250_000_000L, 500_000_000L,
            1_000_000_000L, 5_000_000_000L, 10_000_000_000L, 50_000_000_000L, 100_000_000_000L
    };

    public static long stepMoney(long current, int dir) {
        int i = 0;
        for (int k = 0; k < MONEY_STEPS.length; k++) if (MONEY_STEPS[k] <= current) i = k;
        i = Math.max(0, Math.min(MONEY_STEPS.length - 1, i + dir));
        return MONEY_STEPS[i];
    }

    // Colors a swatch cycles through when clicked
    public static final int[] PRESETS = {
            0x4ADE80, 0x4DA3FF, 0x2F7BFF, 0x8EC5FF, 0x3ADFFF, 0x7CFF3A, 0x2ECC71, 0xB57CFF, 0xFF5CA8,
            0xFF4040, 0xE74C3C, 0xFF5555, 0xFFA500, 0xFFAA00, 0xFFC400, 0xFFD700, 0xFFFFFF, 0xAAAAAA
    };
    public static final int[] BG_PRESETS = {0x0B1220, 0x0B1428, 0x0C1424, 0x101820, 0x0B0B14, 0x1A1A1A, 0x000000};

    public static int nextColor(int current, boolean background) {
        int[] list = background ? BG_PRESETS : PRESETS;
        for (int i = 0; i < list.length; i++) if (list[i] == current) return list[(i + 1) % list.length];
        return list[0];
    }

    public static String hex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }
}
