package net.chaoscraft.chaoscrafts_device_mod.client.avatar;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.chaoscraft.chaoscrafts_device_mod.client.screen.RiftLoginScreen;

import java.util.UUID;

public class AvatarHelper {

    public static ResourceLocation getAvatarTexture(UUID playerId, int size) {
        try {
            RiftLoginScreen tempScreen = new RiftLoginScreen(null);

            ResourceLocation skin = null;
            Minecraft mc = Minecraft.getInstance();

            if (mc.player != null && mc.player.getUUID().equals(playerId)) {
                skin = mc.player.getSkinTextureLocation();
            } else if (mc.level != null) {
                net.minecraft.world.entity.player.Player player = mc.level.getPlayerByUUID(playerId);
                if (player instanceof net.minecraft.client.player.AbstractClientPlayer) {
                    skin = ((net.minecraft.client.player.AbstractClientPlayer) player).getSkinTextureLocation();
                }
            }

            if (skin != null) {
                return tempScreen.getOrCreateAvatarTexture(skin, size, true);
            } else {
                return getDefaultAvatar(size);
            }

        } catch (Exception e) {
            if (Minecraft.getInstance().options.renderDebug) {
                System.out.println("AvatarHelper: getAvatarTexture failed -> " + e);
            }
            return getDefaultAvatar(size);
        }
    }

    public static ResourceLocation getDefaultAvatar(int size) {
        RiftLoginScreen tempScreen = new RiftLoginScreen(null);
        return tempScreen.getOrCreateAvatarTexture(null, size, false);
    }

    public static void clearCache() {
        try {
            RiftLoginScreen.clearCaches();
        } catch (Exception ignored) {}
    }
}