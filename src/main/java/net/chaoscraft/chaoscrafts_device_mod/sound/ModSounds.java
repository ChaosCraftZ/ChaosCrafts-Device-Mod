package net.chaoscraft.chaoscrafts_device_mod.sound;

import net.chaoscraft.chaoscrafts_device_mod.CDM;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, CDM.MOD_ID);

    public static final RegistryObject<SoundEvent> LAPTOP_KEYBOARD_1 = register("laptop.keyboard1");
    public static final RegistryObject<SoundEvent> LAPTOP_KEYBOARD_2 = register("laptop.keyboard2");
    public static final RegistryObject<SoundEvent> LAPTOP_KEYBOARD_3 = register("laptop.keyboard3");
    public static final RegistryObject<SoundEvent> LAPTOP_SPACEBAR   = register("laptop.spacebar");
    public static final RegistryObject<SoundEvent> LAPTOP_TRACKPAD   = register("laptop.trackpad");

    public static final RegistryObject<SoundEvent> LAPTOP_MOUSE_CLICK = register("laptop.mouse_click");
    public static final RegistryObject<SoundEvent> LAPTOP_FAN_NOISE  = register("laptop.fan_noise");
    public static final RegistryObject<SoundEvent> LAPTOP_OPEN       = register("laptop.open");
    public static final RegistryObject<SoundEvent> LAPTOP_CLOSE      = register("laptop.close");

    public static final RegistryObject<SoundEvent> LAPTOP_KEYBOARD_GLOBAL = register("laptop.keyboard_typing_global");

    // for later
    public static final RegistryObject<SoundEvent> PHONE_TOUCH_1      = register("phone.touch1");
    public static final RegistryObject<SoundEvent> PHONE_TOUCH_2      = register("phone.touch2");
    public static final RegistryObject<SoundEvent> PHONE_UNLOCK       = register("phone.unlock");
    public static final RegistryObject<SoundEvent> PHONE_NOTIFICATION = register("phone.notification");
    public static final RegistryObject<SoundEvent> PHONE_BOOT         = register("phone.boot");
    public static final RegistryObject<SoundEvent> PHONE_SHUTDOWN     = register("phone.shutdown");

    // also for later
    public static final RegistryObject<SoundEvent> PC_KEYBOARD_1  = register("pc.keyboard1");
    public static final RegistryObject<SoundEvent> PC_KEYBOARD_2  = register("pc.keyboard2");
    public static final RegistryObject<SoundEvent> PC_KEYBOARD_3  = register("pc.keyboard3");
    public static final RegistryObject<SoundEvent> PC_MOUSE_CLICK = register("pc.mouse_click");
    public static final RegistryObject<SoundEvent> PC_FAN_IDLE    = register("pc.fan_idle");
    public static final RegistryObject<SoundEvent> PC_BOOT        = register("pc.boot");
    public static final RegistryObject<SoundEvent> PC_SHUTDOWN    = register("pc.shutdown");

    private static RegistryObject<SoundEvent> register(String eventName) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CDM.MOD_ID, eventName);
        return SOUND_EVENTS.register(eventName, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void register(net.minecraftforge.eventbus.api.IEventBus bus) {
        SOUND_EVENTS.register(bus);
    }
}
