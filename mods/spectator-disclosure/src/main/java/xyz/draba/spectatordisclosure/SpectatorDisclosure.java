package xyz.draba.spectatordisclosure;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.draba.spectate.DrabaSpectate;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SpectatorDisclosure implements ModInitializer {
    public static final String MOD_ID = "spectator_disclosure";
    public static final Identifier WATCH_PERMISSION = Identifier.fromNamespaceAndPath("drabax", "watch");
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final int REFRESH_INTERVAL_TICKS = 40;

    private final Map<UUID, Integer> lastCounts = new HashMap<>();
    private int refreshTicks;

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(this::updateIndicators);
        LOGGER.info("Spectator Disclosure initialized; permission node is drabax.watch");
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("watch")
                .requires(PermissionPredicates.require(WATCH_PERMISSION, PermissionLevel.GAMEMASTERS))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(SpectatorDisclosure::startWatching))
                .then(Commands.literal("stop")
                        .executes(SpectatorDisclosure::stopWatching)));
    }

    private static int startWatching(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer watcher = context.getSource().getPlayerOrException();
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            if (!DrabaSpectate.startWatching(watcher, target)) {
                return 0;
            }
            return 1;
        } catch (Exception error) {
            LOGGER.error("Could not start /watch session", error);
            context.getSource().sendFailure(Component.literal("Could not start watching that player."));
            return 0;
        }
    }

    private static int stopWatching(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer watcher = context.getSource().getPlayerOrException();
            if (!DrabaSpectate.isWatching(watcher.getUUID())) {
                context.getSource().sendFailure(Component.literal("You are not watching anyone."));
                return 0;
            }
            return DrabaSpectate.stopWatching(watcher) ? 1 : 0;
        } catch (Exception error) {
            LOGGER.error("Could not stop /watch session", error);
            context.getSource().sendFailure(Component.literal("Could not stop the watch session."));
            return 0;
        }
    }

    private void updateIndicators(MinecraftServer server) {
        Map<UUID, Integer> currentCounts = DrabaSpectate.activeTargetCounts();

        boolean refresh = ++refreshTicks >= REFRESH_INTERVAL_TICKS;
        if (refresh) {
            refreshTicks = 0;
        }

        Set<UUID> previouslyVisible = Set.copyOf(lastCounts.keySet());
        for (Map.Entry<UUID, Integer> entry : currentCounts.entrySet()) {
            int count = entry.getValue();
            Integer previous = lastCounts.get(entry.getKey());
            if (refresh || previous == null || previous != count) {
                ServerPlayer target = server.getPlayerList().getPlayer(entry.getKey());
                if (target != null) {
                    target.sendSystemMessage(indicator(count), true);
                }
            }
        }

        for (UUID targetId : previouslyVisible) {
            if (!currentCounts.containsKey(targetId)) {
                ServerPlayer target = server.getPlayerList().getPlayer(targetId);
                if (target != null) {
                    target.sendSystemMessage(Component.empty(), true);
                }
            }
        }

        lastCounts.clear();
        lastCounts.putAll(currentCounts);
    }

    private static Component indicator(int count) {
        String noun = count == 1 ? " anonymous spectator" : " anonymous spectators";
        return Component.literal("\uD83D\uDC41 ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(count + noun).withStyle(ChatFormatting.GRAY));
    }
}
