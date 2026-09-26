package com.ldtteam.blockui.controls;

import com.ldtteam.blockui.BOGuiGraphics;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneParams;
import com.ldtteam.blockui.controls.AbstractTextBuilder.AutomaticTooltipBuilder;
import com.ldtteam.blockui.controls.Tooltip.AutomaticTooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;

/**
 * Control to render an entity as an icon
 */
public class EntityIcon extends Pane
{
    @Nullable
    private Entity entity;
    private int count = 1;
    private float yaw = 30;
    private float pitch = -10;
    private float headyaw = 0;

    public EntityIcon()
    {
        super();
    }

    public EntityIcon(final PaneParams params)
    {
        super(params);

        final Identifier entityName = params.getResource("entity");
        if (entityName != null)
        {
            setEntity(entityName);
        }

        this.count = params.getInteger("count", this.count);
        this.yaw = params.getFloat("yaw", this.yaw);
        this.pitch = params.getFloat("pitch", this.pitch);
        this.headyaw = params.getFloat("head", this.headyaw);
    }

    public void setEntity(@NotNull Identifier entityId)
    {
        // PORT26: Registry#get(Identifier) now returns Optional<Holder.Reference<T>>;
        // getValue(Identifier) is the direct @Nullable T accessor.
        final EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getValue(entityId);
        if (entityType != null)
        {
            setEntity(entityType);
        }
        else
        {
            resetEntity();
        }
    }

    public void setEntity(@NotNull EntityType<?> type)
    {
        // PORT26: EntityType#create(Level) now needs an EntitySpawnReason (GUI-only entity,
        // LOAD is the closest semantic match and only feeds spawn config, not gameplay).
        final Entity entity = mc.level != null ? type.create(mc.level, EntitySpawnReason.LOAD) : null;

        if (entity != null)
        {
            setEntity(entity);
        }
        else
        {
            resetEntity();
        }
    }

    public void setEntity(@NotNull Entity entity)
    {
        this.entity = entity;
        if (onHover instanceof final AutomaticTooltip tooltip)
        {
            tooltip.setText(this.entity.getDisplayName());
        }
    }

    public void resetEntity()
    {
        this.entity = null;
        if (onHover instanceof final AutomaticTooltip tooltip)
        {
            tooltip.clearText();
        }
    }

    public void setCount(final int count)
    {
        this.count = count;
    }

    public void setYaw(final float yaw)
    {
        this.yaw = yaw;
    }

    public void setPitch(final float pitch)
    {
        this.pitch = pitch;
    }

    @Override
    public void drawSelf(final BOGuiGraphics target, final double mx, final double my)
    {
        final Matrix3x2fStack ms = target.pose();

        if (this.entity != null)
        {
            ms.pushMatrix();
            ms.translate(x, y);

            final AABB bb = this.entity.getBoundingBox();
            final float scale = (float) (getHeight() / bb.getYsize() / 1.5);
            final int cx = (getWidth() / 2);
            final int by = getHeight();
            final int offsetY = 2;
            drawEntity(target, cx, by - offsetY, scale, this.headyaw, this.yaw, this.pitch, this.entity);

            if (this.count != 1)
            {
                // PORT26: count label now drawn through the extractor; the old z-offset
                // translation + drawInBatch is replaced by a direct text call after the entity.
                final String s = String.valueOf(this.count);
                ms.pushMatrix();
                ms.translate(getWidth(), getHeight());
                ms.scale(0.75F, 0.75F);
                target.drawString(s,
                    (float) (-4 - mc.font.width(s)),
                    (float) (-mc.font.lineHeight),
                    16777215,
                    true);
                ms.popMatrix();
            }

            ms.popMatrix();
        }
    }

    @Override
    public void onUpdate()
    {
        if (this.onHover == null && this.entity != null)
        {
            new AutomaticTooltipBuilder().hoverPane(this).build().setText(this.entity.getDisplayName());
        }
    }
}
