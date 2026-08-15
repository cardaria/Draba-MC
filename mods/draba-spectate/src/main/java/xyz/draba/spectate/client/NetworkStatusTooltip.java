package xyz.draba.spectate.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import java.util.List;

record NetworkStatusTooltip(List<PlayerLine> players, int columns) implements TooltipComponent {
    static final int ROW_HEIGHT = 10;
    static final int COLUMN_GAP = 12;
    static final int INNER_GAP = 6;
    static final int TOP_GAP = 5;
    static final int TABLE_HEADER_HEIGHT = 10;
    static final int FOOTER_GAP = 7;
    static final int FOOTER_HEIGHT = 9;
    private static final int MIN_TWO_COLUMN_SCREEN_WIDTH = 300;
    private static final int TOOLTIP_VERTICAL_RESERVE = 68;

    NetworkStatusTooltip {
        players = List.copyOf(players);
        columns = Math.max(1, Math.min(2, columns));
    }

    static int chooseColumns(
            int playerCount, int screenWidth, int screenHeight, int twoColumnWidth) {
        int availableRows = Math.max(1,
                (screenHeight - TOOLTIP_VERTICAL_RESERVE) / ROW_HEIGHT);
        boolean needsSecondColumn = playerCount > availableRows;
        boolean secondColumnFits = screenWidth >= MIN_TWO_COLUMN_SCREEN_WIDTH
                && twoColumnWidth <= screenWidth - 12;
        return needsSecondColumn && secondColumnFits ? 2 : 1;
    }

    int rows() {
        return (players.size() + columns - 1) / columns;
    }

    record PlayerLine(String name, String joinedAgo) {
    }

    static final class ClientComponent implements ClientTooltipComponent {
        private static final int NAME_COLOR = 0xFFFFFFFF;
        private static final int SECONDARY_COLOR = 0xFFAAAAAA;
        private static final int HEADER_COLOR = 0xFF777777;
        private static final int FOOTER_COLOR = 0xFF777777;
        private static final String FOOTER = "Click to refresh";

        private final NetworkStatusTooltip data;
        private final int[] nameWidths;
        private final int[] elapsedWidths;
        private final int[] columnWidths;

        ClientComponent(NetworkStatusTooltip data) {
            this.data = data;
            Font font = Minecraft.getInstance().font;
            nameWidths = new int[data.columns()];
            elapsedWidths = new int[data.columns()];
            columnWidths = new int[data.columns()];
            for (int column = 0; column < data.columns(); column++) {
                nameWidths[column] = font.width("Players");
                elapsedWidths[column] = font.width("Joined ago");
                for (int row = 0; row < data.rows(); row++) {
                    NetworkStatusTooltip.PlayerLine line = line(column, row);
                    if (line == null) {
                        continue;
                    }
                    nameWidths[column] = Math.max(nameWidths[column], font.width(line.name()));
                    elapsedWidths[column] = Math.max(
                            elapsedWidths[column], font.width(line.joinedAgo()));
                }
                columnWidths[column] = nameWidths[column] + INNER_GAP + elapsedWidths[column];
            }
        }

        @Override
        public int getHeight(Font font) {
            int tableHeight = data.players().isEmpty()
                    ? 0
                    : TABLE_HEADER_HEIGHT + data.rows() * ROW_HEIGHT;
            int footerGap = data.players().isEmpty() ? 0 : FOOTER_GAP;
            return TOP_GAP + tableHeight + footerGap + FOOTER_HEIGHT;
        }

        @Override
        public int getWidth(Font font) {
            int width = 0;
            for (int columnWidth : columnWidths) {
                width += columnWidth;
            }
            int tableWidth = width + COLUMN_GAP * (data.columns() - 1);
            return Math.max(tableWidth, font.width(FOOTER));
        }

        @Override
        public void extractImage(
                Font font, int tooltipX, int tooltipY, int width, int height,
                GuiGraphicsExtractor graphics) {
            int contentY = tooltipY + TOP_GAP;
            if (!data.players().isEmpty()) {
                int x = tooltipX;
                for (int column = 0; column < data.columns(); column++) {
                    graphics.text(font, Component.literal("Players"),
                            x, contentY, HEADER_COLOR);
                    graphics.text(font, Component.literal("Joined ago"),
                            x + nameWidths[column] + INNER_GAP, contentY, HEADER_COLOR);
                    for (int row = 0; row < data.rows(); row++) {
                        NetworkStatusTooltip.PlayerLine line = line(column, row);
                        if (line == null) {
                            continue;
                        }
                        int y = contentY + TABLE_HEADER_HEIGHT + row * ROW_HEIGHT;
                        graphics.text(font, Component.literal(line.name()), x, y, NAME_COLOR);
                        graphics.text(font, Component.literal(line.joinedAgo()),
                                x + nameWidths[column] + INNER_GAP, y, SECONDARY_COLOR);
                    }
                    x += columnWidths[column] + COLUMN_GAP;
                }
                contentY += TABLE_HEADER_HEIGHT + data.rows() * ROW_HEIGHT + FOOTER_GAP;
            }
            graphics.text(font, Component.literal(FOOTER), tooltipX, contentY, FOOTER_COLOR);
        }

        private NetworkStatusTooltip.PlayerLine line(int column, int row) {
            int index = column * data.rows() + row;
            return index < data.players().size() ? data.players().get(index) : null;
        }
    }
}
