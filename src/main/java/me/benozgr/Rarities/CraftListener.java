package me.benozgr.Rarities;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.*;

public class CraftListener implements Listener {
    private final Rarities plugin;
    private final Map<Player, List<PotionEffect>> playerEffects = new HashMap<>();
    private final Map<Player, ItemStack[]> pendingCrafts = new HashMap<>();

    public CraftListener(Rarities plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        // Get the crafted item and player
        ItemStack craftedItem = event.getInventory().getResult();
        if (craftedItem == null || craftedItem.getType() == Material.AIR) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        int amount = craftedItem.getAmount(); // Number of items crafted (e.g., 3 swords)

        // Check if the item is applicable for rarity
        if (!isRarityApplicable(craftedItem.getType())) return;

        // Store the ingredients in case the player closes the menu
        ItemStack[] ingredients = event.getInventory().getMatrix();
        pendingCrafts.put(player, ingredients);

        // Cancel the default crafting to handle it manually
        event.setCancelled(true);

        // Create a list to hold all crafted items with rarities
        List<ItemStack> craftedItems = new ArrayList<>();

        // Generate a unique item with random rarity for each crafted item
        for (int i = 0; i < amount; i++) {
            ItemStack item = new ItemStack(craftedItem.getType());
            Rarity rarity = getRarity();
            if (rarity != null) {
                setItemRarity(item, rarity);
                craftedItems.add(item);
            }
        }

        // Remove ingredients from the crafting grid
        removeIngredients(event.getInventory(), amount);

        // Give the crafted items to the player
        for (ItemStack item : craftedItems) {
            player.getInventory().addItem(item).values().forEach(leftover -> {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            });
        }

        // Remove the player from the pending crafts map since the craft was successful
        pendingCrafts.remove(player);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();

        // Check if the player has a pending craft
        if (pendingCrafts.containsKey(player)) {
            // Return the items to the player
            ItemStack[] ingredients = pendingCrafts.get(player);
            for (ItemStack item : ingredients) {
                if (item != null && item.getType() != Material.AIR) {
                    player.getInventory().addItem(item).values().forEach(leftover -> {
                        player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                    });
                }
            }

            // Remove the player from the pending crafts map
            pendingCrafts.remove(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Check if the player has a pending craft
        if (pendingCrafts.containsKey(player)) {
            // Return the items to the player
            ItemStack[] ingredients = pendingCrafts.get(player);
            for (ItemStack item : ingredients) {
                if (item != null && item.getType() != Material.AIR) {
                    player.getInventory().addItem(item).values().forEach(leftover -> {
                        player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                    });
                }
            }

            // Remove the player from the pending crafts map
            pendingCrafts.remove(player);
        }
    }

    // Helper method to remove ingredients from the crafting grid
    private void removeIngredients(CraftingInventory inventory, int amount) {
        ItemStack[] matrix = inventory.getMatrix();
        for (ItemStack item : matrix) {
            if (item != null && item.getType() != Material.AIR) {
                item.setAmount(item.getAmount() - amount);
            }
        }
        inventory.setMatrix(matrix); // Update the crafting grid
    }

    private boolean isRarityApplicable(Material material) {
        return material.name().endsWith("_SWORD") ||
                material.name().endsWith("_AXE") ||
                material.name().endsWith("_SHOVEL") ||
                material.name().endsWith("_PICKAXE");
    }

    private Rarity getRarity() {
        // Logic to determine rarity based on configured chances
        int totalChance = 0;
        for (Rarity rarity : plugin.getRarityMap().values()) {
            totalChance += rarity.getChance();
        }

        int random = (int) (Math.random() * totalChance);
        for (Rarity rarity : plugin.getRarityMap().values()) {
            if (random < rarity.getChance()) {
                return rarity;
            }
            random -= rarity.getChance();
        }
        return null; // Fallback, should not happen
    }

    private void setItemRarity(ItemStack item, Rarity rarity) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) meta = Bukkit.getItemFactory().getItemMeta(item.getType());

        String itemType = item.getType().toString();
        String name = plugin.getConfig().getString("items." + itemType + "." + rarity.getName() + ".name");
        List<String> lore = plugin.getConfig().getStringList("items." + itemType + "." + rarity.getName() + ".lore");

        // Set display name and lore
        if (name != null) meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        if (lore != null && !lore.isEmpty()) {
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
            meta.setLore(coloredLore);
        }

        // Add persistent rarity tag
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey rarityKey = new NamespacedKey(plugin, "rarity");
        container.set(rarityKey, PersistentDataType.STRING, rarity.getName());

        item.setItemMeta(meta);
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getNewSlot());

        // Remove effects tied to rare items
        clearEffectsFromRareItems(player);

        // Apply new effects if applicable
        if (item != null && item.hasItemMeta()) {
            Rarity rarity = getRarityFromItem(item);
            if (rarity != null) {
                applyEffects(player, item, rarity);
            }
        }
    }

    private Rarity getRarityFromItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer container = meta.getPersistentDataContainer();
            NamespacedKey rarityKey = new NamespacedKey(plugin, "rarity");

            if (container.has(rarityKey, PersistentDataType.STRING)) {
                String storedRarity = container.get(rarityKey, PersistentDataType.STRING);

                // Match the stored rarity name with the configured rarities
                for (Rarity rarity : plugin.getRarityMap().values()) {
                    if (storedRarity.equalsIgnoreCase(rarity.getName())) {
                        return rarity;
                    }
                }
            }
        }
        return null; // No valid rarity found
    }

    private void applyEffects(Player player, ItemStack item, Rarity rarity) {
        // Get the item type (e.g., DIAMOND_SWORD)
        String itemType = item.getType().toString();

        // Retrieve the effects for the item type and rarity
        List<PotionEffect> effects = getEffectsFromConfig(itemType, rarity);

        // Apply the effects to the player
        for (PotionEffect effect : effects) {
            player.addPotionEffect(effect);
        }

        // Store the applied effects in the playerEffects map (only for rare items)
        if (rarity.getName().equalsIgnoreCase("rare") || rarity.getName().equalsIgnoreCase("legendary")) {
            playerEffects.put(player, effects);
        }
    }

    private List<PotionEffect> getEffectsFromConfig(String itemType, Rarity rarity) {
        List<PotionEffect> effects = new ArrayList<>();

        // Fetch the effects for the item type and rarity from the config
        List<Map<?, ?>> effectConfigs = plugin.getConfig().getMapList("items." + itemType + "." + rarity.getName() + ".effects");

        if (effectConfigs != null) {
            for (Map<?, ?> effectConfig : effectConfigs) {
                // Extract the type and level from the effect config
                String type = (String) effectConfig.get("type");
                int level = (int) effectConfig.get("level");

                // Convert the string type to a PotionEffectType
                PotionEffectType potionEffectType = getPotionEffectType(type);
                if (potionEffectType != null) {
                    effects.add(new PotionEffect(potionEffectType, 999999, level - 1)); // Apply the level, adjusting by -1 for internal level system
                } else {
                    plugin.getLogger().warning("Invalid effect type: " + type);
                }
            }
        }

        return effects;
    }

    private PotionEffectType getPotionEffectType(String type) {
        switch (type.toUpperCase()) {
            case "SPEED":
                return PotionEffectType.SPEED;
            case "JUMP":
                return PotionEffectType.JUMP;
            case "INCREASE_DAMAGE":
                return PotionEffectType.INCREASE_DAMAGE;
            case "FAST_DIGGING":
                return PotionEffectType.FAST_DIGGING;
            case "DAMAGE_RESISTANCE":
                return PotionEffectType.DAMAGE_RESISTANCE;
            case "WATER_BREATHING":
                return PotionEffectType.WATER_BREATHING;
            case "HEAL":
                return PotionEffectType.HEAL;
            case "HARM":
                return PotionEffectType.HARM;
            case "REGENERATION":
                return PotionEffectType.REGENERATION;
            case "POISON":
                return PotionEffectType.POISON;
            case "WITHER":
                return PotionEffectType.WITHER;
            case "ABSORPTION":
                return PotionEffectType.ABSORPTION;
            case "BLINDNESS":
                return PotionEffectType.BLINDNESS;
            case "NIGHT_VISION":
                return PotionEffectType.NIGHT_VISION;
            case "INVISIBILITY":
                return PotionEffectType.INVISIBILITY;
            case "SLOW":
                return PotionEffectType.SLOW;
            case "SLOW_DIGGING":
                return PotionEffectType.SLOW_DIGGING;
            default:
                plugin.getLogger().warning("Unknown potion effect type: " + type);
                return null;
        }
    }

    // Clears effects applied by rare items
    private void clearEffectsFromRareItems(Player player) {
        List<PotionEffect> effectsToRemove = new ArrayList<>();

        // Check if we have any previously stored effects for this player
        List<PotionEffect> storedEffects = playerEffects.get(player);
        if (storedEffects != null) {
            // Remove effects linked to rare items (keeping non-rare effects)
            for (PotionEffect effect : storedEffects) {
                // Check if the effect is tied to a rare item
                if (isEffectFromRareItem(effect)) {
                    effectsToRemove.add(effect);
                }
            }
            // Remove rare effects from player
            for (PotionEffect effect : effectsToRemove) {
                player.removePotionEffect(effect.getType());
            }
        }
    }

    // Determine if the effect is linked to a rare item
    private boolean isEffectFromRareItem(PotionEffect effect) {
        // For simplicity, check if the effect exists in the map; this applies for all types of effects
        return effect != null;
    }
}
