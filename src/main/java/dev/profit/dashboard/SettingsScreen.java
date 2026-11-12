package dev.profit.dashboard;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
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
        HUD("Profit HUD", "The profit box on your screen"),
        POPUP("Popup alert", "The alert for big payments you receive"),
        SOUNDS("Sounds", "Sound effects for alerts and confirmations"),
        PAY("Pay confirm", "The screen that asks before a big /pay"),
        BOARD("Bet board", "Your games and bet limits on screen");

        final String label, about;
        Section(String label, String about) { this.label = label; this.about = about; }
    }

    private enum Kind { TOGGLE, STEPPER, PICKER, COLOR, ACTION }

    /** One row in the options grid. */
    private static final class Row {
        Kind kind;
        String label;
        BooleanSupplier on; Consumer<Boolean> setOn;               // toggle
        Supplier<String> value; Runnable minus, plus;              // stepper / picker
        IntSupplier color; IntConsumer setColor; boolean bg;       // color
        Runnable action;                                           // action
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
                color("Accent", () -> s.accent, v -> s.accent = v, false);
                picker("Theme", () -> PaymentLedger.isChristmas() ? "Christmas" : "Classic",
                        () -> PaymentLedger.setChristmas(!PaymentLedger.isChristmas()),
                        () -> PaymentLedger.setChristmas(!PaymentLedger.isChristmas()));
                action("Reset all settings", () -> { Settings.resetAll(); rebuildWidgets(); });
            }
            case CHAT -> {
                toggle("Chat colors", () -> s.chatColors, v -> s.chatColors = v);
                color("Received $", () -> s.receivedColor, v -> s.receivedColor = v, false);
                color("Name", () -> s.nameColor, v -> s.nameColor = v, false);
                color("Sent $", () -> s.sentColor, v -> s.sentColor = v, false);
                toggle("Bold names", () -> s.boldNames, v -> s.boldNames = v);
                toggle("Bold amounts", () -> s.boldAmounts, v -> s.boldAmounts = v);
            }
            case HUD -> {
                toggle("Show HUD", () -> s.hudShow, v -> s.hudShow = v);
                toggle("Accent bar", () -> s.hudAccentBar, v -> s.hudAccentBar = v);
                toggle("Border", () -> s.hudBorder, v -> s.hudBorder = v);
                toggle("Show paused", () -> s.hudShowPaused, v -> s.hudShowPaused = v);
                stepper("Size", () -> s.hudSize + "x", () -> s.hudSize = clamp(s.hudSize - 0.1f, 0.5f, 2f),
                        () -> s.hudSize = clamp(s.hudSize + 0.1f, 0.5f, 2f));
                stepper("Opacity", () -> s.hudOpacity + "%", () -> s.hudOpacity = clampI(s.hudOpacity - 5, 10, 100),
                        () -> s.hudOpacity = clampI(s.hudOpacity + 5, 10, 100));
                color("Title", () -> s.hudTitle, v -> s.hudTitle = v, false);
                color("Gain", () -> s.hudGain, v -> s.hudGain = v, false);
                color("Loss", () -> s.hudLoss, v -> s.hudLoss = v, false);
                color("Zero", () -> s.hudZero, v -> s.hudZero = v, false);
                color("Background", () -> s.hudBg, v -> s.hudBg = v, true);
                color("Accent", () -> s.hudAccentColor, v -> s.hudAccentColor = v, false);
                String[] corners = {"Top left", "Top right", "Bottom left", "Bottom right"};
                picker("Position", () -> corners[s.hudCorner], () -> s.hudCorner = Math.floorMod(s.hudCorner - 1, 4),
                        () -> s.hudCorner = Math.floorMod(s.hudCorner + 1, 4));
            }
            case POPUP -> {
                toggle("Show popup", () -> s.popupShow, v -> s.popupShow = v);
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
                color("Background", () -> s.popupBg, v -> s.popupBg = v, true);
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
            case BOARD -> {
                toggle("Show board", () -> s.boardShow, v -> s.boardShow = v);
                String[] spots = {"Top left", "Top right", "Bottom left", "Bottom right", "Left middle", "Right middle"};
                picker("Position", () -> spots[Math.floorMod(s.boardPos, 6)], () -> s.boardPos = Math.floorMod(s.boardPos - 1, 6),
                        () -> s.boardPos = Math.floorMod(s.boardPos + 1, 6));
                stepper("Size", () -> s.boardSize + "x", () -> s.boardSize = clamp(s.boardSize - 0.1f, 0.4f, 2f),
                        () -> s.boardSize = clamp(s.boardSize + 0.1f, 0.4f, 2f));
                stepper("Row opacity", () -> s.boardOpacity + "%", () -> s.boardOpacity = clampI(s.boardOpacity - 5, 0, 100),
                        () -> s.boardOpacity = clampI(s.boardOpacity + 5, 0, 100));
                action("Reset games to default", () -> { s.boardTiers = Settings.defaultTiers(); Settings.save(); });
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
                color("Background", () -> s.payBg, v -> s.payBg = v, true);
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
    @Override
    protected void init() {
        ww = Math.min(width - 16, 470);
        wh = Math.min(height - 16, 300);
        wx = (width - ww) / 2;
        wy = (height - wh) / 2;

        sideButtons.clear();
        sideSections.clear();
        int by = wy + 30;
        for (Section sec : Section.values()) {
            Button b = addRenderableWidget(Button.builder(Component.literal(sec.label), btn -> {
                section = sec;
                rebuildWidgets();
            }).bounds(wx + 8, by, 96, 18).build());
            sideButtons.add(b);
            sideSections.add(sec);
            by += 22;
        }
        doneButton = addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(wx + 8, wy + wh - 26, 96, 18).build());

        buildRows();
        int cx = wx + 114, cw = ww - 122, colW = (cw - 6) / 2, rowH = 18, gap = 4;
        int y0 = wy + 44;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int col = i % 2, line = i / 2;
            r.x = cx + col * (colW + 6);
            r.y = y0 + line * (rowH + gap);
            r.w = colW;
            switch (r.kind) {
                case TOGGLE -> r.b1 = invisible(r.x, r.y, r.w, rowH, () -> { r.setOn.accept(!r.on.getAsBoolean()); Settings.save(); });
                case STEPPER -> {
                    r.b1 = invisible(r.x + r.w - 70, r.y + 2, 14, 14, () -> { r.minus.run(); Settings.save(); });
                    r.b2 = invisible(r.x + r.w - 18, r.y + 2, 14, 14, () -> { r.plus.run(); Settings.save(); });
                }
                case PICKER -> {
                    r.b1 = invisible(r.x, r.y, r.w / 2, rowH, () -> { r.minus.run(); Settings.save(); });
                    r.b2 = invisible(r.x + r.w / 2, r.y, r.w - r.w / 2, rowH, () -> { r.plus.run(); Settings.save(); });
                }
                case COLOR -> r.b1 = invisible(r.x + r.w - 92, r.y + 1, 88, 16, () -> {
                    r.setColor.accept(Settings.nextColor(r.color.getAsInt(), r.bg));
                    Settings.save();
                });
                case ACTION -> r.b1 = invisible(r.x, r.y, r.w, rowH, r.action);
            }
        }
    }

    private Button invisible(int x, int y, int w, int h, Runnable onClick) {
        return addRenderableWidget(Button.builder(Component.literal(""), b -> onClick.run()).bounds(x, y, w, h).build());
    }

    private static boolean over(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------------- drawing ----------------
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x88000000);
    }

    private void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        int acc = accent();

        // window, header, sidebar (drawn over the vanilla buttons, which still take the clicks)
        g.fill(wx, wy, wx + ww, wy + wh, WIN);
        outline(g, wx, wy, ww, wh, acc);
        g.fill(wx + 1, wy + 1, wx + ww - 1, wy + 22, HEAD);
        g.fill(wx + 1, wy + 22, wx + ww - 1, wy + 23, acc);
        g.text(font, "Dashboard Mod", wx + 10, wy + 8, acc, false);
        g.fill(wx + 110, wy + 23, wx + 111, wy + wh - 1, acc);

        for (int i = 0; i < sideButtons.size(); i++) {
            Button b = sideButtons.get(i);
            boolean sel = sideSections.get(i) == section;
            sideButton(g, b, b.getMessage().getString(), sel, mouseX, mouseY);
        }
        sideButton(g, doneButton, "Done", false, mouseX, mouseY);

        g.text(font, section.about, wx + 114, wy + 30, MUTED, false);
        for (Row r : rows) drawRow(g, r, mouseX, mouseY);

        if (section == Section.CHAT) drawChatPreview(g);

        String tip = "Tip: click a color box to cycle colors, or < > to change a choice";
        g.text(font, tip, wx + 114, wy + wh - 12, MUTED, false);
    }

    private void sideButton(GuiGraphicsExtractor g, Button b, String label, boolean selected, int mx, int my) {
        int x = b.getX(), y = b.getY(), w = b.getWidth(), h = b.getHeight();
        boolean hover = over(mx, my, x, y, w, h);
        int base = Settings.S.accent;
        g.fill(x, y, x + w, y + h, selected ? mix(base, 0.55f) : hover ? mix(base, 0.35f) : mix(base, 0.22f));
        outline(g, x, y, w, h, selected ? accent() : mix(base, 0.4f));
        g.text(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, TEXT, false);
    }

    private void drawRow(GuiGraphicsExtractor g, Row r, int mx, int my) {
        int x = r.x, y = r.y, w = r.w, h = 18, acc = accent();
        boolean hover = over(mx, my, x, y, w, h);
        if (r.kind == Kind.ACTION) {
            g.fill(x, y, x + w, y + h, hover ? mix(Settings.S.accent, 0.45f) : mix(Settings.S.accent, 0.3f));
            g.text(font, r.label, x + (w - font.width(r.label)) / 2, y + 5, TEXT, false);
            return;
        }
        g.fill(x, y, x + w, y + h, ROW);
        switch (r.kind) {
            case TOGGLE -> {
                g.text(font, r.label, x + 6, y + 5, TEXT, false);
                boolean on = r.on.getAsBoolean();
                int sx = x + w - 30, sy = y + 4;
                g.fill(sx, sy, sx + 24, sy + 10, on ? acc : OFF);
                int kx = on ? sx + 15 : sx + 1;
                g.fill(kx, sy + 1, kx + 8, sy + 9, KNOB);
            }
            case STEPPER -> {
                g.text(font, r.label, x + 6, y + 5, TEXT, false);
                drawSmallBox(g, x + w - 70, y + 2, "-", over(mx, my, x + w - 70, y + 2, 14, 14));
                drawSmallBox(g, x + w - 18, y + 2, "+", over(mx, my, x + w - 18, y + 2, 14, 14));
                String v = r.value.get();
                int vx = x + w - 56, vw = 38;
                g.text(font, v, vx + (vw - font.width(v)) / 2, y + 5, VALUE, false);
            }
            case PICKER -> {
                String text = r.label + ": " + r.value.get();
                g.text(font, "<", x + 6, y + 5, acc, false);
                g.text(font, ">", x + w - 6 - font.width(">"), y + 5, acc, false);
                g.text(font, text, x + (w - font.width(text)) / 2, y + 5, TEXT, false);
            }
            case COLOR -> {
                g.text(font, r.label, x + 6, y + 5, TEXT, false);
                int rgb = r.color.getAsInt();
                int bx = x + w - 92;
                g.fill(bx, y + 1, bx + 70, y + 17, BOX);
                outline(g, bx, y + 1, 70, 16, over(mx, my, bx, y + 1, 88, 16) ? acc : 0xFF2A3A55);
                g.text(font, Settings.hex(rgb), bx + 5, y + 5, TEXT, false);
                g.fill(bx + 72, y + 1, bx + 88, y + 17, 0xFF000000 | rgb);
                outline(g, bx + 72, y + 1, 16, 16, 0xFF2A3A55);
            }
            default -> { }
        }
    }

    private void drawSmallBox(GuiGraphicsExtractor g, int x, int y, String s, boolean hover) {
        g.fill(x, y, x + 14, y + 14, hover ? 0xFF3A5580 : 0xFF2A3A55);
        g.text(font, s, x + (14 - font.width(s)) / 2, y + 3, TEXT, false);
    }

    private void drawChatPreview(GuiGraphicsExtractor g) {
        Settings s = Settings.S;
        int last = rows.isEmpty() ? wy + 44 : rows.get(rows.size() - 1).y + 30;
        int x = wx + 114;
        g.text(font, "Preview", x, last, MUTED, false);
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
        seg(g, "$1M", cx, y2, sent, s.chatColors && s.boldAmounts);
    }

    /** Draws one piece of text (fake bold = drawn twice, 1px apart) and returns where the next piece starts. */
    private int seg(GuiGraphicsExtractor g, String s, int x, int y, int color, boolean bold) {
        g.text(font, s, x, y, color, false);
        if (bold) g.text(font, s, x + 1, y, color, false);
        return x + font.width(s) + (bold ? 1 : 0);
    }
}
