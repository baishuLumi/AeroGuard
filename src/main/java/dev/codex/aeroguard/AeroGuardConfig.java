package dev.codex.aeroguard;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class AeroGuardConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Enable AeroGuard lifecycle patches.")
            .define("enabled", true);
    private static final ModConfigSpec.EnumValue<Policy> LOAD_POLICY = BUILDER
            .comment("Policy used when restoring unloaded Sable sub-levels from holding storage.")
            .defineEnum("loadPolicy", Policy.CENTER_CHUNK);
    private static final ModConfigSpec.EnumValue<Policy> UNLOAD_POLICY = BUILDER
            .comment("Policy used when deciding whether an active Sable sub-level should move to holding storage.")
            .defineEnum("unloadPolicy", Policy.CENTER_CHUNK);
    private static final ModConfigSpec.BooleanValue DEBUG_LOGGING = BUILDER
            .comment("Log when AeroGuard suppresses an edge-chunk unload.")
            .define("debugLogging", false);

    public static final ModConfigSpec SPEC = BUILDER.build();
    private static Boolean runtimeDebug;

    private AeroGuardConfig() {
    }

    public static boolean enabled() {
        return ENABLED.get();
    }

    public static Policy loadPolicy() {
        return LOAD_POLICY.get();
    }

    public static Policy unloadPolicy() {
        return UNLOAD_POLICY.get();
    }

    public static boolean debugLogging() {
        return runtimeDebug != null ? runtimeDebug : DEBUG_LOGGING.get();
    }

    public static void setRuntimeDebug(final boolean enabled) {
        runtimeDebug = enabled;
    }

    public enum Policy {
        CENTER_CHUNK
    }
}
