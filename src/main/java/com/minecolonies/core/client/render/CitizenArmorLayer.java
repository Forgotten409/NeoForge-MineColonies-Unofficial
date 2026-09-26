package com.minecolonies.core.client.render;

import com.minecolonies.api.client.render.modeltype.CitizenModel;
import com.minecolonies.api.client.render.modeltype.CitizenRenderState;
import com.minecolonies.api.util.Log;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.SkullBlock;
import org.jetbrains.annotations.NotNull;

/**
 * Armor layer for citizens.
 *
 * <p>PORT26: rewritten for the render-state architecture. The layer extends the vanilla
 * {@link HumanoidArmorLayer} (which is fully data-driven now: armor models and textures
 * resolve through equipment assets) and adds the two citizen specialties:</p>
 * <ul>
 *   <li>a player-skin head for citizens with a custom texture UUID (the profile lookup is
 *       delegated to the vanilla {@link PlayerSkinRenderCache} — the 1.21.1 manual
 *       session-service fetch with the gameProfileMap cache is obsolete);</li>
 *   <li>the colony-side display armor ({@code ICitizenDataView#getDisplayArmor}) which
 *       overrides the entity equipment when non-empty (santa hats, guard armor): the
 *       display stacks are carried in {@link CitizenRenderState} and swapped into the
 *       equipment fields for the vanilla submit pass.</li>
 * </ul>
 */
public class CitizenArmorLayer extends HumanoidArmorLayer<CitizenRenderState, CitizenModel, HumanoidModel<CitizenRenderState>>
{
    /**
     * Model of the player head rendered for citizens with custom skins.
     */
    private final SkullModelBase        skullModel;
    private final PlayerSkinRenderCache playerSkinRenderCache;

    public CitizenArmorLayer(final RenderLayerParent<CitizenRenderState, CitizenModel> parentLayer, final EntityRendererProvider.Context context)
    {
        // PORT26: HumanoidArmorLayer takes an ArmorModelSet (player/helmet|chestplate|leggings|boots
        // layers) + the equipment renderer now — the old inner/outer HumanoidArmorLayer ctor with
        // ModelManager is gone, and armor rendering is equipment-asset driven.
        super(parentLayer,
          ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), HumanoidModel::new),
          context.getEquipmentRenderer());
        this.skullModel = SkullBlockRenderer.createModel(context.getModelSet(), SkullBlock.Types.PLAYER);
        this.playerSkinRenderCache = context.getPlayerSkinRenderCache();
    }

    @Override
    public void submit(
      @NotNull final PoseStack poseStack,
      @NotNull final SubmitNodeCollector submitNodeCollector,
      final int lightCoords,
      @NotNull final CitizenRenderState state,
      final float yRot,
      final float xRot)
    {
        if (!state.hasCitizenDataView)
        {
            return;
        }

        if (!state.hasCitizenInventory)
        {
            return;
        }

        if (state.isInvisible)
        {
            return;
        }

        if (state.hasCustomTextureUUID && state.customTextureUUID != null)
        {
            this.renderCustomTextureHead(poseStack, submitNodeCollector, lightCoords, state);
        }

        this.renderDisplayArmor(poseStack, submitNodeCollector, lightCoords, state);
    }

    /**
     * Renders the player-skin head for citizens with a custom texture.
     *
     * @param poseStack the pose stack.
     * @param submitNodeCollector the submit collector.
     * @param lightCoords the packed light.
     * @param state the citizen render state.
     */
    private void renderCustomTextureHead(
      final PoseStack poseStack,
      final SubmitNodeCollector submitNodeCollector,
      final int lightCoords,
      final CitizenRenderState state)
    {
        // PORT26: the vanilla skin render cache resolves the profile asynchronously and
        // serves the default skin until loaded (replaces the old fetchProfile + gameProfileMap).
        final ResolvableProfile gameProfile = ResolvableProfile.createResolved(new GameProfile(state.customTextureUUID, "mcoltexturequery"));
        final RenderType renderType = this.playerSkinRenderCache.getOrDefault(gameProfile).renderType();

        poseStack.pushPose();
        // transform onto the (hidden) citizen head — the same transform the vanilla
        // CustomHeadLayer uses for worn skulls
        this.getParentModel().root().translateAndRotate(poseStack);
        this.getParentModel().translateToHead(poseStack);
        poseStack.scale(1.1875F, 1.1875F, 1.1875F);
        SkullBlockRenderer.submitSkull(0.0F, poseStack, submitNodeCollector, lightCoords, this.skullModel, renderType, state.outlineColor, null);
        poseStack.popPose();
    }

    /**
     * Renders the citizen armor. The colony-synced display armor overrides the entity
     * equipment when set, otherwise the vanilla path renders the entity equipment.
     *
     * @param poseStack the pose stack.
     * @param submitNodeCollector the submit collector.
     * @param lightCoords the packed light.
     * @param state the citizen render state.
     */
    private void renderDisplayArmor(
      final PoseStack poseStack,
      final SubmitNodeCollector submitNodeCollector,
      final int lightCoords,
      final CitizenRenderState state)
    {
        final ItemStack savedHead = state.headEquipment;
        final ItemStack savedChest = state.chestEquipment;
        final ItemStack savedLegs = state.legsEquipment;
        final ItemStack savedFeet = state.feetEquipment;

        // PORT26: swap the display armor into the equipment fields of the state; the vanilla
        // HumanoidArmorLayer#submit then renders everything through the data-driven
        // equipment path (equipment assets, trims, glint) exactly like worn armor.
        if (!state.displayArmorHead.isEmpty())
        {
            state.headEquipment = state.displayArmorHead;
        }
        if (!state.displayArmorChest.isEmpty())
        {
            state.chestEquipment = state.displayArmorChest;
        }
        if (!state.displayArmorLegs.isEmpty())
        {
            state.legsEquipment = state.displayArmorLegs;
        }
        if (!state.displayArmorFeet.isEmpty())
        {
            state.feetEquipment = state.displayArmorFeet;
        }

        try
        {
            super.submit(poseStack, submitNodeCollector, lightCoords, state, 0.0F, 0.0F);
        }
        catch (final Exception e)
        {
            // PORT26: keep the 1.21.1 safety net — broken modded armor must not spam-crash the render.
            Log.getLogger().warn("Error rendering armor on citizen, check your latest.log and report to the respective armor mod.", e);
        }
        finally
        {
            state.headEquipment = savedHead;
            state.chestEquipment = savedChest;
            state.legsEquipment = savedLegs;
            state.feetEquipment = savedFeet;
        }
    }
}
