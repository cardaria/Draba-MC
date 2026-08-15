package xyz.draba.hardcore.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.draba.hardcore.DrabaHardcore;

@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
    @Inject(method = "die", at = @At("HEAD"))
    private void drabaHardcore$beforeDeathDrops(DamageSource damageSource, CallbackInfo callback) {
        DrabaHardcore.onActualDeath((ServerPlayer) (Object) this);
    }
}
