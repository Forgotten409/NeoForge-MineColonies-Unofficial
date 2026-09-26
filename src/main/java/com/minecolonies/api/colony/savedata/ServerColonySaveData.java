package com.minecolonies.api.colony.savedata;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.colony.ColonyList;
import com.minecolonies.core.util.BackUpHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_COLONIES;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_COLONY_MANAGER;

/**
 * The implementation of the colonyTagCapability.
 */
public class ServerColonySaveData extends SavedData implements IServerColonySaveData
{
    /**
     * World save data name.
     */
    public static final String NAME = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "colony_manager").toDebugFileName();

    /**
     * PORT26: SavedData.Factory was removed in favour of SavedDataType + Codec.
     * The codec is a thin passthrough around our existing readNBT/writeNBT compound logic.
     * The registry provider is captured by {@link #captureProvider} at access time.
     */
    private static final java.util.concurrent.atomic.AtomicReference<HolderLookup.Provider> LAST_PROVIDER = new java.util.concurrent.atomic.AtomicReference<>();

    /**
     * Remember the provider of the level we are being accessed for (codec encode/decode context).
     */
    static void captureProvider(final HolderLookup.Provider provider)
    {
        LAST_PROVIDER.set(provider);
    }

    private static HolderLookup.Provider currentProvider()
    {
        final HolderLookup.Provider provider = LAST_PROVIDER.get();
        return provider != null ? provider : net.minecraft.data.registries.VanillaRegistries.createLookup();
    }

    public static final com.mojang.serialization.Codec<ServerColonySaveData> CODEC = com.mojang.serialization.Codec.of(
      new com.mojang.serialization.Encoder<ServerColonySaveData>()
      {
          @Override
          public <T> com.mojang.serialization.DataResult<T> encode(final ServerColonySaveData data, final com.mojang.serialization.DynamicOps<T> ops, final T prefix)
          {
              final CompoundTag tag = data.writeNBT(currentProvider(), new CompoundTag());
              return CompoundTag.CODEC.encode(tag, ops, prefix);
          }
      },
      new com.mojang.serialization.Decoder<ServerColonySaveData>()
      {
          @Override
          public <T> com.mojang.serialization.DataResult<com.mojang.datafixers.util.Pair<ServerColonySaveData, T>> decode(final com.mojang.serialization.DynamicOps<T> ops, final T input)
          {
              return CompoundTag.CODEC.decode(ops, input).flatMap(pair -> {
                  final ServerColonySaveData data = new ServerColonySaveData();
                  data.readNBT(currentProvider(), pair.getFirst());
                  return com.mojang.serialization.DataResult.success(com.mojang.datafixers.util.Pair.of(data, pair.getSecond()));
              });
          }
      }
    );

    public static final SavedDataType<ServerColonySaveData> TYPE = new SavedDataType<>(
      Identifier.fromNamespaceAndPath(Constants.MOD_ID, "colony_manager"),
      ServerColonySaveData::new,
      CODEC,
      net.minecraft.util.datafix.DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

    /**
     * The list of all colonies.
     */
    @NotNull
    private final ColonyList<IColony> colonies = new ColonyList<>();

    /**
     * Is this the main overworld cap?
     */
    private boolean overworld;

    private ServerColonySaveData()
    {

    }

    /**
     * Serialize (called by the codec passthrough and the backup helper).
     */
    @NotNull
    CompoundTag saveWithProvider(@NotNull final HolderLookup.Provider provider)
    {
        final CompoundTag tag = new CompoundTag();
        writeNBT(provider, tag);
        return tag;
    }

    @Override
    public IColony createColony(@NotNull final ServerLevel w, @NotNull final String name, @NotNull final BlockPos pos)
    {
        return colonies.create(w, name, pos);
    }

    @Override
    public void deleteColony(final int id)
    {
        colonies.remove(id);
    }

    @Override
    public IColony getColony(final int id)
    {
        return colonies.get(id);
    }

    @Override
    public List<IColony> getColonies()
    {
        return colonies.getCopyAsList();
    }

    @Override
    public void addColony(final IColony colony)
    {
        colonies.add(colony);
    }

    @Override
    public int getTopID()
    {
        return colonies.getTopID();
    }

    @Override
    public boolean isDirty()
    {
        return true;
    }

    private CompoundTag writeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag inputTag)
    {
        final CompoundTag compound = new CompoundTag();
        final ListTag colonyTag = new ListTag();
        for (final IColony colony : colonies)
        {
            try
            {
                colonyTag.add(colony.getColonyTag());
            }
            catch (Exception e)
            {
                Log.getLogger()
                  .error("Colony: " + colony.getName() + " id:" + colony.getID() + " owner:" + colony.getPermissions().getOwnerName() + " could not be saved! Error:", e);
            }
        }

        compound.put(TAG_COLONIES, colonyTag);

        if (overworld)
        {
            final CompoundTag managerCompound = new CompoundTag();
            IColonyManager.getInstance().write(provider, managerCompound);
            compound.put(TAG_COLONY_MANAGER, managerCompound);
        }

        inputTag.put(Constants.MOD_ID, compound);
        return inputTag;
    }

    @Override
    public IServerColonySaveData setOverworld(final boolean overworld)
    {
        this.overworld = overworld;
        return this;
    }

    private void readNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag inputTag)
    {
        final CompoundTag compound = inputTag.getCompoundOrEmpty(Constants.MOD_ID);

        if (!compound.contains(TAG_COLONIES))
        {
            BackUpHelper.loadManagerBackup(provider);
            return;
        }

        // Load all colonies from Nbt
        Multimap<BlockPos, IColony> tempColonies = ArrayListMultimap.create();
        for (final Tag tag : compound.getListOrEmpty(TAG_COLONIES))
        {
            final IColony colony = Colony.loadColony((CompoundTag) tag, null, provider);
            if (colony != null)
            {
                tempColonies.put(colony.getCenter(), colony);
                colonies.add(colony);
            }
        }

        if (compound.contains(TAG_COLONY_MANAGER))
        {
            IColonyManager.getInstance().read(provider, compound.getCompoundOrEmpty(TAG_COLONY_MANAGER));
            this.overworld = true;
        }

        // Check colonies for duplicates causing issues.
        for (final BlockPos pos : tempColonies.keySet())
        {
            // Check if any position has more than one colony
            if (tempColonies.get(pos).size() > 1)
            {
                Log.getLogger().warn("Detected duplicate colonies which are at the same position:");
                for (final IColony colony : tempColonies.get(pos))
                {
                    Log.getLogger()
                        .warn(
                        "ID: " + colony.getID() + " name:" + colony.getName() + " citizens:" + colony.getCitizenManager().getCitizens().size() + " building count:" + colony
                                                                                                                                                                        .getCommonBuildingManager()
                                                                                                                                                                        .getBuildings()
                                                                                                                                                                        .size());
                }
                Log.getLogger().warn("Check and remove all except one of the duplicated colonies above!");
            }
        }
    }
}