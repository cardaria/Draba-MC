package xyz.draba.welcome;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class TodoStore {
    static final int MAX_TASKS = 72;
    static final int MAX_TEXT_LENGTH = 120;

    private static final int SCHEMA_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Comparator<TodoTask> DISPLAY_ORDER = Comparator
            .comparing(TodoTask::pinned).reversed()
            .thenComparing(TodoTask::done)
            .thenComparingInt(TodoTask::id);

    private final Path path;
    private final Map<UUID, PlayerTodos> players = new HashMap<>();

    TodoStore(Path path) throws IOException {
        this.path = path;
        if (Files.exists(path)) {
            load();
        } else {
            save();
        }
    }

    synchronized List<TodoTask> list(UUID playerId) {
        PlayerTodos todos = players.get(playerId);
        if (todos == null) {
            return List.of();
        }
        return todos.tasks.stream().sorted(DISPLAY_ORDER).toList();
    }

    synchronized TodoTask add(UUID playerId, String rawText) throws IOException {
        String text = validateText(rawText);
        PlayerTodos todos = players.computeIfAbsent(playerId, ignored -> new PlayerTodos());
        if (todos.tasks.size() >= MAX_TASKS) {
            throw new IllegalArgumentException("You can keep at most " + MAX_TASKS + " tasks.");
        }

        TodoTask task = new TodoTask(todos.nextId++, text, false, false, Instant.now().toString());
        todos.tasks.add(task);
        try {
            save();
        } catch (IOException exception) {
            todos.tasks.remove(task);
            todos.nextId--;
            if (todos.tasks.isEmpty()) {
                players.remove(playerId);
            }
            throw exception;
        }
        return task;
    }

    synchronized TodoTask edit(UUID playerId, int id, String rawText) throws IOException {
        String text = validateText(rawText);
        return replace(playerId, id, task -> new TodoTask(
                task.id(), text, task.done(), task.pinned(), task.createdAt()));
    }

    synchronized TodoTask toggleDone(UUID playerId, int id) throws IOException {
        return replace(playerId, id, task -> new TodoTask(
                task.id(), task.text(), !task.done(), task.pinned(), task.createdAt()));
    }

    synchronized TodoTask togglePinned(UUID playerId, int id) throws IOException {
        return replace(playerId, id, task -> new TodoTask(
                task.id(), task.text(), task.done(), !task.pinned(), task.createdAt()));
    }

    synchronized boolean delete(UUID playerId, int id) throws IOException {
        PlayerTodos todos = players.get(playerId);
        if (todos == null) {
            return false;
        }

        for (int index = 0; index < todos.tasks.size(); index++) {
            TodoTask task = todos.tasks.get(index);
            if (task.id() != id) {
                continue;
            }
            todos.tasks.remove(index);
            if (todos.tasks.isEmpty()) {
                players.remove(playerId);
            }
            try {
                save();
            } catch (IOException exception) {
                players.put(playerId, todos);
                todos.tasks.add(index, task);
                throw exception;
            }
            return true;
        }
        return false;
    }

    private TodoTask replace(UUID playerId, int id, TaskReplacement replacement) throws IOException {
        PlayerTodos todos = players.get(playerId);
        if (todos == null) {
            throw new IllegalArgumentException("Task #" + id + " does not exist.");
        }

        for (int index = 0; index < todos.tasks.size(); index++) {
            TodoTask current = todos.tasks.get(index);
            if (current.id() == id) {
                TodoTask updated = replacement.apply(current);
                todos.tasks.set(index, updated);
                try {
                    save();
                } catch (IOException exception) {
                    todos.tasks.set(index, current);
                    throw exception;
                }
                return updated;
            }
        }
        throw new IllegalArgumentException("Task #" + id + " does not exist.");
    }

    private void load() throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            if (!rootElement.isJsonObject()) {
                throw new IllegalArgumentException("Todo data root must be an object");
            }
            JsonObject root = rootElement.getAsJsonObject();
            int schemaVersion = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 0;
            if (schemaVersion != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported todo schema version: " + schemaVersion);
            }

            JsonObject playerObject = root.getAsJsonObject("players");
            if (playerObject == null) {
                throw new IllegalArgumentException("Todo data must contain a players object");
            }

            for (Map.Entry<String, JsonElement> playerEntry : playerObject.entrySet()) {
                UUID playerId = UUID.fromString(playerEntry.getKey());
                JsonObject serializedTodos = playerEntry.getValue().getAsJsonObject();
                JsonArray taskArray = serializedTodos.getAsJsonArray("tasks");
                if (taskArray == null || taskArray.size() > MAX_TASKS) {
                    throw new IllegalArgumentException("Invalid task list for " + playerId);
                }

                PlayerTodos todos = new PlayerTodos();
                Set<Integer> usedIds = new HashSet<>();
                int highestId = 0;
                for (JsonElement taskElement : taskArray) {
                    JsonObject taskObject = taskElement.getAsJsonObject();
                    int id = taskObject.get("id").getAsInt();
                    if (id < 1 || !usedIds.add(id)) {
                        throw new IllegalArgumentException("Invalid task id for " + playerId);
                    }
                    String text = validateText(taskObject.get("text").getAsString());
                    boolean done = taskObject.has("done") && taskObject.get("done").getAsBoolean();
                    boolean pinned = taskObject.has("pinned") && taskObject.get("pinned").getAsBoolean();
                    String createdAt = taskObject.has("createdAt")
                            ? taskObject.get("createdAt").getAsString()
                            : Instant.EPOCH.toString();
                    Instant.parse(createdAt);
                    todos.tasks.add(new TodoTask(id, text, done, pinned, createdAt));
                    highestId = Math.max(highestId, id);
                }

                int storedNextId = serializedTodos.has("nextId")
                        ? serializedTodos.get("nextId").getAsInt()
                        : highestId + 1;
                todos.nextId = Math.max(highestId + 1, storedNextId);
                if (!todos.tasks.isEmpty()) {
                    players.put(playerId, todos);
                }
            }
        }
    }

    private void save() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        JsonObject playerObject = new JsonObject();

        players.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    JsonObject serializedTodos = new JsonObject();
                    serializedTodos.addProperty("nextId", entry.getValue().nextId);
                    JsonArray taskArray = new JsonArray();
                    entry.getValue().tasks.stream()
                            .sorted(Comparator.comparingInt(TodoTask::id))
                            .forEach(task -> {
                                JsonObject taskObject = new JsonObject();
                                taskObject.addProperty("id", task.id());
                                taskObject.addProperty("text", task.text());
                                taskObject.addProperty("done", task.done());
                                taskObject.addProperty("pinned", task.pinned());
                                taskObject.addProperty("createdAt", task.createdAt());
                                taskArray.add(taskObject);
                            });
                    serializedTodos.add("tasks", taskArray);
                    playerObject.add(entry.getKey().toString(), serializedTodos);
                });
        root.add("players", playerObject);

        Files.createDirectories(path.getParent());
        Path temporaryPath = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporaryPath, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        try {
            Files.move(temporaryPath, path,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporaryPath, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String validateText(String rawText) {
        String text = rawText == null ? "" : rawText.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Task text cannot be empty.");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "Task text can contain at most " + MAX_TEXT_LENGTH + " characters.");
        }
        if (text.chars().anyMatch(character -> Character.isISOControl(character))) {
            throw new IllegalArgumentException("Task text cannot contain control characters.");
        }
        return text;
    }

    record TodoTask(int id, String text, boolean done, boolean pinned, String createdAt) {
    }

    private static final class PlayerTodos {
        private int nextId = 1;
        private final List<TodoTask> tasks = new ArrayList<>();
    }

    @FunctionalInterface
    private interface TaskReplacement {
        TodoTask apply(TodoTask task);
    }
}
