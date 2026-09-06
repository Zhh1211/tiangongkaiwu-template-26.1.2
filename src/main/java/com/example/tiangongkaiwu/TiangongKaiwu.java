package com.example.tiangongkaiwu;

import java.util.List;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.example.tiangongkaiwu.block.RiceCropBlock;
import com.example.tiangongkaiwu.block.HanmoTaiBlock;
import com.example.tiangongkaiwu.block.entity.HanmoTaiBlockEntity;
import com.example.tiangongkaiwu.hanmo.Puzzle;
import com.example.tiangongkaiwu.hanmo.PuzzleLoader;
import com.example.tiangongkaiwu.hanmo.PuzzleRegistry;
import com.example.tiangongkaiwu.hanmo.network.PuzzleSyncPayload;
import com.example.tiangongkaiwu.item.CanYeItem;
import com.example.tiangongkaiwu.menu.HanmoTaiMenu;

@Mod(TiangongKaiwu.MODID)
public class TiangongKaiwu {

    public static final String MODID = "tiangongkaiwu";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister
            .create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister
            .create(Registries.CREATIVE_MODE_TAB, MODID);

    // ========== 新增：菜单注册器 ==========
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, MODID);

    // 翰墨台菜单类型：必须在 mod 构造期（注册窗口打开前）完成注册，
    // 否则 HanmoTaiMenu 类懒加载到 RegisterMenuScreensEvent 才触发 <clinit>，
    // 会因「RegisterEvent 已触发」而抛 IllegalStateException 导致客户端崩溃。
    public static final DeferredHolder<MenuType<?>, MenuType<HanmoTaiMenu>> HANMO_TAI_MENU =
            MENUS.register("hanmo_tai_menu",
                    () -> IMenuTypeExtension.create((windowId, playerInventory, extraData) ->
                            new HanmoTaiMenu(windowId, playerInventory)));

    // ============================================================
    // 水稻作物
    // ============================================================
    public static final DeferredBlock<Block> RICE_CROP = BLOCKS.register(
        "rice_crop",
        () -> new RiceCropBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.PLANT)
                .noCollission()
                .randomTicks()
                .instabreak()
                .sound(SoundType.CROP)
        )
    );

    public static final DeferredItem<BlockItem> RICE_SEED = ITEMS.register(
        "rice_seed",
        () -> new BlockItem(
            RICE_CROP.get(),
            new Item.Properties()
        )
    );

    // ============================================================
    // 翰墨台与残页
    // ============================================================
    public static final DeferredBlock<HanmoTaiBlock> HANMO_TAI = BLOCKS.register(
        "hanmo_tai",
        () -> new HanmoTaiBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.5f, 3.0f)
                .noOcclusion()
                .pushReaction(PushReaction.BLOCK)   // 容器方块防活塞推动（1.21.1 无 DENY，BLOCK 即推不动），避免材料随方块丢失
        )
    );

    /** 翰墨台方块实体：三格材料（残页/墨水/纸）存方块内，随区块存档。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HanmoTaiBlockEntity>> HANMO_TAI_BE_TYPE =
        BLOCK_ENTITY_TYPES.register("hanmo_tai",
            () -> BlockEntityType.Builder.of(HanmoTaiBlockEntity::new, HANMO_TAI.get()).build(null));

    public static final DeferredItem<BlockItem> HANMO_TAI_ITEM = ITEMS.registerSimpleBlockItem(
        "hanmo_tai",
        HANMO_TAI
    );

    public static final DeferredItem<CanYeItem> CAN_YE = ITEMS.register(
        "can_ye",
        () -> new CanYeItem(
            new Item.Properties()
                .stacksTo(1)
        )
    );

    // ============================================================
    // 翰墨台耗材：松烟墨、宣纸
    // 注：槽位同时接受原版墨囊(ink_sac)与纸(paper)，见 HanmoTaiMenu 的材料白名单
    // ============================================================
    public static final DeferredItem<Item> SONGYAN_MO = ITEMS.registerSimpleItem(
        "songyan_mo",
        new Item.Properties()
    );

    public static final DeferredItem<Item> XUAN_ZHI = ITEMS.registerSimpleItem(
        "xuan_zhi",
        new Item.Properties()
    );

    // ============================================================
    // 创造模式标签页：天工开物
    // ============================================================
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TIANGONG_TAB = CREATIVE_MODE_TABS
            .register("tiangongkaiwu", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.tiangongkaiwu"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> CAN_YE.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(RICE_SEED);
                        output.accept(CAN_YE);
                        output.accept(SONGYAN_MO);
                        output.accept(XUAN_ZHI);
                        output.accept(HANMO_TAI_ITEM);
                    }).build());

    // ============================================================
    // 构造函数
    // ============================================================
    public TiangongKaiwu(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        MENUS.register(modEventBus);   // ← 新增注册

        NeoForge.EVENT_BUS.register(this);

        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(this::onRegisterPayloads);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }

        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());

        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(RICE_SEED);
            event.accept(CAN_YE);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
    }

    /**
     * 注册翰墨台题库的数据包加载器。
     * 题库位于 data/ 下，只有服务端会加载；客户端改由自定义网络包逐题下发。
     */
    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new PuzzleLoader());
    }

    /**
     * 注册题库同步网络包（服务端 → 客户端）。
     * 客户端 handler 直接灌进 PuzzleRegistry，题库在单机客户端物理端也能拿到。
     */
    private void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                PuzzleSyncPayload.TYPE,
                PuzzleSyncPayload.STREAM_CODEC,
                (payload, context) -> {
                    PuzzleRegistry.replaceAll(payload.puzzles());
                    LOGGER.info("[天工开物] 客户端收到题库，共 {} 道", payload.puzzles().size());
                });
    }

    /**
     * 玩家进入世界后，把服务端已加载的全量题库下发给该玩家客户端。
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        List<Puzzle> puzzles = PuzzleRegistry.all();
        if (puzzles.isEmpty()) {
            return;
        }
        PacketDistributor.sendToPlayer(serverPlayer, new PuzzleSyncPayload(puzzles));
        LOGGER.info("[天工开物] 已向玩家 {} 下发题库 {} 道", serverPlayer.getName().getString(), puzzles.size());
    }
}
