package net.minecraft.util;

/**
 * The hunger bar (1.8.9 FoodStats, HYPOTHESIS: recalled constants).
 * <ul>
 *   <li>Food starts at 20 with 5 saturation. Eating adds the food's points
 *       (up to 20) and its saturation (never above the food level).</li>
 *   <li>Exhaustion builds up from actions (capped at 40); every 4 points
 *       takes 1 saturation, or 1 food once saturation is gone (not in
 *       Peaceful).</li>
 *   <li>With food at 18 or more and the naturalRegeneration rule on, a hurt
 *       player heals 1 every 80 ticks, which costs 3 exhaustion.</li>
 *   <li>With food at 0, every 80 ticks starvation deals 1 damage while
 *       health is above 10 (Easy), above 1 (Normal) or always (Hard).</li>
 * </ul>
 */
public final class FoodStats {
    private int foodLevel = 20;
    private float saturationLevel = 5f;
    private float exhaustionLevel;
    private int foodTimer;
    private int prevFoodLevel = 20;

    /** What the bar acts on. */
    public interface Owner {
        int difficulty();
        boolean naturalRegeneration();
        /** Hurt but alive (EntityPlayer.shouldHeal). */
        boolean shouldHeal();
        float health();
        void heal(float amount);
        void starve(float amount);
    }

    /** Eating (FoodStats.addStats): {@code saturation} is the absolute amount (food x modifier x 2). */
    public void addStats(int food, float saturation) {
        foodLevel = Math.min(food + foodLevel, 20);
        saturationLevel = Math.min(saturationLevel + saturation, foodLevel);
    }

    /** One tick (FoodStats.onUpdate). */
    public void tick(Owner owner) {
        int difficulty = owner.difficulty();
        prevFoodLevel = foodLevel;
        if (exhaustionLevel > 4f) {
            exhaustionLevel -= 4f;
            if (saturationLevel > 0f) saturationLevel = Math.max(saturationLevel - 1f, 0f);
            else if (difficulty != 0) foodLevel = Math.max(foodLevel - 1, 0);
        }
        if (owner.naturalRegeneration() && foodLevel >= 18 && owner.shouldHeal()) {
            if (++foodTimer >= 80) {
                owner.heal(1f);
                addExhaustion(3f);
                foodTimer = 0;
            }
        } else if (foodLevel <= 0) {
            if (++foodTimer >= 80) {
                float health = owner.health();
                if (health > 10f || difficulty == 3 || health > 1f && difficulty == 2) owner.starve(1f);
                foodTimer = 0;
            }
        } else {
            foodTimer = 0;
        }
    }

    public void addExhaustion(float amount) { exhaustionLevel = Math.min(exhaustionLevel + amount, 40f); }

    public int getFoodLevel() { return foodLevel; }
    public int getPrevFoodLevel() { return prevFoodLevel; }
    public boolean needFood() { return foodLevel < 20; }
    public float getSaturationLevel() { return saturationLevel; }
    public float getExhaustionLevel() { return exhaustionLevel; }
    public int getFoodTimer() { return foodTimer; }

    public void setFoodLevel(int level) { foodLevel = level; }
    public void setSaturationLevel(float level) { saturationLevel = level; }

    /** Restores saved values (foodLevel, foodSaturationLevel, foodExhaustionLevel, foodTickTimer). */
    public void load(int level, float saturation, float exhaustion, int timer) {
        foodLevel = level;
        prevFoodLevel = level;
        saturationLevel = saturation;
        exhaustionLevel = exhaustion;
        foodTimer = timer;
    }

    /** Back to a fresh bar (a new life). */
    public void reset() { load(20, 5f, 0f, 0); }
}
