package dev.xyat.contentstudio.recipe.network;

import dev.xyat.kineticcore.api.event.KineticEventPriority;
import dev.xyat.kineticcore.api.server.event.KineticServerEvents;
import dev.xyat.kineticcore.api.text.KineticI18n;
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
import dev.xyat.contentstudio.recipe.removal.RemovalCandidate;
import dev.xyat.contentstudio.recipe.removal.OriginalRecipeCatalog;
import dev.xyat.contentstudio.recipe.removal.RemovalCatalogPages;
import dev.xyat.contentstudio.recipe.removal.RemovalEntry;
import dev.xyat.contentstudio.recipe.removal.RemovalMode;
import dev.xyat.contentstudio.recipe.removal.RemovalRuleEvaluator;
import dev.xyat.contentstudio.recipe.removal.RemovalStateCodec;
import dev.xyat.contentstudio.recipe.removal.RuleTransfer;
import dev.xyat.kineticcore.api.menu.KineticMenus;
import dev.xyat.kineticcore.api.network.NetworkBuffer;
import dev.xyat.kineticcore.api.network.NetworkBuffers;
import dev.xyat.kineticcore.api.network.NetworkCodec;
import dev.xyat.kineticcore.api.network.NetworkVersionPolicy;
import dev.xyat.kineticcore.api.network.PacketChannel;
import dev.xyat.kineticcore.api.network.PacketRegistrations;
import dev.xyat.kineticcore.api.network.ServerPacketContext;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import net.minecraft.nbt.CompoundTag;
//? if >=1.21 {
/*import dev.xyat.contentstudio.item.ItemData;
*///?}

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class RecipeNetwork {
    private static final String PROTOCOL_VERSION = "3";
    private static final PacketChannel CHANNEL = PacketChannel.create(
            KineticResourceIds.of(RecipeModule.MODID, "recipe_network"),
            PROTOCOL_VERSION,
            NetworkVersionPolicy.EXACT
    );
    private static final boolean[] PACKET_REGISTERED = new boolean[20];
    private static final Map<UUID, PendingDraft> PENDING_DRAFTS = new ConcurrentHashMap<>();
    private static final AtomicLong NEXT_CLIENT_REQUEST = new AtomicLong();

    public enum SaveStatus { SYNC, APPLIED, REJECTED, PERSISTED_RELOAD_FAILED }

    private record PendingDraft(long requestId, RuleTransfer.Assembler assembler) { }
    private static boolean registered;

    private RecipeNetwork() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        PacketRegistrations.runIndependent(
                () -> registerServerbound(1, RequestSyncPacket.class, (buffer, packet) -> packet.encode(buffer), RequestSyncPacket::new, RequestSyncPacket::handle),
                () -> registerServerbound(3, RecipeChangePacket.class, (buffer, packet) -> packet.encode(buffer), RecipeChangePacket::new, RecipeChangePacket::handle),
                () -> registerServerbound(4, RequestEditPacket.class, (buffer, packet) -> packet.encode(buffer), RequestEditPacket::new, RequestEditPacket::handle),
                () -> registerServerbound(5, RequestRecipeRecordsPacket.class, (buffer, packet) -> packet.encode(buffer), RequestRecipeRecordsPacket::new, RequestRecipeRecordsPacket::handle),
                () -> registerClientbound(6, RecipeRecordsSyncPacket.class, (buffer, packet) -> packet.encode(buffer), RecipeRecordsSyncPacket::new, RecipeRecordsSyncPacket::handle),
                () -> registerClientbound(7, ToastPacket.class, (buffer, packet) -> packet.encode(buffer), ToastPacket::new, ToastPacket::handle),
                () -> registerServerbound(8, ApplyPendingRecipesPacket.class, (buffer, packet) -> packet.encode(buffer), ApplyPendingRecipesPacket::new, ApplyPendingRecipesPacket::handle),
                () -> registerServerbound(9, RequestOpenHubPacket.class, (buffer, packet) -> packet.encode(buffer), RequestOpenHubPacket::new, RequestOpenHubPacket::handle),
                () -> registerServerbound(10, RequestItemRecipesPacket.class, (buffer, packet) -> packet.encode(buffer), RequestItemRecipesPacket::new, RequestItemRecipesPacket::handle),
                () -> registerClientbound(11, ItemRecipesPacket.class, (buffer, packet) -> packet.encode(buffer), ItemRecipesPacket::new, ItemRecipesPacket::handle),
                () -> registerServerbound(12, BeginDraftPacket.class, (buffer, packet) -> packet.encode(buffer), BeginDraftPacket::new, BeginDraftPacket::handle),
                () -> registerServerbound(13, DraftChunkPacket.class, (buffer, packet) -> packet.encode(buffer), DraftChunkPacket::new, DraftChunkPacket::handle),
                () -> registerServerbound(14, CommitDraftPacket.class, (buffer, packet) -> packet.encode(buffer), CommitDraftPacket::new, CommitDraftPacket::handle),
                () -> registerClientbound(15, RuleStateBeginPacket.class, (buffer, packet) -> packet.encode(buffer), RuleStateBeginPacket::new, RuleStateBeginPacket::handle),
                () -> registerClientbound(16, RuleStateChunkPacket.class, (buffer, packet) -> packet.encode(buffer), RuleStateChunkPacket::new, RuleStateChunkPacket::handle),
                () -> registerServerbound(17, RequestRuleImpactPagePacket.class, (buffer, packet) -> packet.encode(buffer), RequestRuleImpactPagePacket::new, RequestRuleImpactPagePacket::handle),
                () -> registerClientbound(18, RuleImpactPagePacket.class, (buffer, packet) -> packet.encode(buffer), RuleImpactPagePacket::new, RuleImpactPagePacket::handle),
                () -> registerClientbound(19, RuleStateOutputPagePacket.class, (buffer, packet) -> packet.encode(buffer), RuleStateOutputPagePacket::new, RuleStateOutputPagePacket::handle),
                () -> registered = allPacketsRegistered()
        );
        if (registered) {
            KineticServerEvents.onPlayerLogout(KineticEventPriority.NORMAL, player ->
                    PENDING_DRAFTS.remove(player.getUUID()));
        }
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
        for (int id = 0; id < PACKET_REGISTERED.length; id++) {
            if (id == 0 || id == 2) continue; // Retired mutating action and unbounded rule sync.
            if (!PACKET_REGISTERED[id]) return false;
        }
        return true;
    }

    public record RequestItemRecipesPacket(ItemStack item, long requestId, int page, long expectedVersion) {
        public RequestItemRecipesPacket(NetworkBuffer buffer) {
            this(buffer.readItemStack(), buffer.readLong(), buffer.readInt(), buffer.readLong());
        }

        public void encode(NetworkBuffer buffer) {
            buffer.writeItemStack(item);
            buffer.writeLong(requestId);
            buffer.writeInt(page);
            buffer.writeLong(expectedVersion);
        }

        public static void handle(RequestItemRecipesPacket packet, ServerPacketContext context) {
            ServerPlayer player = context.sender();
            if (!player.hasPermissions(2) || packet.item.isEmpty() || packet.page < 0 || packet.page > 100_000) return;
            long version = RecipeMemoryManager.catalogVersion(player.getServer().getRecipeManager());
            if (packet.expectedVersion != 0 && packet.expectedVersion != version) {
                CHANNEL.sendToPlayer(player, new ItemRecipesPacket(packet.item, packet.requestId,
                        packet.page, 0, version, true, List.of(), false));
                return;
            }
            List<RecipeSummary> recipes = new ArrayList<>();
            boolean dataError = false;
            for (var recipe : RecipeMemoryManager.recipeCatalog(player.getServer().getRecipeManager())) {
                try {
//? if >=26.1 {
/*
                    if (dev.xyat.contentstudio.recipe.RecipeView.output(recipe.value(), player.level().registryAccess()).is(packet.item.getItem())) {
*///?} else if >=1.21 {
/*
                    if (recipe.value().getResultItem(player.level().registryAccess()).is(packet.item.getItem())) {
*///?} else {
                    if (recipe.getResultItem(player.level().registryAccess()).is(packet.item.getItem())) {
//?}
                        try {
                            recipes.add(RecipeSummary.of(recipe, player.level().registryAccess()));
                        } catch (RuntimeException exception) {
                            dataError = true;
                        }
                    }
                } catch (RuntimeException ignored) {
                }
            }
            recipes.sort(Comparator.comparing(recipe -> recipe.id().toString()));
            SummaryPages pages = summaryPages(recipes);
            if (packet.page >= pages.pages().size()) return;
            CHANNEL.sendToPlayer(player, new ItemRecipesPacket(packet.item, packet.requestId,
                    packet.page, pages.pages().size(), version, false, pages.pages().get(packet.page),
                    dataError || pages.dataError()));
        }
    }

    public record ItemRecipesPacket(ItemStack item, long requestId, int page, int totalPages,
                                    long catalogVersion, boolean stale,
                                    List<RecipeSummary> recipes, boolean dataError) {
        public ItemRecipesPacket(NetworkBuffer buffer) {
            this(buffer.readItemStack(), buffer.readLong(), buffer.readInt(), buffer.readInt(),
                    buffer.readLong(), buffer.readBoolean(), readSummaries(buffer), buffer.readBoolean());
        }

        public void encode(NetworkBuffer buffer) {
            buffer.writeItemStack(item);
            buffer.writeLong(requestId);
            buffer.writeInt(page);
            buffer.writeInt(totalPages);
            buffer.writeLong(catalogVersion);
            buffer.writeBoolean(stale);
            buffer.writeVarInt(recipes.size());
            for (RecipeSummary recipe : recipes) recipe.encode(buffer);
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
                    KineticI18n.translatable("gui.contentstudio.recipe.recipehud.title"),
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
                sendToast(player, KineticI18n.translatable("gui.contentstudio.recipe.recipehud.err.invalid_data"));
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
                sendToast(player, KineticI18n.translatable("gui.contentstudio.recipe.recipehud.err.invalid_data"));
                return;
            }

            String resolvedType = record == null ? packet.editorType : record.editorType;
            RecipeRegistry.EditorType type;
            try {
                type = RecipeRegistry.EditorType.valueOf(resolvedType);
            } catch (IllegalArgumentException exception) {
                sendToast(
                        player,
                        KineticI18n.translatable("msg.contentstudio.recipe.recipehud.invalid_type", resolvedType)
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
                sendToast(player, KineticI18n.translatable("gui.contentstudio.recipe.recipehud.err.invalid_data"));
                return;
            }
            if (packet.action == 1) {
                if (packet.configIndex < 0 && isInvalidUuid(packet.uuid)) {
                    sendToast(player, KineticI18n.translatable("gui.contentstudio.recipe.recipehud.err.invalid_data"));
                    return;
                }
                RecipeSaveManager.deleteOnly(player, packet.uuid, packet.configIndex);
                return;
            }
            if (isInvalidRecipeChange(packet)) {
                sendToast(player, KineticI18n.translatable("gui.contentstudio.recipe.recipehud.err.invalid_data"));
                return;
            }
            if (packet.inputs.stream().allMatch(ItemStack::isEmpty)) {
                sendToast(player, KineticI18n.translatable("gui.contentstudio.recipe.recipehud.err.input_empty"));
                return;
            }
            if (packet.output.isEmpty()) {
                sendToast(player, KineticI18n.translatable("gui.contentstudio.recipe.recipehud.err.output_empty"));
                return;
            }
            RecipeSaveManager.saveOnly(player, packet);
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

    public record BeginDraftPacket(long requestId, int totalBytes, int totalChunks) {
        public BeginDraftPacket(NetworkBuffer buffer) {
            this(buffer.readLong(), buffer.readInt(), buffer.readInt());
        }

        public void encode(NetworkBuffer buffer) {
            buffer.writeLong(requestId);
            buffer.writeInt(totalBytes);
            buffer.writeInt(totalChunks);
        }

        public static void handle(BeginDraftPacket packet, ServerPacketContext context) {
            ServerPlayer player = context.sender();
            if (!player.hasPermissions(2)) return;
            try {
                PENDING_DRAFTS.put(player.getUUID(), new PendingDraft(packet.requestId,
                        new RuleTransfer.Assembler(packet.totalBytes, packet.totalChunks)));
            } catch (IllegalArgumentException exception) {
                PENDING_DRAFTS.remove(player.getUUID());
                sendRuleState(player, packet.requestId, SaveStatus.REJECTED, RecipeRemovalManager.snapshot());
            }
        }
    }

    public record DraftChunkPacket(long requestId, int index, byte[] data) {
        public DraftChunkPacket(NetworkBuffer buffer) {
            this(buffer.readLong(), buffer.readInt(), buffer.readByteArray(RuleTransfer.CHUNK_BYTES));
        }

        public void encode(NetworkBuffer buffer) {
            buffer.writeLong(requestId);
            buffer.writeInt(index);
            buffer.writeByteArray(data, RuleTransfer.CHUNK_BYTES);
        }

        public static void handle(DraftChunkPacket packet, ServerPacketContext context) {
            ServerPlayer player = context.sender();
            if (!player.hasPermissions(2)) return;
            PendingDraft pending = PENDING_DRAFTS.get(player.getUUID());
            if (pending == null || pending.requestId != packet.requestId) return;
            try {
                pending.assembler.append(packet.index, packet.data);
            } catch (IllegalArgumentException exception) {
                PENDING_DRAFTS.remove(player.getUUID(), pending);
                sendRuleState(player, packet.requestId, SaveStatus.REJECTED, RecipeRemovalManager.snapshot());
            }
        }
    }

    public record CommitDraftPacket(long requestId) {
        public CommitDraftPacket(NetworkBuffer buffer) { this(buffer.readLong()); }
        public void encode(NetworkBuffer buffer) { buffer.writeLong(requestId); }

        public static void handle(CommitDraftPacket packet, ServerPacketContext context) {
            ServerPlayer player = context.sender();
            if (!player.hasPermissions(2)) return;
            PendingDraft pending = PENDING_DRAFTS.remove(player.getUUID());
            if (pending == null || pending.requestId != packet.requestId) {
                sendRuleState(player, packet.requestId, SaveStatus.REJECTED, RecipeRemovalManager.snapshot());
                return;
            }
            try {
                List<RemovalEntry> rules = RemovalStateCodec.decodeRules(pending.assembler.finish());
                if (rules.stream().anyMatch(RecipeNetwork::isInvalidRemovalEntry)) {
                    throw new IllegalArgumentException("Invalid removal scope");
                }
                RecipeRemovalManager.saveDraftAndApply(player, packet.requestId, rules);
            } catch (RuntimeException exception) {
                sendRuleState(player, packet.requestId, SaveStatus.REJECTED, RecipeRemovalManager.snapshot());
            }
        }
    }

    public record RuleStateBeginPacket(long requestId, SaveStatus status, int totalBytes,
                                       int totalChunks, long catalogVersion) {
        public RuleStateBeginPacket(NetworkBuffer buffer) {
            this(buffer.readLong(), buffer.readEnum(SaveStatus.class), buffer.readInt(),
                    buffer.readInt(), buffer.readLong());
        }

        public void encode(NetworkBuffer buffer) {
            buffer.writeLong(requestId);
            buffer.writeEnum(status);
            buffer.writeInt(totalBytes);
            buffer.writeInt(totalChunks);
            buffer.writeLong(catalogVersion);
        }

        public static void handle(RuleStateBeginPacket packet) {
            RecipeNetworkClient.handleRuleStateBegin(packet);
        }
    }

    public record RuleStateChunkPacket(long requestId, int index, byte[] data) {
        public RuleStateChunkPacket(NetworkBuffer buffer) {
            this(buffer.readLong(), buffer.readInt(), buffer.readByteArray(RuleTransfer.CHUNK_BYTES));
        }

        public void encode(NetworkBuffer buffer) {
            buffer.writeLong(requestId);
            buffer.writeInt(index);
            buffer.writeByteArray(data, RuleTransfer.CHUNK_BYTES);
        }

        public static void handle(RuleStateChunkPacket packet) {
            RecipeNetworkClient.handleRuleStateChunk(packet);
        }
    }

    public record RuleStateOutputPagePacket(long requestId, int page, int totalPages,
                                            List<ResourceLocation> outputIds) {
        public RuleStateOutputPagePacket(NetworkBuffer buffer) {
            this(buffer.readLong(), buffer.readInt(), buffer.readInt(), readOutputIds(buffer));
        }

        public void encode(NetworkBuffer buffer) {
            buffer.writeLong(requestId);
            buffer.writeInt(page);
            buffer.writeInt(totalPages);
            buffer.writeVarInt(outputIds.size());
            outputIds.forEach(buffer::writeResourceLocation);
        }

        public static void handle(RuleStateOutputPagePacket packet) {
            RecipeNetworkClient.handleRuleStateOutputs(packet);
        }
    }

    public record RequestRuleImpactPagePacket(long requestId, RemovalMode mode, String value,
                                               int page, long expectedVersion) {
        public RequestRuleImpactPagePacket(NetworkBuffer buffer) {
            this(buffer.readLong(), buffer.readEnum(RemovalMode.class), buffer.readUtf(256),
                    buffer.readInt(), buffer.readLong());
        }

        public void encode(NetworkBuffer buffer) {
            buffer.writeLong(requestId);
            buffer.writeEnum(mode);
            buffer.writeUtf(value);
            buffer.writeInt(page);
            buffer.writeLong(expectedVersion);
        }

        public static void handle(RequestRuleImpactPagePacket packet, ServerPacketContext context) {
            ServerPlayer player = context.sender();
            if (!player.hasPermissions(2) || packet.page < 0 || packet.page > 100_000
                    || isInvalidRemovalEntry(new RemovalEntry(packet.mode, packet.value, ""))) return;
            long version = RecipeMemoryManager.catalogVersion(player.getServer().getRecipeManager());
            if (packet.expectedVersion != 0L && packet.expectedVersion != version) {
                CHANNEL.sendToPlayer(player, new RuleImpactPagePacket(packet.requestId, packet.mode,
                        packet.value, packet.page, 0, version, true, List.of()));
                return;
            }
            RemovalEntry scope = new RemovalEntry(packet.mode, packet.value, "");
            List<RemovalCandidate> candidates = RecipeMemoryManager.originalCatalog(player.getServer().getRecipeManager())
                    .entries().values().stream().map(OriginalRecipeCatalog.Entry::candidate)
                    .filter(candidate -> RemovalRuleEvaluator.matchesScope(scope, candidate)).toList();
            List<RemovalCatalogPages.Page> pages;
            try {
                pages = RemovalCatalogPages.of(candidates, version, 128);
            } catch (IllegalArgumentException exception) {
                CHANNEL.sendToPlayer(player, new RuleImpactPagePacket(packet.requestId, packet.mode,
                        packet.value, packet.page, 0, version, false, List.of()));
                return;
            }
            if (packet.page >= pages.size()) return;
            var selected = pages.get(packet.page);
            CHANNEL.sendToPlayer(player, new RuleImpactPagePacket(packet.requestId, packet.mode,
                    packet.value, packet.page, pages.size(), version, false, selected.candidates()));
        }
    }

    public record RuleImpactPagePacket(long requestId, RemovalMode mode, String value, int page,
                                       int totalPages, long catalogVersion, boolean stale,
                                       List<RemovalCandidate> candidates) {
        public RuleImpactPagePacket(NetworkBuffer buffer) {
            this(buffer.readLong(), buffer.readEnum(RemovalMode.class), buffer.readUtf(256),
                    buffer.readInt(), buffer.readInt(), buffer.readLong(), buffer.readBoolean(),
                    readCandidates(buffer));
        }

        public void encode(NetworkBuffer buffer) {
            buffer.writeLong(requestId);
            buffer.writeEnum(mode);
            buffer.writeUtf(value);
            buffer.writeInt(page);
            buffer.writeInt(totalPages);
            buffer.writeLong(catalogVersion);
            buffer.writeBoolean(stale);
            buffer.writeVarInt(candidates.size());
            for (RemovalCandidate candidate : candidates) writeCandidate(buffer, candidate);
        }

        public static void handle(RuleImpactPagePacket packet) {
            RecipeNetworkClient.handleRuleImpactPage(packet);
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
        int size = readBoundedSize(buffer);
        List<Integer> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(buffer.readInt());
        }
        return list;
    }

    private static List<RecipeSummary> readSummaries(NetworkBuffer buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > 16) throw new IllegalArgumentException("Invalid summary count");
        List<RecipeSummary> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) result.add(RecipeSummary.decode(buffer));
        return List.copyOf(result);
    }

    private record SummaryPages(List<List<RecipeSummary>> pages, boolean dataError) { }

    private static SummaryPages summaryPages(List<RecipeSummary> recipes) {
        List<List<RecipeSummary>> pages = new ArrayList<>();
        List<RecipeSummary> current = new ArrayList<>();
        int bytes = 0;
        boolean dataError = false;
        for (RecipeSummary recipe : recipes) {
            int cost;
            try {
                cost = NetworkBuffers.encode(recipe::encode).length + 16;
            } catch (RuntimeException exception) {
                dataError = true;
                continue;
            }
            if (cost > 60 * 1024) {
                dataError = true;
                continue;
            }
            if (!current.isEmpty() && (current.size() >= 16 || bytes + cost > 60 * 1024)) {
                pages.add(List.copyOf(current));
                current.clear();
                bytes = 0;
            }
            current.add(recipe);
            bytes += cost;
        }
        if (!current.isEmpty() || pages.isEmpty()) pages.add(List.copyOf(current));
        return new SummaryPages(List.copyOf(pages), dataError);
    }

    private static void writeCandidate(NetworkBuffer buffer, RemovalCandidate candidate) {
        buffer.writeResourceLocation(candidate.id());
        buffer.writeBoolean(candidate.recipeType() != null);
        if (candidate.recipeType() != null) buffer.writeResourceLocation(candidate.recipeType());
        buffer.writeBoolean(candidate.outputItemId() != null);
        if (candidate.outputItemId() != null) buffer.writeResourceLocation(candidate.outputItemId());
        buffer.writeVarInt(candidate.outputTagIds().size());
        candidate.outputTagIds().forEach(buffer::writeResourceLocation);
    }

    private static List<RemovalCandidate> readCandidates(NetworkBuffer buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > 128) throw new IllegalArgumentException("Invalid candidate count");
        List<RemovalCandidate> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ResourceLocation id = buffer.readResourceLocation();
            ResourceLocation type = buffer.readBoolean() ? buffer.readResourceLocation() : null;
            ResourceLocation output = buffer.readBoolean() ? buffer.readResourceLocation() : null;
            int tags = buffer.readVarInt();
            if (tags < 0 || tags > 512) throw new IllegalArgumentException("Invalid output tag count");
            java.util.Set<ResourceLocation> tagIds = new java.util.LinkedHashSet<>();
            for (int j = 0; j < tags; j++) tagIds.add(buffer.readResourceLocation());
            result.add(new RemovalCandidate(id, type, output, tagIds));
        }
        return List.copyOf(result);
    }

    private static List<ResourceLocation> readOutputIds(NetworkBuffer buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > 64) throw new IllegalArgumentException("Invalid output ID count");
        List<ResourceLocation> ids = new ArrayList<>(count);
        for (int index = 0; index < count; index++) ids.add(buffer.readResourceLocation());
        return List.copyOf(ids);
    }

    private static List<ItemStack> readItemList(NetworkBuffer buffer) {
        int size = readBoundedSize(buffer);
        List<ItemStack> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(buffer.readItemStack());
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

    private static int readBoundedSize(NetworkBuffer buffer) {
        int size = buffer.readInt();
        if (size < 0 || size > 64) {
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
//? if >=1.21 {
/*
                && ItemData.customData(stack).getBoolean("contentstudio_invalid_placeholder");
*///?} else {
                && stack.getTag() != null
                && stack.getTag().getBoolean("contentstudio_invalid_placeholder");
//?}
    }

    private static boolean isInvalidRemovalEntry(RemovalEntry entry) {
        if (entry == null || entry.mode() == null || entry.value() == null || entry.value().isBlank()) {
            return true;
        }
        String value = entry.value().trim();
        return switch (entry.mode()) {
            case MOD -> {
                ResourceLocation probe = KineticResourceIds.tryParse(value + ":entry");
                yield probe == null || !probe.getNamespace().equals(value);
            }
            case OUTPUT, TYPE, RECIPE_ID, TAG -> KineticResourceIds.tryParse(value) == null;
        };
    }

    public static void sendToast(ServerPlayer player, Component message) {
        CHANNEL.sendToPlayer(player, new ToastPacket(message));
    }

    public static void sendRuleState(ServerPlayer player, long requestId, SaveStatus status,
                                     List<RemovalEntry> entries) {
        byte[] bytes = RemovalStateCodec.encodeRules(entries);
        List<byte[]> chunks = RuleTransfer.split(bytes);
        long version = RecipeMemoryManager.catalogVersion(player.getServer().getRecipeManager());
        CHANNEL.sendToPlayer(player, new RuleStateBeginPacket(requestId, status, bytes.length, chunks.size(), version));
        for (int index = 0; index < chunks.size(); index++) {
            CHANNEL.sendToPlayer(player, new RuleStateChunkPacket(requestId, index, chunks.get(index)));
        }
        List<ResourceLocation> outputs = RecipeMemoryManager.originalCatalog(player.getServer().getRecipeManager())
                .entries().values().stream()
                .filter(entry -> entry.removed() && entry.candidate().outputItemId() != null)
                .map(entry -> entry.candidate().outputItemId())
                .distinct().sorted(Comparator.comparing(ResourceLocation::toString)).toList();
        int pages = Math.max(1, (outputs.size() + 63) / 64);
        for (int page = 0; page < pages; page++) {
            CHANNEL.sendToPlayer(player, new RuleStateOutputPagePacket(requestId, page, pages,
                    List.copyOf(outputs.subList(page * 64, Math.min(outputs.size(), (page + 1) * 64)))));
        }
    }

    public static long requestRuleImpact(RemovalMode mode, String value, int page, long catalogVersion,
                                         long requestId) {
        long id = requestId == 0L ? NEXT_CLIENT_REQUEST.incrementAndGet() : requestId;
        CHANNEL.sendToServer(new RequestRuleImpactPagePacket(id, mode, value, page, catalogVersion));
        return id;
    }

    public static long sendRemovalDraft(List<RemovalEntry> entries) {
        byte[] bytes = RemovalStateCodec.encodeRules(entries);
        List<byte[]> chunks = RuleTransfer.split(bytes);
        long requestId = NEXT_CLIENT_REQUEST.incrementAndGet();
        CHANNEL.sendToServer(new BeginDraftPacket(requestId, bytes.length, chunks.size()));
        for (int index = 0; index < chunks.size(); index++) {
            CHANNEL.sendToServer(new DraftChunkPacket(requestId, index, chunks.get(index)));
        }
        CHANNEL.sendToServer(new CommitDraftPacket(requestId));
        return requestId;
    }

    public static void requestOpenHub() {
        CHANNEL.sendToServer(new RequestOpenHubPacket());
    }

    public static long requestItemRecipes(ItemStack item) {
        long requestId = NEXT_CLIENT_REQUEST.incrementAndGet();
        requestItemRecipes(item, requestId, 0, 0L);
        return requestId;
    }

    public static void requestItemRecipes(ItemStack item, long requestId, int page, long catalogVersion) {
        CHANNEL.sendToServer(new RequestItemRecipesPacket(item, requestId, page, catalogVersion));
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
