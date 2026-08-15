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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class GuideBook {
    private static final int CONTENTS_PAGE = 2;

    private static final List<GuidePage> CONTENT_PAGES = List.of(
            page("start", "Start Here",
                    paragraph("This handbook covers our server rules and the client tools AutoModpack installs for you."),
                    paragraph("Read Keybind Setup first. Some useful actions are unbound or overlap by default.")),
            page(null, "Using This Guide",
                    paragraph("Use the page arrows to read normally."),
                    tip("Click Contents at the top of any page to jump between sections.")),
            page(null, "Managed Modpack",
                    paragraph("AutoModpack syncs required files. Draba Resources keeps animations and connected textures active automatically."),
                    warning("Use only the official managed mods. Extra client mods are not allowed.")),
            page(null, "Two Servers",
                    key("survival.example.com", "Survival on Minecraft 26.1.2"),
                    key("hardcore.example.com", "Hardcore on Minecraft 26.1.2"),
                    tip("Esc shows compact live counts for both servers.")),
            page(null, "Hardcore Cooldown",
                    paragraph("A Hardcore death starts a 7-day cooldown. The join screen shows your exact return time."),
                    paragraph("You cannot free-fly while waiting, but you may watch living players.")),
            page(null, "Warband on Hardcore",
                    paragraph("Warband makes Hardcore mobs coordinate and use smarter combat tactics."),
                    warning("Some can breach ordinary walls. Reinforced blocks and containers stay protected.")),
            page(null, "Spectating",
                    key("Esc > Spectate", "Watch an online player"),
                    paragraph("Stand still for 5 seconds; damage or danger cancels."),
                    tip("Returns here unprotected. Arrows switch; T chats; Esc returns.")),
            page(null, "Shared Network Chat",
                    paragraph("Chat is shared. Labels show which server sent each message."),
                    key("/local", "Current server only"),
                    key("/global", "Resume shared chat")),
            page(null, "Explore Your Mods",
                    paragraph("Main Menu > Mods shows what is installed. Many entries include a Config screen."),
                    tip("After an update, fully restart Minecraft. Reconnecting may not reload client resources.")),
            page(null, "Set Render Distance",
                    key("12 chunks", "Recommended render distance"),
                    paragraph("Set this early. Voxy handles the distant view, so a higher vanilla distance is unnecessary.")),
            page("keys", "Essential Keybinds",
                    key("Y", "Xaero Minimap settings"),
                    key("V", "Voice Chat menu")),
            page(null, "Hotbar Key Labels",
                    paragraph("Your current Hotbar Slot 1–9 keys appear over the hotbar and inventory slots."),
                    tip("Change them under Options > Controls. The labels update automatically.")),
            page(null, "Shoulder Camera",
                    key("F5", "Cycle to the shoulder view"),
                    key("Left Alt", "Hold for free look"),
                    warning("Swap Shoulder defaults to U, which conflicts with Xaero waypoints. Rebind it under Controls.")),
            page(null, "Resolve Conflicts",
                    key("M", "World Map — Survival only"),
                    warning("On Survival, Mute Microphone also defaults to M. Move it to a free key.")),
            page(null, "Voice Keybinds",
                    key("N", "Disable Voice Chat"),
                    key("V", "Voice Chat menu"),
                    tip("On Survival, resolve the red M conflict before relying on either feature.")),
            page(null, "Unbound Controls",
                    key("Push to Talk", "Bind this if you prefer PTT voice.")),
            page(null, "Finding Controls",
                    key("AutoCrop", "Its mode-cycle action is unbound."),
                    tip("Open Options > Controls > Key Binds and use its search box.")),
            page("maps", "Xaero Minimap",
                    key("Y", "Minimap settings"),
                    key("U", "Waypoint menu"),
                    key("B", "Create waypoint"),
                    key("Z", "Enlarge minimap"),
                    key("M", "World Map — Survival only")),
            page(null, "Maps & Waypoints",
                    paragraph("Hardcore includes the minimap and waypoints, but not the full World Map."),
                    tip("Waypoints live on your client. Back them up before moving Minecraft instances.")),
            page(null, "Proximity Voice",
                    key("V", "Open the Voice Chat menu."),
                    paragraph("Complete microphone and speaker setup on first use. Nearby players hear you by distance.")),
            page(null, "Voice Options",
                    paragraph("Private groups are available from the Voice Chat menu."),
                    tip("You can bind Push to Talk. Keep M free for the World Map on Survival.")),
            page("elytra", "Elytra Flight Rules",
                    paragraph("Elytra gliding is allowed in every dimension."),
                    warning("Firework boosting is disabled in the Overworld.")),
            page(null, "Rockets by Dimension",
                    key("Overworld", "Gliding only; no rocket boost"),
                    key("Nether", "Firework boosting allowed"),
                    key("End", "Firework boosting allowed")),
            page(null, "Overworld Launches",
                    paragraph("Use height, terrain, or another natural launch. Preserve momentum while gliding."),
                    tip("Plan a safe landing before taking off.")),
            page(null, "Elytra Care",
                    paragraph("Check durability before long trips and carry phantom membranes."),
                    tip("Material repairs use the server's fixed repair cost.")),
            page("enchanting", "Custom Enchantments",
                    paragraph("NeoEnchant adds server enchantments. Enchantment Insights explains them on books and gear."),
                    paragraph("Tooltips show current and maximum levels. Hover before applying an enchantment.")),
            page(null, "Missing Descriptions",
                    paragraph("Descriptions are supplied through the managed client pack."),
                    tip("If one is missing, let AutoModpack update and fully restart Minecraft.")),
            page(null, "Anvils & Repairs",
                    key("5 levels", "Material repair cost"),
                    paragraph("High-cost anvil work is allowed. Vanilla's Too Expensive block is removed.")),
            page(null, "Repair Materials",
                    paragraph("Tridents, bows, shears, elytra, and other special gear can use their configured repair materials."),
                    tip("The material repair price stays fixed at 5 levels.")),
            page(null, "Extract Enchantments",
                    key("2 levels", "Extraction cost"),
                    paragraph("Put undamaged enchanted gear on the left and a normal book on the right.")),
            page(null, "Extraction Warning",
                    warning("Extraction consumes the original gear."),
                    paragraph("The enchanted book is the output.")),
            page("inventory", "JEI Recipes",
                    key("R", "Recipes for hovered item"),
                    key("U", "Uses for hovered item"),
                    key("A", "Add or remove bookmark"),
                    key("Ctrl+F", "Focus JEI search")),
            page(null, "Using JEI",
                    paragraph("JEI shows searchable items beside inventory and recipe screens."),
                    tip("Change these keys under the JEI control categories.")),
            page(null, "Inventory & Storage",
                    paragraph("Inventory Sorter adds sorting controls to inventories and containers."),
                    paragraph("Mouse Tweaks improves click-drag and scroll movement of item stacks.")),
            page(null, "Shulker Previews",
                    paragraph("Shulker Box Tooltip previews container contents while you hover it."),
                    tip("Follow the key hint shown in the tooltip.")),
            page("world", "World Convenience",
                    paragraph("Tree Harvester: hold an axe, sneak, and break a tree to fell it."),
                    paragraph("It spends durability per log and replants a sapling when possible.")),
            page(null, "AutoCrop",
                    paragraph("AutoCrop defaults to Manual. Break mature crops normally and it replants them."),
                    tip("Its mode key is unbound. Configure other modes through Mods.")),
            page(null, "Visuals & Performance",
                    paragraph("Sodium, ImmediatelyFast, Entity Culling, ModernFix, and other helpers work automatically."),
                    tip("Keep normal render distance at the recommended 12 chunks.")),
            page(null, "Distant Terrain",
                    paragraph("The server passively generates and stores Voxy terrain up to 64 chunks around active players."),
                    paragraph("VoxyServer streams available LODs up to 128 chunks away.")),
            page(null, "Optional Visuals",
                    paragraph("Iris provides optional shaders. Fresh Animations and connected textures enhance the client."),
                    paragraph("Smoke and foliage effects add more detail.")),
            page(null, "Tuning Visuals",
                    paragraph("Adjust expensive effects through Video Settings or the Mods menu."),
                    warning("Change settings; do not delete managed files.")),
            page(null, "Sound & Atmosphere",
                    paragraph("Sound Physics, Ambient Sounds, and Presence Footsteps enrich the world."),
                    paragraph("These client-side effects are configurable from the Mods menu.")),
            page(null, "Adjusting Sound",
                    paragraph("Each atmosphere mod has its own settings."),
                    tip("If an effect distracts you, adjust its config instead of removing managed files.")),
            page("commands", "Server Commands",
                    key("/guide", "Open this handbook"),
                    key("/help", "Command summary"),
                    key("/changelog", "Latest server updates"),
                    key("/todo", "Private saved task list")),
            page(null, "More Commands",
                    key("/msg", "Private message"),
                    key("/watch", "Spectate, when permitted")),
            page(null, "Command Discovery",
                    paragraph("Press Tab while typing to discover available command arguments."),
                    tip("Use /help for a clean command summary.")),
            page(null, "Your Todo List",
                    paragraph("Use /todo to open your private task book. Tasks are saved to your UUID on the server."),
                    tip("Select a task to complete, pin, edit, or delete it.")),
            page(null, "Todo Commands",
                    key("/todo add", "Create a task"),
                    key("/todo edit", "Change its text"),
                    key("/todo done", "Complete or reopen"),
                    key("/todo pin", "Pin or unpin"),
                    key("/todo delete", "Delete with confirmation")),
            page(null, "Watching Players",
                    key("/watch <player>", "Begin spectating"),
                    key("/watch stop", "Return to your prior state"),
                    paragraph("This command is permission-controlled and uses the same 5-second safety countdown.")),
            page(null, "Watch Privacy",
                    paragraph("The watched player sees an anonymous spectator count, but not your identity."),
                    tip("Use /watch stop when you are finished.")),
            page(null, "Troubleshooting",
                    paragraph("First, let AutoModpack finish. Then fully restart Minecraft."),
                    paragraph("For controls, search Key Binds and clear conflicts.")),
            page(null, "Check Configuration",
                    paragraph("For a feature, open Main Menu > Mods and inspect its Config screen."),
                    tip("Use /changelog to review recent server changes.")),
            page(null, "Getting Help",
                    paragraph("Still stuck? Tell an admin what you tried and what you expected."),
                    paragraph("Include a screenshot, affected mod or item, and any visible error.")),
            page(null, "Useful Details",
                    paragraph("Mention whether it happens before joining or only on the server."),
                    tip("Specific details make client problems much faster to diagnose."))
    );

    private static final List<TocEntry> CONTENTS_ONE = List.of(
            new TocEntry("start", "Start Here"),
            new TocEntry("keys", "Keybind Setup"),
            new TocEntry("maps", "Maps & Voice"),
            new TocEntry("elytra", "Elytra Rules")
    );

    private static final List<TocEntry> CONTENTS_TWO = List.of(
            new TocEntry("enchanting", "Enchanting & Anvils"),
            new TocEntry("inventory", "Recipes & Inventory"),
            new TocEntry("world", "World & Client Tools"),
            new TocEntry("commands", "Commands & Support")
    );

    private GuideBook() {
    }

    static ItemStack create() {
        List<Component> pages = createPages();
        List<Filterable<Component>> filteredPages = pages.stream()
                .map(Filterable::passThrough)
                .toList();

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough(DrabaWelcome.isHardcoreServer() ? "Draba HC Guide" : "Draba X Guide"),
                "Draba Network",
                0,
                filteredPages,
                true));
        return book;
    }

    static List<Component> createPages() {
        Map<String, Integer> sectionPages = new LinkedHashMap<>();
        for (int index = 0; index < CONTENT_PAGES.size(); index++) {
            String sectionId = CONTENT_PAGES.get(index).sectionId();
            if (sectionId != null) {
                sectionPages.put(sectionId, index + 4);
            }
        }

        java.util.ArrayList<Component> pages = new java.util.ArrayList<>();
        pages.add(coverPage());
        pages.add(contentsPage("Contents  •  1/2", CONTENTS_ONE, sectionPages, "More sections  →", 3));
        pages.add(contentsPage("Contents  •  2/2", CONTENTS_TWO, sectionPages, "←  Previous", 2));
        CONTENT_PAGES.stream().map(GuideBook::contentPage).forEach(pages::add);
        return List.copyOf(pages);
    }

    private static Component coverPage() {
        return Component.empty()
                .append(Component.literal(DrabaWelcome.isHardcoreServer()
                                ? "✦  HC DRABA X SMP  ✦"
                                : "✦  DRABA X SMP  ✦")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal("\n\nPLAYER HANDBOOK")
                        .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD))
                .append(Component.literal("\nEdition 26.1.2").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("\n\nEverything worth knowing about this server, its rules, and your installed client tools.")
                        .withStyle(ChatFormatting.BLACK))
                .append(Component.literal("\n\n"))
                .append(pageLink("Open Contents  →", CONTENTS_PAGE));
    }

    private static Component contentsPage(String title, List<TocEntry> entries,
                                          Map<String, Integer> sectionPages,
                                          String navigationLabel, int navigationPage) {
        MutableComponent page = Component.empty()
                .append(Component.literal(title).withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD))
                .append(Component.literal("\n\n"));

        for (int index = 0; index < entries.size(); index++) {
            TocEntry entry = entries.get(index);
            page.append(Component.literal((index + 1) + "  ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    .append(pageLink(entry.label(), sectionPages.get(entry.sectionId())));
            if (index + 1 < entries.size()) {
                page.append(Component.literal("\n\n"));
            }
        }

        return page.append(Component.literal("\n\n"))
                .append(pageLink(navigationLabel, navigationPage));
    }

    private static Component contentPage(GuidePage guidePage) {
        MutableComponent page = Component.empty()
                .append(pageLink("‹  Contents", CONTENTS_PAGE))
                .append(Component.literal("\n"))
                .append(Component.literal(guidePage.title()).withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD))
                .append(Component.literal("\n\n"));

        for (int index = 0; index < guidePage.blocks().size(); index++) {
            GuideBlock currentBlock = guidePage.blocks().get(index);
            page.append(renderBlock(currentBlock));
            if (index + 1 < guidePage.blocks().size()) {
                GuideBlock nextBlock = guidePage.blocks().get(index + 1);
                String spacing = currentBlock.kind() == BlockKind.KEY && nextBlock.kind() == BlockKind.KEY
                        ? "\n"
                        : "\n\n";
                page.append(Component.literal(spacing));
            }
        }
        return page;
    }

    private static Component renderBlock(GuideBlock block) {
        return switch (block.kind()) {
            case PARAGRAPH -> Component.literal(block.text()).withStyle(ChatFormatting.BLACK);
            case KEY -> Component.empty()
                    .append(Component.literal(block.lead()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    .append(Component.literal(" — " + block.text()).withStyle(ChatFormatting.BLACK));
            case TIP -> Component.empty()
                    .append(Component.literal("TIP  ").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD))
                    .append(Component.literal(block.text()).withStyle(ChatFormatting.BLACK, ChatFormatting.ITALIC));
            case WARNING -> Component.empty()
                    .append(Component.literal("IMPORTANT  ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                    .append(Component.literal(block.text()).withStyle(ChatFormatting.BLACK));
        };
    }

    private static MutableComponent pageLink(String text, int page) {
        return Component.literal(text).withStyle(style -> style
                .withColor(ChatFormatting.DARK_GREEN)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.ChangePage(page)));
    }

    private static GuidePage page(String sectionId, String title, GuideBlock... blocks) {
        return new GuidePage(sectionId, title, List.of(blocks));
    }

    private static GuideBlock paragraph(String text) {
        return new GuideBlock(BlockKind.PARAGRAPH, "", text);
    }

    private static GuideBlock key(String key, String description) {
        return new GuideBlock(BlockKind.KEY, key, description);
    }

    private static GuideBlock tip(String text) {
        return new GuideBlock(BlockKind.TIP, "", text);
    }

    private static GuideBlock warning(String text) {
        return new GuideBlock(BlockKind.WARNING, "", text);
    }

    private enum BlockKind {
        PARAGRAPH,
        KEY,
        TIP,
        WARNING
    }

    private record GuideBlock(BlockKind kind, String lead, String text) {
    }

    private record GuidePage(String sectionId, String title, List<GuideBlock> blocks) {
    }

    private record TocEntry(String sectionId, String label) {
    }
}
