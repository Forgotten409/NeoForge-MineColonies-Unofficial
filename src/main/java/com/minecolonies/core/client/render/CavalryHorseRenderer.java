package com.minecolonies.core.client.render;

import com.google.common.collect.Maps;
import com.minecolonies.core.entity.other.cavalry.CavalryHorseEntity;
import net.minecraft.client.model.animal.equine.BabyHorseModel;
import net.minecraft.client.model.animal.equine.EquineSaddleModel;
import net.minecraft.client.model.animal.equine.HorseModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AbstractHorseRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.HorseMarkingLayer;
import net.minecraft.client.renderer.entity.layers.SimpleEquipmentLayer;
import net.minecraft.client.renderer.entity.state.HorseRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.equine.Variant;

import java.util.Map;

/**
 * Renderer for {@link CavalryHorseEntity}.
 *
 * <p>PORT26: the vanilla {@code HorseRenderer} is final and its layers render from
 * {@link HorseRenderState}; this port-renderer replicates the vanilla body (variant
 * textures, marking/armor/saddle layers) with {@code S = HorseRenderState} so the
 * vanilla layers stay compatible, and additionally publishes the combat-readiness
 * value for {@link CavalryOverlayLayer} during state extraction (layers no longer
 * see the entity itself).</p>
 */
public class CavalryHorseRenderer extends AbstractHorseRenderer<CavalryHorseEntity, HorseRenderState, HorseModel>
{
    private record HorseTextures(Identifier adult, Identifier baby) {}

    private static final Map<Variant, CavalryHorseRenderer.HorseTextures> LOCATION_BY_VARIANT = Maps.newEnumMap(
      Map.of(
        Variant.WHITE,
        new CavalryHorseRenderer.HorseTextures(
          Identifier.withDefaultNamespace("textures/entity/horse/horse_white.png"),
          Identifier.withDefaultNamespace("textures/entity/horse/horse_white_baby.png")),
        Variant.CREAMY,
        new CavalryHorseRenderer.HorseTextures(
          Identifier.withDefaultNamespace("textures/entity/horse/horse_creamy.png"),
          Identifier.withDefaultNamespace("textures/entity/horse/horse_creamy_baby.png")),
        Variant.CHESTNUT,
        new CavalryHorseRenderer.HorseTextures(
          Identifier.withDefaultNamespace("textures/entity/horse/horse_chestnut.png"),
          Identifier.withDefaultNamespace("textures/entity/horse/horse_chestnut_baby.png")),
        Variant.BROWN,
        new CavalryHorseRenderer.HorseTextures(
          Identifier.withDefaultNamespace("textures/entity/horse/horse_brown.png"),
          Identifier.withDefaultNamespace("textures/entity/horse/horse_brown_baby.png")),
        Variant.BLACK,
        new CavalryHorseRenderer.HorseTextures(
          Identifier.withDefaultNamespace("textures/entity/horse/horse_black.png"),
          Identifier.withDefaultNamespace("textures/entity/horse/horse_black_baby.png")),
        Variant.GRAY,
        new CavalryHorseRenderer.HorseTextures(
          Identifier.withDefaultNamespace("textures/entity/horse/horse_gray.png"),
          Identifier.withDefaultNamespace("textures/entity/horse/horse_gray_baby.png")),
        Variant.DARK_BROWN,
        new CavalryHorseRenderer.HorseTextures(
          Identifier.withDefaultNamespace("textures/entity/horse/horse_darkbrown.png"),
          Identifier.withDefaultNamespace("textures/entity/horse/horse_darkbrown_baby.png"))
      )
    );

    public CavalryHorseRenderer(final EntityRendererProvider.Context context)
    {
        super(context, new HorseModel(context.bakeLayer(ModelLayers.HORSE)), new BabyHorseModel(context.bakeLayer(ModelLayers.HORSE_BABY)));
        this.addLayer(new HorseMarkingLayer(this));
        this.addLayer(
          new SimpleEquipmentLayer<>(
            this,
            context.getEquipmentRenderer(),
            EquipmentClientInfo.LayerType.HORSE_BODY,
            state -> state.bodyArmorItem,
            new HorseModel(context.bakeLayer(ModelLayers.HORSE_ARMOR)),
            null,
            2
          )
        );
        this.addLayer(
          new SimpleEquipmentLayer<>(
            this,
            context.getEquipmentRenderer(),
            EquipmentClientInfo.LayerType.HORSE_SADDLE,
            state -> state.saddle,
            new EquineSaddleModel(context.bakeLayer(ModelLayers.HORSE_SADDLE)),
            null,
            2
          )
        );
        this.addLayer(new CavalryOverlayLayer(this));
    }

    @Override
    public Identifier getTextureLocation(final HorseRenderState state)
    {
        final CavalryHorseRenderer.HorseTextures variant = LOCATION_BY_VARIANT.get(state.variant);
        return state.isBaby ? variant.baby : variant.adult;
    }

    @Override
    public HorseRenderState createRenderState()
    {
        return new HorseRenderState();
    }

    @Override
    public void extractRenderState(final CavalryHorseEntity entity, final HorseRenderState state, final float partialTicks)
    {
        super.extractRenderState(entity, state, partialTicks);
        state.variant = entity.getVariant();
        state.markings = entity.getMarkings();
        state.bodyArmorItem = entity.getBodyArmorItem().copy();

        // PORT26: publish combat readiness for the overlay layer (layers are state-only now).
        final float threshold = entity.getMaxHealth() * CavalryHorseEntity.COMBAT_READINESS_THRESHOLD;
        final float cooldown = Math.max(0f, entity.getAnimalDataView() == null ? 0 : entity.getAnimalDataView().getCombatCooldown());
        final float readiness = Mth.clamp(1.0f - (cooldown / Math.max(0.001f, threshold)), 0f, 1f);
        CavalryOverlayLayer.putReadiness(state, readiness);
    }
}
