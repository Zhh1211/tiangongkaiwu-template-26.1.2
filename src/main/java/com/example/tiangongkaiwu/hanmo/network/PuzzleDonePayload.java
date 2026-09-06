package com.example.tiangongkaiwu.hanmo.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 → 服务端：三句全对、请求誊录结算。
 *
 * 服务端据此做权威校验（材料齐、残页待译且与题目条目一致），通过则消耗墨/纸、
 * 把残页翻转为已译并烙上成绩经验；结果经 {@link SettlementResultPayload} 回执。
 *
 * @param puzzleId   客户端刚译完的题目 id（服务端用于反查并校验与残页条目一致）
 * @param wrongTotal 全篇三句累计判错次数（决定经验档位）
 */
public record PuzzleDonePayload(String puzzleId, int wrongTotal) implements CustomPacketPayload {

    public static final Type<PuzzleDonePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("tiangongkaiwu", "puzzle_done"));

    public static final StreamCodec<ByteBuf, PuzzleDonePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(262144), PuzzleDonePayload::puzzleId,
            ByteBufCodecs.INT, PuzzleDonePayload::wrongTotal,
            PuzzleDonePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
