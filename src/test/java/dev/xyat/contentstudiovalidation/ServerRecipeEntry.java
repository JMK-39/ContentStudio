//? if <1.21 {
package dev.xyat.contentstudiovalidation;

/** Forge 1.20.1 entry of the validation mod: only the dedicated-server recipe check runs there. */
@net.minecraftforge.fml.common.Mod("contentstudio_validation")
public final class ServerRecipeEntry {
    public ServerRecipeEntry() {
        if (Boolean.getBoolean("contentstudio.serverRecipeValidation")) {
            dev.xyat.kineticcore.api.runtime.KineticPlatform.runOnClient(() -> ServerRecipeValidation::install);
        }
    }
}
//?}
