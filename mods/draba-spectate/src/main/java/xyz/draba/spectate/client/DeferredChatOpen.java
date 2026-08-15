package xyz.draba.spectate.client;

import net.minecraft.client.gui.components.ChatComponent;

final class DeferredChatOpen {
    private ChatComponent.ChatMethod pending;

    void request(ChatComponent.ChatMethod method) {
        pending = method;
    }

    ChatComponent.ChatMethod consume() {
        ChatComponent.ChatMethod requested = pending;
        pending = null;
        return requested;
    }

    void clear() {
        pending = null;
    }
}
