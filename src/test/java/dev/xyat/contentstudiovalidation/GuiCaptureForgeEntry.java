//? if <1.21 {
package dev.xyat.contentstudiovalidation;

/** Forge 1.20.1 entry of the GUI capture: it runs inside an installed modpack, where the 1.21 checks cannot. */
@net.minecraftforge.fml.common.Mod("contentstudio_gui_capture")
public final class GuiCaptureForgeEntry {
    public GuiCaptureForgeEntry() {
        if (Boolean.getBoolean("contentstudio.lootFallbackValidation")) {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(this::checkLootFallback);
            return;
        }
        if (Boolean.getBoolean("contentstudiotacz.guiValidation")) {
            dev.xyat.kineticcore.api.runtime.KineticPlatform.runOnClient(() -> TaczRecipeSaveValidation::install);
        } else if (Boolean.getBoolean("contentstudio.recipeRepairValidation")) {
            dev.xyat.kineticcore.api.runtime.KineticPlatform.runOnClient(() -> RecipeRepairValidation::install);
        } else if (Boolean.getBoolean("contentstudio.lootMigration")) {
            dev.xyat.kineticcore.api.runtime.KineticPlatform.runOnClient(() -> LootMigrationValidation::install);
        } else if (Boolean.getBoolean("contentstudio.tooltipNavigation")) {
            dev.xyat.kineticcore.api.runtime.KineticPlatform.runOnClient(() -> TooltipNavigationValidation::install);
        } else if (Boolean.getBoolean("contentstudio.recipePlaytest")) {
            dev.xyat.kineticcore.api.runtime.KineticPlatform.runOnClient(() -> RecipePlaytestValidation::install);
        } else if (Boolean.getBoolean("contentstudio.nativeCoverage")) {
            dev.xyat.kineticcore.api.runtime.KineticPlatform.runOnClient(() -> NativeRecipeCoverage::install);
        } else if (Boolean.getBoolean("contentstudio.nativeRecipeCreation")) {
            dev.xyat.kineticcore.api.runtime.KineticPlatform.runOnClient(() -> NativeRecipeCreationValidation::install);
        } else if (Boolean.getBoolean("contentstudio.jeiEditorValidation")) {
            dev.xyat.kineticcore.api.runtime.KineticPlatform.runOnClient(() -> JeiEditorValidation::install);
        } else if (Boolean.getBoolean("contentstudio.nativeRecipeMatrix")) {
            dev.xyat.kineticcore.api.runtime.KineticPlatform.runOnClient(() -> NativeRecipeMatrixValidation::install);
        } else if (Boolean.getBoolean("contentstudio.nativeRecipeValidation")) {
            dev.xyat.kineticcore.api.runtime.KineticPlatform.runOnClient(() -> NativeRecipeValidation::install);
        } else if (Boolean.getBoolean("contentstudio.guiValidation")) {
            dev.xyat.kineticcore.api.runtime.KineticPlatform.runOnClient(() -> GuiLongTextValidation::install);
        }
    }

    private void checkLootFallback(net.minecraftforge.event.server.ServerStartedEvent event) {
        var logger = com.mojang.logging.LogUtils.getLogger();
        try {
            LootRuntimeFallbackChecks.check(event.getServer());
            logger.info("CONTENTSTUDIO_LOOT_FALLBACK_PASS");
        } catch (Exception | AssertionError failure) {
            logger.error("CONTENTSTUDIO_LOOT_FALLBACK_FAIL", failure);
        }
    }
}
//?}
