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
 * @param notes  难字注（可选，可为空列表）：解释生僻古称，出题时显示在文言文下方。
 *               属“内容数据”，不走 lang；UI 会自动加“注：”前缀
 */
public record PuzzleSentence(String wenyan, List<String> tokens, List<String> notes) {

    /** 网络流编解码：wenyan + tokens + notes（均为 UTF8 字符串列表）。客户端经题库同步包拿到题句。 */
    public static final StreamCodec<ByteBuf, PuzzleSentence> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(262144), PuzzleSentence::wenyan,
            ByteBufCodecs.stringUtf8(262144).apply(ByteBufCodecs.list()), PuzzleSentence::tokens,
            ByteBufCodecs.stringUtf8(262144).apply(ByteBufCodecs.list()), PuzzleSentence::notes,
            PuzzleSentence::new
    );
}
