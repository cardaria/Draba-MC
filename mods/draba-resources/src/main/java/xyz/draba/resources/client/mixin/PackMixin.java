package xyz.draba.resources.client.mixin;

import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.draba.resources.client.ManagedPackPolicy;

@Mixin(Pack.class)
abstract class PackMixin {
    @Shadow
    public abstract String getId();

    @Inject(method = "isRequired", at = @At("HEAD"), cancellable = true)
    private void draba$makeManagedPacksRequired(CallbackInfoReturnable<Boolean> callback) {
        if (ManagedPackPolicy.isManaged(getId())) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "isFixedPosition", at = @At("HEAD"), cancellable = true)
    private void draba$fixManagedPackPosition(CallbackInfoReturnable<Boolean> callback) {
        if (ManagedPackPolicy.isManaged(getId())) {
            callback.setReturnValue(true);
        }
    }
}
