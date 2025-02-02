package me.benozgr.Rarities;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class Rarities extends JavaPlugin {

    private static Rarities instance;
    private Map<String, Rarity> rarityMap;

    @Override
    public void onEnable() {
        instance = this;

        // Register events
        getServer().getPluginManager().registerEvents(new CraftListener(this), this);

        // Log a message when the plugin is enabled
        getLogger().info("Rarities etkin!");

        // Load the config and initialize rarity map
        saveDefaultConfig();
        loadRarities();
    }

    @Override
    public void onDisable() {
        // Log a message when the plugin is disabled
        getLogger().info("Rarities devredışı!");
    }

    public static Rarities getInstance() {
        return instance;
    }

    private void loadRarities() {
        rarityMap = new HashMap<>();
        FileConfiguration config = getConfig();

        // Iterate through config to load rarities
        for (String itemType : config.getConfigurationSection("items").getKeys(false)) {
            for (String rarityName : config.getConfigurationSection("items." + itemType).getKeys(false)) {
                String rarityKey = itemType + ":" + rarityName;
                String lore = config.getString("items." + itemType + "." + rarityName + ".lore", "No lore");
                int chance = config.getInt("items." + itemType + "." + rarityName + ".chance", 100);

                Rarity rarity = new Rarity(rarityName, lore, chance);
                rarityMap.put(rarityKey, rarity);
            }
        }
    }

    public Map<String, Rarity> getRarityMap() {
        return rarityMap;
    }
}
