package com.ldtteam.blockui;

import com.ldtteam.blockui.controls.*;
import com.ldtteam.blockui.mod.Log;
import com.ldtteam.blockui.views.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.fml.loading.FMLEnvironment;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Utilities to load xml files.
 */
public final class Loader extends SimplePreparableReloadListener<Map<Identifier, PaneParams>>
{
    public static final Loader INSTANCE = new Loader();

    private final Map<String, Function<PaneParams, ? extends Pane>> paneFactories = new HashMap<>();

    private Map<Identifier, PaneParams> xmlCache = new HashMap<>();

    /**
     * Built-in fallback gui for missing xml files (upstream "create missing gui and don't
     * crash" TODO): a small window with an error label. The requested resource id is
     * filled into the second label programmatically by {@link #createMissingGui}.
     */
    private static final String MISSING_GUI_XML = """
        <window size="220 70" pause="false">
            <text size="220 20" pos="0 10" color="red" textalign="MIDDLE" label="Missing GUI!"/>
            <text id="missing_gui_path" size="220 20" pos="0 40" color="red" textalign="MIDDLE" label="see log"/>
        </window>""";

    private Loader()
    {
        register("view", View::new);
        register("group", Group::new);
        register("scrollgroup", ScrollingGroup::new);
        register("list", ScrollingList::new);
        register("text", Text::new);
        register("button", ButtonImage::new);
        register("toggle", ToggleButton::new);
        register("input", TextFieldVanilla::new);
        register("image", Image::new);
        register("box", Box::new);
        register("itemicon", Loader::itemIcon);
        register("entityicon", EntityIcon::new);
        register("switch", SwitchView::new);
        register("dropdown", DropDownList::new);
        register("overlay", OverlayView::new);
        register("gradient", Gradient::new);
        register("zoomdragview", ZoomDragView::new);
        register("checkbox", CheckBox::new);
    }

    private static ItemIcon itemIcon(final PaneParams paneParams)
    {
        if (paneParams.hasAttribute(ItemIconWithBlockState.PARAM_NBT))
        {
            if (!FMLEnvironment.isProduction() && paneParams.hasAttribute(ItemIconWithProperties.PARAM_PROPERTIES))
            {
                throw new IllegalStateException("Must be one of '%s' or '%s'".formatted(ItemIconWithBlockState.PARAM_NBT, ItemIconWithProperties.PARAM_PROPERTIES));
            }
            return new ItemIconWithBlockState(paneParams);
        }
        if (paneParams.hasAttribute(ItemIconWithProperties.PARAM_PROPERTIES))
        {
            return new ItemIconWithProperties(paneParams);
        }
        return new ItemIcon(paneParams);
    }

    /**
     * registers an element definition class so it can be used in
     * gui definition files
     *
     * @param name          the tag name of the element in the definition file
     * @param factoryMethod the constructor/method to create the element Pane
     */
    public void register(final String name, final Function<PaneParams, ? extends Pane> factoryMethod)
    {
        if (paneFactories.containsKey(name))
        {
            throw new IllegalArgumentException("Duplicate pane type '" + name + "' when registering Pane class method.");
        }

        paneFactories.put(name, factoryMethod);
    }

    /**
     * Uses the loaded parameters to construct a new Pane tree
     *
     * @param params the parameters for the new pane and its children
     * @return the created Pane
     */
    private Pane createFromPaneParams(final PaneParams params)
    {
        final String name = params.getType();
        if (paneFactories.containsKey(name))
        {
            return paneFactories.get(name).apply(params);
        }

        Log.getLogger().error("There is no factory method for " + name);
        return null;
    }

    /**
     * Create a pane from its xml parameters.
     *
     * @param params xml parameters.
     * @param parent parent view.
     * @return the new pane.
     */
    public static Pane createFromPaneParams(final PaneParams params, final View parent)
    {
        if ("layout".equalsIgnoreCase(params.getType()))
        {
            params.getResource("source", r -> createFromXMLFile(r, parent));
            return null;
        }

        if (parent instanceof final BOWindow window && params.getType().equals("window"))
        {
            window.loadParams(params);
            parent.parseChildren(params);
            return parent;
        }
        else if (parent instanceof View && params.getType().equals("window")) // layout
        {
            parent.parseChildren(params);
            return parent;
        }
        else
        {
            params.setParentView(parent);
            final Pane pane = INSTANCE.createFromPaneParams(params);

            if (pane != null)
            {
                pane.putInside(parent);
                pane.parseChildren(params);
            }
            return pane;
        }
    }

    /**
     * Parse XML contains in a Identifier into contents for a Window.
     *
     * @param resource xml as a {@link Identifier}.
     * @param parent   parent view.
     */
    public static Pane createFromXMLFile(final Identifier resource, final View parent)
    {
        if (INSTANCE.xmlCache.containsKey(resource))
        {
            try
            {
                return createFromPaneParams(INSTANCE.xmlCache.get(resource), parent);
            }
            catch (Exception e)
            {
                throw new RuntimeException("Can't parse xml at: " + resource.toString(), e);
            }
        }
        else
        {
            // upstream TODO ("create missing gui and don't crash") resolved: a bad or
            // missing gui resource must never take the whole client down — log it and
            // open the built-in fallback gui instead
            Log.getLogger().error("Gui at \"{}\" was not found! Opening fallback gui instead.", resource);
            return createMissingGui(resource, parent);
        }
    }

    /**
     * Builds the built-in missing-gui window. Never throws on its own — if even this
     * cannot be constructed, the original crash-on-missing-gui behavior is restored.
     *
     * @param resource the gui resource that could not be found
     * @param parent   parent view (a window for gui opens, a view for layout includes)
     * @return fallback pane tree (window branch returns the parent window itself)
     */
    private static Pane createMissingGui(final Identifier resource, final View parent)
    {
        try
        {
            final DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            final Document doc = documentBuilder.parse(new ByteArrayInputStream(MISSING_GUI_XML.getBytes(StandardCharsets.UTF_8)));
            doc.getDocumentElement().normalize();

            final Pane pane = createFromPaneParams(new PaneParams(doc.getDocumentElement()), parent);
            if (pane != null)
            {
                final Text path = pane.findPaneOfTypeByID("missing_gui_path", Text.class);
                if (path != null)
                {
                    path.setText(Component.literal(resource.toString()));
                }
            }
            return pane;
        }
        catch (final Exception e)
        {
            throw new RuntimeException("Gui at \"" + resource + "\" was not found!", e);
        }
    }

    @Override
    protected Map<Identifier, PaneParams> prepare(final ResourceManager rm, final ProfilerFiller profiler)
    {
        profiler.startTick();
        profiler.push("BlockUI-xml-lookup-parsing");

        final Map<Identifier, PaneParams> foundXmls = new HashMap<>();
        final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        final DocumentBuilder documentBuilder;
        try
        {
            documentBuilder = documentBuilderFactory.newDocumentBuilder();
        }
        catch (final ParserConfigurationException e)
        {
            profiler.pop();
            profiler.endTick();
            throw new RuntimeException(e);
        }

        rm.listResources("gui", rl -> rl.getPath().endsWith(".xml")).forEach((rl, res) -> {
            final Document doc;
            try (final InputStream is = res.open())
            {
                doc = documentBuilder.parse(is);
            }
            catch (final IOException | SAXException e)
            {
                Log.getLogger().error("Failed to load xml at: " + rl.toString(), e);
                return;
            }

            doc.getDocumentElement().normalize();
            foundXmls.put(rl, new PaneParams(doc.getDocumentElement()));
        });

        profiler.pop();
        profiler.endTick();
        return foundXmls;
    }

    @Override
    protected void apply(final Map<Identifier, PaneParams> foundXmls, final ResourceManager rm, final ProfilerFiller profiler)
    {
        xmlCache = foundXmls;
    }
}
