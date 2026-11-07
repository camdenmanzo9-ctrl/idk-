package dev.profit.dashboard;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class DashboardClient implements ClientModInitializer {
    private static boolean openNextTick = false;

    @Override
    public void onInitializeClient() {
        PaymentLedger.load();

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) PaymentListener.handle(message.getString());
        });

        // Commands close the chat screen after running, so the menu opens on the next tick.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openNextTick) {
                openNextTick = false;
                client.setScreen(new DashboardScreen());
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            literal("bettors")
                .executes(ctx -> { openNextTick = true; return 1; })
                .then(literal("endsession").executes(ctx -> {
                    PaymentListener.announceEnd(PaymentLedger.endSession());
                    return 1;
                }))
                .then(literal("session").executes(ctx -> {
                    ctx.getSource().sendFeedback(Component.literal("[Bettors] Session running for "
                            + PaymentLedger.formatDuration(PaymentLedger.sessionMillis())));
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
