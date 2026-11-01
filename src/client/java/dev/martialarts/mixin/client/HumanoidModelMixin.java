package dev.martialarts.mixin.client;

import dev.martialarts.client.MartialArtsClient;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Poses a strike over vanilla's pose; on HumanoidModel so worn armour follows the body. */
@Mixin(HumanoidModel.class)
abstract class HumanoidModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void martial_arts$pose(HumanoidRenderState state, CallbackInfo ci) {
        MartialArtsClient.pose((HumanoidModel<?>) (Object) this, state);
    }
}
