package dev.xyat.contentstudio.villager.mixin;

import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.contentstudio.villager.config.VillagerConfig;
import dev.xyat.contentstudio.villager.trade.VillagerTradeRegistry;
import dev.xyat.contentstudio.villager.util.VillagerTradeRuntimeUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(Villager.class)
public abstract class VillagerTweaksMixin extends AbstractVillager {
    @Shadow
    private int updateMerchantTimer;

    @Shadow
    private boolean increaseProfessionLevelOnUpdate;

    @Shadow
    protected abstract void increaseMerchantCareer();

    @Unique
    private Player contentstudio_villager$followingPlayer = null;

    @Unique
    private boolean contentstudio_villager$isTrapped = false;

    @Unique
    private List<MerchantOffer> contentstudio_villager$offersBeforeLateOverride = List.of();

    public VillagerTweaksMixin(EntityType<? extends AbstractVillager> type, Level level) {
        super(type, level);
    }

    @Inject(method = "updateTrades", at = @At("HEAD"), cancellable = true)
    private void contentstudio_villager$applyConfiguredTrades(CallbackInfo ci) {
        if (VillagerTradeRegistry.isSessionUnavailable()) {
            return;
        }

        VillagerData data = ((Villager) (Object) this).getVillagerData();
        ResourceLocation professionId = KineticRegistries.villagerProfessions().id(data.getProfession());
        String profession = professionId.toString();
        int level = data.getLevel();
        if (!VillagerTradeRegistry.hasActiveLevelChanges(profession, level)) {
            return;
        }

        if (VillagerConfig.enableVillagerTradeLateOverride) {
            contentstudio_villager$offersBeforeLateOverride = new ArrayList<>(this.getOffers());
            return;
        }

        VillagerConfig.TradeGroup group = VillagerTradeRegistry.getActiveTradeGroup(profession, level);
        MerchantOffers offers = this.getOffers();
        if (group != null && group.replaceAll()) {
            offers.clear();
        }

        VillagerTradeRuntimeUtil.addConfiguredOffers(
                offers,
                (Villager) (Object) this,
                this.getRandom(),
                profession,
                level,
                VillagerTradeRuntimeUtil.getDefaultOfferCount(profession, level)
        );
        ci.cancel();
    }

    @Inject(method = "updateTrades", at = @At("TAIL"))
    private void contentstudio_villager$applyConfiguredTradesLate(CallbackInfo ci) {
        if (!VillagerConfig.enableVillagerTradeLateOverride || VillagerTradeRegistry.isSessionUnavailable()) {
            contentstudio_villager$offersBeforeLateOverride = List.of();
            return;
        }

        VillagerData data = ((Villager) (Object) this).getVillagerData();
        ResourceLocation professionId = KineticRegistries.villagerProfessions().id(data.getProfession());
        if (professionId == null) {
            contentstudio_villager$offersBeforeLateOverride = List.of();
            return;
        }

        String profession = professionId.toString();
        int level = data.getLevel();
        if (!VillagerTradeRegistry.hasActiveLevelChanges(profession, level)) {
            contentstudio_villager$offersBeforeLateOverride = List.of();
            return;
        }

        VillagerConfig.TradeGroup group = VillagerTradeRegistry.getActiveTradeGroup(profession, level);
        MerchantOffers offers = this.getOffers();
        List<MerchantOffer> previousOffers = contentstudio_villager$offersBeforeLateOverride;
        contentstudio_villager$offersBeforeLateOverride = List.of();

        offers.clear();
        if (group == null || !group.replaceAll()) {
            offers.addAll(previousOffers);
        }

        VillagerTradeRuntimeUtil.addConfiguredOffers(
                offers,
                (Villager) (Object) this,
                this.getRandom(),
                profession,
                level,
                VillagerTradeRuntimeUtil.getDefaultOfferCount(profession, level)
        );
    }

    @Inject(method = "customServerAiStep", at = @At("HEAD"), cancellable = true)
    private void contentstudio_villager$modifyVillagerAi(CallbackInfo ci) {
        Level level = this.level();
        if (level.isClientSide) {
            return;
        }

        long gameTime = level.getGameTime();

        if (VillagerConfig.enableVillagerFollow) {
            if (gameTime % 20 == 0) {
                contentstudio_villager$scanForPlayer();
            }

            if (contentstudio_villager$followingPlayer != null) {
                if (!VillagerConfig.isVillagerFollowItem(contentstudio_villager$followingPlayer.getMainHandItem()) ||
                        this.distanceToSqr(contentstudio_villager$followingPlayer) > 256.0D) {
                    contentstudio_villager$followingPlayer = null;
                    this.getNavigation().stop();
                } else {
                    this.getLookControl().setLookAt(contentstudio_villager$followingPlayer, this.getMaxHeadYRot(), this.getMaxHeadXRot());
                    if (this.distanceToSqr(contentstudio_villager$followingPlayer) > 6.25D) {
                        this.getNavigation().moveTo(contentstudio_villager$followingPlayer, 0.6D);
                    } else {
                        this.getNavigation().stop();
                    }
                    return;
                }
            }
        }

        if (VillagerConfig.villagerTickInterval > 1) {
            if (gameTime % VillagerConfig.villagerTickInterval == 0) {
                contentstudio_villager$updateTrappedStatus();
                if (this.contentstudio_villager$isTrapped) {
                    this.getNavigation().stop();
                }
                return;
            }

            if (this.contentstudio_villager$isTrapped) {
                this.getNavigation().stop();

                if (VillagerConfig.enableVillagerTradeUpdateProtection) {
                    if (this.isTrading()) {
                        return;
                    }

                    if (contentstudio_villager$needsMinimalTradeUpdate()) {
                        contentstudio_villager$runMinimalTradeUpdate();
                    }
                }

                ci.cancel();
            }
        }
    }

    @Unique
    private boolean contentstudio_villager$needsMinimalTradeUpdate() {
        return this.updateMerchantTimer > 0 || this.increaseProfessionLevelOnUpdate;
    }

    @Unique
    private void contentstudio_villager$runMinimalTradeUpdate() {
        if (this.isTrading() || this.updateMerchantTimer <= 0) {
            return;
        }

        --this.updateMerchantTimer;

        if (this.updateMerchantTimer == 0) {
            if (this.increaseProfessionLevelOnUpdate) {
                this.increaseMerchantCareer();
                this.increaseProfessionLevelOnUpdate = false;
            }

            this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
        }
    }

    @Unique
    private void contentstudio_villager$updateTrappedStatus() {
        Level level = this.level();
        List<LivingEntity> nearbyEntities = level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(1.0D));
        if (nearbyEntities.size() > 3) {
            this.contentstudio_villager$isTrapped = true;
            return;
        }

        BlockPos pos = this.blockPosition();
        Villager villager = (Villager) (Object) this;
        boolean canEscape = false;

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = pos.relative(dir);
            if (contentstudio_villager$canVillagerPass(level, adjacent, villager) ||
                    contentstudio_villager$canVillagerPass(level, adjacent.above(), villager) ||
                    contentstudio_villager$canVillagerPass(level, adjacent.below(), villager)) {
                canEscape = true;
                break;
            }
        }

        this.contentstudio_villager$isTrapped = !canEscape;
    }

    @Unique
    private boolean contentstudio_villager$canVillagerPass(Level level, BlockPos pos, Villager villager) {
        float height = villager.getBbHeight();
        int blocksToCheck = Mth.ceil(height);

        for (int i = 0; i < blocksToCheck; i++) {
            BlockPos checkPos = pos.above(i);
            VoxelShape shape = level.getBlockState(checkPos).getCollisionShape(level, checkPos);

            if (!shape.isEmpty()) {
                if (i == 0 && shape.max(Direction.Axis.Y) <= 0.6F) {
                    continue;
                }
                return false;
            }
        }
        return true;
    }

    @Unique
    private void contentstudio_villager$scanForPlayer() {
        List<Player> players = this.level().getEntitiesOfClass(Player.class,
                this.getBoundingBox().inflate(10.0D),
                player -> !player.isSpectator() && VillagerConfig.isVillagerFollowItem(player.getMainHandItem()));

        this.contentstudio_villager$followingPlayer = players.isEmpty() ? null : players.get(0);
    }
}
