package net.chaoscraft.chaoscrafts_device_mod.client.app.messenger;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.chaoscraft.chaoscrafts_device_mod.client.AccountManager;
import net.chaoscraft.chaoscrafts_device_mod.client.app.IApp;
import net.chaoscraft.chaoscrafts_device_mod.client.avatar.AvatarHelper;
import net.chaoscraft.chaoscrafts_device_mod.client.screen.DraggableWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MessengerApp implements IApp {
    private DraggableWindow window;
    private MessengerNetworkManager networkManager;

    private boolean initialized = false;

    private Screen currentScreen = Screen.CHAT_LIST;
    private UUID currentConversationId = null;
    private UUID selectedConversationId = null;
    private String currentFilter = "All";
    private String searchQuery = "";

    private long lastClickTime = 0L;
    private UUID lastClickItem = null;

    private EditBox messageInput;
    private EditBox searchInput;
    private EditBox friendEmailInput;
    private EditBox focusedEditBox = null;
    private EditBox profileDescInput;

    private EditBox groupNameInput;
    private EditBox groupDescriptionInput;
    private final Set<UUID> groupCreationSelected = Collections.synchronizedSet(new HashSet<>());
    private UUID editingGroupId = null;

    private ChatContextMenu chatContextMenu = null;
    private UUID contextTargetId = null;
    private boolean contextTargetIsGroup = false;

    private int leftPanelWidth = 320;
    private int rightPanelWidth = 280;
    private boolean showRightPanel = true;

    private final Map<UUID, Boolean> lastKnownReadState = new ConcurrentHashMap<>();
    private final Map<UUID, Long> readAnimStart = new ConcurrentHashMap<>();

    private final Map<UUID, int[]> messageBounds = new ConcurrentHashMap<>();

    private UUID profileViewingId = null;

    private int profileScrollOffset = 0;
    private int chatListScrollOffset = 0;
    private int contactsScrollOffset = 0;
    private int friendRequestsScrollOffset = 0;
    private int groupCreationScrollOffset = 0;
    private int addToGroupScrollOffset = 0;
    private int conversationScrollOffset = 0;

    private int currentMaxConversationScroll = 1000;

    private UUID lastMessageSender = null;
    private long lastMessageTime = 0;

    private final Map<UUID, List<UUID>> messageGroups = new ConcurrentHashMap<>();

    private final Map<UUID, String> groupDescriptionCache = new ConcurrentHashMap<>();

    private static final Map<String, ResourceLocation> webAvatarTextureCache = new ConcurrentHashMap<>();

    public static void clearWebAvatarCacheFor(UUID playerId) {
        if (playerId == null) return;
        String prefix = playerId.toString() + "_";
        try {
            List<String> keys = new ArrayList<>(webAvatarTextureCache.keySet());
            for (String k : keys) {
                if (k.startsWith(prefix)) {
                    ResourceLocation rl = webAvatarTextureCache.remove(k);
                    if (rl != null) {
                        try { Minecraft.getInstance().getTextureManager().release(rl); } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public static void clearAllWebAvatarCache() {
        try {
            List<String> keys = new ArrayList<>(webAvatarTextureCache.keySet());
            for (String k : keys) {
                ResourceLocation rl = webAvatarTextureCache.remove(k);
                if (rl != null) {
                    try { Minecraft.getInstance().getTextureManager().release(rl); } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }

    enum Screen {
        CHAT_LIST, CONVERSATION, CONTACTS, FRIEND_REQUESTS, PROFILE, GROUP_CREATION, ADD_TO_GROUP, GROUP_SETTINGS
    }

    private static class DisplayItem {
        final UUID id;
        final String title;
        final boolean isGroup;

        DisplayItem(UUID id, String title, boolean isGroup) {
            this.id = id;
            this.title = title == null ? "" : title;
            this.isGroup = isGroup;
        }
    }

    private ResourceLocation getOrCreateAvatarTexture(UUID playerId, int size) {
        try {
            ResourceLocation webAvatar = fetchWebAvatar(playerId, size);
            if (webAvatar != null) {
                return webAvatar;
            }

            ResourceLocation skin = getPlayerSkinTexture(playerId);
            if (skin != null) {
                return AvatarHelper.getAvatarTexture(playerId, size);
            }

            return getDefaultAvatarTexture(size);

        } catch (Exception e) {
            if (Minecraft.getInstance().options.renderDebug) {
                System.out.println("MessengerApp: Failed to get avatar texture for " + playerId + ": " + e);
            }
            return getDefaultAvatarTexture(size);
        }
    }



    private ResourceLocation fetchWebAvatar(UUID playerId, int size) {
        if (playerId == null) return null;
        String key = playerId.toString() + "_" + size;

        try {
            ResourceLocation cached = webAvatarTextureCache.get(key);
            if (cached != null) return cached;
        } catch (Exception ignored) {}

        NativeImage webImage = fetchSkinFromWeb(playerId, size);

        if (webImage != null) {
            try {
                NativeImage circularImage = createCircularAvatar(webImage, size);
                try { webImage.close(); } catch (Exception ignored) {}

                DynamicTexture dynamicTexture = new DynamicTexture(circularImage);
                ResourceLocation textureLocation = ResourceLocation.fromNamespaceAndPath("chaoscrafts_device_mod", "web_avatar/" + playerId.toString() + "_" + size);
                Minecraft.getInstance().getTextureManager().register(textureLocation, dynamicTexture);

                webAvatarTextureCache.put(key, textureLocation);

                if (Minecraft.getInstance().options.renderDebug) {
                    System.out.println("MessengerApp: Successfully loaded and circularized web avatar for " + playerId);
                }
                return textureLocation;
            } catch (Exception e) {
                try { webImage.close(); } catch (Exception ignored) {}
                if (Minecraft.getInstance().options.renderDebug) {
                    System.out.println("MessengerApp: Failed to create circular texture from web image for " + playerId + ": " + e);
                }
            }
        }

        return null;
    }

    private NativeImage createCircularAvatar(NativeImage srcImage, int size) {
        NativeImage dest = new NativeImage(size, size, true);

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                dest.setPixelRGBA(x, y, 0);
            }
        }

        int radius = size / 2;
        float center = radius - 0.5f;

        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                float dx = px - center;
                float dy = py - center;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);

                if (distance <= radius) {
                    int srcX = (int) ((px / (float) size) * srcImage.getWidth());
                    int srcY = (int) ((py / (float) size) * srcImage.getHeight());

                    srcX = Math.max(0, Math.min(srcImage.getWidth() - 1, srcX));
                    srcY = Math.max(0, Math.min(srcImage.getHeight() - 1, srcY));

                    int pixelColor = srcImage.getPixelRGBA(srcX, srcY);
                    dest.setPixelRGBA(px, py, pixelColor);
                }
            }
        }

        return dest;
    }


    private NativeImage fetchSkinFromWeb(UUID playerId, int size) {
        NativeImage image = fetchSkinFromCrafatar(playerId);
        if (image != null) {
            if (Minecraft.getInstance().options.renderDebug) {
                System.out.println("MessengerApp: Got avatar from Crafatar for " + playerId);
            }
            return image;
        }

        String username = getUsernameFromUUID(playerId);
        if (username != null) {
            image = fetchSkinFromMinotar(username, size);
            if (image != null) {
                if (Minecraft.getInstance().options.renderDebug) {
                    System.out.println("MessengerApp: Got avatar from Minotar for " + username);
                }
                return image;
            }
        }

        if (Minecraft.getInstance().options.renderDebug) {
            System.out.println("MessengerApp: No web avatar found for " + playerId);
        }
        return null;
    }

    private NativeImage fetchSkinFromCrafatar(UUID playerUUID) {
        if (playerUUID == null) return null;

        String urlStr = "https://crafatar.com/avatars/" + playerUUID.toString().replace("-", "") + "?size=" + 64 + "&overlay";

        if (Minecraft.getInstance().options.renderDebug) {
            System.out.println("MessengerApp: Trying Crafatar URL: " + urlStr);
        }

        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(5000);
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code >= 200 && code < 400) {
                try (java.io.InputStream in = conn.getInputStream()) {
                    NativeImage ni = NativeImage.read(in);
                    if (Minecraft.getInstance().options.renderDebug) System.out.println("MessengerApp: fetched avatar from Crafatar for " + playerUUID);
                    return ni;
                }
            } else {
                if (Minecraft.getInstance().options.renderDebug) System.out.println("MessengerApp: Crafatar returned code " + code + " for " + playerUUID);
            }
        } catch (Throwable e) {
            if (Minecraft.getInstance().options.renderDebug) System.out.println("MessengerApp: crafatar fetch failed for " + playerUUID + " -> " + e);
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private NativeImage fetchSkinFromMinotar(String username, int size) {
        if (username == null || username.isEmpty()) return null;

        String urlStr = "https://minotar.net/helm/" + username + "/" + Math.max(64, Math.min(512, size));

        if (Minecraft.getInstance().options.renderDebug) {
            System.out.println("MessengerApp: Trying Minotar URL: " + urlStr);
        }

        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(5000);
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code >= 200 && code < 400) {
                try (java.io.InputStream in = conn.getInputStream()) {
                    NativeImage ni = NativeImage.read(in);
                    if (Minecraft.getInstance().options.renderDebug) System.out.println("MessengerApp: fetched avatar from Minotar for " + username);
                    return ni;
                }
            } else {
                if (Minecraft.getInstance().options.renderDebug) System.out.println("MessengerApp: Minotar returned code " + code + " for " + username);
            }
        } catch (Throwable e) {
            if (Minecraft.getInstance().options.renderDebug) System.out.println("MessengerApp: minotar fetch failed for " + username + " -> " + e);
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private ResourceLocation getPlayerSkinTexture(UUID playerId) {
        try {
            Minecraft mc = Minecraft.getInstance();

            if (mc.player != null && mc.player.getUUID().equals(playerId)) {
                return mc.player.getSkinTextureLocation();
            }

            if (mc.level != null) {
                Player player = mc.level.getPlayerByUUID(playerId);
                if (player instanceof AbstractClientPlayer) {
                    return ((AbstractClientPlayer) player).getSkinTextureLocation();
                }
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private ResourceLocation createAvatarTexture(ResourceLocation skin, UUID playerId, int size) {
        return AvatarHelper.getAvatarTexture(playerId, size);
    }

    private String getUsernameFromUUID(UUID playerId) {
        MessengerNetworkManager.Contact contact = networkManager.getContact(playerId);
        if (contact != null && contact.displayName != null) {
            return contact.displayName;
        }

        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.getUUID().equals(playerId)) {
            return Minecraft.getInstance().player.getGameProfile().getName();
        }

        return null;
    }

    private ResourceLocation getDefaultAvatarTexture(int size) {
        return AvatarHelper.getDefaultAvatar(size);
    }

    private ResourceLocation getCurrentPlayerAvatar(int size) {
        try {
            if (Minecraft.getInstance().player != null) {
                UUID playerId = Minecraft.getInstance().player.getUUID();
                return getOrCreateAvatarTexture(playerId, size);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return getDefaultAvatarTexture(size);
    }

    private int[] getContentRect(DraggableWindow window) {
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8;
        int cy = r[1] + 32;
        int cw = r[2] - 16;
        int ch = r[3] - 40;
        return new int[]{cx, cy, cw, ch};
    }

    @Override
    public boolean charTyped(DraggableWindow window, char codePoint, int modifiers) {
        if (focusedEditBox != null) {
            try {
                return focusedEditBox.charTyped(codePoint, modifiers);
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @Override
    public void mouseReleased(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {
    }

    @Override
    public boolean keyPressed(DraggableWindow window, int keyCode, int scanCode, int modifiers) {
        if (currentScreen == Screen.CONVERSATION && focusedEditBox == messageInput) {
            if (keyCode == 257 || keyCode == 335) {
                if ((modifiers & 1) == 0) {
                    String messageText = messageInput.getValue().trim();
                    if (!messageText.isEmpty() && currentConversationId != null) {
                        networkManager.sendMessage(currentConversationId, messageText);
                        messageInput.setValue("");
                        return true;
                    }
                } else {
                    String currentText = messageInput.getValue();
                    int cursorPos = messageInput.getCursorPosition();
                    String newText = currentText.substring(0, cursorPos) + "\n" + currentText.substring(cursorPos);
                    messageInput.setValue(newText);
                    messageInput.setCursorPosition(cursorPos + 1);
                    return true;
                }
            }
        }

        if (focusedEditBox == profileDescInput) {
            if (keyCode == 257 || keyCode == 335) {
                if ((modifiers & 1) != 0) {
                    String currentText = profileDescInput.getValue();
                    int cursorPos = profileDescInput.getCursorPosition();
                    String newText = currentText.substring(0, cursorPos) + "\n" + currentText.substring(cursorPos);
                    profileDescInput.setValue(newText);
                    profileDescInput.setCursorPosition(cursorPos + 1);
                    return true;
                }
                focusedEditBox = null;
                profileDescInput.setFocused(false);
                saveProfileChanges();
                return true;
            }
        }

        if (focusedEditBox != null) {
            try {
                return focusedEditBox.keyPressed(keyCode, scanCode, modifiers);
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        switch (currentScreen) {
            case PROFILE:
                int profileContentHeight = 600;
                int profileViewportHeight = getContentRect(window)[3] - 60;
                int maxProfileScroll = Math.max(0, profileContentHeight - profileViewportHeight);
                profileScrollOffset = (int) Math.max(0, Math.min(maxProfileScroll, profileScrollOffset - delta * 20));
                return true;
            case CHAT_LIST:
                int chatItemCount = networkManager.getContacts().size() + networkManager.getGroups().size();
                int chatContentHeight = chatItemCount * 70;
                int chatViewportHeight = getContentRect(window)[3] - 140;
                int maxChatScroll = Math.max(0, chatContentHeight - chatViewportHeight);
                chatListScrollOffset = (int) Math.max(0, Math.min(maxChatScroll, chatListScrollOffset - delta * 20));
                return true;
            case CONVERSATION:
                conversationScrollOffset = (int) Math.max(0, Math.min(currentMaxConversationScroll, conversationScrollOffset - delta * 20));
                return true;
            case CONTACTS:
                int contactCount = networkManager.getContacts().size();
                int contactContentHeight = 160 + contactCount * 55;
                int contactViewportHeight = getContentRect(window)[3] - 60;
                int maxContactScroll = Math.max(0, contactContentHeight - contactViewportHeight);
                contactsScrollOffset = (int) Math.max(0, Math.min(maxContactScroll, contactsScrollOffset - delta * 20));
                return true;
            case FRIEND_REQUESTS:
                int requestCount = networkManager.getPendingFriendRequestCount();
                int requestContentHeight = 70 + requestCount * 75;
                int requestViewportHeight = getContentRect(window)[3] - 60;
                int maxRequestScroll = Math.max(0, requestContentHeight - requestViewportHeight);
                friendRequestsScrollOffset = (int) Math.max(0, Math.min(maxRequestScroll, friendRequestsScrollOffset - delta * 20));
                return true;
            case GROUP_CREATION:
                int groupContactCount = networkManager.getContacts().size();
                int groupContentHeight = 110 + groupContactCount * 22;
                int groupViewportHeight = getContentRect(window)[3] - 160;
                int maxGroupScroll = Math.max(0, groupContentHeight - groupViewportHeight);
                groupCreationScrollOffset = (int) Math.max(0, Math.min(maxGroupScroll, groupCreationScrollOffset - delta * 20));
                return true;
            case ADD_TO_GROUP:
                int groupCount = networkManager.getGroups().size();
                int addToGroupContentHeight = 110 + groupCount * 34;
                int addToGroupViewportHeight = getContentRect(window)[3] - 140;
                int maxAddToGroupScroll = Math.max(0, addToGroupContentHeight - addToGroupViewportHeight);
                addToGroupScrollOffset = (int) Math.max(0, Math.min(maxAddToGroupScroll, addToGroupScrollOffset - delta * 20));
                return true;
            case GROUP_SETTINGS:
                int settingsContentHeight = 600;
                int settingsViewportHeight = getContentRect(window)[3] - 60;
                int maxSettingsScroll = Math.max(0, settingsContentHeight - settingsViewportHeight);
                profileScrollOffset = (int) Math.max(0, Math.min(maxSettingsScroll, profileScrollOffset - delta * 20));
                return true;
        }
        return false;
    }

    @Override
    public void onOpen(DraggableWindow window) {
        this.window = window;
        this.networkManager = MessengerNetworkManager.getInstance();
        initializeUIComponents();
    }

    private void initializeUIComponents() {
        Minecraft mc = Minecraft.getInstance();

        this.messageInput = new EditBox(mc.font, 0, 0, 200, 60, Component.literal("Type a message...")) {
            @Override
            public boolean charTyped(char codePoint, int modifiers) {
                if (this.isActive()) {
                    if (codePoint == '\n' && (modifiers & 1) != 0) {
                        String currentText = this.getValue();
                        int cursorPos = this.getCursorPosition();
                        String newText = currentText.substring(0, cursorPos) + "\n" + currentText.substring(cursorPos);
                        this.setValue(newText);
                        this.setCursorPosition(cursorPos + 1);
                        return true;
                    }
                }
                return super.charTyped(codePoint, modifiers);
            }
        };
        this.messageInput.setMaxLength(1000);
        this.messageInput.setBordered(false);

        this.searchInput = new EditBox(mc.font, 0, 0, 200, 20, Component.literal("Search..."));
        this.searchInput.setBordered(false);

        this.friendEmailInput = new EditBox(mc.font, 0, 0, 200, 20, Component.literal("friend@rift.com"));
        this.friendEmailInput.setBordered(false);

        this.groupNameInput = new EditBox(mc.font, 0, 0, 200, 20, Component.literal("Group name"));
        this.groupNameInput.setBordered(false);

        this.groupDescriptionInput = new EditBox(mc.font, 0, 0, 200, 20, Component.literal("Group description")) {
            @Override
            public boolean charTyped(char codePoint, int modifiers) {
                return false;
            }

            @Override
            public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                return false;
            }
        };
        this.groupDescriptionInput.setValue("Coming soon...");
        this.groupDescriptionInput.setBordered(false);
        this.groupDescriptionInput.setEditable(false);

        this.profileDescInput = new EditBox(mc.font, 0, 0, 280, 100, Component.literal("Description")) {
            @Override
            public boolean charTyped(char codePoint, int modifiers) {
                return false;
            }

            @Override
            public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                return false;
            }
        };
        this.profileDescInput.setValue("Coming soon...");
        this.profileDescInput.setMaxLength(500);
        this.profileDescInput.setBordered(false);
        this.profileDescInput.setVisible(false);
        this.profileDescInput.setEditable(false);

        this.messageInput.setResponder(text -> {
        });

        this.searchInput.setResponder(text -> {
            this.searchQuery = text == null ? "" : text.trim().toLowerCase();
        });

        this.profileDescInput.setResponder(text -> {
        });

        this.initialized = true;
    }

    @Override
    public void renderContent(GuiGraphics guiGraphics, PoseStack poseStack, DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick) {
        int[] rect = getContentRect(window);
        int cx = rect[0], cy = rect[1], cw = rect[2], ch = rect[3];

        int absMouseX = mouseRelX + Math.round(window.getDisplayX());
        int absMouseY = mouseRelY + Math.round(window.getDisplayY());

        showRightPanel = cw >= 800;
        guiGraphics.fill(cx, cy, cx + cw, cy + ch, DraggableWindow.darkTheme ? 0xFF1F2B38 : 0xFFF0F0F0);

        switch (currentScreen) {
            case CHAT_LIST:
                renderChatListView(guiGraphics, cx, cy, cw, ch, absMouseX, absMouseY);
                break;
            case CONVERSATION:
                renderConversationView(guiGraphics, cx, cy, cw, ch, absMouseX, absMouseY);
                break;
            case CONTACTS:
                renderContactsView(guiGraphics, cx, cy, cw, ch, absMouseX, absMouseY);
                break;
            case FRIEND_REQUESTS:
                renderFriendRequestsView(guiGraphics, cx, cy, cw, ch, absMouseX, absMouseY);
                break;
            case PROFILE:
                renderProfileView(guiGraphics, cx, cy, cw, ch, absMouseX, absMouseY);
                break;
            case GROUP_CREATION:
                renderGroupCreationView(guiGraphics, cx, cy, cw, ch, absMouseX, absMouseY);
                break;
            case ADD_TO_GROUP:
                renderAddToGroupView(guiGraphics, cx, cy, cw, ch, absMouseX, absMouseY);
                break;
            case GROUP_SETTINGS:
                renderGroupSettingsView(guiGraphics, cx, cy, cw, ch, absMouseX, absMouseY);
                break;
        }

        if (chatContextMenu != null && currentScreen == Screen.CHAT_LIST) {
            chatContextMenu.render(guiGraphics, absMouseX, absMouseY, partialTick);
        }
    }

    private void renderChatListView(GuiGraphics guiGraphics, int cx, int cy, int cw, int ch, int mouseX, int mouseY) {
        boolean willShowRight = showRightPanel && currentConversationId != null && currentScreen != Screen.CHAT_LIST;
        int leftWidth = willShowRight ? Math.min(leftPanelWidth, Math.max(0, cw - rightPanelWidth)) : cw;
        int rightWidth = willShowRight ? Math.min(rightPanelWidth, Math.max(0, cw - leftWidth)) : 0;

        guiGraphics.fill(cx, cy, cx + leftWidth, cy + ch, DraggableWindow.darkTheme ? 0xFF2A3942 : 0xFFFFFFFF);
        renderHeader(guiGraphics, "Chats", cx, cy, leftWidth, mouseX, mouseY);
        renderSearchBar(guiGraphics, cx, cy, leftWidth, mouseX, mouseY);
        renderFilterTabs(guiGraphics, cx, cy, leftWidth);
        renderChatList(guiGraphics, cx, cy, leftWidth, ch, mouseX, mouseY);

        if (rightWidth > 0 && currentConversationId != null) {
            renderRightPanel(guiGraphics, cx + leftWidth, cy, rightWidth, ch, mouseX, mouseY);
        }
    }

    private void renderRightPanel(GuiGraphics guiGraphics, int panelX, int panelY, int panelWidth, int panelHeight, int mouseX, int mouseY) {
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, DraggableWindow.darkTheme ? 0xFF1F2B38 : 0xFFF0F0F0);
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + 60, DraggableWindow.darkTheme ? 0xFF2A3942 : 0xFF008069);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Contact Info"), panelX + 16, panelY + 22, 0xFFFFFFFF, false);

        if (currentConversationId != null) {
            MessengerNetworkManager.Contact contact = networkManager.getContact(currentConversationId);
            MessengerNetworkManager.Group group = networkManager.getGroup(currentConversationId);
            if (group != null) {
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(group.name), panelX + 16, panelY + 80, DraggableWindow.darkTheme ? 0xFFFFFFFF : 0xFF000000, false);
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(group.description == null || group.description.isEmpty() ? "No description" : group.description), panelX + 16, panelY + 100, DraggableWindow.darkTheme ? 0xFF8696A0 : 0xFF667781, false);

                int y = panelY + 130;
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Members"), panelX + 16, y - 16, DraggableWindow.darkTheme ? 0xFF8696A0 : 0xFF667781, false);
                for (UUID mid : group.members) {
                    if (y + 30 > panelY + panelHeight) break;
                    String name = mid.toString();
                    MessengerNetworkManager.Contact c = networkManager.getContact(mid);
                    if (c != null) name = c.displayName;
                    guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(name), panelX + 16, y, DraggableWindow.darkTheme ? 0xFFFFFFFF : 0xFF000000, false);
                    y += 26;
                }

                UUID me = null;
                try {
                    if (Minecraft.getInstance().player != null) me = Minecraft.getInstance().player.getUUID();
                } catch (Exception ignored) {
                }
                if (me != null && networkManager.isGroupAdmin(group.groupId, me)) {
                    if (renderButton(guiGraphics, "Edit Group", panelX + 16, panelY + panelHeight - 90, 120, 26, mouseX, mouseY)) {
                        openGroupSettings(group.groupId);
                    }
                }

            } else if (contact != null) {
                int avatarSize = 80;
                ResourceLocation avatar = getOrCreateAvatarTexture(contact.playerId, avatarSize);
                guiGraphics.blit(avatar, panelX + (panelWidth - avatarSize) / 2, panelY + 80, avatarSize, avatarSize, 0, 0, avatarSize, avatarSize, avatarSize, avatarSize);

                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(contact.displayName),
                        panelX + 16, panelY + 180, DraggableWindow.darkTheme ? 0xFFFFFFFF : 0xFF000000, false);
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(contact.email),
                        panelX + 16, panelY + 200, DraggableWindow.darkTheme ? 0xFF8696A0 : 0xFF667781, false);

                String desc = (contact.description == null || contact.description.isEmpty()) ? "No description" : contact.description;
                List<FormattedText> descLines = Minecraft.getInstance().font.getSplitter().splitLines(Component.literal(desc), panelWidth - 32, Style.EMPTY);
                int descY = panelY + 220;
                for (FormattedText line : descLines) {
                    if (descY + 10 > panelY + panelHeight) break;
                    guiGraphics.drawString(Minecraft.getInstance().font, line.getString(), panelX + 16, descY, DraggableWindow.darkTheme ? 0xFF8696A0 : 0xFF667781, false);
                    descY += 10;
                }

                String status = contact.isOnline ? "Online" : "Last seen " + formatTime(contact.lastSeen);
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(status),
                        panelX + 16, panelY + descY, DraggableWindow.darkTheme ? 0xFF8696A0 : 0xFF667781, false);

                boolean isBlocked = networkManager.isBlocked(contact.playerId);
                String blockText = isBlocked ? "Unblock" : "Block";
                renderButton(guiGraphics, blockText, panelX + 16, panelY + descY + 20, 120, 26, mouseX, mouseY);
            }
        }
    }

    private void renderConversationView(GuiGraphics guiGraphics, int cx, int cy, int cw, int ch, int mouseX, int mouseY) {
        if (currentConversationId == null) {
            currentScreen = Screen.CHAT_LIST;
            return;
        }

        MessengerNetworkManager.Contact contact = networkManager.getContact(currentConversationId);
        MessengerNetworkManager.Group group = networkManager.getGroup(currentConversationId);
        if (contact == null && group == null) return;

        guiGraphics.fill(cx, cy, cx + cw, cy + 60, DraggableWindow.darkTheme ? 0xFF2A3942 : 0xFF008069);

        boolean backHovered = isMouseOver(mouseX, mouseY, cx + 8, cy + 15, 32, 30);
        if (backHovered) {
            guiGraphics.fill(cx + 8, cy + 15, cx + 40, cy + 45, 0x66FFFFFF);
        }
        guiGraphics.fill(cx + 8, cy + 15, cx + 40, cy + 45, backHovered ? 0x44FFFFFF : 0x33FFFFFF);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("←"), cx + 18, cy + 25, 0xFFFFFFFF, false);

        int avatarSize = 36;
        ResourceLocation avatar;
        if (contact != null) {
            avatar = getOrCreateAvatarTexture(contact.playerId, avatarSize);
            guiGraphics.blit(avatar, cx + 50, cy + 12, avatarSize, avatarSize, 0, 0, avatarSize, avatarSize, avatarSize, avatarSize);

            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(contact.displayName), cx + 50 + avatarSize + 8, cy + 20, 0xFFFFFFFF, false);
            String status = contact.isOnline ? "online" : "last seen " + formatTime(contact.lastSeen);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(status), cx + 50 + avatarSize + 8, cy + 35, 0xFF8696A0, false);
        } else if (group != null) {
            boolean headerHovered = isMouseOver(mouseX, mouseY, cx + 50, cy + 12, cw - 100, 36);
            if (headerHovered) {
                guiGraphics.fill(cx + 50, cy + 12, cx + cw - 50, cy + 48, 0x33FFFFFF);
            }

            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(group.name), cx + 50, cy + 20, 0xFFFFFFFF, false);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(group.description == null || group.description.isEmpty() ? "Group" : group.description), cx + 50, cy + 35, 0xFF8696A0, false);

            int onlineCount = 0;
            for (UUID memberId : group.members) {
                MessengerNetworkManager.Contact memberContact = networkManager.getContact(memberId);
                if (memberContact != null && memberContact.isOnline) onlineCount++;
            }
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(group.members.size() + " members, " + onlineCount + " online"),
                    cx + 50, cy + 48, 0xFF8696A0, false);
        }

        int messagesStartY = cy + 60;
        int messagesEndY = cy + ch - 70;
        guiGraphics.fill(cx, messagesStartY, cx + cw, messagesEndY, DraggableWindow.darkTheme ? 0xFF0B141A : 0xFFE6E6E6);

        renderMessages(guiGraphics, cx, messagesStartY, messagesEndY, cw);
        renderMessageInput(guiGraphics, cx, cy, cw, ch, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphics guiGraphics, String title, int cx, int cy, int width, int mouseX, int mouseY) {
        guiGraphics.fill(cx, cy, cx + width, cy + 60, DraggableWindow.darkTheme ? 0xFF202C33 : 0xFF008069);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(title), cx + 16, cy + 22, 0xFFFFFFFF, false);

        String[] navItems = {"Chats", "Contacts", "Requests"};
        int navWidth = (width - 32) / navItems.length;

        for (int i = 0; i < navItems.length; i++) {
            int x = cx + 16 + i * navWidth;
            boolean active = (i == 0 && currentScreen == Screen.CHAT_LIST) ||
                    (i == 1 && currentScreen == Screen.CONTACTS) ||
                    (i == 2 && currentScreen == Screen.FRIEND_REQUESTS);

            int color = active ? 0xFF00A884 : 0x00000000;
            guiGraphics.fill(x, cy + 40, x + navWidth, cy + 60, color);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(navItems[i]),
                    x + (navWidth - Minecraft.getInstance().font.width(navItems[i])) / 2, cy + 45,
                    active ? 0xFFFFFFFF : 0xFF8696A0, false);

            if (i == 2) {
                int pending = networkManager.getPendingFriendRequestCount();
                if (pending > 0) {
                    int badgeX = x + navWidth - 20;
                    int badgeY = cy + 42;
                    guiGraphics.fill(badgeX, badgeY, badgeX + 16, badgeY + 16, 0xFFFF5252);
                    guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(String.valueOf(pending)), badgeX + 4, badgeY + 2, 0xFFFFFFFF, false);
                }
            }
        }

        renderIconButton(guiGraphics, "⚙", cx + width - 40, cy + 12, 30, 30, mouseX, mouseY);
    }

    private void renderSearchBar(GuiGraphics guiGraphics, int cx, int cy, int width, int mouseX, int mouseY) {
        guiGraphics.fill(cx + 8, cy + 70, cx + width - 8, cy + 100, DraggableWindow.darkTheme ? 0xFF202C33 : 0xFFFFFFFF);

        String value = searchInput.getValue();
        boolean focused = focusedEditBox == searchInput;
        if (value.isEmpty() && !focused) {
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Search..."), cx + 20, cy + 82,
                    DraggableWindow.darkTheme ? 0xFF8696A0 : 0xFF667781, false);
        } else {
            String display = value.isEmpty() ? "" : Minecraft.getInstance().font.plainSubstrByWidth(value, width - 40);
            if (focused) {
                boolean caretOn = (System.currentTimeMillis() / 500) % 2 == 0;
                if (caretOn) {
                    String withCaret = display + "|";
                    if (Minecraft.getInstance().font.width(withCaret) > width - 40) {
                        display = Minecraft.getInstance().font.plainSubstrByWidth(display, width - 44);
                        display = display + "|";
                    } else display = withCaret;
                }
            }
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(display), cx + 20, cy + 82,
                    DraggableWindow.darkTheme ? 0xFFFFFFFF : 0xFF000000, false);
        }

        searchInput.setX(cx + 20);
        searchInput.setY(cy + 82);
        searchInput.setWidth(width - 40);
    }

    private void renderFilterTabs(GuiGraphics guiGraphics, int cx, int cy, int width) {
        String[] filters = {"All", "Unread", "Favorites", "Groups"};
        int tabWidth = (width - 16) / filters.length;
        for (int i = 0; i < filters.length; i++) {
            int tabX = cx + 8 + i * tabWidth;
            boolean selected = filters[i].equals(currentFilter);
            int color = selected ? (DraggableWindow.darkTheme ? 0xFF00A884 : 0xFF008069) : 0x00000000;
            guiGraphics.fill(tabX, cy + 110, tabX + tabWidth, cy + 130, color);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(filters[i]),
                    tabX + (tabWidth - Minecraft.getInstance().font.width(filters[i])) / 2, cy + 115,
                    selected ? 0xFFFFFFFF : (DraggableWindow.darkTheme ? 0xFF8696A0 : 0xFF667781), false);
        }
    }

    private void renderChatList(GuiGraphics guiGraphics, int cx, int cy, int width, int height, int mouseX, int mouseY) {
        int chatY = cy + 140 - chatListScrollOffset;

        List<DisplayItem> items = new ArrayList<>();
        for (MessengerNetworkManager.Contact contact : networkManager.getContacts()) {
            if (!passesFilter(contact.playerId, contact.displayName)) continue;
            items.add(new DisplayItem(contact.playerId, contact.displayName, false));
        }
        for (MessengerNetworkManager.Group group : networkManager.getGroups()) {
            if (currentFilter.equals("Groups") || passesFilter(group.groupId, group.name)) {
                items.add(new DisplayItem(group.groupId, group.name, true));
            }
        }

        for (DisplayItem item : items) {
            if (chatY + 70 > cy + height) break;
            if (chatY < cy + 140) {
                chatY += 70;
                continue;
            }

            UUID id = item.id;
            boolean isGroup = item.isGroup;

            List<MessengerNetworkManager.Message> conversation = networkManager.getConversation(id);
            String lastMessage = conversation.isEmpty() ? "No messages yet" : conversation.get(conversation.size() - 1).content;

            boolean selected = selectedConversationId != null && selectedConversationId.equals(id);
            if (selected) {
                guiGraphics.fill(cx, chatY, cx + width, chatY + 70, DraggableWindow.darkTheme ? 0xFF2D3B43 : 0xFFE9EDEF);
            }

            int avatarSize = 48;
            ResourceLocation avatar;
            if (!isGroup) {
                MessengerNetworkManager.Contact contact = networkManager.getContact(id);
                avatar = getOrCreateAvatarTexture(contact.playerId, avatarSize);
                guiGraphics.blit(avatar, cx + 12, chatY + 10, avatarSize, avatarSize, 0, 0, avatarSize, avatarSize, avatarSize, avatarSize);
            } else {
                guiGraphics.fill(cx + 12, chatY + 10, cx + 12 + avatarSize, chatY + 10 + avatarSize, 0xFF8E44AD);
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(item.title.substring(0, Math.min(2, item.title.length())).toUpperCase()), cx + 24, chatY + 24, 0xFFFFFFFF, false);
            }

            int textX = cx + 12 + avatarSize + 12;
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(item.title), textX, chatY + 12,
                    DraggableWindow.darkTheme ? 0xFFFFFFFF : 0xFF000000, false);

            if (!isGroup && networkManager.isBlocked(id)) {
                int nameWidth = Minecraft.getInstance().font.width(item.title);
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("🚫"), textX + nameWidth + 6, chatY + 12, 0xFFFF5252, false);
            }

            if (Minecraft.getInstance().font.width(lastMessage) > width - textX - 80) {
                lastMessage = Minecraft.getInstance().font.plainSubstrByWidth(lastMessage, width - textX - 85) + "...";
            }
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(lastMessage), textX, chatY + 30,
                    DraggableWindow.darkTheme ? 0xFF8696A0 : 0xFF667781, false);

            if (!conversation.isEmpty()) {
                String time = formatTime(conversation.get(conversation.size() - 1).timestamp);
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(time), cx + width - 60, chatY + 12,
                        DraggableWindow.darkTheme ? 0xFF8696A0 : 0xFF667781, false);
            }

            int unread = networkManager.getUnreadCount(id);
            if (unread > 0) {
                int badgeX = cx + width - 40;
                int badgeY = chatY + 28;
                guiGraphics.fill(badgeX, badgeY, badgeX + 26, badgeY + 18, 0xFFFF5252);
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(String.valueOf(unread > 99 ? 99 : unread)), badgeX + 6, badgeY + 2, 0xFFFFFFFF, false);
            }

            boolean isFav = networkManager.isFavorite(id);
            String star = isFav ? "★" : "☆";
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(star), cx + width - 20, chatY + 12, isFav ? 0xFFFFD700 : 0xFF8696A0, false);

            chatY += 70;
        }

        if (chatListScrollOffset > 0) {
            guiGraphics.fill(cx + width - 10, cy + 140, cx + width - 5, cy + height, 0x66FFFFFF);
        }
    }

    private boolean passesFilter(UUID id, String title) {
        if (searchQuery != null && !searchQuery.isEmpty()) {
            if (title == null || !title.toLowerCase().contains(searchQuery)) return false;
        }

        switch (currentFilter) {
            case "Unread":
                return networkManager.getUnreadCount(id) > 0;
            case "Favorites":
                return networkManager.isFavorite(id);
            case "Groups":
                return false;
            default:
                return true;
        }
    }

    private void renderContactsView(GuiGraphics guiGraphics, int cx, int cy, int cw, int ch, int mouseX, int mouseY) {
        renderHeader(guiGraphics, "Contacts", cx, cy, cw, mouseX, mouseY);

        guiGraphics.fill(cx + 16, cy + 70, cx + cw - 16, cy + 140, DraggableWindow.darkTheme ? 0xFF2A3942 : 0xFFF8F9FA);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Add Friend by Email"), cx + 32, cy + 85,
                DraggableWindow.darkTheme ? 0xFFFFFFFF : 0xFF000000, false);

        friendEmailInput.setX(cx + 32);
        friendEmailInput.setY(cy + 105);
        friendEmailInput.setWidth(cw - 150);

        String emailValue = friendEmailInput.getValue();
        boolean emailFocused = focusedEditBox == friendEmailInput;
        if (emailValue.isEmpty() && !emailFocused) {
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("friend@rift.com"), cx + 32, cy + 105,
                    DraggableWindow.darkTheme ? 0xFF8696A0 : 0xFF667781, false);
        } else {
            String display = emailValue.isEmpty() ? "" : Minecraft.getInstance().font.plainSubstrByWidth(emailValue, cw - 150 - 12);
            if (emailFocused) {
                boolean caretOn = (System.currentTimeMillis() / 500) % 2 == 0;
                if (caretOn) {
                    String withCaret = display + "|";
                    if (Minecraft.getInstance().font.width(withCaret) > cw - 150 - 12) {
                        display = Minecraft.getInstance().font.plainSubstrByWidth(display, cw - 150 - 16) + "|";
                    } else display = withCaret;
                }
            }
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(display), cx + 32, cy + 105,
                    DraggableWindow.darkTheme ? 0xFFFFFFFF : 0xFF000000, false);
        }

        int buttonColor = isMouseOver(mouseX, mouseY, cx + cw - 110, cy + 105, 80, 20) ? 0xFF00A884 : 0xFF008069;
        guiGraphics.fill(cx + cw - 110, cy + 105, cx + cw - 30, cy + 125, buttonColor);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Add Friend"), cx + cw - 100, cy + 110, 0xFFFFFFFF, false);

        int contactsStartY = cy + 160 - contactsScrollOffset;
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Your Contacts"), cx + 32, contactsStartY - 20,
                DraggableWindow.darkTheme ? 0xFF8696A0 : 0xFF667781, false);

        int contactY = contactsStartY;
        for (MessengerNetworkManager.Contact contact : networkManager.getContacts()) {
            if (contactY + 60 > cy + ch) break;
            if (contactY < cy + 160) {
                contactY += 55;
                continue;
            }

            renderContactItem(guiGraphics, contact, cx + 16, contactY, cw - 32, 50, mouseX, mouseY);
            contactY += 55;
        }

        if (contactsScrollOffset > 0) {
            guiGraphics.fill(cx + cw - 10, cy + 160, cx + cw - 5, cy + ch, 0x66FFFFFF);
        }
    }

    private void renderContactItem(GuiGraphics guiGraphics, MessengerNetworkManager.Contact contact, int x, int y, int width, int height, int mouseX, int mouseY) {
        boolean hovered = isMouseOver(mouseX, mouseY, x, y, width, height);
        if (hovered) {
            guiGraphics.fill(x, y, x + width, y + height, DraggableWindow.darkTheme ? 0x22333333 : 0x22DDDDDD);
        }

        int avatarSize = 40;
        ResourceLocation avatar = getOrCreateAvatarTexture(contact.playerId, avatarSize);
        guiGraphics.blit(avatar, x + 8, y + 5, avatarSize, avatarSize, 0, 0, avatarSize, avatarSize, avatarSize, avatarSize);

        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(contact.displayName), x + 8 + avatarSize + 8, y + 10,
                DraggableWindow.darkTheme ? 0xFFFFFFFF : 0xFF000000, false);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(contact.email), x + 8 + avatarSize + 8, y + 25,
                DraggableWindow.darkTheme ? 0xFF8696A0 : 0xFF667781, false);

        if (contact.isOnline) {
            guiGraphics.fill(x + width - 20, y + 15, x + width - 12, y + 23, 0xFF00A884);
        } else {
            String lastSeen = "last seen " + formatTime(contact.lastSeen);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(lastSeen),
                    x + width - Minecraft.getInstance().font.width(lastSeen) - 8, y + 15,
                    DraggableWindow.darkTheme ? 0xFF8696A0 : 0xFF667781, false);
        }

        renderButton(guiGraphics, "Message", x + width - 100, y + 30, 80, 20, mouseX, mouseY);
    }

    private void renderFriendRequestsView(GuiGraphics guiGraphics, int cx, int cy, int cw, int ch, int mouseX, int mouseY) {
        renderHeader(guiGraphics, "Friend Requests", cx, cy, cw, mouseX, mouseY);

        int requestY = cy + 70 - friendRequestsScrollOffset;
        for (MessengerNetworkManager.FriendRequest request : networkManager.getPendingFriendRequests()) {
            if (requestY + 80 > cy + ch) break;
            if (requestY < cy + 70) {
                requestY += 75;
                continue;
            }

            guiGraphics.fill(cx + 16, requestY, cx + cw - 16, requestY + 70, DraggableWindow.darkTheme ? 0xFF2A3942 : 0xFFF8F9FA);

            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(request.fromPlayerName), cx + 32, requestY + 15,
                    DraggableWindow.darkTheme ? 0xFFFFFFFF : 0xFF000000, false);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Wants to be your friend"), cx + 32, requestY + 30,
                    DraggableWindow.darkTheme ? 0xFF8696A0 : 0xFF667781, false);

            int acceptColor = isMouseOver(mouseX, mouseY, cx + 32, requestY + 45, 60, 20) ? 0xFF00C853 : 0xFF00A884;
            int declineColor = isMouseOver(mouseX, mouseY, cx + 102, requestY + 45, 60, 20) ? 0xFFFF5252 : 0xFFF44336;

            guiGraphics.fill(cx + 32, requestY + 45, cx + 92, requestY + 65, acceptColor);
            guiGraphics.fill(cx + 102, requestY + 45, cx + 162, requestY + 65, declineColor);

            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Accept"), cx + 42, requestY + 50, 0xFFFFFFFF, false);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Decline"), cx + 112, requestY + 50, 0xFFFFFFFF, false);

            requestY += 75;
        }

        if (friendRequestsScrollOffset > 0) {
            guiGraphics.fill(cx + cw - 10, cy + 70, cx + cw - 5, cy + ch, 0x66FFFFFF);
        }
    }

    private void renderProfileView(GuiGraphics guiGraphics, int cx, int cy, int cw, int ch, int mouseX, int mouseY) {
        renderHeader(guiGraphics, "Profile", cx, cy, cw, mouseX, mouseY);

        int scrollY = cy + 60 - profileScrollOffset;
        int contentHeight = ch - 60;

        if (profileViewingId != null) {
            MessengerNetworkManager.Contact contact = networkManager.getContact(profileViewingId);
            if (contact != null) {
                int avatarSize = 120;
                ResourceLocation avatar = getOrCreateAvatarTexture(contact.playerId, avatarSize);
                guiGraphics.blit(avatar, cx + cw / 2 - avatarSize / 2, scrollY + 20, avatarSize, avatarSize, 0, 0, avatarSize, avatarSize, avatarSize, avatarSize);

                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(contact.displayName),
                        cx + cw / 2 - 60, scrollY + 150, 0xFFFFFFFF, false);
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(contact.email),
                        cx + cw / 2 - 60, scrollY + 170, 0xFF8696A0, false);

                String desc = (contact.description == null || contact.description.isEmpty()) ?
                        "This user hasn't set a description yet." : contact.description;

                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Description:"),
                        cx + cw / 2 - 140, scrollY + 200, 0xFF8696A0, false);

                List<FormattedText> descLines = Minecraft.getInstance().font.getSplitter().splitLines(
                        Component.literal(desc), cw - 80, Style.EMPTY);
                int descY = scrollY + 220;
                for (FormattedText line : descLines) {
                    if (descY + 10 > cy + ch) break;
                    guiGraphics.drawString(Minecraft.getInstance().font, line.getString(),
                            cx + cw / 2 - 140, descY, 0xFF8696A0, false);
                    descY += 10;
                }
            } else {
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Profile not found"),
                        cx + 20, scrollY + 20, 0xFFFFFFFF, false);
            }
        } else {
            AccountManager.Account account = AccountManager.getInstance().getAccount();

            int avatarSize = 120;
            ResourceLocation avatar = getCurrentPlayerAvatar(avatarSize);
            guiGraphics.blit(avatar, cx + cw / 2 - avatarSize / 2, scrollY + 20, avatarSize, avatarSize, 0, 0, avatarSize, avatarSize, avatarSize, avatarSize);

            int infoY = scrollY + 160;
            renderEditableProfileField(guiGraphics, "Display Name", account.displayName, cx + cw / 2 - 150, infoY, 300, mouseX, mouseY);
            infoY += 60;
            renderEditableProfileField(guiGraphics, "Email", account.email, cx + cw / 2 - 150, infoY, 300, mouseX, mouseY);
            infoY += 60;

            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Description:"), cx + cw / 2 - 150, infoY,
                    DraggableWindow.darkTheme ? 0xFF8696A0 : 0xFF667781, false);

            int descBgColor = DraggableWindow.darkTheme ? 0xFF2A3942 : 0xFFFFFFFF;
            guiGraphics.fill(cx + cw / 2 - 150, infoY + 20, cx + cw / 2 + 150, infoY + 120, descBgColor);

            String descriptionText = "Coming soon...";
            List<FormattedText> descLines = Minecraft.getInstance().font.getSplitter().splitLines(
                    Component.literal(descriptionText), 280, Style.EMPTY);
            int descY = infoY + 26;
            for (FormattedText line : descLines) {
                guiGraphics.drawString(Minecraft.getInstance().font, line.getString(),
                        cx + cw / 2 - 140, descY, DraggableWindow.darkTheme ? 0xFF8696A0 : 0xFF667781, false);
                descY += 10;
            }

            int borderColor = 0xFF8696A0;
            guiGraphics.fill(cx + cw / 2 - 151, infoY + 19, cx + cw / 2 + 151, infoY + 20, borderColor);
            guiGraphics.fill(cx + cw / 2 - 151, infoY + 119, cx + cw / 2 + 151, infoY + 120, borderColor);
            guiGraphics.fill(cx + cw / 2 - 151, infoY + 20, cx + cw / 2 - 150, infoY + 120, borderColor);
            guiGraphics.fill(cx + cw / 2 + 150, infoY + 20, cx + cw / 2 + 151, infoY + 120, borderColor);

            infoY += 130;

            if (renderButton(guiGraphics, "Save Changes", cx + cw / 2 - 60, infoY, 120, 30, mouseX, mouseY)) {
                saveProfileChanges();
            }
        }

        if (profileScrollOffset > 0) {
            guiGraphics.fill(cx + cw - 10, cy + 60, cx + cw - 5, cy + ch, 0x66FFFFFF);
        }
    }

    private int getCurrentLine(String text, int cursorPosition) {
        if (text.isEmpty()) return 0;
        String[] lines = text.split("\n", -1);
        int currentPos = 0;
        for (int i = 0; i < lines.length; i++) {
            currentPos += lines[i].length() + 1;
            if (currentPos > cursorPosition) return i;
        }
        return lines.length - 1;
    }

    private int getCursorPositionInLine(String text, int cursorPosition, int lineIndex) {
        if (text.isEmpty()) return 0;
        String[] lines = text.split("\n", -1);
        int currentPos = 0;
        for (int i = 0; i < lineIndex; i++) {
            currentPos += lines[i].length() + 1;
        }
        return cursorPosition - currentPos;
    }

    private void renderEditableProfileField(GuiGraphics guiGraphics, String label, String value, int x, int y, int width, int mouseX, int mouseY) {
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(label), x, y - 15,
                DraggableWindow.darkTheme ? 0xFF8696A0 : 0xFF667781, false);

        guiGraphics.fill(x, y, x + width, y + 40, DraggableWindow.darkTheme ? 0xFF2A3942 : 0xFFFFFFFF);

        guiGraphics.fill(x, y, x + width, y + 1, DraggableWindow.darkTheme ? 0xFF8696A0 : 0xFF667781);
        guiGraphics.fill(x, y + 39, x + width, y + 40, DraggableWindow.darkTheme ? 0xFF8696A0 : 0xFF667781);
        guiGraphics.fill(x, y, x + 1, y + 40, DraggableWindow.darkTheme ? 0xFF8696A0 : 0xFF667781);
        guiGraphics.fill(x + width - 1, y, x + width, y + 40, DraggableWindow.darkTheme ? 0xFF8696A0 : 0xFF667781);

        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(value), x + 12, y + 12,
                DraggableWindow.darkTheme ? 0xFFFFFFFF : 0xFF000000, false);

        if (isMouseOver(mouseX, mouseY, x, y, width, 40)) {
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("✎"), x + width - 20, y + 12, 0xFF8696A0, false);
        }
    }

    private void renderMessages(GuiGraphics guiGraphics, int cx, int startY, int endY, int width) {
        if (currentConversationId == null) return;

        List<MessengerNetworkManager.Message> messages = networkManager.getConversation(currentConversationId);
        boolean isGroup = networkManager.getGroup(currentConversationId) != null;

        int totalContentHeight = 0;
        int messageSpacing = 8;

        for (MessengerNetworkManager.Message message : messages) {
            totalContentHeight += getMessageHeight(message.content, width, isGroup) + messageSpacing;
        }

        totalContentHeight += 20;

        int viewportHeight = endY - startY;
        currentMaxConversationScroll = Math.max(0, totalContentHeight - viewportHeight);

        conversationScrollOffset = Math.max(0, Math.min(currentMaxConversationScroll, conversationScrollOffset));

        int messageY = startY + totalContentHeight - conversationScrollOffset;

        messageBounds.clear();

        for (int i = messages.size() - 1; i >= 0; i--) {
            MessengerNetworkManager.Message message = messages.get(i);

            int messageHeight = getMessageHeight(message.content, width, isGroup);
            messageY -= messageHeight + messageSpacing;

            if (messageY + messageHeight < startY) continue;
            if (messageY > endY) continue;

            renderMessageBubble(guiGraphics, message, cx, messageY, width, isGroup, true);

            if (!message.isFromMe() && !message.isRead) {
                networkManager.markMessageRead(currentConversationId, message.messageId);
                lastKnownReadState.put(message.messageId, true);
                readAnimStart.put(message.messageId, System.currentTimeMillis());
            }
        }

        if (currentMaxConversationScroll > 0) {
            int scrollbarHeight = Math.max(40, (int)(viewportHeight * (viewportHeight / (float)totalContentHeight)));
            int scrollbarY = startY + (int)((conversationScrollOffset / (float)currentMaxConversationScroll) * (viewportHeight - scrollbarHeight));
            guiGraphics.fill(cx + width - 10, scrollbarY, cx + width - 5, scrollbarY + scrollbarHeight, 0x66FFFFFF);
        }
    }

    private void renderMessageBubble(GuiGraphics guiGraphics, MessengerNetworkManager.Message message, int cx, int y, int width, boolean showAvatar, boolean isFirstInGroup) {
        boolean isFromMe = message.isFromMe();
        boolean isGroup = networkManager.getGroup(currentConversationId) != null;
        int senderAvatarSize = (isGroup && showAvatar) ? 20 : 0;

        int messageHeight = getMessageHeight(message.content, width, showAvatar && isGroup);

        int bubbleWidth = Math.max(120, Math.min(getMessageWidth(message.content) + 40, width - 120 - (senderAvatarSize + 8)));
        int bubbleX = isFromMe ? cx + width - bubbleWidth - 20 - (isGroup ? senderAvatarSize + 8 : 0) : cx + 20 + (isGroup ? senderAvatarSize + 8 : 0);
        int color = isFromMe ? 0xFF005C4B : (DraggableWindow.darkTheme ? 0xFF2A3942 : 0xFFFFFFFF);

        guiGraphics.fill(bubbleX, y - messageHeight, bubbleX + bubbleWidth, y, color);

        List<FormattedText> lines = Minecraft.getInstance().font.getSplitter().splitLines(Component.literal(message.content), bubbleWidth - 24, Style.EMPTY);
        int textY = y - messageHeight + 6;
        for (FormattedText line : lines) {
            guiGraphics.drawString(Minecraft.getInstance().font, line.getString(), bubbleX + 12, textY,
                    isFromMe ? 0xFFFFFFFF : (DraggableWindow.darkTheme ? 0xFFFFFFFF : 0xFF000000), false);
            textY += 10;
        }

        String time = formatTime(message.timestamp);
        int timeWidth = Minecraft.getInstance().font.width(time);

        String receipt = getReadReceipt(message);
        int receiptWidth = Minecraft.getInstance().font.width(receipt);

        int timeX = bubbleX + bubbleWidth - timeWidth - 12;
        int metaY = y - 8;

        int maxTimeX = bubbleX + bubbleWidth - 8;
        if (timeX + timeWidth + receiptWidth + 6 > maxTimeX) {
            timeX = maxTimeX - timeWidth - receiptWidth - 6;
        }

        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(time), timeX, metaY, isFromMe ? 0x88FFFFFF : 0x88667781, false);

        if (isFromMe) {
            boolean read = message.isRead;
            Boolean last = lastKnownReadState.get(message.messageId);
            long now = System.currentTimeMillis();
            if (last == null || last != read) {
                lastKnownReadState.put(message.messageId, read);
                readAnimStart.put(message.messageId, now);
            }

            long start = readAnimStart.getOrDefault(message.messageId, 0L);
            float progress = start == 0 ? (read ? 1f : 0f) : Math.min(1f, (now - start) / 300f);
            int grey = 0xFF9EA7B0;
            int green = 0xFF4FC3F7;
            int receiptColor = read ? lerpColor(grey, green, progress) : grey;

            int receiptX = timeX + timeWidth + 6;
            if (receiptX + receiptWidth <= bubbleX + bubbleWidth - 8) {
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(receipt), receiptX, metaY, receiptColor, false);
            }
        }

        int avatarX = 0, avatarY = 0, avatarSize = 0;
        if (isGroup && showAvatar) {
            avatarSize = senderAvatarSize;
            if (isFromMe) {
                avatarX = bubbleX + bubbleWidth + 8;
            } else {
                avatarX = bubbleX - avatarSize - 8;
            }
            avatarY = y - messageHeight + (isFirstInGroup ? 6 : 2);
            try {
                ResourceLocation atex;
                if (message.isFromMe()) {
                    atex = getCurrentPlayerAvatar(avatarSize);
                } else {
                    atex = getOrCreateAvatarTexture(message.senderId, avatarSize);
                }
                if (atex == null) {
                    atex = getDefaultAvatarTexture(avatarSize);
                }
                guiGraphics.blit(atex, avatarX, avatarY, avatarSize, avatarSize, 0, 0, avatarSize, avatarSize, avatarSize, avatarSize);
            } catch (Exception ignored) {
                ResourceLocation defaultAvatar = getDefaultAvatarTexture(avatarSize);
                guiGraphics.blit(defaultAvatar, avatarX, avatarY, avatarSize, avatarSize, 0, 0, avatarSize, avatarSize, avatarSize, avatarSize);
            }
        }

        messageBounds.put(message.messageId, new int[]{bubbleX, y - messageHeight, bubbleWidth, messageHeight, avatarX, avatarY, avatarSize});
    }

    private String getReadReceipt(MessengerNetworkManager.Message message) {
        if (!message.isFromMe()) return "";

        if (message.isRead) {
            return "■■";
        } else {
            return "□■";
        }
    }

    private int lerpColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int rr = (int) (ar + (br - ar) * t);
        int rg = (int) (ag + (bg - ag) * t);
        int rb = (int) (ab + (bb - ab) * t);
        return (0xFF << 24) | (rr << 16) | (rg << 8) | rb;
    }

    private void renderMessageInput(GuiGraphics guiGraphics, int cx, int cy, int cw, int ch, int mouseX, int mouseY) {
        guiGraphics.fill(cx, cy + ch - 70, cx + cw, cy + ch, DraggableWindow.darkTheme ? 0xFF1F2B38 : 0xFFF0F0F0);
        guiGraphics.fill(cx + 8, cy + ch - 55, cx + cw - 80, cy + ch - 15, DraggableWindow.darkTheme ? 0xFF2A3942 : 0xFFFFFFFF);

        messageInput.setX(cx + 20);
        messageInput.setY(cy + ch - 50);
        messageInput.setWidth(cw - 100);
        messageInput.setHeight(35);

        String value = messageInput.getValue();
        boolean focused = focusedEditBox == messageInput;

        if (value.isEmpty() && !focused) {
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Type a message... (Shift+Enter for new line)"), cx + 20, cy + ch - 45,
                    DraggableWindow.darkTheme ? 0xFF8696A0 : 0xFF667781, false);
        } else {
            if (focused) {
                List<FormattedText> lines = Minecraft.getInstance().font.getSplitter().splitLines(Component.literal(value), cw - 100 - 12, Style.EMPTY);
                int lineY = cy + ch - 50;
                for (int i = 0; i < Math.min(lines.size(), 3); i++) {
                    String lineText = lines.get(i).getString();
                    if (i == getCurrentLine(value, messageInput.getCursorPosition())) {
                        int cursorPosInLine = getCursorPositionInLine(value, messageInput.getCursorPosition(), i);
                        String beforeCursor = lineText.substring(0, Math.min(cursorPosInLine, lineText.length()));
                        String afterCursor = lineText.substring(Math.min(cursorPosInLine, lineText.length()));

                        boolean caretOn = (System.currentTimeMillis() / 500) % 2 == 0;
                        if (caretOn) {
                            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(beforeCursor + "|" + afterCursor), cx + 20, lineY,
                                    DraggableWindow.darkTheme ? 0xFFFFFFFF : 0xFF000000, false);
                        } else {
                            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(lineText), cx + 20, lineY,
                                    DraggableWindow.darkTheme ? 0xFFFFFFFF : 0xFF000000, false);
                        }
                    } else {
                        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(lineText), cx + 20, lineY,
                                DraggableWindow.darkTheme ? 0xFFFFFFFF : 0xFF000000, false);
                    }
                    lineY += 10;
                }
            } else {
                String display = value.contains("\n") ? value.split("\n")[0] + "..." : value;
                display = Minecraft.getInstance().font.plainSubstrByWidth(display, cw - 100 - 12);
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(display), cx + 20, cy + ch - 45,
                        DraggableWindow.darkTheme ? 0xFFFFFFFF : 0xFF000000, false);
            }
        }

        int sendColor = isMouseOver(mouseX, mouseY, cx + cw - 65, cy + ch - 55, 50, 40) ? 0xFF00C084 : 0xFF00A884;
        guiGraphics.fill(cx + cw - 65, cy + ch - 55, cx + cw - 15, cy + ch - 15, sendColor);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("➤"), cx + cw - 45, cy + ch - 45, 0xFFFFFFFF, false);
    }

    private boolean renderBackButton(GuiGraphics guiGraphics, int cx, int cy, int mouseX, int mouseY) {
        boolean hovered = isMouseOver(mouseX, mouseY, cx + 8, cy + 15, 32, 30);
        guiGraphics.fill(cx + 8, cy + 15, cx + 40, cy + 45, hovered ? 0x44FFFFFF : 0x33FFFFFF);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("←"), cx + 18, cy + 25, 0xFFFFFFFF, false);
        return hovered;
    }

    private boolean renderButton(GuiGraphics guiGraphics, String text, int x, int y, int width, int height, int mouseX, int mouseY) {
        boolean hovered = isMouseOver(mouseX, mouseY, x, y, width, height);
        int color = hovered ? 0xFF00C084 : 0xFF00A884;
        guiGraphics.fill(x, y, x + width, y + height, color);

        int textX = x + (width - Minecraft.getInstance().font.width(text)) / 2;
        int textY = y + (height - 8) / 2;
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(text), textX, textY, 0xFFFFFFFF, false);

        return hovered;
    }

    private boolean renderIconButton(GuiGraphics guiGraphics, String icon, int x, int y, int width, int height, int mouseX, int mouseY) {
        boolean hovered = isMouseOver(mouseX, mouseY, x, y, width, height);
        guiGraphics.fill(x, y, x + width, y + height, hovered ? 0x44FFFFFF : 0x33FFFFFF);

        int iconX = x + (width - Minecraft.getInstance().font.width(icon)) / 2;
        int iconY = y + (height - 8) / 2;
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(icon), iconX, iconY, 0xFFFFFFFF, false);

        return hovered;
    }

    private boolean isMouseOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private int getMessageHeight(String message, int maxWidth, boolean hasAvatar) {
        List<FormattedText> lines = Minecraft.getInstance().font.getSplitter().splitLines(Component.literal(message), maxWidth - (hasAvatar ? 140 : 120), Style.EMPTY);
        return Math.max(20, lines.size() * 10 + 12);
    }

    private int getMessageWidth(String message) {
        String[] lines = message.split("\n");
        int maxWidth = 0;
        for (String line : lines) {
            int width = Minecraft.getInstance().font.width(line);
            if (width > maxWidth) maxWidth = width;
        }
        return maxWidth;
    }

    private String formatTime(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        if (diff < 60000) {
            return "now";
        } else if (diff < 3600000) {
            return (diff / 60000) + "m";
        } else if (diff < 86400000) {
            return (diff / 3600000) + "h";
        } else {
            return (diff / 86400000) + "d";
        }
    }

    @Override
    public boolean mouseDragged(DraggableWindow window, double mouseRelX, double mouseRelY, double dx, double dy) {
        return false;
    }

    @Override
    public boolean mouseClicked(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {
        int[] rect = getContentRect(window);
        int cx = rect[0], cy = rect[1], cw = rect[2], ch = rect[3];

        int actualMouseY = (int) mouseRelY;
        int scrollOffset = 0;

        switch (currentScreen) {
            case PROFILE:
            case GROUP_SETTINGS:
                scrollOffset = profileScrollOffset;
                break;
            case CHAT_LIST:
                scrollOffset = chatListScrollOffset;
                break;
            case CONVERSATION:
                scrollOffset = conversationScrollOffset;
                break;
            case CONTACTS:
                scrollOffset = contactsScrollOffset;
                break;
            case FRIEND_REQUESTS:
                scrollOffset = friendRequestsScrollOffset;
                break;
            case GROUP_CREATION:
                scrollOffset = groupCreationScrollOffset;
                break;
            case ADD_TO_GROUP:
                scrollOffset = addToGroupScrollOffset;
                break;
        }

        int adjustedMouseY = actualMouseY + scrollOffset;

        boolean localShowRight = cw >= 800;
        int leftWidth = (localShowRight && currentConversationId != null && currentScreen != Screen.CHAT_LIST) ? leftPanelWidth : cw;

        if (currentScreen == Screen.CHAT_LIST) {
            if (handleInputFieldClick((int) mouseRelX, adjustedMouseY, cx, cy, leftWidth, ch)) {
                return true;
            }
        } else {
            if (handleInputFieldClick((int) mouseRelX, adjustedMouseY, cx, cy, cw, ch)) {
                return true;
            }
        }

        if (button == 0) {
            if (chatContextMenu != null && currentScreen == Screen.CHAT_LIST) {
                if (chatContextMenu.mouseClicked((int) mouseRelX, (int) mouseRelY, button)) {
                    chatContextMenu = null;
                    return true;
                } else {
                    if (!chatContextMenu.contains((int) mouseRelX, (int) mouseRelY)) {
                        chatContextMenu = null;
                        return true;
                    }
                }
            }

            switch (currentScreen) {
                case CHAT_LIST:
                    return handleChatListClick((int) mouseRelX, adjustedMouseY, cx, cy, leftWidth, ch);
                case CONVERSATION:
                    return handleConversationClick((int) mouseRelX, adjustedMouseY, cx, cy, cw, ch);
                case CONTACTS:
                    return handleContactsClick((int) mouseRelX, adjustedMouseY, cx, cy, cw, ch);
                case FRIEND_REQUESTS:
                    return handleFriendRequestsClick((int) mouseRelX, adjustedMouseY, cx, cy, cw, ch);
                case PROFILE:
                    return handleProfileClick((int) mouseRelX, adjustedMouseY, cx, cy, cw, ch);
                case GROUP_CREATION:
                    return handleGroupCreationClick((int) mouseRelX, adjustedMouseY, cx, cy, cw, ch);
                case ADD_TO_GROUP:
                    return handleAddToGroupClick((int) mouseRelX, adjustedMouseY, cx, cy, cw, ch);
                case GROUP_SETTINGS:
                    return handleGroupSettingsClick((int) mouseRelX, adjustedMouseY, cx, cy, cw, ch);
            }
        } else if (button == 1) {
            if (currentScreen == Screen.CHAT_LIST) {
                int chatY = cy + 140 - chatListScrollOffset;
                List<DisplayItem> items = new ArrayList<>();
                for (MessengerNetworkManager.Contact contact : networkManager.getContacts()) {
                    if (!passesFilter(contact.playerId, contact.displayName)) continue;
                    items.add(new DisplayItem(contact.playerId, contact.displayName, false));
                }
                for (MessengerNetworkManager.Group group : networkManager.getGroups()) {
                    if (currentFilter.equals("Groups") || passesFilter(group.groupId, group.name)) {
                        items.add(new DisplayItem(group.groupId, group.name, true));
                    }
                }

                for (DisplayItem item : items) {
                    if ((int) mouseRelY >= chatY && (int) mouseRelY <= chatY + 70) {
                        contextTargetId = item.id;
                        contextTargetIsGroup = item.isGroup;
                        chatContextMenu = new ChatContextMenu(this, (int) mouseRelX, (int) mouseRelY, contextTargetId, contextTargetIsGroup);
                        return true;
                    }
                    chatY += 70;
                }
            }
        }

        focusedEditBox = null;
        if (searchInput != null) searchInput.setFocused(false);
        if (messageInput != null) messageInput.setFocused(false);
        if (friendEmailInput != null) friendEmailInput.setFocused(false);
        chatContextMenu = null;

        return false;
    }

    private boolean handleInputFieldClick(int mouseX, int mouseY, int cx, int cy, int cw, int ch) {
        if (currentScreen == Screen.CHAT_LIST &&
                isMouseOver(mouseX, mouseY, cx + 20, cy + 82, cw - 40, 18)) {
            focusedEditBox = searchInput;
            searchInput.setFocused(true);
            return true;
        }

        if (currentScreen == Screen.CONTACTS &&
                isMouseOver(mouseX, mouseY, cx + 32, cy + 105, cw - 150, 20)) {
            focusedEditBox = friendEmailInput;
            friendEmailInput.setFocused(true);
            return true;
        }

        if (currentScreen == Screen.CONVERSATION &&
                isMouseOver(mouseX, mouseY, cx + 20, cy + ch - 50, cw - 100, 35)) {
            focusedEditBox = messageInput;
            messageInput.setFocused(true);
            return true;
        }

        if (currentScreen == Screen.GROUP_CREATION && isMouseOver(mouseX, mouseY, cx + 20, cy + ch - 150, cw - 40, 20)) {
            focusedEditBox = groupNameInput;
            groupNameInput.setFocused(true);
            return true;
        }

        if (currentScreen == Screen.PROFILE && profileViewingId == null) {
            int descX = cx + cw / 2 - 140;
            int descY = cy + ch - 100 - profileScrollOffset + 6;
            int descW = 280;
            int descH = 88;

            if (isMouseOver(mouseX, mouseY, descX, descY, descW, descH)) {
                focusedEditBox = profileDescInput;
                profileDescInput.setFocused(true);
                return true;
            }
        }

        if (currentScreen == Screen.GROUP_SETTINGS && isMouseOver(mouseX, mouseY, cx + 20, cy + 140 - profileScrollOffset, cw - 40, 20)) {
            focusedEditBox = groupNameInput;
            groupNameInput.setFocused(true);
            return true;
        }

        if (currentScreen == Screen.GROUP_SETTINGS && isMouseOver(mouseX, mouseY, cx + 20, cy + 200 - profileScrollOffset, cw - 40, 60)) {
            focusedEditBox = groupDescriptionInput;
            groupDescriptionInput.setFocused(true);
            return true;
        }

        return false;
    }

    private boolean handleHeaderNavigation(int mouseX, int mouseY, int cx, int cy, int width) {
        int headerY = cy + 40;
        int headerHeight = 20;

        if (mouseY >= headerY && mouseY <= headerY + headerHeight) {
            int navWidth = (width - 32) / 3;

            if (mouseX >= cx + 16 && mouseX < cx + 16 + navWidth) {
                currentScreen = Screen.CHAT_LIST;
                return true;
            } else if (mouseX >= cx + 16 + navWidth && mouseX < cx + 16 + 2 * navWidth) {
                currentScreen = Screen.CONTACTS;
                return true;
            } else if (mouseX >= cx + 16 + 2 * navWidth && mouseX < cx + 16 + 3 * navWidth) {
                currentScreen = Screen.FRIEND_REQUESTS;
                return true;
            }
        }
        return false;
    }

    private boolean handleChatListClick(int mouseX, int mouseY, int cx, int cy, int cw, int ch) {
        if (handleHeaderNavigation(mouseX, mouseY, cx, cy, cw)) {
            return true;
        }

        if (isMouseOver(mouseX, mouseY, cx + cw - 40, cy + 12, 30, 30)) {
            profileViewingId = null;
            currentScreen = Screen.PROFILE;
            return true;
        }

        int tabY = cy + 110;
        int tabHeight = 20;
        if (mouseY >= tabY && mouseY <= tabY + tabHeight) {
            String[] filters = {"All", "Unread", "Favorites", "Groups"};
            int tabWidth = (cw - 16) / filters.length;
            for (int i = 0; i < filters.length; i++) {
                int tabX = cx + 8 + i * tabWidth;
                if (mouseX >= tabX && mouseX <= tabX + tabWidth) {
                    currentFilter = filters[i];
                    return true;
                }
            }
        }

        int chatY = cy + 140 - chatListScrollOffset;
        List<DisplayItem> items = new ArrayList<>();
        for (MessengerNetworkManager.Contact contact : networkManager.getContacts()) {
            if (!passesFilter(contact.playerId, contact.displayName)) continue;
            items.add(new DisplayItem(contact.playerId, contact.displayName, false));
        }
        for (MessengerNetworkManager.Group group : networkManager.getGroups()) {
            if (currentFilter.equals("Groups") || passesFilter(group.groupId, group.name)) {
                items.add(new DisplayItem(group.groupId, group.name, true));
            }
        }

        for (DisplayItem item : items) {
            if (mouseY >= chatY && mouseY <= chatY + 70) {
                UUID id = item.id;
                int starX = cx + cw - 20;
                int starY = chatY + 12;
                int avatarSize = 48;
                int avatarX = cx + 12;
                int avatarY = chatY + 10;
                if (isMouseOver(mouseX, mouseY, avatarX, avatarY, avatarSize, avatarSize)) {
                    profileViewingId = id;
                    currentScreen = Screen.PROFILE;
                    return true;
                }
                if (isMouseOver(mouseX, mouseY, starX, starY, 16, 16)) {
                    networkManager.toggleFavorite(id);
                    return true;
                }

                if (mouseX < cx + cw) {
                    long now = System.currentTimeMillis();
                    if (selectedConversationId != null && selectedConversationId.equals(id) &&
                            lastClickItem != null && lastClickItem.equals(id) && (now - lastClickTime) < 400) {
                        currentConversationId = id;
                        currentScreen = Screen.CONVERSATION;
                        networkManager.markConversationRead(id);
                        List<MessengerNetworkManager.Message> conv = networkManager.getConversation(id);
                        for (MessengerNetworkManager.Message m : conv) lastKnownReadState.put(m.messageId, m.isRead);
                        lastClickItem = null;
                        lastClickTime = 0;
                        return true;
                    } else {
                        selectedConversationId = id;
                        lastClickItem = id;
                        lastClickTime = now;
                        return true;
                    }
                }
            }
            chatY += 70;
        }

        return false;
    }

    private boolean handleConversationClick(int mouseX, int mouseY, int cx, int cy, int cw, int ch) {
        if (handleHeaderNavigation(mouseX, mouseY, cx, cy, cw)) {
            return true;
        }

        if (mouseX >= cx + 8 && mouseX <= cx + 40 && mouseY >= cy + 15 && mouseY <= cy + 45) {
            currentScreen = Screen.CHAT_LIST;
            return true;
        }

        if (currentConversationId != null) {
            MessengerNetworkManager.Group group = networkManager.getGroup(currentConversationId);
            if (group != null && isMouseOver(mouseX, mouseY, cx + 50, cy + 12, cw - 100, 36)) {
                openGroupSettings(group.groupId);
                return true;
            }
        }

        int headerAvatarSize = 36;
        if (currentConversationId != null) {
            MessengerNetworkManager.Contact contact = networkManager.getContact(currentConversationId);
            if (contact != null && isMouseOver(mouseX, mouseY, cx + 50, cy + 12, headerAvatarSize, headerAvatarSize)) {
                profileViewingId = contact.playerId;
                currentScreen = Screen.PROFILE;
                return true;
            }
        }

        for (Map.Entry<UUID, int[]> e : messageBounds.entrySet()) {
            int[] b = e.getValue();
            if (b == null || b.length < 7) continue;
            int aX = b[4], aY = b[5], aSize = b[6];
            if (aSize > 0 && isMouseOver(mouseX, mouseY, aX, aY, aSize, aSize)) {
                UUID msgId = e.getKey();
                List<MessengerNetworkManager.Message> msgs = networkManager.getConversation(currentConversationId);
                for (MessengerNetworkManager.Message m : msgs) {
                    if (m.messageId.equals(msgId)) {
                        profileViewingId = m.senderId;
                        currentScreen = Screen.PROFILE;
                        return true;
                    }
                }
            }
        }

        if (isMouseOver(mouseX, mouseY, cx + cw - 65, cy + ch - 55, 50, 40)) {
            String messageText = messageInput.getValue().trim();
            if (!messageText.isEmpty() && currentConversationId != null) {
                networkManager.sendMessage(currentConversationId, messageText);
                messageInput.setValue("");
            }
            return true;
        }

        if (currentConversationId != null) {
            MessengerNetworkManager.Group group = networkManager.getGroup(currentConversationId);
            if (group != null) {
                int[] rect = getContentRect(window);
                int leftWidth = showRightPanel ? Math.min(leftPanelWidth, Math.max(0, rect[2] - rightPanelWidth)) : rect[2];
                int panelX = rect[0] + leftWidth;
                int panelY = rect[1];
                int panelW = Math.min(rightPanelWidth, Math.max(0, rect[2] - leftWidth));
                int panelH = rect[3];

                if (isMouseOver(mouseX, mouseY, panelX + 16, panelY + panelH - 90, 120, 26)) {
                    openGroupSettings(group.groupId);
                    return true;
                }
            }
            MessengerNetworkManager.Contact contact = networkManager.getContact(currentConversationId);
            if (contact != null) {
                int[] rect = getContentRect(window);
                int leftWidth = showRightPanel ? Math.min(leftPanelWidth, Math.max(0, rect[2] - rightPanelWidth)) : rect[2];
                int panelX = rect[0] + leftWidth;
                int panelY = rect[1];
                if (isMouseOver(mouseX, mouseY, panelX + 16, panelY + 250, 120, 26)) {
                    toggleBlockContact(contact.playerId);
                    return true;
                }
            }
        }

        return false;
    }

    public void openConversationById(UUID id) {
        if (id == null) return;
        currentConversationId = id;
        currentScreen = Screen.CONVERSATION;
        networkManager.markConversationRead(id);
        List<MessengerNetworkManager.Message> conv = networkManager.getConversation(id);
        for (MessengerNetworkManager.Message m : conv) lastKnownReadState.put(m.messageId, m.isRead);
    }

    public void openCreateGroupWith(UUID contactId) {
        groupCreationSelected.clear();
        if (contactId != null) groupCreationSelected.add(contactId);
        UUID me = null;
        try {
            if (Minecraft.getInstance().player != null) me = Minecraft.getInstance().player.getUUID();
        } catch (Exception ignored) {
        }
        if (me != null) groupCreationSelected.add(me);
        editingGroupId = null;
        groupNameInput.setValue("");
        currentScreen = Screen.GROUP_CREATION;
    }

    public void openAddToGroupFor(UUID contactId) {
        this.contextTargetId = contactId;
        this.currentScreen = Screen.ADD_TO_GROUP;
    }

    public void openEditGroup(UUID groupId) {
        MessengerNetworkManager.Group g = networkManager.getGroup(groupId);
        if (g == null) return;
        editingGroupId = groupId;
        groupCreationSelected.clear();
        groupCreationSelected.addAll(g.members);
        groupNameInput.setValue(g.name == null ? "" : g.name);
        currentScreen = Screen.GROUP_CREATION;
    }


    public void leaveGroup(UUID groupId) {
        UUID me = null;
        try {
            if (Minecraft.getInstance().player != null) me = Minecraft.getInstance().player.getUUID();
        } catch (Exception ignored) {}

        if (me == null) return;

        boolean left = networkManager.leaveGroup(groupId, me);

        if (left) {
            if (currentConversationId != null && currentConversationId.equals(groupId)) {
                currentConversationId = null;
                currentScreen = Screen.CHAT_LIST;
            }

            selectedConversationId = null;
        }

        chatContextMenu = null;
    }

    public void toggleBlockContact(UUID contactId) {
        if (networkManager.isBlocked(contactId)) networkManager.unblockContact(contactId);
        else networkManager.blockContact(contactId);
        chatContextMenu = null;
    }

    public void closeContextMenu() {
        chatContextMenu = null;
    }

    private boolean handleContactsClick(int mouseX, int mouseY, int cx, int cy, int cw, int ch) {
        if (handleHeaderNavigation(mouseX, mouseY, cx, cy, cw)) {
            return true;
        }

        if (isMouseOver(mouseX, mouseY, cx + cw - 110, cy + 105, 80, 20)) {
            String email = friendEmailInput.getValue().trim();
            if (!email.isEmpty()) {
                networkManager.sendFriendRequest(email);
                friendEmailInput.setValue("");
            }
            return true;
        }

        int contactY = cy + 160 - contactsScrollOffset;
        for (MessengerNetworkManager.Contact contact : networkManager.getContacts()) {
            if (contactY + 60 > cy + ch) break;
            if (contactY < cy + 160) {
                contactY += 55;
                continue;
            }

            if (isMouseOver(mouseX, mouseY, cx + 16, contactY, cw - 32, 50)) {
                openConversationById(contact.playerId);
                return true;
            }
            contactY += 55;
        }

        return false;
    }

    private boolean handleFriendRequestsClick(int mouseX, int mouseY, int cx, int cy, int cw, int ch) {
        if (handleHeaderNavigation(mouseX, mouseY, cx, cy, cw)) {
            return true;
        }

        int requestY = cy + 70 - friendRequestsScrollOffset;
        for (MessengerNetworkManager.FriendRequest request : networkManager.getPendingFriendRequests()) {
            if (isMouseOver(mouseX, mouseY, cx + 32, requestY + 45, 60, 20)) {
                networkManager.respondToFriendRequest(request.requestId, true);
                return true;
            }

            if (isMouseOver(mouseX, mouseY, cx + 102, requestY + 45, 60, 20)) {
                networkManager.respondToFriendRequest(request.requestId, false);
                return true;
            }

            requestY += 75;
        }

        return false;
    }

    private boolean handleProfileClick(int mouseX, int mouseY, int cx, int cy, int cw, int ch) {
        int adjustedMouseY = mouseY + profileScrollOffset;

        if (handleHeaderNavigation(mouseX, mouseY, cx, cy, cw)) {
            return true;
        }

        if (currentScreen == Screen.PROFILE && profileViewingId == null) {
            int scrollY = cy + 60 - profileScrollOffset;
            int infoY = scrollY + 160 + 60 + 60;

            int descX = cx + cw / 2 - 140;
            int descY = infoY + 26;
            int descW = 280;
            int descH = 88;

            if (isMouseOver(mouseX, adjustedMouseY, descX, descY, descW, descH)) {
                focusedEditBox = profileDescInput;
                profileDescInput.setFocused(true);
                return true;
            }

            int saveButtonY = infoY + 130;
            if (isMouseOver(mouseX, adjustedMouseY, cx + cw / 2 - 60, saveButtonY, 120, 30)) {
                saveProfileChanges();
                return true;
            }
        }

        if (focusedEditBox == profileDescInput && currentScreen == Screen.PROFILE) {
            int scrollY = cy + 60 - profileScrollOffset;
            int infoY = scrollY + 160 + 60 + 60;
            int descX = cx + cw / 2 - 140;
            int descY = infoY + 26;
            int descW = 280;
            int descH = 88;

            if (!isMouseOver(mouseX, adjustedMouseY, descX, descY, descW, descH)) {
                focusedEditBox = null;
                profileDescInput.setFocused(false);
                saveProfileChanges();
                return true;
            }
        }

        return false;
    }

    private void saveProfileChanges() {
        try {
            AccountManager.Account account = AccountManager.getInstance().getAccount();
            if (account != null && profileDescInput != null) {
                account.description = profileDescInput.getValue();
                AccountManager.getInstance().save();

                MessengerNetworkManager.Contact myContact = networkManager.getContact(Minecraft.getInstance().player.getUUID());
                if (myContact != null) {
                    myContact.description = account.description;
                }
            }
        } catch (Exception e) {
            System.err.println("[MessengerApp] Failed to save profile description: " + e);
        }
    }

    private void renderGroupCreationView(GuiGraphics guiGraphics, int cx, int cy, int cw, int ch, int mouseX, int mouseY) {
        renderHeader(guiGraphics, "Create Group", cx, cy, cw, mouseX, mouseY);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Select people to add to the group."), cx + 20, cy + 80, 0xFFFFFFFF, false);

        int y = cy + 110 - groupCreationScrollOffset;
        for (MessengerNetworkManager.Contact contact : networkManager.getContacts()) {
            if (y + 20 > cy + ch - 160) break;
            if (y < cy + 110) {
                y += 22;
                continue;
            }

            boolean sel = groupCreationSelected.contains(contact.playerId);
            int boxX = cx + 20;
            guiGraphics.fill(boxX, y, boxX + 16, y + 16, sel ? 0xFF00A884 : 0xFF333333);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(contact.displayName), boxX + 26, y, 0xFFFFFFFF, false);
            y += 22;
        }

        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Group Name"), cx + 20, cy + ch - 170, 0xFF8696A0, false);
        groupNameInput.setX(cx + 20);
        groupNameInput.setY(cy + ch - 150);
        groupNameInput.setWidth(cw - 40);
        String value = groupNameInput.getValue();
        boolean focused = focusedEditBox == groupNameInput;
        if (value.isEmpty() && !focused) {
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Group name"), cx + 24, cy + ch - 146, 0xFF667781, false);
        } else {
            String display = value.isEmpty() ? "" : Minecraft.getInstance().font.plainSubstrByWidth(value, cw - 48);
            if (focused) {
                boolean caretOn = (System.currentTimeMillis() / 500) % 2 == 0;
                if (caretOn) display = display + "|";
            }
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(display), cx + 24, cy + ch - 146, 0xFFFFFFFF, false);
        }

        renderButton(guiGraphics, "Create Group", cx + cw - 160, cy + ch - 120, 140, 36, mouseX, mouseY);
        renderButton(guiGraphics, "Cancel", cx + cw - 320, cy + ch - 120, 120, 36, mouseX, mouseY);

        if (groupCreationScrollOffset > 0) {
            guiGraphics.fill(cx + cw - 10, cy + 110, cx + cw - 5, cy + ch - 160, 0x66FFFFFF);
        }
    }

    private boolean handleGroupCreationClick(int mouseX, int mouseY, int cx, int cy, int cw, int ch) {
        int adjustedMouseY = mouseY + groupCreationScrollOffset;

        if (handleHeaderNavigation(mouseX, mouseY, cx, cy, cw)) return true;

        int y = cy + 110 - groupCreationScrollOffset;
        for (MessengerNetworkManager.Contact contact : networkManager.getContacts()) {
            if (y + 20 > cy + ch - 160) break;
            if (y < cy + 110) {
                y += 22;
                continue;
            }

            if (isMouseOver(mouseX, adjustedMouseY, cx + 20, y, cw - 40, 18)) {
                if (groupCreationSelected.contains(contact.playerId)) groupCreationSelected.remove(contact.playerId);
                else groupCreationSelected.add(contact.playerId);
                return true;
            }
            y += 22;
        }

        if (isMouseOver(mouseX, mouseY, cx + cw - 160, cy + ch - 120, 140, 36)) {
            String name = groupNameInput.getValue().trim();
            if (name.isEmpty()) name = "Group " + (networkManager.getGroups().size() + 1);
            List<UUID> members = new ArrayList<>(groupCreationSelected);
            UUID me = null;
            try {
                if (Minecraft.getInstance().player != null) me = Minecraft.getInstance().player.getUUID();
            } catch (Exception ignored) {
            }
            if (me != null && !members.contains(me)) members.add(me);
            if (editingGroupId != null) {
                networkManager.changeGroupName(editingGroupId, me, name);
                MessengerNetworkManager.Group g = networkManager.getGroup(editingGroupId);
                if (g != null) {
                    for (UUID m : members) {
                        if (!g.members.contains(m)) networkManager.addMemberToGroup(g.groupId, me, m);
                    }
                }
                currentConversationId = editingGroupId;
            } else {
                UUID gid = networkManager.createGroup(name, members);
                currentConversationId = gid;
            }
            currentScreen = Screen.CONVERSATION;
            groupCreationSelected.clear();
            groupNameInput.setValue("");
            editingGroupId = null;
            return true;
        }

        if (isMouseOver(mouseX, mouseY, cx + cw - 320, cy + ch - 120, 120, 36)) {
            currentScreen = Screen.CHAT_LIST;
            groupCreationSelected.clear();
            groupNameInput.setValue("");
            return true;
        }

        return false;
    }

    private void renderAddToGroupView(GuiGraphics guiGraphics, int cx, int cy, int cw, int ch, int mouseX, int mouseY) {
        renderHeader(guiGraphics, "Add to Group", cx, cy, cw, mouseX, mouseY);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Choose a group to add the person to, or create a new group."), cx + 20, cy + 80, 0xFFFFFFFF, false);

        int y = cy + 110 - addToGroupScrollOffset;
        for (MessengerNetworkManager.Group group : networkManager.getGroups()) {
            if (y + 30 > cy + ch - 140) break;
            if (y < cy + 110) {
                y += 34;
                continue;
            }

            guiGraphics.fill(cx + 20, y, cx + cw - 20, y + 26, 0x22333333);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(group.name), cx + 28, y + 6, 0xFFFFFFFF, false);
            renderButton(guiGraphics, "Add", cx + cw - 100, y + 4, 70, 20, mouseX, mouseY);
            y += 34;
        }

        renderButton(guiGraphics, "Create New Group", cx + 20, cy + ch - 120, 160, 36, mouseX, mouseY);
        renderButton(guiGraphics, "Cancel", cx + 200, cy + ch - 120, 120, 36, mouseX, mouseY);

        if (addToGroupScrollOffset > 0) {
            guiGraphics.fill(cx + cw - 10, cy + 110, cx + cw - 5, cy + ch - 140, 0x66FFFFFF);
        }
    }

    private boolean handleAddToGroupClick(int mouseX, int mouseY, int cx, int cy, int cw, int ch) {
        int adjustedMouseY = mouseY + addToGroupScrollOffset;

        if (handleHeaderNavigation(mouseX, mouseY, cx, cy, cw)) return true;

        int y = cy + 110 - addToGroupScrollOffset;
        for (MessengerNetworkManager.Group group : networkManager.getGroups()) {
            if (y + 30 > cy + ch - 140) break;
            if (y < cy + 110) {
                y += 34;
                continue;
            }

            if (isMouseOver(mouseX, adjustedMouseY, cx + cw - 100, y + 4, 70, 20)) {
                if (contextTargetId != null) {
                    UUID actor = null;
                    try {
                        if (Minecraft.getInstance().player != null) actor = Minecraft.getInstance().player.getUUID();
                    } catch (Exception ignored) {
                    }
                    if (actor != null) networkManager.addMemberToGroup(group.groupId, actor, contextTargetId);
                }
                currentScreen = Screen.CHAT_LIST;
                chatContextMenu = null;
                return true;
            }
            y += 34;
        }

        if (isMouseOver(mouseX, mouseY, cx + 20, cy + ch - 120, 160, 36)) {
            groupCreationSelected.clear();
            if (contextTargetId != null) groupCreationSelected.add(contextTargetId);
            currentScreen = Screen.GROUP_CREATION;
            chatContextMenu = null;
            return true;
        }

        if (isMouseOver(mouseX, mouseY, cx + 200, cy + ch - 120, 120, 36)) {
            currentScreen = Screen.CHAT_LIST;
            chatContextMenu = null;
            return true;
        }

        return false;
    }

    @Override
    public void tick() {
        if (currentScreen == Screen.CONVERSATION && currentConversationId != null) {
            List<MessengerNetworkManager.Message> messages = networkManager.getConversation(currentConversationId);
            for (MessengerNetworkManager.Message message : messages) {
                if (!message.isFromMe() && !message.isRead) {
                    networkManager.markMessageRead(currentConversationId, message.messageId);
                    lastKnownReadState.put(message.messageId, true);
                    readAnimStart.put(message.messageId, System.currentTimeMillis());
                }
            }
        }
    }


    private void ensureGroupInputsInitialized() {
        if (groupNameInput == null) {
            Minecraft mc = Minecraft.getInstance();

            this.groupNameInput = new EditBox(mc.font, 0, 0, 200, 20, Component.literal("Group name"));
            this.groupNameInput.setBordered(false);
            this.groupNameInput.setMaxLength(100);
            this.groupNameInput.setEditable(true);

            this.groupNameInput.setResponder(text -> {
            });

            this.groupDescriptionInput = new EditBox(mc.font, 0, 0, 200, 60, Component.literal("Group description")) {
                @Override
                public boolean charTyped(char codePoint, int modifiers) {
                    return false;
                }

                @Override
                public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                    return false;
                }
            };
            this.groupDescriptionInput.setValue("Coming soon...");
            this.groupDescriptionInput.setBordered(false);
            this.groupDescriptionInput.setMaxLength(500);
            this.groupDescriptionInput.setEditable(false);
        }
    }

    public void openGroupSettings(UUID groupId) {
        this.contextTargetId = groupId;
        this.currentScreen = Screen.GROUP_SETTINGS;
        this.profileScrollOffset = 0;

        ensureGroupInputsInitialized();

        MessengerNetworkManager.Group group = networkManager.getGroup(groupId);
        if (group != null) {
            groupNameInput.setValue(group.name == null ? "" : group.name);
            groupNameInput.setFocused(false);
        }
    }

    private void renderGroupSettingsView(GuiGraphics guiGraphics, int cx, int cy, int cw, int ch, int mouseX, int mouseY) {
        renderHeader(guiGraphics, "Group Settings", cx, cy, cw, mouseX, mouseY);

        if (contextTargetId == null) {
            currentScreen = Screen.CHAT_LIST;
            return;
        }

        MessengerNetworkManager.Group group = networkManager.getGroup(contextTargetId);
        if (group == null) {
            currentScreen = Screen.CHAT_LIST;
            return;
        }

        int scrollY = cy + 60 - profileScrollOffset;

        int avatarSize = 80;
        guiGraphics.fill(cx + cw / 2 - avatarSize / 2, scrollY + 20, cx + cw / 2 + avatarSize / 2, scrollY + 20 + avatarSize, 0xFF8E44AD);
        String initials = group.name != null && !group.name.isEmpty() ?
                group.name.substring(0, Math.min(2, group.name.length())).toUpperCase() : "GR";
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(initials),
                cx + cw / 2 - 8, scrollY + 20 + avatarSize / 2 - 4, 0xFFFFFFFF, false);

        int infoY = scrollY + 120;
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Group Name"), cx + 20, infoY, 0xFF8696A0, false);

        groupNameInput.setX(cx + 20);
        groupNameInput.setY(infoY + 20);
        groupNameInput.setWidth(cw - 40);

        String nameValue = groupNameInput.getValue();
        boolean nameFocused = focusedEditBox == groupNameInput;

        int nameBgColor = DraggableWindow.darkTheme ? 0xFF2A3942 : 0xFFFFFFFF;
        guiGraphics.fill(cx + 20, infoY + 20, cx + cw - 20, infoY + 40, nameBgColor);

        if (nameValue.isEmpty() && !nameFocused) {
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Group name"), cx + 24, infoY + 24, 0xFF667781, false);
        } else {
            String display = nameValue.isEmpty() ? "" : Minecraft.getInstance().font.plainSubstrByWidth(nameValue, cw - 48);
            if (nameFocused) {
                boolean caretOn = (System.currentTimeMillis() / 500) % 2 == 0;
                if (caretOn) display = display + "|";
            }
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(display), cx + 24, infoY + 24, 0xFFFFFFFF, false);
        }

        int borderColor = nameFocused ? 0xFF00A884 : 0xFF8696A0;
        guiGraphics.fill(cx + 20, infoY + 20, cx + cw - 20, infoY + 21, borderColor);
        guiGraphics.fill(cx + 20, infoY + 39, cx + cw - 20, infoY + 40, borderColor);
        guiGraphics.fill(cx + 20, infoY + 20, cx + 21, infoY + 40, borderColor);
        guiGraphics.fill(cx + cw - 21, infoY + 20, cx + cw - 20, infoY + 40, borderColor);

        infoY += 60;
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Group Description"), cx + 20, infoY, 0xFF8696A0, false);

        String groupDescText = "Coming soon...";
        List<FormattedText> descLines = Minecraft.getInstance().font.getSplitter().splitLines(
                Component.literal(groupDescText), cw - 48, Style.EMPTY);
        int descY = infoY + 20;
        for (FormattedText line : descLines) {
            guiGraphics.drawString(Minecraft.getInstance().font, line.getString(), cx + 24, descY, 0xFF8696A0, false);
            descY += 10;
        }

        infoY += 100;
        if (renderButton(guiGraphics, "Save Changes", cx + 20, infoY, 140, 30, mouseX, mouseY)) {
            UUID me = null;
            try {
                if (Minecraft.getInstance().player != null) me = Minecraft.getInstance().player.getUUID();
            } catch (Exception ignored) {}

            if (me != null && networkManager.isGroupAdmin(group.groupId, me)) {
                String newName = groupNameInput.getValue().trim();

                if (!newName.isEmpty() && !newName.equals(group.name)) {
                    networkManager.changeGroupName(group.groupId, me, newName);
                    group.name = newName;
                }
            }
        }

        infoY += 50;
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Members (" + group.members.size() + ")"), cx + 20, infoY, 0xFF8696A0, false);
        infoY += 25;

        UUID me = null;
        try {
            if (Minecraft.getInstance().player != null) me = Minecraft.getInstance().player.getUUID();
        } catch (Exception ignored) {}

        boolean isAdmin = me != null && networkManager.isGroupAdmin(group.groupId, me);

        for (UUID memberId : group.members) {
            if (infoY + 30 > cy + ch) break;

            MessengerNetworkManager.Contact member = networkManager.getContact(memberId);
            String memberName = member != null ? member.displayName : memberId.toString();
            boolean isMemberAdmin = memberId.equals(group.adminId);

            guiGraphics.fill(cx + 20, infoY, cx + cw - 20, infoY + 25, 0x22333333);

            int avatarSmallSize = 20;
            ResourceLocation avatar = getOrCreateAvatarTexture(memberId, avatarSmallSize);
            guiGraphics.blit(avatar, cx + 25, infoY + 2, avatarSmallSize, avatarSmallSize, 0, 0, avatarSmallSize, avatarSmallSize);

            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(memberName), cx + 50, infoY + 5, 0xFFFFFFFF, false);

            if (isMemberAdmin) {
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Admin"), cx + cw - 80, infoY + 5, 0xFF00A884, false);
            }

            infoY += 30;
        }

        if (me != null && group.members.contains(me)) {
            int leaveY = cy + ch - 60;
            boolean leaveHovered = isMouseOver(mouseX, mouseY, cx + cw - 160, leaveY, 140, 30);
            int leaveColor = leaveHovered ? 0x66FF4444 : 0x44FF4444;
            guiGraphics.fill(cx + cw - 160, leaveY, cx + cw - 20, leaveY + 30, leaveColor);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Leave Group"), cx + cw - 150, leaveY + 8, 0xFFFFFFFF, false);
        }

        if (profileScrollOffset > 0) {
            guiGraphics.fill(cx + cw - 10, cy + 60, cx + cw - 5, cy + ch, 0x66FFFFFF);
        }
    }

    private boolean handleGroupSettingsClick(int mouseX, int mouseY, int cx, int cy, int cw, int ch) {
        if (contextTargetId == null) return false;

        MessengerNetworkManager.Group group = networkManager.getGroup(contextTargetId);
        if (group == null) return false;

        UUID me = null;
        try {
            if (Minecraft.getInstance().player != null) me = Minecraft.getInstance().player.getUUID();
        } catch (Exception ignored) {}

        boolean isAdmin = me != null && networkManager.isGroupAdmin(group.groupId, me);

        int infoY = cy + 140 - profileScrollOffset;
        if (isMouseOver(mouseX, mouseY, cx + 20, infoY, cw - 40, 20)) {
            focusedEditBox = groupNameInput;
            groupNameInput.setFocused(true);
            return true;
        }

        infoY += 60;
        if (isMouseOver(mouseX, mouseY, cx + 20, infoY, cw - 40, 60)) {
            focusedEditBox = groupDescriptionInput;
            groupDescriptionInput.setFocused(true);
            return true;
        }

        infoY += 100;
        if (isMouseOver(mouseX, mouseY, cx + 20, infoY, 140, 30)) {
            if (me != null && networkManager.isGroupAdmin(group.groupId, me)) {
                String newName = groupNameInput.getValue().trim();
                String newDesc = groupDescriptionInput.getValue().trim();

                if (!newName.isEmpty() && !newName.equals(group.name)) {
                    networkManager.changeGroupName(group.groupId, me, newName);
                    group.name = newName;
                }

                if (!newDesc.equals(group.description)) {
                    networkManager.changeGroupDescription(group.groupId, me, newDesc);
                    group.description = newDesc;
                }
            }
            return true;
        }

        if (me != null && group.members.contains(me)) {
            int leaveY = cy + ch - 60;
            if (isMouseOver(mouseX, mouseY, cx + cw - 160, leaveY, 140, 30)) {
                leaveGroup(group.groupId);
                return true;
            }
        }

        return false;
    }
}
