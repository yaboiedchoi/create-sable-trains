package com.yaboiedchoi.createsabletrains.manager;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import java.util.UUID;

/**
 * Holds all Sable-related data for a single train carriage.
 * One SableCarriage exists for every CarriageContraptionEntity in the world.
 */
public class SableCarriage {

    // UUID of carriage
    public final UUID entityId;

    // sable sub-level
    public final ServerSubLevel subLevel;

    // A reference to the Create carriage object.
    // This gives us access to speed, bogey positions, the parent train, etc.
    public final Carriage carriage;

    // Which carriage this is within its train (0 = first, 1 = second, etc.)
    // Useful for knowing if this is the front or back of the train.
    public final int carriageIndex;

    // Whether this carriage's doors should currently be open.
    // Will be used later when we add door automation at stations.
    public boolean doorsOpen = false;

    // Whether the blocks from this carriage have been populated
    // into the Sable sub-level's plot yet.
    // False in v0.1 (kinematic tracking only), true once we solve block population.
    public boolean blocksPopulated = false;

    public SableCarriage(UUID entityId, ServerSubLevel subLevel,
                         Carriage carriage, int carriageIndex) {
        this.entityId = entityId;
        this.subLevel = subLevel;
        this.carriage = carriage;
        this.carriageIndex = carriageIndex;
    }

    /**
     * Returns true if this is the first carriage in the train.
     * The first carriage is the one that leads in the direction of travel.
     */
    public boolean isLeading() {
        return carriageIndex == 0;
    }

    /**
     * Returns true if this carriage's sub-level is still valid.
     * A sub-level becomes invalid if it was removed from the container.
     */
    public boolean isValid() {
        return subLevel != null && !subLevel.isRemoved();
    }
}