package com.yaboiedchoi.createsabletrains.mixin;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.yaboiedchoi.createsabletrains.manager.SableTrainManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CarriageContraptionEntity.class)
public class CarriageContraptionEntityMixin {

    /**
     * INJECTION POINT 1 — Assembly
     *
     * setCarriage() is called when a carriage is fully bound to its entity,
     * both during initial train assembly and when an entity reloads from a
     * saved chunk. We inject at TAIL (the very end) so that by the time our
     * code runs, Create has finished setting up all its internal state —
     * entity.getCarriage() is non-null and the train reference is valid.
     *
     * We pass the entity and its level to the manager, which will allocate
     * a Sable sub-level and register it in both lookup maps.
     */
    @Inject(method = "setCarriage", at = @At("TAIL"))
    private void onSetCarriage(Carriage carriage, CallbackInfo ci) {
        CarriageContraptionEntity self = (CarriageContraptionEntity)(Object)this;

        // setCarriage() runs on both client and server.
        // Sable sub-levels only exist on the server, so we guard here.
        if (!(self.level() instanceof ServerLevel serverLevel))
            return;

        SableTrainManager.INSTANCE.onCarriageAssembled(self, serverLevel);
    }

    /**
     * INJECTION POINT 2 — Tick
     *
     * tick() runs every game tick (20 times per second) for every loaded
     * CarriageContraptionEntity. We inject at HEAD so our position update
     * happens at the start of the tick, before Create does its own movement
     * calculations — this keeps the sub-level in sync with no one-tick lag.
     *
     * We only update once every 2 ticks (tickCount % 2 == 0) to reduce
     * the number of physics pipeline calls while still keeping motion smooth.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        CarriageContraptionEntity self = (CarriageContraptionEntity)(Object)this;

        if (!(self.level() instanceof ServerLevel serverLevel))
            return;

        // Throttle updates slightly — every 2 ticks is smooth enough for v0.1.
        if (self.tickCount % 2 != 0)
            return;

        SableTrainManager.INSTANCE.onCarriageTick(self, serverLevel);
    }
}