package net.chaoscraft.chaoscrafts_device_mod.server;

import net.chaoscraft.chaoscrafts_device_mod.CDM;
import net.chaoscraft.chaoscrafts_device_mod.network.packet.MessengerPackets;
import net.chaoscraft.chaoscrafts_device_mod.server.data.MessengerSavedData;
import net.chaoscraft.chaoscrafts_device_mod.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = CDM.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerMessengerEvents {

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        ServerPlayer player = resolvePlayer(event);
        if (player == null) return;
        try {
            ServerLevel level = player.server.getLevel(Level.OVERWORLD);
            MessengerSavedData data = MessengerSavedData.get(level);

            String email = player.getGameProfile().getName().toLowerCase() + "@rift.com";
            data.registerAccount(player.getUUID(), player.getGameProfile().getName(), email, "");

            List<MessengerPackets.ContactListUpdate.ContactData> contacts = new ArrayList<>();
            for (UUID friendId : data.getFriends(player.getUUID())) {
                MessengerSavedData.PlayerProfile p = data.getProfile(friendId);
                boolean isOnline = player.server.getPlayerList().getPlayer(friendId) != null;
                String display = p.displayName != null ? p.displayName : (p.email != null ? p.email.split("@")[0] : "Player");
                String e = p.email != null ? p.email : (display + "@rift.com");
                long lastSeen = p.lastSeen;
                String avatarHash = p.avatarHash != null ? p.avatarHash : "";
                contacts.add(new MessengerPackets.ContactListUpdate.ContactData(friendId, display, e, avatarHash, isOnline, lastSeen));
            }

            NetworkHandler.sendToPlayer(new MessengerPackets.ContactListUpdate(contacts), player);

            List<MessengerSavedData.PendingFriendRequest> pending = data.popPendingFriendRequests(player.getUUID());
            for (MessengerSavedData.PendingFriendRequest r : pending) {

                MessengerPackets.registerPendingRequest(r.requestId, r.fromPlayerId);

                NetworkHandler.sendToPlayer(new MessengerPackets.ReceiveFriendRequest(
                        r.requestId,
                        r.fromPlayerId,
                        r.fromPlayerName,
                        r.timestamp
                ), player);
            }

            List<MessengerSavedData.Message> msgs = data.popPendingMessages(player.getUUID());
            for (MessengerSavedData.Message m : msgs) {
                if (data.isBlocked(player.getUUID(), m.sender)) {
                    continue;
                }

                UUID convId = m.conversationId != null ? m.conversationId : m.sender;
                NetworkHandler.sendToPlayer(new MessengerPackets.ReceiveMessage(
                        m.sender,
                        data.getProfile(m.sender).displayName != null ? data.getProfile(m.sender).displayName : (data.getProfile(m.sender).email != null ? data.getProfile(m.sender).email.split("@")[0] : "Player"),
                        m.content,
                        m.timestamp,
                        m.id,
                        convId
                ), player);
            }

            for (MessengerSavedData.ConversationData conv : data.getAllConversations()) {
                if (conv != null && conv.isGroup && conv.participants.contains(player.getUUID())) {
                    List<UUID> members = new ArrayList<>(conv.participants);
                    UUID admin = members.isEmpty() ? null : members.get(0);
                    NetworkHandler.sendToPlayer(new MessengerPackets.GroupCreated(conv.id, conv.name == null ? "Group" : conv.name, admin, members), player);
                }
            }

            try {
                List<UUID> blocked = new ArrayList<>(data.getBlocked(player.getUUID()));
                NetworkHandler.sendToPlayer(new MessengerPackets.BlockListUpdate(blocked), player);
            } catch (Exception ignored) {}

            MessengerSavedData.PlayerProfile meProfile = data.getProfile(player.getUUID());
            String myDisplay = meProfile.displayName != null ? meProfile.displayName : (meProfile.email != null ? meProfile.email.split("@")[0] : player.getGameProfile().getName());
            String myEmail = meProfile.email != null ? meProfile.email : (myDisplay + "@rift.com");
            long lastSeen = meProfile.lastSeen;
            String avatarHash = meProfile.avatarHash != null ? meProfile.avatarHash : "";

            for (UUID friendId : data.getFriends(player.getUUID())) {
                ServerPlayer friendPlayer = player.server.getPlayerList().getPlayer(friendId);
                if (friendPlayer != null) {
                    List<MessengerPackets.ContactListUpdate.ContactData> upd = new ArrayList<>();
                    upd.add(new MessengerPackets.ContactListUpdate.ContactData(player.getUUID(), myDisplay, myEmail, avatarHash, true, lastSeen));
                    NetworkHandler.sendToPlayer(new MessengerPackets.ContactListUpdate(upd), friendPlayer);
                }
            }

        } catch (Exception e) {
            System.err.println("[ServerMessengerEvents] Error on player login: " + e);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ServerPlayer player = resolvePlayer(event);
        if (player == null) return;
        try {
            ServerLevel level = player.server.getLevel(Level.OVERWORLD);
            MessengerSavedData data = MessengerSavedData.get(level);
            MessengerSavedData.PlayerProfile p = data.getProfile(player.getUUID());
            p.lastSeen = System.currentTimeMillis();
            data.registerAccount(player.getUUID(), p.displayName != null ? p.displayName : player.getGameProfile().getName(), p.email, p.avatarHash);

            String display = p.displayName != null ? p.displayName : (p.email != null ? p.email.split("@")[0] : player.getGameProfile().getName());
            String email = p.email != null ? p.email : (display + "@rift.com");
            long lastSeen = p.lastSeen;
            String avatarHash = p.avatarHash != null ? p.avatarHash : "";

            for (UUID friendId : data.getFriends(player.getUUID())) {
                ServerPlayer friendPlayer = player.server.getPlayerList().getPlayer(friendId);
                if (friendPlayer != null) {
                    List<MessengerPackets.ContactListUpdate.ContactData> upd = new ArrayList<>();
                    upd.add(new MessengerPackets.ContactListUpdate.ContactData(player.getUUID(), display, email, avatarHash, false, lastSeen));
                    NetworkHandler.sendToPlayer(new MessengerPackets.ContactListUpdate(upd), friendPlayer);
                }
            }

        } catch (Exception e) {
            System.err.println("[ServerMessengerEvents] Error on player logout: " + e);
        }
    }

    private static ServerPlayer resolvePlayer(Object event) {
        if (event == null) return null;
        try {
            Method m = event.getClass().getMethod("getPlayer");
            Object p = m.invoke(event);
            if (p instanceof ServerPlayer) return (ServerPlayer) p;
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            System.err.println("[ServerMessengerEvents] Reflection error getting player via getPlayer: " + e);
        }

        try {
            Method m2 = event.getClass().getMethod("getEntity");
            Object p2 = m2.invoke(event);
            if (p2 instanceof ServerPlayer) return (ServerPlayer) p2;
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            System.err.println("[ServerMessengerEvents] Reflection error getting player via getEntity: " + e);
        }

        return null;
    }
}
