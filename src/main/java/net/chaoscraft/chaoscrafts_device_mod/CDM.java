package net.chaoscraft.chaoscrafts_device_mod;

import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

import net.chaoscraft.chaoscrafts_device_mod.Client.Apps.Example.ExampleAddOn;
import net.chaoscraft.chaoscrafts_device_mod.Client.Sound.ModSounds;
import net.chaoscraft.chaoscrafts_device_mod.Core.AsyncorAPI.AsyncRuntime;
import net.chaoscraft.chaoscrafts_device_mod.Core.Config.ConfigHandler;
import net.chaoscraft.chaoscrafts_device_mod.Core.Network.NetworkHandler;
import net.chaoscraft.chaoscrafts_device_mod.Core.Network.packet.CameraPackets;
import net.chaoscraft.chaoscrafts_device_mod.Core.Network.packet.LaptopDevicePackets;
import net.chaoscraft.chaoscrafts_device_mod.Core.Network.packet.LaptopTypingPacket;
import net.chaoscraft.chaoscrafts_device_mod.Core.PC.block.ModBlocks;
import net.chaoscraft.chaoscrafts_device_mod.Core.PC.block.entity.ModBlockEntities;
import net.chaoscraft.chaoscrafts_device_mod.Core.PC.block.entity.client.LaptopRenderer;
import net.chaoscraft.chaoscrafts_device_mod.Core.PC.item.ModCreativeModTabs;
import net.chaoscraft.chaoscrafts_device_mod.Core.PC.item.ModItems;
import software.bernie.geckolib.GeckoLib;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(CDM.MOD_ID)
public class CDM {
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "chaoscrafts_device_mod";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    public CDM(FMLJavaModLoadingContext context)
    {
        // Register client config (creates chaoscrafts_device_mod-client.toml with experimentalSettings=false by default)
        context.registerConfig(ModConfig.Type.CLIENT, ConfigHandler.CLIENT_SPEC);
        IEventBus modEventBus = context.getModEventBus();

        ModCreativeModTabs.register(modEventBus);

        // Register blocks first so block entity and item registrations can reference them safely
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModSounds.register(modEventBus);

        GeckoLib.initialize();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreative);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Initialize async manager
        AsyncRuntime.get();

        // Register network messages
        registerNetworkMessages();

        // Initialize CameraManager on common setup (server side will load file)
        try {
            net.chaoscraft.chaoscrafts_device_mod.Client.Camera.CameraManager.getInstance();
        } catch (Exception e) {
            LOGGER.warn("Failed to init CameraManager: " + e.getMessage());
        }
    }

    private void registerNetworkMessages() {
        NetworkHandler.registerMessage(CameraPackets.CameraListRequest.class, CameraPackets.CameraListRequest::encode, CameraPackets.CameraListRequest::decode, CameraPackets.CameraListRequest::handle);
        NetworkHandler.registerMessage(CameraPackets.CameraListResponse.class, CameraPackets.CameraListResponse::encode, CameraPackets.CameraListResponse::decode, CameraPackets.CameraListResponse::handle);
        NetworkHandler.registerMessage(CameraPackets.CameraSubscribeRequest.class, CameraPackets.CameraSubscribeRequest::encode, CameraPackets.CameraSubscribeRequest::decode, CameraPackets.CameraSubscribeRequest::handle);
        NetworkHandler.registerMessage(CameraPackets.CameraUnsubscribeRequest.class, CameraPackets.CameraUnsubscribeRequest::encode, CameraPackets.CameraUnsubscribeRequest::decode, CameraPackets.CameraUnsubscribeRequest::handle);
        NetworkHandler.registerMessage(CameraPackets.CameraCaptureRequest.class, CameraPackets.CameraCaptureRequest::encode, CameraPackets.CameraCaptureRequest::decode, CameraPackets.CameraCaptureRequest::handle);
        NetworkHandler.registerMessage(CameraPackets.CameraSnapshot.class, CameraPackets.CameraSnapshot::encode, CameraPackets.CameraSnapshot::decode, CameraPackets.CameraSnapshot::handle);
        NetworkHandler.registerMessage(LaptopTypingPacket.class, LaptopTypingPacket::encode, LaptopTypingPacket::decode, LaptopTypingPacket::handle);
        NetworkHandler.registerMessage(LaptopDevicePackets.RequestState.class, LaptopDevicePackets.RequestState::encode, LaptopDevicePackets.RequestState::decode, LaptopDevicePackets.RequestState::handle);
        NetworkHandler.registerMessage(LaptopDevicePackets.StateUpdate.class, LaptopDevicePackets.StateUpdate::encode, LaptopDevicePackets.StateUpdate::decode, LaptopDevicePackets.StateUpdate::handle);
        NetworkHandler.registerMessage(LaptopDevicePackets.Action.class, LaptopDevicePackets.Action::encode, LaptopDevicePackets.Action::decode, LaptopDevicePackets.Action::handle);
        NetworkHandler.registerMessage(LaptopDevicePackets.ActionResponse.class, LaptopDevicePackets.ActionResponse::encode, LaptopDevicePackets.ActionResponse::decode, LaptopDevicePackets.ActionResponse::handle);
        NetworkHandler.registerMessage(LaptopDevicePackets.Close.class, LaptopDevicePackets.Close::encode, LaptopDevicePackets.Close::decode, LaptopDevicePackets.Close::handle);
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {

            // add both laptop variants
            event.accept(new ItemStack(ModItems.LAPTOP_BLACK_ITEM.get()));
            event.accept(new ItemStack(ModItems.LAPTOP_WHITE_ITEM.get()));
        }

    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // Register block entity renderer
            try {
                BlockEntityRenderers.register(ModBlockEntities.LAPTOP_ENTITY.get(), LaptopRenderer::new);
            } catch (Exception ignored) {}
            // Register shutdown hook
            try {
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    AsyncRuntime.get().shutdown();
                }));
            } catch (Exception ignored) {}

            // Register example add-on apps and icons on client setup so other developers can see how to register
            try {
                ExampleAddOn.register();
            } catch (Exception ignored) {}
        }
    }
}
