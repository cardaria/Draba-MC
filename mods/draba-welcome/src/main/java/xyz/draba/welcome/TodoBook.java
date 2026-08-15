package xyz.draba.welcome;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;

final class TodoBook {
    private static final int TASKS_PER_LIST_PAGE = 5;

    private TodoBook() {
    }

    static ItemStack create(String playerName, List<TodoStore.TodoTask> tasks) {
        List<Component> pages = createPages(tasks);
        List<Filterable<Component>> filteredPages = pages.stream()
                .map(Filterable::passThrough)
                .toList();

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("My Todo List"),
                playerName,
                0,
                filteredPages,
                true));
        return book;
    }

    static List<Component> createPages(List<TodoStore.TodoTask> tasks) {
        int listPageCount = Math.max(1, (tasks.size() + TASKS_PER_LIST_PAGE - 1) / TASKS_PER_LIST_PAGE);
        int firstDetailPage = 2 + listPageCount;
        List<Component> pages = new ArrayList<>();

        pages.add(coverPage(tasks));
        for (int listIndex = 0; listIndex < listPageCount; listIndex++) {
            pages.add(listPage(tasks, listIndex, listPageCount, firstDetailPage));
        }
        for (int taskIndex = 0; taskIndex < tasks.size(); taskIndex++) {
            int backPage = 2 + (taskIndex / TASKS_PER_LIST_PAGE);
            pages.add(detailPage(tasks.get(taskIndex), backPage));
        }
        return List.copyOf(pages);
    }

    private static Component coverPage(List<TodoStore.TodoTask> tasks) {
        long completed = tasks.stream().filter(TodoStore.TodoTask::done).count();
        MutableComponent page = Component.empty()
                .append(Component.literal("✦  MY TODO LIST  ✦")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal("\n\nPrivate and saved on the server.")
                        .withStyle(ChatFormatting.BLACK))
                .append(Component.literal("\n\n" + completed + " complete  •  "
                        + (tasks.size() - completed) + " open").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("\n\n"))
                .append(runLink("＋  Add a task", "/todo add"));

        if (!tasks.isEmpty()) {
            page.append(Component.literal("\n\n"))
                    .append(pageLink("View my tasks  →", 2));
        } else {
            page.append(Component.literal("\n\nNo tasks yet.").withStyle(ChatFormatting.GRAY));
        }
        return page;
    }

    private static Component listPage(List<TodoStore.TodoTask> tasks, int listIndex,
                                      int listPageCount, int firstDetailPage) {
        MutableComponent page = Component.empty()
                .append(Component.literal("MY TASKS  •  " + (listIndex + 1) + "/" + listPageCount)
                        .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD))
                .append(Component.literal("\n\n"));

        int fromIndex = listIndex * TASKS_PER_LIST_PAGE;
        int toIndex = Math.min(tasks.size(), fromIndex + TASKS_PER_LIST_PAGE);
        if (fromIndex == toIndex) {
            page.append(Component.literal("Your todo list is empty.").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("\n\n"))
                    .append(runLink("＋  Add a task", "/todo add"));
            return page;
        }

        for (int taskIndex = fromIndex; taskIndex < toIndex; taskIndex++) {
            TodoStore.TodoTask task = tasks.get(taskIndex);
            String marker = task.done() ? "✓" : "○";
            String pin = task.pinned() ? " ◆" : "";
            String preview = abbreviate(task.text(), 25);
            page.append(Component.literal(marker + " #" + task.id() + pin + " ")
                            .withStyle(task.done() ? ChatFormatting.DARK_GREEN : ChatFormatting.GOLD))
                    .append(pageLink(preview, firstDetailPage + taskIndex));
            if (taskIndex + 1 < toIndex) {
                page.append(Component.literal("\n\n"));
            }
        }
        return page;
    }

    private static Component detailPage(TodoStore.TodoTask task, int backPage) {
        String status = task.done() ? "COMPLETE" : "OPEN";
        String toggleLabel = task.done() ? "↺  Reopen" : "✓  Complete";
        String pinLabel = task.pinned() ? "◇  Unpin" : "◆  Pin";

        return Component.empty()
                .append(Component.literal("#" + task.id() + "  " + status)
                        .withStyle(task.done() ? ChatFormatting.DARK_GREEN : ChatFormatting.GOLD,
                                ChatFormatting.BOLD))
                .append(task.pinned()
                        ? Component.literal("  ◆").withStyle(ChatFormatting.GOLD)
                        : Component.empty())
                .append(Component.literal("\n\n" + task.text()).withStyle(ChatFormatting.BLACK))
                .append(Component.literal("\n\n"))
                .append(runLink(toggleLabel, "/todo done " + task.id()))
                .append(Component.literal("   "))
                .append(runLink(pinLabel, "/todo pin " + task.id()))
                .append(Component.literal("\n"))
                .append(runLink("✎  Edit", "/todo edit " + task.id()))
                .append(Component.literal("   "))
                .append(runLink("×  Delete", "/todo delete " + task.id()))
                .append(Component.literal("\n\n"))
                .append(pageLink("‹  Back to tasks", backPage));
    }

    private static MutableComponent pageLink(String text, int page) {
        return Component.literal(text).withStyle(style -> style
                .withColor(ChatFormatting.DARK_GREEN)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.ChangePage(page)));
    }

    private static MutableComponent runLink(String text, String command) {
        return Component.literal(text).withStyle(style -> style
                .withColor(ChatFormatting.DARK_GREEN)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand(command)));
    }

    private static String abbreviate(String text, int maximumLength) {
        if (text.length() <= maximumLength) {
            return text;
        }
        return text.substring(0, maximumLength - 1).stripTrailing() + "…";
    }
}
