package net.chaoscraft.chaoscrafts_device_mod.Core.PC.block.entity;

import net.chaoscraft.chaoscrafts_device_mod.Client.Sound.LaptopKeySoundManager;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Device.DeviceState;
import net.chaoscraft.chaoscrafts_device_mod.Core.Network.NetworkHandler;
import net.chaoscraft.chaoscrafts_device_mod.Core.Network.packet.LaptopDevicePackets;
import net.chaoscraft.chaoscrafts_device_mod.Core.PC.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.chaoscraft.chaoscrafts_device_mod.Core.AsyncorAPI.AsyncRuntime;
import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.RenderUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public class LaptopEntity extends BlockEntity implements GeoBlockEntity {
    private AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

    private static final double FE_PER_TICK = 10.0;
    private static final int POWERED_GRACE_TICKS = 3;

    private double neededAcc = 0.0;
    private int poweredTicks = 0;

    private int syncTicker = 0;

    private final IEnergyStorage consumer = new IEnergyStorage() {
        @Override public int receiveEnergy(int maxReceive, boolean simulate) {
            int needNow = (int) Math.floor(neededAcc);
            if (needNow <= 0) return 0;
            int take = Math.min(maxReceive, needNow);
            if (take > 0 && !simulate) {
                neededAcc -= take;
                markPowered();
            }
            return take;
        }
        @Override public int extractEnergy(int maxExtract, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return 0; }
        @Override public int getMaxEnergyStored() { return 0; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    };
    private final LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> consumer);

    private boolean open = false;
    private boolean whiteVariant = false;
    private long lastToggleTime = 0;
    private static final long TOGGLE_COOLDOWN = 300;

    private String animationState = "closed_idle";
    private long animationStartTime = 0;

    public static final String TAG_DEVICE_STATE = "DeviceState";

    private DeviceState deviceState = new DeviceState();
    private final Set<UUID> activeUsers = new HashSet<>();
    private UUID deviceId;

    private static final Gson GSON = new GsonBuilder().excludeFieldsWithModifiers(java.lang.reflect.Modifier.TRANSIENT).create();

    public LaptopEntity(BlockPos pPos, BlockState pBlockState) {
        super(ModBlockEntities.LAPTOP_ENTITY.get(), pPos, pBlockState);
        this.whiteVariant = pBlockState.getBlock() == ModBlocks.LAPTOP_WHITE.get();
        this.animationState = open ? "open_idle" : "closed_idle";
    }

    public static void serverTick(Level lvl, BlockPos pos, BlockState st, LaptopEntity be) {
        be.neededAcc += FE_PER_TICK;
        if (be.poweredTicks > 0) be.poweredTicks--;
        if ((be.syncTicker++ & 0x7) == 0) {
            be.setChanged();
            lvl.sendBlockUpdated(pos, st, st, 3);
        }
    }

    public static void clientTick(Level lvl, BlockPos pos, BlockState st, LaptopEntity be) {
        if (be.poweredTicks > 0) be.poweredTicks--;
    }

    private void markPowered() {
        this.poweredTicks = POWERED_GRACE_TICKS;
        if (level != null && !level.isClientSide) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable net.minecraft.core.Direction side) {
        if (cap == ForgeCapabilities.ENERGY) return energyCap.cast();
        return super.getCapability(cap, side);
    }

    @Override public void invalidateCaps() { super.invalidateCaps(); energyCap.invalidate(); }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private PlayState predicate(AnimationState<GeoAnimatable> state) {
        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - animationStartTime;
        if (animationState.equals("opening") && elapsed >= 750) {
            animationState = "open_idle";
        } else if (animationState.equals("closing") && elapsed >= 1250) {
            animationState = "closed_idle";
        }
        switch (animationState) {
            case "opening":
                state.getController().setAnimation(RawAnimation.begin().then("animation.laptop.open", Animation.LoopType.PLAY_ONCE));
                break;
            case "closing":
                state.getController().setAnimation(RawAnimation.begin().then("animation.laptop.close", Animation.LoopType.PLAY_ONCE));
                break;
            case "open_idle":
                state.getController().setAnimation(RawAnimation.begin().then("animation.laptop.open_idle", Animation.LoopType.LOOP));
                break;
            case "closed_idle":
            default:
                state.getController().setAnimation(RawAnimation.begin().then("animation.laptop.closed_idle", Animation.LoopType.LOOP));
                break;
        }
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object blockEntity) {
        return RenderUtils.getCurrentTick();
    }

    public boolean isOpen() { return open; }
    public boolean isWhiteVariant() { return whiteVariant; }

    public void setWhiteVariant(boolean white) {
        this.whiteVariant = white;
        setChanged();
    }

    public void setOpen(boolean open, boolean notify) {
        if (this.open == open) return;
        long now = System.currentTimeMillis();
        if (now - lastToggleTime < TOGGLE_COOLDOWN) return;
        this.open = open;
        this.lastToggleTime = now;
        this.animationState = open ? "opening" : "closing";
        this.animationStartTime = now;
        // Play the appropriate sound exactly when animation starts (client side only)
        if (level != null && level.isClientSide) {
            if (open) LaptopKeySoundManager.playLaptopOpen(); else LaptopKeySoundManager.playLaptopClose();
        }
        if (level != null && level.isClientSide) {
            this.triggerAnim("controller", animationState);
        }
        if (notify) {
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    public boolean toggleOpen() {
        setOpen(!open, true);
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("Open", open);
        tag.putBoolean("White", whiteVariant);
        tag.putLong("LastToggle", lastToggleTime);
        tag.putString("AnimationState", animationState);
        tag.putLong("AnimationStart", animationStartTime);
        tag.putDouble("need", neededAcc);
        tag.putInt("pTicks", poweredTicks);
        if (deviceId != null) {
            tag.putUUID("DeviceId", deviceId);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        open = tag.getBoolean("Open");
        whiteVariant = tag.getBoolean("White");
        lastToggleTime = tag.getLong("LastToggle");
        animationState = tag.getString("AnimationState");
        animationStartTime = tag.getLong("AnimationStart");
        neededAcc = tag.getDouble("need");
        poweredTicks = tag.getInt("pTicks");
        if (tag.hasUUID("DeviceId")) {
            deviceId = tag.getUUID("DeviceId");
        } else {
            deviceId = UUID.randomUUID();
        }
        loadDeviceStateFromFile();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        super.onDataPacket(net, pkt);
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            boolean wasOpen = this.open;
            this.load(tag);
            if (wasOpen != this.open) {
                this.animationState = this.open ? "opening" : "closing";
                this.animationStartTime = System.currentTimeMillis();
                if (level != null && level.isClientSide) {
                    // Ensure correct sound plays on client when server syncs change
                    if (this.open) LaptopKeySoundManager.playLaptopOpen(); else LaptopKeySoundManager.playLaptopClose();
                    this.triggerAnim("controller", animationState);
                }
            }
        }
    }

    public DeviceState getDeviceState() {
        return deviceState;
    }

    public DeviceState snapshotDeviceState() {
        return deviceState.copy();
    }

    public boolean updateDeviceState(Function<DeviceState, Boolean> modifier) {
        if (modifier == null) {
            return false;
        }
        boolean changed = Boolean.TRUE.equals(modifier.apply(deviceState));
        if (changed) {
            setChanged();
            AsyncRuntime.get().submitIo(() -> saveDeviceStateToFile());
        }
        return changed;
    }

    public void addActiveUser(UUID playerId) {
        if (playerId != null) {
            activeUsers.add(playerId);
        }
    }

    public void removeActiveUser(UUID playerId) {
        if (playerId != null) {
            activeUsers.remove(playerId);
        }
    }

    public Set<UUID> getActiveUsers() {
        return Set.copyOf(activeUsers);
    }

    public void broadcastDeviceState() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        DeviceState snapshot = snapshotDeviceState();
        CompoundTag tag = snapshot.saveToTag();
        for (UUID watcherId : activeUsers) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(watcherId);
            if (player != null) {
                NetworkHandler.sendToPlayer(new LaptopDevicePackets.StateUpdate(worldPosition, tag), player);
            }
        }
    }

    @Override
    public void saveToItem(ItemStack stack) {
        super.saveToItem(stack);
        if (deviceId != null) {
            stack.getOrCreateTag().putUUID("DeviceId", deviceId);
        }
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(UUID deviceId) {
        this.deviceId = deviceId;
    }

    public void saveDeviceStateToFile() {
        if (level != null && !level.isClientSide && level.getServer() != null && deviceId != null) {
            try {
                Path dir = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("pcmod").resolve("data");
                Files.createDirectories(dir);
                Path file = dir.resolve(deviceId.toString() + ".json");
                String json = GSON.toJson(deviceState);
                Files.write(file, json.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                // Log error if needed
            }
        }
    }

    public void loadDeviceStateFromFile() {
        if (level != null && !level.isClientSide && level.getServer() != null && deviceId != null) {
            try {
                Path dir = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("pcmod").resolve("data");
                Path file = dir.resolve(deviceId.toString() + ".json");
                if (Files.exists(file)) {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    DeviceState loaded = GSON.fromJson(json, DeviceState.class);
                    if (loaded != null) {
                        deviceState = loaded;
                    } else {
                        deviceState = new DeviceState();
                        deviceState.initializeDefaults();
                    }
                } else {
                    deviceState = new DeviceState();
                    deviceState.initializeDefaults();
                }
            } catch (Exception e) {
                deviceState = new DeviceState();
                deviceState.initializeDefaults();
            }
        } else {
            deviceState = new DeviceState();
            deviceState.initializeDefaults();
        }
    }
}
