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
    public boolean skinFaces = true;
    public String displayName = "";   // name shown in "Welcome back, ..." (empty = your username)   // false = purple letter circles like the reference picture

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

    // General: menu background
    public int themeIndex = 0;            // see THEMES
    public boolean bgGradient = true;
    public int bgTop = 0x000000, bgBottom = 0x000000;
    public int bgOpacity = 95, bgBrightness = 0, dimGame = 55;

    // Chat: tax
    public boolean countTax = false, colorTax = true;
    public int taxColor = 0x9E9E9E;
    public float taxPct = 2.0f;

    // Chat filter
    public boolean filterOn = false, filterReceived = true, filterSent = true;
    public long filterUnder = 1_000_000L;

    // Win streak
    public boolean streakShow = true, streakSeparate = true, streakCommands = true, streakAccent = false, streakBorder = true;
    public float streakSize = 0.9f;
    public int streakOpacity = 60;
    public int wins = 0, losses = 0, streak = 0;   // streak: + = wins in a row, - = losses in a row
    public int streakX = 8, streakY = 60;

    // Popup style
    public int popupAnim = 0;                      // 0 Pop, 1 Slide, 2 Fade
    public int popupOpacity = 92, popupBorderW = 1, popupGlowPct = 100, popupSparkleCount = 8;
    public boolean popupPlus = true, popupCountUp = true, popupFace = true, popupFaceBounce = true, popupTimerBar = true;
    public int popupTimerColor = 0xFFD700;

    // Move HUDs (offsets from each box's normal spot)
    public int hudOffX = 0, hudOffY = 0, auctionOffX = 0, auctionOffY = 0;

    public static final String[] THEMES = {"Pure black", "Charcoal", "Midnight", "Navy", "Plum", "Custom"};
    public static final int[][] THEME_COLORS = {
            {0x000000, 0x000000}, {0x1C1C1C, 0x111111}, {0x0D0B15, 0x050409},
            {0x0B1220, 0x060A12}, {0x1E1028, 0x0C0612}};

    public static void applyTheme(int i) {
        S.themeIndex = Math.floorMod(i, THEMES.length);
        if (S.themeIndex < THEME_COLORS.length) {
            S.bgTop = THEME_COLORS[S.themeIndex][0];
            S.bgBottom = THEME_COLORS[S.themeIndex][1];
        }
    }

    public static final long[] SMALL_STEPS = {
            1L, 1_000L, 10_000L, 100_000L, 1_000_000L, 10_000_000L, 50_000_000L, 100_000_000L, 1_000_000_000L
    };

    public static long stepSmall(long current, int dir) {
        int i = 0;
        for (int k = 0; k < SMALL_STEPS.length; k++) if (SMALL_STEPS[k] <= current) i = k;
        return SMALL_STEPS[Math.max(0, Math.min(SMALL_STEPS.length - 1, i + dir))];
    }

    // Mod theme (whole mod): see MOD_THEMES
    public int modTheme = 0;

    // Text overlay (your own lines on screen, e.g. bet limits)
    public boolean overlayOn = false, overlayRainbow = false, overlayShadow = true;
    public String overlayLine1 = "MIN $100M  MAX $2B", overlayLine2 = "TIE = PUSH";
    public float overlaySize = 1.5f;
    public int overlayColor = 0xFFFFFF;
    public int overlayX = 0, overlayY = 4;

    /** Themes: name, accent color, background tint. The rest of each palette is worked out from these two. */
    public static final String[] MOD_THEME_NAMES = {
            "Purple", "Gray", "Mint", "Sunset", "Forest", "Ocean", "Sunrise", "Twilight", "Nebula",
            "Cosmic", "Meadow", "Blossom", "Storm", "Autumn", "Moonlight", "Coral Reef", "Tundra", "Rainforest",
            "Starry Night", "Crimson", "Gold", "Pink", "Lavender", "Cherry", "Lime", "Teal", "Sky",
            "Peach", "Ruby", "Emerald", "Sapphire", "Amber", "Ice", "Midnight", "Neon", "Mono"};
    private static final int[] THEME_ACCENT = {
            0x8B5CF6, 0x9CA3AF, 0x34D399, 0xF97350, 0x22C55E, 0x0EA5E9, 0xFB923C, 0xA855F7, 0x7C3AED,
            0x6366F1, 0xA3E635, 0xF9A8D4, 0x94A3B8, 0xEA580C, 0x818CF8, 0xF87171, 0xCBD5E1, 0x16A34A,
            0x3B82F6, 0xE11D48, 0xF59E0B, 0xEC4899, 0xC4B5FD, 0xF43F5E, 0x84CC16, 0x14B8A6, 0x38BDF8,
            0xFDBA74, 0xDC2626, 0x10B981, 0x2563EB, 0xF59E0B, 0x7DD3FC, 0x4F46E5, 0x22D3EE, 0xD4D4D4};
    private static final int[] THEME_TINT = {
            0x1A1033, 0x1F2023, 0x0E2A22, 0x2E1410, 0x0E2214, 0x0A1E30, 0x2E1A0C, 0x241035, 0x1C0B33,
            0x0E0C2A, 0x1C260C, 0x2E1424, 0x1A1E26, 0x2A140A, 0x12142E, 0x2E1214, 0x1E2228, 0x0C2414,
            0x0A1030, 0x2A0A10, 0x2A200A, 0x2E0E22, 0x221A33, 0x2E0C14, 0x1A260A, 0x0A2624, 0x0A1E2E,
            0x2E1E12, 0x2A0A0A, 0x0A2A1E, 0x0A1430, 0x2A1E0A, 0x0E222E, 0x0C0A26, 0x082428, 0x161616};

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** background, frame, panel, border, accent, accent light for theme i */
    public static int[] themeColors(int i) {
        i = Math.floorMod(i, MOD_THEME_NAMES.length);
        int tint = THEME_TINT[i], acc = THEME_ACCENT[i];
        return new int[]{mix(tint, 0x000000, 0.80f), mix(tint, 0x000000, 0.62f), mix(tint, 0x000000, 0.50f),
                mix(tint, 0xFFFFFF, 0.06f), acc, mix(acc, 0xFFFFFF, 0.40f)};
    }

    public static int themeSwatch(int i) { return THEME_ACCENT[Math.floorMod(i, THEME_ACCENT.length)]; }

    /** Pick a mod theme and carry it into the rest of the mod (menu, HUD, popup, auctions). */
    public static void applyModTheme(int i) {
        S.modTheme = Math.floorMod(i, MOD_THEME_NAMES.length);
        int[] t = themeColors(S.modTheme);
        S.bgTop = t[1];
        S.bgBottom = t[0];
        S.themeIndex = THEMES.length - 1;   // menu background follows the theme
        S.accent = t[4];
        S.hudAccentColor = t[4];
        S.popupBorder = t[4];
        Auction.color = 0xFF000000 | t[4];
    }

    // Rakeback (Rakeback tab): give back this % of what a player lost, once they've lost at least rakeMin
    public int rakePct = 10;
    public long rakeMin = 1_000_000_000L;

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
