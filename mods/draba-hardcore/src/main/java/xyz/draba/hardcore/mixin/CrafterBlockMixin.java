package xyz.draba.hardcore.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.draba.hardcore.ProcessingOwnership;

@Mixin(CrafterBlock.class)
abstract class CrafterBlockMixin {
    @Inject(method = "dispenseFrom", at = @At("HEAD"))
    private void drabaHardcore$beforeCraft(BlockState state, ServerLevel level, BlockPos pos,
                                           CallbackInfo ci) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof CrafterBlockEntity crafter) {
            ProcessingOwnership.beforeCrafter(crafter);
        }
    }

    @Inject(method = "dispenseFrom", at = @At("RETURN"))
    private void drabaHardcore$afterCraft(BlockState state, ServerLevel level, BlockPos pos,
                                          CallbackInfo ci) {
        ProcessingOwnership.afterCrafter();
    }

    @Inject(method = "dispenseItem", at = @At("HEAD"))
    private void drabaHardcore$ownResult(ServerLevel level, BlockPos pos, CrafterBlockEntity crafter,
                                         ItemStack output, BlockState state, RecipeHolder<?> recipe,
                                         CallbackInfo ci) {
        ProcessingOwnership.ownCrafterOutput(crafter, output);
    }
}
