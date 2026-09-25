package com.joshua.excavation.mixin;

import com.joshua.excavation.ExcavationMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void excavation$collectDrop(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer player = ExcavationMod.collectingFor();
        if (player == null || !(entity instanceof ItemEntity item) || player.level() != (Object) this) return;
        ItemStack remaining = item.getItem().copy();
        player.getInventory().add(remaining);
        if (remaining.isEmpty()) {
            cir.setReturnValue(true);
        } else {
            item.setItem(remaining);
        }
    }
}
