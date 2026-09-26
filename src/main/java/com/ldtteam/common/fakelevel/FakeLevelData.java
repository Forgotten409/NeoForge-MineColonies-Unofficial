package com.ldtteam.common.fakelevel;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.WritableLevelData;
import java.util.function.Supplier;

/**
 * Porting: class is relatively small, just check super class manually (all of missing methods are/were just aliases)
 * <p>
 * PORT26: LevelData was heavily reworked in 26.1.2 — spawn pos/angle, day time, weather and game rules accessors
 * were removed from the interface (moved elsewhere: ClockManager, ServerLevel, LevelData.RespawnData). Old 1.21.1
 * overrides (getSpawnPos, getSpawnAngle, getDayTime, isThundering, isRaining, setRaining, getGameRules,
 * setSpawn(BlockPos, float)) were dropped; getRespawnData delegates to the vanilla level data.
 */
public class FakeLevelData implements WritableLevelData
{
    protected Supplier<LevelData> vanillaLevelData;
    protected final IFakeLevelLightProvider lightProvider;

    protected FakeLevelData(final Supplier<LevelData> vanillaLevelData, final IFakeLevelLightProvider lightProvider)
    {
        this.vanillaLevelData = vanillaLevelData;
        this.lightProvider = lightProvider;
    }

    @Override
    public LevelData.RespawnData getRespawnData()
    {
        return vanillaLevelData.get().getRespawnData();
    }

    @Override
    public long getGameTime()
    {
        return vanillaLevelData.get().getGameTime();
    }

    // PORT26: verify these setters exist on 26.1.2 WritableLevelData (implemented without @Override on purpose)
    public void setGameTime(final long time)
    {
        // Noop
    }

    public void setSpawn(final LevelData.RespawnData respawnData)
    {
        // Noop
    }

    @Override
    public boolean isHardcore()
    {
        return false;
    }

    @Override
    public Difficulty getDifficulty()
    {
        // would like peaceful but dont want to trigger entity remove in case someone actually manage to tick fake level
        return Difficulty.EASY;
    }

    @Override
    public boolean isDifficultyLocked()
    {
        return true;
    }
}
