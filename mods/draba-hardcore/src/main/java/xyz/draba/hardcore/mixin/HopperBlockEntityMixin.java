package xyz.draba.hardcore.mixin;

import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.draba.hardcore.TransferOwnership;

@Mixin(HopperBlockEntity.class)
abstract class HopperBlockEntityMixin {
    @Inject(method = "tryMoveInItem", at = @At("HEAD"))
    private static void drabaHardcore$beforeInsert(Container source, Container destination,
                                                   ItemStack moving, int slot, Direction side,
                                                   CallbackInfoReturnable<ItemStack> cir) {
        TransferOwnership.beforeHopperInsert(destination, moving, slot);
    }

    @Inject(method = "tryMoveInItem", at = @At("RETURN"))
    private static void drabaHardcore$afterInsert(Container source, Container destination,
                                                  ItemStack moving, int slot, Direction side,
                                                  CallbackInfoReturnable<ItemStack> cir) {
        TransferOwnership.afterHopperInsert(cir.getReturnValue());
    }
}
