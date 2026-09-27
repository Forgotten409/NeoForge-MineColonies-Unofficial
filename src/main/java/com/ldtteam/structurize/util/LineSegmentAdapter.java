package com.ldtteam.structurize.util;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;

/**
 * PORT26-compat (Iris &amp; shader packs): adapts simple endpoint-pair line
 * emission ({@code addVertex().setColor()} twice per segment, no normals) to
 * vanilla's {@code RenderTypes.lines()} vertex format
 * {@code POSITION_COLOR_NORMAL_LINE_WIDTH} (mode LINES — the GPU expands the
 * segment to a screen-space quad, the shader derives the direction from
 * {@code Position + Normal}, see {@code core/rendertype_lines.vsh}).
 *
 * <p>Line buffers always come in vertex <em>pairs</em> (one GL line each), so
 * this consumer buffers a vertex (position + color) until its partner
 * completes, computes the segment direction once the second position is
 * known, and writes both vertices with the shared normal and a pixel line
 * width. All other {@link VertexConsumer} methods pass straight through
 * (callers of our line types only use position + color).</p>
 */
public class LineSegmentAdapter implements VertexConsumer
{
    /**
     * Default pixel width of the fallback lines. Vanilla's block-hitbox
     * outline renders at 2.0; 2.5 keeps our boxes clearly readable.
     */
    public static final float DEFAULT_PIXEL_WIDTH = 2.5F;

    private final VertexConsumer parent;
    private final float pixelWidth;

    private boolean hasFirst = false;
    private float x1, y1, z1;
    private int r1, g1, b1, a1;

    public LineSegmentAdapter(final VertexConsumer parent)
    {
        this(parent, DEFAULT_PIXEL_WIDTH);
    }

    public LineSegmentAdapter(final VertexConsumer parent, final float pixelWidth)
    {
        this.parent = parent;
        this.pixelWidth = pixelWidth;
    }

    @Override
    public VertexConsumer addVertex(final float x, final float y, final float z)
    {
        if (!hasFirst)
        {
            hasFirst = true;
            x1 = x;
            y1 = y;
            z1 = z;
            return this;
        }

        // second vertex of the pair — the pair is complete, emit it with the
        // segment normal (the lines shader wants the raw model-space direction
        // of the segment: it projects Position and Position + Normal to derive
        // the screen-space line direction). A zero-length segment is dropped.
        final Vector3f normal = new Vector3f(x - x1, y - y1, z - z1);
        if (normal.lengthSquared() < 1.0E-6F)
        {
            hasFirst = false;
            return this;
        }
        normal.normalize();

        parent.addVertex(x1, y1, z1).setColor(r1, g1, b1, a1).setNormal(normal.x, normal.y, normal.z).setLineWidth(pixelWidth);
        parent.addVertex(x, y, z).setColor(r1, g1, b1, a1).setNormal(normal.x, normal.y, normal.z).setLineWidth(pixelWidth);
        hasFirst = false;
        return this;
    }

    @Override
    public VertexConsumer setColor(final int r, final int g, final int b, final int a)
    {
        r1 = r;
        g1 = g;
        b1 = b;
        a1 = a;
        return this;
    }

    @Override
    public VertexConsumer setColor(final int color)
    {
        // PORT26: the int packed color is ARGB (see BufferBuilder#setColor).
        setColor((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >> 24) & 0xFF);
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(final float width)
    {
        // ignored — the adapter owns the (pixel) width; our callers pass
        // world-unit widths meant for the triangle-expansion types
        return this;
    }

    @Override
    public VertexConsumer setUv(final float u, final float v)
    {
        return this;
    }

    @Override
    public VertexConsumer setUv1(final int u, final int v)
    {
        return this;
    }

    @Override
    public VertexConsumer setUv2(final int u, final int v)
    {
        return this;
    }

    @Override
    public VertexConsumer setNormal(final float x, final float y, final float z)
    {
        // normals are derived from the segment pair — explicit normals are ignored
        return this;
    }
}
