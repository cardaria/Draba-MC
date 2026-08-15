package me.drex.antixray.fabric;

import me.drex.antixray.fabric.command.DebugCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;

public class AntiXrayMod implements ModInitializer {

    private AntiXrayFabric mod;

    @Override
    public void onInitialize() {
        this.mod = new AntiXrayFabric();
        CommandRegistrationCallback.EVENT.register((commandDispatcher, commandBuildContext, commandSelection) -> {
            if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
                DebugCommand.register(commandDispatcher);
            }
        });
    }
}
