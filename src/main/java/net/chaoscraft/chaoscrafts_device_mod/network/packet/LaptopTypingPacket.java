package net.chaoscraft.chaoscrafts_device_mod.network.packet;

import net.chaoscraft.chaoscrafts_device_mod.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class LaptopTypingPacket {
    private final BlockPos devicePos;

    public LaptopTypingPacket(BlockPos devicePos) { this.devicePos = devicePos; }

    public static void encode(LaptopTypingPacket pkt, FriendlyByteBuf buf) {
        buf.writeBoolean(pkt.devicePos != null);
        if (pkt.devicePos != null) buf.writeBlockPos(pkt.devicePos);
    }
    public static LaptopTypingPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = null;
        if (buf.readBoolean()) pos = buf.readBlockPos();
        return new LaptopTypingPacket(pos);
    }

    public static void handle(LaptopTypingPacket pkt, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
                BlockPos origin = pkt.devicePos != null ? pkt.devicePos : sender.blockPosition();
                sender.level().playSound(sender, origin, ModSounds.LAPTOP_KEYBOARD_GLOBAL.get(), SoundSource.BLOCKS, 0.45f, 0.95f + sender.level().random.nextFloat()*0.1f);
            }
        });
        ctx.setPacketHandled(true);
    }
}
