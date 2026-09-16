package com.joshua.excavation;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class ExcavationMod implements ModInitializer {
    public static final String MOD_ID = "excavation";
    public static final ResourceKey<Enchantment> EXCAVATION =
            ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(MOD_ID, "excavation"));

    private static final ThreadLocal<Boolean> BREAKING_EXTRA_BLOCKS =
            ThreadLocal.withInitial(() -> false);

    @Override
    public void onInitialize() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide() || BREAKING_EXTRA_BLOCKS.get()) {
                return;
            }

            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }

            ItemStack stack = serverPlayer.getMainHandItem();
            if (!isExcavationTool(stack)) {
                return;
            }

            int level = getExcavationLevel(world, stack);
            if (level <= 0) {
                return;
            }

            mineCube(serverPlayer, pos, level);
        });
    }

    private static boolean isExcavationTool(ItemStack stack) {
        return stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS);
    }

    private static int getExcavationLevel(Level world, ItemStack stack) {
        Holder.Reference<Enchantment> enchantment = world.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(EXCAVATION);

        return EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
    }

    private static void mineCube(ServerPlayer player, BlockPos center, int level) {
        int radius = level;

        BREAKING_EXTRA_BLOCKS.set(true);
        try {
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        if (x == 0 && y == 0 && z == 0) {
                            continue;
                        }

                        BlockPos target = center.offset(x, y, z);
                        BlockState targetState = player.level().getBlockState(target);

                        if (!canMineExtraBlock(player, target, targetState)) {
                            continue;
                        }

                        player.gameMode.destroyBlock(target);
                    }
                }
            }
        } finally {
            BREAKING_EXTRA_BLOCKS.set(false);
        }
    }

    private static boolean canMineExtraBlock(
            ServerPlayer player,
            BlockPos pos,
            BlockState targetState
    ) {
        if (targetState.isAir()) {
            return false;
        }

        if (targetState.getDestroySpeed(player.level(), pos) < 0.0F) {
            return false;
        }

        ItemStack stack = player.getMainHandItem();
        if (!stack.isCorrectToolForDrops(targetState)) {
            return false;
        }

        return true;
    }
}
