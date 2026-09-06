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
 * 题库放在 data/ 下，只有服务端会加载；客户端通过自定义网络包逐题获取，
 * 因此这里的静态缓存只在服务端有意义，客户端侧恒为空。
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
