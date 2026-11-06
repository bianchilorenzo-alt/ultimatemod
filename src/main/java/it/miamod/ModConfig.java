package it.miamod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/** Impostazioni e waypoint, salvati in config/miamod.json. */
public class ModConfig {
	public boolean smoothSwing = true;
	/** 0.0 = animazione normale, 1.0 = massima fluidita'. */
	public float swingSmoothing = 1.0f;
	public String tracked = "";
	public List<Waypoint> waypoints = new ArrayList<>();

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ModConfig instance;

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("miamod.json");
	}

	public static ModConfig get() {
		if (instance == null) {
			load();
		}
		return instance;
	}

	public static void load() {
		try {
			if (Files.exists(file())) {
				instance = GSON.fromJson(Files.readString(file()), ModConfig.class);
			}
		} catch (Exception e) {
			MiaMod.LOGGER.error("Impossibile leggere la configurazione", e);
		}
		if (instance == null) {
			instance = new ModConfig();
		}
		if (instance.waypoints == null) {
			instance.waypoints = new ArrayList<>();
		}
		if (instance.tracked == null) {
			instance.tracked = "";
		}
	}

	public static void save() {
		try {
			Files.writeString(file(), GSON.toJson(get()));
		} catch (Exception e) {
			MiaMod.LOGGER.error("Impossibile salvare la configurazione", e);
		}
	}
}
