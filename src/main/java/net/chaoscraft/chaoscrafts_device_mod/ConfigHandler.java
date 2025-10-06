package net.chaoscraft.chaoscrafts_device_mod;

import net.minecraftforge.common.ForgeConfigSpec;

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
        Client(ForgeConfigSpec.Builder b) {
            b.push("general");
            experimentalSettings = b
                    .comment("Enable experimental client features. When false the laptop fan sound is force-muted.")
                    .define("experimentalSettings", false);
            b.pop();
        }
    }

    public static boolean experimentalEnabled() {
        return CLIENT.experimentalSettings.get();
    }
}