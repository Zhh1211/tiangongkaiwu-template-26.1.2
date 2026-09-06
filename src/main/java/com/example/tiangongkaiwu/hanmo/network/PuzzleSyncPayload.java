package com.example.tiangongkaiwu.hanmo.network;

import com.example.tiangongkaiwu.hanmo.Puzzle;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 服务端 → 客户端：全量题库同步包。
 *
 * 题库 JSON 放在 data/ 下，只有服务端会加载（数据包机制），单人游戏的客户端
 * 物理端拿不到。因此玩家登录/进入世界后，服务端把整库打包成此题下发给客户端，
 * 客户端 handler 直接灌进 {@link com.example.tiangongkaiwu.hanmo.PuzzleRegistry}。
 */
public record PuzzleSyncPayload(List<Puzzle> puzzles) implements CustomPacketPayload {

    public static final Type<PuzzleSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("tiangongkaiwu", "sync_puzzles"));

    public static final StreamCodec<ByteBuf, PuzzleSyncPayload> STREAM_CODEC = StreamCodec.composite(
            Puzzle.STREAM_CODEC.apply(ByteBufCodecs.list()), PuzzleSyncPayload::puzzles,
            PuzzleSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
