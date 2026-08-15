package xyz.draba.network;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import com.velocitypowered.api.proxy.server.ServerPing;

import java.util.UUID;
import java.util.List;

public final class DrabaNetworkNoticesTest {
    private DrabaNetworkNoticesTest() {
    }

    public static void main(String[] args) {
        String survival = plain(DrabaNetworkNotices.brandedKickReason(
                "main", Component.text("Client pack update required.")));
        require(survival.startsWith("Draba XSMP\n"), "Survival kick lacks its full brand");
        require(survival.contains("fully restart Minecraft"), "Modpack kick lacks restart guidance");

        String hardcore = plain(DrabaNetworkNotices.brandedKickReason(
                "hardcore", Component.text("Client pack update required.")));
        require(hardcore.startsWith("Draba HC XSMP\n"), "Hardcore kick lacks its full brand");

        String ordinary = plain(DrabaNetworkNotices.brandedKickReason(
                "main", Component.text("You are not whitelisted.")));
        require(!ordinary.contains("fully restart Minecraft"),
                "Ordinary kicks must not show irrelevant restart guidance");

        require(plain(DrabaNetworkNotices.joinLeaveMessage("main", "Alice", true))
                        .equals("[SC] Alice joined the game"),
                "Survival join mirrored to Hardcore lacks its SC badge");
        require(plain(DrabaNetworkNotices.joinLeaveMessage("main", "Alice", false))
                        .equals("[SC] Alice left the game"),
                "Survival leave mirrored to Hardcore lacks its SC badge");
        require(plain(DrabaNetworkNotices.joinLeaveMessage("hardcore", "Bob", true))
                        .equals("[HC] Bob joined the game"),
                "Hardcore join mirrored to Survival lost its HC badge");
        require(plain(DrabaNetworkNotices.joinLeaveMessage("lobby", "Carol", true))
                        .equals("Carol joined the game"),
                "Unknown backends must not receive a misleading badge");

        require("main".equals(DrabaNetworkNotices.serverForVirtualHost("survival.example.com")),
                "Survival status host was not recognized");
        require("hardcore".equals(
                        DrabaNetworkNotices.serverForVirtualHost("HARDCORE.EXAMPLE.COM.:25565")),
                "Hardcore status host normalization failed");
        require(DrabaNetworkNotices.serverForVirtualHost("example.org") == null,
                "Unrelated pings must not be rewritten");

        long joinedAt = 1_800_000_000L;
        UUID marker = DrabaNetworkNotices.statusSampleId(joinedAt);
        require(marker.getMostSignificantBits() == DrabaNetworkNotices.JOIN_TIME_MAGIC,
                "Status join marker magic changed");
        require(marker.getLeastSignificantBits() == joinedAt,
                "Status join timestamp did not round-trip");

        ServerPing.SamplePlayer stale = new ServerPing.SamplePlayer(
                "Stale", UUID.randomUUID());
        ServerPing.SamplePlayer current = new ServerPing.SamplePlayer("Alice", marker);
        ServerPing originalPing = ServerPing.builder()
                .version(new ServerPing.Version(1, "test"))
                .onlinePlayers(99)
                .maximumPlayers(30)
                .samplePlayers(stale)
                .description(Component.text("test"))
                .build();
        ServerPing updatedPing = DrabaNetworkNotices.withStatusPlayers(
                originalPing, 1, List.of(current));
        require(updatedPing.getPlayers().orElseThrow().getOnline() == 1,
                "Status ping did not replace the online count");
        require(updatedPing.getPlayers().orElseThrow().getSample().equals(List.of(current)),
                "Status ping retained stale backend sample players");

        System.out.println("Network notice, status, and branded kick-message audit passed.");
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
