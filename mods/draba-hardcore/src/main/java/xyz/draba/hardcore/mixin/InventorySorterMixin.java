package xyz.draba.hardcore.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.draba.hardcore.ContainerSortOwnership;

import java.util.List;

@Pseudo
@Mixin(targets = "net.kyrptonaught.inventorysorter.inventory.container.ContainerStacks", remap = false)
abstract class InventorySorterMixin {
    @Inject(method = "set", at = @At("HEAD"), remap = false, require = 0)
    private static void drabaHardcore$beforeSortSet(Container container, int start,
                                                    List<ItemStack> replacement, CallbackInfo ci) {
        ContainerSortOwnership.beforeSet(container, start, replacement);
    }

    @Inject(method = "set", at = @At("RETURN"), remap = false, require = 0)
    private static void drabaHardcore$afterSortSet(Container container, int start,
                                                   List<ItemStack> replacement, CallbackInfo ci) {
        ContainerSortOwnership.afterSet();
    }
}
