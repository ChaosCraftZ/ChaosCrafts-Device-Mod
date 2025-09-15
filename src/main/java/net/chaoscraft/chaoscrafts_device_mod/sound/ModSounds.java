package net.chaoscraft.chaoscrafts_device_mod.sound;

import net.chaoscraft.chaoscrafts_device_mod.CDM;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Central sound event registry for the ChaosCraft Device Mod.
 *
 * Group sections (laptop, phone, pc, etc.). Currently only the laptop
 * sounds are really implemented – the others stand as clear placeholders
 * for future expansion so the structure is ready.
 */
public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, CDM.MOD_ID);

    // ---------------------------------------------------------------------
    // Laptop sounds (IMPLEMENTED)
    // ---------------------------------------------------------------------
    public static final RegistryObject<SoundEvent> LAPTOP_KEYBOARD_1 = register("laptop.keyboard1");
    public static final RegistryObject<SoundEvent> LAPTOP_KEYBOARD_2 = register("laptop.keyboard2");
    public static final RegistryObject<SoundEvent> LAPTOP_KEYBOARD_3 = register("laptop.keyboard3");
    public static final RegistryObject<SoundEvent> LAPTOP_SPACEBAR   = register("laptop.spacebar");
    public static final RegistryObject<SoundEvent> LAPTOP_TRACKPAD   = register("laptop.trackpad");
    // Separate logical registration for mouse click – for now this can use the same underlying .ogg as trackpad until a dedicated file exists.
    public static final RegistryObject<SoundEvent> LAPTOP_MOUSE_CLICK = register("laptop.mouse_click");
    public static final RegistryObject<SoundEvent> LAPTOP_FAN_NOISE  = register("laptop.fan_noise");
    public static final RegistryObject<SoundEvent> LAPTOP_OPEN       = register("laptop.open");
    public static final RegistryObject<SoundEvent> LAPTOP_CLOSE      = register("laptop.close");
    // Global typing sound (broadcast so that other nearby players hear typing – original player excluded)
    public static final RegistryObject<SoundEvent> LAPTOP_KEYBOARD_GLOBAL = register("laptop.keyboard_typing_global");

    // ---------------------------------------------------------------------
    // Phone sounds (PLACEHOLDERS ONLY – add real .ogg files + sounds.json entries later)
    // ---------------------------------------------------------------------
    public static final RegistryObject<SoundEvent> PHONE_TOUCH_1      = register("phone.touch1");
    public static final RegistryObject<SoundEvent> PHONE_TOUCH_2      = register("phone.touch2");
    public static final RegistryObject<SoundEvent> PHONE_UNLOCK       = register("phone.unlock");
    public static final RegistryObject<SoundEvent> PHONE_NOTIFICATION = register("phone.notification");
    public static final RegistryObject<SoundEvent> PHONE_BOOT         = register("phone.boot");
    public static final RegistryObject<SoundEvent> PHONE_SHUTDOWN     = register("phone.shutdown");

    // ---------------------------------------------------------------------
    // PC / Desktop tower sounds (PLACEHOLDERS)
    // ---------------------------------------------------------------------
    public static final RegistryObject<SoundEvent> PC_KEYBOARD_1  = register("pc.keyboard1");
    public static final RegistryObject<SoundEvent> PC_KEYBOARD_2  = register("pc.keyboard2");
    public static final RegistryObject<SoundEvent> PC_KEYBOARD_3  = register("pc.keyboard3");
    public static final RegistryObject<SoundEvent> PC_MOUSE_CLICK = register("pc.mouse_click");
    public static final RegistryObject<SoundEvent> PC_FAN_IDLE    = register("pc.fan_idle");
    public static final RegistryObject<SoundEvent> PC_BOOT        = register("pc.boot");
    public static final RegistryObject<SoundEvent> PC_SHUTDOWN    = register("pc.shutdown");

    // ---------------------------------------------------------------------
    // Registration helper
    // ---------------------------------------------------------------------
    private static RegistryObject<SoundEvent> register(String eventName) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CDM.MOD_ID, eventName);
        return SOUND_EVENTS.register(eventName, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void register(net.minecraftforge.eventbus.api.IEventBus bus) {
        SOUND_EVENTS.register(bus);
    }

    // ---------------------------------------------------------------------
    // Playback Helpers (Laptop)
    // ---------------------------------------------------------------------

    /**
     * Plays a random laptop keyboard key (non-space) sound at the player's current position.
     * Uses SoundSource.PLAYERS so volume scaling respects player-related settings.
     */
    public static void playRandomKeyboardSound(Player player) {
        if (player == null) return;
        int pick = ThreadLocalRandom.current().nextInt(3);
        RegistryObject<SoundEvent> chosen = switch (pick) {
            case 0 -> LAPTOP_KEYBOARD_1;
            case 1 -> LAPTOP_KEYBOARD_2;
            default -> LAPTOP_KEYBOARD_3;
        };
        playSound(player, chosen, SoundSource.PLAYERS, 0.65f, randomPitch(0.95f, 1.05f));
    }

    /** Generic convenience wrapper with default category (PLAYERS), volume & pitch. */
    public static void playSound(Player player, RegistryObject<SoundEvent> sound) {
        playSound(player, sound, SoundSource.PLAYERS, 0.8f, 1.0f);
    }

    /**
     * Full control overload for playing a registered sound at the player's location.
     * If run on logical server, this will sync to nearby clients. Safe to call on either side.
     */
    public static void playSound(Player player, RegistryObject<SoundEvent> sound, SoundSource category, float volume, float pitch) {
        if (player == null || sound == null || sound.get() == null) return;
        Level level = player.level();
        // world.playSound(null, ...) -> all players including the source (client side local playback acceptable)
        level.playSound(null, player.getX(), player.getY(), player.getZ(), sound.get(), category, volume, pitch);
    }

    /**
     * Broadcasts the global keyboard typing sound so ONLY other nearby players hear it.
     * Uses the Mojang helper of world.playSound(sourcePlayer, ...) which excludes the given player.
     */
    public static void playGlobalKeyboardTypingForOthers(Player source) {
        if (source == null || LAPTOP_KEYBOARD_GLOBAL.get() == null) return;
        Level level = source.level();
        // Exclude the source player: pass source as the first parameter (server side).
        // On client side this call is ignored unless level is a client world; typically invoked server-side from a packet handler.
        level.playSound(source, BlockPos.containing(source.getX(), source.getY(), source.getZ()), LAPTOP_KEYBOARD_GLOBAL.get(), SoundSource.PLAYERS, 0.7f, randomPitch(0.9f, 1.1f));
    }

    private static float randomPitch(float min, float max) {
        return (float)(min + ThreadLocalRandom.current().nextDouble() * (max - min));
    }
}
