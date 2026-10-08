# Native Recipe Fields Implementation Plan

> Execute inline with superpowers:executing-plans; user has authorized continued implementation and publication.

**Goal:** Edit installed third-party datapack recipes without losing their native fields or replacing current workstation GUIs.
**Architecture:** Original JSON catalog → bounded structural document → server serializer validation → separate atomic overrides → existing late reload pipeline.
**Tech Stack:** Gson, current Kinetic page/network APIs, Stonecutter, installed Forge/NeoForge recipe serializers.
**Spec:** ../specs/2026-10-09-native-recipe-fields-design.md

## Global Constraints

- Forge 1.20.1 Java 17; NeoForge 1.21.1 Java 21; NeoForge 26.1.2 Java 25.
- Preserve native workstation layouts and existing script-safe removal behavior.
- Fixed controls, scrolling text, at least 2 px spacing; identical English/Chinese key sets.
- No copied ViScriptRecipe implementation, new runtime dependency, game download or interference with user processes.

## Review Focus

- Unknown nested fields and number precision survive edits and save/load.
- Inactive conditions or unavailable serializers must not produce an apparently successful save.
- Stale drafts and copy-ID collisions must not replace another edit.
- Array deletion, object keys containing slashes and invalid primitive input must remain safe.
- Invalid overrides must not prevent valid overrides or existing editor recipes from loading.

### Task 1: Lossless bounded document and persistence

Files: new `recipe/nativeedit/NativeRecipeDocument.java`, `NativeRecipeStore.java`; tests `recipe/nativeedit/NativeRecipeDocumentTest.java`.
Interface: document `at(List<String>)`, `setPrimitive(List<String>, String)`, `duplicate/remove(List<String>)`, `json()`; store load/save ID→JSON with revision checks.
- [x] Write/run failing tests for field preservation, numeric validation, array mutation, escaped keys and bounded depth.
- [x] Implement document and atomic store; run tests to green.

### Task 2: Catalog, decode and late apply

Files: `OriginalRecipeCatalog`, `RecipeMemoryManager`, `RecipeModule`; new native network/service classes.
Interface: original catalog `sources()`; memory `decodeNative(manager,id,json)`; paged search/import/save/restore packets with permission and revision checks.
- [x] Keep defensively copied original JSON alongside existing catalog entries.
- [x] Validate and late-apply native overrides using each node's installed serializers and conditions.
- [x] Test real recipes, stale writes and invalid data in runtime fixtures.

### Task 3: Kinetic field browser

Files: new `NativeRecipeBrowserPage`, `NativeRecipeFieldsPage`; modify hub; both language JSONs.
- [x] Add fixed text-row search list and nested typed controls/item selectors; copy/new ID and restore actions.
- [x] Keep existing workstation controls and backgrounds; run all builds and inspect EN/ZH captures.

### Task 4: Delivery

Files: CHANGELOG, README links and nested Wiki language pages.
- [x] Verify actual three-version runtime behavior, artifact bytecode and language parity.
- [x] Build to D:/NEWMODS, deploy installed profiles and clean old ContentStudio artifacts.
Delivery procedure: commit/push source and Wiki using XYAT with separate EN/CN descriptions, then verify the exact-SHA GitHub Actions run and release assets. Publication status is recorded in Git history and Actions.

### Task 3b: Independent workstation preview editor

- [x] Pin cutting/tool/chance-output, shaped-key and cooking-container geometry in pure document tests.
- [x] Discover station icons, filter recipes by serializer and open a clickable Kinetic recipe diagram before advanced fields.
- [x] Render native mod textures without linking JEI; edit selected slot fields and preserve all unedited JSON.
- [x] Exercise all three nodes with JEI absent, including EN/ZH and long-label captures.

### Task 5: Per-mod native recipe validation

- [x] Inventory the reference mod families and local version/loader dependencies; query official compatible mod releases without downloading Minecraft.
- [x] Build an opt-in fixture that changes native serializer-visible fields through real save/reload/restore packets and writes per-type results.
- [x] Run Forge 1.20.1, NeoForge 1.21.1 and NeoForge 26.1.2 independently, resolving loader/required-library issues before counting results.
- [x] Review missing/unverified types and distinguish unavailable releases, dynamic recipes and actual editor failures.
- [x] Inspect shared fixed card sizes, native diagrams and selected-slot controls in both languages and window sizes.
- [x] Prepare an accurate per-mod/per-version evidence matrix for publication alongside the delivery report; keep validation details out of CHANGELOG.
