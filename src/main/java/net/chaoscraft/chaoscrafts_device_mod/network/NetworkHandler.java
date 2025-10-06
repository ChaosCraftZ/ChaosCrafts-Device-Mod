package net.chaoscraft.chaoscrafts_device_mod.network;

import net.chaoscraft.chaoscrafts_device_mod.network.packet.LidStateSyncPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class NetworkHandler {
    private static final String PROTOCOL = "1";
    private static final ResourceLocation CHANNEL = ResourceLocation.fromNamespaceAndPath("chaoscrafts_device_mod", "net");
    private static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(CHANNEL, () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static int id = 0;

    public static <T> void registerMessage(Class<T> clazz, BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder, BiConsumer<T, Supplier<NetworkEvent.Context>> handler) {
        INSTANCE.registerMessage(id++, clazz, encoder, decoder::apply, (msg, ctxSupplier) -> { handler.accept(msg, ctxSupplier); ctxSupplier.get().setPacketHandled(true); });
    }

    public static SimpleChannel getChannel() { return INSTANCE; }

    public static void sendToServer(Object pkt) {
        INSTANCE.sendToServer(pkt);
    }

    public static void sendToPlayer(Object pkt, net.minecraft.server.level.ServerPlayer player) {
        if (player == null) return;
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), pkt);
    }
}
