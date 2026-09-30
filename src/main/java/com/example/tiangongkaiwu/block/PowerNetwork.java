package com.example.tiangongkaiwu.block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.tiangongkaiwu.TiangongKaiwu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 「劲」网络：动力总量分配（2026-09-27 拍板：传输零损耗、只让机器消耗）。
 *
 * <p>书源：《天工开物·粹精》「不及則粗，太過則粉」——劲分不到，碓就舂不透，出糙米。
 * 机制取自 Create 的 KineticNetwork（MIT，已研读 §10.1）：网络劲 = Σ动力源；
 * 每台机器有定额（碓 = 3）；劲按确定顺序分配，分不到的机器"不及"。
 *
 * <p><b>实现选择：无方块实体的确定性 BFS。</b>不走 Create 的常驻网络对象（那需要 BE），
 * 而是每台机器结算时从自己出发沿劲网泛洪一次，拿到「全网劲总量 + 全网机器列表」，
 * 然后按**坐标序**分配——任何一台机器算出的分配结果都一致（确定性是关键：
 * 若按"谁先 BFS 谁先得"，两台机器会各算各的、互相矛盾）。
 * 代价是每台机器各跑一次 BFS（上限 {@link #MAX_NODES} 格，低频 10 tick 一次），
 * 几十台的规模毫无压力；将来真到上百台再引入 BE 网络（规划 §8.3-4 的架构留了口）。
 *
 * <p>传播规则（§10.6 拍板）：
 * <ul>
 * <li>传动杆：只沿**自身轴向**传播（不再六向、不再每格衰减）；</li>
 * <li>牙轮：从进入方向垂直的四个方向传出——**90° 变向必须经过牙轮**；</li>
 * <li>动力源（筒车/牛车/踏车/拔车）与机器（碓）是**端点**：只出劲/只吃劲，不导劲。</li>
 * </ul>
 */
public final class PowerNetwork {

    /** 每台机器的劲消耗定额（书页口径：碓的"劲"）。 */
    public static final int DEFAULT_DEMAND = 3;
    /** 单次泛洪的方块数上限：防跑飞，也保证最坏情况下的开销有界。 */
    private static final int MAX_NODES = 256;

    private PowerNetwork() {
    }

    /**
     * 给一台机器分配劲。
     *
     * @return 分到的劲：= demand 劲足（"太过"）；1 ~ demand-1 劲不足（"不及"→出糙米）；0 没劲（停）。
     */
    public static int allocate(Level level, BlockPos machinePos, int demand) {
        Scan scan = scan(level, machinePos);
        int remaining = scan.totalPower;
        for (BlockPos m : scan.machines) {
            int take = Math.min(demand, remaining);
            if (m.equals(machinePos)) {
                return take;
            }
            remaining -= take;
        }
        return 0;
    }

    /** 网络劲总量（传动杆贴图用：本格通着多少劲，铜箍亮不亮）。 */
    public static int networkPower(Level level, BlockPos anyPos) {
        return scan(level, anyPos).totalPower;
    }

    // ==================== 泛洪 ====================

    /** 一次泛洪的结果：全网劲总量 + 全部机器（按坐标序，分配用）。 */
    private record Scan(int totalPower, List<BlockPos> machines) {
    }

    /** BFS 队列元素：位置 + 进入方向（决定牙轮往哪些方向传出）。 */
    private record Entry(BlockPos pos, Direction entryDir) {
    }

    private static Scan scan(Level level, BlockPos start) {
        int totalPower = 0;
        List<BlockPos> machines = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Deque<Entry> queue = new ArrayDeque<>();

        visited.add(start);
        queue.add(new Entry(start, null));

        while (!queue.isEmpty() && visited.size() < MAX_NODES) {
            Entry cur = queue.poll();
            BlockState state = level.getBlockState(cur.pos());

            // 起点（或任何被计入的格）按自身类型分发的方向
            List<Direction> outDirs;
            if (state.getBlock() instanceof ShaftBlock shaft) {
                outDirs = dirsAlong(state.getValue(ShaftBlock.AXIS));
            } else if (state.getBlock() instanceof GearBlock) {
                // 牙轮：向进入方向的垂直向传出；起点无进入方向 → 六向
                outDirs = new ArrayList<>();
                for (Direction d : Direction.values()) {
                    if (cur.entryDir() == null || d.getAxis() != cur.entryDir().getAxis()) {
                        outDirs.add(d);
                    }
                }
            } else {
                // 端点（机器或动力源）：不再外传
                continue;
            }

            for (Direction dir : outDirs) {
                BlockPos nbPos = cur.pos().relative(dir);
                if (!visited.add(nbPos)) {
                    continue;
                }
                BlockState nb = level.getBlockState(nbPos);
                if (nb.getBlock() instanceof ShaftBlock || nb.getBlock() instanceof GearBlock) {
                    queue.add(new Entry(nbPos, dir));
                } else if (nb.getBlock() == TiangongKaiwu.DUI.get()) {
                    machines.add(nbPos);                       // 机器是端点：吃劲，不导劲
                } else {
                    int power = WaterDevices.emittedPower(nb); // 动力源是端点：出劲，不导劲
                    if (power > 0) {
                        totalPower += power;
                    }
                }
            }
        }

        machines.sort(null); // BlockPos 自然序（确定性），任何机器视角算出的分配一致
        return new Scan(totalPower, machines);
    }

    /** 沿某轴的两个方向。 */
    private static List<Direction> dirsAlong(Direction.Axis axis) {
        List<Direction> dirs = new ArrayList<>(2);
        for (Direction d : Direction.values()) {
            if (d.getAxis() == axis) {
                dirs.add(d);
            }
        }
        return dirs;
    }
}
