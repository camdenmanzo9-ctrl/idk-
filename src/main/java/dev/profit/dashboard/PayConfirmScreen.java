package dev.profit.dashboard;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * "Are you sure?" screen for big /pay commands. Nothing is sent until you click Confirm,
 * and then it sends exactly the /pay you typed. Cancel (or Esc) throws it away.
 */
public class PayConfirmScreen extends Screen {
    /** Set while we send the confirmed command ourselves, so it isn't stopped a second time. */
    public static boolean bypass = false;
    /** A /pay waiting for the next tick to open this screen (the chat box has to close first). */
    public static PayConfirmScreen pending = null;

    private final String command, target;
    private final long amount;
    private final boolean test;
    private final long opened = System.currentTimeMillis();
    private Button confirm, cancel;

    public PayConfirmScreen(String command, String target, long amount, boolean test) {
        super(Component.literal("Confirm payment"));
        this.command = command;
        this.target = target;
        this.amount = amount;
        this.test = test;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        int w = 220, h = 120, x = (width - w) / 2, y = (height - h) / 2;
        confirm = addRenderableWidget(Button.builder(Component.literal("Confirm"), b -> doConfirm())
                .bounds(x + 12, y + h - 30, (w - 36) / 2, 20).build());
        cancel = addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(x + 24 + (w - 36) / 2, y + h - 30, (w - 36) / 2, 20).build());
    }

    private long waitLeft() {
        return Math.max(0, (long) (Settings.S.payDelay * 1000) - (System.currentTimeMillis() - opened));
    }

    private void doConfirm() {
        if (waitLeft() > 0) return;
        Alerts.playConfirmSound();
        if (!test && minecraft != null && minecraft.player != null) {
            bypass = true;
            try {
                minecraft.player.connection.sendCommand(command);
            } finally {
                bypass = false;
            }
        }
        onClose();
    }

    private static int argb(int rgb, int a) { return (a << 24) | (rgb & 0xFFFFFF); }

    private static int rainbow(double t) {
        double h = (t % 1 + 1) % 1 * 6;
        int i = (int) h;
        double fr = h - i;
        int q = (int) (255 * (1 - fr)), tt = (int) (255 * fr);
        int r, g, b;
        switch (i) {
            case 0 -> { r = 255; g = tt; b = 0; }
            case 1 -> { r = q; g = 255; b = 0; }
            case 2 -> { r = 0; g = 255; b = tt; }
            case 3 -> { r = 0; g = q; b = 255; }
            case 4 -> { r = tt; g = 0; b = 255; }
            default -> { r = 255; g = 0; b = q; }
        }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, argb(0x000000, Settings.S.payDim * 255 / 100));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        Settings s = Settings.S;
        int w = 220, h = 120, x = (width - w) / 2, y = (height - h) / 2;
        double t = System.currentTimeMillis() / 1500.0;

        g.fill(x, y, x + w, y + h, argb(s.payBg, 255));
        for (int px = 0; px < w; px++) {
            int c = s.payRainbow ? rainbow(t + px / (double) w) : argb(s.payAccent, 255);
            g.fill(x + px, y, x + px + 1, y + 2, c);
            g.fill(x + px, y + h - 1, x + px + 1, y + h, c);
        }
        for (int py = 0; py < h; py++) {
            int c = s.payRainbow ? rainbow(t + py / (double) h * 0.3) : argb(s.payAccent, 255);
            g.fill(x, y + py, x + 1, y + py + 1, c);
            g.fill(x + w - 1, y + py, x + w, y + py + 1, c);
        }

        g.text(font, test ? "CONFIRM PAYMENT (TEST)" : "CONFIRM PAYMENT", x + 12, y + 10, argb(s.payAccent, 255), false);
        g.text(font, "Pay", x + 12, y + 26, 0xFFFFFFFF, false);
        g.text(font, target, x + 12 + font.width("Pay "), y + 26, argb(s.payName, 255), false);

        g.pose().pushMatrix();
        g.pose().translate(x + 12, y + 40);
        g.pose().scale(2f, 2f);
        g.text(font, Money.format(amount), 0, 0, argb(s.payAmount, 255), false);
        g.pose().popMatrix();

        if (s.payShowTax) {
            long tax = Math.round(amount * (s.payTax / 100.0));
            g.text(font, "+" + Money.format(tax) + " tax (" + s.payTax + "%)", x + 12, y + 62, 0xFF9AA0AD, false);
        }

        // buttons drawn over the vanilla ones (those still take the clicks)
        long left = waitLeft();
        drawButton(g, confirm, left > 0 ? "Wait " + String.format("%.1f", left / 1000.0) + "s" : "Confirm",
                left > 0 ? 0x555555 : s.payConfirm, mouseX, mouseY);
        drawButton(g, cancel, "Cancel", s.payCancel, mouseX, mouseY);
    }

    private void drawButton(GuiGraphicsExtractor g, Button b, String label, int color, int mx, int my) {
        int x = b.getX(), y = b.getY(), w = b.getWidth(), h = b.getHeight();
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        g.fill(x, y, x + w, y + h, argb(color, hover ? 255 : 210));
        g.text(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, 0xFFFFFFFF, false);
    }
}
