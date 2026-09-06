package com.example.tiangongkaiwu.hanmo;

import java.util.List;

/**
 * 一道翻译题：一张残页 = 三句文言 = 对应一条教程书条目。
 *
 * @param id        题目唯一标识，与 JSON 文件的 id 字段一致
 * @param entry     译成功后要解锁的 Modonomicon 条目，格式「分类id/条目id」
 * @param sentences 三句话，逐句翻译，翻对一句才显示下一句
 */
public record Puzzle(String id, String entry, List<PuzzleSentence> sentences) {
}
