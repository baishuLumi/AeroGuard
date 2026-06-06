package dev.codex.aeroguard;

import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(AeroGuard.MOD_ID)
public class AeroGuard {
    public static final String MOD_ID = "aeroguard";
    public static final String TARGET_SABLE_VERSION = "1.2.2";
    public static final Logger LOGGER = LoggerFactory.getLogger("AeroGuard");

    public AeroGuard(final ModContainer modContainer, final IEventBus modBus) {
        modContainer.registerConfig(ModConfig.Type.SERVER, AeroGuardConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
    }

    private void registerCommands(final RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("aeroguard")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(context -> {
                    final String sableVersion = getLoadedSableVersion();
                    final boolean versionMatches = isSupportedSableVersion(sableVersion);

                    context.getSource().sendSuccess(() -> Component.literal("AeroGuard 状态：")
                            .append(enabledText(AeroGuardConfig.enabled()))
                            .append(Component.literal("，Sable 版本："))
                            .append(Component.literal(sableVersion == null ? "not loaded" : sableVersion)
                                    .withStyle(versionMatches ? ChatFormatting.GREEN : ChatFormatting.RED))
                            .append(Component.literal("，恢复策略：" + policyText(AeroGuardConfig.loadPolicy())))
                            .append(Component.literal("，卸载策略：" + policyText(AeroGuardConfig.unloadPolicy())))
                            .append(Component.literal("，调试日志：" + booleanText(AeroGuardConfig.debugLogging()))), false);
                    return 1;
                }))
                .then(Commands.literal("debug")
                        .then(Commands.argument("enabled", BoolArgumentType.bool()).executes(context -> {
                            final boolean enabled = BoolArgumentType.getBool(context, "enabled");
                            AeroGuardConfig.setRuntimeDebug(enabled);
                            context.getSource().sendSuccess(() -> Component.literal("AeroGuard 调试日志已")
                                    .append(Component.literal(enabled ? "开启" : "关闭")
                                            .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED)), true);
                            return 1;
                        }))));
    }

    private static Component enabledText(final boolean enabled) {
        return Component.literal(enabled ? "已启用" : "已关闭")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static String booleanText(final boolean enabled) {
        return enabled ? "开启" : "关闭";
    }

    private static String policyText(final AeroGuardConfig.Policy policy) {
        return switch (policy) {
            case CENTER_CHUNK -> "中心区块";
        };
    }

    public static String getLoadedSableVersion() {
        return ModList.get().getModContainerById("sable")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse(null);
    }

    public static boolean isTargetSableLoaded() {
        return isSupportedSableVersion(getLoadedSableVersion());
    }

    public static boolean patchesEnabled() {
        return AeroGuardConfig.enabled() && isTargetSableLoaded();
    }

    private static boolean isSupportedSableVersion(final String version) {
        return TARGET_SABLE_VERSION.equals(version);
    }
}
