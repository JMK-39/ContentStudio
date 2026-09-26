# Recipe Removal Before Script Writes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make removal rules inspectable and reversible while applying them only to original datapack recipes before KubeJS or CraftTweaker script writes.

**Architecture:** Capture and filter the original recipe JSON map at `RecipeManager.apply` before KubeJS's priority-1100 HEAD mixin; Forge runs CraftTweaker's script reload listener after its recipe manager listener. Keep a server-owned original-recipe catalog for rule evaluation and paged impact previews; the late ContentStudio listener only adds editor recipes. Persist per-rule excluded recipe IDs, submit rule drafts atomically, and reuse one impact editor for bulk entry points. No KubeJS or CraftTweaker API is linked.

**Tech Stack:** Java 17, Minecraft Forge 1.20.1, ForgeGradle 6, Mixin 0.8.5, KineticCore networking/widgets, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-26-recipe-removal-before-kubejs-design.md`

## Global Constraints

- Only recipes present in the original JSON map before KubeJS or CraftTweaker normal recipe scripts run can be removal targets. No script-mod ID or namespace heuristic.
- ContentStudio declares no build or metadata dependency on KubeJS or CraftTweaker and links none of their classes; it must work with neither, either one, or both installed.
- ContentStudio editor recipes are also protected from all removal rules.
- `TYPE` matches parsed `Recipe#getType()`, not the JSON serializer's `type` property.
- Rule exclusions are recipe IDs scoped to one rule. Unchecking an output group excludes its currently listed IDs; future different IDs remain subject to the rule.
- New bulk rules start fully selected. Existing rules show their exclusions. Search never changes hidden selections.
- Existing `recipe_bundle.json` files without `excluded_recipe_ids` remain valid.
- The client and server network protocol is versioned together; server owns validation and applied state.

## File Structure

| Unit | Responsibility |
| --- | --- |
| `recipe/removal/RemovalEntry.java`, new `RemovalCandidate.java`, new `RemovalRuleEvaluator.java` | Rule identity/content, compact original-recipe metadata, shared match/exclusion decisions. |
| `recipe/RecipeConfigStore.java` | Backward-compatible JSON persistence of exclusions and complete rule snapshots. |
| new `recipe/mixin/RecipeManagerOriginalRecipesMixin.java`, new `contentstudio.recipe.mixins.json`, `build.gradle` | Intercept the mutable recipe JSON map before either script mod's recipe writes without linking their classes. |
| `recipe/RecipeMemoryManager.java`, new `recipe/removal/OriginalRecipeCatalog.java`, `recipe/removal/RecipeRemovalManager.java` | Original catalog, condition-aware parsing/filtering, protected editor insertion, authoritative save/reload. |
| `recipe/network/RecipeNetwork.java`, `RecipeNetworkClient.java` | Compact paged impact queries, original-item recipe queries, atomic draft save and result sync. |
| `recipe/client/gui/RecipeRemovalScreen.java`, `RecipeRemovalSelectionScreen.java`, new `RecipeRemovalImpactScreen.java`, new `recipe/removal/RuleImpactDraft.java` | Status/reason display, range picker, grouped impact selection and two explicit commit actions. |
| `recipe/client/gui/RecipeRemovalPreviewScreen.java`, `assets/contentstudio/lang/{zh_cn,en_us}.json`, `README.md` | Protected preview/action state, copy and user documentation. |

## Review Focus

1. KubeJS creates a recipe with a custom namespace or the same ID as a removed original; CraftTweaker creates one through its normal API or recreates an original `crafttweaker:<path>` ID. All remain active after startup and `/reload` (Tasks 2 and 7 integration fixtures).
2. A Forge-conditional or malformed original recipe is not falsely listed or removed when its result cannot be resolved (Task 2 parser tests).
3. Two broad rules match one recipe and only one excludes it: the other still removes it, and the UI says so (Tasks 1 and 5 tests).
4. An excluded ID temporarily disappears with a disabled datapack: saving/reloading preserves the exclusion for its return (Task 1 persistence test).
5. A large all-selected exact-ID operation or interrupted save never partially writes rules or exceeds a single network payload (Task 3 transfer tests).

---

### Task 1: Rule identity, matching, and persistence

**Files:** Modify `src/main/java/dev/xyat/contentstudio/recipe/removal/RemovalEntry.java`, `src/main/java/dev/xyat/contentstudio/recipe/RecipeConfigStore.java`, `build.gradle`; create `src/main/java/dev/xyat/contentstudio/recipe/removal/RemovalCandidate.java`, `RemovalRuleEvaluator.java`, `src/test/java/dev/xyat/contentstudio/recipe/removal/RemovalRuleEvaluatorTest.java`, `RemovalConfigCompatibilityTest.java`.

**Interfaces:** `RemovalEntry.key(): RemovalEntry.Key(mode,value)` separates unique rule identity from full content equality; `excludedRecipeIds(): List<ResourceLocation>` is immutable. `RemovalCandidate(ResourceLocation id, @Nullable ResourceLocation recipeType, @Nullable ResourceLocation outputItemId, Set<ResourceLocation> outputTagIds)` carries unknown fields as null/empty. `RemovalRuleEvaluator.matchesScope(RemovalEntry,RemovalCandidate): boolean`, `matchingRules(RemovalCandidate,List<RemovalEntry>): List<RemovalEntry>`, and `blockingRules(RemovalCandidate,List<RemovalEntry>): List<RemovalEntry>` are the only match implementations used by server and client.

- [ ] Write JUnit tests asserting `minecraft:one` matches an ID rule for `minecraft:one`, MOD `minecraft`, OUTPUT `minecraft:iron_ingot`, TAG `forge:ingots/iron`, and parsed TYPE `minecraft:crafting`; an exclusion removes that rule from `blockingRules`; with two matching broad rules and one exclusion exactly one blocker remains. Assert `excludedRecipeIds()` contains each ID once and rejects mutation; legacy JSON loads an empty exclusion list and a missing `minecraft:old` exclusion survives save/load.
- [ ] Run `./gradlew.bat test --tests "*RemovalRuleEvaluatorTest" --tests "*RemovalConfigCompatibilityTest" --no-daemon`; confirm failures identify missing exclusion/matcher behavior.
- [ ] Add JUnit 5 test dependencies and `useJUnitPlatform()`. Implement the interfaces above; persist `excluded_recipe_ids` as an array of valid resource locations, reject duplicate `(mode,value)` entries at the server validation boundary, and retain the old three-argument `RemovalEntry` constructor for current call sites.
- [ ] Re-run the two targeted tests, then `./gradlew.bat test --no-daemon`; expect all green. Commit the domain and compatibility change.

### Task 2: Filter original recipes before script writes and protect later additions

**Files:** Create `src/main/java/dev/xyat/contentstudio/recipe/mixin/RecipeManagerOriginalRecipesMixin.java`, `src/main/java/dev/xyat/contentstudio/recipe/removal/OriginalRecipeCatalog.java`, `src/main/resources/contentstudio.recipe.mixins.json`, `src/test/java/dev/xyat/contentstudio/recipe/removal/OriginalRecipeFilterTest.java`; modify `build.gradle`, `src/main/java/dev/xyat/contentstudio/recipe/RecipeMemoryManager.java`, `recipe/removal/RecipeRemovalManager.java`; inspect `src/main/resources/META-INF/mods.toml` for unwanted dependencies.

**Interfaces:** `RecipeMemoryManager.beforeScriptRecipes(RecipeManager, Map<ResourceLocation,JsonElement>, ResourceManager): void` snapshots candidates and removes only matching source-map entries. `originalCatalog(RecipeManager): OriginalRecipeCatalog` returns an immutable catalog containing candidates, optional parsed `Recipe<?>`, and the original removal decision. `applySnapshot` inserts editor recipes without evaluating removal rules. The Mixin targets `RecipeManager.apply(Map,ResourceManager,ProfilerFiller)` HEAD with its full method descriptor at priority **1200**, before installed KubeJS priority **1100**. Forge appends CraftTweaker's `ScriptReloadListener` after vanilla listeners, so its script additions are later than this hook. Source: [Forge listener order](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.1/patches/minecraft/net/minecraft/server/ReloadableServerResources.java.patch#L369-L380), [CraftTweaker registration](https://github.com/CraftTweaker/CraftTweaker/blob/1.20.1/Forge/src/main/java/com/blamejared/crafttweaker/impl/event/CTCommonEventHandler.java#L687-L693), [script execution](https://github.com/CraftTweaker/CraftTweaker/blob/1.20.1/Common/src/main/java/com/blamejared/crafttweaker/impl/script/ScriptReloadListener.java#L674-L716).

- [ ] Write tests asserting a rule removes `example:original` from the raw map but keeps it in the catalog; adding a later recipe with that ID still leaves it in the final manager. Assert editor insertion bypasses removal, ID/MOD still match a recipe with no resolvable output, unmet Forge conditions and `_` metadata are absent from candidates, and a JSON serializer `type` different from parsed `Recipe#getType()` never drives TYPE matching.
- [ ] Run `./gradlew.bat test --tests "*OriginalRecipeFilterTest" --no-daemon`; confirm the missing boundary/filter failures.
- [ ] Implement the pre-hook with the actual reload `ResourceManager` and registry/Forge condition context. Modify the mutable `HashMap` supplied by Forge before KubeJS receives it; capture the pre-filter catalog once per reload. If config loading fails, leave the map untouched and report it. Remove the late `removeIf` and stop rebuilding the catalog from the post-script manager. Do not import, reflect on, or declare any build or metadata dependency for KubeJS or CraftTweaker.
- [ ] Re-run targeted tests and `./gradlew.bat compileJava --no-daemon --stacktrace`; expect green. Inspect the built metadata and dependency graph for zero KubeJS/CraftTweaker entries. Verify in isolated Forge runs that the pre-filter executes before KubeJS processing and CraftTweaker script execution, and each script source can recreate a removed original under the same ID. Commit the lifecycle change.

### Task 3: Authoritative catalog queries and atomic rule saving

**Files:** Modify `src/main/java/dev/xyat/contentstudio/recipe/network/RecipeNetwork.java`, `RecipeNetworkClient.java`, `recipe/removal/RecipeRemovalManager.java`, `recipe/RecipeConfigStore.java`; create `src/test/java/dev/xyat/contentstudio/recipe/removal/RemovalSaveTransferTest.java`.

**Interfaces:** Keep per-item full `RecipeSummary` queries but source them only from `originalCatalog`. Add `RequestRuleImpactPage(mode,value,page)` / `RuleImpactPage(page,total,catalogVersion,List<RemovalCandidate>)`, sorted by ID with at most **128** compact candidates per page. Add a complete-draft transfer (`begin`, bounded byte chunks, `commit`) with a per-player request ID; only `commit` writes the validated full snapshot through existing `RecipeConfigStore.writeRootAtomic`, reloads once, and returns `APPLIED`, `REJECTED`, or `PERSISTED_RELOAD_FAILED` plus authoritative rules. Retire the current `ActionPacket` add/remove mutation path for removal rules, so unsaved client edits never alter server state. Bump recipe network protocol from `2` to `3`.

- [ ] Write tests asserting 257 candidates produce pages of 128/128/1 with no repeated ID; a different catalog version rejects the remaining pages; missing chunk, a fifth MiB, duplicate rule key, or invalid ID never changes the saved JSON; a 12,000-ID draft round-trips in chunks of at most 32 KiB and one final write; reload failure after a successful file write returns `PERSISTED_RELOAD_FAILED`.
- [ ] Run `./gradlew.bat test --tests "*RemovalSaveTransferTest" --no-daemon`; confirm the expected failures.
- [ ] Implement bounded transfer chunks (maximum **32 KiB each**, maximum **4 MiB assembled**, one pending request per player), server validation, the existing atomic file writer, one reload, and authoritative result sync. Keep client drafts dirty until `APPLIED`; on `PERSISTED_RELOAD_FAILED`, explain that the file changed but the game has not applied it, and keep the draft for retry.
- [ ] Re-run targeted tests and `./gradlew.bat test --no-daemon`; expect green. Commit networking and saving.

### Task 4: Pure impact-selection model

**Files:** Create `src/main/java/dev/xyat/contentstudio/recipe/removal/RuleImpactDraft.java`, `src/test/java/dev/xyat/contentstudio/recipe/removal/RuleImpactDraftTest.java`.

**Interfaces:** `RuleImpactDraft(rule,candidates)` exposes `selectionForOutput(outputId)`, `toggleOutput(outputId)`, `toggleRecipe(recipeId)`, `selectAll(boolean)`, `selectedRecipeIds()`, and `buildRangeRule()`. It preserves exclusions for IDs absent from the current catalog and never treats a search filter as a selection filter.

- [ ] Write tests for new-rule all-selected default, existing exclusions, an output group moving through all/partial/none, a single recipe toggle, global select/clear with a filtered UI view, and preservation of temporarily absent excluded IDs.
- [ ] Run `./gradlew.bat test --tests "*RuleImpactDraftTest" --no-daemon`; confirm expected failures.
- [ ] Implement the model and its immutable results; make the exact-ID action derive `RECIPE_ID` entries only from `selectedRecipeIds()`, while `buildRangeRule()` stores unselected current IDs as exclusions.
- [ ] Re-run targeted tests and the full test suite; expect green. Commit the selection model.

### Task 5: Make rule causes and recovery visible in the main browser

**Files:** Modify `src/main/java/dev/xyat/contentstudio/recipe/client/gui/RecipeRemovalScreen.java`, `RecipeRemovalPreviewScreen.java`, `RecipeNetworkClient.java`, `src/main/resources/assets/contentstudio/lang/zh_cn.json`, `en_us.json`; create `src/test/java/dev/xyat/contentstudio/recipe/removal/RemovalDisplayStateTest.java`.

**Interfaces:** A pure display-state helper maps each original candidate plus draft rules to `ACTIVE`, `REMOVED`, or `EXCLUDED`, the list of matching rules, and whether another rule still blocks it. The screen only uses server original-catalog entries for removal actions; JEI-only and protected later entries are preview-only even when IDs collide.

- [ ] Write tests asserting a removed `example:original` row shows its `OUTPUT` cause; restoring deletes its exact ID rule and excludes it from each broad rule; if only one of two broad rules excludes it, status remains `REMOVED`; a later same-ID script recipe never becomes an original-catalog action target.
- [ ] Run `./gradlew.bat test --tests "*RemovalDisplayStateTest" --no-daemon`; confirm expected failures.
- [ ] Add visible status/reason count and selected-recipe rule details; enable “恢复这一条” by excluding its ID from every matching broad rule and deleting the exact rule, and “重新移除” by removing its exclusions or adding an ID rule. Add “当前物品相关规则／全部规则”; each rule row shows affected item/recipe/exclusion counts and a way to clear stale exclusion IDs. Distinguish original removal from final active same-ID recipe. Update preview button state and both locales.
- [ ] Re-run targeted tests, full tests, and `./gradlew.bat compileJava --no-daemon --stacktrace`; expect green. Commit the browser change.

### Task 6: Unified range impact editor and right-click type entry

**Files:** Create `src/main/java/dev/xyat/contentstudio/recipe/client/gui/RecipeRemovalImpactScreen.java`; modify `RecipeRemovalSelectionScreen.java`, `RecipeRemovalScreen.java`, `RecipeNetworkClient.java`, both locale JSON files; create `src/test/java/dev/xyat/contentstudio/recipe/removal/RemovalImpactFlowTest.java`.

**Interfaces:** The range picker chooses MOD/OUTPUT/TAG/TYPE and value, then opens one impact screen fed by paged original candidates. The impact screen groups by output, shows tri-state rows and per-recipe expansion, starts fully checked for new rules, and provides “只移除当前勾选的配方” versus “按此范围持续移除”. Item right-click type choice opens this same screen focused on the clicked item; confirmation is disabled until all pages load.

- [ ] Write flow tests asserting a new range has all candidate IDs selected; filtering to one item leaves unseen selections intact; right-click type `minecraft:crafting` on one output still shows a second output; exact-ID confirmation adds only selected ID rules while range confirmation adds one rule with unselected current IDs excluded; another rule still blocking an unchecked recipe is shown.
- [ ] Run `./gradlew.bat test --tests "*RemovalImpactFlowTest" --no-daemon`; confirm expected failures.
- [ ] Implement the shared picker and impact screen, counts, tri-state group rows, explicit future-rule explanation, edit-existing exclusions, and protected/no-original empty state. Clicking an existing rule reopens the impact screen and exposes stale-ID cleanup. Reuse KineticCore widgets; avoid a second rule-matching implementation in UI.
- [ ] Re-run targeted tests, full tests, and a client UI smoke test at 640×360 for long IDs, scrolling, search, and translated labels. Commit the impact editor.

### Task 7: Integration verification and user documentation

**Files:** Modify `README.md`; add focused integration fixture/instructions under `docs/superpowers/verification/2026-09-26-recipe-removal.md`.

**Interfaces:** No new production API. Fixtures name an original recipe, a matching range rule with one exclusion, a KubeJS script recipe with a custom namespace, a CraftTweaker `craftingTable.addShapeless` recipe, same-ID recreations by each, and a ContentStudio editor recipe. For the CraftTweaker same-ID case, make the original datapack ID `crafttweaker:fixture`, since its normal recipe API fixes that namespace. Install script mods only in isolated verification instances; neither belongs in project dependencies.

- [ ] Run `./gradlew.bat test --no-daemon --stacktrace` and `./gradlew.bat build --no-daemon --stacktrace -Poutput_mods_dir=build/release`; record outputs.
- [ ] Run isolated Forge instances with neither script mod, KubeJS alone, CraftTweaker alone, and both. In each, verify startup and `/reload`; verify KubeJS's custom-namespace recipe and both mods' same-ID recreations survive matching removal rules. Also verify original scope removal, current-ID exclusion, future different-ID matching, editor-recipe immunity, save failure recovery, and JEI-only preview behavior. Record observed IDs and log order in the verification document; stop and fix earlier tasks if a check fails.
- [ ] Update README with the two commit modes, per-rule exclusions, the pre-script protection boundary, optional KubeJS/CraftTweaker compatibility, and save/apply timing. Run `git diff --check`, inspect final `git status`, and commit documentation.
