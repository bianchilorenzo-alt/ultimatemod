package it.miamod;

import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;

/**
 * Waypoint: /wp add|del|list|dir|track|stop
 * Il waypoint "tracciato" mostra distanza e direzione sopra la hotbar.
 */
public class WaypointCommands {

	public static void register(CommandDispatcher<FabricClientCommandSource> d) {
		d.register(ClientCommandManager.literal("wp")
				.then(ClientCommandManager.literal("add")
						.then(ClientCommandManager.argument("nome", StringArgumentType.word()).executes(WaypointCommands::add)))
				.then(ClientCommandManager.literal("del")
						.then(ClientCommandManager.argument("nome", StringArgumentType.word()).executes(WaypointCommands::del)))
				.then(ClientCommandManager.literal("list").executes(WaypointCommands::list))
				.then(ClientCommandManager.literal("dir")
						.then(ClientCommandManager.argument("nome", StringArgumentType.word()).executes(WaypointCommands::dir)))
				.then(ClientCommandManager.literal("track")
						.then(ClientCommandManager.argument("nome", StringArgumentType.word()).executes(WaypointCommands::track)))
				.then(ClientCommandManager.literal("stop").executes(ctx -> {
					ModConfig.get().tracked = "";
					ModConfig.save();
					ctx.getSource().sendFeedback(Component.literal("Tracciamento fermato."));
					return 1;
				})));
	}

	/** Ricava "minecraft:overworld" dalla chiave della dimensione, senza dipendere dai nomi dei metodi. */
	static String dimName(ResourceKey<?> key) {
		String s = String.valueOf(key);
		int i = s.lastIndexOf('/');
		if (i >= 0) {
			s = s.substring(i + 1);
		}
		return s.replace("]", "").trim();
	}

	private static Waypoint find(String name) {
		for (Waypoint w : ModConfig.get().waypoints) {
			if (w.name.equalsIgnoreCase(name)) {
				return w;
			}
		}
		return null;
	}

	private static int add(CommandContext<FabricClientCommandSource> ctx) {
		String name = StringArgumentType.getString(ctx, "nome");
		Player p = ctx.getSource().getPlayer();
		BlockPos pos = p.blockPosition();
		String dim = dimName(ctx.getSource().getWorld().dimension());
		Waypoint old = find(name);
		if (old != null) {
			ModConfig.get().waypoints.remove(old);
		}
		ModConfig.get().waypoints.add(new Waypoint(name, pos.getX(), pos.getY(), pos.getZ(), dim));
		ModConfig.save();
		ctx.getSource().sendFeedback(Component.literal(
				"Waypoint '" + name + "' salvato a " + pos.getX() + " " + pos.getY() + " " + pos.getZ()));
		return 1;
	}

	private static int del(CommandContext<FabricClientCommandSource> ctx) {
		String name = StringArgumentType.getString(ctx, "nome");
		Waypoint w = find(name);
		if (w == null) {
			ctx.getSource().sendFeedback(Component.literal("Waypoint '" + name + "' non trovato."));
			return 0;
		}
		ModConfig.get().waypoints.remove(w);
		ModConfig.save();
		ctx.getSource().sendFeedback(Component.literal("Waypoint '" + name + "' eliminato."));
		return 1;
	}

	private static int list(CommandContext<FabricClientCommandSource> ctx) {
		if (ModConfig.get().waypoints.isEmpty()) {
			ctx.getSource().sendFeedback(Component.literal("Nessun waypoint. Usa /wp add <nome>"));
			return 1;
		}
		Player p = ctx.getSource().getPlayer();
		String dim = dimName(ctx.getSource().getWorld().dimension());
		for (Waypoint w : ModConfig.get().waypoints) {
			String extra = w.dimension.equals(dim)
					? String.format(Locale.ROOT, " - %.0f blocchi", distance(p, w))
					: " - altra dimensione (" + w.dimension + ")";
			ctx.getSource().sendFeedback(Component.literal(
					w.name + ": " + w.x + " " + w.y + " " + w.z + extra));
		}
		return 1;
	}

	private static int dir(CommandContext<FabricClientCommandSource> ctx) {
		String name = StringArgumentType.getString(ctx, "nome");
		Waypoint w = find(name);
		if (w == null) {
			ctx.getSource().sendFeedback(Component.literal("Waypoint '" + name + "' non trovato."));
			return 0;
		}
		String dim = dimName(ctx.getSource().getWorld().dimension());
		if (!w.dimension.equals(dim)) {
			ctx.getSource().sendFeedback(Component.literal("'" + name + "' e' in un'altra dimensione (" + w.dimension + ")."));
			return 0;
		}
		ctx.getSource().sendFeedback(Component.literal(describe(ctx.getSource().getPlayer(), w)));
		return 1;
	}

	private static int track(CommandContext<FabricClientCommandSource> ctx) {
		String name = StringArgumentType.getString(ctx, "nome");
		Waypoint w = find(name);
		if (w == null) {
			ctx.getSource().sendFeedback(Component.literal("Waypoint '" + name + "' non trovato."));
			return 0;
		}
		ModConfig.get().tracked = w.name;
		ModConfig.save();
		ctx.getSource().sendFeedback(Component.literal("Sto tracciando '" + w.name + "'. Usa /wp stop per fermarti."));
		return 1;
	}

	/** Chiamato a ogni tick: aggiorna la scritta sopra la hotbar ogni 5 tick. */
	public static void tickTracker(Minecraft client, int ticks) {
		if (ticks % 5 != 0 || client.player == null || client.level == null) {
			return;
		}
		String tracked = ModConfig.get().tracked;
		if (tracked.isEmpty()) {
			return;
		}
		Waypoint w = find(tracked);
		if (w == null || !w.dimension.equals(dimName(client.level.dimension()))) {
			return;
		}
		client.player.displayClientMessage(Component.literal(describe(client.player, w)), true);
	}

	private static double distance(Player p, Waypoint w) {
		double dx = w.x + 0.5 - p.getX();
		double dy = w.y - p.getY();
		double dz = w.z + 0.5 - p.getZ();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static String describe(Player p, Waypoint w) {
		double dx = w.x + 0.5 - p.getX();
		double dz = w.z + 0.5 - p.getZ();

		// Direzione assoluta (N = -Z, E = +X)
		double bearing = Math.toDegrees(Math.atan2(dx, -dz));
		if (bearing < 0) {
			bearing += 360;
		}
		String[] points = { "N", "NE", "E", "SE", "S", "SO", "O", "NO" };
		String compass = points[(int) Math.round(bearing / 45.0) % 8];

		// Direzione relativa a dove guardi (freccia)
		double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
		double diff = targetYaw - p.getYRot();
		diff = ((diff % 360) + 540) % 360 - 180;
		String arrow;
		double a = Math.abs(diff);
		if (a <= 22.5) {
			arrow = "\u2191";
		} else if (a <= 67.5) {
			arrow = diff > 0 ? "\u2197" : "\u2196";
		} else if (a <= 112.5) {
			arrow = diff > 0 ? "\u2192" : "\u2190";
		} else if (a <= 157.5) {
			arrow = diff > 0 ? "\u2198" : "\u2199";
		} else {
			arrow = "\u2193";
		}

		return String.format(Locale.ROOT, "%s %s  %s  %.0f blocchi  (y %d)",
				arrow, w.name, compass, distance(p, w), w.y);
	}
}
