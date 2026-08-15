package xyz.draba.spectate.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record ClientModPolicyResponsePayload(long nonce, List<String> modIds)
        implements CustomPacketPayload {
    public static final int MAX_MODS = 512;
    public static final int MAX_ID_LENGTH = 128;
    public static final Type<ClientModPolicyResponsePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("draba_spectate", "mod_policy_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientModPolicyResponsePayload> CODEC =
            CustomPacketPayload.codec(ClientModPolicyResponsePayload::write,
                    ClientModPolicyResponsePayload::read);

    public ClientModPolicyResponsePayload {
        modIds = List.copyOf(modIds);
        if (modIds.size() > MAX_MODS) {
            throw new IllegalArgumentException("Too many reported client mods");
        }
    }

    private static ClientModPolicyResponsePayload read(RegistryFriendlyByteBuf buffer) {
        long nonce = buffer.readLong();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_MODS) {
            throw new IllegalArgumentException("Invalid reported client mod count: " + count);
        }
        List<String> modIds = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            modIds.add(buffer.readUtf(MAX_ID_LENGTH));
        }
        return new ClientModPolicyResponsePayload(nonce, modIds);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeLong(nonce);
        buffer.writeVarInt(modIds.size());
        for (String modId : modIds) {
            buffer.writeUtf(modId, MAX_ID_LENGTH);
        }
    }

    @Override
    public Type<ClientModPolicyResponsePayload> type() {
        return TYPE;
    }
}
