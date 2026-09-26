package com.ldtteam.blockui;

import com.ldtteam.blockui.util.cursor.CursorUtils;
import com.ldtteam.blockui.views.BOWindow;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

/**
 * Wraps MineCrafts GuiScreen for Blockout's Window — PORT 26.1.2.
 *
 * <p>Originally (1.21.1) {@code render()} replaced the vanilla projection matrix with a
 * framebuffer-sized orthographic projection, created a private {@code BOGuiGraphics} over
 * a fresh {@code PoseStack} (window translate + custom render scale) and drew the window
 * immediately through the old tessellation pipeline.
 *
 * <p>In 26.1.2 that whole approach is gone. {@code render(GuiGraphics,...)} does not exist
 * anymore — screens override {@code extractRenderState(GuiGraphicsExtractor,...)} and queue
 * render states. The window translate/scale is expressed through the extractor's
 * {@link org.joml.Matrix3x2fStack} ({@code pushMatrix/translate/scale/popMatrix}) and every
 * primitive (fill, text, blit, item) is queued through the same extractor. The custom
 * framebuffer projection hijack is therefore no longer needed.
 *
 * <p>Input events use the new records: {@link KeyEvent} ({@code key()} /
 * {@code modifiers()}), {@link MouseButtonEvent} ({@code x()} / {@code y()} /
 * {@code button()}), and {@code charTyped(CharacterEvent)} ({@code codepoint()}).
 */
public class BOScreen extends Screen
{
    protected double renderScale = 1.0d;
    protected double mcScale = 1.0d;
    protected BOWindow window;
    protected double x = 0;
    protected double y = 0;
    public static boolean isMouseLeftDown = false;
    protected boolean isOpen = false;
    protected int framebufferWidth;
    protected int framebufferHeight;
    protected int absoluteMouseX;
    protected int absoluteMouseY;

    /**
     * Create a GuiScreen from a Blockout window.
     *
     * @param w blockout window.
     */
    public BOScreen(final BOWindow w)
    {
        super(Component.literal("Blockout GUI"));
        window = w;
    }

    @Override
    public void extractBackground(final GuiGraphicsExtractor gui, final int mx, final int my, final float f)
    {
        // PORT26: vanilla automatically extracts a screen background sized with this.width/
        // this.height — but this class repurposes those fields as the *window* size after the
        // first frame, which drew a window-sized dark square in the top-left corner instead
        // of a full-screen gradient. The 1.21.1 BOScreen never rendered the vanilla background
        // either (the super.renderBackground call was commented out — blockui windows draw
        // their own lightbox), so skip it and only keep the deferred subtitles extraction.
        if (minecraft != null)
        {
            minecraft.gui.extractDeferredSubtitles();
        }
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor gui, final int mx, final int my, final float f)
    {
        if (minecraft == null || !isOpen) // should never happen though
        {
            return;
        }

        absoluteMouseX = mx;
        absoluteMouseY = my;
        framebufferWidth = minecraft.getWindow().getGuiScaledWidth();
        framebufferHeight = minecraft.getWindow().getGuiScaledHeight();

        mcScale = minecraft.getWindow().getGuiScale();
        renderScale = window.getRenderType().calcRenderScale(minecraft.getWindow(), window);

        if (window.hasLightbox() && minecraft.screen == this)
        {
            gui.fillGradient(0, 0, framebufferWidth, framebufferHeight, -1072689136, -804253680);
        }

        width = window.getWidth();
        height = window.getHeight();
        x = Math.floor((framebufferWidth - width * renderScale) / 2.0d);
        y = Math.floor((framebufferHeight - height * renderScale) / 2.0d);

        final BOGuiGraphics target = new BOGuiGraphics(minecraft, gui);
        target.resetCursorFrame();

        // window transform: translate to centered position, apply render scale
        final var pose = gui.pose();
        pose.pushMatrix();
        pose.translate((float) x, (float) y);
        pose.scale((float) renderScale, (float) renderScale);

        try
        {
            window.draw(target, calcRelativeX(mx), calcRelativeY(my));

            if (minecraft.screen == this)
            {
                int debugX = (int) (-x / renderScale) + 3;
                if (Pane.debugging)
                {
                    debugX = target.drawString(
                        "XML: %s Scaling: %s (vanilla: %.2f our: %.2f) "
                            .formatted(window.getXmlResourceLocation(), window.getRenderType().name(), mcScale, renderScale),
                        debugX,
                        -minecraft.font.lineHeight,
                        Color.getByName("white"));
                }
                target.applyCursor(debugX);
            }

            // PORT26: the new GUI pipeline batches render states per stratum (and per type
            // within a stratum), so drawLast overlays (tooltips!) submitted in the same
            // stratum as the window content can end up underneath item render states.
            // Vanilla queues its own tooltips in a fresh stratum — do the same.
            gui.nextStratum();
            window.drawLast(target, calcRelativeX(mx), calcRelativeY(my));
        }
        catch (final Exception e)
        {
            final CrashReport crashReport = CrashReport.forThrowable(e, "Rendering BO screen");
            final CrashReportCategory category = crashReport.addCategory("BO screen rendering details");
            category.setDetail("XML res loc", () -> window.getXmlResourceLocation().toString());
            category.setDetail("Scaling mode (window render type)", () -> window.getRenderType().name());
            category.setDetail("Vanilla gui scale", () -> Double.toString(mcScale));
            category.setDetail("BO gui scale", () -> Double.toString(renderScale));
            throw new ReportedException(crashReport);
        }
        finally
        {
            pose.popMatrix();
        }
    }

    @Override
    public boolean keyPressed(final KeyEvent event)
    {
        // keys without printable representation
        final int key = event.key();
        if (key >= 0 && key <= GLFW.GLFW_KEY_LAST)
        {
            try
            {
                return window.onKeyTyped('\0', key);
            }
            catch (final Exception e)
            {
                final CrashReport crashReport = CrashReport.forThrowable(e, "KeyPressed event for BO screen");
                final CrashReportCategory category = crashReport.addCategory("BO screen key event details");
                category.setDetail("XML res loc", () -> window.getXmlResourceLocation().toString());
                category.setDetail("GLFW key value", () -> Integer.toString(key));
                throw new ReportedException(crashReport);
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(final net.minecraft.client.input.CharacterEvent event)
    {
        final char ch = (char) event.codepoint();
        try
        {
            return window.onKeyTyped(ch, Character.toUpperCase(ch));
        }
        catch (final Exception e)
        {
            final CrashReport crashReport = CrashReport.forThrowable(e, "CharTyped event for BO screen");
            final CrashReportCategory category = crashReport.addCategory("BO screen char event details");
            category.setDetail("XML res loc", () -> window.getXmlResourceLocation().toString());
            category.setDetail("Char value", () -> Character.toString(ch));
            throw new ReportedException(crashReport);
        }
    }

    @Override
    public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick)
    {
        final double mx = calcRelativeX(event.x());
        final double my = calcRelativeY(event.y());
        final int keyCode = event.button();
        try
        {
            if (keyCode == GLFW.GLFW_MOUSE_BUTTON_LEFT)
            {
                // Adjust coordinate to origin of window
                isMouseLeftDown = true;
                return window.click(mx, my);
            }
            else if (keyCode == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
            {
                return window.rightClick(mx, my);
            }
        }
        catch (final Exception e)
        {
            final CrashReport crashReport = CrashReport.forThrowable(e, "MousePressed event for BO screen");
            final CrashReportCategory category = crashReport.addCategory("BO screen mouse event details");
            category.setDetail("XML res loc", () -> Objects.toString(window.getXmlResourceLocation()));
            category.setDetail("GLFW mouse key value", () -> Integer.toString(keyCode));
            throw new ReportedException(crashReport);
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(final double mx, final double my, final double scrollHorizontalDiff, final double scrollVerticalDiff)
    {
        if (scrollVerticalDiff != 0)
        {
            try
            {
                return window.scrollInput(scrollHorizontalDiff * 10, scrollVerticalDiff * 10, calcRelativeX(mx), calcRelativeY(my));
            }
            catch (final Exception e)
            {
                final CrashReport crashReport = CrashReport.forThrowable(e, "MouseScroll event for BO screen");
                final CrashReportCategory category = crashReport.addCategory("BO screen scroll event details");
                category.setDetail("XML res loc", () -> window.getXmlResourceLocation().toString());
                category.setDetail("Scroll value", () -> Double.toString(scrollVerticalDiff));
                throw new ReportedException(crashReport);
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(final net.minecraft.client.input.MouseButtonEvent event, final double deltaX, final double deltaY)
    {
        final double xIn = event.x();
        final double yIn = event.y();
        try
        {
            return window.onMouseDrag(calcRelativeX(xIn), calcRelativeY(yIn), event.input(), deltaX, deltaY);
        }
        catch (final Exception e)
        {
            final CrashReport crashReport = CrashReport.forThrowable(e, "MouseDragged event for BO screen");
            final CrashReportCategory category = crashReport.addCategory("BO screen mouse event details");
            category.setDetail("XML res loc", () -> window.getXmlResourceLocation().toString());
            throw new ReportedException(crashReport);
        }
    }

    @Override
    public boolean mouseReleased(final MouseButtonEvent event)
    {
        final int keyCode = event.button();
        if (keyCode == GLFW.GLFW_MOUSE_BUTTON_LEFT)
        {
            // Adjust coordinate to origin of window
            isMouseLeftDown = false;
            try
            {
                return window.onMouseReleased(calcRelativeX(event.x()), calcRelativeY(event.y()));
            }
            catch (final Exception e)
            {
                final CrashReport crashReport = CrashReport.forThrowable(e, "MouseReleased event for BO screen");
                final CrashReportCategory category = crashReport.addCategory("BO screen mouse event details");
                category.setDetail("XML res loc", () -> window.getXmlResourceLocation().toString());
                category.setDetail("GLFW mouse key value", () -> Integer.toString(keyCode));
                throw new ReportedException(crashReport);
            }
        }
        return false;
    }

    /**
     * Get the open window here.
     * @return the window.
     */
    public BOWindow getWindow()
    {
        return window;
    }

    @Override
    public void tick()
    {
        try
        {
            if (minecraft != null)
            {
                if (!isOpen)
                {
                    window.onOpened();
                    isOpen = true;
                }
                else
                {
                    window.onUpdate();

                    final LocalPlayer player = minecraft == null ? null : minecraft.player;
                    if (player != null && (!player.isAlive() || player.isDeadOrDying()))
                    {
                        player.closeContainer();
                    }
                }
            }
        }
        catch (final Exception e)
        {
            final CrashReport crashReport = CrashReport.forThrowable(e, "Ticking/Updating BO screen");
            final CrashReportCategory category = crashReport.addCategory("BO screen update details");
            category.setDetail("XML res loc", () -> window.getXmlResourceLocation().toString());
            category.setDetail("Is opened", () -> Boolean.toString(isOpen));
            throw new ReportedException(crashReport);
        }
    }

    @Override
    public void removed()
    {
        try
        {
            window.onClosed();
        }
        catch (final Exception e)
        {
            final CrashReport crashReport = CrashReport.forThrowable(e, "Closing BO screen");
            final CrashReportCategory category = crashReport.addCategory("BO screen closing details");
            category.setDetail("XML res loc", () -> window.getXmlResourceLocation().toString());
            category.setDetail("Is opened", () -> Boolean.toString(isOpen));
            throw new ReportedException(crashReport);
        }
        finally
        {
            BOWindow.clearFocus();
            CursorUtils.resetCursor();
        }
    }

    @Override
    public boolean isPauseScreen()
    {
        return window.doesWindowPauseGame();
    }

    /**
     * Converts X from event to unscaled and unscrolled X for child in relative (top-left) coordinates.
     *
     * <p>PORT26: events and the window transform are both in GUI-scaled coordinates now
     * (the old code converted through framebuffer pixels because of the custom projection),
     * so no {@code mcScale} multiplication.
     */
    private double calcRelativeX(final double xIn)
    {
        return (xIn - x) / renderScale;
    }

    /**
     * Converts Y from event to unscaled and unscrolled Y for child in relative (top-left) coordinates.
     */
    private double calcRelativeY(final double yIn)
    {
        return (yIn - y) / renderScale;
    }

    public double getRenderScale()
    {
        return renderScale;
    }

    public double getVanillaGuiScale()
    {
        return mcScale;
    }

    public int getFramebufferWidth()
    {
        return framebufferWidth;
    }

    public int getFramebufferHeight()
    {
        return framebufferHeight;
    }

    public int getAbsoluteMouseX()
    {
        return absoluteMouseX;
    }

    public int getAbsoluteMouseY()
    {
        return absoluteMouseY;
    }
}
