package com.ldtteam.blockui.util.color;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Wrapper for having default color for vertex consumer
 */
public class ColouredVertexConsumer implements VertexConsumer
{
    protected final VertexConsumer parent;
    public IColour defaultColor = null;

    public ColouredVertexConsumer(final VertexConsumer parent)
    {
        this.parent = parent;
    }

    @Override
    public ColouredVertexConsumer addVertex(final float x, final float y, final float z)
    {
        parent.addVertex(x, y, z);
        return this;
    }

    @Override
    public ColouredVertexConsumer setColor(final int r, final int g, final int b, final int a)
    {
        parent.setColor(r, g, b, a);
        return this;
    }

    @Override
    public ColouredVertexConsumer setColor(final int color)
    {
        // PORT26: new packed-color overload — the int is ARGB
        // (see BufferBuilder#setColor -> putRgba -> ARGB.toABGR).
        parent.setColor(color);
        return this;
    }

    @Override
    public ColouredVertexConsumer setLineWidth(final float width)
    {
        // PORT26: new abstract method on VertexConsumer (LINE_WIDTH element).
        parent.setLineWidth(width);
        return this;
    }

    /**
     * Applies previously set defaultColor, will shamelessly NPE if you forgot to set it
     */
    public ColouredVertexConsumer setDefaultColor()
    {
        defaultColor.writeIntoBuffer(this);
        return this;
    }

    @Override
    public ColouredVertexConsumer setUv(final float u, final float v)
    {
        parent.setUv(u, v);
        return this;
    }

    @Override
    public ColouredVertexConsumer setUv1(final int u, final int v)
    {
        parent.setUv1(u, v);
        return this;
    }

    @Override
    public ColouredVertexConsumer setUv2(final int u, final int v)
    {
        parent.setUv2(u, v);
        return this;
    }

    @Override
    public ColouredVertexConsumer addVertex(final PoseStack.Pose pose, final float x, final float y, final float z)
    {
        // PORT26: matrix-carrying overload (default on VertexConsumer returns the raw type,
        // breaking the default-color chaining) — keep our covariant return.
        parent.addVertex(pose, x, y, z);
        return this;
    }

    @Override
    public ColouredVertexConsumer setNormal(final float x, final float y, final float z)
    {
        parent.setNormal(x, y, z);
        return this;
    }
}
