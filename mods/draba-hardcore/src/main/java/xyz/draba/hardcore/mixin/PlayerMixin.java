package xyz.draba.hardcore.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.draba.hardcore.DrabaHardcore;

@Mixin(Player.class)
abstract class PlayerMixin {
    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"))
    private void drabaHardcore$ownDroppedItems(ItemStack stack, boolean randomDirection,
                                               CallbackInfoReturnable<ItemEntity> cir) {
        if ((Object) this instanceof ServerPlayer player && !stack.isEmpty()) {
            DrabaHardcore.markPlayerDrop(player, stack);
        }
    }
}
