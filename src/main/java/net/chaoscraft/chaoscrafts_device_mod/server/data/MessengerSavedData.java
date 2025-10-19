package net.chaoscraft.chaoscrafts_device_mod.server.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public class MessengerSavedData extends SavedData {
    private final Map<UUID, PlayerProfile> profiles = new HashMap<>();
    private final Map<UUID, ConversationData> conversations = new HashMap<>();
    private final Map<UUID, Set<UUID>> friendRelations = new HashMap<>();
    private final Map<String, UUID> emailToUuid = new HashMap<>();
    private final Map<UUID, List<PendingFriendRequest>> pendingFriendRequests = new HashMap<>();
    private final Map<UUID, List<Message>> pendingMessages = new HashMap<>();
    private final Map<UUID, Set<UUID>> blockedRelations = new HashMap<>();

    public static MessengerSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(MessengerSavedData::load, MessengerSavedData::new, "messenger_data");
    }

    public PlayerProfile getProfile(UUID playerId) {
        return profiles.computeIfAbsent(playerId, k -> new PlayerProfile(playerId));
    }

    public void registerAccount(UUID playerId, String displayName, String email, String avatarHash) {
        if (email != null && !email.isEmpty()) {
            emailToUuid.put(email.toLowerCase(Locale.ROOT), playerId);
        }
        PlayerProfile p = getProfile(playerId);
        if (displayName != null) p.displayName = displayName;
        if (avatarHash != null) p.avatarHash = avatarHash;
        p.email = email;
        p.lastSeen = System.currentTimeMillis();
        setDirty();
    }

    public Optional<UUID> resolveEmail(String email) {
        if (email == null) return Optional.empty();
        return Optional.ofNullable(emailToUuid.get(email.toLowerCase(Locale.ROOT)));
    }

    public void addPendingFriendRequest(UUID targetId, PendingFriendRequest req) {
        pendingFriendRequests.computeIfAbsent(targetId, k -> new ArrayList<>()).add(req);
        setDirty();
    }

    public List<PendingFriendRequest> getPendingFriendRequests(UUID targetId) {
        return pendingFriendRequests.getOrDefault(targetId, Collections.emptyList());
    }

    public List<PendingFriendRequest> popPendingFriendRequests(UUID targetId) {
        List<PendingFriendRequest> list = pendingFriendRequests.remove(targetId);
        if (list == null) return Collections.emptyList();
        setDirty();
        return list;
    }

    public Optional<PendingFriendRequest> removePendingRequestById(UUID requestId) {
        for (Iterator<Map.Entry<UUID, List<PendingFriendRequest>>> it = pendingFriendRequests.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, List<PendingFriendRequest>> e = it.next();
            List<PendingFriendRequest> list = e.getValue();
            for (Iterator<PendingFriendRequest> it2 = list.iterator(); it2.hasNext(); ) {
                PendingFriendRequest r = it2.next();
                if (r.requestId.equals(requestId)) {
                    it2.remove();
                    if (list.isEmpty()) it.remove();
                    setDirty();
                    return Optional.of(r);
                }
            }
        }
        return Optional.empty();
    }

    public void addFriendRelation(UUID a, UUID b) {
        friendRelations.computeIfAbsent(a, k -> new HashSet<>()).add(b);
        friendRelations.computeIfAbsent(b, k -> new HashSet<>()).add(a);
        setDirty();
    }

    public Set<UUID> getFriends(UUID playerId) {
        return friendRelations.getOrDefault(playerId, Collections.emptySet());
    }

    public void addPendingMessage(UUID targetId, Message msg) {
        pendingMessages.computeIfAbsent(targetId, k -> new ArrayList<>()).add(msg);
        setDirty();
    }

    public List<Message> getPendingMessages(UUID targetId) {
        return pendingMessages.getOrDefault(targetId, Collections.emptyList());
    }

    public List<Message> popPendingMessages(UUID targetId) {
        List<Message> list = pendingMessages.remove(targetId);
        if (list == null) return Collections.emptyList();
        setDirty();
        return list;
    }

    public Optional<Message> removePendingMessageById(UUID messageId) {
        for (Iterator<Map.Entry<UUID, List<Message>>> it = pendingMessages.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, List<Message>> e = it.next();
            List<Message> list = e.getValue();
            for (Iterator<Message> it2 = list.iterator(); it2.hasNext(); ) {
                Message m = it2.next();
                if (m.id.equals(messageId)) {
                    it2.remove();
                    if (list.isEmpty()) it.remove();
                    setDirty();
                    return Optional.of(m);
                }
            }
        }
        return Optional.empty();
    }

    public void addBlock(UUID blocker, UUID blockedId) {
        if (blocker == null || blockedId == null) return;
        blockedRelations.computeIfAbsent(blocker, k -> new HashSet<>()).add(blockedId);
        setDirty();
    }

    public void removeBlock(UUID blocker, UUID blockedId) {
        Set<UUID> set = blockedRelations.get(blocker);
        if (set != null) {
            set.remove(blockedId);
            if (set.isEmpty()) blockedRelations.remove(blocker);
            setDirty();
        }
    }

    public boolean isBlocked(UUID blocker, UUID possibleBlocked) {
        return blockedRelations.getOrDefault(blocker, Collections.emptySet()).contains(possibleBlocked);
    }

    public Set<UUID> getBlocked(UUID blocker) {
        return blockedRelations.getOrDefault(blocker, Collections.emptySet());
    }

    public ConversationData getConversationData(UUID id) {
        return conversations.get(id);
    }

    public void putConversationData(UUID id, ConversationData conv) {
        if (id == null || conv == null) return;
        conversations.put(id, conv);
        setDirty();
    }

    public Collection<ConversationData> getAllConversations() {
        return conversations.values();
    }

    public void addMessageToConversation(UUID convId, Message m) {
        conversations.computeIfAbsent(convId, ConversationData::new).messages.add(m);
        setDirty();
    }

    public static MessengerSavedData load(CompoundTag tag) {
        MessengerSavedData data = new MessengerSavedData();

        if (tag.contains("profiles", Tag.TAG_LIST)) {
            ListTag list = tag.getList("profiles", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag ptag = list.getCompound(i);
                try {
                    UUID id = UUID.fromString(ptag.getString("id"));
                    PlayerProfile p = new PlayerProfile(id);
                    p.displayName = ptag.getString("displayName");
                    p.status = ptag.getString("status");
                    p.avatarHash = ptag.getString("avatarHash");
                    p.lastSeen = ptag.getLong("lastSeen");
                    p.email = ptag.contains("email") ? ptag.getString("email") : null;
                    data.profiles.put(id, p);
                    if (p.email != null && !p.email.isEmpty()) data.emailToUuid.put(p.email.toLowerCase(Locale.ROOT), id);
                } catch (Exception ignored) {}
            }
        }

        if (tag.contains("friendRelations", Tag.TAG_LIST)) {
            ListTag frList = tag.getList("friendRelations", Tag.TAG_COMPOUND);
            for (int i = 0; i < frList.size(); i++) {
                CompoundTag entry = frList.getCompound(i);
                try {
                    UUID id = UUID.fromString(entry.getString("id"));
                    ListTag friends = entry.getList("friends", Tag.TAG_STRING);
                    Set<UUID> set = new HashSet<>();
                    for (int j = 0; j < friends.size(); j++) {
                        set.add(UUID.fromString(friends.getString(j)));
                    }
                    data.friendRelations.put(id, set);
                } catch (Exception ignored) {}
            }
        }

        if (tag.contains("blockedRelations", Tag.TAG_LIST)) {
            ListTag blList = tag.getList("blockedRelations", Tag.TAG_COMPOUND);
            for (int i = 0; i < blList.size(); i++) {
                CompoundTag entry = blList.getCompound(i);
                try {
                    UUID id = UUID.fromString(entry.getString("id"));
                    ListTag blocked = entry.getList("blocked", Tag.TAG_STRING);
                    Set<UUID> set = new HashSet<>();
                    for (int j = 0; j < blocked.size(); j++) {
                        set.add(UUID.fromString(blocked.getString(j)));
                    }
                    data.blockedRelations.put(id, set);
                } catch (Exception ignored) {}
            }
        }

        if (tag.contains("pendingFriendRequests", Tag.TAG_LIST)) {
            ListTag pfList = tag.getList("pendingFriendRequests", Tag.TAG_COMPOUND);
            for (int i = 0; i < pfList.size(); i++) {
                CompoundTag entry = pfList.getCompound(i);
                try {
                    UUID target = UUID.fromString(entry.getString("target"));
                    UUID requestId = UUID.fromString(entry.getString("requestId"));
                    UUID from = UUID.fromString(entry.getString("from"));
                    String fromName = entry.getString("fromName");
                    long ts = entry.getLong("timestamp");
                    PendingFriendRequest r = new PendingFriendRequest(requestId, from, fromName, ts);
                    data.pendingFriendRequests.computeIfAbsent(target, k -> new ArrayList<>()).add(r);
                } catch (Exception ignored) {}
            }
        }

        if (tag.contains("pendingMessages", Tag.TAG_LIST)) {
            ListTag pmList = tag.getList("pendingMessages", Tag.TAG_COMPOUND);
            for (int i = 0; i < pmList.size(); i++) {
                CompoundTag entry = pmList.getCompound(i);
                try {
                    UUID target = UUID.fromString(entry.getString("target"));
                    UUID msgId = UUID.fromString(entry.getString("id"));
                    UUID sender = UUID.fromString(entry.getString("sender"));
                    String content = entry.getString("content");
                    long ts = entry.getLong("timestamp");
                    UUID conv = entry.contains("conversation") ? UUID.fromString(entry.getString("conversation")) : target;
                    Message m = new Message(msgId, sender, content, ts, conv);
                    data.pendingMessages.computeIfAbsent(target, k -> new ArrayList<>()).add(m);
                } catch (Exception ignored) {}
            }
        }

        if (tag.contains("conversations", Tag.TAG_LIST)) {
            ListTag convList = tag.getList("conversations", Tag.TAG_COMPOUND);
            for (int i = 0; i < convList.size(); i++) {
                CompoundTag convTag = convList.getCompound(i);
                try {
                    UUID id = UUID.fromString(convTag.getString("id"));
                    ConversationData conv = new ConversationData(id);
                    conv.name = convTag.getString("name");
                    conv.isGroup = convTag.getBoolean("isGroup");

                    if (convTag.hasUUID("adminId")) {
                        conv.adminId = convTag.getUUID("adminId");
                    }

                    if (convTag.contains("description")) {
                        conv.description = convTag.getString("description");
                    }

                    ListTag partList = convTag.getList("participants", Tag.TAG_STRING);
                    for (int j = 0; j < partList.size(); j++) {
                        conv.participants.add(UUID.fromString(partList.getString(j)));
                    }

                    ListTag msgList = convTag.getList("messages", Tag.TAG_COMPOUND);
                    for (int j = 0; j < msgList.size(); j++) {
                        CompoundTag msgTag = msgList.getCompound(j);
                        UUID msgId = UUID.fromString(msgTag.getString("id"));
                        UUID sender = UUID.fromString(msgTag.getString("sender"));
                        String content = msgTag.getString("content");
                        long timestamp = msgTag.getLong("timestamp");
                        UUID convId = msgTag.contains("conversation") ? UUID.fromString(msgTag.getString("conversation")) : id;
                        Message msg = new Message(msgId, sender, content, timestamp, convId);
                        conv.messages.add(msg);
                    }

                    data.conversations.put(id, conv);
                } catch (Exception ignored) {}
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag profilesList = new ListTag();
        for (PlayerProfile p : profiles.values()) {
            CompoundTag ptag = new CompoundTag();
            ptag.putString("id", p.playerId.toString());
            if (p.displayName != null) ptag.putString("displayName", p.displayName);
            if (p.status != null) ptag.putString("status", p.status);
            if (p.avatarHash != null) ptag.putString("avatarHash", p.avatarHash);
            ptag.putLong("lastSeen", p.lastSeen);
            if (p.email != null) ptag.putString("email", p.email);
            profilesList.add(ptag);
        }
        tag.put("profiles", profilesList);

        ListTag frList = new ListTag();
        for (Map.Entry<UUID, Set<UUID>> e : friendRelations.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", e.getKey().toString());
            ListTag friends = new ListTag();
            for (UUID f : e.getValue()) friends.add(StringTag.valueOf(f.toString()));
            entry.put("friends", friends);
            frList.add(entry);
        }
        tag.put("friendRelations", frList);

        ListTag blList = new ListTag();
        for (Map.Entry<UUID, Set<UUID>> e : blockedRelations.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", e.getKey().toString());
            ListTag blocked = new ListTag();
            for (UUID b : e.getValue()) blocked.add(StringTag.valueOf(b.toString()));
            entry.put("blocked", blocked);
            blList.add(entry);
        }
        tag.put("blockedRelations", blList);

        ListTag pfList = new ListTag();
        for (Map.Entry<UUID, List<PendingFriendRequest>> e : pendingFriendRequests.entrySet()) {
            UUID target = e.getKey();
            for (PendingFriendRequest r : e.getValue()) {
                CompoundTag entry = new CompoundTag();
                entry.putString("target", target.toString());
                entry.putString("requestId", r.requestId.toString());
                entry.putString("from", r.fromPlayerId.toString());
                entry.putString("fromName", r.fromPlayerName);
                entry.putLong("timestamp", r.timestamp);
                pfList.add(entry);
            }
        }
        tag.put("pendingFriendRequests", pfList);

        ListTag pmList = new ListTag();
        for (Map.Entry<UUID, List<Message>> e : pendingMessages.entrySet()) {
            UUID target = e.getKey();
            for (Message m : e.getValue()) {
                CompoundTag entry = new CompoundTag();
                entry.putString("target", target.toString());
                entry.putString("id", m.id.toString());
                entry.putString("sender", m.sender.toString());
                entry.putString("content", m.content);
                entry.putLong("timestamp", m.timestamp);
                if (m.conversationId != null) entry.putString("conversation", m.conversationId.toString());
                pmList.add(entry);
            }
        }

        ListTag convList = new ListTag();
        for (ConversationData conv : conversations.values()) {
            CompoundTag convTag = new CompoundTag();
            convTag.putString("id", conv.id.toString());
            convTag.putString("name", conv.name == null ? "" : conv.name);
            convTag.putBoolean("isGroup", conv.isGroup);

            if (conv.adminId != null) {
                convTag.putUUID("adminId", conv.adminId);
            }

            convTag.putString("description", conv.description == null ? "" : conv.description);

            ListTag partList = new ListTag();
            for (UUID participant : conv.participants) {
                partList.add(StringTag.valueOf(participant.toString()));
            }
            convTag.put("participants", partList);

            ListTag msgList = new ListTag();
            for (Message msg : conv.messages) {
                CompoundTag msgTag = new CompoundTag();
                msgTag.putString("id", msg.id.toString());
                msgTag.putString("sender", msg.sender.toString());
                msgTag.putString("content", msg.content);
                msgTag.putLong("timestamp", msg.timestamp);
                if (msg.conversationId != null) {
                    msgTag.putString("conversation", msg.conversationId.toString());
                }
                msgList.add(msgTag);
            }
            convTag.put("messages", msgList);

            convList.add(convTag);
        }

        tag.put("conversations", convList);

        return tag;
    }

    public static class PlayerProfile {
        public final UUID playerId;
        public String displayName;
        public String status = "";
        public String avatarHash = "";
        public long lastSeen;
        public String email = null;

        public PlayerProfile(UUID playerId) {
            this.playerId = playerId;
        }
    }

    public static class ConversationData {
        public final UUID id;
        public String name;
        public final Set<UUID> participants = new HashSet<>();
        public final List<Message> messages = new ArrayList<>();
        public boolean isGroup;
        public UUID adminId;
        public String description = "";

        public ConversationData(UUID id) {
            this.id = id;
        }
    }

    public static class Message {
        public final UUID id;
        public final UUID sender;
        public final String content;
        public final long timestamp;
        public final UUID conversationId;
        public UUID replyTo;

        public Message(UUID id, UUID sender, String content, long timestamp) {
            this(id, sender, content, timestamp, null);
        }

        public Message(UUID id, UUID sender, String content, long timestamp, UUID conversationId) {
            this.id = id;
            this.sender = sender;
            this.content = content;
            this.timestamp = timestamp;
            this.conversationId = conversationId;
        }
    }

    public static class PendingFriendRequest {
        public final UUID requestId;
        public final UUID fromPlayerId;
        public final String fromPlayerName;
        public final long timestamp;

        public PendingFriendRequest(UUID requestId, UUID fromPlayerId, String fromPlayerName, long timestamp) {
            this.requestId = requestId;
            this.fromPlayerId = fromPlayerId;
            this.fromPlayerName = fromPlayerName;
            this.timestamp = timestamp;
        }
    }
}
