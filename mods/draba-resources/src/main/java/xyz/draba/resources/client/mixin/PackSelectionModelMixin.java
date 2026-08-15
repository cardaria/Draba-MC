package xyz.draba.resources.client.mixin;

import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.draba.resources.client.ManagedPackPolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Mixin(PackSelectionModel.class)
abstract class PackSelectionModelMixin {
    @Shadow
    @Final
    private List<Pack> selected;

    @Inject(method = "getSelected", at = @At("HEAD"))
    private void draba$orderBeforeDisplaying(
            CallbackInfoReturnable<Stream<PackSelectionModel.Entry>> callback) {
        draba$enforceDisplayOrder();
    }

    @Inject(method = "updateRepoSelectedList", at = @At("HEAD"))
    private void draba$orderBeforeUpdatingRepository(CallbackInfo callback) {
        draba$enforceDisplayOrder();
    }

    @Inject(method = "getSelected", at = @At("RETURN"), cancellable = true)
    private void draba$hideSelectedTechnicalPacks(
            CallbackInfoReturnable<Stream<PackSelectionModel.Entry>> callback) {
        callback.setReturnValue(callback.getReturnValue()
                .filter(entry -> !ManagedPackPolicy.isTechnical(entry.getId())));
    }

    @Inject(method = "getUnselected", at = @At("RETURN"), cancellable = true)
    private void draba$hideUnselectedTechnicalPacks(
            CallbackInfoReturnable<Stream<PackSelectionModel.Entry>> callback) {
        callback.setReturnValue(callback.getReturnValue()
                .filter(entry -> !ManagedPackPolicy.isTechnical(entry.getId())));
    }

    private void draba$enforceDisplayOrder() {
        Map<String, Pack> packsById = new LinkedHashMap<>();
        for (Pack pack : selected) {
            packsById.putIfAbsent(pack.getId(), pack);
        }
        List<String> orderedIds = ManagedPackPolicy.enforceDisplayOrder(packsById.keySet());
        if (orderedIds.equals(selected.stream().map(Pack::getId).toList())) {
            return;
        }
        selected.clear();
        for (String id : orderedIds) {
            Pack pack = packsById.get(id);
            if (pack != null) {
                selected.add(pack);
            }
        }
    }
}
