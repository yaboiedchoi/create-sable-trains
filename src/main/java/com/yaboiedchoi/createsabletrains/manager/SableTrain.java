package com.yaboiedchoi.createsabletrains.manager;

import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Holds all Sable-related data for an entire train.
 * One SableTrain exists for every active Create train that has
 * at least one carriage loaded in the world.
 */
public class SableTrain {

    // The UUID of the Create Train object this wraps.
    // Matches carriage.train.id in Create's data model.
    public final UUID trainId;

    // A reference to the Create Train object.
    // Gives us access to speed, schedule, navigation, graph position, etc.
    public final Train train;

    // All carriages belonging to this train, in order from front to back.
    // Index 0 is always the leading carriage.
    private final List<SableCarriage> carriages = new ArrayList<>();

    // The display name of this train, synced from Create.
    // Will be used for nameplate support later.
    public Component name;

    // Whether this train is currently stopped at a station.
    // Used to trigger door open/close logic on all carriages at once.
    public boolean atStation = false;

    public SableTrain(UUID trainId, Train train) {
        this.trainId = trainId;
        this.train = train;
        this.name = train.name;
    }

    /**
     * Adds a carriage to this train.
     * Carriages should be added in order — index 0 first.
     */
    public void addCarriage(SableCarriage carriage) {
        carriages.add(carriage);
    }

    /**
     * Removes a carriage from this train by entity UUID.
     * Called when a carriage is unloaded or disassembled.
     */
    public void removeCarriage(UUID entityId) {
        carriages.removeIf(c -> c.entityId.equals(entityId));
    }

    /**
     * Returns an unmodifiable view of all carriages.
     * Use this for iteration — don't modify the list directly.
     */
    public List<SableCarriage> getCarriages() {
        return Collections.unmodifiableList(carriages);
    }

    /**
     * Returns the carriage at a given index, or null if out of range.
     */
    public SableCarriage getCarriage(int index) {
        if (index < 0 || index >= carriages.size())
            return null;
        return carriages.get(index);
    }

    /**
     * Returns true if this train has no carriages loaded.
     * The manager uses this to know when it's safe to clean up.
     */
    public boolean isEmpty() {
        return carriages.isEmpty();
    }

    /**
     * Returns the current speed of the train from Create's data.
     * Positive = forward, negative = backward, zero = stopped.
     */
    public double getSpeed() {
        return train.speed;
    }

    /**
     * Returns true if the train is currently stopped (speed is
     * effectively zero). Used for door logic later.
     */
    public boolean isStopped() {
        return Math.abs(getSpeed()) < 0.01;
    }

    /**
     * Opens or closes doors on all carriages simultaneously.
     * Placeholder for future door automation logic.
     */
    public void setDoorsOpen(boolean open) {
        this.atStation = open;
        for (SableCarriage carriage : carriages) {
            carriage.doorsOpen = open;
        }
    }
}