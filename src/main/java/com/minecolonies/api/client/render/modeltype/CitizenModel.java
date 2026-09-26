package com.minecolonies.api.client.render.modeltype;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Pose;
import org.jetbrains.annotations.NotNull;

/**
 * Citizen model.
 *
 * <p>PORT26: operates on {@link CitizenRenderState} instead of the entity directly;
 * HumanoidModel generics now require render states.</p>
 */
public class CitizenModel extends HumanoidModel<CitizenRenderState>
{
    /**
     * Working render meta.
     */
    private static final String RENDER_META_WORKING = "working";

    public static boolean isItApril1st = false;

    public CitizenModel(final ModelPart part)
    {
        super(part, RenderTypes::entityCutout);
    }

    @Override
    public void setupAnim(@NotNull final CitizenRenderState state)
    {
        super.setupAnim(state);

        if (body.xRot == 0)
        {
            body.xRot = getActualRotation(state);
        }

        if (head.xRot == 0)
        {
            head.xRot = getActualRotation(state);
        }

        if (state.hasCitizenDataView && state.hasCustomTextureUUID)
        {
            head.visible = false;
            hat.visible = false;
        }
        else
        {
            head.visible = true;
            hat.visible = true;
        }

        if (isItApril1st)
        {
            switch (state.civilianId % 7)
            {
                case 0:
                    leftArm.visible = false;
                    break;
                case 1:
                    rightArm.visible = false;
                    break;
                case 2:
                    body.visible = false;
                    break;
                case 3:
                    head.visible = false;
                    break;
                case 4:
                    hat.visible = false;
                    break;
                case 5:
                    leftLeg.visible = false;
                    break;
                case 6:
                    rightLeg.visible = false;
                    break;
            }
        }
    }

    public static LayerDefinition createMesh()
    {
        MeshDefinition meshdefinition = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        // PORT26 FIX ("no faces" family): citizen textures (citizen/default/*.png etc.) are
        // 128x64 canvases carrying the modern 64x64 skin layout in their top-left quadrant
        // (same layout the per-job citizen models declare — see FemaleCitizenModel). The
        // old 64x32 declaration made every UV sample the wrong region (left limbs at
        // texOffs(32,48)/(16,48) resolved to v 1.5..2.0 — outside a 32-tall texture), and
        // renderers that baked this from ModelLayers.PLAYER (64x64) smeared the face.
        return LayerDefinition.create(meshdefinition, 128, 64);
    }

    /**
     * Override to change body rotation.
     *
     * @return the rotation.
     */
    public float getActualRotation(@NotNull final CitizenRenderState state)
    {
        return 0;
    }

    /**
     * Check if the citizen is supposed to be working.
     * @param state the citizen render state to check.
     * @return true if so.
     */
    public boolean isWorking(final CitizenRenderState state)
    {
        return state.working;
    }

    /**
     * Check if the hat should be displayed.
     * @param state the citizen render state.
     * @return true if so.
     */
    public boolean displayHat(final CitizenRenderState state)
    {
        if (state.pose == Pose.SLEEPING || !state.headEquipment.isEmpty())
        {
            return false;
        }
        return !state.hasCitizenDataView || (state.displayArmorHeadEmpty && !state.hasCustomTextureUUID);
    }
}
