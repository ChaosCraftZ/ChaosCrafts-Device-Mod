package net.chaoscraft.chaoscrafts_device_mod;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraft.client.Minecraft;

public final class ConfigHandler {
    private ConfigHandler() {}

    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        CLIENT = new Client(b);
        CLIENT_SPEC = b.build();
    }

    public static class Client {
        public final ForgeConfigSpec.BooleanValue experimentalSettings;
        public final ForgeConfigSpec.BooleanValue experimentalApps;
        public final ForgeConfigSpec.IntValue uiScalePercent;
        public final ForgeConfigSpec.IntValue uiScaleGui1;
        public final ForgeConfigSpec.IntValue uiScaleGui2;
        public final ForgeConfigSpec.IntValue uiScaleGui3;
        public final ForgeConfigSpec.IntValue uiScaleGui4;
         public final ForgeConfigSpec.BooleanValue debugButtons;

         Client(ForgeConfigSpec.Builder b) {
             b.push("general");
            experimentalSettings = b
                    .comment("Enable experimental client features. When false the laptop fan sound is force-muted.")
                    .define("experimentalSettings", false);

            experimentalApps = b
                    .comment("Enable experimental apps (YouTube, Browser, Calendar). When false these apps are hidden from the desktop and the Calendar entry is hidden from the marketplace.")
                    .define("experimentalApps", false);

            uiScalePercent = b
                    .comment("UI scale percent for the mod's UI (1-200). 100 is the default (no change). Values below 100 shrink the UI, above 100 enlarge it.")
                    .defineInRange("uiScalePercent", 100, 1, 200);

            uiScaleGui1 = b
                    .comment("UI scale percent to use when Minecraft's GUI scale is 1 (percent 1-200).")
                    .defineInRange("uiScaleGui1", 125, 1, 200);

            uiScaleGui2 = b
                    .comment("UI scale percent to use when Minecraft's GUI scale is 2 (percent 1-200).")
                    .defineInRange("uiScaleGui2", 100, 1, 200);

            uiScaleGui3 = b
                    .comment("UI scale percent to use when Minecraft's GUI scale is 3 (percent 1-200).")
                    .defineInRange("uiScaleGui3", 75, 1, 200);

            uiScaleGui4 = b
                    .comment("UI scale percent to use when Minecraft's GUI scale is 4 (percent 1-200).")
                    .defineInRange("uiScaleGui4", 50, 1, 200);

             debugButtons = b
                     .comment("Show debug overlay for all button/hitboxes when true. Useful for testing UI scale and hit areas.")
                     .define("debugButtons", false);
             b.pop();
         }
     }

    public static boolean experimentalEnabled() {
        return CLIENT.experimentalSettings.get();
    }

    public static boolean experimentalAppsEnabled() {
        return CLIENT.experimentalApps.get();
    }

    public static int uiScalePercent() {
        return CLIENT.uiScalePercent.get();
    }

    public static int effectiveUiScalePercent() {
        int base = uiScalePercent();
         try {
             Minecraft mc = Minecraft.getInstance();
             if (mc == null) return base;
             int guiScale = 0;
             try {
                 if (mc.getWindow() != null) guiScale = (int) Math.round(mc.getWindow().getGuiScale());
             } catch (Exception ignored) {
                 guiScale = 0;
             }
             switch (guiScale) {
                 case 1: return CLIENT.uiScaleGui1.get();
                 case 2: return CLIENT.uiScaleGui2.get();
                 case 3: return CLIENT.uiScaleGui3.get();
                 case 4: return CLIENT.uiScaleGui4.get();
                 default: return base;
             }
         } catch (Exception ignored) {
             return base;
         }
    }

    public static float uiScaleFactor() {
        return effectiveUiScalePercent() / 100.0f;
    }

    public static boolean debugButtonsEnabled() {
        return CLIENT.debugButtons.get();
    }
}