package com.example.tiangongkaiwu.hanmo;

import com.example.tiangongkaiwu.TiangongKaiwu;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 从数据包读取翰墨台题库。
 *
 * 扫描所有命名空间下的 data/&lt;namespace&gt;/hanmotai/puzzles/*.json，
 * 解析结果交给 {@link PuzzleRegistry}。任何单文件解析失败只跳过该文件，不影响其余题目。
 */
public class PuzzleLoader extends SimpleJsonResourceReloadListener {

    public static final String DIRECTORY = "hanmotai/puzzles";

    private static final Gson GSON = new GsonBuilder().create();

    public PuzzleLoader() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        List<Puzzle> loaded = new ArrayList<>();

        for (Map.Entry<ResourceLocation, JsonElement> resourceEntry : resources.entrySet()) {
            ResourceLocation key = resourceEntry.getKey();
            try {
                JsonObject root = resourceEntry.getValue().getAsJsonObject();

                String id = root.has("id") ? root.get("id").getAsString() : key.getPath();
                String entry = root.get("entry").getAsString();

                List<PuzzleSentence> sentences = new ArrayList<>();
                for (JsonElement sentenceElement : root.getAsJsonArray("sentences")) {
                    JsonObject sentenceObj = sentenceElement.getAsJsonObject();
                    String wenyan = sentenceObj.get("wenyan").getAsString();

                    List<String> tokens = new ArrayList<>();
                    for (JsonElement tokenElement : sentenceObj.getAsJsonArray("tokens")) {
                        tokens.add(tokenElement.getAsString());
                    }

                    if (tokens.isEmpty()) {
                        throw new IllegalArgumentException("tokens 不能为空");
                    }
                    sentences.add(new PuzzleSentence(wenyan, List.copyOf(tokens)));
                }

                loaded.add(new Puzzle(id, entry, List.copyOf(sentences)));
            } catch (Exception ex) {
                TiangongKaiwu.LOGGER.error("[天工开物] 题库 {} 解析失败，已跳过该文件", key, ex);
            }
        }

        PuzzleRegistry.replaceAll(loaded);
        TiangongKaiwu.LOGGER.info("[天工开物] 题库加载完毕，共 {} 道", loaded.size());
    }
}
