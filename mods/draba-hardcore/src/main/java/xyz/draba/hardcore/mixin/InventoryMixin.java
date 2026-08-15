package xyz.draba.hardcore.mixin;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.draba.hardcore.ItemOwnership;

@Mixin(Inventory.class)
abstract class InventoryMixin {
    @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"))
    private void drabaHardcore$clearPickedUpOwnership(ItemStack stack,
                                                      CallbackInfoReturnable<Boolean> cir) {
        ItemOwnership.clear(stack);
    }

    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"))
    private void drabaHardcore$clearPickedUpOwnershipAtSlot(int slot, ItemStack stack,
                                                            CallbackInfoReturnable<Boolean> cir) {
        ItemOwnership.clear(stack);
    }
}
