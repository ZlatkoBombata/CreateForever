package org.omega.createforever.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import org.omega.createforever.menus.SewingMachineMenu;
import org.omega.createforever.recipe.SewingRecipe;
import org.omega.createforever.util.ModTags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SewingMachineScreen extends AbstractContainerScreen<SewingMachineMenu> {
    private static final int LIST_X = 8;
    private static final int LIST_WIDTH = 136;
    private static final int PREVIEW_X = 151;
    private static final int PREVIEW_WIDTH = 82;
    private static final int RECIPE_X = 239;
    private static final int RECIPE_WIDTH = 123;
    private static final int ROW_HEIGHT = 18;
    private static final int LIST_TOP = 31;
    private static final int LIST_BOTTOM = 204;

    private final Map<TagKey<Item>, Boolean> expandedTags = new HashMap<>();
    private final Map<String, Component> ingredientNameCache = new HashMap<>();
    private final Set<Item> craftableHatSet;
    private final TagGroup rootGroup;
    private final RemotePlayer previewPlayer;
    private List<ListRow> visibleRows = List.of();
    private int scrollOffset;

    public SewingMachineScreen(SewingMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 370;
        imageHeight = 220;
        titleLabelY = 8;

        craftableHatSet = new HashSet<>(menu.getCraftableHats());
        rootGroup = createTagTree();
        expandTags(rootGroup);

        ClientLevel level = Minecraft.getInstance().level;
        previewPlayer = new RemotePlayer(level, Minecraft.getInstance().player.getGameProfile());
        previewPlayer.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        graphics.fill(left, top, left + imageWidth, top + imageHeight, 0xFF252321);
        graphics.fill(left + 4, top + 24, left + 144, top + 208, 0xFF171716);
        graphics.fill(left + 147, top + 24, left + 237, top + 208, 0xFF171716);
        graphics.fill(left + 240, top + 24, left + 366, top + 208, 0xFF171716);
        graphics.fill(left + 145, top + 24, left + 146, top + 208, 0xFF514D48);
        graphics.fill(left + 238, top + 24, left + 239, top + 208, 0xFF514D48);

        visibleRows = buildRows();
        renderHatList(graphics, mouseX, mouseY);
        renderPreview(graphics, mouseX, mouseY);
        renderRecipe(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, titleLabelY, 0xFFFFFF, false);
        graphics.drawCenteredString(font, Component.translatable("screen.createforever.sewing_machine.preview"),
                PREVIEW_X + PREVIEW_WIDTH / 2, 27, 0xD4C7AF);
        graphics.drawString(font, Component.translatable("screen.createforever.sewing_machine.ingredients"),
                RECIPE_X, 27, 0xD4C7AF);
    }

    private void renderHatList(GuiGraphics graphics, int mouseX, int mouseY) {
        int top = topPos + LIST_TOP;
        int bottom = topPos + LIST_BOTTOM;
        graphics.enableScissor(leftPos + LIST_X, top, leftPos + LIST_X + LIST_WIDTH, bottom);

        for (int rowIndex = scrollOffset; rowIndex < visibleRows.size(); rowIndex++) {
            int y = top + (rowIndex - scrollOffset) * ROW_HEIGHT;
            if (y + ROW_HEIGHT > bottom) {
                break;
            }

            ListRow row = visibleRows.get(rowIndex);
            int x = leftPos + LIST_X + 2 + row.depth * 10;
            boolean hovered = mouseX >= leftPos + LIST_X && mouseX < leftPos + LIST_X + LIST_WIDTH
                    && mouseY >= y && mouseY < y + ROW_HEIGHT;

            if (row.item != null && menu.getSelectedHat() == row.item) {
                graphics.fill(leftPos + LIST_X + 1, y, leftPos + LIST_X + LIST_WIDTH - 1, y + ROW_HEIGHT,
                        0xFF51493C);
            } else if (hovered) {
                graphics.fill(leftPos + LIST_X + 1, y, leftPos + LIST_X + LIST_WIDTH - 1, y + ROW_HEIGHT,
                        0xFF34312D);
            }

            if (row.item != null) {
                graphics.renderItem(new ItemStack(row.item), x, y + 1);
                graphics.drawString(font, fitText(row.name, LIST_WIDTH - (x - (leftPos + LIST_X)) - 24),
                        x + 20, y + 5, 0xE7E1D8, false);
            } else {
                boolean expanded = expandedTags.getOrDefault(row.tag, false);
                graphics.drawString(font, expanded ? "v" : ">", x, y + 4, 0xD4C7AF, false);
                graphics.drawString(font, fitText(row.name, LIST_WIDTH - (x - (leftPos + LIST_X)) - 15),
                        x + 10, y + 4, 0xD4C7AF, false);
            }
        }
        graphics.disableScissor();

        if (visibleRows.size() * ROW_HEIGHT > bottom - top) {
            graphics.fill(leftPos + LIST_X + LIST_WIDTH - 3, top, leftPos + LIST_X + LIST_WIDTH - 1, bottom,
                    0xFF393633);
            int thumbHeight = Math.max(12, (bottom - top) * (bottom - top) / (visibleRows.size() * ROW_HEIGHT));
            int maxScroll = maxScrollRows();
            int thumbY = top + (bottom - top - thumbHeight) * scrollOffset / Math.max(1, maxScroll);
            graphics.fill(leftPos + LIST_X + LIST_WIDTH - 3, thumbY,
                    leftPos + LIST_X + LIST_WIDTH - 1, thumbY + thumbHeight, 0xFFB7A58B);
        }
    }

    private void renderPreview(GuiGraphics graphics, int mouseX, int mouseY) {
        Item selectedHat = menu.getSelectedHat();
        previewPlayer.setItemSlot(EquipmentSlot.HEAD,
                selectedHat == null ? ItemStack.EMPTY : new ItemStack(selectedHat));
        if (selectedHat != null) {
            Component name = new ItemStack(selectedHat).getHoverName();
            graphics.drawCenteredString(font, fitText(name, PREVIEW_WIDTH - 6),
                    leftPos + PREVIEW_X + PREVIEW_WIDTH / 2, topPos + 190, 0xE7E1D8);
        }
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                leftPos + PREVIEW_X + 4, topPos + 42,
                leftPos + PREVIEW_X + PREVIEW_WIDTH - 4, topPos + 183,
                58, 0.0F, mouseX, mouseY, previewPlayer);
    }

    private void renderRecipe(GuiGraphics graphics, int mouseX, int mouseY) {
        SewingRecipe recipe = menu.getSelectedRecipe();
        if (recipe == null) {
            graphics.drawString(font, Component.translatable("screen.createforever.sewing_machine.no_recipe"),
                    leftPos + RECIPE_X, topPos + 49, 0xA9A39B);
            return;
        }

        int y = topPos + 47;
        for (IngredientLine line : ingredientLines(recipe)) {
            graphics.renderItem(line.displayStack, leftPos + RECIPE_X, y);
            Component label = line.count > 1
                    ? Component.literal(line.name.getString() + " x" + line.count)
                    : line.name;
            graphics.drawString(font, fitText(label, RECIPE_WIDTH - 27),
                    leftPos + RECIPE_X + 23, y + 5, 0xE7E1D8, false);
            y += 20;
            if (y > topPos + 177) {
                graphics.drawString(font, "...", leftPos + RECIPE_X + 3, y - 2, 0xA9A39B, false);
                break;
            }
        }

        if (menu.canCraftSelected()) {
            int buttonX = leftPos + RECIPE_X;
            int buttonY = topPos + 183;
            boolean hovered = mouseX >= buttonX && mouseX < buttonX + RECIPE_WIDTH
                    && mouseY >= buttonY && mouseY < buttonY + 20;
            graphics.fill(buttonX, buttonY, buttonX + RECIPE_WIDTH, buttonY + 20,
                    hovered ? 0xFF6B8050 : 0xFF53683B);
            graphics.drawCenteredString(font,
                    Component.translatable("screen.createforever.sewing_machine.sew"),
                    buttonX + RECIPE_WIDTH / 2, buttonY + 6, 0xFFFFFF);
        }
    }

    private List<IngredientLine> ingredientLines(SewingRecipe recipe) {
        Map<String, IngredientLine> lines = new LinkedHashMap<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            ItemStack[] choices = ingredient.getItems();
            if (choices.length == 0) {
                continue;
            }

            String key = ingredientKey(choices);
            IngredientLine existing = lines.get(key);
            if (existing == null) {
                lines.put(key, new IngredientLine(choices[0], ingredientName(choices, key), 1));
            } else {
                existing.count++;
            }
        }
        return List.copyOf(lines.values());
    }

    private String ingredientKey(ItemStack[] choices) {
        return String.join("|", java.util.Arrays.stream(choices)
                .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()) + ":" + stack.getComponents().hashCode())
                .sorted()
                .toList());
    }

    private Component ingredientName(ItemStack[] choices, String key) {
        if (choices.length == 1) {
            return choices[0].getHoverName();
        }

        return ingredientNameCache.computeIfAbsent(key, ignored -> resolveTagIngredientName(choices));
    }

    private Component resolveTagIngredientName(ItemStack[] choices) {
        Set<Item> choiceItems = java.util.Arrays.stream(choices)
                .map(ItemStack::getItem)
                .collect(java.util.stream.Collectors.toSet());
        for (TagKey<Item> tag : BuiltInRegistries.ITEM.getTagNames().toList()) {
            Set<Item> tagItems = BuiltInRegistries.ITEM.getTag(tag)
                    .stream()
                    .flatMap(holders -> holders.stream().map(Holder::value))
                    .collect(java.util.stream.Collectors.toSet());
            if (tagItems.equals(choiceItems)) {
                return Component.translatable("tag." + tag.location().getNamespace() + "." + tag.location().getPath());
            }
        }
        return Component.translatable("screen.createforever.sewing_machine.tag_ingredient");
    }

    private List<ListRow> buildRows() {
        List<ListRow> rows = new ArrayList<>();
        rows.add(ListRow.tag(ModTags.Items.CRAFTABLE_HATS, 0));
        if (!expandedTags.getOrDefault(ModTags.Items.CRAFTABLE_HATS, false)) {
            return rows;
        }
        appendGroupRows(rows, rootGroup, 0, new HashSet<>());
        return rows;
    }

    private TagGroup createTagTree() {
        TagKey<Item> rootTag = ModTags.Items.CRAFTABLE_HATS;
        Map<TagKey<Item>, Set<Item>> tagContents = new HashMap<>();
        for (TagKey<Item> tag : BuiltInRegistries.ITEM.getTagNames().toList()) {
            if (tag.equals(rootTag)) {
                continue;
            }
            Set<Item> contents = BuiltInRegistries.ITEM.getTag(tag)
                    .stream()
                    .flatMap(holders -> holders.stream().map(Holder::value))
                    .collect(java.util.stream.Collectors.toSet());
            if (!contents.isEmpty() && craftableHatSet.containsAll(contents)
                    && !contents.equals(craftableHatSet)) {
                tagContents.put(tag, contents);
            }
        }

        TagGroup root = new TagGroup(rootTag, craftableHatSet);
        Map<TagKey<Item>, TagGroup> groups = new HashMap<>();
        groups.put(rootTag, root);
        tagContents.forEach((tag, contents) -> groups.put(tag, new TagGroup(tag, contents)));

        for (Map.Entry<TagKey<Item>, Set<Item>> entry : tagContents.entrySet()) {
            TagKey<Item> parentTag = rootTag;
            int parentSize = craftableHatSet.size();
            for (Map.Entry<TagKey<Item>, Set<Item>> candidate : tagContents.entrySet()) {
                if (!candidate.getKey().equals(entry.getKey())
                        && candidate.getValue().size() > entry.getValue().size()
                        && candidate.getValue().containsAll(entry.getValue())
                        && candidate.getValue().size() < parentSize) {
                    parentTag = candidate.getKey();
                    parentSize = candidate.getValue().size();
                }
            }
            groups.get(parentTag).children.add(groups.get(entry.getKey()));
        }
        sortChildren(root);
        return root;
    }

    private void expandTags(TagGroup group) {
        expandedTags.put(group.tag, true);
        for (TagGroup child : group.children) {
            expandTags(child);
        }
    }

    private void sortChildren(TagGroup group) {
        group.children.sort(Comparator.comparing(child -> child.tag.location().toString()));
        for (TagGroup child : group.children) {
            sortChildren(child);
        }
    }

    private void appendGroupRows(List<ListRow> rows, TagGroup group, int depth, Set<Item> displayedItems) {
        for (TagGroup child : group.children) {
            rows.add(ListRow.tag(child.tag, depth + 1));
            if (expandedTags.getOrDefault(child.tag, false)) {
                appendGroupRows(rows, child, depth + 1, displayedItems);
            }
        }

        group.items.stream()
                .filter(item -> !displayedItems.contains(item))
                .sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()))
                .forEach(item -> {
                    rows.add(ListRow.item(item, depth + 1));
                    displayedItems.add(item);
                });
    }

    private int maxScrollRows() {
        int rowsInView = (LIST_BOTTOM - LIST_TOP) / ROW_HEIGHT;
        return Math.max(0, visibleRows.size() - rowsInView);
    }

    private Component fitText(Component component, int maxWidth) {
        String text = component.getString();
        if (font.width(text) <= maxWidth) {
            return component;
        }
        while (!text.isEmpty() && font.width(text + "...") > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return Component.literal(text + "...");
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isInside(mouseX, mouseY, LIST_X, LIST_TOP, LIST_WIDTH, LIST_BOTTOM - LIST_TOP)) {
            int rowIndex = scrollOffset + (int) (mouseY - topPos - LIST_TOP) / ROW_HEIGHT;
            if (rowIndex >= 0 && rowIndex < visibleRows.size()) {
                ListRow row = visibleRows.get(rowIndex);
                if (row.item != null) {
                    int index = menu.getCraftableHats().indexOf(row.item);
                    menu.setSelectedHatIndex(index);
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
                } else {
                    expandedTags.put(row.tag, !expandedTags.getOrDefault(row.tag, false));
                    scrollOffset = Math.min(scrollOffset, maxScrollRows());
                }
                return true;
            }
        }

        if (button == 0 && menu.canCraftSelected()
                && isInside(mouseX, mouseY, RECIPE_X, 183, RECIPE_WIDTH, 20)) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, SewingMachineMenu.CRAFT_BUTTON);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isInside(mouseX, mouseY, LIST_X, LIST_TOP, LIST_WIDTH, LIST_BOTTOM - LIST_TOP)) {
            scrollOffset = Math.max(0, Math.min(maxScrollRows(), scrollOffset - (int) Math.signum(scrollY) * 3));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= leftPos + x && mouseX < leftPos + x + width
                && mouseY >= topPos + y && mouseY < topPos + y + height;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record ListRow(TagKey<Item> tag, Item item, Component name, int depth) {
        private static ListRow tag(TagKey<Item> tag, int depth) {
            return new ListRow(tag, null,
                    Component.translatable("tag." + tag.location().getNamespace() + "." + tag.location().getPath()),
                    depth);
        }

        private static ListRow item(Item item, int depth) {
            return new ListRow(null, item, new ItemStack(item).getHoverName(), depth);
        }
    }

    private static final class TagGroup {
        private final TagKey<Item> tag;
        private final Set<Item> items;
        private final List<TagGroup> children = new ArrayList<>();

        private TagGroup(TagKey<Item> tag, Set<Item> items) {
            this.tag = tag;
            this.items = items;
        }
    }

    private static final class IngredientLine {
        private final ItemStack displayStack;
        private final Component name;
        private int count;

        private IngredientLine(ItemStack displayStack, Component name, int count) {
            this.displayStack = displayStack;
            this.name = name;
            this.count = count;
        }
    }
}
