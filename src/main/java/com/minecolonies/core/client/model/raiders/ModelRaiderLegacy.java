package com.minecolonies.core.client.model.raiders;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose; // PORT26: PartPose moved out of geom.builders → geom
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Legacy-format humanoid raider model (64x32 skin layout).
 *
 * <p>PORT26 FIX ("mobs have no faces"): the barbarian/pirate/drowned-pirate renderers
 * previously baked {@code ModelLayers.PLAYER}. That layer is declared <b>64x64</b> and uses
 * the modern skin layout (left arm {@code texOffs(32,48)}, left leg {@code texOffs(16,48)}),
 * while these raiders use classic <b>64x32</b> textures (barbarian1.png, pirate*.png,
 * drowned_pirate*.png — verified against the shipped art). Model UVs are normalized by the
 * <i>layer</i> texture size at bake time but sampled against the <i>actual</i> texture
 * dimensions at draw time, so a 64x64-declared layer + 64x32 texture samples the wrong
 * region of the skin — the face quad (v 8..16) landed on texture rows 4..8 and was never
 * displayed (heads rendered with the head-top strip smeared over them, limbs sampled the
 * wrong quadrants).</p>
 *
 * <p>This layer keeps the standard humanoid part names (required by
 * {@link HumanoidModel}'s constructor: head/hat/body/right_arm/left_arm/right_leg/left_leg)
 * but declares the texture as 64x32 and mirrors the <i>right</i> limb UVs onto the left
 * limbs — the exact layout vanilla's 64x32 skeleton layer uses, at full (4px) limb width.
 * Classic skins then sample correctly, including the face.</p>
 */
public final class ModelRaiderLegacy
{
    private ModelRaiderLegacy()
    {
        throw new IllegalStateException("Utility class");
    }

    public static LayerDefinition createBodyLayer()
    {
        final MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        final PartDefinition root = mesh.getRoot();

        // Legacy 64x32 layout: the left limbs mirror the right limbs' UV regions
        // (instead of the modern 64x64 positions at (32,48)/(16,48) which do not exist
        // in a 32-tall skin).
        root.addOrReplaceChild(
          "left_arm",
          CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
          PartPose.offset(5.0F, 2.0F, 0.0F)
        );
        root.addOrReplaceChild(
          "left_leg",
          CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
          PartPose.offset(1.9F, 12.0F, 0.0F)
        );

        return LayerDefinition.create(mesh, 64, 32);
    }
}
