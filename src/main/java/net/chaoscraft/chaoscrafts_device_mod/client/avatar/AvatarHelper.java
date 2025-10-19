package net.chaoscraft.chaoscrafts_device_mod.client.avatar;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.chaoscraft.chaoscrafts_device_mod.client.screen.RiftLoginScreen;

import java.util.UUID;

public class AvatarHelper {

    public static ResourceLocation getAvatarTexture(UUID playerId, int size) {
        try {
            return RiftLoginScreen.getAvatarTexture(playerId, size);
        } catch (Exception e) {
            e.printStackTrace();
            return getDefaultAvatar(size);
        }
    }

    public static ResourceLocation getDefaultAvatar(int size) {
        return RiftLoginScreen.getDefaultAvatar(size);
    }

    public static void clearCache() {
    }
}