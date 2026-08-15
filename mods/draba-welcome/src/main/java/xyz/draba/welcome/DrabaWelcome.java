package xyz.draba.welcome;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DrabaWelcome implements ModInitializer {
    public static final String MOD_ID = "draba_welcome";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final String CHANGELOG_FILE_NAME = "draba-changelog.json";
    private static final String TODO_FILE_NAME = "draba-todos.json";
    private static final String DEFAULT_CHANGELOG_RESOURCE = "/default-changelog.json";
    private static final String BOOK_TITLE = "Draba Changelog";
    private static final long HARDCORE_PROMOTION_DELAY_TICKS = 5L * 20L;
    private static final Map<UUID, Long> PENDING_HARDCORE_PROMOTIONS = new HashMap<>();
    private static Path changelogPath;
    private static TodoStore todoStore;

    @Override
    public void onInitialize() {
        changelogPath = FabricLoader.getInstance().getConfigDir().resolve(CHANGELOG_FILE_NAME);
        ensureDefaultChangelog();
        try {
            todoStore = new TodoStore(FabricLoader.getInstance().getConfigDir().resolve(TODO_FILE_NAME));
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Failed to load private todo data", exception);
        }
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            welcome(handler.player);
            scheduleHardcorePromotion(server, handler.player);
        });
        ServerTickEvents.END_SERVER_TICK.register(DrabaWelcome::sendScheduledHardcorePromotions);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
        LOGGER.info("Draba Welcome initialized with /help, /guide, /todo, and /changelog");
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("help")
                .executes(context -> showHelp(context.getSource())));
        dispatcher.register(Commands.literal("changelog")
                .executes(context -> showChangelog(context.getSource())));
        dispatcher.register(Commands.literal("guide")
                .executes(context -> showGuide(context.getSource())));
        dispatcher.register(Commands.literal("todo")
                .executes(context -> showTodo(context.getSource()))
                .then(Commands.literal("list")
                        .executes(context -> showTodo(context.getSource())))
                .then(Commands.literal("add")
                        .executes(context -> openAddTodoDialog(context.getSource()))
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(context -> addTodo(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "text")))))
                .then(Commands.literal("edit")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(context -> openEditTodoDialog(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "id")))
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(context -> editTodo(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "id"),
                                                StringArgumentType.getString(context, "text"))))))
                .then(Commands.literal("done")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(context -> toggleTodoDone(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "id")))))
                .then(Commands.literal("pin")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(context -> toggleTodoPinned(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "id")))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(context -> confirmTodoDeletion(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "id")))
                                .then(Commands.literal("confirm")
                                        .executes(context -> deleteTodo(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "id")))))));
    }

    private static void welcome(ServerPlayer player) {
        player.sendSystemMessage(Component.empty());
        player.sendSystemMessage(brandTitle(""));
        player.sendSystemMessage(
                Component.literal("Invite Only").withStyle(ChatFormatting.RED)
                        .append(Component.literal("  •  Welcome, ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(player.getGameProfile().name()).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
        player.sendSystemMessage(
                Component.literal(isHardcoreServer()
                                ? "High-stakes Hardcore. Death means a 7-day cooldown."
                                : "A private survival world for good company and new stories.")
                        .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.empty());
        player.sendSystemMessage(sectionHeading("QUICK LINKS"));
        player.sendSystemMessage(commandRow("/help", "Server guide and commands."));
        player.sendSystemMessage(commandRow("/guide", "Player handbook and mod guide."));
        player.sendSystemMessage(commandRow("/todo", "Your private saved task list."));
        sendTodayUpdates(player);
        player.sendSystemMessage(Component.empty());
    }

    private static void sendTodayUpdates(ServerPlayer player) {
        try {
            String today = LocalDate.now().toString();
            long updateCount = loadChangelog().stream()
                    .filter(entry -> entry.date().equals(today))
                    .count();
            String updateLabel = updateCount == 1 ? " update today" : " updates today";

            player.sendSystemMessage(
                    Component.empty()
                            .append(Component.literal("› ").withStyle(ChatFormatting.DARK_GRAY))
                            .append(Component.literal("/changelog").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                            .append(Component.literal(" — ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(Long.toString(updateCount))
                                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                            .append(Component.literal(updateLabel + ".").withStyle(ChatFormatting.GRAY)));
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Failed to count today's changelog entries from {}", changelogPath, exception);
        }
    }

    private static void scheduleHardcorePromotion(MinecraftServer server, ServerPlayer player) {
        if (isHardcoreServer()) {
            return;
        }
        long dueAt = server.overworld().getGameTime() + HARDCORE_PROMOTION_DELAY_TICKS;
        PENDING_HARDCORE_PROMOTIONS.put(player.getUUID(), dueAt);
    }

    private static void sendScheduledHardcorePromotions(MinecraftServer server) {
        if (isHardcoreServer()) {
            PENDING_HARDCORE_PROMOTIONS.clear();
            return;
        }

        long now = server.overworld().getGameTime();
        Iterator<Map.Entry<UUID, Long>> iterator = PENDING_HARDCORE_PROMOTIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> promotion = iterator.next();
            if (now < promotion.getValue()) {
                continue;
            }

            iterator.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(promotion.getKey());
            if (player != null) {
                sendHardcorePromotion(player);
            }
        }
    }

    private static void sendHardcorePromotion(ServerPlayer player) {
        player.sendSystemMessage(Component.empty());
        player.sendSystemMessage(
                Component.literal("✦ ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("HARDCORE").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                        .append(Component.literal("  •  ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.literal("hardcore.example.com").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                        .append(Component.literal(" ✦").withStyle(ChatFormatting.GOLD)));
        player.sendSystemMessage(Component.literal(
                        "A separate, permanent Hardcore world with a 7-day death cooldown.")
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal(
                        "Enemies fight in coordinated groups, grow with your progress, and can breach ordinary defenses.")
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(
                Component.literal("Chat stays shared with Survival; ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("[HC]").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                        .append(Component.literal(" marks Hardcore messages. Use ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("/local").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                        .append(Component.literal(" or ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("/global").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                        .append(Component.literal(" anytime.").withStyle(ChatFormatting.GRAY)));
        player.sendSystemMessage(Component.empty());
    }

    private static int showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.empty(), false);
        source.sendSuccess(() -> brandTitle(" Server Guide"), false);
        source.sendSuccess(() -> Component.empty(), false);
        source.sendSuccess(() -> sectionHeading("ESSENTIALS"), false);
        sendCommandHelp(source, "/help", "Show this server guide.");
        sendCommandHelp(source, "/guide", "Open the player handbook and mod guide.");
        sendCommandHelp(source, "/changelog", "Browse the latest server updates.");
        sendCommandHelp(source, "/todo", "Open your private saved task list.");
        source.sendSuccess(() -> Component.empty(), false);
        source.sendSuccess(() -> sectionHeading("PLAYER TOOLS"), false);
        sendCommandHelp(source, "/watch <player>", "Spectate another player anonymously.");
        sendCommandHelp(source, "/watch stop", "Return to your previous location and game mode.");
        sendCommandHelp(source, "/msg <player> <message>", "Send a private message.");
        if (isHardcoreServer()) {
            source.sendSuccess(() -> Component.empty(), false);
            source.sendSuccess(() -> sectionHeading("HARDCORE"), false);
            sendCommandHelp(source, "/hc status", "Show your death cooldown status.");
            source.sendSuccess(() -> Component.literal("Death begins a 7-day cooldown; waiting players may watch living players.")
                    .withStyle(ChatFormatting.DARK_RED), false);
        }
        source.sendSuccess(() -> Component.empty(), false);
        source.sendSuccess(() -> Component.literal("Tip: press Tab while typing a command to see available options.")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false);
        source.sendSuccess(() -> Component.empty(), false);
        return 1;
    }

    private static void sendCommandHelp(CommandSourceStack source, String command, String description) {
        source.sendSuccess(() -> commandRow(command, description), false);
    }

    private static Component brandTitle(String suffix) {
        MutableComponent title = Component.empty()
                .append(Component.literal("✦ ").withStyle(ChatFormatting.GOLD));
        if (isHardcoreServer()) {
            title.append(Component.literal("HC ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        }
        return title
                .append(Component.literal("Draba ").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Component.literal("X").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD))
                .append(Component.literal(" SMP" + suffix).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Component.literal(" ✦").withStyle(ChatFormatting.GOLD));
    }

    static boolean isHardcoreServer() {
        return FabricLoader.getInstance().isModLoaded("draba_hardcore");
    }

    private static Component sectionHeading(String title) {
        return Component.literal(title).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    private static Component commandRow(String command, String description) {
        return Component.empty()
                .append(Component.literal("› ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(command).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal(" — " + description).withStyle(ChatFormatting.GRAY));
    }

    private static int showChangelog(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        try {
            List<ChangelogEntry> entries = loadChangelog();
            if (entries.isEmpty()) {
                source.sendFailure(Component.literal("The changelog is currently empty."));
                return 0;
            }

            openTemporaryBook(player, createChangelogBook(entries));
            return 1;
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Failed to open changelog from {}", changelogPath, exception);
            source.sendFailure(Component.literal("The changelog could not be opened. Please notify an administrator."));
            return 0;
        }
    }

    private static int showGuide(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        openTemporaryBook(player, GuideBook.create());
        return 1;
    }

    private static int showTodo(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        openTemporaryBook(player, TodoBook.create(
                player.getGameProfile().name(),
                todoStore.list(player.getUUID())));
        return 1;
    }

    private static int addTodo(CommandSourceStack source, String text) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        try {
            TodoStore.TodoTask task = todoStore.add(player.getUUID(), text);
            player.sendSystemMessage(todoSuccess("Added task #" + task.id() + "."));
            return showTodo(source);
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        } catch (IOException exception) {
            return todoStorageFailure(source, exception);
        }
    }

    private static int openAddTodoDialog(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TodoDialog.openAdd(player);
        return 1;
    }

    private static int openEditTodoDialog(CommandSourceStack source, int id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TodoStore.TodoTask task = todoStore.list(player.getUUID()).stream()
                .filter(candidate -> candidate.id() == id)
                .findFirst()
                .orElse(null);
        if (task == null) {
            source.sendFailure(Component.literal("Task #" + id + " does not exist."));
            return 0;
        }
        TodoDialog.openEdit(player, task);
        return 1;
    }

    private static int editTodo(CommandSourceStack source, int id, String text) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        try {
            todoStore.edit(player.getUUID(), id, text);
            player.sendSystemMessage(todoSuccess("Updated task #" + id + "."));
            return showTodo(source);
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        } catch (IOException exception) {
            return todoStorageFailure(source, exception);
        }
    }

    private static int toggleTodoDone(CommandSourceStack source, int id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        try {
            TodoStore.TodoTask task = todoStore.toggleDone(player.getUUID(), id);
            player.sendSystemMessage(todoSuccess(
                    task.done() ? "Completed task #" + id + "." : "Reopened task #" + id + "."));
            return showTodo(source);
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        } catch (IOException exception) {
            return todoStorageFailure(source, exception);
        }
    }

    private static int toggleTodoPinned(CommandSourceStack source, int id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        try {
            TodoStore.TodoTask task = todoStore.togglePinned(player.getUUID(), id);
            player.sendSystemMessage(todoSuccess(
                    task.pinned() ? "Pinned task #" + id + "." : "Unpinned task #" + id + "."));
            return showTodo(source);
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        } catch (IOException exception) {
            return todoStorageFailure(source, exception);
        }
    }

    private static int confirmTodoDeletion(CommandSourceStack source, int id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean exists = todoStore.list(player.getUUID()).stream().anyMatch(task -> task.id() == id);
        if (!exists) {
            source.sendFailure(Component.literal("Task #" + id + " does not exist."));
            return 0;
        }

        player.sendSystemMessage(Component.empty()
                .append(Component.literal("Delete task #" + id + "? ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("[Confirm deletion]").withStyle(style -> style
                        .withColor(ChatFormatting.RED)
                        .withBold(true)
                        .withClickEvent(new ClickEvent.RunCommand("/todo delete " + id + " confirm")))));
        return 1;
    }

    private static int deleteTodo(CommandSourceStack source, int id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        try {
            if (!todoStore.delete(player.getUUID(), id)) {
                source.sendFailure(Component.literal("Task #" + id + " does not exist."));
                return 0;
            }
            player.sendSystemMessage(todoSuccess("Deleted task #" + id + "."));
            return showTodo(source);
        } catch (IOException exception) {
            return todoStorageFailure(source, exception);
        }
    }

    private static Component todoSuccess(String message) {
        return Component.empty()
                .append(Component.literal("✓ ").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD))
                .append(Component.literal(message).withStyle(ChatFormatting.GRAY));
    }

    private static int todoStorageFailure(CommandSourceStack source, IOException exception) {
        LOGGER.error("Failed to save private todo data", exception);
        source.sendFailure(Component.literal("Your todo list could not be saved. Please notify an administrator."));
        return 0;
    }

    private static ItemStack createChangelogBook(List<ChangelogEntry> entries) {
        List<Filterable<Component>> pages = entries.stream()
                .map(DrabaWelcome::createChangelogPage)
                .map(Filterable::passThrough)
                .toList();

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough(BOOK_TITLE),
                "Draba X SMP",
                0,
                pages,
                true));
        return book;
    }

    private static Component createChangelogPage(ChangelogEntry entry) {
        MutableComponent page = Component.empty()
                .append(Component.literal("#" + entry.number() + " ")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal(entry.title())
                        .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD))
                .append(Component.literal("\n" + entry.date())
                        .withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("\n\n"));

        for (int index = 0; index < entry.changes().size(); index++) {
            page.append(Component.literal("• ").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(entry.changes().get(index)).withStyle(ChatFormatting.BLACK));
            if (index + 1 < entry.changes().size()) {
                page.append(Component.literal("\n"));
            }
        }

        return page;
    }

    private static void openTemporaryBook(ServerPlayer player, ItemStack book) {
        InteractionHand hand = InteractionHand.MAIN_HAND;
        ItemStack original = player.getItemInHand(hand);

        try {
            player.setItemInHand(hand, book);
            player.inventoryMenu.broadcastChanges();
            player.openItemGui(book, hand);
        } finally {
            player.setItemInHand(hand, original);
            player.inventoryMenu.broadcastChanges();
        }
    }

    private static List<ChangelogEntry> loadChangelog() throws IOException {
        try (Reader reader = Files.newBufferedReader(changelogPath, StandardCharsets.UTF_8)) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            if (!rootElement.isJsonObject()) {
                throw new IllegalArgumentException("Changelog root must be a JSON object");
            }

            JsonArray entryArray = rootElement.getAsJsonObject().getAsJsonArray("entries");
            if (entryArray == null) {
                throw new IllegalArgumentException("Changelog must contain an entries array");
            }

            List<ChangelogEntry> entries = new ArrayList<>();
            Set<Integer> usedNumbers = new HashSet<>();

            for (JsonElement element : entryArray) {
                if (!element.isJsonObject()) {
                    throw new IllegalArgumentException("Every changelog entry must be a JSON object");
                }

                JsonObject object = element.getAsJsonObject();
                int number = object.get("number").getAsInt();
                if (number < 1 || !usedNumbers.add(number)) {
                    throw new IllegalArgumentException("Changelog numbers must be positive and unique");
                }

                String date = requiredString(object, "date");
                String title = requiredString(object, "title");
                JsonArray changesArray = object.getAsJsonArray("changes");
                if (changesArray == null || changesArray.isEmpty()) {
                    throw new IllegalArgumentException("Every changelog entry must contain changes");
                }

                List<String> changes = new ArrayList<>();
                for (JsonElement change : changesArray) {
                    String text = change.getAsString().strip();
                    if (!text.isEmpty()) {
                        changes.add(text);
                    }
                }
                if (changes.isEmpty()) {
                    throw new IllegalArgumentException("Every changelog entry must contain non-empty changes");
                }

                entries.add(new ChangelogEntry(number, date, title, List.copyOf(changes)));
            }

            entries.sort(Comparator.comparingInt(ChangelogEntry::number).reversed());
            return List.copyOf(entries);
        }
    }

    private static String requiredString(JsonObject object, String fieldName) {
        JsonElement element = object.get(fieldName);
        if (element == null) {
            throw new IllegalArgumentException("Missing changelog field: " + fieldName);
        }

        String value = element.getAsString().strip();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Empty changelog field: " + fieldName);
        }
        return value;
    }

    private static void ensureDefaultChangelog() {
        if (Files.exists(changelogPath)) {
            return;
        }

        try {
            Files.createDirectories(changelogPath.getParent());
            try (InputStream input = DrabaWelcome.class.getResourceAsStream(DEFAULT_CHANGELOG_RESOURCE)) {
                if (input == null) {
                    throw new IOException("Bundled default changelog is missing");
                }
                Files.copy(input, changelogPath);
            }
            LOGGER.info("Created default changelog at {}", changelogPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create default changelog", exception);
        }
    }

    private record ChangelogEntry(int number, String date, String title, List<String> changes) {
    }
}
