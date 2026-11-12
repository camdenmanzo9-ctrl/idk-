package dev.profit.dashboard;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * On/off switches for the Balance HUD mod's panels (balance box, profit tracker, rakeback).
 * Saved in config/snowy-panels.txt, which the Balance HUD mod checks twice a second.
 */
public final class Panels {
    private Panels() {}

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("snowy-panels.txt");
    public static boolean balance = true, profit = true, rakeback = true;

    public static void load() {
        try {
            if (!Files.exists(FILE)) return;
            for (String line : Files.readAllLines(FILE)) {
                String[] p = line.split("=");
                if (p.length < 2) continue;
                boolean on = p[1].trim().equalsIgnoreCase("on");
                switch (p[0].trim().toLowerCase()) {
                    case "balance" -> balance = on;
                    case "profit" -> profit = on;
                    case "rakeback" -> rakeback = on;
                    default -> { }
                }
            }
        } catch (Exception ignored) { }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, "balance=" + (balance ? "on" : "off") + "\n"
                    + "profit=" + (profit ? "on" : "off") + "\n"
                    + "rakeback=" + (rakeback ? "on" : "off") + "\n");
        } catch (Exception ignored) { }
    }
}
