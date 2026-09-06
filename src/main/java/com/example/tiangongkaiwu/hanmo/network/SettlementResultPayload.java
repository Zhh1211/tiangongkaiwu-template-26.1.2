package com.example.tiangongkaiwu.hanmo.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 → 客户端：翰墨台誊录结算结果回执。
 *
 * @param ok         是否结算成功
 * @param reasonKey  失败原因翻译键（ok 时为空字符串）
 */
public record SettlementResultPayload(boolean ok, String reasonKey) implements CustomPacketPayload {

    public static final Type<SettlementResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("tiangongkaiwu", "settlement_result"));

    public static final StreamCodec<ByteBuf, SettlementResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SettlementResultPayload::ok,
            ByteBufCodecs.stringUtf8(512), SettlementResultPayload::reasonKey,
            SettlementResultPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
