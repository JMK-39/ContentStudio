package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.page.KineticContainerPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.render.KineticTexture;
import dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors;
import dev.xyat.kineticcore.api.client.gui.text.KineticText;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.ui.NumberType;
import dev.xyat.kineticcore.api.client.gui.widget.KineticButton;
import dev.xyat.kineticcore.api.client.gui.widget.KineticNumberField;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.contentstudio.recipe.RecipeRecord;
import dev.xyat.contentstudio.recipe.RecipeRegistry;
import dev.xyat.contentstudio.recipe.UniversalRecipeMenu;
import dev.xyat.contentstudio.recipe.network.RecipeNetwork;
import dev.xyat.contentstudio.recipe.network.RecipeNetwork.RecipeChangePacket;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
//? if >=1.21 {
/*import dev.xyat.contentstudio.item.ItemData;
*///?}

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class RecipePage extends KineticContainerPage<UniversalRecipeMenu> {
    private static final KineticTexture CRAFTING_BG = KineticTexture.of("minecraft", "textures/gui/container/crafting_table.png");
    private static final KineticTexture FURNACE_BG = KineticTexture.of("minecraft", "textures/gui/container/furnace.png");
    private static final KineticTexture SMITHING_BG = KineticTexture.of("minecraft", "textures/gui/container/smithing.png");
    private static final KineticTexture STONECUTTER_BG = KineticTexture.of("minecraft", "textures/gui/container/stonecutter.png");
    private static final int OUTPUT_SLOT_SIZE = 16;
    private static final int COUNT_INPUT_WIDTH = 26;
    private static final int COUNT_INPUT_FRAME_PADDING = 2;

    private boolean isShapeless = false;
    private final int[] inputNbtModes = new int[9];
    private boolean outputUseNbt = true;
    private String draftCountText = "1";
    /** 载入配方时的输出数量，作为数量框的默认值 / Output count when the recipe was loaded; the count field's default. */
    private String loadedCountText = "1";

    private KineticNumberField countInput;
    private KineticButton saveButton;
    private int boxX;
    private int boxY;
    private final Map<String,List<ItemStack>> tagPreviews=new HashMap<>();

    public RecipePage(UniversalRecipeMenu menu, Component title) {
        super(menu, title);
        // 原 isPauseScreen() 返回 false / Former isPauseScreen() returned false.
        setPausesGame(false);
        setImageSize(176, 166);
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
        if (menu().clientRecordData == null) return;
        RecipeRecord record = RecipeRecord.loadFromNBT(menu().clientRecordData);
        isShapeless = record.isShapeless;
        outputUseNbt = record.outputUseNbt;
        java.util.Arrays.fill(inputNbtModes, 0);
        for (int i = 0; i < record.inputModes.size() && i < inputNbtModes.length; i++) {
            inputNbtModes[i] = record.inputModes.get(i);
        }
        for (int i = 0; i < menu().inputContainer.getContainerSize(); i++) {
            menu().inputContainer.setItem(i, i < record.inputs.size() ? record.inputs.get(i).copy() : ItemStack.EMPTY);
        }
        menu().outputContainer.setItem(0, record.output == null ? ItemStack.EMPTY : record.output.copy());
        draftCountText = Integer.toString(Math.max(1, record.output == null ? 1 : record.output.getCount()));
        loadedCountText = draftCountText;
        menu().clientRecordData = null;
    }

    private RecipeDraftSnapshot captureRecipeDraft() {
        List<CompoundTag> inputs = new ArrayList<>();
        for (int i = 0; i < menu().inputContainer.getContainerSize(); i++) {
            inputs.add(saveStack(menu().inputContainer.getItem(i)));
        }
        List<Integer> modes = new ArrayList<>(inputNbtModes.length);
        for (int mode : inputNbtModes) modes.add(mode);
        String countText = countInput == null ? draftCountText : countInput.textValue();
        draftCountText = countText;
        return new RecipeDraftSnapshot(
                List.copyOf(inputs),
                saveStack(menu().outputContainer.getItem(0)),
                isShapeless,
                List.copyOf(modes),
                outputUseNbt,
                countText
        );
    }

    private void restoreRecipeDraft(RecipeDraftSnapshot snapshot) {
        if (snapshot == null) return;
        for (int i = 0; i < menu().inputContainer.getContainerSize(); i++) {
            menu().inputContainer.setItem(i, i < snapshot.inputs().size() ? loadStack(snapshot.inputs().get(i)) : ItemStack.EMPTY);
        }
        menu().outputContainer.setItem(0, loadStack(snapshot.output()));
        isShapeless = snapshot.shapeless();
        java.util.Arrays.fill(inputNbtModes, 0);
        for (int i = 0; i < snapshot.inputModes().size() && i < inputNbtModes.length; i++) {
            inputNbtModes[i] = snapshot.inputModes().get(i);
        }
        outputUseNbt = snapshot.outputUseNbt();
        draftCountText = snapshot.countText();
        if (countInput != null) countInput.setTextValue(draftCountText);
        rebuildEditorWidgets();
    }

    private static CompoundTag saveStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return new CompoundTag();
//? if >=1.21 {
/*
        return ItemData.save(stack);
*///?} else {
        return stack.save(new CompoundTag());
//?}
    }

    private static ItemStack loadStack(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return ItemStack.EMPTY;
//? if >=1.21 {
/*
        return ItemData.load(tag.copy());
*///?} else {
        return ItemStack.of(tag.copy());
//?}
    }

    private void rebuildEditorWidgets() {
        if (!isAttached()) return;
        String preservedCount = draftCountText;
        rebuild();
        draftCountText = preservedCount;
        if (countInput != null) countInput.setTextValue(preservedCount);
    }

    public void showToast(Component msg) {
        KineticOverlays.toast(msg);
    }

    private void showToast(String key, Object... args) {
        showToast(KineticI18n.translatable(key, args));
    }

    @Override
    protected void build(KineticUi ui) {
        tagPreviews.clear();
        if (menu().type == RecipeRegistry.EditorType.SMITHING) {
            this.outputUseNbt = false;
            setTitleLabelPosition(imageWidth() - KineticText.width(title()) - 10, 10);
        }

        int bW = 85;
        int bH = 20;
        int sp = 4;
        int x = leftPos() - bW - 6;
        int y = topPos() + 5;

        // 原 HighZButton/HighZToggleButton → layer(1) / Former HighZButton/HighZToggleButton → layer(1).
        ui.button(x, y, bW)
                .text(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.back"))
                .layer(1)
                .onClick(this::returnFromRecipeEditor)
                .build();
        y += bH + sp;

        if (menu().type == RecipeRegistry.EditorType.CRAFTING) {
            ui.toggle(x, y, bW)
                    .value(isShapeless)
                    .labels(
                            KineticI18n.translatable("gui.contentstudio.recipe.recipehud.mode.shapeless"),
                            KineticI18n.translatable("gui.contentstudio.recipe.recipehud.mode.shaped")
                    )
                    .layer(1)
                    .onChange(value -> isShapeless = value)
                    .build();
            y += bH + sp;
        }

        saveButton = ui.button(x, y, bW)
                .text(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.save_deferred"))
                .layer(1)
                .onClick(this::handleSave)
                .build();

        int outputSlotX;
        int outputSlotY;
        switch (menu().type) {
            case CRAFTING -> { outputSlotX = 124; outputSlotY = 35; }
            case SMITHING -> { outputSlotX = 98; outputSlotY = 48; }
            case STONECUTTER -> { outputSlotX = 143; outputSlotY = 33; }
            default -> { outputSlotX = 116; outputSlotY = 35; }
        }

        boxX = leftPos() + outputSlotX;
        boxY = topPos() + outputSlotY - 18;

        if (countInput != null) {
            draftCountText = countInput.textValue();
        }

        countInput = ui.numberField(boxX + (OUTPUT_SLOT_SIZE - COUNT_INPUT_WIDTH) / 2, boxY, COUNT_INPUT_WIDTH, NumberType.INT)
                .allowNegative(false)
                .range(1, 64)
                .build();
        countInput.limitTextLength(3);
        countInput.setTextValue(draftCountText);
        countInput.setDefaultText(loadedCountText);
        countInput.onTextChange(value -> draftCountText = value);
    }

    private Component getModeText() {
        return isShapeless
                ? KineticI18n.translatable("gui.contentstudio.recipe.recipehud.mode.shapeless")
                : KineticI18n.translatable("gui.contentstudio.recipe.recipehud.mode.shaped");
    }

    private void handleItemSelectorResult(KineticSelectors.ItemSelection selection, int slotIdx, Container container, boolean allowTag) {
        if (selection.isTag()) {
            if (!allowTag) return;
            String tagId = "#" + selection.value();
            var variants=tagPreview(tagId);
            if(variants.isEmpty()) {showToast("gui.contentstudio.recipe.recipehud.err.empty_tag",tagId);return;}
            ItemStack dummy = variants.get(0).copy();
//? if >=1.21 {
/*
            ItemData.updateCustomData(dummy, tag -> tag.putString("kt_tag", tagId));
            dummy.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, KineticI18n.translatable("gui.contentstudio.recipe.recipehud.tooltip.tag_item.colored", Component.literal(tagId)));
*///?} else {
            dummy.getOrCreateTag().putString("kt_tag", tagId);
            dummy.setHoverName(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.tooltip.tag_item.colored", Component.literal(tagId)));
//?}
            container.setItem(slotIdx, dummy);
            return;
        }
        if (!selection.isItem()) return;
        ItemStack stack = selection.stack().copy();
        container.setItem(slotIdx, stack.isEmpty() || stack.is(Items.AIR) ? ItemStack.EMPTY : stack);
    }

    private static String ingredientTag(ItemStack stack) {
        if(stack.isEmpty())return "";
        //? if >=1.21 {
        /*return ItemData.customData(stack).getString("kt_tag");
        *///?} else {
        return stack.hasTag()?stack.getTag().getString("kt_tag"):"";
        //?}
    }
    private List<ItemStack> tagPreview(String id) {
        return tagPreviews.computeIfAbsent(id,key->NativeRecipeStacks.read(new com.google.gson.JsonPrimitive(key)));
    }
    @Override protected void renderForeground(KineticGraphics graphics,int mouseX,int mouseY,float partialTick) {
        graphics.push();
        try {
            graphics.raise(1);
            for(var slot:menu().slots) {
                if(slot.container!=menu().inputContainer)continue;
                String id=ingredientTag(slot.getItem());if(id.isEmpty())continue;
                int x=leftPos()+slot.x-1,y=topPos()+slot.y-1;
                // Legacy records may still carry a paper internally. The native slot hides that carrier completely.
                RecipeSlots.draw(graphics,x,y,18);
                var frame=NativeRecipeStacks.frame(tagPreview(id),System.currentTimeMillis());
                if(!frame.isEmpty())graphics.item(frame,x+1,y+1);
                RecipeSlots.tagMarker(graphics,x,y,18,slot.getItem().getCount());
            }
        }finally{graphics.pop();}
    }

    private boolean isInvalidPlaceholder(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
//? if >=1.21 {
/*
                && ItemData.customData(stack).getBoolean("contentstudio_invalid_placeholder");
*///?} else {
                && stack.getTag() != null
                && stack.getTag().getBoolean("contentstudio_invalid_placeholder");
//?}
    }

    private boolean containsInvalidPlaceholder() {
        if (isInvalidPlaceholder(menu().outputContainer.getItem(0))) {
            return true;
        }
        int inputCount = menu().type == RecipeRegistry.EditorType.CRAFTING
                ? 9
                : (menu().type == RecipeRegistry.EditorType.SMITHING ? 3 : 1);
        for (int i = 0; i < inputCount; i++) {
            if (isInvalidPlaceholder(menu().inputContainer.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    private void openItemSelectorForSlot(int slotIdx, Container container, boolean allowTag) {
        KineticSelectors.openItemSelector(
                selection -> handleItemSelectorResult(selection, slotIdx, container, allowTag)
        );
    }

    @Override
    protected boolean onMouseClickCapture(MouseInput input) {
        // 原 containerMouseClicked 在槽位与控件之前处理 / The old containerMouseClicked ran before slots and controls.
        double mouseX = input.x();
        double mouseY = input.y();
        Slot hoveredSlot = hoveredSlot();
        boolean editableSlot = hoveredSlot != null && (hoveredSlot.container == menu().inputContainer
                || hoveredSlot.container == menu().outputContainer);
        if (countInput != null) {
            if (countInput.contains(mouseX, mouseY)) {
                focus(countInput);
            } else {
                blur(countInput);
            }
        }

        if (input.isLeft()
                && hoveredSlot != null
                && isInvalidPlaceholder(hoveredSlot.getItem())
                && menu().getCarried().isEmpty()) {
            if (editableSlot) {
                int slotIdx = hoveredSlot.getContainerSlot();
                Container container = hoveredSlot.container;
                boolean isInput = container == menu().inputContainer;
                openItemSelectorForSlot(slotIdx, container, isInput);
                return true;
            }
        }

        // Keep the existing shortcut alongside the explicit context-menu action.
        if (input.isLeft() && input.hasShift() && editableSlot && hoveredSlot.hasItem() && menu().getCarried().isEmpty()) {
            editSlotItem(hoveredSlot.getContainerSlot(),hoveredSlot.container);
            return true;
        }
        // 左键槽位：打开物品搜索（输入槽支持返回#tag，输出槽只返回物品）
        if (input.isLeft() && editableSlot && menu().getCarried().isEmpty()) {
            if (editableSlot) {
                int slotIdx = hoveredSlot.getContainerSlot();
                Container container = hoveredSlot.container;
                boolean isInput = hoveredSlot.container == menu().inputContainer;

                openItemSelectorForSlot(slotIdx, container, isInput);
                return true;
            }
        }

        if (input.isRight() && editableSlot && menu().getCarried().isEmpty()) {
            openContextMenu(input.x(),input.y(),slotMenu(hoveredSlot.getContainerSlot(),hoveredSlot.container),184);
            return true;
        }
        return false;
    }

    // 原 getTooltipFromContainerItem 覆写：仅供本页自绘提示使用；首行取物品原版提示首行（名称）
    // Former getTooltipFromContainerItem override: only used by this page's own tooltip; the first line is the item's
    // vanilla tooltip first line (its name).
    private List<KineticOverlays.MenuItem> slotMenu(int slotIdx,Container container) {
        var stack=container.getItem(slotIdx);
        boolean isInput=container==menu().inputContainer;
        var items=new ArrayList<KineticOverlays.MenuItem>();
        items.add(KineticOverlays.MenuItem.action(NativeRecipeBrowserPage.tr("choose_slot"),()->openItemSelectorForSlot(slotIdx,container,isInput)));
        if(!stack.isEmpty() && !isInvalidPlaceholder(stack))
            items.add(KineticOverlays.MenuItem.action(NativeRecipeBrowserPage.tr("edit_slot"),()->editSlotItem(slotIdx,container)));
        else items.add(KineticOverlays.MenuItem.disabled(NativeRecipeBrowserPage.tr("edit_slot")));
        if(!stack.isEmpty() && isInput && ingredientTag(stack).isEmpty() && !isInvalidPlaceholder(stack)) {
            String[] modes={"none","weak","strong"};
            for(int mode=0;mode<modes.length;mode++) {
                int choice=mode;
                items.add(KineticOverlays.MenuItem.choice(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.status.nbt.colored").copy()
                        .append(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.mode."+modes[mode]+".colored")),Component.empty(),inputNbtModes[slotIdx]==mode,
                        ()->inputNbtModes[slotIdx]=choice));
            }
        }else if(!stack.isEmpty() && !isInput && menu().type!=RecipeRegistry.EditorType.SMITHING)
            items.add(KineticOverlays.MenuItem.toggle(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.status.nbt_output.colored"),Component.empty(),outputUseNbt,()->outputUseNbt=!outputUseNbt));
        if(!stack.isEmpty())items.add(KineticOverlays.MenuItem.danger(NativeRecipeBrowserPage.tr("remove_slot"),()->{
            container.setItem(slotIdx,ItemStack.EMPTY);
            if(isInput)inputNbtModes[slotIdx]=0;else outputUseNbt=false;
        }));
        else items.add(KineticOverlays.MenuItem.disabled(NativeRecipeBrowserPage.tr("remove_slot")));
        return List.copyOf(items);
    }
    private void editSlotItem(int slotIdx,Container container) {
        ItemStack stack=container.getItem(slotIdx);
        if(stack.isEmpty() || isInvalidPlaceholder(stack))return;
        if(!ingredientTag(stack).isEmpty()) {
            openItemSelectorForSlot(slotIdx,container,true);
            return;
        }
//? if >=1.21 {
/*
                String components = ItemData.format(stack);
                String itemId = dev.xyat.kineticcore.api.registry.KineticRegistries.items().id(stack.getItem()).toString();
                if (isAttached()) {
                    ItemData.edit(itemId, components, saved -> {
                        try {
                            ItemStack updated = ItemData.compile(itemId, saved);
                            updated.setCount(stack.getCount());
                            container.setItem(slotIdx, updated);
                            showToast("msg.contentstudio.recipe.saved");
                        } catch (IllegalArgumentException ignored) {
                        }
                    });
                }
*///?} else {
                String initNbt = (stack.hasTag() && stack.getTag() != null) ? stack.getTag().toString() : "";

                if (isAttached()) {
                    KineticSelectors.openNbtEditor(initNbt, (savedNbt) -> {
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

//?}
    }

    private List<Component> editorSlotTooltip(ItemStack stack, Slot hoveredSlot) {
//? if >=1.21 {
/*
        List<Component> original = stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(KineticClientRuntime.currentLevel()), KineticClientRuntime.localPlayer(), TooltipFlag.NORMAL);
*///?} else {
        List<Component> original = stack.getTooltipLines(KineticClientRuntime.localPlayer(), TooltipFlag.NORMAL);
//?}
        if (isInvalidPlaceholder(stack)) {
            List<Component> invalidTooltip = new ArrayList<>();
            invalidTooltip.add(stack.getHoverName());
            invalidTooltip.add(Component.empty());
            invalidTooltip.add(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.tooltip.invalid_placeholder_replace.colored"));
            return invalidTooltip;
        }
        if (hoveredSlot != null && hoveredSlot.getItem() == stack
                && (hoveredSlot.container == menu().inputContainer || hoveredSlot.container == menu().outputContainer)) {
            List<Component> cleaned = new ArrayList<>();
            String ingredientTag=hoveredSlot.container==menu().inputContainer?ingredientTag(stack):"";
            var shown=ingredientTag.isEmpty()?ItemStack.EMPTY:NativeRecipeStacks.frame(tagPreview(ingredientTag),System.currentTimeMillis());
            if(!shown.isEmpty())cleaned.add(shown.getHoverName());
            else if (!original.isEmpty()) cleaned.add(original.get(0));
            cleaned.add(Component.empty());
            if (hoveredSlot.container == menu().inputContainer) {
//? if >=1.21 {
/*
                if (ItemData.customData(stack).contains("kt_tag")) {
*///?} else {
                if (stack.getTag() != null && stack.hasTag() && stack.getTag().contains("kt_tag")) {
//?}
                    cleaned.add(stack.getHoverName());
                    cleaned.add(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.tooltip.lclick_remove.tag.colored"));
                    cleaned.add(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.tooltip.rclick_toggle.colored"));
                    return cleaned;
                }
                int mode = inputNbtModes[hoveredSlot.getContainerSlot()];
                Component statusPrefix = KineticI18n.translatable("gui.contentstudio.recipe.recipehud.status.nbt.colored");
                if (mode == 1) {
                    cleaned.add(statusPrefix.copy().append(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.mode.weak.colored")));
                    cleaned.add(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.mode.weak.desc.colored"));
                } else if (mode == 2) {
                    cleaned.add(statusPrefix.copy().append(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.mode.strong.colored")));
                    cleaned.add(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.mode.strong.desc.colored"));
                } else {
                    cleaned.add(statusPrefix.copy().append(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.mode.none.colored")));
                    cleaned.add(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.mode.none.desc.colored"));
                }
                cleaned.add(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.tooltip.rclick_toggle.colored"));
            } else {
                Component statusPrefix = KineticI18n.translatable("gui.contentstudio.recipe.recipehud.status.nbt_output.colored");
                if (menu().type == RecipeRegistry.EditorType.SMITHING) {
                    cleaned.add(statusPrefix.copy().append(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.mode.off.colored")));
                    cleaned.add(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.mode.none.desc.colored"));
                } else {
                    cleaned.add(statusPrefix.copy().append(KineticI18n.translatable(outputUseNbt ? "gui.contentstudio.recipe.recipehud.mode.on.colored" : "gui.contentstudio.recipe.recipehud.mode.off.colored")));
                    cleaned.add(KineticI18n.translatable(outputUseNbt ? "gui.contentstudio.recipe.recipehud.mode.on.desc.colored" : "gui.contentstudio.recipe.recipehud.mode.off.desc.colored"));
                    cleaned.add(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.tooltip.rclick_toggle.colored"));
                }
            }
            cleaned.add(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.tooltip.shift_edit_nbt.colored"));
            cleaned.add(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.tooltip.lclick_remove.slot.colored"));
            return cleaned;
        }
        return null;
    }

    private void handleSave() {
        if (containsInvalidPlaceholder()) {
            showToast("gui.contentstudio.recipe.recipehud.err.invalid_placeholder");
            return;
        }
        ItemStack originalOut = menu().outputContainer.getItem(0);
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
        int inputCount = (menu().type == RecipeRegistry.EditorType.CRAFTING) ?
                9 : ((menu().type == RecipeRegistry.EditorType.SMITHING) ? 3 : 1);
        for (int i = 0; i < inputCount; i++) {
            inputs.add(menu().inputContainer.getItem(i));
            modes.add(inputNbtModes[i]);
        }
        if (menu().type != RecipeRegistry.EditorType.CRAFTING || isShapeless) {
            if (inputs.stream().allMatch(ItemStack::isEmpty)) {
                showToast("gui.contentstudio.recipe.recipehud.err.input_empty");
                return;
            }
        }
        String uuidToSend = menu().editUuid == null ? "" : menu().editUuid;
        RecipeNetwork.sendRecipeChange(new RecipeChangePacket(uuidToSend, menu().editConfigIndex, menu().type.name(), isShapeless, modes, menu().type != RecipeRegistry.EditorType.SMITHING && outputUseNbt, 0, inputs, outStack));
        RecipeEditSessionState.markPendingRecipeApply();
        draftCountText = countInput.textValue();
        commitDraft();
        showToast("gui.contentstudio.recipe.recipehud.msg.saving_only");
    }

    private void returnFromRecipeEditor() {
        close();
    }

    @Override
    protected boolean onCloseRequested() {
        RecipeEditSessionState.applyPendingAndClear();
        if (RecipePreviewState.returnToPreview && KineticClientRuntime.localPlayer() != null) {
            RecipeNetwork.requestRecipeRecords();
        }
        return false;
    }

    @Override
    protected void renderContainerBackground(KineticGraphics g, int mouseX, int mouseY, float partialTick) {
        KineticTexture t = switch (menu().type) {
            case CRAFTING -> CRAFTING_BG;
            case SMITHING -> SMITHING_BG;
            case STONECUTTER -> STONECUTTER_BG;
            default -> FURNACE_BG;
        };
        g.texture(t, leftPos(), topPos(), 0, 0, imageWidth(), imageHeight());
        int countInputFrameWidth = COUNT_INPUT_WIDTH + COUNT_INPUT_FRAME_PADDING * 2;
        int countInputFrameX = boxX + (OUTPUT_SLOT_SIZE - countInputFrameWidth) / 2;
        KineticTheme.panelAlt(g, countInputFrameX, boxY, countInputFrameWidth, 12);
    }

    /**
     * 原 requestContainerTooltips 为空、提示在 renderScreenOverlay 中按屏幕坐标请求；此处在提示阶段按页面坐标显示同样内容。
     * The old requestContainerTooltips was empty and tooltips were requested in renderScreenOverlay at screen
     * coordinates; the same content is shown here in the tooltip pass.
     */
    @Override
    protected void renderTooltips(int mouseX, int mouseY) {
        if (saveButton != null && saveButton.contains(mouseX, mouseY)) {
            showTooltip(List.of(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.tooltip.save_deferred")));
            return;
        }

        if (countInput != null && countInput.contains(mouseX, mouseY)) {
            showTooltip(List.of(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.tooltip.count_input")));
            return;
        }

        Slot hoveredSlot = hoveredSlot();
        if (hoveredSlot == null || !menu().getCarried().isEmpty()) {
            return;
        }

        if (hoveredSlot.hasItem()) {
            List<Component> custom = editorSlotTooltip(hoveredSlot.getItem(), hoveredSlot);
            if (custom != null) showTooltip(custom);
            else showItemTooltip(hoveredSlot.getItem());
            return;
        }

        if (hoveredSlot.container != menu().inputContainer
                && hoveredSlot.container != menu().outputContainer) {
            return;
        }

        List<Component> tooltip = new ArrayList<>();
        tooltip.add(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.tooltip.empty_slot_title.colored"));
        tooltip.add(KineticI18n.translatable("gui.contentstudio.recipe.recipehud.tooltip.lclick_search.colored"));
        showTooltip(tooltip);
    }
}
