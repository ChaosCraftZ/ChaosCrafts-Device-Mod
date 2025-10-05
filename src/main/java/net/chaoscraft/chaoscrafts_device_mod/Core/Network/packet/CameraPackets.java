package net.chaoscraft.chaoscrafts_device_mod.Core.Network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.chaoscraft.chaoscrafts_device_mod.Client.Camera.CameraInfo;
import net.chaoscraft.chaoscrafts_device_mod.Core.Network.NetworkHandler;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class CameraPackets {
    public static class CameraListRequest {
        public CameraListRequest() {}
        public static void encode(CameraListRequest msg, FriendlyByteBuf buf) {}
        public static CameraListRequest decode(FriendlyByteBuf buf) { return new CameraListRequest(); }
        public static void handle(CameraListRequest msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                // Server-side: build list and send back - CameraManager will handle
                net.chaoscraft.chaoscrafts_device_mod.Client.Camera.CameraManager mgr = net.chaoscraft.chaoscrafts_device_mod.Client.Camera.CameraManager.getInstance();
                List<CameraInfo> list = mgr.listCameras();
                NetworkHandler.sendToPlayer(new CameraListResponse(list), ctx.get().getSender());
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class CameraListResponse {
        public final List<CameraInfo> cameras;
        public CameraListResponse(List<CameraInfo> cams) { this.cameras = cams == null ? new ArrayList<>() : cams; }
        public static void encode(CameraListResponse msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.cameras.size());
            for (CameraInfo c : msg.cameras) {
                buf.writeUtf(c.id);
                buf.writeUUID(c.owner);
                buf.writeInt(c.x); buf.writeInt(c.y); buf.writeInt(c.z);
                buf.writeUtf(c.dimension == null ? "" : c.dimension);
                buf.writeUtf(c.name == null ? "" : c.name);
                buf.writeBoolean(c.isPublic);
                buf.writeInt(c.width); buf.writeInt(c.height); buf.writeInt(c.fps);
            }
        }
        public static CameraListResponse decode(FriendlyByteBuf buf) {
            int n = buf.readInt();
            List<CameraInfo> cams = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                CameraInfo c = new CameraInfo();
                c.id = buf.readUtf();
                c.owner = buf.readUUID();
                c.x = buf.readInt(); c.y = buf.readInt(); c.z = buf.readInt();
                c.dimension = buf.readUtf(); c.name = buf.readUtf();
                c.isPublic = buf.readBoolean();
                c.width = buf.readInt(); c.height = buf.readInt(); c.fps = buf.readInt();
                cams.add(c);
            }
            return new CameraListResponse(cams);
        }
        public static void handle(CameraListResponse msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                // Client side: hand off to HomeSecurityApp UI (store in client state)
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.chaoscraft.chaoscrafts_device_mod.Client.Apps.BaseApps.HomeSecurityClientState.setCameras(msg.cameras));
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class CameraSubscribeRequest {
        public String cameraId;
        public CameraSubscribeRequest() {}
        public CameraSubscribeRequest(String id) { this.cameraId = id; }
        public static void encode(CameraSubscribeRequest msg, FriendlyByteBuf buf) { buf.writeUtf(msg.cameraId == null ? "" : msg.cameraId); }
        public static CameraSubscribeRequest decode(FriendlyByteBuf buf) { return new CameraSubscribeRequest(buf.readUtf()); }
        public static void handle(CameraSubscribeRequest msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sender = ctx.get().getSender();
                if (sender == null) return;
                net.chaoscraft.chaoscrafts_device_mod.Client.Camera.CameraManager mgr = net.chaoscraft.chaoscrafts_device_mod.Client.Camera.CameraManager.getInstance();
                CameraInfo ci = mgr.getCamera(msg.cameraId);
                if (ci == null) return;
                // forward capture request to owner
                NetworkHandler.sendToPlayer(new CameraCaptureRequest(msg.cameraId, ci.width, ci.height, ci.fps), sender.getServer().getPlayerList().getPlayer(ci.owner));
                // Note: permission checks omitted for brevity
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class CameraUnsubscribeRequest {
        public String cameraId; public CameraUnsubscribeRequest() {} public CameraUnsubscribeRequest(String id) { this.cameraId = id; }
        public static void encode(CameraUnsubscribeRequest msg, FriendlyByteBuf buf) { buf.writeUtf(msg.cameraId == null ? "" : msg.cameraId); }
        public static CameraUnsubscribeRequest decode(FriendlyByteBuf buf) { return new CameraUnsubscribeRequest(buf.readUtf()); }
        public static void handle(CameraUnsubscribeRequest msg, Supplier<NetworkEvent.Context> ctx) { ctx.get().setPacketHandled(true); }
    }

    public static class CameraCaptureRequest {
        public String cameraId; public int width, height, fps;
        public CameraCaptureRequest() {}
        public CameraCaptureRequest(String cameraId, int width, int height, int fps) { this.cameraId = cameraId; this.width = width; this.height = height; this.fps = fps; }
        public static void encode(CameraCaptureRequest msg, FriendlyByteBuf buf) { buf.writeUtf(msg.cameraId); buf.writeInt(msg.width); buf.writeInt(msg.height); buf.writeInt(msg.fps); }
        public static CameraCaptureRequest decode(FriendlyByteBuf buf) { return new CameraCaptureRequest(buf.readUtf(), buf.readInt(), buf.readInt(), buf.readInt()); }
        public static void handle(CameraCaptureRequest msg, Supplier<NetworkEvent.Context> ctx) {
            // Client (owner) receives this: capture an image and send snapshot back
            ctx.get().enqueueWork(() -> {
                // Run client-only capture logic on the client
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    try {
                        // Create a tiny placeholder image bytes (replace with real capture later)
                        byte[] data = ("CameraSnapshot:" + msg.cameraId + ",owner=" + net.minecraft.client.Minecraft.getInstance().player.getUUID()).getBytes();
                        NetworkHandler.sendToServer(new CameraSnapshot(msg.cameraId, data));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class CameraSnapshot {
        public String cameraId; public byte[] imageBytes;
        public CameraSnapshot() {}
        public CameraSnapshot(String id, byte[] b) { this.cameraId = id; this.imageBytes = b; }
        public static void encode(CameraSnapshot msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.cameraId == null ? "" : msg.cameraId);
            if (msg.imageBytes == null) buf.writeInt(0);
            else { buf.writeInt(msg.imageBytes.length); buf.writeBytes(msg.imageBytes); }
        }
        public static CameraSnapshot decode(FriendlyByteBuf buf) {
            CameraSnapshot s = new CameraSnapshot(); s.cameraId = buf.readUtf(); int len = buf.readInt(); if (len > 0) { s.imageBytes = new byte[len]; buf.readBytes(s.imageBytes); } else s.imageBytes = new byte[0]; return s;
        }
        public static void handle(CameraSnapshot msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                // Server receives snapshot from owner and forwards to subscribed viewers
                net.chaoscraft.chaoscrafts_device_mod.Client.Camera.CameraManager mgr = net.chaoscraft.chaoscrafts_device_mod.Client.Camera.CameraManager.getInstance();
                List<ServerPlayer> viewers = mgr.getSubscribers(msg.cameraId);
                if (viewers == null) return;
                for (ServerPlayer sp : viewers) {
                    NetworkHandler.sendToPlayer(new CameraSnapshot(msg.cameraId, msg.imageBytes), sp);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

}
