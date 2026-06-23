package com.example.tiangongkaiwu.data;

import java.util.List;

/**
 * 残页数据对象
 * 存储每张残页的完整信息
 */
public record CanYeData(
    String id,                     // 残页唯一标识，如 "naily_xu"
    String original,               // 文言文原文
    String translation,            // 正确译文（白话文）
    List<String> chunks,           // 词块列表（玩家需要拼装的词语）
    String unlockRecipe            // 解锁的配方ID，如 "tiangongkaiwu:rice_seed"
) {
    // 无额外方法，record 自动生成构造器、getter、equals、hashCode
}