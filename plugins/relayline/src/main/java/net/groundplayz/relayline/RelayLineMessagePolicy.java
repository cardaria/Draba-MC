package net.groundplayz.relayline;

import java.util.Locale;
import java.util.Set;

final class RelayLineMessagePolicy {
    private RelayLineMessagePolicy() {
    }

    static boolean isLocalOnly(String message, Set<String> normalizedPrefixes) {
        if (message == null || normalizedPrefixes.isEmpty()) {
            return false;
        }
        String normalizedMessage = message.stripLeading().toLowerCase(Locale.ROOT);
        return normalizedPrefixes.stream().anyMatch(normalizedMessage::startsWith);
    }
}
