package com.example.tiangongkaiwu;

import java.util.List;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
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

import com.example.tiangongkaiwu.block.RicePanicleBlock;
import com.example.tiangongkaiwu.block.RiceStalkBlock;
import com.example.tiangongkaiwu.block.RiceStalkBlockDry;
import com.example.tiangongkaiwu.block.DragonBoneCarBlock;
import com.example.tiangongkaiwu.block.DuiBlock;
import com.example.tiangongkaiwu.block.FengFanBlock;
import com.example.tiangongkaiwu.block.JianBlock;
import com.example.tiangongkaiwu.block.ShaftBlock;
import com.example.tiangongkaiwu.block.TongCheBlock;
import com.example.tiangongkaiwu.block.WellLiftBlock;
import com.example.tiangongkaiwu.block.HanmoTaiBlock;
import com.example.tiangongkaiwu.block.entity.HanmoTaiBlockEntity;
import com.example.tiangongkaiwu.hanmo.Puzzle;
import com.example.tiangongkaiwu.hanmo.PuzzleLoader;
import com.example.tiangongkaiwu.hanmo.PuzzleRegistry;
import com.example.tiangongkaiwu.hanmo.PuzzleSettlement;
import com.example.tiangongkaiwu.hanmo.network.PuzzleDonePayload;
import com.example.tiangongkaiwu.hanmo.network.PuzzleSyncPayload;
import com.example.tiangongkaiwu.hanmo.network.SettlementResultPayload;
import com.example.tiangongkaiwu.item.CanYeItem;
import com.example.tiangongkaiwu.item.FertilizerItem;
import com.example.tiangongkaiwu.item.RiceBranItem;
import com.example.tiangongkaiwu.item.ResidualData;
import com.example.tiangongkaiwu.item.RiceGrainItem;
import com.example.tiangongkaiwu.menu.HanmoTaiMenu;
import com.example.tiangongkaiwu.recipe.PoundingRecipe;

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

    // ========== 加工配方：碓（舂米）==========
    // 数据驱动：data/tiangongkaiwu/recipe/*.json，type 写 "tiangongkaiwu:pounding"。
    // 详见 PoundingRecipe（input / min_power / result+byproduct / coarse 四件）。
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<PoundingRecipe>> POUNDING_TYPE =
            RECIPE_TYPES.register("pounding",
                    () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(MODID, "pounding")));
    public static final DeferredHolder<RecipeSerializer<?>, PoundingRecipe.Serializer> POUNDING_SERIALIZER =
            RECIPE_SERIALIZERS.register("pounding", PoundingRecipe.Serializer::new);

    // ========== 物品数据组件：残页状态（目标条目/是否已译/成绩经验） ==========
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MODID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResidualData>> RESIDUAL =
            DATA_COMPONENTS.register("residual", () -> DataComponentType.<ResidualData>builder()
                    .persistent(ResidualData.CODEC)
                    .networkSynchronized(ResidualData.STREAM_CODEC)
                    .build());

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
    // 水稻作物（v0.3 重做：水培两段式 — 浸水稻秆 + 成熟稻穗 + 枯秆）
    // 详见 docs/乃粒稻作玩法设计.md
    // ============================================================
    /** 下部浸水稻秆（AGE 0–7，成熟后自动在正上方抽穗）。 */
    public static final DeferredBlock<RiceStalkBlock> RICE_STALK = BLOCKS.register(
        "rice_stalk",
        () -> new RiceStalkBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.PLANT)
                .noCollission()
                .randomTicks()
                .instabreak()
                .sound(SoundType.CROP)
        )
    );

    /** 上部稻穗（仅成熟期出现；打穗掉稻谷 2–3 + 秆再生）。 */
    public static final DeferredBlock<RicePanicleBlock> RICE_PANICLE = BLOCKS.register(
        "rice_panicle",
        () -> new RicePanicleBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.PLANT)
                .noCollission()
                .instabreak()
                .sound(SoundType.CROP)
        )
    );

    /** 枯秆（演示版稻灾：空桶取水即转入此态，数段后自毁；倒水救活为浸水稻秆 AGE 0）。 */
    public static final DeferredBlock<RiceStalkBlockDry> RICE_STALK_DRY = BLOCKS.register(
        "rice_stalk_dry",
        () -> new RiceStalkBlockDry(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.PLANT)
                .noCollission()
                .randomTicks()
                .instabreak()
                .sound(SoundType.CROP)
        )
    );

    /** 稻谷：种子 + 脱粒原料（单一物品，不造单独种子物品）。*/
    public static final DeferredItem<RiceGrainItem> RICE_GRAIN = ITEMS.register(
        "rice_grain",
        () -> new RiceGrainItem(new Item.Properties())
    );

    /** 米：脱粒产物，可食（小恢复主食档：饱食 3 / 饱和度 0.4）。*/
    public static final DeferredItem<Item> RICE = ITEMS.register(
        "rice",
        () -> new Item(
            new Item.Properties()
                .food(new FoodProperties.Builder()
                    .nutrition(3)
                    .saturationModifier(0.4f)
                    .build())
        )
    );

    /** 糙米：动力不足时"舂不透"的产物（书：不及則粗）；丢回碓再舂一次即成白米。 */
    public static final DeferredItem<Item> BROWN_RICE = ITEMS.register(
        "brown_rice",
        () -> new Item(
            new Item.Properties()
                .food(new FoodProperties.Builder()
                    .nutrition(2)
                    .saturationModifier(0.25f)
                    .build())
        )
    );

    /** 细糠：舂米的副产物，皮膜成粉（书：以供犬豕之豢）。手持右键猪/犬即可喂，豕还粪。 */
    public static final DeferredItem<RiceBranItem> RICE_BRAN = ITEMS.register(
        "rice_bran",
        () -> new RiceBranItem(new Item.Properties())
    );

    // ============================================================
    // 稻宜·粪田（对症肥料）：书「土性不同改土法不同，用错不宜也」
    // 生效判定在 RiceStalkBlock.useItemOn —— 只有它知道田底下铺的是什么土
    // ============================================================
    /** 粪肥：人畜穢遺，普天之所同也——通用催熟。来源：细糠喂猪，豕还粪。 */
    public static final DeferredItem<FertilizerItem> FEN_FEI = ITEMS.register(
        "fen_fei",
        () -> new FertilizerItem(new Item.Properties(), "fen_fei")
    );

    /** 骨灰：凡禽獸骨烧成，只宜冷浆土（书：土性帶冷漿者，宜骨灰蘸秧根）。 */
    public static final DeferredItem<FertilizerItem> GU_HUI = ITEMS.register(
        "gu_hui",
        () -> new FertilizerItem(new Item.Properties(), "gu_hui")
    );

    /** 石灰：只宜冷浆土（书：石灰淹苗足，向陽暖土不宜也）。 */
    public static final DeferredItem<FertilizerItem> SHI_HUI = ITEMS.register(
        "shi_hui",
        () -> new FertilizerItem(new Item.Properties(), "shi_hui")
    );

    // ============================================================
    // 水利装置（B/C 批）：筒车（水力）／牛车／踏车／拔车（龙骨车三驱动）／梘（引水槽）／传动轴（动力传动）
    // 动力模型：动力源 → 传动轴（每格衰减 1 点）→ 加工机器（后续批：舂米臼等）
    // 详见 docs/乃粒稻作玩法设计.md「水利」与 docs/天工开物·乃粒原文.md「水利」节
    // ============================================================
    /** 筒车：放下即自动展开为 3×3 大轮；立于流水之上激轮自转，输出动力 12，并可给邻接的梘送水。 */
    public static final DeferredBlock<TongCheBlock> TONG_CHE = BLOCKS.register(
        "tong_che",
        () -> new TongCheBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(1.5f)
                .noOcclusion()
                .noCollission()
                .sound(SoundType.WOOD)
        )
    );

    /** 梘（引水槽）：把水引到远处的田里；槽里有水时替挨着的稻田补水。 */
    public static final DeferredBlock<JianBlock> JIAN = BLOCKS.register(
        "jian",
        () -> new JianBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(0.8f)
                .noOcclusion()
                .noCollission()
                .sound(SoundType.WOOD)
        )
    );

    /** 传动轴：把动力送到加工机器（沿轴网每格衰减 1 点）。 */
    public static final DeferredBlock<ShaftBlock> SHAFT = BLOCKS.register(
        "shaft",
        () -> new ShaftBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(0.8f)
                .noOcclusion()
                .noCollission()
                .sound(SoundType.WOOD)
        )
    );

    public static final DeferredItem<BlockItem> TONG_CHE_ITEM =
            ITEMS.registerSimpleBlockItem("tong_che", TONG_CHE);
    public static final DeferredItem<BlockItem> JIAN_ITEM =
            ITEMS.registerSimpleBlockItem("jian", JIAN);
    public static final DeferredItem<BlockItem> SHAFT_ITEM =
            ITEMS.registerSimpleBlockItem("shaft", SHAFT);

    /** 牛车（龙骨车·长车）：近旁有拴绳系住的牛则转，动力 10（书：一人竟日五亩，而牛则倍之）。 */
    public static final DeferredBlock<DragonBoneCarBlock> NIU_CHE = BLOCKS.register(
        "niu_che",
        () -> new DragonBoneCarBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(1.5f)
                .noOcclusion()
                .sound(SoundType.WOOD),
            DragonBoneCarBlock.Kind.NIU
        )
    );

    /** 踏车（龙骨车·长车）：有人站在车上踏转则转，动力 5（书：聚数人踏转，一人竟日五亩）。 */
    public static final DeferredBlock<DragonBoneCarBlock> TA_CHE = BLOCKS.register(
        "ta_che",
        () -> new DragonBoneCarBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(1.5f)
                .noOcclusion()
                .sound(SoundType.WOOD),
            DragonBoneCarBlock.Kind.TA
        )
    );

    /** 拔车（龙骨车·短车）：有人在车旁手摇则转，动力 2（书：数尺之车，一人两手疾转，二亩而已）。 */
    public static final DeferredBlock<DragonBoneCarBlock> BA_CHE = BLOCKS.register(
        "ba_che",
        () -> new DragonBoneCarBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(1.0f)
                .noOcclusion()
                .sound(SoundType.WOOD),
            DragonBoneCarBlock.Kind.BA
        )
    );

    public static final DeferredItem<BlockItem> NIU_CHE_ITEM =
            ITEMS.registerSimpleBlockItem("niu_che", NIU_CHE);
    public static final DeferredItem<BlockItem> TA_CHE_ITEM =
            ITEMS.registerSimpleBlockItem("ta_che", TA_CHE);
    public static final DeferredItem<BlockItem> BA_CHE_ITEM =
            ITEMS.registerSimpleBlockItem("ba_che", BA_CHE);

    // ============================================================
    // 粹精·舂米：碓（动力网的第一个"消费端"）
    // 依据 docs/天工开物·粹精原文.md 攻稻节：水碓主舂，則兼併礱功；不及則粗；細糠以供犬豕
    // ============================================================
    /** 碓（舂米）：相邻传动轴有动力即自转；料从上方容器取、产物入下方容器或弹出。一臼一格，可并列多臼。 */
    public static final DeferredBlock<DuiBlock> DUI = BLOCKS.register(
        "dui",
        () -> new DuiBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(2.0f)
                .noOcclusion()
                .sound(SoundType.STONE)
        )
    );

    public static final DeferredItem<BlockItem> DUI_ITEM =
            ITEMS.registerSimpleBlockItem("dui", DUI);

    // ============================================================
    // 水利收尾：井具（桔槔／辘轳）+ 风帆车（排水）
    // 书：用桔槔、轆轤，功勞又甚細已 ／ 揚郡以風帆數扇，俟風轉車，風息則止……
    //     此車為救潦，欲去澤水以便栽種。蓋去水非取水也，不適濟旱
    // ============================================================
    /** 桔槔：临水而立，靠配重无人自汲，但"功劳甚细"——全套水利里最慢的一档。 */
    public static final DeferredBlock<WellLiftBlock> JIE_GAO = BLOCKS.register(
        "jie_gao",
        () -> new WellLiftBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(1.0f)
                .noOcclusion()
                .sound(SoundType.WOOD),
            WellLiftBlock.Kind.JIE_GAO
        )
    );

    /** 辘轳：井上绞盘，须有人在旁摇；比桔槔快，仍远逊筒车。 */
    public static final DeferredBlock<WellLiftBlock> LU_LU = BLOCKS.register(
        "lu_lu",
        () -> new WellLiftBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(1.0f)
                .noOcclusion()
                .sound(SoundType.WOOD),
            WellLiftBlock.Kind.LU_LU
        )
    );

    /** 风帆车：下雨才转，专司**排水**（抽掉半径内的水源，把涝地变干田）；对旱田毫无用处。 */
    public static final DeferredBlock<FengFanBlock> FENG_FAN_CHE = BLOCKS.register(
        "feng_fan_che",
        () -> new FengFanBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(1.5f)
                .noOcclusion()
                .sound(SoundType.WOOD)
        )
    );

    public static final DeferredItem<BlockItem> JIE_GAO_ITEM =
            ITEMS.registerSimpleBlockItem("jie_gao", JIE_GAO);
    public static final DeferredItem<BlockItem> LU_LU_ITEM =
            ITEMS.registerSimpleBlockItem("lu_lu", LU_LU);
    public static final DeferredItem<BlockItem> FENG_FAN_CHE_ITEM =
            ITEMS.registerSimpleBlockItem("feng_fan_che", FENG_FAN_CHE);

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
                        output.accept(RICE_GRAIN);
                        output.accept(RICE);
                        output.accept(BROWN_RICE);
                        output.accept(RICE_BRAN);
                        output.accept(FEN_FEI);
                        output.accept(GU_HUI);
                        output.accept(SHI_HUI);
                        output.accept(CAN_YE);
                        output.accept(SONGYAN_MO);
                        output.accept(XUAN_ZHI);
                        output.accept(HANMO_TAI_ITEM);
                        output.accept(TONG_CHE_ITEM);
                        output.accept(JIAN_ITEM);
                        output.accept(SHAFT_ITEM);
                        output.accept(NIU_CHE_ITEM);
                        output.accept(TA_CHE_ITEM);
                        output.accept(BA_CHE_ITEM);
                        output.accept(DUI_ITEM);
                        output.accept(JIE_GAO_ITEM);
                        output.accept(LU_LU_ITEM);
                        output.accept(FENG_FAN_CHE_ITEM);
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
        DATA_COMPONENTS.register(modEventBus);
        RECIPE_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);

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
            event.accept(RICE_GRAIN);
            event.accept(RICE);
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

        // 客户端三句全对 → 服务端誊录结算（服务端权威：校验材料/残页/条目一致 → 消耗 → 翻转残页烙经验）
        event.registrar("1").playToServer(
                PuzzleDonePayload.TYPE,
                PuzzleDonePayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer serverPlayer) {
                        PuzzleSettlement.handle(serverPlayer, payload);
                    }
                });

        // 结算结果回执：失败时在客户端弹原因（成功无需提示，屏幕已显示"通篇译毕"）
        event.registrar("1").playToClient(
                SettlementResultPayload.TYPE,
                SettlementResultPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (payload.ok()) {
                        return;
                    }
                    String key = payload.reasonKey();
                    if (key == null || key.isBlank()) {
                        return;
                    }
                    if (context.player() instanceof net.minecraft.client.player.LocalPlayer clientPlayer) {
                        clientPlayer.sendSystemMessage(net.minecraft.network.chat.Component.translatable(key));
                    }
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
