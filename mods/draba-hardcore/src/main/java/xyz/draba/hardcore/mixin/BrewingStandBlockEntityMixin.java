package xyz.draba.hardcore.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.draba.hardcore.ProcessingOwnership;

@Mixin(BrewingStandBlockEntity.class)
abstract class BrewingStandBlockEntityMixin {
    @ModifyArg(
            method = "doBrew",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/Containers;dropItemStack(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V"),
            index = 4)
    private static ItemStack drabaHardcore$ownDroppedRemainder(ItemStack remainder) {
        ProcessingOwnership.ownBrewingRemainder(remainder);
        return remainder;
    }

    @Inject(method = "doBrew", at = @At("HEAD"))
    private static void drabaHardcore$beforeBrew(Level level, BlockPos pos,
                                                 NonNullList<ItemStack> items, CallbackInfo ci) {
        ProcessingOwnership.beforeBrewing(items);
    }

    @Inject(method = "doBrew", at = @At("RETURN"))
    private static void drabaHardcore$afterBrew(Level level, BlockPos pos,
                                                NonNullList<ItemStack> items, CallbackInfo ci) {
        ProcessingOwnership.afterBrewing(items);
    }
}
