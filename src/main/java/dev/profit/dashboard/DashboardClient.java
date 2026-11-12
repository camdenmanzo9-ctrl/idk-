package dev.profit.dashboard;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class DashboardClient implements ClientModInitializer {
    private static boolean openNextTick = false, openSettingsNextTick = false;
    private static long balAskedAt = 0, lastTick = 0;
    private static final java.util.regex.Pattern MONEY_RX =
            java.util.regex.Pattern.compile("\\$\\s?([\\d,]+(?:\\.\\d+)?\\s?[KkMmBbTt]?)");

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("bettors_dashboard", "main"));
    private static KeyMapping openKey, winKey, loseKey;

    @Override
    public void onInitializeClient() {
        PaymentLedger.load();
        Settings.load();

        // Profit box + big-payment popup, drawn on top of the game
        // Big /pay: hold it and ask first. The mod never sends it: after Confirm the command is
        // put back in your chat box and you press Enter yourself.
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            if (PayConfirmScreen.approved != null && PayConfirmScreen.approved.equals(command.trim())) {
                PayConfirmScreen.approved = null;   // you already confirmed this exact one
                return true;
            }
            Settings st = Settings.S;
            String[] a = command.trim().split("\\s+");
            if (!st.payAsk || a.length < 3 || !a[0].equalsIgnoreCase("pay")) return true;
            try {
                long amount = Money.parse(a[2]);
                if (amount < st.payFrom) return true;
                PayConfirmScreen.pending = new PayConfirmScreen(command.trim(), a[1], amount, false);
                return false;
            } catch (Exception e) {
                return true;
            }
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bettors_dashboard", "alerts"), (g, dt) -> Alerts.render(g));

        // Recolor payment messages in chat (only changes how they look on your screen)
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> overlay ? message : Alerts.recolor(message));

        // When you type /bal yourself, read the reply for the Balance card
        ClientSendMessageEvents.COMMAND.register(command -> {
            String c = command.trim().toLowerCase();
            if (c.equals("bal") || c.equals("balance")) balAskedAt = System.currentTimeMillis();
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay || balAskedAt == 0) return;
            if (System.currentTimeMillis() - balAskedAt > 5000) { balAskedAt = 0; return; }
            String text = message.getString();
            if (PaymentListener.parse(text) != null) return;
            java.util.regex.Matcher m = MONEY_RX.matcher(text);
            if (m.find()) {
                try { PaymentLedger.setBalance(Money.parse(m.group(1))); } catch (Exception ignored) { }
                balAskedAt = 0;
            }
        });


        // Default key: B. Change it in Options > Controls > Key Binds > Bettors Dashboard.
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.bettors_dashboard.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY));
        // Win streak keys (not set by default; pick keys in Controls)
        winKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.bettors_dashboard.win", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        loseKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.bettors_dashboard.lose", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));

        // Chat filter: hide small payments from chat. They are still counted first.
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            Settings st = Settings.S;
            if (overlay || !st.filterOn) return true;
            String text = message.getString();
            PaymentListener.Parsed p = PaymentListener.parse(text);
            if (p == null) return true;
            if (p.incoming() ? !st.filterReceived : !st.filterSent) return true;
            long amt;
            try { amt = Money.parse(p.amountText()); } catch (Exception e) { return true; }
            if (amt >= st.filterUnder) return true;
            PaymentListener.handle(text);   // still counts for profit and popups
            return false;
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) PaymentListener.handle(message.getString());
        });

        // Commands close the chat screen after running, so the menu opens on the next tick.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (winKey.consumeClick()) if (client.screen == null) Alerts.addWin();
            while (loseKey.consumeClick()) if (client.screen == null) Alerts.addLoss();
            while (openKey.consumeClick()) {
                if (client.screen == null) openNextTick = true;
            }
            if (openNextTick) {
                openNextTick = false;
                client.setScreen(new OverviewScreen());
            }
            if (openSettingsNextTick) {
                openSettingsNextTick = false;
                client.setScreen(new SettingsScreen(null));
            }
            if (PayConfirmScreen.pending != null && client.screen == null) {
                client.setScreen(PayConfirmScreen.pending);
                PayConfirmScreen.pending = null;
            }
            Alerts.tick();
            Auction.tick();
            long now = System.currentTimeMillis();
            if (client.player != null && lastTick > 0) PaymentLedger.addPlaytime(Math.min(1000, now - lastTick));
            lastTick = now;
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("win").executes(ctx -> {
                if (!Settings.S.streakCommands) { ctx.getSource().sendFeedback(Component.literal("[Bettors] /win is turned off in Settings")); return 1; }
                Alerts.addWin();
                return 1;
            }));
            dispatcher.register(literal("lose").executes(ctx -> {
                if (!Settings.S.streakCommands) { ctx.getSource().sendFeedback(Component.literal("[Bettors] /lose is turned off in Settings")); return 1; }
                Alerts.addLoss();
                return 1;
            }));
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            literal("bettors")
                .executes(ctx -> { openNextTick = true; return 1; })
                .then(literal("endsession").executes(ctx -> {
                    if (!PaymentLedger.isSessionActive()) {
                        ctx.getSource().sendFeedback(Component.literal("[Bettors] No session running. Use /bettors startsession"));
                        return 1;
                    }
                    PaymentLedger.endSession();
                    ctx.getSource().sendFeedback(Component.literal("[Bettors] Session ended."));
                    return 1;
                }))
                .then(literal("settings").executes(ctx -> { openSettingsNextTick = true; return 1; }))
                .then(literal("theme")
                    .then(literal("christmas").executes(ctx -> { PaymentLedger.setChristmas(true); return 1; }))
                    .then(literal("classic").executes(ctx -> { PaymentLedger.setChristmas(false); return 1; })))
                .then(literal("clearsessions").executes(ctx -> {
                    PaymentLedger.clearHistory();
                    ctx.getSource().sendFeedback(Component.literal("[Bettors] Session history cleared."));
                    return 1;
                }))
                .then(literal("startsession").executes(ctx -> {
                    PaymentLedger.startSession();
                    ctx.getSource().sendFeedback(Component.literal("[Bettors] New session started."));
                    return 1;
                }))
                .then(literal("session").executes(ctx -> {
                    ctx.getSource().sendFeedback(Component.literal(PaymentLedger.isSessionActive()
                            ? "[Bettors] Session running for " + PaymentLedger.formatDuration(PaymentLedger.sessionMillis())
                            : "[Bettors] No session running (last one lasted " + PaymentLedger.formatDuration(PaymentLedger.sessionMillis()) + ")"));
                    return 1;
                }))
                .then(literal("debug").executes(ctx -> {
                    PaymentListener.debug = !PaymentListener.debug;
                    ctx.getSource().sendFeedback(Component.literal("[Bettors] Debug " + (PaymentListener.debug ? "ON" : "OFF")));
                    return 1;
                }))
                .then(literal("add").then(argument("player", StringArgumentType.word())
                    .then(argument("paidYou", StringArgumentType.word())
                    .then(argument("youPaid", StringArgumentType.word())
                    .executes(ctx -> manual(ctx, false))))))
                .then(literal("import").then(argument("player", StringArgumentType.word())
                    .then(argument("paidYou", StringArgumentType.word())
                    .then(argument("youPaid", StringArgumentType.word())
                    .executes(ctx -> manual(ctx, true))))))
        ));
    }

    private static int manual(CommandContext<FabricClientCommandSource> ctx, boolean allTimeOnly) {
        String player = StringArgumentType.getString(ctx, "player");
        try {
            long in = Money.parse(StringArgumentType.getString(ctx, "paidYou"));
            long out = Money.parse(StringArgumentType.getString(ctx, "youPaid"));
            if (allTimeOnly) PaymentLedger.importAllTime(player, in, out);
            else PaymentLedger.record(player, in, out);
            ctx.getSource().sendFeedback(Component.literal("[Bettors] " + (allTimeOnly ? "Imported " : "Added ")
                    + player + ": paid you " + Money.format(in) + ", you paid " + Money.format(out)));
        } catch (Exception e) {
            ctx.getSource().sendError(Component.literal("[Bettors] Amounts look like 50M, 1.5B or 2000 (no $ sign)"));
        }
        return 1;
    }
}
