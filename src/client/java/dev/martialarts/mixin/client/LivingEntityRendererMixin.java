package dev.martialarts.mixin.client;

import dev.martialarts.MartialArts;
import dev.martialarts.client.MartialArtsClient;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Carries an entity's synced move, and the ticks since it started, into its render state for HumanoidModelMixin. */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void martial_arts$move(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        var shown = entity.getAttached(MartialArts.SHOWN);
        state.setData(MartialArtsClient.MOVE, shown == null ? "" : shown.move());
        state.setData(MartialArtsClient.AGE, shown == null ? 0F : entity.level().getGameTime() - shown.since() + partialTick);
    }
}
