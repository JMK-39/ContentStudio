//? if >=1.21 {
/*package dev.xyat.contentstudiovalidation;

@net.neoforged.fml.common.Mod("contentstudio_gui_capture")
public final class GuiCaptureNeoForgeEntry {
    public GuiCaptureNeoForgeEntry() {
        if (Boolean.getBoolean("contentstudio.nativeRecipeMatrix")) {
            dev.xyat.kineticcore.api.runtime.KineticPlatform.runOnClient(() -> NativeRecipeMatrixValidation::install);
        } else if (Boolean.getBoolean("contentstudio.nativeRecipeValidation")) {
            dev.xyat.kineticcore.api.runtime.KineticPlatform.runOnClient(() -> NativeRecipeValidation::install);
        } else if (Boolean.getBoolean("contentstudio.guiValidation")) {
            dev.xyat.kineticcore.api.runtime.KineticPlatform.runOnClient(() -> GuiLongTextValidation::install);
        }
    }
}
*///?}
