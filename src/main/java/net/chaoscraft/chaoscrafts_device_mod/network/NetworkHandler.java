package net.chaoscraft.chaoscrafts_device_mod.network;

import net.chaoscraft.chaoscrafts_device_mod.network.packet.LidStateSyncPacket;
import net.chaoscraft.chaoscrafts_device_mod.network.packet.MessengerPackets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
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
        INSTANCE.registerMessage(id++, clazz, encoder, decoder::apply, (msg, ctxSupplier) -> {
            handler.accept(msg, ctxSupplier);
            ctxSupplier.get().setPacketHandled(true);
        });
    }

    public static void registerMessengerPackets() {
        registerMessage(MessengerPackets.ProfileUpdateRequest.class, MessengerPackets.ProfileUpdateRequest::encode, MessengerPackets.ProfileUpdateRequest::decode, MessengerPackets.ProfileUpdateRequest::handle);
        registerMessage(MessengerPackets.SendMessage.class, MessengerPackets.SendMessage::encode, MessengerPackets.SendMessage::decode, MessengerPackets.SendMessage::handle);
        registerMessage(MessengerPackets.ReceiveMessage.class, MessengerPackets.ReceiveMessage::encode, MessengerPackets.ReceiveMessage::decode, MessengerPackets.ReceiveMessage::handle);
        registerMessage(MessengerPackets.CreateGroup.class, MessengerPackets.CreateGroup::encode, MessengerPackets.CreateGroup::decode, MessengerPackets.CreateGroup::handle);
        registerMessage(MessengerPackets.GroupCreated.class, MessengerPackets.GroupCreated::encode, MessengerPackets.GroupCreated::decode, MessengerPackets.GroupCreated::handle);
        registerMessage(MessengerPackets.FriendRequest.class, MessengerPackets.FriendRequest::encode, MessengerPackets.FriendRequest::decode, MessengerPackets.FriendRequest::handle);
        registerMessage(MessengerPackets.FriendResponse.class, MessengerPackets.FriendResponse::encode, MessengerPackets.FriendResponse::decode, MessengerPackets.FriendResponse::handle);
        registerMessage(MessengerPackets.ReceiveFriendRequest.class, MessengerPackets.ReceiveFriendRequest::encode, MessengerPackets.ReceiveFriendRequest::decode, MessengerPackets.ReceiveFriendRequest::handle);
        registerMessage(MessengerPackets.ReceiveFriendResponse.class, MessengerPackets.ReceiveFriendResponse::encode, MessengerPackets.ReceiveFriendResponse::decode, MessengerPackets.ReceiveFriendResponse::handle);
        registerMessage(MessengerPackets.ContactListUpdate.class, MessengerPackets.ContactListUpdate::encode, MessengerPackets.ContactListUpdate::decode, MessengerPackets.ContactListUpdate::handle);
        registerMessage(MessengerPackets.BlockUser.class, MessengerPackets.BlockUser::encode, MessengerPackets.BlockUser::decode, MessengerPackets.BlockUser::handle);
        registerMessage(MessengerPackets.UnblockUser.class, MessengerPackets.UnblockUser::encode, MessengerPackets.UnblockUser::decode, MessengerPackets.UnblockUser::handle);
        registerMessage(MessengerPackets.BlockListUpdate.class, MessengerPackets.BlockListUpdate::encode, MessengerPackets.BlockListUpdate::decode, MessengerPackets.BlockListUpdate::handle);
        registerMessage(MessengerPackets.LeaveGroup.class, MessengerPackets.LeaveGroup::encode, MessengerPackets.LeaveGroup::decode, MessengerPackets.LeaveGroup::handle);
        registerMessage(MessengerPackets.ChangeGroupName.class, MessengerPackets.ChangeGroupName::encode, MessengerPackets.ChangeGroupName::decode, MessengerPackets.ChangeGroupName::handle);
        registerMessage(MessengerPackets.GroupNameChanged.class, MessengerPackets.GroupNameChanged::encode, MessengerPackets.GroupNameChanged::decode, MessengerPackets.GroupNameChanged::handle);
        registerMessage(MessengerPackets.RemoveGroupMember.class, MessengerPackets.RemoveGroupMember::encode, MessengerPackets.RemoveGroupMember::decode, MessengerPackets.RemoveGroupMember::handle);
        registerMessage(MessengerPackets.ChangeGroupDescription.class, MessengerPackets.ChangeGroupDescription::encode, MessengerPackets.ChangeGroupDescription::decode, MessengerPackets.ChangeGroupDescription::handle);
        registerMessage(MessengerPackets.GroupDescriptionChanged.class, MessengerPackets.GroupDescriptionChanged::encode, MessengerPackets.GroupDescriptionChanged::decode, MessengerPackets.GroupDescriptionChanged::handle);
        registerMessage(MessengerPackets.MemberRemovedFromGroup.class, MessengerPackets.MemberRemovedFromGroup::encode, MessengerPackets.MemberRemovedFromGroup::decode, MessengerPackets.MemberRemovedFromGroup::handle);
        registerMessage(MessengerPackets.AddMemberToGroup.class, MessengerPackets.AddMemberToGroup::encode, MessengerPackets.AddMemberToGroup::decode, MessengerPackets.AddMemberToGroup::handle);
    }

    public static SimpleChannel getChannel() { return INSTANCE; }

    public static void sendToServer(Object pkt) {
        INSTANCE.sendToServer(pkt);
    }

    public static void sendToPlayer(Object pkt, ServerPlayer player) {
        if (player == null) return;
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), pkt);
    }
}