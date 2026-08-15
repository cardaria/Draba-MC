package xyz.draba.hardcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SpectateActionPayload(int action) implements CustomPacketPayload {
    public static final int START = 0;
    public static final int PREVIOUS = -1;
    public static final int NEXT = 1;
    public static final int STOP = 2;

    public static final Type<SpectateActionPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("draba_hardcore", "spectate_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpectateActionPayload> CODEC =
            CustomPacketPayload.codec(SpectateActionPayload::write, SpectateActionPayload::new);

    public SpectateActionPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(action);
    }

    public boolean isValid() {
        return action == START || action == PREVIOUS || action == NEXT || action == STOP;
    }

    @Override
    public Type<SpectateActionPayload> type() {
        return TYPE;
    }
}
