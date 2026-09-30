package net.minecraft.stats;

/**
 * One achievement (1.8.9 Achievement): its id ("mineWood", saved as
 * "achievement.mineWood"), where it sits on the achievement map (columns and
 * rows of 24 pixels), the item drawn as its icon, the achievement that must
 * come first (null for the independent first one), and whether it is a
 * "special" (challenge) one, drawn with a spiky frame and purple name.
 */
public record Achievement(String id, int column, int row, String icon, String parent, boolean special) {
    public String statName() { return "achievement." + id; }
    public String nameKey() { return "achievement." + id; }
    public String descriptionKey() { return "achievement." + id + ".desc"; }
}
