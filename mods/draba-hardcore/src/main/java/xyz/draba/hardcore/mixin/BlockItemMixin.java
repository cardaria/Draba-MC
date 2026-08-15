package xyz.draba.hardcore.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.draba.hardcore.DrabaHardcore;

@Mixin(BlockItem.class)
abstract class BlockItemMixin {
    @Inject(method = "place", at = @At("RETURN"))
    private void drabaHardcore$claimPlacedStorage(BlockPlaceContext context,
                                                  CallbackInfoReturnable<InteractionResult> callback) {
        if (callback.getReturnValue().consumesAction() && context.getPlayer() instanceof ServerPlayer player) {
            DrabaHardcore.claimPlacedStorage(player, context.getClickedPos());
        }
    }
}
