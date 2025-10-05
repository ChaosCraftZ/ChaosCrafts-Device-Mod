package net.chaoscraft.chaoscrafts_device_mod.Client.Screen.session;

import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Device.DeviceState;
import net.chaoscraft.chaoscrafts_device_mod.Core.Network.NetworkHandler;
import net.chaoscraft.chaoscrafts_device_mod.Core.Network.packet.LaptopDevicePackets;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.Optional;

/**
 * Client-side session wrapper for interacting with a specific laptop device.
 * Maintains a local mirror of the server-owned {@link DeviceState} and forwards
 * user actions back to the server.
 */
public class ClientDeviceSession {
    private final BlockPos devicePos;
    private final DeviceState state = new DeviceState();
    private boolean initialized = false;
    private String lastMessage = "";

    public ClientDeviceSession(BlockPos pos) {
        this.devicePos = pos;
        // Clear default data so UI remains blank until the first sync arrives.
        this.state.loadFromTag(new CompoundTag());
    }

    public BlockPos getDevicePos() {
        return devicePos;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void requestInitialState() {
        if (devicePos != null) {
            NetworkHandler.sendToServer(new LaptopDevicePackets.RequestState(devicePos));
        }
    }

    public void close() {
        if (devicePos != null) {
            NetworkHandler.sendToServer(new LaptopDevicePackets.Close(devicePos));
        }
    }

    public void applyState(CompoundTag tag) {
        if (tag == null) {
            return;
        }
        this.state.loadFromTag(tag);
        this.initialized = true;
    }

    public void applyActionResponse(LaptopDevicePackets.ActionResponse response) {
        if (response != null && !response.success && response.message != null) {
            this.lastMessage = response.message;
        } else {
            this.lastMessage = "";
        }
    }

    public List<DeviceState.DesktopIconData> getDesktopIcons() {
        return state.copyDesktopIcons();
    }

    public List<DeviceState.VirtualFileSnapshot> listDirectory(String path) {
        return state.listDirectory(path);
    }

    public Optional<DeviceState.VirtualFileSnapshot> getFile(String path) {
        return state.getFile(path);
    }

    public void sendMoveIcon(String name, int x, int y) {
        if (!isValidName(name)) return;
        CompoundTag payload = new CompoundTag();
        payload.putString("Name", name);
        payload.putInt("X", x);
        payload.putInt("Y", y);
        sendAction(LaptopDevicePackets.ActionType.MOVE_ICON, payload);
    }

    public void sendRenameIcon(String oldName, String newName) {
        if (!isValidName(oldName) || !isValidName(newName)) return;
        CompoundTag payload = new CompoundTag();
        payload.putString("Old", oldName);
        payload.putString("New", newName);
        sendAction(LaptopDevicePackets.ActionType.RENAME_ICON, payload);
    }

    public void sendAddIcon(String name, int x, int y) {
        if (!isValidName(name)) return;
        CompoundTag payload = new CompoundTag();
        payload.putString("Name", name);
        payload.putInt("X", x);
        payload.putInt("Y", y);
        sendAction(LaptopDevicePackets.ActionType.ADD_ICON, payload);
    }

    public void sendRemoveIcon(String name) {
        if (!isValidName(name)) return;
        CompoundTag payload = new CompoundTag();
        payload.putString("Name", name);
        sendAction(LaptopDevicePackets.ActionType.REMOVE_ICON, payload);
    }

    public void sendCreatePath(String parent, String name, boolean directory, String content) {
        if (!isValidName(name)) return;
        CompoundTag payload = new CompoundTag();
        payload.putString("Parent", normalizePath(parent));
        payload.putString("Name", name);
        payload.putBoolean("Dir", directory);
        if (!directory && content != null) {
            payload.putString("Content", content);
        }
        sendAction(LaptopDevicePackets.ActionType.CREATE_PATH, payload);
    }

    public void sendDeletePath(String path) {
        CompoundTag payload = new CompoundTag();
        payload.putString("Path", normalizePath(path));
        sendAction(LaptopDevicePackets.ActionType.DELETE_PATH, payload);
    }

    public void sendRenamePath(String path, String newName) {
        if (!isValidName(newName)) return;
        CompoundTag payload = new CompoundTag();
        payload.putString("Path", normalizePath(path));
        payload.putString("New", newName);
        sendAction(LaptopDevicePackets.ActionType.RENAME_PATH, payload);
    }

    public void sendUpdateFile(String path, String content) {
        CompoundTag payload = new CompoundTag();
        payload.putString("Path", normalizePath(path));
        payload.putString("Content", content == null ? "" : content);
        sendAction(LaptopDevicePackets.ActionType.UPDATE_FILE_CONTENT, payload);
    }

    private void sendAction(LaptopDevicePackets.ActionType type, CompoundTag payload) {
        if (devicePos == null || type == null) {
            return;
        }
        NetworkHandler.sendToServer(new LaptopDevicePackets.Action(devicePos, type, payload));
    }

    private static boolean isValidName(String name) {
        return name != null && !name.isBlank();
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String trimmed = path.trim();
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        return trimmed;
    }
}
