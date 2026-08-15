package xyz.draba.hardcore.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.draba.hardcore.ProcessingOwnership;

@Mixin(AbstractFurnaceBlockEntity.class)
abstract class AbstractFurnaceBlockEntityMixin {
    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void drabaHardcore$beforeServerTick(ServerLevel level, BlockPos pos, BlockState state,
                                                       AbstractFurnaceBlockEntity furnace, CallbackInfo ci) {
        ProcessingOwnership.beforeFurnaceTick(furnace);
    }

    @Inject(method = "serverTick", at = @At("RETURN"))
    private static void drabaHardcore$afterServerTick(ServerLevel level, BlockPos pos, BlockState state,
                                                      AbstractFurnaceBlockEntity furnace, CallbackInfo ci) {
        ProcessingOwnership.afterFurnaceTick(furnace);
    }

    @Inject(method = "burn", at = @At("HEAD"))
    private static void drabaHardcore$beforeBurn(NonNullList<ItemStack> items, ItemStack input,
                                                 ItemStack recipeOutput, CallbackInfo ci) {
        ProcessingOwnership.beforeFurnace(items);
    }

    @Inject(method = "burn", at = @At("RETURN"))
    private static void drabaHardcore$afterBurn(NonNullList<ItemStack> items, ItemStack input,
                                                ItemStack recipeOutput, CallbackInfo ci) {
        ProcessingOwnership.afterFurnace(items);
    }
}
