package xyz.draba.welcome;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class GuideBookAudit {
    private GuideBookAudit() {
    }

    public static void main(String[] args) {
        List<Component> pages = GuideBook.createPages();
        List<String> densityFailures = new ArrayList<>();

        for (int index = 0; index < pages.size(); index++) {
            Component page = pages.get(index);
            int pageNumber = index + 1;
            int textLength = page.getString().length();
            if (textLength > 300) {
                throw new IllegalStateException("Page " + pageNumber + " is too dense: " + textLength + " characters");
            }
            int estimatedLines = estimateLines(page.getString());
            if (estimatedLines > 14) {
                densityFailures.add("page " + pageNumber + ": " + estimatedLines + " lines");
            }

            List<Integer> targets = new ArrayList<>();
            for (Component part : page.toFlatList()) {
                if (part.getStyle().getClickEvent() instanceof ClickEvent.ChangePage changePage) {
                    int target = changePage.page();
                    if (target < 1 || target > pages.size()) {
                        throw new IllegalStateException("Page " + pageNumber + " links outside the book: " + target);
                    }
                    targets.add(target);
                }
            }

            if (pageNumber == 1 && !targets.equals(List.of(2))) {
                throw new IllegalStateException("Cover must link to Contents: " + targets);
            }
            if (pageNumber >= 4 && (targets.isEmpty() || targets.getFirst() != 2)) {
                throw new IllegalStateException("Topic page " + pageNumber + " lacks a Contents link: " + targets);
            }

            System.out.printf("page=%02d chars=%03d lines=%02d links=%s text=%s%n",
                    pageNumber,
                    textLength,
                    estimatedLines,
                    targets,
                    page.getString().replace('\n', ' '));
        }

        if (!densityFailures.isEmpty()) {
            throw new IllegalStateException("Pages may overflow: " + String.join(", ", densityFailures));
        }
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
}
