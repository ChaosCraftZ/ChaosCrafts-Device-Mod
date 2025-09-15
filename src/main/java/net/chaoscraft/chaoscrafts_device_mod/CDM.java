package net.chaoscraft.chaoscrafts_device_mod;

import com.mojang.logging.LogUtils;
import net.chaoscraft.chaoscrafts_device_mod.block.ModBlocks;
import net.chaoscraft.chaoscrafts_device_mod.block.entity.LaptopEntity;
import net.chaoscraft.chaoscrafts_device_mod.block.entity.ModBlockEntities;
import net.chaoscraft.chaoscrafts_device_mod.block.entity.client.LaptopRenderer;
import net.chaoscraft.chaoscrafts_device_mod.item.ModCreativeModTabs;
import net.chaoscraft.chaoscrafts_device_mod.item.ModItems;
import net.chaoscraft.chaoscrafts_device_mod.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

import net.chaoscraft.chaoscrafts_device_mod.client.async.AsyncTaskManager;
import net.chaoscraft.chaoscrafts_device_mod.network.NetworkHandler;
import net.chaoscraft.chaoscrafts_device_mod.network.packet.CameraPackets;
import net.chaoscraft.chaoscrafts_device_mod.network.packet.LaptopTypingPacket;
import net.chaoscraft.chaoscrafts_device_mod.client.app.ExampleAddOn;
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
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ConfigHandler.CLIENT_SPEC);
        IEventBus modEventBus = context.getModEventBus();

        ModCreativeModTabs.register(modEventBus);

        // Register blocks first so block entity and item registrations can reference them safely
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModSounds.register(modEventBus);

        GeckoLib.initialize();

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Initialize async manager
        AsyncTaskManager.getInstance();

        // Register network messages
        NetworkHandler.getChannel();
        NetworkHandler.registerMessage(CameraPackets.CameraListRequest.class, CameraPackets.CameraListRequest::encode, CameraPackets.CameraListRequest::decode, CameraPackets.CameraListRequest::handle);
        NetworkHandler.registerMessage(CameraPackets.CameraListResponse.class, CameraPackets.CameraListResponse::encode, CameraPackets.CameraListResponse::decode, CameraPackets.CameraListResponse::handle);
        NetworkHandler.registerMessage(CameraPackets.CameraSubscribeRequest.class, CameraPackets.CameraSubscribeRequest::encode, CameraPackets.CameraSubscribeRequest::decode, CameraPackets.CameraSubscribeRequest::handle);
        NetworkHandler.registerMessage(CameraPackets.CameraUnsubscribeRequest.class, CameraPackets.CameraUnsubscribeRequest::encode, CameraPackets.CameraUnsubscribeRequest::decode, CameraPackets.CameraUnsubscribeRequest::handle);
        NetworkHandler.registerMessage(CameraPackets.CameraCaptureRequest.class, CameraPackets.CameraCaptureRequest::encode, CameraPackets.CameraCaptureRequest::decode, CameraPackets.CameraCaptureRequest::handle);
        NetworkHandler.registerMessage(CameraPackets.CameraSnapshot.class, CameraPackets.CameraSnapshot::encode, CameraPackets.CameraSnapshot::decode, CameraPackets.CameraSnapshot::handle);
        NetworkHandler.registerMessage(LaptopTypingPacket.class, LaptopTypingPacket::encode, LaptopTypingPacket::decode, LaptopTypingPacket::handle);

        // Initialize CameraManager on common setup (server side will load file)
        try {
            net.chaoscraft.chaoscrafts_device_mod.backend.CameraManager.getInstance();
        } catch (Exception e) {
            LOGGER.warn("Failed to init CameraManager: " + e.getMessage());
        }
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
            BlockEntityRenderers.register(ModBlockEntities.LAPTOP_ENTITY.get(), LaptopRenderer::new);
            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                AsyncTaskManager.getInstance().shutdown();
            }));

            // Register example add-on apps and icons on client setup so other developers can see how to register
            try {
                ExampleAddOn.register();
            } catch (Exception ignored) {}
        }
    }
}
