package com.example.tiangongkaiwu.fertility;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;

/**
 * 地力（#40 v2，书曰「勤農糞田，多方以助之」）：按坐标存的世界数据。
 *
 * <p>地力不是方块、不是物品，是<b>那片地</b>的属性——沙田熟化了还是沙田，黏土田熟化了还是黏土田。
 * 存取都以坐标为键，跟世界存档走（重启不丢），换世界不通用。
 *
 * <p>规则：
 * <ul>
 * <li>施粪（稻秆上施四种粪肥）→ 脚下坐标 +1~2；</li>
 * <li>收获（坐在该坐标上的作物被收）→ -1；</li>
 * <li>土被挖走（坐标变空气）→ 该条目自然作废；</li>
 * <li>下限 0，无上限——增产幅度由收获时的掉落乘算体现。</li>
 * </ul>
 */
public class FertilityData extends SavedData {

    private static final String DATA_NAME = "tiangongkaiwu_fertility";

    private final Long2IntOpenHashMap map = new Long2IntOpenHashMap();

    public FertilityData() {
    }

    /** 取（每维度一份，随世界存档读写）。 */
    public static FertilityData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(FertilityData::new, FertilityData::load, null), DATA_NAME);
    }

    /** 读某格地力；该格已被挖成空气 → 条目作废清零。 */
    public static int fertilityAt(ServerLevel level, BlockPos soilPos) {
        FertilityData data = get(level);
        long key = soilPos.asLong();
        if (!data.map.containsKey(key)) {
            return 0;
        }
        if (level.getBlockState(soilPos).isAir()) {
            data.map.remove(key);
            data.setDirty();
            return 0;
        }
        return data.map.get(key);
    }

    /** 加减地力（下限 0，无上限）。 */
    public static void add(ServerLevel level, BlockPos soilPos, int delta) {
        FertilityData data = get(level);
        long key = soilPos.asLong();
        int next = Math.max(0, data.map.get(key) + delta);
        if (next == 0 && !data.map.containsKey(key)) {
            return;
        }
        if (next == 0) {
            data.map.remove(key);
        } else {
            data.map.put(key, next);
        }
        data.setDirty();
    }

    private static FertilityData load(CompoundTag tag, HolderLookup.Provider provider) {
        FertilityData data = new FertilityData();
        long[] keys = tag.getLongArray("keys");
        int[] vals = tag.getIntArray("vals");
        for (int i = 0; i < keys.length && i < vals.length; i++) {
            data.map.put(keys[i], vals[i]);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        long[] keys = new long[map.size()];
        int[] vals = new int[map.size()];
        int i = 0;
        for (Long2IntMap.Entry e : map.long2IntEntrySet()) {
            keys[i] = e.getLongKey();
            vals[i] = e.getIntValue();
            i++;
        }
        tag.putLongArray("keys", keys);
        tag.putIntArray("vals", vals);
        return tag;
    }
}
