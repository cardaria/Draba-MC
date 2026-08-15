package me.drex.antixray.fabric.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import me.drex.antixray.fabric.mixin.debug.PalettedContainerAccessor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

public class DebugCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("debug-antixray")
                .then(
                    Commands.literal("place-blockstates")
                        .then(
                            Commands.argument("blockstate-count", IntegerArgumentType.integer(1))
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    int blockStateCount = IntegerArgumentType.getInteger(context, "blockstate-count");
                                    ServerLevel level = source.getLevel();
                                    Vec3 position = source.getPosition();
                                    BlockPos blockPos = BlockPos.containing(position);
                                    ChunkPos chunkPos = ChunkPos.containing(blockPos);

                                    List<BlockState> blockStates = BuiltInRegistries.BLOCK.entrySet().stream().map(Map.Entry::getValue).flatMap(block -> block.getStateDefinition().getPossibleStates().stream()).toList();
                                    if (blockStateCount > blockStates.size()) {
                                        source.sendFailure(Component.literal("There are only " + blockStateCount + " block states."));
                                        return 0;
                                    }

                                    BlockPos origin = new BlockPos(chunkPos.getMinBlockX(), 0, chunkPos.getMinBlockZ());
                                    for (int i = 0; i < blockStateCount; i++) {
                                        int x = i % 8;
                                        int z = (i / 8) % 8;
                                        int y = i / 64;

                                        int spacedX = x * 2;
                                        int spacedY = y * 2;
                                        int spacedZ = z * 2;

                                        level.setBlock(origin.offset(spacedX, spacedY, spacedZ), blockStates.get(i), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS | Block.UPDATE_LIMIT, 0);
                                    }
                                    return 1;
                                })
                        )
                ).then(
                    Commands.literal("palette-info")
                        .executes(context -> {
                            var chunk = context.getSource().getLevel().getChunk(context.getSource().getPlayer().blockPosition());
                            var s = chunk.getSection(context.getSource().getLevel().getSectionIndex(context.getSource().getPlayer().getBlockY()));

                            var a = ((PalettedContainerAccessor<BlockState>) s.getStates()).getData();

                            context.getSource().sendSuccess(() -> Component.literal("Chunk: " + chunk.getPos() + " Palette: " + a.palette() + " | " + " Size: " + a.palette().getSize() + " | Bits: " + a.storage().getBits()), false);
                            return 1;
                        })
                )
        );
    }
}
