package net.chaoscraft.chaoscrafts_device_mod.client.app.messenger;

import net.chaoscraft.chaoscrafts_device_mod.network.NetworkHandler;
import net.chaoscraft.chaoscrafts_device_mod.network.packet.MessengerPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MessengerNetworkManager {
    private static MessengerNetworkManager instance;
    private final Map<UUID, List<Message>> conversations = new ConcurrentHashMap<>();
    private final Map<UUID, Contact> contacts = new ConcurrentHashMap<>();
    private final List<FriendRequest> incomingFriendRequests = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, UUID> emailToUuidCache = new ConcurrentHashMap<>();

    private final Set<UUID> favorites = Collections.synchronizedSet(new HashSet<>());
    private final Map<UUID, Group> groups = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> unreadCounts = new ConcurrentHashMap<>();
    private final Set<UUID> blocked = Collections.synchronizedSet(new HashSet<>());

    private MessengerNetworkManager() {}

    public static MessengerNetworkManager getInstance() {
        if (instance == null) {
            instance = new MessengerNetworkManager();
        }
        return instance;
    }

    public void sendMessage(UUID recipientId, String message) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        Message msg = new Message(
                UUID.randomUUID(),
                player.getUUID(),
                recipientId,
                message,
                System.currentTimeMillis(),
                false
        );

        addMessageToConversation(recipientId, msg);

        NetworkHandler.sendToServer(new MessengerPackets.SendMessage(recipientId, message, null));
    }

    public void sendFriendRequest(String targetEmail) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        NetworkHandler.sendToServer(new MessengerPackets.FriendRequest(targetEmail));
    }

    public void respondToFriendRequest(UUID requestId, boolean accept) {
        FriendRequest request = getFriendRequest(requestId);
        if (request == null) return;

        if (accept) {
            Contact newContact = new Contact(
                    request.fromPlayerId,
                    request.fromPlayerName,
                    request.fromPlayerName + "@rift.com",
                    "",
                    true,
                    System.currentTimeMillis()
            );
            addContact(newContact);

            addMessageToConversation(request.fromPlayerId, new Message(
                    UUID.randomUUID(), request.fromPlayerId, Minecraft.getInstance().player.getUUID(),
                    "Thanks for accepting my friend request!",
                    System.currentTimeMillis(), true
            ));
        }

        removeFriendRequest(requestId);
        NetworkHandler.sendToServer(new MessengerPackets.FriendResponse(requestId, accept));
    }

    public void addMessageToConversation(UUID conversationId, Message message) {
        conversations.computeIfAbsent(conversationId, k -> new ArrayList<>()).add(message);
        try {
            Player player = Minecraft.getInstance().player;
            if (player != null && !message.senderId.equals(player.getUUID())) {
                unreadCounts.put(conversationId, unreadCounts.getOrDefault(conversationId, 0) + 1);
            }
        } catch (Exception ignored) {}
    }

    public List<Message> getConversation(UUID conversationId) {
        return conversations.getOrDefault(conversationId, new ArrayList<>());
    }

    public void addContact(Contact contact) {
        try {
            if (contact.email != null && !contact.email.isEmpty()) {
                UUID tempId = UUID.nameUUIDFromBytes(contact.email.getBytes());
                if (!conversations.containsKey(contact.playerId) && conversations.containsKey(tempId)) {
                    List<Message> temp = conversations.remove(tempId);
                    if (temp != null) {
                        List<Message> target = conversations.computeIfAbsent(contact.playerId, k -> new ArrayList<>());
                        target.addAll(0, temp);
                    }
                }
            }
        } catch (Exception ignored) {}

        contacts.put(contact.playerId, contact);
    }

    public void removeContact(UUID playerId) {
        contacts.remove(playerId);
        favorites.remove(playerId);
        unreadCounts.remove(playerId);
    }

    public Contact getContact(UUID playerId) {
        return contacts.get(playerId);
    }

    public Collection<Contact> getContacts() {
        return contacts.values();
    }

    public void addFriendRequest(FriendRequest request) {
        incomingFriendRequests.add(request);
    }

    public void removeFriendRequest(UUID requestId) {
        incomingFriendRequests.removeIf(req -> req.requestId.equals(requestId));
    }

    public FriendRequest getFriendRequest(UUID requestId) {
        return incomingFriendRequests.stream()
                .filter(req -> req.requestId.equals(requestId))
                .findFirst()
                .orElse(null);
    }

    public List<FriendRequest> getPendingFriendRequests() {
        return new ArrayList<>(incomingFriendRequests);
    }

    public void toggleFavorite(UUID id) {
        if (favorites.contains(id)) favorites.remove(id);
        else favorites.add(id);
    }

    public boolean isFavorite(UUID id) {
        return favorites.contains(id);
    }

    public Collection<UUID> getFavorites() {
        return new ArrayList<>(favorites);
    }

    public static class Group {
        public final UUID groupId;
        public String name;
        public final List<UUID> members;
        public UUID adminId;
        public String description;

        public Group(UUID groupId, String name, List<UUID> members) {
            this(groupId, name, members, null, "");
        }

        public Group(UUID groupId, String name, List<UUID> members, UUID adminId, String description) {
            this.groupId = groupId;
            this.name = name;
            this.members = members;
            this.adminId = adminId;
            this.description = description;
        }
    }

    public UUID createGroup(String name, List<UUID> memberIds) {
        try {
            NetworkHandler.sendToServer(new MessengerPackets.CreateGroup(name, memberIds));
        } catch (Exception ignored) {}
        return null;
    }

    public UUID createGroup(String name, List<UUID> memberIds, UUID adminId, String description) {
        try {
            NetworkHandler.sendToServer(new MessengerPackets.CreateGroup(name, memberIds));
        } catch (Exception ignored) {}
        return null;
    }

    public Collection<Group> getGroups() {
        return groups.values();
    }

    public Group getGroup(UUID groupId) {
        return groups.get(groupId);
    }

    public void upsertGroup(Group g) {
        if (g == null || g.groupId == null) return;
        groups.put(g.groupId, g);
        conversations.computeIfAbsent(g.groupId, k -> new ArrayList<>());
    }

    public boolean addMemberToGroup(UUID groupId, UUID actorId, UUID newMemberId) {
        Group g = groups.get(groupId);
        if (g == null) return false;
        if (!g.members.contains(newMemberId)) {
            g.members.add(newMemberId);

            conversations.computeIfAbsent(groupId, k -> new ArrayList<>());

            try {
                NetworkHandler.sendToServer(new MessengerPackets.AddMemberToGroup(groupId, newMemberId));
            } catch (Exception ignored) {}

            return true;
        }
        return false;
    }

    public boolean leaveGroup(UUID groupId, UUID memberId) {
        Group g = groups.get(groupId);
        if (g == null) return false;

        boolean removed = g.members.remove(memberId);
        if (removed) {
            conversations.remove(groupId);
            unreadCounts.remove(groupId);

            groups.remove(groupId);

            try {
                NetworkHandler.sendToServer(new MessengerPackets.LeaveGroup(groupId));
            } catch (Exception ignored) {}
        }
        return removed;
    }

    public boolean isGroupAdmin(UUID groupId, UUID playerId) {
        Group g = groups.get(groupId);
        if (g == null) return false;
        return g.adminId != null && g.adminId.equals(playerId);
    }

    public void removeGroup(UUID groupId) {
        if (groupId == null) return;
        groups.remove(groupId);
        conversations.remove(groupId);
        unreadCounts.remove(groupId);
    }

    public void blockContact(UUID id) {
        if (id == null) return;
        blocked.add(id);
        try {
            NetworkHandler.sendToServer(new MessengerPackets.BlockUser(id));
        } catch (Exception ignored) {}
    }

    public void unblockContact(UUID id) {
        if (id == null) return;
        blocked.remove(id);
        try {
            NetworkHandler.sendToServer(new MessengerPackets.UnblockUser(id));
        } catch (Exception ignored) {}
    }

    public boolean isBlocked(UUID id) {
        return blocked.contains(id);
    }

    public void setBlockedList(List<UUID> list) {
        blocked.clear();
        if (list != null) blocked.addAll(list);
    }

    public List<UUID> getBlockedIds() {
        return new ArrayList<>(blocked);
    }

    public int getUnreadCount(UUID conversationId) {
        return unreadCounts.getOrDefault(conversationId, 0);
    }

    public int getTotalUnreadCount() {
        return unreadCounts.values().stream().mapToInt(Integer::intValue).sum();
    }

    public void markConversationRead(UUID conversationId) {
        List<Message> msgs = conversations.get(conversationId);
        if (msgs != null) {
            for (Message m : msgs) m.isRead = true;
        }
        unreadCounts.remove(conversationId);
    }

    public void markMessageRead(UUID conversationId, UUID messageId) {
        List<Message> msgs = conversations.get(conversationId);
        if (msgs == null) return;
        for (Message m : msgs) {
            if (m.messageId.equals(messageId)) {
                if (!m.isRead) {
                    m.isRead = true;
                    int cur = unreadCounts.getOrDefault(conversationId, 0);
                    if (cur <= 1) unreadCounts.remove(conversationId); else unreadCounts.put(conversationId, cur - 1);
                }
                break;
            }
        }
    }

    public int getPendingFriendRequestCount() {
        return incomingFriendRequests.size();
    }

    public void initializeDemoContacts() {
        UUID aliceId = UUID.nameUUIDFromBytes("alice@rift.com".getBytes());
        UUID bobId = UUID.nameUUIDFromBytes("bob@rift.com".getBytes());
        UUID charlieId = UUID.nameUUIDFromBytes("charlie@rift.com".getBytes());

        addContact(new Contact(aliceId, "Alice", "alice@rift.com", "", true, System.currentTimeMillis(), "Hello! I'm Alice."));
        addContact(new Contact(bobId, "Bob", "bob@rift.com", "", false, System.currentTimeMillis() - 3600000, "Bob here. Nice to meet you!"));
        addContact(new Contact(charlieId, "Charlie", "charlie@rift.com", "", true, System.currentTimeMillis() - 7200000, "Multiline\ndescription\nfor testing"));

        Player player = Minecraft.getInstance().player;
        if (player != null) {
            addMessageToConversation(aliceId, new Message(
                    UUID.randomUUID(), aliceId, player.getUUID(), "Hey there! How are you?",
                    System.currentTimeMillis() - 300000, true
            ));
            addMessageToConversation(bobId, new Message(
                    UUID.randomUUID(), bobId, player.getUUID(), "Don't forget the meeting tomorrow",
                    System.currentTimeMillis() - 1800000, true
            ));
        }
    }

    public static class Message {
        public final UUID messageId;
        public final UUID senderId;
        public final UUID recipientId;
        public final String content;
        public final long timestamp;
        public boolean isRead;

        public Message(UUID messageId, UUID senderId, UUID recipientId, String content, long timestamp, boolean isRead) {
            this.messageId = messageId;
            this.senderId = senderId;
            this.recipientId = recipientId;
            this.content = content;
            this.timestamp = timestamp;
            this.isRead = isRead;
        }

        public boolean isFromMe() {
            Player player = Minecraft.getInstance().player;
            return player != null && senderId.equals(player.getUUID());
        }
    }

    public static class Contact {
        public final UUID playerId;
        public final String displayName;
        public final String email;
        public final String avatarHash;
        public final boolean isOnline;
        public final long lastSeen;
        public String description;

        public Contact(UUID playerId, String displayName, String email, String avatarHash, boolean isOnline, long lastSeen) {
            this(playerId, displayName, email, avatarHash, isOnline, lastSeen, "");
        }

        public Contact(UUID playerId, String displayName, String email, String avatarHash, boolean isOnline, long lastSeen, String description) {
            this.playerId = playerId;
            this.displayName = displayName;
            this.email = email;
            this.avatarHash = avatarHash;
            this.isOnline = isOnline;
            this.lastSeen = lastSeen;
            this.description = description == null ? "" : description;
        }
    }

    public static class FriendRequest {
        public final UUID requestId;
        public final UUID fromPlayerId;
        public final String fromPlayerName;
        public final long timestamp;

        public FriendRequest(UUID requestId, UUID fromPlayerId, String fromPlayerName, long timestamp) {
            this.requestId = requestId;
            this.fromPlayerId = fromPlayerId;
            this.fromPlayerName = fromPlayerName;
            this.timestamp = timestamp;
        }
    }

    public boolean changeGroupName(UUID groupId, UUID actorId, String newName) {
        Group g = groups.get(groupId);
        if (g == null) return false;
        if (g.adminId != null && g.adminId.equals(actorId)) {
            g.name = newName;

            try {
                NetworkHandler.sendToServer(new MessengerPackets.ChangeGroupName(groupId, newName));
            } catch (Exception ignored) {}
            return true;
        }
        return false;
    }

    public boolean changeGroupDescription(UUID groupId, UUID actorId, String newDescription) {
        Group g = groups.get(groupId);
        if (g == null) return false;
        if (g.adminId != null && g.adminId.equals(actorId)) {
            g.description = newDescription;
            try {
                NetworkHandler.sendToServer(new MessengerPackets.ChangeGroupDescription(groupId, newDescription));
            } catch (Exception ignored) {}
            return true;
        }
        return false;
    }
}
