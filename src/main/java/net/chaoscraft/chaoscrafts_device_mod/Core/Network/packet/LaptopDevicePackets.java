package net.chaoscraft.chaoscrafts_device_mod.Core.Network.packet;

import net.chaoscraft.chaoscrafts_device_mod.Core.Network.NetworkHandler;
import net.chaoscraft.chaoscrafts_device_mod.Core.PC.block.entity.LaptopEntity;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Device.DeviceState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class LaptopDevicePackets {
    private static final String KEY_NAME = "Name";
    private static final String KEY_X = "X";
    private static final String KEY_Y = "Y";
    private static final String KEY_OLD = "Old";
    private static final String KEY_NEW = "New";
    private static final String KEY_PARENT = "Parent";
    private static final String KEY_DIR = "Dir";
    private static final String KEY_CONTENT = "Content";
    private static final String KEY_PATH = "Path";

    private LaptopDevicePackets() {}

    public enum ActionType {
        MOVE_ICON,
        RENAME_ICON,
        ADD_ICON,
        REMOVE_ICON,
        CREATE_PATH,
        DELETE_PATH,
        RENAME_PATH,
        UPDATE_FILE_CONTENT
    }

    public static class RequestState {
        public final BlockPos pos;
        public RequestState(BlockPos pos) { this.pos = pos; }
        public static void encode(RequestState msg, FriendlyByteBuf buf) { buf.writeBlockPos(msg.pos); }
        public static RequestState decode(FriendlyByteBuf buf) { return new RequestState(buf.readBlockPos()); }
        public static void handle(RequestState msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                BlockEntity be = player.level().getBlockEntity(msg.pos);
                if (!(be instanceof LaptopEntity laptop)) return;
                laptop.addActiveUser(player.getUUID());
                DeviceState snapshot = laptop.snapshotDeviceState();
                NetworkHandler.sendToPlayer(new StateUpdate(msg.pos, snapshot.saveToTag()), player);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class StateUpdate {
        public final BlockPos pos;
        public final CompoundTag state;
        public StateUpdate(BlockPos pos, CompoundTag state) {
            this.pos = pos;
            this.state = state == null ? new CompoundTag() : state.copy();
        }
        public static void encode(StateUpdate msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.pos);
            buf.writeNbt(msg.state);
        }
        public static StateUpdate decode(FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            CompoundTag tag = buf.readNbt();
            return new StateUpdate(pos, tag == null ? new CompoundTag() : tag);
        }
        public static void handle(StateUpdate msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.chaoscraft.chaoscrafts_device_mod.Client.Screen.DesktopScreen.applyRemoteState(msg.pos, msg.state)));
            ctx.get().setPacketHandled(true);
        }
    }

    public static class Action {
        public final BlockPos pos;
        public final ActionType action;
        public final CompoundTag payload;
        public Action(BlockPos pos, ActionType action, CompoundTag payload) {
            this.pos = pos;
            this.action = action;
            this.payload = payload == null ? new CompoundTag() : payload;
        }
        public static void encode(Action msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.pos);
            buf.writeEnum(msg.action);
            buf.writeNbt(msg.payload);
        }
        public static Action decode(FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            ActionType type = buf.readEnum(ActionType.class);
            CompoundTag payload = buf.readNbt();
            return new Action(pos, type, payload == null ? new CompoundTag() : payload);
        }
        public static void handle(Action msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                BlockEntity be = player.level().getBlockEntity(msg.pos);
                if (!(be instanceof LaptopEntity laptop)) return;
                ActionResult result = applyAction(laptop, msg.action, msg.payload);
                NetworkHandler.sendToPlayer(new ActionResponse(msg.action, result.success, result.message), player);
                if (result.success) {
                    laptop.broadcastDeviceState();
                }
            });
            ctx.get().setPacketHandled(true);
        }

        private static ActionResult applyAction(LaptopEntity laptop, ActionType type, CompoundTag data) {
            switch (type) {
                case MOVE_ICON: {
                    String name = data.getString(KEY_NAME);
                    int x = data.getInt(KEY_X);
                    int y = data.getInt(KEY_Y);
                    if (name == null || name.isBlank()) return ActionResult.fail("Invalid icon");
                    boolean success = laptop.updateDeviceState(ds -> ds.updateDesktopIconPosition(name, x, y));
                    return success ? ActionResult.success() : ActionResult.fail("Icon missing");
                }
                case RENAME_ICON: {
                    String oldName = data.getString(KEY_OLD);
                    String newName = data.getString(KEY_NEW);
                    if (oldName == null || newName == null || newName.isBlank()) return ActionResult.fail("Invalid icon");
                    boolean success = laptop.updateDeviceState(ds -> ds.renameDesktopIcon(oldName, newName));
                    return success ? ActionResult.success() : ActionResult.fail("Name already exists");
                }
                case ADD_ICON: {
                    String name = data.getString(KEY_NAME);
                    int x = data.getInt(KEY_X);
                    int y = data.getInt(KEY_Y);
                    if (name == null || name.isBlank()) return ActionResult.fail("Invalid icon");
                    boolean success = laptop.updateDeviceState(ds -> ds.addDesktopIcon(name, x, y));
                    return success ? ActionResult.success() : ActionResult.fail("Duplicate icon");
                }
                case REMOVE_ICON: {
                    String name = data.getString(KEY_NAME);
                    if (name == null || name.isBlank()) return ActionResult.fail("Invalid icon");
                    boolean success = laptop.updateDeviceState(ds -> ds.removeDesktopIcon(name));
                    return success ? ActionResult.success() : ActionResult.fail("Icon missing");
                }
                case CREATE_PATH: {
                    String parent = data.getString(KEY_PARENT);
                    String name = data.getString(KEY_NAME);
                    boolean dir = data.getBoolean(KEY_DIR);
                    String content = data.contains(KEY_CONTENT) ? data.getString(KEY_CONTENT) : "";
                    if (name == null || name.isBlank()) return ActionResult.fail("Invalid name");
                    boolean success = laptop.updateDeviceState(ds -> ds.createPath(parent, name, dir, content));
                    return success ? ActionResult.success() : ActionResult.fail("Cannot create path");
                }
                case DELETE_PATH: {
                    String path = data.getString(KEY_PATH);
                    if (path == null || path.isBlank()) return ActionResult.fail("Invalid path");
                    boolean success = laptop.updateDeviceState(ds -> ds.deletePath(path));
                    return success ? ActionResult.success() : ActionResult.fail("Cannot delete path");
                }
                case RENAME_PATH: {
                    String path = data.getString(KEY_PATH);
                    String newName = data.getString(KEY_NEW);
                    if (path == null || path.isBlank() || newName == null || newName.isBlank()) return ActionResult.fail("Invalid path");
                    boolean success = laptop.updateDeviceState(ds -> ds.renamePath(path, newName));
                    return success ? ActionResult.success() : ActionResult.fail("Cannot rename");
                }
                case UPDATE_FILE_CONTENT: {
                    String path = data.getString(KEY_PATH);
                    if (path == null || path.isBlank()) return ActionResult.fail("Invalid path");
                    String content = data.contains(KEY_CONTENT) ? data.getString(KEY_CONTENT) : "";
                    boolean success = laptop.updateDeviceState(ds -> ds.updateFileContent(path, content));
                    return success ? ActionResult.success() : ActionResult.fail("Cannot update file");
                }
            }
            return ActionResult.fail("Invalid request");
        }
    }

    public static class ActionResponse {
        public final ActionType action;
        public final boolean success;
        public final String message;
        public ActionResponse(ActionType action, boolean success, String message) {
            this.action = action;
            this.success = success;
            this.message = message == null ? "" : message;
        }
        public static void encode(ActionResponse msg, FriendlyByteBuf buf) {
            buf.writeEnum(msg.action);
            buf.writeBoolean(msg.success);
            buf.writeUtf(msg.message, 256);
        }
        public static ActionResponse decode(FriendlyByteBuf buf) {
            ActionType type = buf.readEnum(ActionType.class);
            boolean success = buf.readBoolean();
            String message = buf.readUtf();
            return new ActionResponse(type, success, message);
        }
        public static void handle(ActionResponse msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.chaoscraft.chaoscrafts_device_mod.Client.Screen.DesktopScreen.handleActionResponse(msg)));
            ctx.get().setPacketHandled(true);
        }
    }

    public static class Close {
        public final BlockPos pos;
        public Close(BlockPos pos) { this.pos = pos; }
        public static void encode(Close msg, FriendlyByteBuf buf) { buf.writeBlockPos(msg.pos); }
        public static Close decode(FriendlyByteBuf buf) { return new Close(buf.readBlockPos()); }
        public static void handle(Close msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                BlockEntity be = player.level().getBlockEntity(msg.pos);
                if (be instanceof LaptopEntity laptop) {
                    laptop.removeActiveUser(player.getUUID());
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    private static class ActionResult {
        final boolean success;
        final String message;
        private ActionResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        static ActionResult success() { return new ActionResult(true, ""); }
        static ActionResult fail(String message) { return new ActionResult(false, message == null ? "" : message); }
    }
}
