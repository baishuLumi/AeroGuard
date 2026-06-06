package dev.codex.aeroguard.mixin;

import dev.codex.aeroguard.AeroGuard;
import dev.codex.aeroguard.AeroGuardConfig;
import dev.codex.aeroguard.SubLevelBoundsUtil;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk", remap = false)
public class SubLevelHoldingChunkMixin {
    @Inject(method = "canLoadSubLevel", at = @At("HEAD"), cancellable = true, remap = false)
    private static void aeroguard$loadFromCenterChunk(final ServerLevel level,
                                                     final SubLevelData data,
                                                     final CallbackInfoReturnable<Boolean> cir) {
        if (!AeroGuard.patchesEnabled() || AeroGuardConfig.loadPolicy() != AeroGuardConfig.Policy.CENTER_CHUNK) {
            return;
        }

        final ChunkPos center = SubLevelBoundsUtil.centerChunk(data.bounds());
        cir.setReturnValue(PhysicsChunkTicketManager.isChunkLoadedEnough(level, center.x, center.z));
    }
}
