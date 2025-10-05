package net.chaoscraft.chaoscrafts_device_mod.Client;

import net.chaoscraft.chaoscrafts_device_mod.Client.Screen.DesktopScreen;
import net.chaoscraft.chaoscrafts_device_mod.Core.PC.block.entity.LaptopEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Objects;

/**
 * Client-side helper for opening the laptop desktop UI from block interactions.
 */
public final class LaptopClient {
    private LaptopClient() {
    }

    public static void openDesktopScreen(BlockPos devicePos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        minecraft.execute(() -> openDesktopScreenInternal(minecraft, devicePos));
    }

    private static void openDesktopScreenInternal(Minecraft minecraft, BlockPos devicePos) {
        BlockPos targetPos = devicePos;
        Level level = minecraft.level;

        if (targetPos != null) {
            if (level == null || !(level.getBlockEntity(targetPos) instanceof LaptopEntity)) {
                targetPos = null;
            }
        }

        if (minecraft.screen instanceof DesktopScreen desktop) {
            if (Objects.equals(desktop.getDevicePos(), targetPos)) {
                return; // already open for this device
            }
        }

        minecraft.setScreen(new DesktopScreen(targetPos));
    }
}
