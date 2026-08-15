package xyz.draba.resources.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.draba.resources.client.DrabaResourcesClient;
import xyz.draba.resources.client.ManagedPackPolicy;
import xyz.draba.resources.client.ServerIdentity;

@Mixin(net.minecraft.client.gui.screens.packs.PackSelectionScreen.class)
abstract class PackSelectionScreenMixin {
    @Inject(method = "getPackIcon", at = @At("HEAD"), cancellable = true)
    private void draba$useServerIcon(
            Pack pack, CallbackInfoReturnable<Identifier> callback) {
        if (!ManagedPackPolicy.FACADE_ID.equals(pack.getId())) {
            return;
        }

        ServerData server = Minecraft.getInstance().getCurrentServer();
        String address = server == null ? null : server.ip;
        Identifier icon = ServerIdentity.iconForAddress(address) == ServerIdentity.Icon.HARDCORE
                ? DrabaResourcesClient.HARDCORE_ICON
                : DrabaResourcesClient.SURVIVAL_ICON;
        callback.setReturnValue(icon);
    }
}
