package com.minecolonies.api.client.render.modeltype;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Amazon model.
 *
 * <p>PORT26 FIX (Task 47-c, "amazon head too high + gap"): the 1.21.1 override used to
 * re-apply pivot-Y offsets every frame (head.y -= 3, legs -= 3.5, arms -= 2) because vanilla
 * 1.21.1 {@code HumanoidModel#setupAnim} clobbered the part pivots with hardcoded values
 * (head 0 / body 0 / arms 2 / legs 12) each frame, overriding the baked geometry. In 26.1
 * the render pipeline resets every {@link ModelPart} to its <b>baked</b> pose before
 * {@code setupAnim} runs ({@code Model#setupAnim} -> {@code resetPose()}), and vanilla no
 * longer writes pivots -- the amazon geometry already bakes the correct positions
 * (head pivot y = -3 so the head cube ends exactly at the body's top edge, legs at y = 9,
 * arms at y = 0). Keeping the compensations applied them a second time: head ended up at
 * y = -6 (3px too high, leaving a visible gap between head and torso), legs floated 3.5px
 * and arms sat 2px too high. The compensations are therefore removed.</p>
 */
public class AmazonModel extends HumanoidModel<RaiderRenderState>
{
    public AmazonModel(final ModelPart part)
    {
        super(part);
    }
}
