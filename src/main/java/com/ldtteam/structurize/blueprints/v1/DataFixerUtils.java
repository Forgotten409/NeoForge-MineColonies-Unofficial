package com.ldtteam.structurize.blueprints.v1;

import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.SharedConstants;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utils for data fixer mechanism
 */
public class DataFixerUtils
{
    /**
     * If the used datafixer is the vanilla one.
     */
    public static boolean isVanillaDF = DataFixers.getDataFixer() instanceof com.mojang.datafixers.DataFixerUpper;

    /**
     * Private constructor to hide implicit one.
     */
    private DataFixerUtils()
    {
        // Intentionally left empty.
    }

    /**
     * PORT26 (build-tool preview delay fix): blueprints shipped with the mod carry 1.21-era
     * data versions, so *every* palette entry / tile entity / entity of *every* decoded
     * blueprint runs the vanilla data fixer across ~1000 schema versions. The vanilla
     * DataFixerUpper re-folds the rule chain for every call, which made decoding a single
     * structure-pack folder take seconds (the "build tool menu / preview lags" report).
     *
     * Blueprint payloads massively repeat the same inputs (the same palette block states
     * appear over and over across blueprints), so a bounded memo keyed by the input tag
     * dedupes virtually all of those runs. Inputs are tiny (block-state entries are a
     * name + a few properties), the output is deterministic for a fixed version range,
     * and the cache is synchronized because decode happens on the Structurize IO pool.
     */
    private static final int FIXER_CACHE_LIMIT = 8192;
    private static final Map<String, CompoundTag> FIXER_CACHE =
      java.util.Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true)
      {
          @Override
          protected boolean removeEldestEntry(final Map.Entry<String, CompoundTag> eldest)
          {
              return size() > FIXER_CACHE_LIMIT;
          }
      });

    private static CompoundTag memoizedFix(
      final CompoundTag dataIn, final TypeReference dataType, final int startVersion, final int endVersion)
    {
        final String key = System.identityHashCode(dataType) + "|" + startVersion + "->" + endVersion + "|" + dataIn.toString();
        final CompoundTag cached = FIXER_CACHE.get(key);
        if (cached != null)
        {
            return cached;
        }

        final CompoundTag fixed = (CompoundTag) DataFixers.getDataFixer()
            .update(dataType, new Dynamic<>(NbtOps.INSTANCE, dataIn), startVersion, endVersion)
            .getValue();
        FIXER_CACHE.put(key, fixed);
        return fixed;
    }

    public static CompoundTag runDataFixer(final CompoundTag dataIn, final TypeReference dataType, final DataVersion startVersion)
    {
        // PORT26: getDataVersion().getVersion() -> dataVersion().version()
        return runDataFixer(dataIn, dataType, startVersion.getDataVersion(), SharedConstants.getCurrentVersion().dataVersion().version());
    }

    public static CompoundTag runDataFixer(final CompoundTag dataIn, final TypeReference dataType, final int startVersion)
    {
        // PORT26: getDataVersion().getVersion() -> dataVersion().version()
        return runDataFixer(dataIn, dataType, startVersion, SharedConstants.getCurrentVersion().dataVersion().version());
    }

    public static CompoundTag runDataFixer(final CompoundTag dataIn, final TypeReference dataType, final DataVersion startVersion, final DataVersion endVersion)
    {
        return runDataFixer(dataIn, dataType, startVersion.getDataVersion(), endVersion.getDataVersion());
    }

    public static CompoundTag runDataFixer(final CompoundTag dataIn, final TypeReference dataType, final int startVersion, final int endVersion)
    {
        return runDataFixer(
            dataIn,
            dataType,
            startVersion,
            endVersion,
            startVersion <= DataVersion.pre1466.getDataVersion() && DataVersion.post1466.getDataVersion() <= endVersion && dataType == References.BLOCK_ENTITY);
    }

    public static CompoundTag runDataFixer(
        final CompoundTag dataIn,
        final TypeReference dataType,
        final int startVersion,
        final int endVersion,
        final boolean debugNonBlockstate)
    {
        // PORT26 (build-tool preview delay fix): route the plain single-shot fixer through
        // the memoization cache — see FIXER_CACHE above. Only the actual fixer run is
        // cached; the no-op and cascade variants stay direct.
        return startVersion == endVersion
            ? dataIn
            : debugNonBlockstate && dataType != References.BLOCK_STATE
                ? runDataFixerCascade(dataIn, dataType, startVersion, endVersion)
                : memoizedFix(dataIn, dataType, startVersion, endVersion);
    }

    public static CompoundTag runDataFixerCascade(final CompoundTag dataIn, final TypeReference dataType, final int startVersion, final int endVersion)
    {
        CompoundTag fixedNbt = dataIn;
        DataVersion currentVersion = DataVersion.findFromDataVersion(startVersion);

        while (currentVersion.getDataVersion() < endVersion)
        {
            fixedNbt = (CompoundTag) DataFixers.getDataFixer()
                .update(
                    dataType,
                    new Dynamic<>(NbtOps.INSTANCE, fixedNbt),
                    currentVersion.getDataVersion(),
                    currentVersion.getSuccessor().getDataVersion())
                .getValue();
            currentVersion = currentVersion.getSuccessor();
            if (currentVersion == DataVersion.pre1466 && dataType == References.BLOCK_ENTITY)
            {
                currentVersion = DataVersion.post1466;
            }
        }

        return fixedNbt;
    }
}
