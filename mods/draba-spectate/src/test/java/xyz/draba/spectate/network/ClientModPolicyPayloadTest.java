package xyz.draba.spectate.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientModPolicyPayloadTest {
    @Test
    void challengeRoundTripPreservesNonce() {
        ClientModPolicyChallengePayload expected =
                new ClientModPolicyChallengePayload(918273645L);
        RegistryFriendlyByteBuf buffer = buffer();

        ClientModPolicyChallengePayload.CODEC.encode(buffer, expected);

        assertEquals(expected, ClientModPolicyChallengePayload.CODEC.decode(buffer));
    }

    @Test
    void responseRoundTripPreservesBoundedModList() {
        ClientModPolicyResponsePayload expected = new ClientModPolicyResponsePayload(
                -1729L, List.of("automodpack", "draba_spectate", "hotbar-keys"));
        RegistryFriendlyByteBuf buffer = buffer();

        ClientModPolicyResponsePayload.CODEC.encode(buffer, expected);

        assertEquals(expected, ClientModPolicyResponsePayload.CODEC.decode(buffer));
    }

    @Test
    void responseRejectsUnboundedModCounts() {
        List<String> tooMany = java.util.Collections.nCopies(
                ClientModPolicyResponsePayload.MAX_MODS + 1, "mod");

        assertThrows(IllegalArgumentException.class,
                () -> new ClientModPolicyResponsePayload(1L, tooMany));
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }
}
