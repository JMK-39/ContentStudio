# ContentStudio multi-version migration verification / 多版本迁移验收

## Scope / 范围

2026-10-03. Branch: codex/multiversion-migration-20261003; original HEAD b9bd8d1. Java 21, Gradle 9.8.0, Stonecutter 0.9.8, ModDevGradle 2.0.148. Enabled Forge 1.20.1 and NeoForge 1.21.1; 26.1.2 is reserved and disabled because the core port is still under repair. No core edits, remote code pushes or Releases.

保存了原有六个修改文件的补丁（.gradle/migration/preexisting.patch）和原 Forge JAR（.gradle/migration/contentstudio-baseline.jar）。沿用已有迁移方案，在用户指定目录的迁移分支操作。

## Implementation / 实现

- Shared loader build scripts, per-version properties, local-first dependency resolution, output naming, wrapper/daemon, architecture/reobf/Mixin checks and CI buildAll.
- Neo uses native item Data Components, separate component editor and terms. Forge retains NBT. Old item NBT syntax is rejected on Neo; no conversion is added.
- Recipes use RecipeHolder and registry-aware codecs; original datapack removal precedes script additions. Staged reload tag context is retained. Native component ingredients preserve explicit removals, partial/exact matching, display stacks and network codecs.
- Loot uses reloadable registries and retains holder identity, tags, numeric/reverse indices and frozen state. Native set_components and component removal rules replace item NBT fields on Neo. CustomData component values use the native codec's SNBT-string alternative to preserve numeric types in JSON; this is component data, not legacy item-NBT conversion.
- Trades use ItemCost predicates and native offer codecs. Serialization preserves typed CustomData, custom trade IDs and no-restock flags; offer copies retain metadata. Component validation uses each slot's actual item prototype.
- Neo payment slots reject component-removal constraints that vanilla ItemCost cannot represent; sell stacks and recipe/loot patches retain removals.
- Detailed documentation moved into separate English/Chinese Wiki topic pages; README and external mod description remain brief; full history restored to CHANGELOG.md newest first.

## Verification / 验证

| Check | Forge 1.20.1 | NeoForge 1.21.1 |
|---|---|---|
| Offline buildAll | PASS | PASS |
| JUnit | 36 tests / 11 original classes, 0 failures | 56 tests / 15 classes, 0 failures |
| Architecture / final-JAR references | PASS / 0 problems | PASS / 0 problems |
| Mixin targets | 7 Mixins, 0 problems | 8 Mixins, 0 problems |
| Release classes | 221, Java class version 65 | 230, Java class version 65 |
| Mixin configs | 3; JAVA_17; refmap and MixinConfigs present | 3; JAVA_21; TOML registration |
| KineticCore requirement | [26.10.3,) unchanged | [26.10.3,) |
| Runtime | Skipped at user request; stable original behavior | Existing PCL2 instance: PASS |

Forge baseline SHA256: 12B9DA207299EE0694405E9899DF2721002DE4FA3E5638A08752721F75BDDB4C.

Fresh javap comparison of all 221 classes (84,580 lines) preserves ordered instructions and signatures after normalizing constant-pool indices. Six baseline resources are unchanged, including both language files and all three Mixin configs. mods.toml replaces the version token with the same runtime version; dependency ranges are unchanged. Only expected build/bytecode/refmap/manifest differences remain.

Neo runtime uses only F:/game/异界战斗幻想/.minecraft/versions/1.21.1-NeoForge_21.1.252 through the existing PCL2 Java/memory settings. No new game version download or memory setting changes. 2026-10-03 21:44:57: CONTENTSTUDIO_VALIDATION_PASS failures=0, 24 runtime checks. Includes component values/removals and JSON/network round trips; recipe IDs/staged tags; trade metadata save/load/copy/no-restock; actual sword/stone validation; loot holder/reverse/numeric/frozen/tag integrity; nested global append once and removal hooks; full server datapack reload. F6 ContentStudio and workbench recipe editor open successfully with JEI installed.

Meaningful RED -> GREEN regressions: ingredient default-component removal; trade CustomData numeric-type preservation; loot JSON CustomData numeric-type preservation. Independent code review identified wrong item prototypes in trade validation and changed loot item callbacks; fixed and verified. Final read-only review found no important remaining issues.

Runtime logs also contain unrelated pre-existing IMBlocker missing AbstractScrollArea Mixin warnings and ModernUI font-path warnings. No ContentStudio Mixin errors or new known_mixin_issues remain.

## Artifacts / 产物

- D:/NEWMODS/contentstudio-forge-1.20.1-26.10.3.jar — SHA256 6C7017E96D73DA41151626BB012212FE0180BD60FD55101E0292C9DFF563304B
- D:/NEWMODS/contentstudio-neoforge-1.21.1-26.10.3.jar — SHA256 A292B9ACBF692DF8800750E48992CAC3EA5CBA15BDE4EC820E7C34CC324CAA7C

Neo release remains installed for user validation. Temporary fixture moved out of mods to codex-migration-backup/contentstudio-validation-20261003/contentstudio-validation-tested.jar after normal client shutdown. No existing Forge game process was started or stopped.

The final build changes only the manifest build timestamp relative to the Neo runtime-tested JAR; all other 273 entries are byte-for-byte identical. The installed release was synchronized to the final artifact.

Build/evidence logs are ignored under .gradle/migration: buildAll-progress.log, trade-unit-red.log, trade-unit-green.log, loot-types-red.log, runtime-green.log, baseline-final-javap.txt and release-final-javap.txt.

## Documentation / 文档

Six addon Wikis (TextStudio, RealmControl, KineticArmory, ItemControl, EntityControl, ContentStudio) mirror the core: English and Chinese are separate paired topic pages, reciprocal language links at top; Home/sidebar English navigation above Chinese. 58 pairs, 128 Markdown pages, 470 internal links verified. Original bilingual draft backups remain ignored in each repo .gradle/migration/wiki-bilingual-backup.md. Each Wiki has its own local Git commit and origin configured. Wiki content is local and not yet published; GitHub wiki remotes require an initial page. KineticArmory's previously-disabled has_wiki setting was enabled and read back as true on the user's request.

CHANGELOG.md is reverse chronological and preserves historical entries. Detailed behavior tutorials are in the local Wiki; README/external mod MD provide short feature descriptions, dependencies and separate language tutorial links.
