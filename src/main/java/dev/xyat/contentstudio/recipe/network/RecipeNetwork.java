package dev.xyat.contentstudio.recipe.network;

import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.contentstudio.recipe.RecipeDatabase;
import dev.xyat.contentstudio.recipe.RecipeMemoryManager;
import dev.xyat.contentstudio.recipe.RecipeMenu;
import dev.xyat.contentstudio.recipe.RecipeModule;
import dev.xyat.contentstudio.recipe.RecipeRecord;
import dev.xyat.contentstudio.recipe.RecipeRegistry;
import dev.xyat.contentstudio.recipe.RecipeSaveManager;
import dev.xyat.contentstudio.recipe.UniversalRecipeMenu;
import dev.xyat.contentstudio.recipe.removal.RecipeRemovalManager;
import dev.xyat.contentstudio.recipe.removal.RecipeSummary;
import dev.xyat.contentstudio.recipe.removal.RemovalEntry;
import dev.xyat.kineticcore.api.menu.KineticMenus;
import dev.xyat.kineticcore.api.network.NetworkBuffer;
import dev.xyat.kineticcore.api.network.NetworkCodec;
import dev.xyat.kineticcore.api.network.NetworkVersionPolicy;
import dev.xyat.kineticcore.api.network.PacketChannel;
import dev.xyat.kineticcore.api.network.PacketRegistrations;
import dev.xyat.kineticcore.api.network.ServerPacketContext;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RecipeNetwork {
    private static final String PROTOCOL_VERSION = "2";
    private static final PacketChannel CHANNEL = PacketChannel.create(
            KineticResourceIds.of(RecipeModule.MODID, "recipe_network"),
            PROTOCOL_VERSION,
            NetworkVersionPolicy.EXACT
    );
    private static final boolean[] PACKET_REGISTERED = new boolean[12];
    private static boolean registered;

    private RecipeNetwork() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        PacketRegistrations.runIndependent(
                () -> registerServerbound(0, ActionPacket.class, (buffer, packet) -> packet.encode(buffer), ActionPacket::new, ActionPacket::handle),
                () -> registerServerbound(1, RequestSyncPacket.class, (buffer, packet) -> packet.encode(buffer), RequestSyncPacket::new, RequestSyncPacket::handle),
                () -> registerClientbound(2, SyncPacket.class, (buffer, packet) -> packet.encode(buffer), SyncPacket::new, SyncPacket::handle),
                () -> registerServerbound(3, RecipeChangePacket.class, (buffer, packet) -> packet.encode(buffer), RecipeChangePacket::new, RecipeChangePacket::handle),
                () -> registerServerbound(4, RequestEditPacket.class, (buffer, packet) -> packet.encode(buffer), RequestEditPacket::new, RequestEditPacket::handle),
                () -> registerServerbound(5, RequestRecipeRecordsPacket.class, (buffer, packet) -> packet.encode(buffer), RequestRecipeRecordsPacket::new, RequestRecipeRecordsPacket::handle),
                () -> registerClientbound(6, RecipeRecordsSyncPacket.class, (buffer, packet) -> packet.encode(buffer), RecipeRecordsSyncPacket::new, RecipeRecordsSyncPacket::handle),
                () -> registerClientbound(7, ToastPacket.class, (buffer, packet) -> packet.encode(buffer), ToastPacket::new, ToastPacket::handle),
                () -> registerServerbound(8, ApplyPendingRecipesPacket.class, (buffer, packet) -> packet.encode(buffer), ApplyPendingRecipesPacket::new, ApplyPendingRecipesPacket::handle),
                () -> registerServerbound(9, RequestOpenHubPacket.class, (buffer, packet) -> packet.encode(buffer), RequestOpenHubPacket::new, RequestOpenHubPacket::handle),
                () -> registerServerbound(10, RequestItemRecipesPacket.class, (buffer, packet) -> packet.encode(buffer), RequestItemRecipesPacket::new, RequestItemRecipesPacket::handle),
                () -> registerClientbound(11, ItemRecipesPacket.class, (buffer, packet) -> packet.encode(buffer), ItemRecipesPacket::new, ItemRecipesPacket::handle),
                () -> registered = allPacketsRegistered()
        );
    }

    private static <T> void registerServerbound(
            int id,
            Class<T> type,
            java.util.function.BiConsumer<NetworkBuffer, T> encoder,
            java.util.function.Function<NetworkBuffer, T> decoder,
            dev.xyat.kineticcore.api.network.ServerboundPacketHandler<T> handler
    ) {
        if (PACKET_REGISTERED[id]) return;
        CHANNEL.registerServerbound(id, type, NetworkCodec.of(encoder, decoder), handler);
        PACKET_REGISTERED[id] = true;
    }

    private static <T> void registerClientbound(
            int id,
            Class<T> type,
            java.util.function.BiConsumer<NetworkBuffer, T> encoder,
            java.util.function.Function<NetworkBuffer, T> decoder,
            java.util.function.Consumer<T> handler
    ) {
        if (PACKET_REGISTERED[id]) return;
        CHANNEL.registerClientbound(id, type, NetworkCodec.of(encoder, decoder), handler);
        PACKET_REGISTERED[id] = true;
    }

    private static boolean allPacketsRegistered() {
        for (boolean value : PACKET_REGISTERED) {
            if (!value) return false;
        }
        return true;
    }

    public record RequestItemRecipesPacket(ItemStack item) {
        public RequestItemRecipesPacket(NetworkBuffer buffer) {
            this(buffer.readItemStack());
        }

        public void encode(NetworkBuffer buffer) {
            buffer.writeItemStack(item);
        }

        public static void handle(RequestItemRecipesPacket packet, ServerPacketContext context) {
            ServerPlayer player = context.sender();
            if (!player.hasPermissions(2) || packet.item.isEmpty()) return;
            List<RecipeSummary> recipes = new ArrayList<>();
            boolean dataError = false;
            for (var recipe : RecipeMemoryManager.recipeCatalog(player.server.getRecipeManager())) {
                try {
                    if (recipe.getResultItem(player.level().registryAccess()).is(packet.item.getItem())) {
                        try {
                            recipes.add(RecipeSummary.of(recipe, player.level().registryAccess()));
                        } catch (RuntimeException exception) {
                            dataError = true;
                        }
                    }
                } catch (RuntimeException ignored) {
                }
            }
            CHANNEL.sendToPlayer(player, new ItemRecipesPacket(packet.item, recipes, dataError));
        }
    }

    public record ItemRecipesPacket(ItemStack item, List<RecipeSummary> recipes, boolean dataError) {
        public ItemRecipesPacket(NetworkBuffer buffer) {
            this(buffer.readItemStack(), buffer.readList(RecipeSummary::decode), buffer.readBoolean());
        }

        public void encode(NetworkBuffer buffer) {
            buffer.writeItemStack(item);
            buffer.writeList(recipes, (target, recipe) -> recipe.encode(target));
            buffer.writeBoolean(dataError);
        }

        public static void handle(ItemRecipesPacket packet) {
            RecipeNetworkClient.handleItemRecipes(packet);
        }
    }

    public record RequestOpenHubPacket() {
        public RequestOpenHubPacket(NetworkBuffer buffer) {
            this();
        }

        public void encode(NetworkBuffer buffer) {
        }

        public static void handle(RequestOpenHubPacket packet, ServerPacketContext context) {
            ServerPlayer player = context.sender();
            if (!player.hasPermissions(2)) return;
            RecipeDatabase.loadDatabase();
            KineticMenus.open(
                    player,
                    Component.translatable("gui.contentstudio.recipe.recipehud.title"),
                    (id, inventory, menuPlayer) -> new RecipeMenu(id, inventory)
            );
        }
    }

    public record ToastPacket(Component message) {
        public ToastPacket(NetworkBuffer buffer) {
            this(buffer.readComponent());
        }

        public void encode(NetworkBuffer buffer) {
            buffer.writeComponent(message);
        }

        public static void handle(ToastPacket packet) {
            RecipeNetworkClient.handleToast(packet);
        }
    }

    public record RequestEditPacket(String uuid, String editorType, int configIndex) {
        public RequestEditPacket(NetworkBuffer buffer) {
            this(buffer.readUtf(), buffer.readUtf(), buffer.readInt());
        }

        public void encode(NetworkBuffer buffer) {
            buffer.writeUtf(uuid == null ? "" : uuid);
            buffer.writeUtf(editorType == null ? "" : editorType);
            buffer.writeInt(configIndex);
        }

        public static void handle(RequestEditPacket packet, ServerPacketContext context) {
            ServerPlayer player = context.sender();
            if (!player.hasPermissions(2)) {
                return;
            }
            if (packet.configIndex < 0 && !packet.uuid.isEmpty() && isInvalidUuid(packet.uuid)) {
                sendToast(player, Component.translatable("gui.contentstudio.recipe.recipehud.err.invalid_data"));
                return;
            }
            RecipeDatabase.reloadDatabase();

            RecipeRecord record = null;
            if (packet.configIndex >= 0) {
                record = RecipeDatabase.editorSnapshot().stream()
                        .filter(value -> value.configIndex == packet.configIndex)
                        .findFirst()
                        .orElse(null);
            } else if (!packet.uuid.isEmpty()) {
                record = RecipeDatabase.records.stream()
                        .filter(value -> value.uuid != null && value.uuid.equals(packet.uuid))
                        .findFirst()
                        .orElse(null);
            }

            if (packet.configIndex >= 0 && record == null) {
                sendToast(player, Component.translatable("gui.contentstudio.recipe.recipehud.err.invalid_data"));
                return;
            }

            String resolvedType = record == null ? packet.editorType : record.editorType;
            RecipeRegistry.EditorType type;
            try {
                type = RecipeRegistry.EditorType.valueOf(resolvedType);
            } catch (IllegalArgumentException exception) {
                sendToast(
                        player,
                        Component.translatable("msg.contentstudio.recipe.recipehud.invalid_type", resolvedType)
                );
                return;
            }

            RecipeRecord finalRecord = record;
            KineticMenus.open(
                    player,
                    type.getTitle(),
                    (id, inventory, menuPlayer) -> new UniversalRecipeMenu(id, inventory, type, finalRecord),
                    buffer -> {
                        buffer.writeUtf(type.name());
                        buffer.writeBoolean(finalRecord != null);
                        if (finalRecord != null) {
                            buffer.writeNbt(finalRecord.saveToNBT());
                            buffer.writeUtf(finalRecord.uuid == null ? "" : finalRecord.uuid);
                            buffer.writeInt(finalRecord.configIndex);
                        }
                    }
            );
        }
    }

    public record RecipeChangePacket(
            String uuid,
            int configIndex,
            String editorType,
            boolean isShapeless,
            List<Integer> inputNbtModes,
            boolean outputUseNbt,
            int action,
            List<ItemStack> inputs,
            ItemStack output
    ) {
        public RecipeChangePacket(NetworkBuffer buffer) {
            this(
                    buffer.readUtf(),
                    buffer.readInt(),
                    buffer.readUtf(),
                    buffer.readBoolean(),
                    readIntList(buffer),
                    buffer.readBoolean(),
                    buffer.readInt(),
                    readItemList(buffer),
                    buffer.readItemStack()
            );
        }

        public void encode(NetworkBuffer buffer) {
            buffer.writeUtf(uuid == null ? "" : uuid);
            buffer.writeInt(configIndex);
            buffer.writeUtf(editorType);
            buffer.writeBoolean(isShapeless);
            buffer.writeInt(inputNbtModes.size());
            for (int mode : inputNbtModes) {
                buffer.writeInt(mode);
            }
            buffer.writeBoolean(outputUseNbt);
            buffer.writeInt(action);
            buffer.writeInt(inputs.size());
            for (ItemStack stack : inputs) {
                buffer.writeItemStack(stack);
            }
            buffer.writeItemStack(output);
        }

        public static void handle(RecipeChangePacket packet, ServerPacketContext context) {
            ServerPlayer player = context.sender();
            if (!player.hasPermissions(2)) {
                return;
            }
            if (packet.action != 0 && packet.action != 1) {
                sendToast(player, Component.translatable("gui.contentstudio.recipe.recipehud.err.invalid_data"));
                return;
            }
            if (packet.action == 1) {
                if (packet.configIndex < 0 && isInvalidUuid(packet.uuid)) {
                    sendToast(player, Component.translatable("gui.contentstudio.recipe.recipehud.err.invalid_data"));
                    return;
                }
                RecipeSaveManager.deleteOnly(player, packet.uuid, packet.configIndex);
                return;
            }
            if (isInvalidRecipeChange(packet)) {
                sendToast(player, Component.translatable("gui.contentstudio.recipe.recipehud.err.invalid_data"));
                return;
            }
            if (packet.inputs.stream().allMatch(ItemStack::isEmpty)) {
                sendToast(player, Component.translatable("gui.contentstudio.recipe.recipehud.err.input_empty"));
                return;
            }
            if (packet.output.isEmpty()) {
                sendToast(player, Component.translatable("gui.contentstudio.recipe.recipehud.err.output_empty"));
                return;
            }
            RecipeSaveManager.saveOnly(player, packet);
        }
    }

    public record ActionPacket(int action, RemovalEntry entry) {
        public ActionPacket(NetworkBuffer buffer) {
            this(buffer.readInt(), buffer.readBoolean() ? RemovalEntry.fromNetwork(buffer) : null);
        }

        public void encode(NetworkBuffer buffer) {
            buffer.writeInt(action);
            buffer.writeBoolean(entry != null);
            if (entry != null) {
                entry.toNetwork(buffer);
            }
        }

        public static void handle(ActionPacket packet, ServerPacketContext context) {
            ServerPlayer player = context.sender();
            if (!player.hasPermissions(2)) return;
            if (packet.action == 0 && isValidRemovalEntry(packet.entry)) {
                RecipeRemovalManager.addEntry(packet.entry);
            } else if (packet.action == 1 && isValidRemovalEntry(packet.entry)) {
                RecipeRemovalManager.removeEntry(packet.entry);
            } else if (packet.action == 2 && packet.entry == null) {
                RecipeRemovalManager.saveAndApply(player);
            } else {
                sendToast(player, Component.translatable("gui.contentstudio.recipe.recipehud.err.invalid_data"));
            }
        }
    }

    public record ApplyPendingRecipesPacket() {
        public ApplyPendingRecipesPacket(NetworkBuffer buffer) {
            this();
        }

        public void encode(NetworkBuffer buffer) {
        }

        public static void handle(ApplyPendingRecipesPacket packet, ServerPacketContext context) {
            ServerPlayer player = context.sender();
            if (player.hasPermissions(2)) {
                RecipeSaveManager.applyPending(player);
            }
        }
    }

    public record RequestSyncPacket() {
        public RequestSyncPacket(NetworkBuffer buffer) {
            this();
        }

        public void encode(NetworkBuffer buffer) {
        }

        public static void handle(RequestSyncPacket packet, ServerPacketContext context) {
            ServerPlayer player = context.sender();
            if (player.hasPermissions(2)) {
                RecipeRemovalManager.syncToPlayer(player);
            }
        }
    }

    public record SyncPacket(List<RemovalEntry> entries, List<ItemStack> modifiedItems) {
        public SyncPacket(NetworkBuffer buffer) {
            this(readEntries(buffer), buffer.readList(NetworkBuffer::readItemStack));
        }

        public void encode(NetworkBuffer buffer) {
            buffer.writeInt(entries.size());
            for (RemovalEntry entry : entries) {
                entry.toNetwork(buffer);
            }
            buffer.writeList(modifiedItems, (target, stack) -> target.writeItemStack(stack));
        }

        public static void handle(SyncPacket packet) {
            RecipeNetworkClient.handleSync(packet);
        }
    }

    public record RequestRecipeRecordsPacket() {
        public RequestRecipeRecordsPacket(NetworkBuffer buffer) {
            this();
        }

        public void encode(NetworkBuffer buffer) {
        }

        public static void handle(RequestRecipeRecordsPacket packet, ServerPacketContext context) {
            ServerPlayer player = context.sender();
            if (player.hasPermissions(2)) {
                syncRecipeRecords(player);
            }
        }
    }

    public record RecipeRecordsSyncPacket(List<RecipeRecord> records) {
        public RecipeRecordsSyncPacket(NetworkBuffer buffer) {
            this(readRecords(buffer));
        }

        public void encode(NetworkBuffer buffer) {
            buffer.writeInt(records.size());
            for (RecipeRecord record : records) {
                buffer.writeNbt(record.saveToNBT());
            }
        }

        public static void handle(RecipeRecordsSyncPacket packet) {
            RecipeNetworkClient.handleRecipeRecords(packet);
        }
    }

    private static List<Integer> readIntList(NetworkBuffer buffer) {
        int size = readBoundedSize(buffer, 64);
        List<Integer> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(buffer.readInt());
        }
        return list;
    }

    private static List<ItemStack> readItemList(NetworkBuffer buffer) {
        int size = readBoundedSize(buffer, 64);
        List<ItemStack> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(buffer.readItemStack());
        }
        return list;
    }

    private static List<RemovalEntry> readEntries(NetworkBuffer buffer) {
        int size = buffer.readInt();
        if (size < 0) {
            throw new IllegalArgumentException("invalid list size");
        }
        List<RemovalEntry> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(RemovalEntry.fromNetwork(buffer));
        }
        return list;
    }

    private static List<RecipeRecord> readRecords(NetworkBuffer buffer) {
        int size = buffer.readInt();
        if (size < 0) {
            throw new IllegalArgumentException("invalid list size");
        }
        List<RecipeRecord> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            CompoundTag tag = buffer.readNbt();
            if (tag != null) {
                try {
                    RecipeRecord record = RecipeRecord.loadFromNBT(tag);
                    if (record.output != null && !record.output.isEmpty()) {
                        list.add(record);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return list;
    }

    private static int readBoundedSize(NetworkBuffer buffer, int maximum) {
        int size = buffer.readInt();
        if (size < 0 || size > maximum) {
            throw new IllegalArgumentException("invalid list size");
        }
        return size;
    }

    private static boolean isInvalidUuid(String value) {
        if (value == null || value.isBlank()) return true;
        try {
            UUID.fromString(value);
            return false;
        } catch (IllegalArgumentException exception) {
            return true;
        }
    }

    private static boolean isInvalidRecipeChange(RecipeChangePacket packet) {
        if (packet == null || packet.editorType() == null || packet.inputs() == null
                || packet.inputNbtModes() == null || packet.output() == null) {
            return true;
        }
        if (packet.uuid() != null && !packet.uuid().isEmpty() && isInvalidUuid(packet.uuid())) {
            return true;
        }

        RecipeRegistry.EditorType type;
        try {
            type = RecipeRegistry.EditorType.valueOf(packet.editorType());
        } catch (IllegalArgumentException exception) {
            return true;
        }

        int required = switch (type) {
            case CRAFTING -> 9;
            case SMITHING -> 3;
            default -> 1;
        };
        if (packet.inputs().size() != required || packet.inputNbtModes().size() != required) {
            return true;
        }
        if (type != RecipeRegistry.EditorType.CRAFTING && packet.isShapeless()) {
            return true;
        }
        if (type == RecipeRegistry.EditorType.SMITHING && packet.outputUseNbt()) {
            return true;
        }
        for (Integer mode : packet.inputNbtModes()) {
            if (mode == null || mode < 0 || mode > 2) return true;
        }
        for (ItemStack stack : packet.inputs()) {
            if (stack == null || isInvalidPlaceholder(stack)) return true;
            if (!stack.isEmpty() && KineticRegistries.items().id(stack.getItem()) == null) return true;
        }
        if (isInvalidPlaceholder(packet.output()) || packet.output().isEmpty()
                || KineticRegistries.items().id(packet.output().getItem()) == null
                || packet.output().getCount() < 1 || packet.output().getCount() > 64) {
            return true;
        }
        if (type == RecipeRegistry.EditorType.SMITHING) {
            return packet.inputs().stream().anyMatch(ItemStack::isEmpty);
        }
        if (type != RecipeRegistry.EditorType.CRAFTING) {
            return packet.inputs().get(0).isEmpty();
        }
        return packet.inputs().stream().allMatch(ItemStack::isEmpty);
    }

    private static boolean isInvalidPlaceholder(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.getTag() != null
                && stack.getTag().getBoolean("contentstudio_invalid_placeholder");
    }

    private static boolean isValidRemovalEntry(RemovalEntry entry) {
        if (entry == null || entry.mode() == null || entry.value() == null || entry.value().isBlank()) {
            return false;
        }
        String value = entry.value().trim();
        return switch (entry.mode()) {
            case MOD -> {
                ResourceLocation probe = KineticResourceIds.tryParse(value + ":entry");
                yield probe != null && probe.getNamespace().equals(value);
            }
            case OUTPUT -> {
                ResourceLocation id = KineticResourceIds.tryParse(value);
                yield id != null && KineticRegistries.items().contains(id);
            }
            case TYPE -> {
                ResourceLocation id = KineticResourceIds.tryParse(value);
                yield id != null && KineticRegistries.recipeTypes().contains(id);
            }
            case RECIPE_ID, TAG -> KineticResourceIds.tryParse(value) != null;
        };
    }

    public static void sendToast(ServerPlayer player, Component message) {
        CHANNEL.sendToPlayer(player, new ToastPacket(message));
    }

    public static void sendSyncToPlayer(ServerPlayer player, List<RemovalEntry> entries, List<ItemStack> modifiedItems) {
        CHANNEL.sendToPlayer(player, new SyncPacket(entries, modifiedItems));
    }

    public static void sendAdd(RemovalEntry entry) {
        CHANNEL.sendToServer(new ActionPacket(0, entry));
    }

    public static void sendRemove(RemovalEntry entry) {
        CHANNEL.sendToServer(new ActionPacket(1, entry));
    }

    public static void sendSaveRemovalRequest() {
        CHANNEL.sendToServer(new ActionPacket(2, null));
    }

    public static void requestOpenHub() {
        CHANNEL.sendToServer(new RequestOpenHubPacket());
    }

    public static void requestItemRecipes(ItemStack item) {
        CHANNEL.sendToServer(new RequestItemRecipesPacket(item));
    }

    public static void requestOpen() {
        CHANNEL.sendToServer(new RequestSyncPacket());
    }

    public static void requestRecipeRecords() {
        CHANNEL.sendToServer(new RequestRecipeRecordsPacket());
    }

    public static void requestEdit(String uuid, String editorType, int configIndex) {
        CHANNEL.sendToServer(new RequestEditPacket(uuid, editorType, configIndex));
    }

    public static void sendRecipeChange(RecipeChangePacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendApplyPendingRecipesRequest() {
        CHANNEL.sendToServer(new ApplyPendingRecipesPacket());
    }

    public static void syncRecipeRecords(ServerPlayer player) {
        RecipeDatabase.reloadDatabase();
        CHANNEL.sendToPlayer(player, new RecipeRecordsSyncPacket(RecipeDatabase.editorSnapshot()));
    }
}
