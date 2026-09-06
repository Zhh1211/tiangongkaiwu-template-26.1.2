package com.example.tiangongkaiwu.hanmo;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * 一道翻译题中的一句话。
 *
 * @param wenyan 文言原文，显示在残页上
 * @param tokens 白话词块，按<b>正确顺序</b>排列；客户端负责打乱后交给玩家还原
 */
public record PuzzleSentence(String wenyan, List<String> tokens) {

    /** 网络流编解码：wenyan(UTF8) + tokens(String 列表)。客户端经题库同步包拿到题句。 */
    public static final StreamCodec<ByteBuf, PuzzleSentence> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(262144), PuzzleSentence::wenyan,
            ByteBufCodecs.stringUtf8(262144).apply(ByteBufCodecs.list()), PuzzleSentence::tokens,
            PuzzleSentence::new
    );
}
