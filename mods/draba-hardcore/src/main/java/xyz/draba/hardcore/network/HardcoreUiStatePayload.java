package xyz.draba.hardcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record HardcoreUiStatePayload(
        boolean active,
        long eligibleAtEpochMillis,
        boolean watching,
        String targetName,
        int targetIndex,
        int targetCount) implements CustomPacketPayload {
    public static final Type<HardcoreUiStatePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("draba_hardcore", "ui_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HardcoreUiStatePayload> CODEC =
            CustomPacketPayload.codec(HardcoreUiStatePayload::write, HardcoreUiStatePayload::new);

    public HardcoreUiStatePayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBoolean(),
                buffer.readLong(),
                buffer.readBoolean(),
                buffer.readUtf(64),
                buffer.readVarInt(),
                buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(active);
        buffer.writeLong(eligibleAtEpochMillis);
        buffer.writeBoolean(watching);
        buffer.writeUtf(targetName, 64);
        buffer.writeVarInt(targetIndex);
        buffer.writeVarInt(targetCount);
    }

    public static HardcoreUiStatePayload inactive() {
        return new HardcoreUiStatePayload(false, 0L, false, "", -1, 0);
    }

    @Override
    public Type<HardcoreUiStatePayload> type() {
        return TYPE;
    }
}
