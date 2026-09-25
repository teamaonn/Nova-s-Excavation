package com.joshua.excavation;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class ExcavationMod implements ModInitializer {
    public static final String MOD_ID = "excavation";
    private static final ResourceKey<Enchantment> EXCAVATION = key("excavation");
    private static final ResourceKey<Enchantment> QUARRY = key("quarry");
    private static final ResourceKey<Enchantment> CHUNK_EATER = key("chunk_eater");
    private static final int BLOCKS_PER_TICK = 256;
    private static final Map<UUID, MiningJob> JOBS = new HashMap<>();
    private static final Map<UUID, InitialBreak> INITIAL_BREAKS = new HashMap<>();
    private static final ThreadLocal<Boolean> BREAKING_EXTRA_BLOCKS = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<ServerPlayer> COLLECTING_FOR = new ThreadLocal<>();

    private static ResourceKey<Enchantment> key(String name) {
        return ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(MOD_ID, name));
    }

    @Override
    public void onInitialize() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide() || BREAKING_EXTRA_BLOCKS.get() || !(player instanceof ServerPlayer serverPlayer)) return true;
            ItemStack tool = serverPlayer.getMainHandItem();
            INITIAL_BREAKS.remove(serverPlayer.getUUID());
            if (isExcavationTool(tool) && (getLevel(world, tool, QUARRY) > 0 || getLevel(world, tool, CHUNK_EATER) > 0)) {
                INITIAL_BREAKS.put(serverPlayer.getUUID(), new InitialBreak(pos.immutable(), tool.copy(), tool.getDamageValue()));
                if (tool.getDamageValue() > 0) tool.setDamageValue(tool.getDamageValue() - 1);
            }
            return true;
        });
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide() || BREAKING_EXTRA_BLOCKS.get() || !(player instanceof ServerPlayer serverPlayer)) return;
            InitialBreak first = INITIAL_BREAKS.remove(serverPlayer.getUUID());
            if (first != null && first.pos.equals(pos)) {
                ItemStack held = serverPlayer.getMainHandItem();
                if (held.isEmpty()) {
                    serverPlayer.setItemInHand(InteractionHand.MAIN_HAND, first.tool);
                } else if (held.getItem() == first.tool.getItem()) {
                    held.setDamageValue(first.damage);
                }
            }
            ItemStack tool = serverPlayer.getMainHandItem();
            if (!isExcavationTool(tool) || JOBS.containsKey(serverPlayer.getUUID())) return;

            int radius = 0;
            boolean freeDurability = false;
            int chunkLevel = getLevel(world, tool, CHUNK_EATER);
            int quarryLevel = getLevel(world, tool, QUARRY);
            int excavationLevel = getLevel(world, tool, EXCAVATION);
            if (chunkLevel > 0) {
                radius = 16 + Math.min(chunkLevel, 16);
                freeDurability = true;
            } else if (quarryLevel > 0) {
                radius = 8 + Math.min(quarryLevel, 8);
                freeDurability = true;
            } else if (excavationLevel > 0) {
                radius = Math.min(excavationLevel, 7);
            }
            if (radius > 0) JOBS.put(serverPlayer.getUUID(), new MiningJob(serverPlayer, pos.immutable(), tool, radius, freeDurability));
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Iterator<MiningJob> iterator = JOBS.values().iterator();
            while (iterator.hasNext()) {
                if (!iterator.next().tick()) iterator.remove();
            }
        });
    }

    public static ServerPlayer collectingFor() {
        return COLLECTING_FOR.get();
    }

    private static boolean isExcavationTool(ItemStack stack) {
        return stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS);
    }

    private static int getLevel(Level world, ItemStack stack, ResourceKey<Enchantment> key) {
        Holder.Reference<Enchantment> enchantment = world.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
        return EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
    }

    private static final class MiningJob {
        private final ServerPlayer player;
        private final BlockPos center;
        private final ItemStack tool;
        private final int radius;
        private final boolean freeDurability;
        private int index;

        private MiningJob(ServerPlayer player, BlockPos center, ItemStack tool, int radius, boolean freeDurability) {
            this.player = player;
            this.center = center;
            this.tool = tool;
            this.radius = radius;
            this.freeDurability = freeDurability;
        }

        private boolean tick() {
            if (player.isRemoved() || player.getMainHandItem() != tool || !isExcavationTool(tool)
                    || !player.level().hasChunkAt(center)) return false;
            int diameter = 2 * radius + 1;
            int total = diameter * diameter * diameter;
            int limit = Math.min(total, index + BLOCKS_PER_TICK);
            BREAKING_EXTRA_BLOCKS.set(true);
            try {
                while (index < limit) {
                    int current = index++;
                    int x = current / (diameter * diameter) - radius;
                    int y = current / diameter % diameter - radius;
                    int z = current % diameter - radius;
                    if (x == 0 && y == 0 && z == 0) continue;
                    BlockPos target = center.offset(x, y, z);
                    if (!player.level().hasChunkAt(target)) continue;
                    BlockState state = player.level().getBlockState(target);
                    if (state.isAir() || state.getDestroySpeed(player.level(), target) < 0.0F
                            || !tool.isCorrectToolForDrops(state)) continue;

                    int damage = tool.getDamageValue();
                    if (freeDurability && damage > 0) tool.setDamageValue(damage - 1);
                    if (freeDurability) COLLECTING_FOR.set(player);
                    try {
                        player.gameMode.destroyBlock(target);
                    } finally {
                        COLLECTING_FOR.remove();
                        if (freeDurability && !tool.isEmpty()) tool.setDamageValue(damage);
                    }
                    if (player.getMainHandItem() != tool || tool.isEmpty()) return false;
                }
            } finally {
                BREAKING_EXTRA_BLOCKS.set(false);
                COLLECTING_FOR.remove();
            }
            return index < total;
        }
    }

    private record InitialBreak(BlockPos pos, ItemStack tool, int damage) {}
}
