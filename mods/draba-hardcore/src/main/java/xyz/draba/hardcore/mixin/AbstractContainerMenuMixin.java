package xyz.draba.hardcore.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.draba.hardcore.MenuOwnership;

@Mixin(AbstractContainerMenu.class)
abstract class AbstractContainerMenuMixin {
    @Unique
    private MenuOwnership.Snapshot drabaHardcore$ownershipSnapshot;

    @Inject(method = "clicked", at = @At("HEAD"))
    private void drabaHardcore$beforeClick(int slot, int button, ContainerInput input, Player player,
                                           CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            drabaHardcore$ownershipSnapshot = MenuOwnership.capture(
                    (AbstractContainerMenu) (Object) this, slot, button, input, serverPlayer);
        }
    }

    @Inject(method = "clicked", at = @At("RETURN"))
    private void drabaHardcore$afterClick(int slot, int button, ContainerInput input, Player player,
                                          CallbackInfo ci) {
        if (drabaHardcore$ownershipSnapshot != null) {
            MenuOwnership.reconcile(drabaHardcore$ownershipSnapshot);
            drabaHardcore$ownershipSnapshot = null;
        }
    }
}
