package com.example.tiangongkaiwu.hanmo;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 题库缓存。
 *
 * 服务端：题库放 data/ 下，由 PuzzleLoader 在数据包重载时填充；
 * 客户端：data 包读不到，玩家登录后由 PuzzleSyncPayload 网络包全量下发、
 * 同样经 {@link #replaceAll} 填充（两处共用同一静态缓存与入口）。
 */
public final class PuzzleRegistry {

    private static final Map<String, Puzzle> BY_ID = new LinkedHashMap<>();
    private static final List<Puzzle> ALL = new ArrayList<>();

    private PuzzleRegistry() {
    }

    public static synchronized void replaceAll(Collection<Puzzle> puzzles) {
        BY_ID.clear();
        ALL.clear();
        for (Puzzle puzzle : puzzles) {
            BY_ID.put(puzzle.id(), puzzle);
            ALL.add(puzzle);
        }
    }

    public static synchronized Optional<Puzzle> byId(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    /** 该条目名下可用的题（题库 JSON 的 entry 与残页组件 entry 匹配）；残页抽题池。 */
    public static synchronized List<Puzzle> byEntry(String entry) {
        if (entry == null) {
            return List.of();
        }
        List<Puzzle> out = new ArrayList<>();
        for (Puzzle puzzle : ALL) {
            if (entry.equals(puzzle.entry())) {
                out.add(puzzle);
            }
        }
        return List.copyOf(out);
    }

    public static synchronized List<Puzzle> all() {
        return List.copyOf(ALL);
    }

    public static synchronized Puzzle random(RandomSource random) {
        return ALL.isEmpty() ? null : ALL.get(random.nextInt(ALL.size()));
    }

    public static synchronized int size() {
        return ALL.size();
    }
}
