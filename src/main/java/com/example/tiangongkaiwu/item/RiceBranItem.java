package com.example.tiangongkaiwu.item;

import com.example.tiangongkaiwu.TiangongKaiwu;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 细糠：碓舂米时"皮膜成粉"的副产物。书曰「**以供犬豕之豢**」——拿去喂猪、喂狗。
 *
 * <p>它同时也是**粪肥的来源**，接上书里那句「人畜穢遺」：
 * 碓出糠 → 糠喂豕 → 豕还粪 → 粪肥田 → 稻更好。种、舂、喂、肥四步就闭环了。
 *
 * <p>同一头牲畜有冷却（记在实体持久化数据里，所以**不需要任何 tick 钩子**）。
 */
public class RiceBranItem extends Item {

    /** 喂食冷却（tick）：一头猪约一分钟出一份粪，够玩家慢慢攒。 */
    private static final int COOLDOWN = 1200;
    /** 持久化数据里的键。 */
    private static final String FED_KEY = "tiangongkaiwu.bran_fed";

    public RiceBranItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                 InteractionHand hand) {
        // 书只说"犬豕"；其它动物不拦（牛照旧吃小麦等）
        if (!(target instanceof Pig) && !(target instanceof Wolf)) {
            return InteractionResult.PASS;
        }
        if (target.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        CompoundTag data = target.getPersistentData();
        long now = target.level().getGameTime();
        long last = data.getLong(FED_KEY);
        if (last != 0L && now - last < COOLDOWN) {
            player.displayClientMessage(Component.translatable("item.tiangongkaiwu.rice_bran.full"), true);
            return InteractionResult.SUCCESS;
        }
        data.putLong(FED_KEY, now);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        // 豕还粪：脚下留一份粪肥
        target.level().addFreshEntity(new ItemEntity(target.level(),
                target.getX(), target.getY() + 0.2D, target.getZ(),
                new ItemStack(TiangongKaiwu.FEN_FEI.get())));
        if (target.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    target.getX(), target.getY() + 0.6D, target.getZ(), 6, 0.3D, 0.3D, 0.3D, 0.0D);
        }
        target.level().playSound(null, target.blockPosition(), SoundEvents.BONE_MEAL_USE,
                SoundSource.NEUTRAL, 0.6F, 1.1F);
        player.displayClientMessage(Component.translatable("item.tiangongkaiwu.rice_bran.fed"), true);
        return InteractionResult.SUCCESS;
    }
}
