package dev.profit.dashboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.player.PlayerModelPart;

/**
 * A copy of you for the welcome picture only: same skin and skin layers,
 * nothing in your hands and no cape. It is never added to the world.
 */
final class PreviewPlayer extends RemotePlayer {
    PreviewPlayer(ClientLevel level, com.mojang.authlib.GameProfile profile) {
        super(level, profile);
        refreshParts();
    }

    /** Show the same hat / jacket / sleeve layers you have on, but never the cape. */
    void refreshParts() {
        Minecraft mc = Minecraft.getInstance();
        int mask = 0;
        for (PlayerModelPart p : PlayerModelPart.values())
            if (p != PlayerModelPart.CAPE && mc.options.isModelPartEnabled(p)) mask |= p.getMask();
        this.getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) mask);
    }
}
