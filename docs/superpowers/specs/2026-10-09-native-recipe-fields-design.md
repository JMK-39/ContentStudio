# Native third-party recipe fields

Extend ContentStudio's current Kinetic API editor to discover and edit installed mods' datapack recipes on Forge 1.20.1, NeoForge 1.21.1 and NeoForge 26.1.2. Keep the existing six native workstation pages and late override pipeline unchanged.

The reference is ViScriptRecipe's master (1.21.1) and 1.20.1 branches. Borrow the separation of discovery, import and validation, without copying its implementation or adopting LDLib. Import the original JSON captured before script listeners, preserving every unknown field, nested ingredient, chance, condition and version-specific NBT/component representation. Recipes created only by scripts or synthetic viewer displays are outside this importer.

Add a fixed-width Kinetic page beside the original hub actions. Search server recipe IDs and serializer IDs with paged text rows. Open a recipe as nested objects and arrays, editing primitive values through typed fields, booleans through toggles and item/tag values through existing selectors. Duplicate array members and remove them; copy a complete recipe to a new ID as the starting point for additions. No mandatory raw JSON editing and no new optional mod dependency.

Store these lossless recipes separately from the old recipe bundle. Validate a complete draft with the installed serializer and conditions on the server before writing atomically. Apply saved overrides after all baseline recipes and existing ContentStudio recipes. Saving an existing ID replaces it; copying requires an unused ID. Restoring removes the override and reloads the original datapacks/scripts. Permissions and optimistic revision checks reject unauthorized or stale writes. Invalid drafts never change the saved file.

All three versions share the same fixed layout, scrolling labels and 2 px minimum spacing. The original recipe backgrounds and inventory remain native. English/Chinese keys must match. Java release levels remain 17/21/25. CHANGELOG describes player-facing changes only; detailed bilingual documentation lives in the ignored nested Wiki.

Acceptance: structural editing preserves unedited JSON; registered third-party serializers validate and reload real recipes; unsupported/inactive recipes report a reason; concurrent edits cannot overwrite one another silently; all enabled builds and language checks pass; actual JARs reach D:/NEWMODS and installed profiles; source and Wiki are pushed and exact-commit CI verified.

## Workstation preview refinement

The primary entry is a discovered workstation icon, followed by that workstation's recipes. The primary editor is a Kinetic-rendered, clickable recipe diagram, independent of JEI. Match familiar query layouts and use the installed mod's own recipe textures where an adapter is available. Start with Farmer's Delight cutting board/cooking pot and native crafting/cooking layouts. Unknown serializers retain a clearly labelled generic diagram plus complete typed fields, without claiming a universal exact layout. Selecting a slot exposes its own fields and item/tag picker; advanced fields retain unknown data. JEI remains an optional query integration only. Preserve the old six editors, fixed controls, complete icons and 2 px spacing across all versions.

## Mod navigation and runtime coverage

The hub includes mod-name/ID search and whole-row scrolling. Vanilla remains pinned above the mod grid and opens the original six workstation actions. Create opens processing/assembly groups before individual serializers. Every mod, group and workstation card uses the same fixed width and height, independent of translated text length.

Validate every reference mod with an available compatible dependency, separately for each Minecraft version. Prefer installed local JARs; download only official mod dependencies and reuse the installed game/loader. Use a disposable copy of the existing profile/save, mute sound and disable tutorials. For every installed serializer with an editable native recipe: change a real field, save through the actual network path, reload, compare the live serializer-visible data, reopen and compare saved JSON, restore and verify the original live data. Record exact versions, recipe IDs, changed fields and results. A mod loading successfully, a recipe appearing in the list, or a successful build does not count as an editing pass. Missing versions, dynamic recipes without editable fields and unverified cases remain explicitly separate. These checks do not claim that every physical machine's automation cycle was exercised.
