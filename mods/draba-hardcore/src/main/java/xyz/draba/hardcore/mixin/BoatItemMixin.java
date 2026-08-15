package xyz.draba.hardcore.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoatItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import xyz.draba.hardcore.DrabaHardcore;

@Mixin(BoatItem.class)
abstract class BoatItemMixin {
    @ModifyArg(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"),
            index = 0)
    private Entity drabaHardcore$claimPlacedStorageBoat(Entity entity,
                                                         @Local(argsOnly = true) Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            DrabaHardcore.claimPlacedStorageEntity(serverPlayer, entity);
        }
        return entity;
    }
}
