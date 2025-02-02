package me.benozgr.Rarities;

public class Rarity {

    private final String name;
    private final String lore;
    private final int chance;

    public Rarity(String name, String lore, int chance) {
        this.name = name;
        this.lore = lore;
        this.chance = chance;
    }

    public String getName() {
        return name;
    }

    public String getLore() {
        return lore;
    }

    public int getChance() {
        return chance;
    }
}
