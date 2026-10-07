package dev.xyat.contentstudiovalidation;

// This entry exists only in the explicitly built verification JAR.
//? if >=1.21 {
/*@net.neoforged.fml.common.Mod("contentstudio_parity")
*///?} else {
@net.minecraftforge.fml.common.Mod("contentstudio_parity")
//?}
public final class VillagerParityEntry {
    public VillagerParityEntry() {
        if (Boolean.getBoolean("contentstudio.villagerParity")) {
            dev.xyat.kineticcore.api.runtime.KineticPlatform.runOnClient(() -> VillagerParityValidation::install);
        }
    }
}
