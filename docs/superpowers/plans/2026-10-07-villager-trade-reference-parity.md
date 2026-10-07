# Villager trade reference parity implementation plan

**Goal:** Merge useful trade behavior and editor interactions from the third-party archive into current official ContentStudio for Forge1.20.1, NeoForge1.21.1 and NeoForge26.1.2.
**Basis:** f0c979e; reference E:/EDGE/aitaocunming_villager_trade_only.zip. The archive is incomplete and uses the old API. It is reference data only.
**Architecture:** Keep KineticPage/KineticUi, current config/server lifecycle, independent packet registration, native per-version item data, late override and 26.1 server-provided trade previews. Adapt missing functionality instead of replacing classes with legacy versions.

## Constraints

- Existing navigation, toolbar, slots, vanilla appearance, field positions, default tracking and all trade modes remain intact. New source/issue controls use free space.
- Merge-all is the default. Source-only is an independent immediate server setting, not a replacement for Late Override or part of draft undo.
- Dedicated-server level2 permission support stays; do not copy the reference singleplayer-only restriction.
- English/Chinese complete keys match; newest CHANGELOG first, English above Chinese. Detailed documentation belongs in separate bilingual Wiki pages.
- Core GUI/API/gameplay source remains unchanged. The user's subsequent Java-target request authorizes build/documentation changes across the core and addons: actual Java17 on1.20.1, Java21 on1.21.1, Java25 on26.1.2. Use local artifacts/caches and output all enabled nodes to D:/NEWMODS. Test existing profiles, preserve heap/settings and only stop owned PIDs. No push.

## Tasks

1. **Config and live trade behavior**
   - [x] Independent source/reference audit; existing merge behavior and late override identified.
   - [x] Clean pinned worktree baseline buildAll passes offline (18s,38tasks).
   - [x] Add TradeSourceMode merge_all/local_only persistence and live rollback/republish via current registry lifecycle.
   - [x] Add source-only candidate generation and marked-offer filtering for villagers/traders in every version; guard server/session state and preserve current late hooks.
   - [x] Add detailed validation using existing modern item validators; return validGroups/validOffers/validOverrides and per-field issues. Count skipped OFFER indices uniquely.
   - [x] Keep boolean applyAndSaveLive wrapper; add TradeSaveOutcome and applyAndSaveLiveDetailed with lateOverride parameter.
   - [x] Verify strict/partial validation, unknown modes, empty second payment and native NBT/components with meaningful tests.

2. **New-API editor and network parity**
   - [x] Register source request/result and open-denied feedback without colliding with26.1 preview packet8; preserve late override in existing save payload. Result codes permission_denied/server_unavailable/invalid_data/invalid_mode/write_failed/unknown.
   - [x] Add source menu, problem count/prev/next focus and styled highlights/tooltips without moving existing controls. Bound source/problem/close menus inside the existing editor frame using the public fixed-width Core API.
   - [x] Valid subset save retains invalid client drafts; wait for successful ACK to commit/close, block edits while pending, retain drafts on failure. Compare ACK against pure canonical accepted records.
   - [x] Save/discard/cancel handles transient form and level changes. Restore quantity replacement/right-click clear, undo vanilla baseline and numeric defaults, and toggle internal values.
   - [x] Configurable shortcut is unbound by default and uses opaque KineticGui parents plus connection/player isolation; never cache native widgets/screens. Explicitly restore accepted shared rules on suspension/discard, including unchanged-original partial drafts.
   - [x] Root network consumes saveTradeConfig(groups,offers,overrides,lateOverride,notifySuccess), saveTradeSourceMode(mode). Page replies: handleTradeSaveResult(success,failureCode), handleTradeSourceModeResult(success,mode,failureCode).

3. **Verification and delivery**
   - [x] BuildAll offline, language parity, architecture, Mixin targets, reobf refs and relevant JUnit checks all pass. Inspect actual release archives for bytecode61/65/69, refmaps and no fixture.
   - [x] Final installed Forge fixture passes100 checks and16 English/Chinese854/1920 captures in the existing full modpack with unchanged8GiB heap and Core26.10.8. Its obsolete deduplication option is removed; all other log settings are preserved. Earlier startup memory/deadlock failures no longer block final acceptance.
   - [x] NeoForge1.21.1 and26.1.2 fixtures each pass100 checks and16 English/Chinese854/1920 captures; actual integrated-server saves, source isolation, canonical partial ACK, unchanged-original draft discard/suspension and all menu bounds pass. Exact latest config bytes and touched options are restored. Dedicated-server sessions and simulated disk-write failures were not exercised.
   - [x] Output release artifacts to D:/NEWMODS; deploy loader-matched releases into existing full Forge pack and existing Neo profiles, hash-check. No standalone Minecraft download; remove owned temporary fixture jars afterward.
   - [x] Independent final code review; no unresolved source issues. Verification results and untested dedicated-server/failure scenarios are recorded in docs/villager-trade-20261007-verification.md. Local commit/integration completed; no push.

## Review focus

Source-only must affect untouched profession/levels and avoid recursive/duplicate generation or losing marked usage data. Source switch does not reconstruct already-filtered vanilla offers until normal generation. Partial save cannot silently drop invalid client drafts. Late Override and remote editing survive. Component syntax must pass modern payment/result validation while legacy NBT remains Forge-only. Deferred ACKs and shortcut sessions cannot commit another connection's draft.
