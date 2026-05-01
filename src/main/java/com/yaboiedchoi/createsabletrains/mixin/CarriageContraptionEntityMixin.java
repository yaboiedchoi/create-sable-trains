package com.yaboiedchoi.createsabletrains.mixin;

import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.yaboiedchoi.createsabletrains.CreateSableTrains;

@Mixin(CarriageContraptionEntity.class)
public class CarriageContraptionEntityMixin {

    @Inject(
            method = "tick", // <-- WHICH method we're injecting into
            at = @At("HEAD") // <-- inject at the very start of tick()
    )
    private void onTick(CallbackInfo ci) {
        // "ci" is CallbackInfo - it lets us cancel the method early if we want to.
        // We won't cancel anything right now, just log.

        // Cast "this" to the real entity type so we can access its fields
        CarriageContraptionEntity self = (CarriageContraptionEntity)(Object)this;
        Level level = self.level();

        // Only log on the server side, and only once every 100 ticks (5 seconds)
        // so we don't spam the console
        if (!level.isClientSide && self.tickCount % 100 == 0) {
            CreateSableTrains.LOGGER.info(
                    "CarriageContraptionEntity ticking at position: {}",
                    self.position()
            );
        }
    }
}