package com.minecolonies.api.client.render.modeltype;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * PORT26: render state for citizens (extracted from the entity before rendering).
 * Carries all citizen-specific data the models/layers previously pulled from the entity directly.
 */
public class CitizenRenderState extends HumanoidRenderState
{
    public boolean isFemale;
    public boolean hasCitizenDataView;
    /** citizenDataView.getCustomTextureUUID() != null — hides the model head/hat. */
    public boolean hasCustomTextureUUID;
    /** citizenDataView.getCustomTexture() != null — swaps to the custom-texture model. */
    public boolean hasCustomTexture;
    /** citizenDataView.getDisplayArmor(EquipmentSlot.HEAD).isEmpty(). */
    public boolean displayArmorHeadEmpty;
    /** render metadata contains "working". */
    public boolean working;
    /**
     * PORT26: raw render metadata string of the entity (comma-less concatenation of the
     * active meta tags, e.g. "rodfish") — the models use substring {@code contains}
     * checks on it, exactly like the 1.21.1 models did on the entity string.
     */
    public String  renderMetadata = "";
    public int     civilianId;
    public Identifier modelTypeId;

    // ------------------------------------------------------------------
    // PORT26: renderer-extracted data (filled by RenderBipedCitizen).
    // ------------------------------------------------------------------

    /** Resolved texture of this citizen (custom texture if set, else model type texture). */
    public Identifier texture;

    /**
     * PORT26 FIX (model swap leak): the per-citizen model selected by the renderer.
     * LevelRenderer extracts the render state of EVERY visible citizen before submitting
     * any of them, so the old "assign this.model during extractRenderState" approach leaked
     * the last-extracted citizen's model into all rendered citizens (random gender /
     * job-model switches). The model now travels per-state and is swapped onto the renderer
     * for the duration of {@code submit()} only.
     */
    public CitizenModel model;

    /** citizenDataView.getCustomTextureUUID() — drives the player-skin head of the armor layer. */
    public UUID customTextureUUID;

    /** citizenDataView.getInventory() != null — armor layer gate (inventory view not yet synced). */
    public boolean hasCitizenInventory;

    /** citizenDataView.getDisplayArmor(slot) — the visually displayed armor (santa hat, guard armor). */
    public ItemStack displayArmorHead = ItemStack.EMPTY;
    public ItemStack displayArmorChest = ItemStack.EMPTY;
    public ItemStack displayArmorLegs = ItemStack.EMPTY;
    public ItemStack displayArmorFeet = ItemStack.EMPTY;

    /** citizenDataView.hasVisibleStatus() — status icon under the name tag. */
    public boolean showStatusIcon;
    /** citizenDataView.getStatusIcon(). */
    public Identifier statusIcon;
}
