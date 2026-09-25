package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import net.minecraft.ChatFormatting;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.search.KineticItemSearch;
import dev.xyat.kineticcore.api.client.selector.KineticSelectors;
import dev.xyat.kineticcore.api.client.widget.button.KineticButtons.HighZButton;
import dev.xyat.kineticcore.api.client.widget.input.KineticNumericFields.NumericEditBox;
import dev.xyat.contentstudio.recipe.RecipeRecord;
import dev.xyat.contentstudio.recipe.RecipeRegistry;
import dev.xyat.contentstudio.recipe.UniversalRecipeMenu;
import dev.xyat.contentstudio.recipe.network.RecipeNetwork;
import dev.xyat.contentstudio.recipe.network.RecipeNetwork.RecipeChangePacket;
import net.minecraft.client.gui.GuiGraphics;
import dev.xyat.kineticcore.api.client.screen.KineticContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;

public class RecipeScreen extends KineticContainerScreen<UniversalRecipeMenu> {
    private static final ResourceLocation CRAFTING_BG = KineticResourceIds.parse("textures/gui/container/crafting_table.png");
    private static final ResourceLocation FURNACE_BG = KineticResourceIds.parse("textures/gui/container/furnace.png");
    private static final ResourceLocation SMITHING_BG = KineticResourceIds.parse("textures/gui/container/smithing.png");
    private static final ResourceLocation STONECUTTER_BG = KineticResourceIds.parse("textures/gui/container/stonecutter.png");
    private static final int OUTPUT_SLOT_SIZE = 16;
    private static final int COUNT_INPUT_WIDTH = 26;
    private static final int COUNT_INPUT_FRAME_PADDING = 2;

    private boolean isShapeless = false;
    private final int[] inputNbtModes = new int[9];
    private boolean outputUseNbt = true;
    private String draftCountText = "1";

    private NumericEditBox countInput;
    private HighZButton saveButton;
    private int boxX;
    private int boxY;

    public RecipeScreen(UniversalRecipeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
loadInitialDraft();
        configureStandaloneDraft(this::captureRecipeDraft, this::restoreRecipeDraft);
    }

    private record RecipeDraftSnapshot(
            List<CompoundTag> inputs,
            CompoundTag output,
            boolean shapeless,
            List<Integer> inputModes,
            boolean outputUseNbt,
            String countText
    ) {
    }

    private void loadInitialDraft() {
        if (menu.clientRecordData == null) return;
        RecipeRecord record = RecipeRecord.loadFromNBT(menu.clientRecordData);
        isShapeless = record.isShapeless;
        outputUseNbt = record.outputUseNbt;
        java.util.Arrays.fill(inputNbtModes, 0);
        for (int i = 0; i < record.inputModes.size() && i < inputNbtModes.length; i++) {
            inputNbtModes[i] = record.inputModes.get(i);
        }
        for (int i = 0; i < menu.inputContainer.getContainerSize(); i++) {
            menu.inputContainer.setItem(i, i < record.inputs.size() ? record.inputs.get(i).copy() : ItemStack.EMPTY);
        }
        menu.outputContainer.setItem(0, record.output == null ? ItemStack.EMPTY : record.output.copy());
        draftCountText = Integer.toString(Math.max(1, record.output == null ? 1 : record.output.getCount()));
        menu.clientRecordData = null;
    }

    private RecipeDraftSnapshot captureRecipeDraft() {
        List<CompoundTag> inputs = new ArrayList<>();
        for (int i = 0; i < menu.inputContainer.getContainerSize(); i++) {
            inputs.add(saveStack(menu.inputContainer.getItem(i)));
        }
        List<Integer> modes = new ArrayList<>(inputNbtModes.length);
        for (int mode : inputNbtModes) modes.add(mode);
        String countText = countInput == null ? draftCountText : countInput.getValue();
        draftCountText = countText;
        return new RecipeDraftSnapshot(
                List.copyOf(inputs),
                saveStack(menu.outputContainer.getItem(0)),
                isShapeless,
                List.copyOf(modes),
                outputUseNbt,
                countText
        );
    }

    private void restoreRecipeDraft(RecipeDraftSnapshot snapshot) {
        if (snapshot == null) return;
        for (int i = 0; i < menu.inputContainer.getContainerSize(); i++) {
            menu.inputContainer.setItem(i, i < snapshot.inputs().size() ? loadStack(snapshot.inputs().get(i)) : ItemStack.EMPTY);
        }
        menu.outputContainer.setItem(0, loadStack(snapshot.output()));
        isShapeless = snapshot.shapeless();
        java.util.Arrays.fill(inputNbtModes, 0);
        for (int i = 0; i < snapshot.inputModes().size() && i < inputNbtModes.length; i++) {
            inputNbtModes[i] = snapshot.inputModes().get(i);
        }
        outputUseNbt = snapshot.outputUseNbt();
        draftCountText = snapshot.countText();
        if (countInput != null) countInput.setValue(draftCountText);
        rebuildEditorWidgets();
    }

    private static CompoundTag saveStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return new CompoundTag();
        return stack.save(new CompoundTag());
    }

    private static ItemStack loadStack(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return ItemStack.EMPTY;
        return ItemStack.of(tag.copy());
    }

    private void rebuildEditorWidgets() {
        if (minecraft == null) return;
        String preservedCount = draftCountText;
        rebuildUi();
        draftCountText = preservedCount;
        if (countInput != null) countInput.setValue(preservedCount);
    }

    public void showToast(Component msg) {
        KineticOverlays.toast(msg);
    }

    private void showToast(String key, Object... args) {
        showToast(Component.translatable(key, args));
    }

    @Override
    protected void buildUi() {
        if (menu.type == RecipeRegistry.EditorType.SMITHING) {
            this.outputUseNbt = false;
            this.titleLabelX = this.imageWidth - this.font.width(this.title) - 10;
            this.titleLabelY = 10;
        }

        int bW = 85;
        int bH = 20;
        int sp = 4;
        int x = this.leftPos - bW - 6;
        int y = this.topPos + 5;

        addHighZButton(
                x, y, bW, Component.translatable("gui.contentstudio.recipe.recipehud.back"), null, 40,
                this::returnFromRecipeEditor
        );
        y += bH + sp;

        if (menu.type == RecipeRegistry.EditorType.CRAFTING) {
            addHighZToggleButton(
                    x, y, bW, isShapeless,
                    Component.translatable("gui.contentstudio.recipe.recipehud.mode.shapeless"),
                    Component.translatable("gui.contentstudio.recipe.recipehud.mode.shaped"),
                    null, null, value -> isShapeless = value, 40
            );
            y += bH + sp;
        }

        saveButton = addHighZButton(
                x, y, bW, Component.translatable("gui.contentstudio.recipe.recipehud.save_deferred"),
                null, 40, this::handleSave
        );

        int outputSlotX;
        int outputSlotY;
        switch (menu.type) {
            case CRAFTING -> { outputSlotX = 124; outputSlotY = 35; }
            case SMITHING -> { outputSlotX = 98; outputSlotY = 48; }
            case STONECUTTER -> { outputSlotX = 143; outputSlotY = 33; }
            default -> { outputSlotX = 116; outputSlotY = 35; }
        }

        boxX = this.leftPos + outputSlotX;
        boxY = this.topPos + outputSlotY - 18;

        if (countInput != null) {
            draftCountText = countInput.getValue();
        }

        countInput = addIntegerField(
                boxX + (OUTPUT_SLOT_SIZE - COUNT_INPUT_WIDTH) / 2, boxY, COUNT_INPUT_WIDTH, Component.empty(),
                false, 1, 64, null, null
        );
        countInput.setMaxLength(3);
        countInput.setValue(draftCountText);
        countInput.setResponder(value -> draftCountText = value);
    }

    private Component getModeText() {
        return isShapeless
                ? Component.translatable("gui.contentstudio.recipe.recipehud.mode.shapeless")
                : Component.translatable("gui.contentstudio.recipe.recipehud.mode.shaped");
    }

    private void handleItemSelectorResult(KineticSelectors.ItemSelection selection, int slotIdx, Container container, boolean allowTag) {
        if (selection.isTag()) {
            if (!allowTag) return;
            String tagId = "#" + selection.value();
            ItemStack dummy = new ItemStack(Items.PAPER);
            dummy.getOrCreateTag().putString("kt_tag", tagId);
            dummy.setHoverName(Component.translatable("gui.contentstudio.recipe.recipehud.tooltip.tag_item.colored", Component.literal(tagId).withStyle(ChatFormatting.GREEN)));
            container.setItem(slotIdx, dummy);
            return;
        }
        if (!selection.isItem()) return;
        ItemStack stack = selection.stack().copy();
        container.setItem(slotIdx, stack.isEmpty() || stack.is(Items.AIR) ? ItemStack.EMPTY : stack);
    }

    private boolean isInvalidPlaceholder(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.getTag() != null
                && stack.getTag().getBoolean("contentstudio_invalid_placeholder");
    }

    private boolean containsInvalidPlaceholder() {
        if (isInvalidPlaceholder(menu.outputContainer.getItem(0))) {
            return true;
        }
        int inputCount = menu.type == RecipeRegistry.EditorType.CRAFTING
                ? 9
                : (menu.type == RecipeRegistry.EditorType.SMITHING ? 3 : 1);
        for (int i = 0; i < inputCount; i++) {
            if (isInvalidPlaceholder(menu.inputContainer.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    private void openItemSelectorForSlot(int slotIdx, Container container, boolean allowTag) {
        KineticSelectors.openItemSelector(
                this,
                selection -> handleItemSelectorResult(selection, slotIdx, container, allowTag)
        );
    }

    @Override
    protected boolean containerMouseClicked(double mouseX, double mouseY, int button) {
        if (countInput != null) {
            if (countInput.isMouseOver(mouseX, mouseY)) {
                focusControl(countInput);
            } else {
                blurControl(countInput);
            }
        }

        if (KineticMouseButtons.isPrimary(button)
                && this.hoveredSlot != null
                && isInvalidPlaceholder(this.hoveredSlot.getItem())
                && this.menu.getCarried().isEmpty()) {
            if (this.hoveredSlot.container == menu.inputContainer
                    || this.hoveredSlot.container == menu.outputContainer) {
                int slotIdx = this.hoveredSlot.getContainerSlot();
                Container container = this.hoveredSlot.container;
                boolean isInput = container == menu.inputContainer;
                openItemSelectorForSlot(slotIdx, container, isInput);
                return true;
            }
        }

        // Shift+左键有物品：直接召唤全屏统一 NBT 编辑器
        if (KineticMouseButtons.isPrimary(button) && KineticClientRuntime.shiftModifierDown() && this.hoveredSlot != null && this.hoveredSlot.hasItem() && this.menu.getCarried().isEmpty()) {
            if (this.hoveredSlot.container == menu.inputContainer || this.hoveredSlot.container == menu.outputContainer) {
                int slotIdx = this.hoveredSlot.getContainerSlot();
                Container container = this.hoveredSlot.container;
                ItemStack stack = this.hoveredSlot.getItem();
                String initNbt = (stack.hasTag() && stack.getTag() != null) ? stack.getTag().toString() : "";

                if (this.minecraft != null) {
                    KineticSelectors.openNbtEditor(this, initNbt, (savedNbt) -> {
                        try {
                            if (savedNbt == null || savedNbt.trim().isEmpty() || savedNbt.trim().equals("{}")) {
                                stack.setTag(null);
                            } else {
                                stack.setTag(net.minecraft.nbt.TagParser.parseTag(savedNbt));
                            }
                            container.setItem(slotIdx, stack);
                            showToast("msg.contentstudio.recipe.saved");
                        } catch (Exception ignored) {
                        }
                    });
                }
                return true;
            }
        }

        // 左键空槽：打开物品搜索（输入槽支持返回#tag，输出槽只返回物品）
        if (KineticMouseButtons.isPrimary(button) && this.hoveredSlot != null && !this.hoveredSlot.hasItem() && this.menu.getCarried().isEmpty()) {
            if (this.hoveredSlot.container == menu.inputContainer || this.hoveredSlot.container == menu.outputContainer) {
                int slotIdx = this.hoveredSlot.getContainerSlot();
                Container container = this.hoveredSlot.container;
                boolean isInput = this.hoveredSlot.container == menu.inputContainer;

                openItemSelectorForSlot(slotIdx, container, isInput);
                return true;
            }
        }

        // 右键输入槽有物品：切换NBT匹配模式（tag物品不可切换）
        if (KineticMouseButtons.isSecondary(button) && this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            if (this.hoveredSlot.container == menu.inputContainer) {
                int slotIdx = this.hoveredSlot.getContainerSlot();
                ItemStack stack = this.hoveredSlot.getItem();
                if (stack.getTag() != null && stack.hasTag() && stack.getTag().contains("kt_tag")) return true;
                inputNbtModes[slotIdx] = (inputNbtModes[slotIdx] + 1) % 3;
                return true;
            } else if (this.hoveredSlot.container == menu.outputContainer) {
                if (menu.type != RecipeRegistry.EditorType.SMITHING) outputUseNbt = !outputUseNbt;
                return true;
            }
        }

        return false;
    }

    @Override
    protected @NotNull List<Component> getTooltipFromContainerItem(@NotNull ItemStack stack) {
        List<Component> original = super.getTooltipFromContainerItem(stack);
        if (isInvalidPlaceholder(stack)) {
            List<Component> invalidTooltip = new ArrayList<>();
            invalidTooltip.add(stack.getHoverName());
            invalidTooltip.add(Component.empty());
            invalidTooltip.add(Component.translatable("gui.contentstudio.recipe.recipehud.tooltip.invalid_placeholder_replace.colored"));
            return invalidTooltip;
        }
        if (this.hoveredSlot != null && this.hoveredSlot.getItem() == stack
                && (this.hoveredSlot.container == menu.inputContainer || this.hoveredSlot.container == menu.outputContainer)) {
            List<Component> cleaned = new ArrayList<>();
            if (!original.isEmpty()) cleaned.add(original.get(0));
            cleaned.add(Component.empty());
            if (this.hoveredSlot.container == menu.inputContainer) {
                if (stack.getTag() != null && stack.hasTag() && stack.getTag().contains("kt_tag")) {
                    cleaned.add(stack.getHoverName());
                    cleaned.add(Component.translatable("gui.contentstudio.recipe.recipehud.tooltip.lclick_remove.tag.colored"));
                    return cleaned;
                }
                int mode = inputNbtModes[this.hoveredSlot.getContainerSlot()];
                Component statusPrefix = Component.translatable("gui.contentstudio.recipe.recipehud.status.nbt.colored");
                if (mode == 1) {
                    cleaned.add(statusPrefix.copy().append(Component.translatable("gui.contentstudio.recipe.recipehud.mode.weak.colored")));
                    cleaned.add(Component.translatable("gui.contentstudio.recipe.recipehud.mode.weak.desc.colored"));
                } else if (mode == 2) {
                    cleaned.add(statusPrefix.copy().append(Component.translatable("gui.contentstudio.recipe.recipehud.mode.strong.colored")));
                    cleaned.add(Component.translatable("gui.contentstudio.recipe.recipehud.mode.strong.desc.colored"));
                } else {
                    cleaned.add(statusPrefix.copy().append(Component.translatable("gui.contentstudio.recipe.recipehud.mode.none.colored")));
                    cleaned.add(Component.translatable("gui.contentstudio.recipe.recipehud.mode.none.desc.colored"));
                }
                cleaned.add(Component.translatable("gui.contentstudio.recipe.recipehud.tooltip.rclick_toggle.colored"));
            } else {
                Component statusPrefix = Component.translatable("gui.contentstudio.recipe.recipehud.status.nbt_output.colored");
                if (menu.type == RecipeRegistry.EditorType.SMITHING) {
                    cleaned.add(statusPrefix.copy().append(Component.translatable("gui.contentstudio.recipe.recipehud.mode.off.colored")));
                    cleaned.add(Component.translatable("gui.contentstudio.recipe.recipehud.mode.none.desc.colored"));
                } else {
                    cleaned.add(statusPrefix.copy().append(Component.translatable(outputUseNbt ? "gui.contentstudio.recipe.recipehud.mode.on.colored" : "gui.contentstudio.recipe.recipehud.mode.off.colored")));
                    cleaned.add(Component.translatable(outputUseNbt ? "gui.contentstudio.recipe.recipehud.mode.on.desc.colored" : "gui.contentstudio.recipe.recipehud.mode.off.desc.colored"));
                    cleaned.add(Component.translatable("gui.contentstudio.recipe.recipehud.tooltip.rclick_toggle.colored"));
                }
            }
            cleaned.add(Component.translatable("gui.contentstudio.recipe.recipehud.tooltip.shift_edit_nbt.colored"));
            cleaned.add(Component.translatable("gui.contentstudio.recipe.recipehud.tooltip.lclick_remove.slot.colored"));
            return cleaned;
        }
        return original;
    }

    private void handleSave() {
        if (containsInvalidPlaceholder()) {
            showToast("gui.contentstudio.recipe.recipehud.err.invalid_placeholder");
            return;
        }
        ItemStack originalOut = menu.outputContainer.getItem(0);
        if (originalOut.isEmpty()) {
            showToast("gui.contentstudio.recipe.recipehud.err.output_empty");
            return;
        }
        Integer countValue =
                countInput.getIntValue();

        if (countValue == null) {
            showToast(
                    "msg.contentstudio.recipe.invalid_number"
            );
            return;
        }

        int count = countValue;
        ItemStack outStack = originalOut.copy();
        outStack.setCount(count);
        List<ItemStack> inputs = new ArrayList<>();
        List<Integer> modes = new ArrayList<>();
        int inputCount = (menu.type == RecipeRegistry.EditorType.CRAFTING) ?
                9 : ((menu.type == RecipeRegistry.EditorType.SMITHING) ? 3 : 1);
        for (int i = 0; i < inputCount; i++) {
            inputs.add(menu.inputContainer.getItem(i));
            modes.add(inputNbtModes[i]);
        }
        if (menu.type != RecipeRegistry.EditorType.CRAFTING || isShapeless) {
            if (inputs.stream().allMatch(ItemStack::isEmpty)) {
                showToast("gui.contentstudio.recipe.recipehud.err.input_empty");
                return;
            }
        }
        String uuidToSend = menu.editUuid == null ? "" : menu.editUuid;
        RecipeNetwork.sendRecipeChange(new RecipeChangePacket(uuidToSend, menu.editConfigIndex, menu.type.name(), isShapeless, modes, menu.type != RecipeRegistry.EditorType.SMITHING && outputUseNbt, 0, inputs, outStack));
        RecipeEditSessionState.markPendingRecipeApply();
        draftCountText = countInput.getValue();
        commitDraft();
        showToast("gui.contentstudio.recipe.recipehud.msg.saving_only");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void returnFromRecipeEditor() {
        onClose();
    }

    @Override
    protected boolean handleCloseRequest() {
        RecipeEditSessionState.applyPendingAndClear();
        if (RecipePreviewState.returnToPreview && KineticClientRuntime.localPlayer() != null) {
            RecipeNetwork.requestRecipeRecords();
        }
        return false;
    }

    @Override
    @ParametersAreNonnullByDefault
    protected void renderBg(GuiGraphics g, float p, int x, int y) {
        ResourceLocation t = switch (menu.type) {
            case CRAFTING -> CRAFTING_BG;
            case SMITHING -> SMITHING_BG;
            case STONECUTTER -> STONECUTTER_BG;
            default -> FURNACE_BG;
        };
        g.blit(t, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
        for (net.minecraft.world.inventory.Slot slot : this.menu.slots) {
            if (slot.isActive() && (slot.container == this.menu.inputContainer || slot.container == this.menu.outputContainer)) {
                GuiTheme.itemSlot(g, this.leftPos + slot.x - 1, this.topPos + slot.y - 1);
            }
        }
        int countInputFrameWidth = COUNT_INPUT_WIDTH + COUNT_INPUT_FRAME_PADDING * 2;
        int countInputFrameX = boxX + (OUTPUT_SLOT_SIZE - countInputFrameWidth) / 2;
        GuiTheme.panelAlt(g, countInputFrameX, boxY, countInputFrameWidth, 12);
    }

    @Override
    protected void requestContainerTooltips(
            @NotNull GuiGraphics graphics,
            int virtualMouseX,
            int virtualMouseY,
            int screenMouseX,
            int screenMouseY
    ) {
    }

    @Override
    protected void renderScreenOverlay(
            GuiGraphics graphics,
            int virtualMouseX,
            int virtualMouseY,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        List<Component> tooltip = buildOverlayTooltip(virtualMouseX, virtualMouseY);
        if (tooltip == null || tooltip.isEmpty()) {
            return;
        }

        KineticOverlays.requestTooltip(tooltip, mouseX, mouseY);
    }

    private List<Component> buildOverlayTooltip(int virtualMouseX, int virtualMouseY) {
        if (saveButton != null && saveButton.isMouseOver(virtualMouseX, virtualMouseY)) {
            return List.of(Component.translatable("gui.contentstudio.recipe.recipehud.tooltip.save_deferred"));
        }

        if (countInput != null && countInput.isMouseOver(virtualMouseX, virtualMouseY)) {
            return List.of(Component.translatable("gui.contentstudio.recipe.recipehud.tooltip.count_input"));
        }

        if (hoveredSlot == null || !menu.getCarried().isEmpty()) {
            return null;
        }

        if (hoveredSlot.hasItem()) {
            return getTooltipFromContainerItem(hoveredSlot.getItem());
        }

        if (hoveredSlot.container != menu.inputContainer
                && hoveredSlot.container != menu.outputContainer) {
            return null;
        }

        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("gui.contentstudio.recipe.recipehud.tooltip.empty_slot_title.colored"));
        tooltip.add(Component.translatable("gui.contentstudio.recipe.recipehud.tooltip.lclick_search.colored"));
        return tooltip;
    }
}
