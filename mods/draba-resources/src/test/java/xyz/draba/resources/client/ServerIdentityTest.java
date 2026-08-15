package xyz.draba.resources.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ServerIdentityTest {
    @Test
    void hardcoreHostnameUsesHardcoreIcon() {
        assertEquals(ServerIdentity.Icon.HARDCORE,
                ServerIdentity.iconForAddress("HARDCORE.EXAMPLE.COM:25565"));
    }

    @Test
    void survivalAndMenusUseSurvivalIcon() {
        assertEquals(ServerIdentity.Icon.SURVIVAL,
                ServerIdentity.iconForAddress("survival.example.com"));
        assertEquals(ServerIdentity.Icon.SURVIVAL,
                ServerIdentity.iconForAddress(null));
    }
}
