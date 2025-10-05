package net.chaoscraft.chaoscrafts_device_mod.Core.Network.packet;

import net.chaoscraft.chaoscrafts_device_mod.Core.PC.block.entity.LaptopEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class LidStateSyncPacket {
    private final BlockPos pos;
    private final boolean isOpen;

    public LidStateSyncPacket(BlockPos pos, boolean isOpen) {
        this.pos = pos;
        this.isOpen = isOpen;
    }

    public static void encode(LidStateSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeBoolean(packet.isOpen);
    }

    public static LidStateSyncPacket decode(FriendlyByteBuf buffer) {
        return new LidStateSyncPacket(buffer.readBlockPos(), buffer.readBoolean());
    }

    public static void handle(LidStateSyncPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            Level level = context.get().getSender().level();
            if (level instanceof ServerLevel serverLevel && serverLevel.isLoaded(packet.pos)) {
                BlockEntity be = serverLevel.getBlockEntity(packet.pos);
                if (be instanceof LaptopEntity laptop) {
                    // Use the updated setOpen method with two parameters
                    laptop.setOpen(packet.isOpen, true);
                }
            }
        });
        context.get().setPacketHandled(true);
    }
}