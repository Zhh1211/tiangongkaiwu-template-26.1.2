package com.example.tiangongkaiwu.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 残页数据组件：一张《天工》残页的实例状态（1.21.1 物品数据组件 = 单物品多状态载体）。
 *
 * 设计拍板（2026-09-06，用户）：「译后残页」不另造新物品——残页携带本组件走完生命周期：
 *   目标残页(translated=false) → 翰墨台译毕 → 服务端翻转为已译并烙上成绩 xp
 *   → 玩家右键已译残页：解锁条目 + 发放 xp + 用完即毁（防刷经验，激励寻新残页）。
 * 创造物品栏只保留单一 can_ye 入口，不因条目增多而污染。
 *
 * @param entry      本页指向的 Modonomicon 教程书条目，格式「分类id/条目id」，如 {@code naili_juan/naili_mai}
 * @param translated 是否已译毕（翰墨台三句全对后由服务端置 true）
 * @param xp         译毕时按错误次数结算的成绩经验（右键时一次性发放）
 */
public record ResidualData(String entry, boolean translated, int xp) {

    /** JSON/组件持久化编解码：/give 赋目标时同样走此 codec。 */
    public static final Codec<ResidualData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("entry").forGetter(ResidualData::entry),
            Codec.BOOL.fieldOf("translated").forGetter(ResidualData::translated),
            Codec.INT.fieldOf("xp").forGetter(ResidualData::xp)
    ).apply(instance, ResidualData::new));

    /** 网络流编解码（组件随物品同步到客户端，tooltip 才读得到）。 */
    public static final StreamCodec<ByteBuf, ResidualData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(262144), ResidualData::entry,
            ByteBufCodecs.BOOL, ResidualData::translated,
            ByteBufCodecs.INT, ResidualData::xp,
            ResidualData::new
    );

    /** 新建一张指向某条目的「待译」残页。 */
    public static ResidualData pending(String entry) {
        return new ResidualData(entry, false, 0);
    }

    /** 条目 id 的尾段（「分类id/条目id」→「条目id」），用于拼解锁 advancement 与条目名翻译键。 */
    public String tail() {
        int i = entry.lastIndexOf('/');
        return i >= 0 ? entry.substring(i + 1) : entry;
    }
}
