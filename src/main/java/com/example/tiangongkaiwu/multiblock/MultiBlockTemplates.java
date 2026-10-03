package com.example.tiangongkaiwu.multiblock;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 多方块模板注册表：按 {@code index.json} 加载全部内置模板（mod jar 内 classpath 读取）。
 *
 * <p>模板由 {@code .workbuddy/scripts/nbt_to_template.py} 从玩家摆的结构方块 .nbt 编译生成；
 * 新机器接入 = 摆结构 → 编译 → index 里出现，墨斗零改动。
 */
public final class MultiBlockTemplates {

    private static final Map<String, StructureTemplate> TEMPLATES = new HashMap<>();
    private static boolean bootstrapped = false;

    private MultiBlockTemplates() {
    }

    private static void bootstrap() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;
        JsonObject index = readJson("/data/tiangongkaiwu/structures/index.json");
        if (index == null) {
            return;
        }
        for (JsonElement e : index.getAsJsonArray("templates")) {
            String name = e.getAsString();
            JsonObject json = readJson("/data/tiangongkaiwu/structures/" + name + ".json");
            if (json != null) {
                TEMPLATES.put(name, StructureTemplate.fromJson(name, json));
            }
        }
    }

    /** 按机器 id 取模板（如 "dui"）；没有则 null。 */
    public static StructureTemplate get(String machine) {
        bootstrap();
        return TEMPLATES.get(machine);
    }

    private static JsonObject readJson(String path) {
        try (InputStream in = MultiBlockTemplates.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }
}
