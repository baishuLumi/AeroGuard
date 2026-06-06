package dev.codex.aeroguard.mixin;

import dev.codex.aeroguard.AeroGuard;
import dev.codex.aeroguard.AeroGuardConfig;
import dev.codex.aeroguard.SubLevelBoundsUtil;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.object.ArbitraryPhysicsObject;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicket;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

@Mixin(value = PhysicsChunkTicketManager.class, remap = false)
public class PhysicsChunkTicketManagerMixin {
    @Shadow
    private Map<SectionPos, PhysicsChunkTicket> physicsChunks;

    @Unique
    private static long aeroguard$lastEdgeLogTick = Long.MIN_VALUE;

    @Inject(method = "update", at = @At("HEAD"), cancellable = true, remap = false)
    private void aeroguard$centerChunkSubLevelLifecycle(final ServerLevel level,
                                                       final ServerSubLevelContainer container,
                                                       final SubLevelPhysicsSystem system,
                                                       final PhysicsPipeline pipeline,
                                                       final double timeStep,
                                                       final CallbackInfo ci) {
        if (!AeroGuard.patchesEnabled() || AeroGuardConfig.unloadPolicy() != AeroGuardConfig.Policy.CENTER_CHUNK) {
            return;
        }

        ci.cancel();
        this.aeroguard$updateWithCenterChunkLifecycle(level, container, system, pipeline, timeStep);
    }

    @Unique
    private void aeroguard$updateWithCenterChunkLifecycle(final ServerLevel level,
                                                         final ServerSubLevelContainer container,
                                                         final SubLevelPhysicsSystem system,
                                                         final PhysicsPipeline pipeline,
                                                         final double timeStep) {
        final SubLevelHoldingChunkMap holdingChunkMap = container.getHoldingChunkMap();
        final long gameTime = level.getGameTime();
        final Iterator<Map.Entry<SectionPos, PhysicsChunkTicket>> chunkIter = this.physicsChunks.entrySet().iterator();

        while (chunkIter.hasNext()) {
            final Map.Entry<SectionPos, PhysicsChunkTicket> entry = chunkIter.next();
            final SectionPos sectionPos = entry.getKey();
            final PhysicsChunkTicket ticket = entry.getValue();

            final LevelPlot plot = SubLevelContainer.getContainer(level).getPlot(sectionPos.chunk());
            final boolean outdated = ticket.lastInhabitedTick() < gameTime - 20 && plot == null;
            final boolean noLongerExistent = !PhysicsChunkTicketManager.isChunkLoadedEnough(level, sectionPos.x(), sectionPos.z());
            if (outdated || noLongerExistent) {
                pipeline.handleChunkSectionRemoval(sectionPos.x(), sectionPos.y(), sectionPos.z());
                chunkIter.remove();
            } else if (SubLevelPhysicsSystem.USE_TICKETS_FOR_QUERIES && ticket.residentSubLevels() != null && !ticket.residentSubLevels().isEmpty()) {
                ticket.residentSubLevels().clear();
            }
        }

        final LongOpenHashSet unloadedChunks = new LongOpenHashSet();
        final BoundingBox3d bounds = new BoundingBox3d();
        final BoundingBox3d predictedBounds = new BoundingBox3d();
        final Vector3d velocity = new Vector3d();

        final Iterator<ArbitraryPhysicsObject> objectIter = system.getArbitraryObjects().iterator();
        arbitraryObjectLoop:
        while (objectIter.hasNext()) {
            final ArbitraryPhysicsObject arbitraryObject = objectIter.next();
            arbitraryObject.getBoundingBox(bounds);
            bounds.expand(1.0, bounds);

            final BoundingBox3i chunkBounds = new BoundingBox3i(
                    Mth.floor(bounds.minX()) >> 4,
                    Mth.floor(bounds.minY()) >> 4,
                    Mth.floor(bounds.minZ()) >> 4,
                    Mth.floor(bounds.maxX()) >> 4,
                    Mth.floor(bounds.maxY()) >> 4,
                    Mth.floor(bounds.maxZ()) >> 4
            );

            for (int x = chunkBounds.minX(); x <= chunkBounds.maxX(); x++) {
                for (int z = chunkBounds.minZ(); z <= chunkBounds.maxZ(); z++) {
                    final long chunkKey = ChunkPos.asLong(x, z);

                    if (!PhysicsChunkTicketManager.isChunkLoadedEnough(level, x, z) || unloadedChunks.contains(chunkKey)) {
                        arbitraryObject.onUnloaded(holdingChunkMap, new ChunkPos(x, z));
                        unloadedChunks.add(chunkKey);
                        objectIter.remove();
                        continue arbitraryObjectLoop;
                    }
                }
            }

            for (int x = chunkBounds.minX(); x <= chunkBounds.maxX(); x++) {
                for (int z = chunkBounds.minZ(); z <= chunkBounds.maxZ(); z++) {
                    for (int y = chunkBounds.minY(); y <= chunkBounds.maxY(); y++) {
                        this.aeroguard$addTicketIfLoaded(level, pipeline, x, y, z, gameTime, null);
                    }
                }
            }
        }

        subLevelLoop:
        for (int i = 0; i < container.getAllSubLevels().size(); i++) {
            final ServerSubLevel subLevel = container.getAllSubLevels().get(i);
            if (subLevel.isRemoved()) {
                continue;
            }

            bounds.set(subLevel.boundingBox());
            predictedBounds.set(bounds);

            if (subLevel.lastPose().position().distanceSquared(subLevel.logicalPose().position()) > 0.05 * 0.05) {
                system.getPipeline().getLinearVelocity(subLevel, velocity.zero()).mul(timeStep);
                predictedBounds.move(0.0, Mth.clamp(velocity.y, -PhysicsChunkTicketManager.MAX_PREDICTION_DISTANCE, PhysicsChunkTicketManager.MAX_PREDICTION_DISTANCE), 0.0);
                bounds.expandTo(predictedBounds);
            }

            bounds.expand(1.0, bounds);
            final ChunkPos centerChunk = SubLevelBoundsUtil.centerChunk(bounds);
            final long centerKey = centerChunk.toLong();

            if (!PhysicsChunkTicketManager.isChunkLoadedEnough(level, centerChunk.x, centerChunk.z) || unloadedChunks.contains(centerKey)) {
                unloadedChunks.add(centerKey);
                holdingChunkMap.moveToUnloaded(subLevel, centerChunk);
                i--;
                continue;
            }

            final BoundingBox3i chunkBounds = bounds.chunkBoundsFrom();
            for (int x = chunkBounds.minX(); x <= chunkBounds.maxX(); x++) {
                for (int z = chunkBounds.minZ(); z <= chunkBounds.maxZ(); z++) {
                    final boolean loadedEnough = PhysicsChunkTicketManager.isChunkLoadedEnough(level, x, z);

                    if (!loadedEnough && AeroGuardConfig.debugLogging() && aeroguard$lastEdgeLogTick != level.getGameTime()) {
                        aeroguard$lastEdgeLogTick = level.getGameTime();
                        AeroGuard.LOGGER.info("Kept sub-level {} active because center chunk {} is loaded; ignored edge chunk {},{}",
                                subLevel.getUniqueId(), centerChunk, x, z);
                    }

                    if (!loadedEnough) {
                        continue;
                    }

                    for (int y = chunkBounds.minY(); y <= chunkBounds.maxY(); y++) {
                        this.aeroguard$addTicketIfLoaded(level, pipeline, x, y, z, gameTime, subLevel);
                    }
                }
            }
        }
    }

    @Unique
    private void aeroguard$addTicketIfLoaded(final Level level,
                                             final PhysicsPipeline pipeline,
                                             final int x,
                                             final int y,
                                             final int z,
                                             final long gameTime,
                                             final ServerSubLevel resident) {
        if (!PhysicsChunkTicketManager.isChunkLoadedEnough((ServerLevel) level, x, z)) {
            return;
        }

        final SectionPos sectionPos = SectionPos.of(x, y, z);
        final int index = level.getSectionIndexFromSectionY(y);

        if (index < 0 || index >= level.getSectionsCount()) {
            return;
        }

        final PhysicsChunkTicket ticket = this.aeroguard$addTicket(level, pipeline, sectionPos, x, z, index, gameTime);

        if (resident != null && SubLevelPhysicsSystem.USE_TICKETS_FOR_QUERIES) {
            ticket.residentSubLevels().add(resident);
        }
    }

    @Unique
    private @NotNull PhysicsChunkTicket aeroguard$addTicket(final Level level,
                                                            final PhysicsPipeline pipeline,
                                                            final SectionPos sectionPos,
                                                            final int x,
                                                            final int z,
                                                            final int index,
                                                            final long gameTime) {
        PhysicsChunkTicket existingTicket = this.physicsChunks.get(sectionPos);
        if (existingTicket == null) {
            final LevelChunk chunk = level.getChunk(x, z);

            pipeline.handleChunkSectionAddition(chunk.getSection(index), x, sectionPos.y(), z, false);

            final Collection<SubLevel> residents = SubLevelPhysicsSystem.USE_TICKETS_FOR_QUERIES ? new ObjectArraySet<>(SubLevelPhysicsSystem.DEFAULT_RESIDENT_CAPACITY) : null;
            final PhysicsChunkTicket newTicket = new PhysicsChunkTicket(sectionPos, gameTime, residents);
            this.physicsChunks.put(sectionPos, newTicket);

            existingTicket = newTicket;
        }

        existingTicket.setLastInhabitedTick(gameTime);
        return existingTicket;
    }
}
