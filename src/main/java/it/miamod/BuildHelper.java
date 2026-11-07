package it.miamod;

import java.util.ArrayDeque;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Aiuto per costruire: seleziona due angoli e riempi, svuota, crea muri, sfere, cerchi.
 * Usa i comandi normali di Minecraft (/fill, /setblock), quindi servono i permessi
 * (mondo con cheat attivi o operatore). Non esiste "annulla": attenzione!
 */
public class BuildHelper {
	private static BlockPos pos1;
	private static BlockPos pos2;
	private static final ArrayDeque<String> QUEUE = new ArrayDeque<>();
	private static final int PER_TICK = 10;
	private static final long MAX_FILL = 32768;

	/** Invia pochi comandi per tick, cosi' il gioco non si blocca e il server non si arrabbia. */
	public static void tick(Minecraft mc) {
		if (mc.player == null) {
			QUEUE.clear();
			return;
		}
		for (int i = 0; i < PER_TICK && !QUEUE.isEmpty(); i++) {
			mc.player.connection.sendCommand(QUEUE.poll());
		}
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> d) {
		d.register(ClientCommands.literal("bh")
				.then(ClientCommands.literal("pos1").executes(ctx -> setPos(ctx, true)))
				.then(ClientCommands.literal("pos2").executes(ctx -> setPos(ctx, false)))
				.then(ClientCommands.literal("info").executes(BuildHelper::info))
				.then(ClientCommands.literal("stop").executes(ctx -> {
					QUEUE.clear();
					say(ctx, "Operazione fermata.");
					return 1;
				}))
				.then(ClientCommands.literal("fill")
						.then(ClientCommands.argument("blocco", StringArgumentType.greedyString())
								.executes(ctx -> region(ctx, "", false))))
				.then(ClientCommands.literal("hollow")
						.then(ClientCommands.argument("blocco", StringArgumentType.greedyString())
								.executes(ctx -> region(ctx, " hollow", true))))
				.then(ClientCommands.literal("walls")
						.then(ClientCommands.argument("blocco", StringArgumentType.greedyString())
								.executes(BuildHelper::walls)))
				.then(ClientCommands.literal("replace")
						.then(ClientCommands.argument("vecchio", StringArgumentType.word())
								.then(ClientCommands.argument("nuovo", StringArgumentType.greedyString())
										.executes(BuildHelper::replace))))
				.then(ClientCommands.literal("sfera")
						.then(ClientCommands.argument("raggio", IntegerArgumentType.integer(1, 15))
								.then(ClientCommands.argument("blocco", StringArgumentType.greedyString())
										.executes(ctx -> sphere(ctx, false)))))
				.then(ClientCommands.literal("sfera_vuota")
						.then(ClientCommands.argument("raggio", IntegerArgumentType.integer(2, 15))
								.then(ClientCommands.argument("blocco", StringArgumentType.greedyString())
										.executes(ctx -> sphere(ctx, true)))))
				.then(ClientCommands.literal("cerchio")
						.then(ClientCommands.argument("raggio", IntegerArgumentType.integer(1, 40))
								.then(ClientCommands.argument("blocco", StringArgumentType.greedyString())
										.executes(BuildHelper::circle)))));
	}

	private static void say(CommandContext<FabricClientCommandSource> ctx, String msg) {
		ctx.getSource().sendFeedback(Component.literal(msg));
	}

	private static String clean(String s) {
		String t = s.trim();
		if (t.isEmpty() || t.startsWith("/") || t.contains("\n")) {
			return null;
		}
		return t;
	}

	private static int setPos(CommandContext<FabricClientCommandSource> ctx, boolean first) {
		BlockPos p = ctx.getSource().getPlayer().blockPosition();
		if (first) {
			pos1 = p;
		} else {
			pos2 = p;
		}
		say(ctx, (first ? "Posizione 1" : "Posizione 2") + ": " + p.getX() + " " + p.getY() + " " + p.getZ());
		return 1;
	}

	private static long volume() {
		long dx = Math.abs(pos1.getX() - pos2.getX()) + 1L;
		long dy = Math.abs(pos1.getY() - pos2.getY()) + 1L;
		long dz = Math.abs(pos1.getZ() - pos2.getZ()) + 1L;
		return dx * dy * dz;
	}

	private static int info(CommandContext<FabricClientCommandSource> ctx) {
		if (pos1 == null || pos2 == null) {
			say(ctx, "Imposta prima /bh pos1 e /bh pos2.");
			return 0;
		}
		say(ctx, "Selezione: " + (Math.abs(pos1.getX() - pos2.getX()) + 1) + " x "
				+ (Math.abs(pos1.getY() - pos2.getY()) + 1) + " x "
				+ (Math.abs(pos1.getZ() - pos2.getZ()) + 1) + " = " + volume() + " blocchi");
		return 1;
	}

	private static boolean ready(CommandContext<FabricClientCommandSource> ctx) {
		if (pos1 == null || pos2 == null) {
			say(ctx, "Imposta prima /bh pos1 e /bh pos2.");
			return false;
		}
		return true;
	}

	private static int region(CommandContext<FabricClientCommandSource> ctx, String suffix, boolean singleCommand) {
		if (!ready(ctx)) {
			return 0;
		}
		String block = clean(StringArgumentType.getString(ctx, "blocco"));
		if (block == null) {
			say(ctx, "Nome blocco non valido.");
			return 0;
		}
		if (singleCommand && volume() > MAX_FILL) {
			say(ctx, "Per 'hollow' la selezione puo' avere al massimo " + MAX_FILL + " blocchi. Usa /bh walls.");
			return 0;
		}
		fillBox(block, pos1.getX(), pos1.getY(), pos1.getZ(), pos2.getX(), pos2.getY(), pos2.getZ(), suffix);
		say(ctx, "In corso... (/bh stop per fermare)");
		return 1;
	}

	private static int walls(CommandContext<FabricClientCommandSource> ctx) {
		if (!ready(ctx)) {
			return 0;
		}
		String block = clean(StringArgumentType.getString(ctx, "blocco"));
		if (block == null) {
			say(ctx, "Nome blocco non valido.");
			return 0;
		}
		int x1 = Math.min(pos1.getX(), pos2.getX());
		int x2 = Math.max(pos1.getX(), pos2.getX());
		int y1 = Math.min(pos1.getY(), pos2.getY());
		int y2 = Math.max(pos1.getY(), pos2.getY());
		int z1 = Math.min(pos1.getZ(), pos2.getZ());
		int z2 = Math.max(pos1.getZ(), pos2.getZ());
		fillBox(block, x1, y1, z1, x2, y2, z1, "");
		fillBox(block, x1, y1, z2, x2, y2, z2, "");
		fillBox(block, x1, y1, z1, x1, y2, z2, "");
		fillBox(block, x2, y1, z1, x2, y2, z2, "");
		say(ctx, "Muri in costruzione...");
		return 1;
	}

	private static int replace(CommandContext<FabricClientCommandSource> ctx) {
		if (!ready(ctx)) {
			return 0;
		}
		String oldB = clean(StringArgumentType.getString(ctx, "vecchio"));
		String newB = clean(StringArgumentType.getString(ctx, "nuovo"));
		if (oldB == null || newB == null) {
			say(ctx, "Nome blocco non valido.");
			return 0;
		}
		fillBox(newB, pos1.getX(), pos1.getY(), pos1.getZ(), pos2.getX(), pos2.getY(), pos2.getZ(), " replace " + oldB);
		say(ctx, "Sostituzione in corso...");
		return 1;
	}

	/** Divide a meta' la scatola finche' ogni /fill sta sotto il limite di 32768 blocchi. */
	private static void fillBox(String block, int ax, int ay, int az, int bx, int by, int bz, String suffix) {
		int x1 = Math.min(ax, bx), x2 = Math.max(ax, bx);
		int y1 = Math.min(ay, by), y2 = Math.max(ay, by);
		int z1 = Math.min(az, bz), z2 = Math.max(az, bz);
		long dx = x2 - x1 + 1L, dy = y2 - y1 + 1L, dz = z2 - z1 + 1L;
		if (dx * dy * dz <= MAX_FILL) {
			QUEUE.add("fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " " + block + suffix);
			return;
		}
		if (dx >= dy && dx >= dz) {
			int m = x1 + (int) (dx / 2) - 1;
			fillBox(block, x1, y1, z1, m, y2, z2, suffix);
			fillBox(block, m + 1, y1, z1, x2, y2, z2, suffix);
		} else if (dy >= dz) {
			int m = y1 + (int) (dy / 2) - 1;
			fillBox(block, x1, y1, z1, x2, m, z2, suffix);
			fillBox(block, x1, m + 1, z1, x2, y2, z2, suffix);
		} else {
			int m = z1 + (int) (dz / 2) - 1;
			fillBox(block, x1, y1, z1, x2, y2, m, suffix);
			fillBox(block, x1, y1, m + 1, x2, y2, z2, suffix);
		}
	}

	private static int sphere(CommandContext<FabricClientCommandSource> ctx, boolean hollow) {
		int r = IntegerArgumentType.getInteger(ctx, "raggio");
		String block = clean(StringArgumentType.getString(ctx, "blocco"));
		if (block == null) {
			say(ctx, "Nome blocco non valido.");
			return 0;
		}
		BlockPos c = ctx.getSource().getPlayer().blockPosition();
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				int rem = r * r - dx * dx - dy * dy;
				if (rem < 0) {
					continue;
				}
				int zmax = (int) Math.floor(Math.sqrt(rem));
				int inner = (r - 1) * (r - 1) - dx * dx - dy * dy;
				if (hollow && inner > 0) {
					int zmin = (int) Math.ceil(Math.sqrt(inner));
					if (zmin <= zmax) {
						segment(block, c, dx, dy, -zmax, -zmin);
						segment(block, c, dx, dy, zmin, zmax);
					} else {
						segment(block, c, dx, dy, -zmax, -zmax);
						segment(block, c, dx, dy, zmax, zmax);
					}
				} else {
					segment(block, c, dx, dy, -zmax, zmax);
				}
			}
		}
		say(ctx, (hollow ? "Sfera vuota" : "Sfera") + " in costruzione attorno a te...");
		return 1;
	}

	private static void segment(String block, BlockPos c, int dx, int dy, int z1, int z2) {
		QUEUE.add("fill " + (c.getX() + dx) + " " + (c.getY() + dy) + " " + (c.getZ() + z1) + " "
				+ (c.getX() + dx) + " " + (c.getY() + dy) + " " + (c.getZ() + z2) + " " + block);
	}

	private static int circle(CommandContext<FabricClientCommandSource> ctx) {
		int r = IntegerArgumentType.getInteger(ctx, "raggio");
		String block = clean(StringArgumentType.getString(ctx, "blocco"));
		if (block == null) {
			say(ctx, "Nome blocco non valido.");
			return 0;
		}
		BlockPos c = ctx.getSource().getPlayer().blockPosition();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double dist = Math.sqrt(dx * dx + dz * dz);
				if (Math.abs(dist - r) <= 0.5) {
					QUEUE.add("setblock " + (c.getX() + dx) + " " + c.getY() + " " + (c.getZ() + dz) + " " + block);
				}
			}
		}
		say(ctx, "Cerchio in costruzione al tuo livello...");
		return 1;
	}
}
