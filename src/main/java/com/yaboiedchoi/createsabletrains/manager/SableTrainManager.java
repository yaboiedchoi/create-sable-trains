package com.yaboiedchoi.createsabletrains.manager;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageContraption;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import com.yaboiedchoi.createsabletrains.CreateSableTrains;
import org.joml.Quaterniond;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Central manager that bridges Create's train system with Sable's sub-level system.
 * Maintains a record of every active train and carriage that has a Sable sub-level.
 *
 * This is a singleton — one instance manages all trains on the server.
 * The maps inside it handle multiple trains simultaneously.
 */
public class SableTrainManager {

    // The single instance of this manager.
    public static final SableTrainManager INSTANCE = new SableTrainManager();

    // Fast lookup: given a carriage entity's UUID, get its SableCarriage data.
    // This is the map we use every tick to update sub-level positions.
    private final Map<UUID, SableCarriage> carriagesByEntityId = new HashMap<>();

    // Fast lookup: given a Create train's UUID, get its SableTrain data.
    // This is used for train-level operations like door control or nameplate updates.
    private final Map<UUID, SableTrain> trainsByTrainId = new HashMap<>();

    // Private constructor — use INSTANCE instead.
    private SableTrainManager() {}

    // -------------------------------------------------------------------------
    // ASSEMBLY
    // -------------------------------------------------------------------------

    /**
     * Called when a CarriageContraptionEntity is fully assembled and added to the world.
     * Creates a Sable sub-level for this carriage and registers it in both maps.
     */
    public void onCarriageAssembled(CarriageContraptionEntity entity, ServerLevel level) {

        // Only run on the server side — Sable sub-levels are server objects.
        if (level.isClientSide())
            return;

        // Get the Sable sub-level container for this level.
        // This is Sable's registry of all sub-levels in the world.
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            CreateSableTrains.LOGGER.warn(
                    "Could not get SubLevelContainer for level {} — Sable may not be loaded.",
                    level.dimension().location()
            );
            return;
        }

        // Get Create's Carriage object from the entity.
        // This gives us access to the train, bogey positions, index, etc.
        Carriage carriage = entity.getCarriage();
        if (carriage == null) {
            CreateSableTrains.LOGGER.warn(
                    "CarriageContraptionEntity {} has no carriage — skipping assembly.",
                    entity.getUUID()
            );
            return;
        }

        // Don't register the same carriage twice if the entity reloads.
        if (carriagesByEntityId.containsKey(entity.getUUID())) {
            CreateSableTrains.LOGGER.info(
                    "Carriage {} already registered — skipping duplicate assembly.",
                    entity.getUUID()
            );
            return;
        }

        // Build the initial pose (position + orientation) for the sub-level.
        // We start it at the entity's current world position.
        Pose3d pose = buildPose(entity);

        // Ask Sable to allocate a new empty sub-level at that pose.
        // This reserves a plot in Sable's grid and creates the physics body.
        ServerSubLevel subLevel;
        try {
            subLevel = (ServerSubLevel) container.allocateNewSubLevel(pose);
        } catch (Exception e) {
            CreateSableTrains.LOGGER.error(
                    "Failed to allocate Sable sub-level for carriage {}",
                    entity.getUUID(), e
            );
            return;
        }

        // Find the carriage's index within its train.
        // Index 0 is the leading carriage.
        int carriageIndex = carriage.train.carriages.indexOf(carriage);

        // Create our wrapper object for this carriage.
        SableCarriage sableCarriage = new SableCarriage(
                entity.getUUID(),
                subLevel,
                carriage,
                carriageIndex
        );

        // Register it by entity UUID for fast tick lookups.
        carriagesByEntityId.put(entity.getUUID(), sableCarriage);

        // Find or create the SableTrain for this carriage's parent train.
        UUID trainId = carriage.train.id;
        SableTrain sableTrain = trainsByTrainId.computeIfAbsent(
                trainId,
                id -> new SableTrain(id, carriage.train)
        );
        sableTrain.addCarriage(sableCarriage);

        CreateSableTrains.LOGGER.info(
                "Assembled Sable sub-level for carriage {} (train: {}, index: {})",
                entity.getUUID(), trainId, carriageIndex
        );
    }

    // -------------------------------------------------------------------------
    // TICK
    // -------------------------------------------------------------------------

    /**
     * Called every tick for every CarriageContraptionEntity.
     * Updates the Sable sub-level's position and orientation to match
     * where Create has placed the carriage along the rails.
     */
    public void onCarriageTick(CarriageContraptionEntity entity, ServerLevel level) {

        if (level.isClientSide())
            return;

        SableCarriage sableCarriage = carriagesByEntityId.get(entity.getUUID());
        if (sableCarriage == null || !sableCarriage.isValid())
            return;

        ServerSubLevel subLevel = sableCarriage.subLevel;
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null)
            return;

        // Build the current pose from where Create has placed the entity this tick.
        Pose3d pose = buildPose(entity);

        // Update the sub-level's logical pose directly.
        // "Logical pose" is Sable's authoritative record of where a sub-level is.
        subLevel.logicalPose().position().set(
                pose.position().x,
                pose.position().y,
                pose.position().z
        );
        subLevel.logicalPose().orientation().set(pose.orientation());

        // Tell Sable's physics pipeline to teleport the rigid body to match.
        // Without this, the visual/logical position updates but physics collisions don't.
        container.physicsSystem().getPipeline().teleport(
                subLevel,
                subLevel.logicalPose().position(),
                subLevel.logicalPose().orientation()
        );

        // Store the current pose as "last pose" so Sable can interpolate
        // smoothly between ticks for rendering.
        subLevel.updateLastPose();
    }

    // -------------------------------------------------------------------------
    // REMOVAL
    // -------------------------------------------------------------------------

    /**
     * Called when a CarriageContraptionEntity is removed from the world.
     * Cleans up the Sable sub-level and removes entries from both maps.
     */
    public void onCarriageRemoved(CarriageContraptionEntity entity, ServerLevel level) {

        if (level.isClientSide())
            return;

        SableCarriage sableCarriage = carriagesByEntityId.remove(entity.getUUID());
        if (sableCarriage == null)
            return;

        // Remove the sub-level from Sable's container so it doesn't keep
        // ticking and consuming resources after the carriage is gone.
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null && sableCarriage.isValid()) {
            try {
                container.removeSubLevel(
                        sableCarriage.subLevel,
                        dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED
                );
            } catch (Exception e) {
                CreateSableTrains.LOGGER.warn(
                        "Failed to remove sub-level for carriage {}",
                        entity.getUUID(), e
                );
            }
        }

        // Remove this carriage from its parent SableTrain.
        UUID trainId = sableCarriage.carriage.train.id;
        SableTrain sableTrain = trainsByTrainId.get(trainId);
        if (sableTrain != null) {
            sableTrain.removeCarriage(entity.getUUID());

            // If the train has no carriages left, clean up the SableTrain too.
            if (sableTrain.isEmpty()) {
                trainsByTrainId.remove(trainId);
                CreateSableTrains.LOGGER.info(
                        "Removed SableTrain {} — no carriages remaining.",
                        trainId
                );
            }
        }

        CreateSableTrains.LOGGER.info(
                "Removed Sable sub-level for carriage {}",
                entity.getUUID()
        );
    }

    // -------------------------------------------------------------------------
    // SERVER LIFECYCLE
    // -------------------------------------------------------------------------

    /**
     * Called when the server stops or a world unloads.
     * Clears all data so we start fresh on the next load.
     * Without this, stale UUIDs from the previous session would linger.
     */
    public void onServerStopped() {
        carriagesByEntityId.clear();
        trainsByTrainId.clear();
        CreateSableTrains.LOGGER.info("TrainSableManager cleared on server stop.");
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    /**
     * Builds a Sable Pose3d from a CarriageContraptionEntity's current
     * position and yaw angle.
     *
     * Pose3d holds two things:
     *   - position: where the sub-level's center is in world space
     *   - orientation: which way it's facing, as a quaternion
     *
     * A quaternion is just a compact way of representing a 3D rotation.
     * We build ours from the entity's yaw (left/right angle) and pitch
     * (up/down angle), which Create has already calculated from the rails.
     */
    private Pose3d buildPose(CarriageContraptionEntity entity) {
        Pose3d pose = new Pose3d();

        // Set position to the entity's current world position.
        pose.position().set(
                entity.getX(),
                entity.getY(),
                entity.getZ()
        );

        // Convert yaw and pitch (in degrees) to a quaternion.
        // In Minecraft, yaw rotates around the Y axis (left/right).
        // Pitch rotates around the X axis (up/down on slopes).
        // We negate yaw because Minecraft and JOML use opposite conventions.
        double yawRad   = Math.toRadians(-entity.yaw);
        double pitchRad = Math.toRadians(entity.pitch);

        pose.orientation()
                .identity()
                .rotateY(yawRad)
                .rotateX(pitchRad);

        return pose;
    }

    // -------------------------------------------------------------------------
    // QUERIES
    // -------------------------------------------------------------------------

    /**
     * Returns the SableCarriage for a given entity UUID, or null if not found.
     */
    public SableCarriage getCarriage(UUID entityId) {
        return carriagesByEntityId.get(entityId);
    }

    /**
     * Returns the SableTrain for a given train UUID, or null if not found.
     */
    public SableTrain getTrain(UUID trainId) {
        return trainsByTrainId.get(trainId);
    }
}