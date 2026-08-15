package xyz.draba.hardcore.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import xyz.draba.hardcore.DrabaHardcore;

@Mixin(MinecartItem.class)
abstract class MinecartItemMixin {
    @ModifyArg(
            method = "useOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"),
            index = 0)
    private Entity drabaHardcore$claimPlacedStorageMinecart(Entity entity,
                                                             @Local(argsOnly = true) UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
            DrabaHardcore.claimPlacedStorageEntity(serverPlayer, entity);
        }
        return entity;
    }
}
