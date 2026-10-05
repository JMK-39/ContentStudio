package dev.xyat.contentstudio.recipe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
//? if >=1.21 {
/*import dev.xyat.contentstudio.item.ItemData;
*///?}

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RecipeRecord {
    public String uuid;
    public String editorType;
    public boolean isShapeless;
    public boolean outputUseNbt;
    public ItemStack output;
    public List<ItemStack> inputs;
    public List<Integer> inputModes;
    public String comment;
    public boolean invalidConfig;
    public int configIndex;
    public String invalidReason;

    public RecipeRecord() {
        this.uuid = UUID.randomUUID().toString();
        this.inputs = new ArrayList<>();
        this.inputModes = new ArrayList<>();
        this.comment = "";
        this.invalidConfig = false;
        this.configIndex = -1;
        this.invalidReason = "";
    }

    public CompoundTag saveToNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("uuid", uuid == null ? "" : uuid);
        tag.putString("editorType", editorType == null || editorType.isBlank() ? "CRAFTING" : editorType);
        tag.putBoolean("isShapeless", isShapeless);
        tag.putBoolean("outputUseNbt", outputUseNbt);
        ItemStack safeOutput = output == null ? ItemStack.EMPTY : output;
//? if >=1.21 {
/*
        tag.put("output", ItemData.save(safeOutput));
*///?} else {
        tag.put("output", safeOutput.save(new CompoundTag()));
//?}
        tag.putString("comment", comment != null ? comment : "");
        tag.putBoolean("invalidConfig", invalidConfig);
        tag.putInt("configIndex", configIndex);
        tag.putString("invalidReason", invalidReason == null ? "" : invalidReason);

        ListTag inputsList = new ListTag();
        for (int i = 0; i < inputs.size(); i++) {
            CompoundTag slotTag = new CompoundTag();
            ItemStack input = inputs.get(i) == null ? ItemStack.EMPTY : inputs.get(i);
            int mode = i < inputModes.size() ? inputModes.get(i) : 0;
//? if >=1.21 {
/*
            slotTag.put("item", ItemData.save(input));
*///?} else {
            slotTag.put("item", input.save(new CompoundTag()));
//?}
            slotTag.putInt("mode", mode);
            inputsList.add(slotTag);
        }
        tag.put("inputs", inputsList);
        return tag;
    }

    public static RecipeRecord loadFromNBT(CompoundTag tag) {
        RecipeRecord record = new RecipeRecord();
        record.uuid = tag.getString("uuid");
        record.editorType = tag.getString("editorType");
        record.isShapeless = tag.getBoolean("isShapeless");
        // Written out on 26.1: its getter rule and the NeoForge key rename would overlap here.
//? if >=26.1 {
/*        record.outputUseNbt = tag.getBooleanOr("outputUseComponents", false);
*///?} else {
        record.outputUseNbt = tag.getBoolean("outputUseNbt");
//?}
//? if >=1.21 {
/*
        record.output = ItemData.load(tag.getCompound("output"));
*///?} else {
        record.output = ItemStack.of(tag.getCompound("output"));
//?}
        record.comment = tag.getString("comment");
        record.invalidConfig = tag.getBoolean("invalidConfig");
        record.configIndex = tag.contains("configIndex") ? tag.getInt("configIndex") : -1;
        record.invalidReason = tag.getString("invalidReason");

        ListTag list = tag.getList("inputs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag data = list.getCompound(i);
//? if >=1.21 {
/*
            record.inputs.add(ItemData.load(data.getCompound("item")));
*///?} else {
            record.inputs.add(ItemStack.of(data.getCompound("item")));
//?}
            record.inputModes.add(data.getInt("mode"));
        }
        return record;
    }
}
