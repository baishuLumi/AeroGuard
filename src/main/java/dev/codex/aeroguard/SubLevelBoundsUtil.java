package dev.codex.aeroguard;

import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

public final class SubLevelBoundsUtil {
    private SubLevelBoundsUtil() {
    }

    public static ChunkPos centerChunk(final BoundingBox3dc bounds) {
        final double centerX = (bounds.minX() + bounds.maxX()) * 0.5;
        final double centerZ = (bounds.minZ() + bounds.maxZ()) * 0.5;
        return new ChunkPos(Mth.floor(centerX) >> 4, Mth.floor(centerZ) >> 4);
    }
}
