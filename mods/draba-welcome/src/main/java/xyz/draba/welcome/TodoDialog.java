package xyz.draba.welcome;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.action.CommandTemplate;
import net.minecraft.server.dialog.action.ParsedTemplate;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

final class TodoDialog {
    private TodoDialog() {
    }

    static void openAdd(ServerPlayer player) {
        player.openDialog(Holder.direct(createAdd()));
    }

    static void openEdit(ServerPlayer player, TodoStore.TodoTask task) {
        player.openDialog(Holder.direct(createEdit(task)));
    }

    static NoticeDialog createAdd() {
        return create("Add a task", "What would you like to remember?", "", "/todo add $(task)", "Add task");
    }

    static NoticeDialog createEdit(TodoStore.TodoTask task) {
        return create(
                "Edit task #" + task.id(),
                "Update the task text below.",
                task.text(),
                "/todo edit " + task.id() + " $(task)",
                "Save changes");
    }

    private static NoticeDialog create(String title, String message,
                                       String initialText, String commandTemplate, String actionLabel) {
        Input taskInput = new Input("task", new TextInput(
                310,
                Component.literal("Task").withStyle(ChatFormatting.GOLD),
                true,
                initialText,
                TodoStore.MAX_TEXT_LENGTH,
                Optional.empty()));

        CommonDialogData common = new CommonDialogData(
                Component.literal(title).withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD),
                Optional.empty(),
                true,
                false,
                DialogAction.CLOSE,
                List.of(new PlainMessage(Component.literal(message), 310)),
                List.of(taskInput));

        ParsedTemplate template = ParsedTemplate.CODEC
                .parse(JsonOps.INSTANCE, new JsonPrimitive(commandTemplate))
                .getOrThrow();
        ActionButton action = new ActionButton(
                new CommonButtonData(Component.literal(actionLabel), 150),
                Optional.of(new CommandTemplate(template)));

        return new NoticeDialog(common, action);
    }
}
