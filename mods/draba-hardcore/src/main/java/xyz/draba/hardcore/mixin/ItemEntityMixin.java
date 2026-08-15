package xyz.draba.hardcore.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.draba.hardcore.TransferOwnership;

@Mixin(ItemEntity.class)
abstract class ItemEntityMixin {
    @Inject(method = "merge(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"))
    private static void drabaHardcore$beforeMerge(ItemStack target, ItemStack source, int limit,
                                                  CallbackInfoReturnable<ItemStack> cir) {
        TransferOwnership.beforeItemMerge(target, source);
    }

    @Inject(method = "merge(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"))
    private static void drabaHardcore$afterMerge(ItemStack target, ItemStack source, int limit,
                                                 CallbackInfoReturnable<ItemStack> cir) {
        TransferOwnership.afterItemMerge(cir.getReturnValue());
    }
}
