package com.minecolonies.core.client.render;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.other.NewBobberEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Determines how the fish hook is rendered.
 *
 * <p>PORT26: render-state rewrite, mirrors the vanilla {@code FishingHookRenderer}:
 * the hook quad and the fishing line are submitted as custom geometry through the
 * submit collector, and the line origin (the citizen's hand) is extracted into the
 * render state instead of being recomputed against the entity at draw time.</p>
 */
public class RenderFishHook extends EntityRenderer<NewBobberEntity, RenderFishHook.State>
{
    /**
     * The resource location containing the hook texture.
     * PORT26: vanilla texture path — the 1.21.1 code pointed at
     * "textures/entity/fishing_hook.png", which exists neither in vanilla nor in the
     * minecolonies assets ("textures/entity/fishing/fishing_hook.png" is the real path).
     */
    private static final Identifier TEXTURE_LOCATION = Identifier.withDefaultNamespace("textures/entity/fishing/fishing_hook.png");

    /**
     * The render type of the hook.
     */
    private static final RenderType RENDER_TYPE = RenderTypes.entityCutoutCull(TEXTURE_LOCATION);

    /**
     * PORT26: render state of the citizen fishing hook — carries the (camera-relative)
     * line origin and whether a citizen owner exists at all.
     */
    public static class State extends FishingHookRenderState
    {
        public boolean hasCitizenOwner;
    }

    /**
     * Required constructor, sets the RenderManager.
     *
     * @param context context that we use.
     */
    public RenderFishHook(final EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    protected boolean affectedByCulling(@NotNull final NewBobberEntity entity)
    {
        // PORT26: Entity#noCulling was removed — never frustum-cull the fish hook
        // (same behavior as the old this.noCulling = true on the entity).
        return false;
    }

    @NotNull
    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(@NotNull final NewBobberEntity entity, @NotNull final State state, final float partialTicks)
    {
        super.extractRenderState(entity, state, partialTicks);

        if (entity.getOwner() instanceof AbstractEntityCitizen citizen)
        {
            state.hasCitizenOwner = true;
            state.lineOriginOffset = getCitizenHandPos(citizen, partialTicks).subtract(entity.getPosition(partialTicks).add(0.0, 0.25, 0.0));
        }
        else
        {
            state.hasCitizenOwner = false;
            state.lineOriginOffset = Vec3.ZERO;
        }
    }

    /**
     * The hand position of the citizen holding the rod (third person math of the old
     * 1.21.1 renderer / vanilla FishingHookRenderer#getPlayerHandPos).
     *
     * @param citizen      the citizen owning the hook.
     * @param partialTicks partial ticks.
     * @return the hand position.
     */
    private static Vec3 getCitizenHandPos(final AbstractEntityCitizen citizen, final float partialTicks)
    {
        int invert = citizen.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        final ItemStack mainHandItem = citizen.getMainHandItem();
        if (!mainHandItem.is(Items.FISHING_ROD))
        {
            invert = -invert;
        }

        final float citizenYRot = Mth.lerp(partialTicks, citizen.yBodyRotO, citizen.yBodyRot) * ((float) Math.PI / 180F);
        final double sin = Mth.sin(citizenYRot);
        final double cos = Mth.cos(citizenYRot);
        final float scale = citizen.getScale();
        final double rightOffset = invert * 0.35D * scale;
        final double forwardOffset = 0.8D * scale;
        final float yOffset = citizen.isCrouching() ? -0.1875F : 0.0F;

        // PORT26: third-person hand position of the 1.21.1 renderer (the old code computed the
        // swing animation but only used the body rotation, exactly like vanilla's third-person branch).
        return citizen.getEyePosition(partialTicks)
                 .add(-cos * rightOffset - sin * forwardOffset, yOffset - 0.45D * scale, -sin * rightOffset + cos * forwardOffset);
    }

    @Override
    public void submit(
      @NotNull final State state,
      @NotNull final PoseStack poseStack,
      @NotNull final SubmitNodeCollector submitNodeCollector,
      @NotNull final CameraRenderState camera)
    {
        if (!state.hasCitizenOwner)
        {
            // the 1.21.1 renderer drew nothing when the owner was no citizen
            return;
        }

        poseStack.pushPose();
        poseStack.pushPose();
        poseStack.scale(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(camera.orientation);
        submitNodeCollector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> {
            vertex(buffer, pose, state.lightCoords, 0.0F, 0, 0, 1);
            vertex(buffer, pose, state.lightCoords, 1.0F, 0, 1, 1);
            vertex(buffer, pose, state.lightCoords, 1.0F, 1, 1, 0);
            vertex(buffer, pose, state.lightCoords, 0.0F, 1, 0, 0);
        });
        poseStack.popPose();

        final float xa = (float) state.lineOriginOffset.x;
        final float ya = (float) state.lineOriginOffset.y;
        final float za = (float) state.lineOriginOffset.z;
        final float width = Minecraft.getInstance().gameRenderer.getGameRenderState().windowRenderState.appropriateLineWidth;
        submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
            for (int i = 0; i < 16; i++)
            {
                final float a0 = fraction(i, 16);
                final float a1 = fraction(i + 1, 16);
                stringVertex(xa, ya, za, buffer, pose, a0, a1, width);
                stringVertex(xa, ya, za, buffer, pose, a1, a0, width);
            }
        });
        poseStack.popPose();

        super.submit(state, poseStack, submitNodeCollector, camera);
    }

    private static float fraction(final int first, final int second)
    {
        return (float) first / (float) second;
    }

    private static void vertex(final VertexConsumer buffer, final Pose pose, final int lightCoords, final float x, final int y, final int u, final int v)
    {
        buffer.addVertex(pose, x - 0.5F, (float) y - 0.5F, 0.0F)
          .setColor(-1)
          .setUv((float) u, (float) v)
          .setOverlay(OverlayTexture.NO_OVERLAY)
          .setLight(lightCoords)
          .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static void stringVertex(
      final float xa, final float ya, final float za,
      final VertexConsumer buffer, final Pose pose,
      final float aa, final float nexta, final float width)
    {
        final float x = xa * aa;
        final float y = ya * (aa * aa + aa) * 0.5F + 0.25F;
        final float z = za * aa;
        float nx = xa * nexta - x;
        float ny = ya * (nexta * nexta + nexta) * 0.5F + 0.25F - y;
        float nz = za * nexta - z;
        final float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= length;
        ny /= length;
        nz /= length;
        buffer.addVertex(pose, x, y, z).setColor(-16777216).setNormal(pose, nx, ny, nz).setLineWidth(width);
    }
}
