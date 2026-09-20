package com.example.tiangongkaiwu.compat.jade;

import com.example.tiangongkaiwu.TiangongKaiwu;
import com.example.tiangongkaiwu.block.DragonBoneCarBlock;
import com.example.tiangongkaiwu.block.DuiBlock;
import com.example.tiangongkaiwu.block.FengFanBlock;
import com.example.tiangongkaiwu.block.JianBlock;
import com.example.tiangongkaiwu.block.RiceStalkBlock;
import com.example.tiangongkaiwu.block.RiceStalkBlockDry;
import com.example.tiangongkaiwu.block.ShaftBlock;
import com.example.tiangongkaiwu.block.TongCheBlock;
import com.example.tiangongkaiwu.block.WaterDevices;
import com.example.tiangongkaiwu.block.WellLiftBlock;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade 集成：把天工开物装置的内部状态显示在准星提示里，省得靠猜。
 *
 * <p>装 Jade（或任意支持 WAILA 协议的查看器）时才生效；mod 本身**不硬依赖** Jade——
 * 这个类只被 Jade 的 {@code @WailaPlugin} 注解扫描发现，mod 主类不引用它，
 * 所以没装 Jade 时它不会被加载（不会 NoClassDefFoundError）。
 *
 * <p>显示内容：
 * <ul>
 *   <li>稻田：田水 N/7（见底会警告）、生长阶段 N/7</li>
 *   <li>枯秆：已枯（提示倒水可救）</li>
 *   <li>筒车 / 龙骨车 / 桔槔 / 辘轳 / 风帆车：运转中还是停、输出动力、是否栓木碍止</li>
 *   <li>传动轴：当前动力 N/15</li>
 *   <li>梘：槽中有水没水</li>
 *   <li>碓：正在舂还是待料</li>
 * </ul>
 */
@WailaPlugin("tiangongkaiwu")
public class TiangongKaiwuJadePlugin implements IWailaPlugin {

    private static final IBlockComponentProvider DEVICE_STATE = new DeviceStateProvider();

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        for (Class<? extends Block> type : new Class[]{
                RiceStalkBlock.class, RiceStalkBlockDry.class,
                TongCheBlock.class, ShaftBlock.class, JianBlock.class,
                DragonBoneCarBlock.class, WellLiftBlock.class, FengFanBlock.class,
                DuiBlock.class}) {
            registration.registerBlockComponent(DEVICE_STATE, type);
        }
    }

    /** 把方块状态翻译成人话。 */
    static final class DeviceStateProvider implements IBlockComponentProvider {

        private static final ResourceLocation UID =
                ResourceLocation.fromNamespaceAndPath("tiangongkaiwu", "device_state");

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockState state = accessor.getBlockState();
            Block block = state.getBlock();

            if (block == TiangongKaiwu.RICE_STALK.get()) {
                int moisture = state.getValue(RiceStalkBlock.MOISTURE);
                tooltip.add(Component.translatable("jade.tiangongkaiwu.field_water",
                        moisture, RiceStalkBlock.MAX_MOISTURE));
                tooltip.add(Component.translatable("jade.tiangongkaiwu.growth",
                        state.getValue(RiceStalkBlock.AGE), RiceStalkBlock.MAX_AGE));
                if (moisture < RiceStalkBlock.WATER_VISIBLE_MIN) {
                    tooltip.add(Component.translatable("jade.tiangongkaiwu.need_water"));
                }
                return;
            }

            if (block == TiangongKaiwu.RICE_STALK_DRY.get()) {
                tooltip.add(Component.translatable("jade.tiangongkaiwu.parched"));
                return;
            }

            if (block instanceof TongCheBlock) {
                boolean running = WaterDevices.isRunning(state);
                tooltip.add(Component.translatable(running
                        ? "jade.tiangongkaiwu.running" : "jade.tiangongkaiwu.stopped"));
                if (state.getValue(TongCheBlock.PLUGGED)) {
                    tooltip.add(Component.translatable("jade.tiangongkaiwu.plugged"));
                } else if (!running) {
                    tooltip.add(Component.translatable("jade.tiangongkaiwu.need_flowing"));
                }
                tooltip.add(Component.translatable("jade.tiangongkaiwu.power",
                        WaterDevices.emittedPower(state)));
                return;
            }

            if (block instanceof ShaftBlock) {
                tooltip.add(Component.translatable("jade.tiangongkaiwu.power_max",
                        state.getValue(ShaftBlock.POWER), ShaftBlock.MAX_POWER));
                return;
            }

            if (block instanceof JianBlock) {
                tooltip.add(Component.translatable(state.getValue(JianBlock.WATERED)
                        ? "jade.tiangongkaiwu.trough_filled" : "jade.tiangongkaiwu.trough_empty"));
                return;
            }

            if (block instanceof DuiBlock) {
                tooltip.add(Component.translatable(state.getValue(DuiBlock.ACTIVE)
                        ? "jade.tiangongkaiwu.pounding" : "jade.tiangongkaiwu.idle"));
                return;
            }

            if (block instanceof DragonBoneCarBlock || block instanceof WellLiftBlock
                    || block instanceof FengFanBlock) {
                boolean running = WaterDevices.isRunning(state);
                tooltip.add(Component.translatable(running
                        ? "jade.tiangongkaiwu.running" : "jade.tiangongkaiwu.stopped"));
                int power = WaterDevices.emittedPower(state);
                if (power > 0) {
                    tooltip.add(Component.translatable("jade.tiangongkaiwu.power", power));
                }
            }
        }
    }
}
