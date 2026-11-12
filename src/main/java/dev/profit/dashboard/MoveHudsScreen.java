package dev.profit.dashboard;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Move the profit box, win streak box and auction box with the arrow buttons. */
public class MoveHudsScreen extends Screen {
    private final Screen parent;
    private static int which = 0;   // 0 profit, 1 streak, 2 auction
    private static final String[] NAMES = {"Profit HUD", "Win streak", "Auction box"};
    private static boolean big = false;

    public MoveHudsScreen(Screen parent) {
        super(Component.literal("Move HUDs"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        Settings.save();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    private void nudge(int dx, int dy) {
        int step = big ? 20 : 4;
        Settings s = Settings.S;
        switch (which) {
            case 0 -> { s.hudOffX += dx * step; s.hudOffY += dy * step; }
            case 1 -> { s.streakX += dx * step; s.streakY += dy * step; s.streakSeparate = true; }
            default -> { s.auctionOffX += dx * step; s.auctionOffY += dy * step; }
        }
        Settings.save();
    }

    private void reset() {
        Settings s = Settings.S;
        switch (which) {
            case 0 -> { s.hudOffX = 0; s.hudOffY = 0; }
            case 1 -> { s.streakX = 8; s.streakY = 60; }
            default -> { s.auctionOffX = 0; s.auctionOffY = 0; }
        }
        Settings.save();
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        addRenderableWidget(Button.builder(Component.literal("< " + NAMES[which] + " >"), b -> {
            which = (which + 1) % NAMES.length;
            rebuildWidgets();
        }).bounds(cx - 70, cy - 52, 140, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Up"), b -> nudge(0, -1)).bounds(cx - 20, cy - 26, 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Left"), b -> nudge(-1, 0)).bounds(cx - 64, cy - 2, 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Right"), b -> nudge(1, 0)).bounds(cx + 24, cy - 2, 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Down"), b -> nudge(0, 1)).bounds(cx - 20, cy + 22, 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal(big ? "Step: big" : "Step: small"), b -> {
            big = !big;
            rebuildWidgets();
        }).bounds(cx - 70, cy + 50, 68, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset"), b -> reset()).bounds(cx + 2, cy + 50, 68, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(cx - 40, cy + 76, 80, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x40000000);   // light, so you can see the boxes move
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        String t = "Move HUDs - pick a box, then use the arrows";
        g.text(font, t, (width - font.width(t)) / 2, height / 2 - 70, 0xFFFFFFFF, true);
        if (which == 2 && !Auction.running) {
            String n = "(the auction box only shows while an auction runs)";
            g.text(font, n, (width - font.width(n)) / 2, height / 2 + 100, 0xFFB4B4B4, true);
        }
    }
}
