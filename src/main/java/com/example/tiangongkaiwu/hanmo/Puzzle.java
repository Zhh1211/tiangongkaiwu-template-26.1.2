package com.example.tiangongkaiwu.hanmo;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * 一道翻译题：一张残页 = 三句文言 = 对应一条教程书条目。
 *
 * @param id        题目唯一标识，与 JSON 文件的 id 字段一致
 * @param entry     译成功后要解锁的 Modonomicon 条目，格式「分类id/条目id」
 * @param sentences 三句话，逐句翻译，翻对一句才显示下一句
 */
public record Puzzle(String id, String entry, List<PuzzleSentence> sentences) {

    /** 网络流编解码：id + entry(UTF8) + sentences(句列表)。客户端经题库同步包拿到整道题。 */
    public static final StreamCodec<ByteBuf, Puzzle> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(262144), Puzzle::id,
            ByteBufCodecs.stringUtf8(262144), Puzzle::entry,
            PuzzleSentence.STREAM_CODEC.apply(ByteBufCodecs.list()), Puzzle::sentences,
            Puzzle::new
    );
}
