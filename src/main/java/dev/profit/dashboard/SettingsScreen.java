package dev.profit.dashboard;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Dashboard Mod settings: General, Chat, Profit HUD, Popup alert, Sounds, Pay confirm. */
public class SettingsScreen extends Screen {
    private enum Section {
        GENERAL("General", "Theme and general options"),
        CHAT("Chat", "How payment messages look in chat"),
        FILTER("Chat filter", "Hide small payments in chat"),
        HUD("Profit HUD", "The profit of the day box"),
        STREAK("Win streak", "Win streak counter (/win, /lose or keybinds)"),
        POPUP("Popup alert", "The alert for big payments you receive"),
        STYLE("Popup style", "Animations and looks of the popup alert"),
        OVERLAY("Text overlay", "Your own text on screen (and on stream)"),
        SOUNDS("Sounds", "Sound effects for alerts and confirmations"),
        PAY("Pay confirm", "The screen that asks before a big /pay (you press Enter)");

        final String label, about;
        Section(String label, String about) { this.label = label; this.about = about; }

        int color() {
            return switch (this) {
                case GENERAL -> 0xFF5A5E68;
                case CHAT -> 0xFF3F8A2C;
                case FILTER -> 0xFF2D6290;
                case HUD -> 0xFF8F7A1C;
                case STREAK -> 0xFF9A5418;
                case POPUP -> 0xFF8E2F7E;
                case STYLE -> 0xFF8E4C80;
                case OVERLAY -> 0xFF5A4A9A;
                case SOUNDS -> 0xFF1F7A80;
                case PAY -> 0xFF8A2E2E;
            };
        }
    }

    private enum Kind { TOGGLE, STEPPER, PICKER, COLOR, ACTION, TEXT }

    /** One row in the options grid. */
    private static final class Row {
        Kind kind;
        String label;
        BooleanSupplier on; Consumer<Boolean> setOn;               // toggle
        Supplier<String> value; Runnable minus, plus;              // stepper / picker
        IntSupplier color; IntConsumer setColor; boolean bg;       // color
        Runnable action;                                           // action
        Supplier<String> textGet; Consumer<String> textSet;        // text
        EditBox box;
        int x, y, w;
        Button b1, b2;
    }

    private static Section section = Section.GENERAL;
    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private final List<Button> sideButtons = new ArrayList<>();
    private final List<Section> sideSections = new ArrayList<>();
    private Button doneButton;
    private int wx, wy, ww, wh;

    public SettingsScreen(Screen parent) {
        super(Component.literal("Dashboard Mod"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        Settings.save();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    // ---------------- colors ----------------
    private static int accent() { return 0xFF000000 | (Settings.S.accent & 0xFFFFFF); }
    private static int mix(int rgb, float t) {   // darken toward black
        int r = (int) (((rgb >> 16) & 0xFF) * t), g = (int) (((rgb >> 8) & 0xFF) * t), b = (int) ((rgb & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
    private static final int WIN = 0xF20C1422, HEAD = 0xFF111C2E, ROW = 0xFF16233A, TEXT = 0xFFE8F0FF,
            MUTED = 0xFF8496B0, VALUE = 0xFF8EC5FF, BOX = 0xFF050A14, KNOB = 0xFFFFFFFF, OFF = 0xFF2A3A55;

    // ---------------- building rows ----------------
    private Row toggle(String label, BooleanSupplier on, Consumer<Boolean> set) {
        Row r = new Row(); r.kind = Kind.TOGGLE; r.label = label; r.on = on; r.setOn = set; rows.add(r); return r;
    }
    private Row stepper(String label, Supplier<String> value, Runnable minus, Runnable plus) {
        Row r = new Row(); r.kind = Kind.STEPPER; r.label = label; r.value = value; r.minus = minus; r.plus = plus; rows.add(r); return r;
    }
    private Row picker(String label, Supplier<String> value, Runnable prev, Runnable next) {
        Row r = new Row(); r.kind = Kind.PICKER; r.label = label; r.value = value; r.minus = prev; r.plus = next; rows.add(r); return r;
    }
    private Row color(String label, IntSupplier get, IntConsumer set, boolean bg) {
        Row r = new Row(); r.kind = Kind.COLOR; r.label = label; r.color = get; r.setColor = set; r.bg = bg; rows.add(r); return r;
    }
    private Row textRow(String label, Supplier<String> get, Consumer<String> set) {
        Row r = new Row(); r.kind = Kind.TEXT; r.label = label; r.textGet = get; r.textSet = set; rows.add(r); return r;
    }

    private Row action(String label, Runnable action) {
        Row r = new Row(); r.kind = Kind.ACTION; r.label = label; r.action = action; rows.add(r); return r;
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, Math.round(v * 10) / 10f)); }
    private static int clampI(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private void buildRows() {
        rows.clear();
        Settings s = Settings.S;
        switch (section) {
            case GENERAL -> {
                picker("Theme", () -> Settings.MOD_THEME_NAMES[Math.floorMod(s.modTheme, Settings.MOD_THEME_NAMES.length)],
                        () -> Settings.applyModTheme(s.modTheme - 1), () -> Settings.applyModTheme(s.modTheme + 1));
                picker("Menu", () -> Settings.THEMES[Math.floorMod(s.themeIndex, Settings.THEMES.length)],
                        () -> Settings.applyTheme(s.themeIndex - 1), () -> Settings.applyTheme(s.themeIndex + 1));
                picker("Background", () -> s.bgGradient ? "Gradient" : "Solid",
                        () -> s.bgGradient = !s.bgGradient, () -> s.bgGradient = !s.bgGradient);
                color("Top color", () -> s.bgTop, v -> { s.bgTop = v; s.themeIndex = Settings.THEMES.length - 1; }, true);
                color("Bottom color", () -> s.bgBottom, v -> { s.bgBottom = v; s.themeIndex = Settings.THEMES.length - 1; }, true);
                stepper("Opacity", () -> s.bgOpacity + "%", () -> s.bgOpacity = clampI(s.bgOpacity - 5, 30, 100),
                        () -> s.bgOpacity = clampI(s.bgOpacity + 5, 30, 100));
                stepper("Brightness", () -> (s.bgBrightness >= 0 ? "+" : "") + s.bgBrightness + "%",
                        () -> s.bgBrightness = clampI(s.bgBrightness - 5, 0, 50), () -> s.bgBrightness = clampI(s.bgBrightness + 5, 0, 50));
                stepper("Dim game", () -> s.dimGame + "%", () -> s.dimGame = clampI(s.dimGame - 5, 0, 95),
                        () -> s.dimGame = clampI(s.dimGame + 5, 0, 95));
                toggle("Skin faces", () -> s.skinFaces, v -> s.skinFaces = v);
                action("Reset all settings", () -> { Settings.resetAll(); rebuildWidgets(); });
            }
            case CHAT -> {
                toggle("Chat colors", () -> s.chatColors, v -> s.chatColors = v);
                toggle("Count tax in profit", () -> s.countTax, v -> s.countTax = v);
                color("Name", () -> s.nameColor, v -> s.nameColor = v, false);
                color("Received $", () -> s.receivedColor, v -> s.receivedColor = v, false);
                color("Sent $", () -> s.sentColor, v -> s.sentColor = v, false);
                color("Tax text", () -> s.taxColor, v -> s.taxColor = v, false);
                toggle("Color the tax", () -> s.colorTax, v -> s.colorTax = v);
                toggle("Bold names", () -> s.boldNames, v -> s.boldNames = v);
                toggle("Bold amounts", () -> s.boldAmounts, v -> s.boldAmounts = v);
                stepper("Tax %", () -> String.valueOf(s.taxPct), () -> s.taxPct = clamp(s.taxPct - 0.5f, 0f, 20f),
                        () -> s.taxPct = clamp(s.taxPct + 0.5f, 0f, 20f));
            }
            case FILTER -> {
                toggle("Hide small", () -> s.filterOn, v -> s.filterOn = v);
                picker("Hide under", () -> Money.format(s.filterUnder), () -> s.filterUnder = Settings.stepSmall(s.filterUnder, -1),
                        () -> s.filterUnder = Settings.stepSmall(s.filterUnder, 1));
                toggle("Hide received", () -> s.filterReceived, v -> s.filterReceived = v);
                toggle("Hide sent", () -> s.filterSent, v -> s.filterSent = v);
            }
            case STREAK -> {
                toggle("Show streak", () -> s.streakShow, v -> s.streakShow = v);
                toggle("Separate HUD", () -> s.streakSeparate, v -> s.streakSeparate = v);
                toggle("/win and /lose", () -> s.streakCommands, v -> s.streakCommands = v);
                stepper("Size", () -> s.streakSize + "x", () -> s.streakSize = clamp(s.streakSize - 0.1f, 0.5f, 2f),
                        () -> s.streakSize = clamp(s.streakSize + 0.1f, 0.5f, 2f));
                toggle("Accent bar", () -> s.streakAccent, v -> s.streakAccent = v);
                toggle("Border", () -> s.streakBorder, v -> s.streakBorder = v);
                stepper("Opacity", () -> s.streakOpacity + "%", () -> s.streakOpacity = clampI(s.streakOpacity - 5, 0, 100),
                        () -> s.streakOpacity = clampI(s.streakOpacity + 5, 0, 100));
                action("Reset streak", Alerts::resetStreak);
            }
            case STYLE -> {
                String[] anims = {"Pop", "Slide", "Fade"};
                picker("Animation", () -> anims[Math.floorMod(s.popupAnim, 3)], () -> s.popupAnim = Math.floorMod(s.popupAnim - 1, 3),
                        () -> s.popupAnim = Math.floorMod(s.popupAnim + 1, 3));
                stepper("Opacity", () -> s.popupOpacity + "%", () -> s.popupOpacity = clampI(s.popupOpacity - 5, 20, 100),
                        () -> s.popupOpacity = clampI(s.popupOpacity + 5, 20, 100));
                stepper("Border width", () -> s.popupBorderW + "px", () -> s.popupBorderW = clampI(s.popupBorderW - 1, 1, 4),
                        () -> s.popupBorderW = clampI(s.popupBorderW + 1, 1, 4));
                stepper("Glow", () -> s.popupGlowPct + "%", () -> s.popupGlowPct = clampI(s.popupGlowPct - 10, 0, 100),
                        () -> s.popupGlowPct = clampI(s.popupGlowPct + 10, 0, 100));
                stepper("Sparkles", () -> String.valueOf(s.popupSparkleCount), () -> s.popupSparkleCount = clampI(s.popupSparkleCount - 1, 0, 30),
                        () -> s.popupSparkleCount = clampI(s.popupSparkleCount + 1, 0, 30));
                toggle("+ before amount", () -> s.popupPlus, v -> s.popupPlus = v);
                toggle("Count-up amount", () -> s.popupCountUp, v -> s.popupCountUp = v);
                toggle("Face bounce", () -> s.popupFaceBounce, v -> s.popupFaceBounce = v);
                toggle("Timer bar", () -> s.popupTimerBar, v -> s.popupTimerBar = v);
                color("Timer color", () -> s.popupTimerColor, v -> s.popupTimerColor = v, false);
                action("Test popup (closes menu)", () -> { Alerts.testPopup(); if (minecraft != null) minecraft.setScreen(null); });
            }
            case HUD -> {
                toggle("Show HUD", () -> s.hudShow, v -> s.hudShow = v);
                toggle("Accent bar", () -> s.hudAccentBar, v -> s.hudAccentBar = v);
                toggle("Border", () -> s.hudBorder, v -> s.hudBorder = v);
                stepper("Size", () -> s.hudSize + "x", () -> s.hudSize = clamp(s.hudSize - 0.1f, 0.5f, 2f),
                        () -> s.hudSize = clamp(s.hudSize + 0.1f, 0.5f, 2f));
                stepper("Opacity", () -> s.hudOpacity + "%", () -> s.hudOpacity = clampI(s.hudOpacity - 5, 10, 100),
                        () -> s.hudOpacity = clampI(s.hudOpacity + 5, 10, 100));
                color("Title", () -> s.hudTitle, v -> s.hudTitle = v, false);
                color("Gain", () -> s.hudGain, v -> s.hudGain = v, false);
                color("Loss", () -> s.hudLoss, v -> s.hudLoss = v, false);
                color("Zero", () -> s.hudZero, v -> s.hudZero = v, false);
                color("BG", () -> s.hudBg, v -> s.hudBg = v, true);
                color("Accent", () -> s.hudAccentColor, v -> s.hudAccentColor = v, false);
                String[] corners = {"Top left", "Top right", "Bottom left", "Bottom right"};
                picker("Position", () -> corners[s.hudCorner], () -> s.hudCorner = Math.floorMod(s.hudCorner - 1, 4),
                        () -> s.hudCorner = Math.floorMod(s.hudCorner + 1, 4));
            }
            case POPUP -> {
                toggle("Show popup", () -> s.popupShow, v -> s.popupShow = v);
                toggle("Skin face", () -> s.popupFace, v -> s.popupFace = v);
                picker("Min", () -> Money.format(s.popupMin), () -> s.popupMin = Settings.stepMoney(s.popupMin, -1),
                        () -> s.popupMin = Settings.stepMoney(s.popupMin, 1));
                stepper("Duration", () -> s.popupDuration + "s", () -> s.popupDuration = clamp(s.popupDuration - 0.5f, 1f, 10f),
                        () -> s.popupDuration = clamp(s.popupDuration + 0.5f, 1f, 10f));
                stepper("Size", () -> s.popupSize + "x", () -> s.popupSize = clamp(s.popupSize - 0.1f, 0.5f, 2f),
                        () -> s.popupSize = clamp(s.popupSize + 0.1f, 0.5f, 2f));
                toggle("Rainbow border", () -> s.popupRainbow, v -> s.popupRainbow = v);
                toggle("Sparkles", () -> s.popupSparkles, v -> s.popupSparkles = v);
                toggle("Glow", () -> s.popupGlow, v -> s.popupGlow = v);
                toggle("Shine", () -> s.popupShine, v -> s.popupShine = v);
                color("Amount", () -> s.popupAmount, v -> s.popupAmount = v, false);
                color("Name", () -> s.popupName, v -> s.popupName = v, false);
                color("BG", () -> s.popupBg, v -> s.popupBg = v, true);
                color("Border", () -> s.popupBorder, v -> s.popupBorder = v, false);
                action("Test popup (closes menu)", () -> { Alerts.testPopup(); if (minecraft != null) minecraft.setScreen(null); });
            }
            case SOUNDS -> {
                toggle("Popup sound", () -> s.soundPopup, v -> s.soundPopup = v);
                picker("Sound", () -> Alerts.SOUND_NAMES[Math.floorMod(s.soundIndex, Alerts.SOUND_NAMES.length)],
                        () -> s.soundIndex = Math.floorMod(s.soundIndex - 1, Alerts.SOUND_NAMES.length),
                        () -> s.soundIndex = Math.floorMod(s.soundIndex + 1, Alerts.SOUND_NAMES.length));
                stepper("Volume", () -> s.soundVolume + "%", () -> s.soundVolume = clampI(s.soundVolume - 10, 0, 100),
                        () -> s.soundVolume = clampI(s.soundVolume + 10, 0, 100));
                stepper("Pitch", () -> String.valueOf(s.soundPitch), () -> s.soundPitch = clamp(s.soundPitch - 0.1f, 0.5f, 2f),
                        () -> s.soundPitch = clamp(s.soundPitch + 0.1f, 0.5f, 2f));
                stepper("Repeat", () -> s.soundRepeat + "x", () -> s.soundRepeat = clampI(s.soundRepeat - 1, 1, 5),
                        () -> s.soundRepeat = clampI(s.soundRepeat + 1, 1, 5));
                toggle("Also when queued", () -> s.soundWhenQueued, v -> s.soundWhenQueued = v);
                action("Preview popup sound", () -> Alerts.playSound(s.soundIndex, s.soundRepeat));
                toggle("Confirm sound", () -> s.soundConfirm, v -> s.soundConfirm = v);
                picker("Confirm", () -> Alerts.SOUND_NAMES[Math.floorMod(s.confirmSoundIndex, Alerts.SOUND_NAMES.length)],
                        () -> s.confirmSoundIndex = Math.floorMod(s.confirmSoundIndex - 1, Alerts.SOUND_NAMES.length),
                        () -> s.confirmSoundIndex = Math.floorMod(s.confirmSoundIndex + 1, Alerts.SOUND_NAMES.length));
                action("Preview confirm sound", () -> Alerts.playSound(s.confirmSoundIndex, 1));
            }
            case OVERLAY -> {
                toggle("Show overlay", () -> s.overlayOn, v -> s.overlayOn = v);
                toggle("Rainbow text", () -> s.overlayRainbow, v -> s.overlayRainbow = v);
                textRow("Line 1", () -> s.overlayLine1, v -> s.overlayLine1 = v);
                textRow("Line 2", () -> s.overlayLine2, v -> s.overlayLine2 = v);
                stepper("Size", () -> s.overlaySize + "x", () -> s.overlaySize = clamp(s.overlaySize - 0.25f, 0.5f, 4f),
                        () -> s.overlaySize = clamp(s.overlaySize + 0.25f, 0.5f, 4f));
                toggle("Shadow", () -> s.overlayShadow, v -> s.overlayShadow = v);
                color("Color", () -> s.overlayColor, v -> s.overlayColor = v, false);
            }
            case PAY -> {
                toggle("Ask before /pay", () -> s.payAsk, v -> s.payAsk = v);
                picker("Ask from", () -> Money.format(s.payFrom), () -> s.payFrom = Settings.stepMoney(s.payFrom, -1),
                        () -> s.payFrom = Settings.stepMoney(s.payFrom, 1));
                stepper("Delay", () -> s.payDelay + "s", () -> s.payDelay = clamp(s.payDelay - 0.5f, 0f, 5f),
                        () -> s.payDelay = clamp(s.payDelay + 0.5f, 0f, 5f));
                stepper("Tax %", () -> String.valueOf(s.payTax), () -> s.payTax = clamp(s.payTax - 0.5f, 0f, 20f),
                        () -> s.payTax = clamp(s.payTax + 0.5f, 0f, 20f));
                toggle("Show tax", () -> s.payShowTax, v -> s.payShowTax = v);
                toggle("Rainbow border", () -> s.payRainbow, v -> s.payRainbow = v);
                stepper("Dim screen", () -> s.payDim + "%", () -> s.payDim = clampI(s.payDim - 5, 0, 95),
                        () -> s.payDim = clampI(s.payDim + 5, 0, 95));
                color("Accent", () -> s.payAccent, v -> s.payAccent = v, false);
                color("BG", () -> s.payBg, v -> s.payBg = v, true);
                color("Name", () -> s.payName, v -> s.payName = v, false);
                color("Amount", () -> s.payAmount, v -> s.payAmount = v, false);
                color("Confirm btn", () -> s.payConfirm, v -> s.payConfirm = v, false);
                color("Cancel btn", () -> s.payCancel, v -> s.payCancel = v, false);
                action("Test confirm screen", () -> {
                    if (minecraft != null) minecraft.setScreen(new PayConfirmScreen("", "ExamplePlayer",
                            Math.max(s.payFrom, 5_000_000_000L), true));
                });
            }
        }
    }

    // ---------------- layout ----------------
    private static final int ROW_H = 20, ROW_GAP = 5;

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
        ww = Math.min(width - 60, 460);
        wh = Math.min(height - 16, 300);
        wx = (width - ww) / 2;
        wy = (height - wh) / 2;

        sideButtons.clear();
        sideSections.clear();
        int by = wy + 36;   // below the title and rainbow line
        for (Section sec : Section.values()) {
            Button b = addWidget(Button.builder(Component.literal(sec.label), btn -> {
                section = sec;
                rebuildWidgets();
            }).bounds(wx + 12, by, 102, 16).build());
            sideButtons.add(b);
            sideSections.add(sec);
            by += 18;   // a little tighter so Move HUDs always fits
        }
        doneButton = addWidget(Button.builder(Component.literal("Move HUDs"), b -> {
            if (minecraft != null) minecraft.setScreen(new MoveHudsScreen(this));
        })
                .bounds(wx + 12, wy + wh - 28, 102, 18).build());

        buildRows();
        int cx = wx + 130, cw = ww - 140, colW = (cw - 8) / 2;
        int y0 = wy + 52;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int col = i % 2, line = i / 2;
            r.x = cx + col * (colW + 8);
            r.y = y0 + line * (ROW_H + ROW_GAP);
            r.w = colW;
            switch (r.kind) {
                case TOGGLE -> r.b1 = invisible(r.x, r.y, r.w, ROW_H, () -> { r.setOn.accept(!r.on.getAsBoolean()); Settings.save(); });
                case STEPPER -> {
                    r.b1 = invisible(r.x + r.w - 66, r.y + 3, 14, 14, () -> { r.minus.run(); Settings.save(); });
                    r.b2 = invisible(r.x + r.w - 20, r.y + 3, 14, 14, () -> { r.plus.run(); Settings.save(); });
                }
                case PICKER -> {
                    r.b1 = invisible(r.x, r.y, r.w / 2, ROW_H, () -> { r.minus.run(); Settings.save(); });
                    r.b2 = invisible(r.x + r.w / 2, r.y, r.w - r.w / 2, ROW_H, () -> { r.plus.run(); Settings.save(); });
                }
                case COLOR -> r.b1 = invisible(r.x + r.w - 98, r.y + 3, 92, 14, () -> {
                    r.setColor.accept(Settings.nextColor(r.color.getAsInt(), r.bg));
                    Settings.save();
                });
                case ACTION -> r.b1 = invisible(r.x, r.y, r.w, ROW_H, r.action);
                case TEXT -> {
                    int bx = r.x + 72, bw = r.w - 80;
                    r.box = new EditBox(font, -4000, -4000, bw, 14, Component.literal(r.label));   // hidden: drawn by us
                    r.box.setMaxLength(48);
                    r.box.setValue(r.textGet.get());
                    r.box.setResponder(v -> r.textSet.accept(v));
                    addWidget(r.box);
                    final EditBox eb = r.box;
                    r.b1 = invisible(bx, r.y + 3, bw, 14, () -> pendingFocus = eb);
                }
            }
        }
        addThemeTiles();
    }

    // ---- Themes gallery: 2 columns x 9 rows per page ----
    private static int themePage = 0;
    private static final int PER_PAGE = 18;
    private final List<Button> themeTiles = new ArrayList<>();
    private final List<Integer> themeTileIndex = new ArrayList<>();
    private Button pagePrev, pageNext;

    private int pages() { return (Settings.MOD_THEME_NAMES.length + PER_PAGE - 1) / PER_PAGE; }

    private void addThemeTiles() {
        themeTiles.clear();
        themeTileIndex.clear();
        pagePrev = null; pageNext = null;
        if (true) return;   // no gallery page: themes are picked in General > Theme
        int cx = wx + 130, cw = ww - 140, colW = (cw - 8) / 2, tileH = 17, gap = 3, y0 = wy + 50;
        themePage = Math.floorMod(themePage, pages());
        for (int k = 0; k < PER_PAGE; k++) {
            int i = themePage * PER_PAGE + k;
            if (i >= Settings.MOD_THEME_NAMES.length) break;
            final int idx = i;
            int col = k / 9, row = k % 9;
            Button b = invisible(cx + col * (colW + 8), y0 + row * (tileH + gap), colW, tileH, () -> {
                Settings.applyModTheme(idx);
                Settings.save();
            });
            themeTiles.add(b);
            themeTileIndex.add(idx);
        }
        int py = y0 + 9 * (tileH + gap) + 4;
        pagePrev = invisible(cx, py, 24, 16, () -> { themePage = Math.floorMod(themePage - 1, pages()); rebuildWidgets(); });
        pageNext = invisible(cx + cw - 24, py, 24, 16, () -> { themePage = Math.floorMod(themePage + 1, pages()); rebuildWidgets(); });
    }

    private void themeGallery(GuiGraphicsExtractor g, int mx, int my) {
        for (int k = 0; k < themeTiles.size(); k++) {
            Button b = themeTiles.get(k);
            int idx = themeTileIndex.get(k);
            float p = rowProgress(k / 2 + 1);
            if (p <= 0.01f) continue;
            int x = b.getX(), y = b.getY() + (int) ((1 - p) * -8), w = b.getWidth(), h = b.getHeight();
            boolean sel = idx == Settings.S.modTheme, hot = over(mx, my, b.getX(), b.getY(), w, h);
            soft(g, x, y, w, h, alpha(sel ? 0xFF3A3A48 : hot ? ROW_HOVER : ROW_BG, p));
            if (sel) outline(g, x, y, w, h, alpha(0xFF000000 | Settings.themeSwatch(idx), p));
            int sw = 16, sh = 10, sx = x + 5, sy = y + (h - sh) / 2;
            int c = 0xFF000000 | Settings.themeSwatch(idx);
            for (int yy = 0; yy < sh; yy++)   // swatch with a soft top-to-bottom shade
                g.fill(sx, sy + yy, sx + sw, sy + yy + 1, alpha(lighten(c, 0.25f - yy * 0.05f > 0 ? 0.25f - yy * 0.05f : 0f), p));
            txt(g, Settings.MOD_THEME_NAMES[idx], sx + sw + 7, y + (h - 8) / 2, alpha(T_TEXT, p));
        }
        if (pagePrev != null) {
            int y = pagePrev.getY();
            soft(g, pagePrev.getX(), y, 24, 16, over(mx, my, pagePrev.getX(), y, 24, 16) ? 0xFF4A4A4A : 0xFF333333);
            txt(g, "<", pagePrev.getX() + 9, y + 4, T_TEXT);
            soft(g, pageNext.getX(), y, 24, 16, over(mx, my, pageNext.getX(), y, 24, 16) ? 0xFF4A4A4A : 0xFF333333);
            txt(g, ">", pageNext.getX() + 9, y + 4, T_TEXT);
            String pg = "Page " + (themePage + 1) + "/" + pages();
            int cx = wx + 130, cw = ww - 140;
            txt(g, pg, cx + (cw - font.width(pg)) / 2, y + 4, T_TEXT);
        }
    }

    private Button invisible(int x, int y, int w, int h, Runnable onClick) {
        return addWidget(Button.builder(Component.literal(""), b -> onClick.run()).bounds(x, y, w, h).build());
    }

    private static boolean over(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------------- drawing (dark charcoal style, Minecraft font) ----------------
    private static final int WIN_BG = 0xF5171717, HEADER = 0xFF1B1B1B, ROW_BG = 0xFF262626, ROW_HOVER = 0xFF2E2E2E,
            T_TEXT = 0xFFF0F0F0, T_MUTED = 0xFFB4B4B4, T_DIM = 0xFF8C8C8C, YELLOW = 0xFFE8C547;

    private long openedAt = System.currentTimeMillis();   // for the slide-in animation

    private static int alpha(int c, float a) {
        int base = (c >>> 24) & 0xFF;
        return (Math.max(0, Math.min(255, (int) (base * a))) << 24) | (c & 0xFFFFFF);
    }

    private static int lighten(int c, float t) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        r += (int) ((255 - r) * t); g += (int) ((255 - g) * t); b += (int) ((255 - b) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int rainbow(double t) {
        double h = (t % 1 + 1) % 1 * 6;
        int i = (int) h;
        double fr = h - i;
        int q = (int) (255 * (1 - fr)), tt = (int) (255 * fr);
        int r, gg, b;
        switch (i) {
            case 0 -> { r = 255; gg = tt; b = 0; }
            case 1 -> { r = q; gg = 255; b = 0; }
            case 2 -> { r = 0; gg = 255; b = tt; }
            case 3 -> { r = 0; gg = q; b = 255; }
            case 4 -> { r = tt; gg = 0; b = 255; }
            default -> { r = 255; gg = 0; b = q; }
        }
        return 0xFF000000 | (r << 16) | (gg << 8) | b;
    }

    private void txt(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        if (((color >>> 24) & 0xFF) < 8) return;   // fully faded: skip
        g.text(font, s, x, y, color, false);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, (Math.min(255, Settings.S.dimGame * 255 / 100) << 24));
    }

    private static int bright(int rgb) {
        return lighten(0xFF000000 | rgb, Settings.S.bgBrightness / 100f) & 0xFFFFFF;
    }

    private void windowBackground(GuiGraphicsExtractor g) {
        Settings st = Settings.S;
        int a = Math.max(0, Math.min(255, st.bgOpacity * 255 / 100));
        int top = bright(st.bgTop), bot = bright(st.bgGradient ? st.bgBottom : st.bgTop);
        // a near-black theme still needs to read as a panel: keep at least a charcoal floor
        for (int yy = 0; yy < wh; yy += 4) {
            float t = yy / (float) Math.max(1, wh);
            int r = (int) (((top >> 16) & 0xFF) + ((((bot >> 16) & 0xFF) - ((top >> 16) & 0xFF)) * t));
            int gg = (int) (((top >> 8) & 0xFF) + ((((bot >> 8) & 0xFF) - ((top >> 8) & 0xFF)) * t));
            int b = (int) ((top & 0xFF) + (((bot & 0xFF) - (top & 0xFF)) * t));
            g.fill(wx, wy + yy, wx + ww, Math.min(wy + wh, wy + yy + 4), (a << 24) | (r << 16) | (gg << 8) | b);
        }
    }

    private void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    /** Box with softly cut corners. */
    private void soft(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill) {
        g.fill(x + 1, y, x + w - 1, y + h, fill);
        g.fill(x, y + 1, x + w, y + h - 1, fill);
    }

    /** Eased 0..1 progress for row i of the slide-in (top row first). */
    private float rowProgress(int i) {
        long t = System.currentTimeMillis() - openedAt - i * 45L;
        float p = Math.max(0f, Math.min(1f, t / 220f));
        return 1f - (1f - p) * (1f - p) * (1f - p);
    }

    private Object pendingFocus;   // text box to focus on the next frame

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (pendingFocus instanceof EditBox eb) { setFocused(eb); pendingFocus = null; }
        super.extractRenderState(g, mouseX, mouseY, delta);
        int secCol = section.color();

        // window + header with title and a slowly shifting rainbow line
        windowBackground(g);
        g.fill(wx, wy, wx + ww, wy + 26, HEADER);
        g.pose().pushMatrix();
        g.pose().translate(wx + 10, wy + 8);
        g.pose().scale(1.35f, 1.35f);
        g.text(font, "Snowy's Dashboard", 0, 0, T_TEXT, false);
        g.pose().popMatrix();
        double t0 = System.currentTimeMillis() / 4000.0;
        for (int px = 0; px < ww; px += 3) g.fill(wx + px, wy + 26, wx + Math.min(ww, px + 3), wy + 28, rainbow(t0 + px / (double) ww));
        g.fill(wx + 122, wy + 28, wx + 123, wy + wh, 0xFF2C2C2C);

        // sidebar: every section has its own color
        for (int i = 0; i < sideButtons.size(); i++) {
            Button b = sideButtons.get(i);
            sideButton(g, b, b.getMessage().getString(), sideSections.get(i), sideSections.get(i) == section, mouseX, mouseY);
        }
        {
            Button b = doneButton;
            int x = b.getX(), y = b.getY(), w = b.getWidth(), h = b.getHeight();
            boolean hover = over(mouseX, mouseY, x, y, w, h);
            soft(g, x, y, w, h, hover ? 0xFF5A5A5A : 0xFF444444);
            txt(g, "Move HUDs", x + (w - font.width("Move HUDs")) / 2, y + (h - 8) / 2, T_TEXT);
        }

        // section description with a colored underline (slides in first)
        int cx = wx + 130, cw = ww - 140;
        float pHead = rowProgress(0);
        int hy = wy + 34 + (int) ((1 - pHead) * -6);
        txt(g, section.about, cx, hy, alpha(T_MUTED, pHead));
        g.fill(cx, hy + 11, cx + (int) (cw * pHead), hy + 12, alpha(lighten(secCol, 0.25f), pHead));

        // rows slide in one after another, top to bottom
        for (int i = 0; i < rows.size(); i++) drawRow(g, rows.get(i), mouseX, mouseY, rowProgress(i + 1));
        if (!themeTiles.isEmpty()) themeGallery(g, mouseX, mouseY);
        if (section == Section.CHAT) drawChatPreview(g);
        if (section == Section.FILTER) {
            Settings st = Settings.S;
            int last = rows.isEmpty() ? wy + 52 : rows.get(rows.size() - 1).y + 32;
            String a = st.filterOn ? "Filter is on: payments under " + Money.format(st.filterUnder) + " are hidden."
                    : "Filter is off: all payments are shown.";
            txt(g, a, wx + 130, last, T_TEXT);
            txt(g, "Hidden payments still count for the profit and the popup.", wx + 130, last + 12, T_DIM);
        }

        txt(g, "Tip: click a color square to cycle colors, click < > to pick", cx, wy + wh - 12, T_DIM);
    }

    private void sideButton(GuiGraphicsExtractor g, Button b, String label, Section sec, boolean selected, int mx, int my) {
        int x = b.getX(), y = b.getY(), w = b.getWidth(), h = b.getHeight();
        boolean hover = over(mx, my, x, y, w, h);
        int base = sec.color();
        int fill = selected ? lighten(base, 0.18f) : hover ? mix(base, 0.85f) : mix(base, 0.62f);
        soft(g, x, y, w, h, fill);
        if (selected) {
            outline(g, x, y, w, h, lighten(base, 0.5f));
            g.fill(x - 6, y + 2, x - 4, y + h - 2, lighten(base, 0.55f));   // marker on the left edge
        }
        txt(g, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, selected || hover ? 0xFFFFFFFF : 0xFFE6E6E6);
    }

    private void drawRow(GuiGraphicsExtractor g, Row r, int mx, int my, float p) {
        if (p <= 0.01f) return;
        int x = r.x, w = r.w, h = ROW_H;
        int y = r.y + (int) ((1 - p) * -10);    // drops into place
        boolean hover = over(mx, my, r.x, r.y, w, h);
        int ty = y + (h - 8) / 2;
        if (r.kind == Kind.ACTION) {
            soft(g, x, y, w, h, alpha(hover ? 0xFF4A4A4A : 0xFF3A3A3A, p));
            txt(g, r.label, x + (w - font.width(r.label)) / 2, ty, alpha(T_MUTED, p));
            return;
        }
        if (r.kind == Kind.TEXT) {
            soft(g, x, y, w, h, alpha(hover ? ROW_HOVER : ROW_BG, p));
            txt(g, r.label, x + 8, ty, alpha(T_TEXT, p));
            int bx = r.x + 72, bw = r.w - 80, by = y + 3;
            boolean focused = r.box != null && r.box.isFocused();
            g.fill(bx, by, bx + bw, by + 14, alpha(0xFF0A0A0A, p));
            outline(g, bx, by, bw, 14, alpha(focused ? accent() : 0xFF5A5A5A, p));
            String v = r.box == null ? "" : r.box.getValue();
            String shown = v;
            while (shown.length() > 1 && font.width(shown) > bw - 10) shown = shown.substring(1);
            int tx = bx + 4;
            if (shown.isEmpty() && !focused) txt(g, "click to type", tx, by + 3, alpha(T_DIM, p));
            else txt(g, shown, tx, by + 3, alpha(0xFFFFFFFF, p));
            if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = tx + font.width(shown);
                g.fill(cx, by + 2, cx + 1, by + 12, 0xFFFFFFFF);
            }
            return;
        }
        soft(g, x, y, w, h, alpha(hover ? ROW_HOVER : ROW_BG, p));
        switch (r.kind) {
            case TOGGLE -> {
                txt(g, r.label, x + 8, ty, alpha(T_TEXT, p));
                boolean on = r.on.getAsBoolean();
                int sw = 24, sh = 11, sx = x + w - sw - 8, sy = y + (h - sh) / 2;
                if (on) {   // accent track, dark knob on the right
                    soft(g, sx, sy, sw, sh, alpha(accent(), p));
                    g.fill(sx + sw - 10, sy + 2, sx + sw - 2, sy + sh - 2, alpha(0xFF1A1A1A, p));
                } else {    // dark track, gray knob on the left
                    soft(g, sx, sy, sw, sh, alpha(0xFF3C3C48, p));
                    g.fill(sx + 2, sy + 2, sx + 10, sy + sh - 2, alpha(0xFF7A7A86, p));
                }
            }
            case STEPPER -> {
                txt(g, r.label, x + 8, ty, alpha(T_TEXT, p));
                smallBox(g, x + w - 66, y + 3, "-", over(mx, my, x + w - 66, r.y + 3, 14, 14), p);
                smallBox(g, x + w - 20, y + 3, "+", over(mx, my, x + w - 20, r.y + 3, 14, 14), p);
                String v = r.value.get();
                int vx = x + w - 52, vw = 32;
                txt(g, v, vx + (vw - font.width(v)) / 2, ty, alpha(YELLOW, p));
            }
            case PICKER -> {
                String text = r.label + ": " + r.value.get();
                txt(g, "<", x + 8, ty, alpha(T_TEXT, p));
                txt(g, ">", x + w - 8 - font.width(">"), ty, alpha(T_TEXT, p));
                txt(g, text, x + (w - font.width(text)) / 2, ty, alpha(T_TEXT, p));
            }
            case COLOR -> {
                txt(g, r.label, x + 8, ty, alpha(T_TEXT, p));
                int rgb = r.color.getAsInt();
                int bx = x + w - 98;
                boolean hot = over(mx, my, bx, r.y + 3, 92, 14);
                g.fill(bx, y + 2, bx + 74, y + 18, alpha(0xFF0A0A0A, p));
                outline(g, bx, y + 2, 74, 16, alpha(hot ? accent() : 0xFF5A5A5A, p));
                txt(g, Settings.hex(rgb), bx + 5, y + 6, alpha(T_TEXT, p));
                g.fill(bx + 77, y + 2, bx + 93, y + 18, alpha(0xFF000000 | rgb, p));
            }
            default -> { }
        }
    }

    private void smallBox(GuiGraphicsExtractor g, int x, int y, String s, boolean hover, float p) {
        g.fill(x, y, x + 14, y + 14, alpha(hover ? 0xFF6A6A6A : 0xFF4A4A4A, p));
        txt(g, s, x + (14 - font.width(s)) / 2 + 1, y + 3, alpha(T_TEXT, p));
    }

    private void drawChatPreview(GuiGraphicsExtractor g) {
        Settings s = Settings.S;
        int last = rows.isEmpty() ? wy + 52 : rows.get(rows.size() - 1).y + 32;
        int x = wx + 130;
        txt(g, "Preview", x, last, T_MUTED);
        int y1 = last + 12, y2 = last + 24;
        int name = s.chatColors ? 0xFF000000 | s.nameColor : 0xFFFFFFFF;
        int recv = s.chatColors ? 0xFF000000 | s.receivedColor : 0xFFFFFFFF;
        int sent = s.chatColors ? 0xFF000000 | s.sentColor : 0xFFFFFFFF;
        int cx = x;
        cx = seg(g, "ExamplePlayer", cx, y1, name, s.chatColors && s.boldNames);
        cx = seg(g, " paid you ", cx, y1, 0xFFFFFFFF, false);
        seg(g, "$5B", cx, y1, recv, s.chatColors && s.boldAmounts);
        cx = x;
        cx = seg(g, "You paid ", cx, y2, 0xFFFFFFFF, false);
        cx = seg(g, "ExamplePlayer", cx, y2, name, s.chatColors && s.boldNames);
        cx = seg(g, " ", cx, y2, 0xFFFFFFFF, false);
        cx = seg(g, "$1M", cx, y2, sent, s.chatColors && s.boldAmounts);
        if (s.taxPct > 0) {
            long tax = Math.round(1_000_000L * (s.taxPct / 100.0));
            seg(g, " (+" + Money.format(tax) + " tax)", cx, y2, s.chatColors && s.colorTax ? 0xFF000000 | s.taxColor : 0xFFAAAAAA, false);
        }
    }

    /** Draws one piece of text (fake bold = drawn twice, 1px apart) and returns where the next piece starts. */
    private int seg(GuiGraphicsExtractor g, String s, int x, int y, int color, boolean bold) {
        g.text(font, s, x, y, color, false);
        if (bold) g.text(font, s, x + 1, y, color, false);
        return x + font.width(s) + (bold ? 1 : 0);
    }
}
