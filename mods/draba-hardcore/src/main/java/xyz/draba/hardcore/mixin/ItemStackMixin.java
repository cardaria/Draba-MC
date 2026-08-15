package xyz.draba.hardcore.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.draba.hardcore.ItemOwnership;

@Mixin(ItemStack.class)
abstract class ItemStackMixin {
    @Inject(method = "split", at = @At("HEAD"))
    private void drabaHardcore$beforeSplit(int amount, CallbackInfoReturnable<ItemStack> cir) {
        ItemOwnership.beforeSplit((ItemStack) (Object) this);
    }

    @Inject(method = "split", at = @At("RETURN"))
    private void drabaHardcore$afterSplit(int amount, CallbackInfoReturnable<ItemStack> cir) {
        ItemOwnership.afterSplit((ItemStack) (Object) this, cir.getReturnValue());
    }

    @Inject(method = "isSameItemSameComponents", at = @At("HEAD"), cancellable = true)
    private static void drabaHardcore$ignoreLedgerForStacking(ItemStack first, ItemStack second,
                                                              CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(ItemOwnership.sameItemAndComponentsIgnoringOwnership(first, second));
    }

    @Inject(method = "matches", at = @At("HEAD"), cancellable = true)
    private static void drabaHardcore$keepLedgerInSynchronization(ItemStack first, ItemStack second,
                                                                  CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(first == second || (first.getCount() == second.getCount()
                && first.is(second.getItem()) && first.getComponents().equals(second.getComponents())));
    }

    @Inject(method = "hashItemAndComponents", at = @At("HEAD"), cancellable = true)
    private static void drabaHardcore$hashWithoutLedger(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ItemOwnership.hashItemAndComponentsIgnoringOwnership(stack));
    }
}
