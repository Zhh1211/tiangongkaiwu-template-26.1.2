package com.example.tiangongkaiwu.hanmo;

import java.util.List;

/**
 * 一道翻译题中的一句话。
 *
 * @param wenyan 文言原文，显示在残页上
 * @param tokens 白话词块，按<b>正确顺序</b>排列；客户端负责打乱后交给玩家还原
 */
public record PuzzleSentence(String wenyan, List<String> tokens) {
}
