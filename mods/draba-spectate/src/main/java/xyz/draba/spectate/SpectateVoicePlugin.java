package xyz.draba.spectate;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.SoundPacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.VoiceDistanceEvent;
import de.maxhenkel.voicechat.api.packets.LocationalSoundPacket;

import java.util.UUID;

public final class SpectateVoicePlugin implements VoicechatPlugin {
    private static final int FILTER_PRIORITY = Integer.MAX_VALUE;
    private static final int RELAY_PRIORITY = Integer.MIN_VALUE;

    @Override
    public String getPluginId() {
        return DrabaSpectate.MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(EntitySoundPacketEvent.class,
                event -> filterAutomaticDelivery(event), FILTER_PRIORITY);
        registration.registerEvent(LocationalSoundPacketEvent.class,
                event -> filterAutomaticDelivery(event), FILTER_PRIORITY);
        registration.registerEvent(StaticSoundPacketEvent.class,
                event -> filterAutomaticDelivery(event), FILTER_PRIORITY);
        registration.registerEvent(VoiceDistanceEvent.class,
                SpectateVoicePlugin::relayFromEffectiveOrigin, RELAY_PRIORITY);
    }

    private static void filterAutomaticDelivery(SoundPacketEvent<?> event) {
        SpectateVoiceState.Snapshot snapshot = SpectateVoiceState.snapshot();
        UUID senderId = playerId(event.getSenderConnection());
        UUID receiverId = playerId(event.getReceiverConnection());
        if (SpectateVoiceRouting.replacesAutomaticDelivery(
                event.getSource(),
                snapshot.isWatching(senderId),
                snapshot.isWatching(receiverId))) {
            event.cancel();
        }
    }

    private static void relayFromEffectiveOrigin(VoiceDistanceEvent event) {
        SpectateVoiceState.Snapshot snapshot = SpectateVoiceState.snapshot();
        UUID senderId = playerId(event.getSenderConnection());
        if (senderId == null || snapshot.sessions().isEmpty()) {
            return;
        }
        boolean senderWatching = snapshot.isWatching(senderId);
        SpectateVoiceRouting.VoicePosition source = snapshot.effectivePosition(senderId);
        if (source == null) {
            return;
        }

        for (UUID receiverId : snapshot.players().keySet()) {
            if (senderId.equals(receiverId)) {
                continue;
            }
            boolean receiverWatching = snapshot.isWatching(receiverId);
            if (!SpectateVoiceRouting.needsManualDelivery(senderWatching, receiverWatching)) {
                continue;
            }
            SpectateVoiceRouting.VoicePosition listener = snapshot.effectivePosition(receiverId);
            SpectateVoiceRouting.VoicePosition camera = snapshot.cameraPosition(receiverId);
            if (!SpectateVoiceRouting.isAudible(source, listener, event.getDistance())
                    || camera == null) {
                continue;
            }

            VoicechatConnection receiver = event.getVoicechat().getConnectionOf(receiverId);
            if (!canReceiveProximity(event.getSenderConnection(), receiver)) {
                continue;
            }
            SpectateVoiceRouting.VoicePosition rebased =
                    SpectateVoiceRouting.rebase(source, listener, camera);
            LocationalSoundPacket packet = event.getPacket().locationalSoundPacketBuilder()
                    .position(event.getVoicechat().createPosition(
                            rebased.x(), rebased.y(), rebased.z()))
                    .distance(event.getDistance())
                    .build();
            event.getVoicechat().sendLocationalSoundPacketTo(receiver, packet);
        }
    }

    private static boolean canReceiveProximity(
            VoicechatConnection sender, VoicechatConnection receiver) {
        if (receiver == null || !receiver.isConnected()
                || receiver.getPlayer() == null) {
            return false;
        }
        Group receiverGroup = receiver.getGroup();
        Group senderGroup = sender == null ? null : sender.getGroup();
        return SpectateVoiceRouting.canReceiveProximity(
                receiver.isConnected(),
                receiver.isDisabled(),
                receiver.isInstalled(),
                senderGroup == null ? null : senderGroup.getId(),
                receiverGroup == null ? null : receiverGroup.getId(),
                receiverGroup != null && Group.Type.ISOLATED.equals(receiverGroup.getType()));
    }

    private static UUID playerId(VoicechatConnection connection) {
        return connection == null || connection.getPlayer() == null
                ? null
                : connection.getPlayer().getUuid();
    }
}
