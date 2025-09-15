//package net.chaoscraft.chaoscrafts_device_mod.network.packet;

//import net.minecraft.core.BlockPos;
//import net.minecraft.network.FriendlyByteBuf;
//import net.minecraft.server.level.ServerLevel;
//import net.minecraft.world.level.Level;
//import net.minecraft.world.level.block.entity.BlockEntity;
//import net.minecraftforge.network.NetworkEvent;
//import net.chaoscraft.chaoscrafts_device_mod.block.entity.LaptopEntity;

//import java.util.function.Supplier;

//public class LidAngleSyncPacket {
//private final BlockPos pos;
// private final float angle;

//public LidAngleSyncPacket(BlockPos pos, float angle) {
//this.pos = pos;
//this.angle = angle;
//}

//public static void encode(LidAngleSyncPacket packet, FriendlyByteBuf buffer) {
//buffer.writeBlockPos(packet.pos);
//buffer.writeFloat(packet.angle);
//}

//public static LidAngleSyncPacket decode(FriendlyByteBuf buffer) {
//return new LidAngleSyncPacket(buffer.readBlockPos(), buffer.readFloat());
//}

//public static void handle(LidAngleSyncPacket packet, Supplier<NetworkEvent.Context> context) {
//context.get().enqueueWork(() -> {
            // Get the level from the context instead of the sender
//Level level = context.get().getSender().level();
//if (level instanceof ServerLevel serverLevel && serverLevel.hasChunkAt(packet.pos)) {
//BlockEntity be = serverLevel.getBlockEntity(packet.pos);
//if (be instanceof LaptopEntity laptop) {
                    // Use setTargetLidAngle instead of setLidAngle
//laptop.setTargetLidAngle(packet.angle);
//}
//}
//});
//context.get().setPacketHandled(true);
//}
//}
