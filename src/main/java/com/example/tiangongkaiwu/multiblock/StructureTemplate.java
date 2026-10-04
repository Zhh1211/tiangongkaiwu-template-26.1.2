package com.example.tiangongkaiwu.multiblock;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import com.example.tiangongkaiwu.block.ShaftBlock;

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
 * <li>{@link Role#FEED} 料口——**必须漏斗且朝向匹配**（2026-10-04 拍板：视觉自说明）</li>
 * <li>{@link Role#SHAFT_SOCKET} 轴位——**必须传动杆且轴向匹配**（劲从杆处传入）</li>
 * </ul>
 *
 * <p>朝向锁定（2026-10-04 拍板）：模板记录样例方块的属性（杆的 axis、漏斗的 facing），
 * 成型匹配时按结构旋转换算后强制比对——玩家乱摆朝向直接结构不符。
 */
public final class StructureTemplate {

    public enum Role {
        WOOD, SLIDER, PIVOT, ROTOR, FEED, SHAFT_SOCKET
    }

    /**
     * 一个部件位：相对核心的偏移 + 角色 + 样例（wood 记方块种类供补料优先同材质；
     * shaft_socket/feed 记属性供朝向锁定）。
     */
    public record Part(BlockPos pos, Role role, String sample, Map<String, String> props) {
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
                case FEED -> isHopperWithFacing(state, part, rot);
                case SHAFT_SOCKET -> isShaftWithAxis(state, part, rot);
            };
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /** 轴位：必须是传动杆，且轴向与模板样例按旋转换算后一致。 */
    private static boolean isShaftWithAxis(BlockState state, Part part, int rot) {
        if (!(state.getBlock() instanceof ShaftBlock)) {
            return false;
        }
        String expect = part.props() != null ? part.props().get("axis") : null;
        if (expect == null) {
            return true;
        }
        String actual = state.getValue(ShaftBlock.AXIS).getName();
        return rotateAxisName(expect, rot).equals(actual);
    }

    /** 料口：必须是漏斗，且朝向与模板样例按旋转换算后一致（上下向不受水平旋转影响）。 */
    private static boolean isHopperWithFacing(BlockState state, Part part, int rot) {
        if (!state.is(Blocks.HOPPER)) {
            return false;
        }
        String expect = part.props() != null ? part.props().get("facing") : null;
        if (expect == null) {
            return true;
        }
        Direction expected = rotateFacing(Direction.byName(expect), rot);
        Direction actual = state.hasProperty(BlockStateProperties.FACING_HOPPER)
                ? state.getValue(BlockStateProperties.FACING_HOPPER) : null;
        return actual == expected;
    }

    /** 轴名随结构旋转：spin 奇数次 x↔z 互换，y 不变（与 {@link #spin} 的 (x,z)→(z,−x) 一致）。 */
    private static String rotateAxisName(String axis, int rot) {
        if (rot % 2 == 0 || axis.equals("y")) {
            return axis;
        }
        return axis.equals("x") ? "z" : "x";
    }

    /** 水平朝向随结构旋转：与 spin 的偏移旋转同向（NORTH→WEST），上下向不变。 */
    private static Direction rotateFacing(Direction d, int rot) {
        if (d.getAxis() == Direction.Axis.Y) {
            return d;
        }
        for (int i = 0; i < rot; i++) {
            d = d.getCounterClockWise();
        }
        return d;
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
            Map<String, String> props = null;
            if (o.has("props")) {
                props = new java.util.LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> en : o.getAsJsonObject("props").entrySet()) {
                    props.put(en.getKey(), en.getValue().getAsString());
                }
            }
            parts.add(new Part(pos, role, sample, props));
        }
        return new StructureTemplate(machine, parts);
    }
}
