package com.example.tiangongkaiwu.multiblock;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 多方块结构模板（D15 数据驱动）：由 {@code nbt_to_template.py} 从玩家摆的结构方块 .nbt 编译生成，
 * JSON 存于 {@code data/tiangongkaiwu/structures/<machine>.json}，索引在 {@code index.json}。
 *
 * <p>角色语义：
 * <ul>
 * <li>{@link Role#WOOD} 木料位——任意原木/木板，玩家摆的保持原样（只换运动件路线）</li>
 * <li>{@link Role#SLIDER}/{@link Role#PIVOT}/{@link Role#ROTOR} 运动件位——成型时换装机构方块</li>
 * <li>{@link Role#FEED} 料口——要求空或容器，永不自动摆放</li>
 * <li>{@link Role#SHAFT_SOCKET} 轴位——劲网接入口（包壳接口，后续批次换装）</li>
 * </ul>
 */
public final class StructureTemplate {

    public enum Role {
        WOOD, SLIDER, PIVOT, ROTOR, FEED, SHAFT_SOCKET
    }

    /** 一个部件位：相对核心的偏移 + 角色 + 玩家摆的样例方块（补料优先同材质用）。 */
    public record Part(BlockPos pos, Role role, String sample) {
    }

    private final String machine;
    private final List<Part> parts;

    public StructureTemplate(String machine, List<Part> parts) {
        this.machine = machine;
        this.parts = List.copyOf(parts);
    }

    public String machine() {
        return this.machine;
    }

    public List<Part> parts() {
        return this.parts;
    }

    /**
     * 在 core 处尝试匹配：四向旋转各试一遍，全部部件合格返回旋转次数，否则 -1。
     * 合格判定按角色：WOOD/PIVOT/ROTOR=任意木料；SLIDER=空或木料；
     * FEED=空或容器；SHAFT_SOCKET=空或传动杆。
     */
    public int matchAt(Level level, BlockPos core) {
        for (int rot = 0; rot < 4; rot++) {
            if (fits(level, core, rot)) {
                return rot;
            }
        }
        return -1;
    }

    private boolean fits(Level level, BlockPos core, int rot) {
        for (Part part : this.parts) {
            BlockPos p = core.offset(spin(part.pos(), rot));
            BlockState state = level.getBlockState(p);
            boolean ok = switch (part.role()) {
                case WOOD, PIVOT, ROTOR -> isWoodish(state);
                case SLIDER -> state.isAir() || isWoodish(state);
                case FEED -> state.isAir() || level.getBlockEntity(p) instanceof Container;
                case SHAFT_SOCKET -> state.isAir()
                        || state.getBlock() instanceof com.example.tiangongkaiwu.block.ShaftBlock;
            };
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /** 某旋转下某角色的全部世界坐标（幽灵预览/补料/自检共用）。 */
    public List<BlockPos> worldPositions(BlockPos core, Role role, int rot) {
        List<BlockPos> out = new ArrayList<>();
        for (Part part : this.parts) {
            if (part.role() == role) {
                out.add(core.offset(spin(part.pos(), rot)));
            }
        }
        return out;
    }

    /** 绕核心的 90° 旋转（与旧版墨斗 spin 一致）。 */
    public static BlockPos spin(BlockPos off, int rot) {
        int x = off.getX();
        int z = off.getZ();
        for (int i = 0; i < rot; i++) {
            int nx = z;
            int nz = -x;
            x = nx;
            z = nz;
        }
        return new BlockPos(x, off.getY(), z);
    }

    /** 木料判定：任意原木或木板（D15：任意木料拼装）。 */
    public static boolean isWoodish(BlockState state) {
        return state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS);
    }

    // ==================== JSON 解析 ====================

    public static StructureTemplate fromJson(String machine, JsonObject json) {
        List<Part> parts = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray("parts")) {
            JsonObject o = e.getAsJsonObject();
            JsonArray p = o.getAsJsonArray("pos");
            BlockPos pos = new BlockPos(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt());
            Role role = Role.valueOf(o.get("role").getAsString().toUpperCase(Locale.ROOT));
            String sample = o.has("sample") ? o.get("sample").getAsString() : null;
            parts.add(new Part(pos, role, sample));
        }
        return new StructureTemplate(machine, parts);
    }
}
