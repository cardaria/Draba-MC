package xyz.draba.resources.client.mixin;

import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.draba.resources.client.DrabaResourcesClient;
import xyz.draba.resources.client.ManagedPackPolicy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(PackRepository.class)
abstract class PackRepositoryMixin {
    private static final Set<String> draba$warnedMissing = ConcurrentHashMap.newKeySet();

    @Inject(method = "rebuildSelected", at = @At("RETURN"), cancellable = true)
    private void draba$enableAndOrderManagedPacks(
            Collection<String> requestedIds,
            CallbackInfoReturnable<List<Pack>> callback) {
        PackRepository repository = (PackRepository) (Object) this;
        if (!repository.isAvailable(ManagedPackPolicy.FACADE_ID)) {
            return;
        }

        Collection<String> availableIds = repository.getAvailableIds();
        List<String> selectedIds = callback.getReturnValue().stream()
                .map(Pack::getId)
                .toList();
        List<String> orderedIds = ManagedPackPolicy.enforceOrder(selectedIds, availableIds);
        List<Pack> orderedPacks = new ArrayList<>(orderedIds.size());
        for (String id : orderedIds) {
            Pack pack = repository.getPack(id);
            if (pack != null) {
                orderedPacks.add(pack);
            }
        }
        callback.setReturnValue(List.copyOf(orderedPacks));

        List<String> missing = ManagedPackPolicy.missingTechnicalPacks(availableIds);
        for (String id : missing) {
            if (draba$warnedMissing.add(id)) {
                DrabaResourcesClient.LOGGER.warn(
                        "Managed resource pack '{}' is missing; continuing without it", id);
            }
        }
        for (String id : ManagedPackPolicy.LOAD_ORDER) {
            if (availableIds.contains(id)) {
                draba$warnedMissing.remove(id);
            }
        }
    }
}
