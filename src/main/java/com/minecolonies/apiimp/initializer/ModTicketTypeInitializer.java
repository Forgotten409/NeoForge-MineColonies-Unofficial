package com.minecolonies.apiimp.initializer;

import com.minecolonies.api.util.constant.Constants;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.TicketType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry entry for the minecolonies chunk-load ticket type.
 *
 * <p>PORT26 (crash fix #10): In 26.1.2 {@code TicketType} is a plain record stored in the
 * {@code minecraft:ticket_type} registry (vanilla registers its own types during bootstrap).
 * The 1.21.1 code registered {@code minecolonies:initial_chunkload} from the static
 * initializer of {@code ColonyConstants} — in 26.1.2 that class only initializes at first
 * use (entity creation during the first server tick), long after the registry froze, which
 * threw {@code IllegalStateException: Registry is already frozen} inside
 * {@code ExceptionInInitializerError} and poisoned {@code ColonyConstants} for the rest of
 * the session ("Couldnt analyze animal: minecolonies:citizen" x4 + log appender failures).
 * Ticket types, like every other registry object, must be registered through the mod's
 * registration phase — hence this DeferredRegister, wired in
 * {@code com.minecolonies.core.MineColonies#init}.</p>
 */
public final class ModTicketTypeInitializer
{
    public static final DeferredRegister<TicketType> TICKET_TYPES = DeferredRegister.create(Registries.TICKET_TYPE, Constants.MOD_ID);

    /**
     * Specific ticket type for minecolonies tickets (colony chunk loading).
     * Flags: PERSIST + LOADING + SIMULATION + KEEP_DIMENSION_ACTIVE
     * (replaces the old 1.21.1 {@code forceTicks=true} region ticket).
     */
    public static final DeferredHolder<TicketType, TicketType> KEEP_LOADED_TYPE =
      TICKET_TYPES.register("initial_chunkload",
        () -> new TicketType(TicketType.NO_TIMEOUT,
                             TicketType.FLAG_PERSIST
                               | TicketType.FLAG_LOADING
                               | TicketType.FLAG_SIMULATION
                               | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));

    private ModTicketTypeInitializer()
    {
        /*
         * Intentionally left empty.
         */
    }
}
