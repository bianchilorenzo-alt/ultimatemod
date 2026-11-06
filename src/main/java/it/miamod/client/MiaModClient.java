package it.miamod.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import it.miamod.BuildHelper;
import it.miamod.MiaMod;
import it.miamod.ModConfig;
import it.miamod.WaypointCommands;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.Component;

public class MiaModClient implements ClientModInitializer {
	private static int ticks = 0;

	@Override
	public void onInitializeClient() {
		ModConfig.get();

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			WaypointCommands.register(dispatcher);
			BuildHelper.register(dispatcher);

			// /animazioni on | off | intensita <0-100>
			dispatcher.register(ClientCommandManager.literal("animazioni")
					.then(ClientCommandManager.literal("on").executes(ctx -> {
						ModConfig.get().smoothSwing = true;
						ModConfig.save();
						ctx.getSource().sendFeedback(Component.literal("Animazioni fluide: ATTIVE"));
						return 1;
					}))
					.then(ClientCommandManager.literal("off").executes(ctx -> {
						ModConfig.get().smoothSwing = false;
						ModConfig.save();
						ctx.getSource().sendFeedback(Component.literal("Animazioni fluide: DISATTIVE"));
						return 1;
					}))
					.then(ClientCommandManager.literal("intensita")
							.then(ClientCommandManager.argument("valore", IntegerArgumentType.integer(0, 100))
									.executes(ctx -> {
										int v = IntegerArgumentType.getInteger(ctx, "valore");
										ModConfig.get().swingSmoothing = v / 100f;
										ModConfig.save();
										ctx.getSource().sendFeedback(Component.literal("Intensita' animazioni: " + v + "%"));
										return 1;
									}))));
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ticks++;
			BuildHelper.tick(client);
			WaypointCommands.tickTracker(client, ticks);
		});

		MiaMod.LOGGER.info("Ultimate Mod (client) caricata!");
	}
}
