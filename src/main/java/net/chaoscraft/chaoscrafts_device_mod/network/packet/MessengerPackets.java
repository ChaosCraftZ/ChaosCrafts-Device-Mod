package net.chaoscraft.chaoscrafts_device_mod.network.packet;

import net.chaoscraft.chaoscrafts_device_mod.client.app.messenger.MessengerNetworkManager;
import net.chaoscraft.chaoscrafts_device_mod.network.NetworkHandler;
import net.chaoscraft.chaoscrafts_device_mod.server.data.MessengerSavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class MessengerPackets {

    private static final java.util.concurrent.ConcurrentHashMap<UUID, UUID> pendingRequests = new java.util.concurrent.ConcurrentHashMap<>();

    public static void registerPendingRequest(UUID requestId, UUID senderUuid) {
        if (requestId == null || senderUuid == null) return;
        pendingRequests.put(requestId, senderUuid);
    }

    public static class ProfileUpdateRequest {
        private final String displayName;
        private final String status;
        private final byte[] avatarData;

        public ProfileUpdateRequest(String displayName, String status, byte[] avatarData) {
            this.displayName = displayName;
            this.status = status;
            this.avatarData = avatarData;
        }

        public static void encode(ProfileUpdateRequest msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.displayName);
            buf.writeUtf(msg.status);
            buf.writeByteArray(msg.avatarData);
        }

        public static ProfileUpdateRequest decode(FriendlyByteBuf buf) {
            return new ProfileUpdateRequest(buf.readUtf(), buf.readUtf(), buf.readByteArray());
        }

        public static void handle(ProfileUpdateRequest msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    System.out.println("[Server] Profile update from " + player.getGameProfile().getName());
                    try {
                        ServerLevel level = player.server.getLevel(Level.OVERWORLD);
                        MessengerSavedData data = MessengerSavedData.get(level);
                        String email = player.getGameProfile().getName().toLowerCase() + "@rift.com";
                        String avatarHash = "";
                        if (msg.avatarData != null && msg.avatarData.length > 0) {
                            try {
                                MessageDigest md = MessageDigest.getInstance("MD5");
                                byte[] digest = md.digest(msg.avatarData);
                                avatarHash = String.format("%032x", new BigInteger(1, digest));
                            } catch (NoSuchAlgorithmException ignored) {}
                        }
                        data.registerAccount(player.getUUID(), msg.displayName, email, avatarHash);
                    } catch (Exception e) {
                        System.err.println("[Server] Failed to register profile: " + e);
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class SendMessage {
        private final UUID recipientId;
        private final String message;
        private final UUID replyTo;

        public SendMessage(UUID recipientId, String message, UUID replyTo) {
            this.recipientId = recipientId;
            this.message = message;
            this.replyTo = replyTo;
        }

        public static void encode(SendMessage msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.recipientId);
            buf.writeUtf(msg.message);
            buf.writeBoolean(msg.replyTo != null);
            if (msg.replyTo != null) {
                buf.writeUUID(msg.replyTo);
            }
        }

        public static SendMessage decode(FriendlyByteBuf buf) {
            UUID recipientId = buf.readUUID();
            String message = buf.readUtf();
            boolean hasReply = buf.readBoolean();
            UUID replyTo = hasReply ? buf.readUUID() : null;
            return new SendMessage(recipientId, message, replyTo);
        }

        public static void handle(SendMessage msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sender = ctx.get().getSender();
                if (sender != null) {
                    try {
                        ServerLevel level = sender.server.getLevel(Level.OVERWORLD);
                        MessengerSavedData data = MessengerSavedData.get(level);

                        String email = sender.getGameProfile().getName().toLowerCase() + "@rift.com";
                        data.registerAccount(sender.getUUID(), sender.getGameProfile().getName(), email, "");

                        MessengerSavedData.ConversationData conv = data.getConversationData(msg.recipientId);
                        if (conv != null && conv.isGroup) {
                            if (!conv.participants.contains(sender.getUUID())) {
                                System.out.println("[Server] Sender " + sender.getUUID() + " tried to send message to group " + conv.id + " but is not a member");
                                ctx.get().setPacketHandled(true);
                                return;
                            }
                             MessengerSavedData.Message gm = new MessengerSavedData.Message(UUID.randomUUID(), sender.getUUID(), msg.message, System.currentTimeMillis(), conv.id);
                             conv.messages.add(gm);
                             data.putConversationData(conv.id, conv);

                            for (UUID member : conv.participants) {
                                if (member.equals(sender.getUUID())) continue;
                                if (data.isBlocked(member, sender.getUUID())) continue;

                                ServerPlayer memberPlayer = sender.server.getPlayerList().getPlayer(member);
                                if (memberPlayer != null) {
                                    NetworkHandler.sendToPlayer(new ReceiveMessage(
                                            sender.getUUID(),
                                            sender.getGameProfile().getName(),
                                            msg.message,
                                            System.currentTimeMillis(),
                                            gm.id,
                                            conv.id
                                    ), memberPlayer);
                                } else {
                                    if (!data.isBlocked(member, sender.getUUID())) {
                                        data.addPendingMessage(member, gm);
                                    }
                                }
                            }

                        } else {
                            ServerPlayer recipient = sender.server.getPlayerList().getPlayer(msg.recipientId);

                            if (data.isBlocked(msg.recipientId, sender.getUUID())) {
                                System.out.println("[Server] Message from " + sender.getUUID() + " to " + msg.recipientId + " blocked by recipient");
                            } else {
                                if (recipient != null) {
                                    NetworkHandler.sendToPlayer(new ReceiveMessage(
                                            sender.getUUID(),
                                            sender.getGameProfile().getName(),
                                            msg.message,
                                            System.currentTimeMillis(),
                                            UUID.randomUUID(),
                                            sender.getUUID()
                                    ), recipient);
                                } else {
                                    MessengerSavedData.Message m = new MessengerSavedData.Message(
                                            UUID.randomUUID(),
                                            sender.getUUID(),
                                            msg.message,
                                            System.currentTimeMillis(),
                                            sender.getUUID()
                                    );
                                    data.addPendingMessage(msg.recipientId, m);
                                    System.out.println("[Server] Stored offline message for " + msg.recipientId + " from " + sender.getUUID());
                                }
                            }
                        }

                    } catch (Exception e) {
                        System.err.println("[Server] Error handling SendMessage: " + e);
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class ReceiveMessage {
        private final UUID senderId;
        private final String senderName;
        private final String message;
        private final long timestamp;
        private final UUID messageId;
        private final UUID conversationId;

        public ReceiveMessage(UUID senderId, String senderName, String message, long timestamp, UUID messageId, UUID conversationId) {
            this.senderId = senderId;
            this.senderName = senderName;
            this.message = message;
            this.timestamp = timestamp;
            this.messageId = messageId;
            this.conversationId = conversationId;
        }

        public static void encode(ReceiveMessage msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.senderId);
            buf.writeUtf(msg.senderName);
            buf.writeUtf(msg.message);
            buf.writeLong(msg.timestamp);
            buf.writeUUID(msg.messageId);
            buf.writeUUID(msg.conversationId);
        }

        public static ReceiveMessage decode(FriendlyByteBuf buf) {
            return new ReceiveMessage(
                    buf.readUUID(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readLong(),
                    buf.readUUID(),
                    buf.readUUID()
            );
        }

        public static void handle(ReceiveMessage msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                try {
                    if (MessengerNetworkManager.getInstance().isBlocked(msg.senderId)) return;
                } catch (Exception ignored) {}

                MessengerNetworkManager.getInstance().addMessageToConversation(
                        msg.conversationId,
                        new MessengerNetworkManager.Message(
                                msg.messageId,
                                msg.senderId,
                                msg.conversationId,
                                msg.message,
                                msg.timestamp,
                                true
                        )
                );
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class CreateGroup {
        private final String name;
        private final List<UUID> members;

        public CreateGroup(String name, List<UUID> members) {
            this.name = name;
            this.members = members;
        }

        public static void encode(CreateGroup msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.name);
            buf.writeInt(msg.members.size());
            for (UUID u : msg.members) buf.writeUUID(u);
        }

        public static CreateGroup decode(FriendlyByteBuf buf) {
            String name = buf.readUtf();
            int size = buf.readInt();
            List<UUID> members = new ArrayList<>();
            for (int i = 0; i < size; i++) members.add(buf.readUUID());
            return new CreateGroup(name, members);
        }

        public static void handle(CreateGroup msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sender = ctx.get().getSender();
                if (sender == null) return;
                try {
                    ServerLevel level = sender.server.getLevel(Level.OVERWORLD);
                    MessengerSavedData data = MessengerSavedData.get(level);

                    UUID admin = sender.getUUID();
                    if (!msg.members.contains(admin)) msg.members.add(admin);
                    String email = sender.getGameProfile().getName().toLowerCase() + "@rift.com";
                    data.registerAccount(admin, sender.getGameProfile().getName(), email, "");

                    UUID gid = UUID.randomUUID();
                    MessengerSavedData.ConversationData conv = new MessengerSavedData.ConversationData(gid);
                    conv.isGroup = true;
                    conv.name = msg.name != null && !msg.name.isEmpty() ? msg.name : "Group";
                    conv.participants.addAll(msg.members);
                    conv.adminId = admin;
                    data.putConversationData(gid, conv);
                    data.setDirty();

                    for (UUID member : msg.members) {
                        ServerPlayer memberPlayer = sender.server.getPlayerList().getPlayer(member);
                        if (memberPlayer != null) {
                            List<UUID> partCopy = new ArrayList<>(conv.participants);
                            NetworkHandler.sendToPlayer(new GroupCreated(gid, conv.name, admin, partCopy), memberPlayer);
                        }
                    }

                } catch (Exception e) {
                    System.err.println("[Server] Error handling CreateGroup: " + e);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class GroupCreated {
        private final UUID groupId;
        private final String name;
        private final UUID adminId;
        private final List<UUID> members;

        public GroupCreated(UUID groupId, String name, UUID adminId, List<UUID> members) {
            this.groupId = groupId;
            this.name = name;
            this.adminId = adminId;
            this.members = members;
        }

        public static void encode(GroupCreated msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.groupId);
            buf.writeUtf(msg.name);
            buf.writeUUID(msg.adminId == null ? new UUID(0,0) : msg.adminId);
            buf.writeInt(msg.members.size());
            for (UUID u : msg.members) buf.writeUUID(u);
        }

        public static GroupCreated decode(FriendlyByteBuf buf) {
            UUID gid = buf.readUUID();
            String name = buf.readUtf();
            UUID admin = buf.readUUID();
            if (admin.getMostSignificantBits() == 0 && admin.getLeastSignificantBits() == 0) admin = null;
            int size = buf.readInt();
            List<UUID> members = new ArrayList<>();
            for (int i = 0; i < size; i++) members.add(buf.readUUID());
            return new GroupCreated(gid, name, admin, members);
        }

        public static void handle(GroupCreated msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                MessengerNetworkManager manager = MessengerNetworkManager.getInstance();
                MessengerNetworkManager.Group g = new MessengerNetworkManager.Group(msg.groupId, msg.name, msg.members);
                g.adminId = msg.adminId;
                manager.upsertGroup(g);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class FriendRequest {
        private final String targetEmail;

        public FriendRequest(String targetEmail) {
            this.targetEmail = targetEmail;
        }

        public static void encode(FriendRequest msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.targetEmail);
        }

        public static FriendRequest decode(FriendlyByteBuf buf) {
            return new FriendRequest(buf.readUtf());
        }

        public static void handle(FriendRequest msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sender = ctx.get().getSender();
                if (sender != null) {
                    try {
                        ServerLevel level = sender.server.getLevel(Level.OVERWORLD);
                        MessengerSavedData data = MessengerSavedData.get(level);

                        String senderEmail = sender.getGameProfile().getName().toLowerCase() + "@rift.com";
                        data.registerAccount(sender.getUUID(), sender.getGameProfile().getName(), senderEmail, "");

                        java.util.Optional<UUID> optTarget = data.resolveEmail(msg.targetEmail);
                        if (optTarget.isPresent()) {
                            UUID targetId = optTarget.get();
                            ServerPlayer recipient = sender.server.getPlayerList().getPlayer(targetId);
                            UUID requestId = UUID.randomUUID();
                            pendingRequests.put(requestId, sender.getUUID());

                            if (recipient != null) {
                                NetworkHandler.sendToPlayer(new ReceiveFriendRequest(
                                        requestId,
                                        sender.getUUID(),
                                        sender.getGameProfile().getName(),
                                        System.currentTimeMillis()
                                ), recipient);
                            } else {
                                MessengerSavedData.PendingFriendRequest r = new MessengerSavedData.PendingFriendRequest(
                                        requestId,
                                        sender.getUUID(),
                                        sender.getGameProfile().getName(),
                                        System.currentTimeMillis()
                                );
                                data.addPendingFriendRequest(targetId, r);
                                System.out.println("[Server] Queued friend request for offline player: " + msg.targetEmail);
                            }
                        } else {
                            System.out.println("[Server] Friend target not found (no account): " + msg.targetEmail);
                        }
                    } catch (Exception e) {
                        System.err.println("[Server] Error handling FriendRequest: " + e);
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class ReceiveFriendRequest {
        private final UUID requestId;
        private final UUID fromPlayerId;
        private final String fromPlayerName;
        private final long timestamp;

        public ReceiveFriendRequest(UUID requestId, UUID fromPlayerId, String fromPlayerName, long timestamp) {
            this.requestId = requestId;
            this.fromPlayerId = fromPlayerId;
            this.fromPlayerName = fromPlayerName;
            this.timestamp = timestamp;
        }

        public static void encode(ReceiveFriendRequest msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.requestId);
            buf.writeUUID(msg.fromPlayerId);
            buf.writeUtf(msg.fromPlayerName);
            buf.writeLong(msg.timestamp);
        }

        public static ReceiveFriendRequest decode(FriendlyByteBuf buf) {
            return new ReceiveFriendRequest(
                    buf.readUUID(),
                    buf.readUUID(),
                    buf.readUtf(),
                    buf.readLong()
            );
        }

        public static void handle(ReceiveFriendRequest msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                MessengerNetworkManager.getInstance().addFriendRequest(new MessengerNetworkManager.FriendRequest(
                        msg.requestId,
                        msg.fromPlayerId,
                        msg.fromPlayerName,
                        msg.timestamp
                ));
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class FriendResponse {
        private final UUID requestId;
        private final boolean accept;

        public FriendResponse(UUID requestId, boolean accept) {
            this.requestId = requestId;
            this.accept = accept;
        }

        public static void encode(FriendResponse msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.requestId);
            buf.writeBoolean(msg.accept);
        }

        public static FriendResponse decode(FriendlyByteBuf buf) {
            return new FriendResponse(buf.readUUID(), buf.readBoolean());
        }

        public static void handle(FriendResponse msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer responder = ctx.get().getSender();
                if (responder != null) {
                    try {
                        UUID originalSender = pendingRequests.remove(msg.requestId);

                        ServerLevel level = responder.server.getLevel(Level.OVERWORLD);
                        MessengerSavedData data = MessengerSavedData.get(level);

                        MessengerSavedData.PendingFriendRequest pending = null;
                        if (originalSender == null) {
                            java.util.Optional<MessengerSavedData.PendingFriendRequest> maybe = data.removePendingRequestById(msg.requestId);
                            if (maybe.isPresent()) {
                                pending = maybe.get();
                                originalSender = pending.fromPlayerId;
                            }
                        }

                        if (originalSender != null) {
                            data.addFriendRelation(originalSender, responder.getUUID());
                        }

                        if (originalSender != null) {
                            ServerPlayer original = responder.server.getPlayerList().getPlayer(originalSender);
                            if (original != null) {
                                NetworkHandler.sendToPlayer(new ReceiveFriendResponse(
                                        msg.requestId,
                                        responder.getUUID(),
                                        responder.getGameProfile().getName(),
                                        msg.accept,
                                        System.currentTimeMillis()
                                ), original);
                            }
                        }

                    } catch (Exception e) {
                        System.err.println("[Server] Error handling FriendResponse: " + e);
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class ReceiveFriendResponse {
        private final UUID requestId;
        private final UUID fromPlayerId;
        private final String fromPlayerName;
        private final boolean accepted;
        private final long timestamp;

        public ReceiveFriendResponse(UUID requestId, UUID fromPlayerId, String fromPlayerName, boolean accepted, long timestamp) {
            this.requestId = requestId;
            this.fromPlayerId = fromPlayerId;
            this.fromPlayerName = fromPlayerName;
            this.accepted = accepted;
            this.timestamp = timestamp;
        }

        public static void encode(ReceiveFriendResponse msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.requestId);
            buf.writeUUID(msg.fromPlayerId);
            buf.writeUtf(msg.fromPlayerName);
            buf.writeBoolean(msg.accepted);
            buf.writeLong(msg.timestamp);
        }

        public static ReceiveFriendResponse decode(FriendlyByteBuf buf) {
            return new ReceiveFriendResponse(
                    buf.readUUID(),
                    buf.readUUID(),
                    buf.readUtf(),
                    buf.readBoolean(),
                    buf.readLong()
            );
        }

        public static void handle(ReceiveFriendResponse msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                if (msg.accepted) {
                    MessengerNetworkManager.Contact newContact = new MessengerNetworkManager.Contact(
                            msg.fromPlayerId,
                            msg.fromPlayerName,
                            msg.fromPlayerName + "@rift.com",
                            "",
                            true,
                            msg.timestamp
                    );
                    MessengerNetworkManager.getInstance().addContact(newContact);

                    MessengerNetworkManager.getInstance().addMessageToConversation(msg.fromPlayerId, new MessengerNetworkManager.Message(
                            UUID.randomUUID(),
                            msg.fromPlayerId,
                            null,
                            "Hi! I accepted your friend request!",
                            msg.timestamp,
                            true
                    ));
                }

                MessengerNetworkManager.getInstance().removeFriendRequest(msg.requestId);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class ContactListUpdate {
        private final List<ContactData> contacts;

        public ContactListUpdate(List<ContactData> contacts) {
            this.contacts = contacts;
        }

        public static void encode(ContactListUpdate msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.contacts.size());
            for (ContactData contact : msg.contacts) {
                buf.writeUUID(contact.playerId);
                buf.writeUtf(contact.displayName);
                buf.writeUtf(contact.email);
                buf.writeUtf(contact.avatarHash);
                buf.writeBoolean(contact.isOnline);
                buf.writeLong(contact.lastSeen);
            }
        }

        public static ContactListUpdate decode(FriendlyByteBuf buf) {
            int size = buf.readInt();
            List<ContactData> contacts = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                contacts.add(new ContactData(
                        buf.readUUID(),
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readBoolean(),
                        buf.readLong()
                ));
            }
            return new ContactListUpdate(contacts);
        }

        public static void handle(ContactListUpdate msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                MessengerNetworkManager manager = MessengerNetworkManager.getInstance();
                for (ContactData contact : msg.contacts) {
                    manager.addContact(new MessengerNetworkManager.Contact(
                            contact.playerId,
                            contact.displayName,
                            contact.email,
                            contact.avatarHash,
                            contact.isOnline,
                            contact.lastSeen
                    ));
                }
            });
            ctx.get().setPacketHandled(true);
        }

        public static class ContactData {
            public final UUID playerId;
            public final String displayName;
            public final String email;
            public final String avatarHash;
            public final boolean isOnline;
            public final long lastSeen;

            public ContactData(UUID playerId, String displayName, String email, String avatarHash, boolean isOnline, long lastSeen) {
                this.playerId = playerId;
                this.displayName = displayName;
                this.email = email;
                this.avatarHash = avatarHash;
                this.isOnline = isOnline;
                this.lastSeen = lastSeen;
            }
        }
    }

    public static class BlockUser {
        private final UUID targetId;

        public BlockUser(UUID targetId) {
            this.targetId = targetId;
        }

        public static void encode(BlockUser msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.targetId);
        }

        public static BlockUser decode(FriendlyByteBuf buf) {
            return new BlockUser(buf.readUUID());
        }

        public static void handle(BlockUser msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer actor = ctx.get().getSender();
                if (actor == null) return;
                try {
                    ServerLevel level = actor.server.getLevel(Level.OVERWORLD);
                    MessengerSavedData data = MessengerSavedData.get(level);
                    data.addBlock(actor.getUUID(), msg.targetId);
                    System.out.println("[Server] " + actor.getUUID() + " blocked " + msg.targetId);
                } catch (Exception e) {
                    System.err.println("[Server] Error handling BlockUser: " + e);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class UnblockUser {
        private final UUID targetId;

        public UnblockUser(UUID targetId) {
            this.targetId = targetId;
        }

        public static void encode(UnblockUser msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.targetId);
        }

        public static UnblockUser decode(FriendlyByteBuf buf) {
            return new UnblockUser(buf.readUUID());
        }

        public static void handle(UnblockUser msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer actor = ctx.get().getSender();
                if (actor == null) return;
                try {
                    ServerLevel level = actor.server.getLevel(Level.OVERWORLD);
                    MessengerSavedData data = MessengerSavedData.get(level);
                    data.removeBlock(actor.getUUID(), msg.targetId);
                    System.out.println("[Server] " + actor.getUUID() + " unblocked " + msg.targetId);
                } catch (Exception e) {
                    System.err.println("[Server] Error handling UnblockUser: " + e);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class BlockListUpdate {
        private final List<UUID> blocked;

        public BlockListUpdate(List<UUID> blocked) {
            this.blocked = blocked;
        }

        public static void encode(BlockListUpdate msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.blocked.size());
            for (UUID u : msg.blocked) buf.writeUUID(u);
        }

        public static BlockListUpdate decode(FriendlyByteBuf buf) {
            int size = buf.readInt();
            List<UUID> blocked = new ArrayList<>();
            for (int i = 0; i < size; i++) blocked.add(buf.readUUID());
            return new BlockListUpdate(blocked);
        }

        public static void handle(BlockListUpdate msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                try {
                    MessengerNetworkManager.getInstance().setBlockedList(msg.blocked);
                } catch (Exception ignored) {}
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class LeaveGroup {
        private final UUID groupId;

        public LeaveGroup(UUID groupId) {
            this.groupId = groupId;
        }

        public static void encode(LeaveGroup msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.groupId);
        }

        public static LeaveGroup decode(FriendlyByteBuf buf) {
            return new LeaveGroup(buf.readUUID());
        }

        public static void handle(LeaveGroup msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sender = ctx.get().getSender();
                if (sender != null) {
                    try {
                        ServerLevel level = sender.server.getLevel(Level.OVERWORLD);
                        MessengerSavedData data = MessengerSavedData.get(level);

                        MessengerSavedData.ConversationData conv = data.getConversationData(msg.groupId);
                        if (conv != null && conv.participants.contains(sender.getUUID())) {
                            conv.participants.remove(sender.getUUID());
                            data.setDirty();

                            for (UUID memberId : conv.participants) {
                                ServerPlayer memberPlayer = sender.server.getPlayerList().getPlayer(memberId);
                                if (memberPlayer != null) {
                                    List<UUID> updatedMembers = new ArrayList<>(conv.participants);
                                    NetworkHandler.sendToPlayer(new GroupCreated(conv.id, conv.name, conv.adminId, updatedMembers), memberPlayer);
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[Server] Error handling LeaveGroup: " + e);
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class ChangeGroupName {
        private final UUID groupId;
        private final String newName;

        public ChangeGroupName(UUID groupId, String newName) {
            this.groupId = groupId;
            this.newName = newName;
        }

        public static void encode(ChangeGroupName msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.groupId);
            buf.writeUtf(msg.newName);
        }

        public static ChangeGroupName decode(FriendlyByteBuf buf) {
            return new ChangeGroupName(buf.readUUID(), buf.readUtf());
        }

        public static void handle(ChangeGroupName msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sender = ctx.get().getSender();
                if (sender != null) {
                    try {
                        ServerLevel level = sender.server.getLevel(Level.OVERWORLD);
                        MessengerSavedData data = MessengerSavedData.get(level);

                        MessengerSavedData.ConversationData conv = data.getConversationData(msg.groupId);
                        if (conv != null && conv.adminId != null && conv.adminId.equals(sender.getUUID())) {
                            conv.name = msg.newName;
                            data.setDirty();

                            for (UUID member : conv.participants) {
                                if (member.equals(sender.getUUID())) continue;
                                ServerPlayer memberPlayer = sender.server.getPlayerList().getPlayer(member);
                                if (memberPlayer != null) {
                                    NetworkHandler.sendToPlayer(new GroupNameChanged(msg.groupId, msg.newName), memberPlayer);
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[Server] Error handling ChangeGroupName: " + e);
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class GroupNameChanged {
        private final UUID groupId;
        private final String newName;

        public GroupNameChanged(UUID groupId, String newName) {
            this.groupId = groupId;
            this.newName = newName;
        }

        public static void encode(GroupNameChanged msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.groupId);
            buf.writeUtf(msg.newName);
        }

        public static GroupNameChanged decode(FriendlyByteBuf buf) {
            return new GroupNameChanged(buf.readUUID(), buf.readUtf());
        }

        public static void handle(GroupNameChanged msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                MessengerNetworkManager manager = MessengerNetworkManager.getInstance();
                MessengerNetworkManager.Group group = manager.getGroup(msg.groupId);
                if (group != null) {
                    group.name = msg.newName;
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
    public static class RemoveGroupMember {
        private final UUID groupId;
        private final UUID memberId;

        public RemoveGroupMember(UUID groupId, UUID memberId) {
            this.groupId = groupId;
            this.memberId = memberId;
        }

        public static void encode(RemoveGroupMember msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.groupId);
            buf.writeUUID(msg.memberId);
        }

        public static RemoveGroupMember decode(FriendlyByteBuf buf) {
            return new RemoveGroupMember(buf.readUUID(), buf.readUUID());
        }

        public static void handle(RemoveGroupMember msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sender = ctx.get().getSender();
                if (sender != null) {
                    try {
                        ServerLevel level = sender.server.getLevel(Level.OVERWORLD);
                        MessengerSavedData data = MessengerSavedData.get(level);

                        MessengerSavedData.ConversationData conv = data.getConversationData(msg.groupId);
                        if (conv != null && conv.adminId != null && conv.adminId.equals(sender.getUUID())) {
                            conv.participants.remove(msg.memberId);
                            data.setDirty();

                            ServerPlayer removedPlayer = sender.server.getPlayerList().getPlayer(msg.memberId);
                            if (removedPlayer != null) {
                                NetworkHandler.sendToPlayer(new MemberRemovedFromGroup(msg.groupId), removedPlayer);
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[Server] Error handling RemoveGroupMember: " + e);
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class ChangeGroupDescription {
        private final UUID groupId;
        private final String newDescription;

        public ChangeGroupDescription(UUID groupId, String newDescription) {
            this.groupId = groupId;
            this.newDescription = newDescription;
        }

        public static void encode(ChangeGroupDescription msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.groupId);
            buf.writeUtf(msg.newDescription);
        }

        public static ChangeGroupDescription decode(FriendlyByteBuf buf) {
            return new ChangeGroupDescription(buf.readUUID(), buf.readUtf());
        }

        public static void handle(ChangeGroupDescription msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sender = ctx.get().getSender();
                if (sender != null) {
                    try {
                        ServerLevel level = sender.server.getLevel(Level.OVERWORLD);
                        MessengerSavedData data = MessengerSavedData.get(level);

                        MessengerSavedData.ConversationData conv = data.getConversationData(msg.groupId);
                        if (conv != null && conv.adminId != null && conv.adminId.equals(sender.getUUID())) {
                            conv.description = msg.newDescription;
                            data.setDirty();

                            for (UUID member : conv.participants) {
                                if (member.equals(sender.getUUID())) continue;
                                ServerPlayer memberPlayer = sender.server.getPlayerList().getPlayer(member);
                                if (memberPlayer != null) {
                                    NetworkHandler.sendToPlayer(new GroupDescriptionChanged(msg.groupId, msg.newDescription), memberPlayer);
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[Server] Error handling ChangeGroupDescription: " + e);
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class GroupDescriptionChanged {
        private final UUID groupId;
        private final String newDescription;

        public GroupDescriptionChanged(UUID groupId, String newDescription) {
            this.groupId = groupId;
            this.newDescription = newDescription;
        }

        public static void encode(GroupDescriptionChanged msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.groupId);
            buf.writeUtf(msg.newDescription);
        }

        public static GroupDescriptionChanged decode(FriendlyByteBuf buf) {
            return new GroupDescriptionChanged(buf.readUUID(), buf.readUtf());
        }

        public static void handle(GroupDescriptionChanged msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                MessengerNetworkManager manager = MessengerNetworkManager.getInstance();
                MessengerNetworkManager.Group group = manager.getGroup(msg.groupId);
                if (group != null) {
                    group.description = msg.newDescription;
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class MemberRemovedFromGroup {
        private final UUID groupId;

        public MemberRemovedFromGroup(UUID groupId) {
            this.groupId = groupId;
        }

        public static void encode(MemberRemovedFromGroup msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.groupId);
        }

        public static MemberRemovedFromGroup decode(FriendlyByteBuf buf) {
            return new MemberRemovedFromGroup(buf.readUUID());
        }

        public static void handle(MemberRemovedFromGroup msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                MessengerNetworkManager manager = MessengerNetworkManager.getInstance();
                manager.removeGroup(msg.groupId);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class AddMemberToGroup {
        private final UUID groupId;
        private final UUID memberId;

        public AddMemberToGroup(UUID groupId, UUID memberId) {
            this.groupId = groupId;
            this.memberId = memberId;
        }

        public static void encode(AddMemberToGroup msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.groupId);
            buf.writeUUID(msg.memberId);
        }

        public static AddMemberToGroup decode(FriendlyByteBuf buf) {
            return new AddMemberToGroup(buf.readUUID(), buf.readUUID());
        }

        public static void handle(AddMemberToGroup msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sender = ctx.get().getSender();
                if (sender != null) {
                    try {
                        ServerLevel level = sender.server.getLevel(Level.OVERWORLD);
                        MessengerSavedData data = MessengerSavedData.get(level);

                        MessengerSavedData.ConversationData conv = data.getConversationData(msg.groupId);
                        if (conv != null && conv.participants.contains(sender.getUUID())) {
                            if (!conv.participants.contains(msg.memberId)) {
                                conv.participants.add(msg.memberId);
                                data.setDirty();

                                ServerPlayer newMember = sender.server.getPlayerList().getPlayer(msg.memberId);
                                if (newMember != null) {
                                    List<UUID> updatedMembers = new ArrayList<>(conv.participants);
                                    NetworkHandler.sendToPlayer(new GroupCreated(conv.id, conv.name, conv.adminId, updatedMembers), newMember);
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[Server] Error handling AddMemberToGroup: " + e);
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}

