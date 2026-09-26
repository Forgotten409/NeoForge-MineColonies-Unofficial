package com.ldtteam.structurize.api;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Logging utility class.
 *
 * <p>PORT26 (multipiston seed batch): the standalone structurize mod is not ported yet —
 * this minimal Log keeps the {@code com.ldtteam.structurize.api} FQCN stable so that the
 * multipiston merge (and later the full structurize phase) compiles unchanged. The logger
 * name is fixed to "structurize" instead of pulling in the full Constants class.
 */
public final class Log
{
    /**
     * Mod logger.
     */
    private static Logger logger = null;

    /**
     * Private constructor to hide the public one.
     */
    private Log()
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * Getter for the structurize Logger.
     *
     * @return the logger.
     */
    public static Logger getLogger()
    {
        // Only create logger if current logger is empty.
        if (logger == null)
        {
            Log.logger = LogManager.getLogger("structurize");
        }
        return logger;
    }
}
