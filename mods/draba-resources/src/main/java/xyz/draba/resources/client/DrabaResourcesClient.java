package xyz.draba.resources.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DrabaResourcesClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("draba_resources");
    public static final Identifier FACADE_PACK = Identifier.fromNamespaceAndPath(
            "draba_resources", "managed");
    public static final Identifier SURVIVAL_ICON = Identifier.fromNamespaceAndPath(
            "draba_resources", "textures/gui/survival.png");
    public static final Identifier HARDCORE_ICON = Identifier.fromNamespaceAndPath(
            "draba_resources", "textures/gui/hardcore.png");

    @Override
    public void onInitializeClient() {
        ModContainer container = FabricLoader.getInstance()
                .getModContainer("draba_resources")
                .orElseThrow(() -> new IllegalStateException("Draba Resources mod container is missing"));

        boolean registered = ResourceLoader.registerBuiltinPack(
                FACADE_PACK,
                container,
                Component.literal("Draba Resources"),
                PackActivationType.ALWAYS_ENABLED);
        if (!registered) {
            LOGGER.error("Could not register the Draba Resources facade pack");
        }
    }
}
