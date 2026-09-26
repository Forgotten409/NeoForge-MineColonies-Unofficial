package com.minecolonies.core.client.render;

import com.minecolonies.api.client.render.modeltype.CitizenModel;
import com.minecolonies.api.client.render.modeltype.CitizenRenderState;
import com.minecolonies.api.client.render.modeltype.IModelType;
import com.minecolonies.api.client.render.modeltype.ModModelTypes;
import com.minecolonies.api.client.render.modeltype.registry.IModelTypeRegistry;
import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.apiimp.initializer.ModModelTypeInitializer;
import com.minecolonies.core.client.render.worldevent.RenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.minecolonies.core.event.ClientRegistryHandler; // PORT26 FIX (no faces): bake our 128x64 citizen layer
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import org.jetbrains.annotations.NotNull;

/**
 * Renderer for the citizens.
 *
 * <p>PORT26: rewritten to the render-state architecture
 * ({@code MobRenderer<AbstractEntityCitizen, CitizenRenderState, CitizenModel>}). All
 * citizen-specific data the models and layers need is extracted into
 * {@link CitizenRenderState} in {@link #extractRenderState}; the per-frame model swap of
 * the old {@code render()} override now happens there too, because
 * {@code LivingEntityRenderer#submit} reads {@code this.model} when rendering.</p>
 */
public class RenderBipedCitizen extends MobRenderer<AbstractEntityCitizen, CitizenRenderState, CitizenModel>
{
    private static final double SHADOW_SIZE = 0.5F;
    public static  boolean isItGhostTime    = false;

    /**
     * Ghost tint (30% white) for the (currently disabled) halloween ghost mode.
     */
    private static final int GHOST_TINT = ARGB.color(77, 255, 255, 255);

    /**
     * Renders model, see {@link MobRenderer}.
     *
     * @param context the context for this Renderer.
     */
    public RenderBipedCitizen(final EntityRendererProvider.Context context)
    {
        // PORT26 FIX (no faces): the fallback citizen model must be baked from our CITIZEN
        // layer (128x64 — matches the citizen skin canvases). ModelLayers.PLAYER is 64x64
        // and smeared every UV (face included) across the 128x64 textures.
        super(context, new CitizenModel(context.bakeLayer(ClientRegistryHandler.CITIZEN)), (float) SHADOW_SIZE);
        this.addLayer(new CitizenArmorLayer(this, context));
        // PORT26: ItemInHandLayer takes only the parent now — the item renderer comes from the context.
        this.addLayer(new ItemInHandLayer<>(this));
        ModModelTypeInitializer.init(context);
    }

    @NotNull
    @Override
    public CitizenRenderState createRenderState()
    {
        return new CitizenRenderState();
    }

    @Override
    public void extractRenderState(
      @NotNull final AbstractEntityCitizen citizen,
      @NotNull final CitizenRenderState state,
      final float partialTicks)
    {
        super.extractRenderState(citizen, state, partialTicks);

        // PORT26: the humanoid equipment/item extraction previously done inside the model
        // setup (crouching, arm poses, held items, armor equipment stacks) now comes from
        // the shared vanilla humanoid extractor.
        HumanoidMobRenderer.extractHumanoidRenderState(citizen, state, partialTicks, this.itemModelResolver);

        // Citizen-specific data (previously pulled from the entity by the models directly).
        state.isFemale = citizen.isFemale();
        final ICitizenDataView citizenDataView = citizen.getCitizenDataView();
        state.hasCitizenDataView = citizenDataView != null;
        if (citizenDataView != null)
        {
            state.hasCustomTextureUUID = citizenDataView.getCustomTextureUUID() != null;
            state.customTextureUUID = citizenDataView.getCustomTextureUUID();
            state.hasCustomTexture = citizenDataView.getCustomTexture() != null;
            state.displayArmorHeadEmpty = citizenDataView.getDisplayArmor(EquipmentSlot.HEAD).isEmpty();
            state.hasCitizenInventory = citizenDataView.getInventory() != null;
            state.displayArmorHead = citizenDataView.getDisplayArmor(EquipmentSlot.HEAD);
            state.displayArmorChest = citizenDataView.getDisplayArmor(EquipmentSlot.CHEST);
            state.displayArmorLegs = citizenDataView.getDisplayArmor(EquipmentSlot.LEGS);
            state.displayArmorFeet = citizenDataView.getDisplayArmor(EquipmentSlot.FEET);
            state.showStatusIcon = citizenDataView.hasVisibleStatus();
            state.statusIcon = citizenDataView.getStatusIcon();
        }

        // PORT26: the render metadata is a raw (comma-less) string, the models check it with substring contains.
        final String renderMetadata = citizen.getRenderMetadata();
        state.renderMetadata = renderMetadata;
        state.working = renderMetadata.contains("working");

        state.civilianId = citizen.getCivilianID();
        state.modelTypeId = citizen.getModelType();

        // PORT26: arm poses were set on the model fields in the old render() override.
        state.rightArmPose = RenderUtils.getArmPose(citizen, InteractionHand.MAIN_HAND);
        state.leftArmPose = RenderUtils.getArmPose(citizen, InteractionHand.OFF_HAND);

        // PORT26 FIX (model swap leak): the per-frame model swap of the old setupMainModelFrom()
        // now resolves into the render state — see submit() for why assigning this.model here
        // (the old approach) leaked the last-extracted citizen's model into every citizen.
        state.model = resolveModelFor(citizen);

        // PORT26: texture resolution (was getTextureLocation(entity)).
        state.texture = getTextureFrom(citizen);
    }

    /**
     * Selects the model to render this citizen with.
     *
     * @param citizen the citizen entity.
     * @return the model for this citizen (never null — falls back to this renderer's default).
     */
    private CitizenModel resolveModelFor(@NotNull final AbstractEntityCitizen citizen)
    {
        final IModelType modelType = IModelTypeRegistry.getInstance().getModelType(citizen.getModelType());
        CitizenModel citizenModel = citizen.isFemale() ? modelType.getFemaleModel() : modelType.getMaleModel();
        if (citizenModel == null)
        {
            // no model for this model type — fall back to the other gender, then to the
            // plain player-shaped citizen model baked in the constructor.
            citizenModel = citizen.isFemale() ? modelType.getMaleModel() : modelType.getFemaleModel();
            if (citizenModel == null)
            {
                citizenModel = this.getModel();
            }
        }

        if (citizen.getCitizenDataView() != null && citizen.getCitizenDataView().getCustomTexture() != null)
        {
            citizenModel = IModelTypeRegistry.getInstance().getModelType(ModModelTypes.CUSTOM_ID).getMaleModel();
            if (citizenModel == null)
            {
                citizenModel = this.getModel();
            }
        }

        return citizenModel;
    }

    /**
     * PORT26 FIX (model swap leak): LevelRenderer extracts the render state of every visible
     * citizen BEFORE submitting any of them (extractEntities fills a list; submitEntities
     * renders it afterwards). Mutating this.model during extraction therefore leaked the
     * last-extracted citizen's model into every rendered citizen — the "citizens randomly
     * switch gender / job model" bug. The selected model travels in the render state and
     * is swapped onto the renderer for the duration of this submit only (single-threaded,
     * always restored).
     */
    @Override
    public void submit(
      @NotNull final CitizenRenderState state,
      @NotNull final PoseStack poseStack,
      @NotNull final SubmitNodeCollector submitNodeCollector,
      @NotNull final CameraRenderState camera)
    {
        final CitizenModel renderModel = state.model;
        if (renderModel != null && renderModel != this.model)
        {
            final CitizenModel original = this.model;
            this.model = renderModel;
            try
            {
                super.submit(state, poseStack, submitNodeCollector, camera);
            }
            finally
            {
                this.model = original;
            }
        }
        else
        {
            super.submit(state, poseStack, submitNodeCollector, camera);
        }
    }

    /**
     * Resolves the texture for this citizen.
     *
     * @param citizen the citizen entity.
     * @return the texture.
     */
    private static Identifier getTextureFrom(@NotNull final AbstractEntityCitizen citizen)
    {
        if (citizen.getCitizenDataView() != null && citizen.getCitizenDataView().getCustomTexture() != null)
        {
            return citizen.getCitizenDataView().getCustomTexture();
        }
        return citizen.getTexture();
    }

    /**
     * PORT26: ghost mode (halloween, currently disabled upstream) — the 1.21.1 version
     * hacked a 30% shader color via RenderSystem.setShaderColor (removed in 26.1); the
     * render-state equivalent is a translucent render type plus a tinted color.
     */
    @Override
    protected RenderType getRenderType(final CitizenRenderState state, final boolean isBodyVisible, final boolean forceTransparent, final boolean appearGlowing)
    {
        if (isItGhostTime)
        {
            // vanilla entity translucent render type (blending) so the ghost tint alpha applies
            return net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(state.texture);
        }
        return super.getRenderType(state, isBodyVisible, forceTransparent, appearGlowing);
    }

    @Override
    protected int getModelTint(final CitizenRenderState state)
    {
        return isItGhostTime ? GHOST_TINT : super.getModelTint(state);
    }

    /**
     * PORT26: the status icon under the name tag (was the renderNameTag override).
     * The base class submits the name tag itself; the icon is an additional textured quad
     * submitted as custom geometry right below it.
     */
    @Override
    protected void submitNameDisplay(
      @NotNull final CitizenRenderState state,
      @NotNull final PoseStack poseStack,
      @NotNull final SubmitNodeCollector submitNodeCollector,
      @NotNull final CameraRenderState camera)
    {
        super.submitNameDisplay(state, poseStack, submitNodeCollector, camera);

        if (state.showStatusIcon && state.nameTag != null && state.nameTagAttachment != null && state.distanceToCameraSq <= 4096.0D)
        {
            poseStack.pushPose();
            poseStack.translate(state.nameTagAttachment.x, state.nameTagAttachment.y + 0.9, state.nameTagAttachment.z);
            poseStack.mulPose(camera.orientation);
            poseStack.scale(0.025F, -0.025F, 0.025F);

            submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.worldEntityIcon(state.statusIcon), (pose, buffer) -> {
                addIconVertex(buffer, pose, -5, 0, 0, 0, 0);
                addIconVertex(buffer, pose, -5, 10, 0, 0, 1);
                addIconVertex(buffer, pose, 5, 10, 0, 1, 1);
                addIconVertex(buffer, pose, 5, 0, 0, 1, 0);
            });

            poseStack.popPose();
        }
    }

    private static void addIconVertex(
      final VertexConsumer buffer,
      final PoseStack.Pose pose,
      final float x, final float y, final float z,
      final float u, final float v)
    {
        // PORT26: the icon render type uses the POSITION_TEX format (position + uv only).
        buffer.addVertex(pose, x, y, z).setUv(u, v);
    }

    @NotNull
    @Override
    public Identifier getTextureLocation(final CitizenRenderState state)
    {
        // PORT26: the texture is resolved in extractRenderState (the state carries it).
        return state.texture;
    }
}
