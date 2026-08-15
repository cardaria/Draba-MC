package xyz.draba.spectate.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SpectateStatePayload(
        boolean watching,
        boolean arming,
        int armingTicksRemaining,
        boolean startAllowed,
        String startReason,
        String targetName,
        int targetEntityId,
        int targetIndex,
        int targetCount) implements CustomPacketPayload {
    private static final int MAX_TEXT_LENGTH = 128;

    public static final Type<SpectateStatePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("draba_spectate", "state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpectateStatePayload> CODEC =
            CustomPacketPayload.codec(SpectateStatePayload::write, SpectateStatePayload::new);

    public SpectateStatePayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readUtf(MAX_TEXT_LENGTH),
                buffer.readUtf(MAX_TEXT_LENGTH),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(watching);
        buffer.writeBoolean(arming);
        buffer.writeVarInt(armingTicksRemaining);
        buffer.writeBoolean(startAllowed);
        buffer.writeUtf(startReason, MAX_TEXT_LENGTH);
        buffer.writeUtf(targetName, MAX_TEXT_LENGTH);
        buffer.writeVarInt(targetEntityId);
        buffer.writeVarInt(targetIndex);
        buffer.writeVarInt(targetCount);
    }

    @Override
    public Type<SpectateStatePayload> type() {
        return TYPE;
    }
}
