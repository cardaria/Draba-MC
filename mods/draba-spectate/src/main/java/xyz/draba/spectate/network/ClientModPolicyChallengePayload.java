package xyz.draba.spectate.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientModPolicyChallengePayload(long nonce) implements CustomPacketPayload {
    public static final Type<ClientModPolicyChallengePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("draba_spectate", "mod_policy_challenge"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientModPolicyChallengePayload> CODEC =
            CustomPacketPayload.codec(
                    (payload, buffer) -> buffer.writeLong(payload.nonce()),
                    buffer -> new ClientModPolicyChallengePayload(buffer.readLong()));

    @Override
    public Type<ClientModPolicyChallengePayload> type() {
        return TYPE;
    }
}
