package it.miamod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import it.miamod.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

/**
 * Rende piu' morbido il colpo (swing) del giocatore: applica una curva "smoothstep"
 * al progresso dell'animazione. Questa e' la parte 1 delle animazioni; le animazioni
 * piu' complesse (mano, cambio oggetto) richiedono di agganciarsi al renderer.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

	@Inject(method = "getAttackAnim", at = @At("RETURN"), cancellable = true)
	private void miamod$smoothSwing(float partialTick, CallbackInfoReturnable<Float> cir) {
		ModConfig cfg = ModConfig.get();
		if (!cfg.smoothSwing || (Object) this != Minecraft.getInstance().player) {
			return;
		}
		float p = cir.getReturnValue();
		if (p <= 0f || p >= 1f) {
			return;
		}
		float eased = p * p * (3f - 2f * p);
		cir.setReturnValue(p + (eased - p) * cfg.swingSmoothing);
	}
}
