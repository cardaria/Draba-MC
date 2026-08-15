package xyz.draba.welcome;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.NoticeDialog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TodoFeatureAudit {
    private TodoFeatureAudit() {
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Path temporaryDirectory = Files.createTempDirectory("draba-todo-audit-");
        Path dataPath = temporaryDirectory.resolve("todos.json");
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        try {
            TodoStore store = new TodoStore(dataPath);
            TodoStore.TodoTask first = store.add(playerId, "Build the storage room");
            TodoStore.TodoTask second = store.add(playerId, "Collect spruce logs");
            store.edit(playerId, second.id(), "Collect two stacks of spruce logs");
            store.toggleDone(playerId, first.id());
            store.togglePinned(playerId, second.id());

            TodoStore reloaded = new TodoStore(dataPath);
            List<TodoStore.TodoTask> persisted = reloaded.list(playerId);
            require(persisted.size() == 2, "Expected two persisted tasks");
            require(persisted.getFirst().id() == second.id() && persisted.getFirst().pinned(),
                    "Pinned task should sort first");
            require(persisted.getLast().id() == first.id() && persisted.getLast().done(),
                    "Completion state should persist");
            require(reloaded.delete(playerId, first.id()), "Existing task should delete");
            require(!reloaded.delete(playerId, first.id()), "Deleted task must stay absent");

            expectInvalid(() -> reloaded.add(playerId, "   "), "Empty text must be rejected");
            expectInvalid(() -> reloaded.add(playerId, "x".repeat(TodoStore.MAX_TEXT_LENGTH + 1)),
                    "Overlong text must be rejected");

            List<TodoStore.TodoTask> maximumTasks = new ArrayList<>();
            for (int id = 1; id <= TodoStore.MAX_TASKS; id++) {
                maximumTasks.add(new TodoStore.TodoTask(
                        id,
                        id == 1 ? "A".repeat(TodoStore.MAX_TEXT_LENGTH) : "Task " + id,
                        id % 3 == 0,
                        id % 5 == 0,
                        "2026-07-25T00:00:00Z"));
            }
            auditBook(TodoBook.createPages(maximumTasks));
            auditBook(TodoBook.createPages(List.of()));
            auditDialog(TodoDialog.createAdd(), "dynamic/run_command", "/todo add $(task)");
            auditDialog(TodoDialog.createEdit(maximumTasks.getFirst()),
                    "dynamic/run_command", "/todo edit 1 $(task)");
            System.out.println("Todo storage and book audit passed.");
        } finally {
            Files.deleteIfExists(dataPath.resolveSibling(dataPath.getFileName() + ".tmp"));
            Files.deleteIfExists(dataPath);
            Files.deleteIfExists(temporaryDirectory);
        }
    }

    private static void auditBook(List<Component> pages) {
        require(pages.size() <= 100, "Todo book exceeds Minecraft's page limit");
        for (int index = 0; index < pages.size(); index++) {
            Component page = pages.get(index);
            int estimatedLines = estimateLines(page.getString());
            require(estimatedLines <= 14,
                    "Todo page " + (index + 1) + " may overflow at " + estimatedLines + " lines: "
                            + page.getString().replace('\n', ' '));

            for (Component part : page.toFlatList()) {
                ClickEvent clickEvent = part.getStyle().getClickEvent();
                if (clickEvent instanceof ClickEvent.ChangePage changePage) {
                    require(changePage.page() >= 1 && changePage.page() <= pages.size(),
                            "Todo book has an invalid page target: " + changePage.page());
                }
                require(!(clickEvent instanceof ClickEvent.SuggestCommand),
                        "Written books cannot handle suggest_command actions in Minecraft 26.1.2");
            }
        }
    }

    private static void auditDialog(NoticeDialog dialog, String actionType, String commandTemplate) {
        JsonElement encoded = Dialog.DIRECT_CODEC
                .encodeStart(JsonOps.INSTANCE, dialog)
                .getOrThrow();
        String json = encoded.toString();
        require(json.contains(actionType), "Todo dialog lacks a dynamic command action: " + json);
        require(json.contains(commandTemplate), "Todo dialog has the wrong command template: " + json);
        Dialog.DIRECT_CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
    }

    private static int estimateLines(String text) {
        int lines = 0;
        for (String explicitLine : text.split("\\n", -1)) {
            if (explicitLine.isEmpty()) {
                lines++;
                continue;
            }
            int currentWidth = 0;
            for (String word : explicitLine.split(" ")) {
                int wordWidth = pixelWidth(word);
                int separatorWidth = currentWidth == 0 ? 0 : 4;
                if (wordWidth > 114) {
                    if (currentWidth > 0) {
                        lines++;
                        currentWidth = 0;
                    }
                    for (int characterIndex = 0; characterIndex < word.length(); characterIndex++) {
                        int characterWidth = pixelWidth(word.substring(characterIndex, characterIndex + 1));
                        if (currentWidth > 0 && currentWidth + characterWidth > 114) {
                            lines++;
                            currentWidth = 0;
                        }
                        currentWidth += characterWidth;
                    }
                    continue;
                }
                if (currentWidth > 0 && currentWidth + separatorWidth + wordWidth > 114) {
                    lines++;
                    currentWidth = wordWidth;
                } else {
                    currentWidth += separatorWidth + wordWidth;
                }
            }
            lines++;
        }
        return lines;
    }

    private static int pixelWidth(String text) {
        int width = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            width += switch (character) {
                case ' ', 'I', 'i', 'l', '.', ',', '\'', '!', '|', ':', ';' -> 3;
                case 't', 'f', 'r', '(', ')', '[', ']' -> 4;
                case 'm', 'w', 'M', 'W', '@', '✦' -> 7;
                default -> 6;
            };
        }
        return width;
    }

    private static void expectInvalid(ThrowingAction action, String message) throws Exception {
        try {
            action.run();
            throw new IllegalStateException(message);
        } catch (IllegalArgumentException expected) {
            // Expected validation failure.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}
