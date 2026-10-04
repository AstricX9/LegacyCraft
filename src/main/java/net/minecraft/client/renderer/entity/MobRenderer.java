package net.minecraft.client.renderer.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.GuiTextures;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.LightMap;
import net.minecraft.client.renderer.PlayerModel;
import net.minecraft.client.renderer.StandardLighting;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityTNTPrimed;
import net.minecraft.entity.EntityXPOrb;
import net.minecraft.entity.MovingEntity;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.MobSpawner;
import net.minecraft.world.World;

import static org.lwjgl.opengl.GL11.*;

/**
 * Draws mobs, lit TNT and experience orbs (1.8.9 RendererLivingEntity,
 * RenderCreeper/ModelCreeper, RenderTNTPrimed and RenderXPOrb, HYPOTHESIS:
 * recalled), plus the round shadow under entities (Render.renderShadow,
 * Fancy graphics with Entity Shadows on). The caller has the modelview in
 * world coordinates.
 */
public final class MobRenderer {
    public static final ResourceLocation CREEPER = new ResourceLocation("minecraft:textures/entity/creeper/creeper.png");
    public static final ResourceLocation XP_ORB = new ResourceLocation("minecraft:textures/entity/experience_orb.png");
    public static final ResourceLocation SHADOW = new ResourceLocation("minecraft:textures/misc/shadow.png");
    public static final ResourceLocation PIG = new ResourceLocation("minecraft:textures/entity/pig/pig.png");
    public static final ResourceLocation COW = new ResourceLocation("minecraft:textures/entity/cow/cow.png");
    public static final ResourceLocation SHEEP = new ResourceLocation("minecraft:textures/entity/sheep/sheep.png");
    public static final ResourceLocation SHEEP_FUR = new ResourceLocation("minecraft:textures/entity/sheep/sheep_fur.png");
    public static final ResourceLocation CHICKEN = new ResourceLocation("minecraft:textures/entity/chicken.png");
    public static final ResourceLocation PIGMAN = new ResourceLocation("minecraft:textures/entity/zombie_pigman.png");
    public static final ResourceLocation SKELETON = new ResourceLocation("minecraft:textures/entity/skeleton/skeleton.png");
    public static final ResourceLocation WITHER_SKELETON = new ResourceLocation("minecraft:textures/entity/skeleton/wither_skeleton.png");
    public static final ResourceLocation BLAZE = new ResourceLocation("minecraft:textures/entity/blaze.png");
    public static final ResourceLocation GHAST = new ResourceLocation("minecraft:textures/entity/ghast/ghast.png");
    public static final ResourceLocation GHAST_SHOOTING = new ResourceLocation("minecraft:textures/entity/ghast/ghast_shooting.png");
    public static final ResourceLocation MAGMA_CUBE = new ResourceLocation("minecraft:textures/entity/slime/magmacube.png");
    public static final ResourceLocation ARROW = new ResourceLocation("minecraft:textures/entity/arrow.png");
    public static final ResourceLocation WITHER = new ResourceLocation("minecraft:textures/entity/wither/wither.png");
    public static final ResourceLocation WITHER_INVULNERABLE = new ResourceLocation("minecraft:textures/entity/wither/wither_invulnerable.png");
    public static final ResourceLocation WITHER_ARMOR = new ResourceLocation("minecraft:textures/entity/wither/wither_armor.png");

    /** BossStatus.setBossStatus: the boss drawn this frame (its health share and name key), read by the HUD. */
    public float bossHealthScale;
    public String bossName;
    public boolean bossRendered;

    private final GuiTextures textures;
    private final ItemRenderer items;
    private World world;
    private long worldTime;
    private double cameraX, cameraY, cameraZ;
    private float cameraYaw, cameraPitch;

    public MobRenderer(GuiTextures textures, ItemRenderer items) {
        this.textures = textures;
        this.items = items;
    }

    /** The camera (for shadow fading and turning orbs to face it) and the world, set each frame. */
    public void setView(World world, long worldTime, double x, double y, double z, float yaw, float pitch) {
        this.world = world;
        this.worldTime = worldTime;
        cameraX = x; cameraY = y; cameraZ = z;
        cameraYaw = yaw; cameraPitch = pitch;
    }

    // ---- living ------------------------------------------------------------------------------

    /** The creeper model's parts (ModelCreeper, 64x32): head, body and four legs on 6-pixel-high pivots. */
    private static final float[][] CREEPER_LEGS = {{-2, 18, 4}, {2, 18, 4}, {-2, 18, -4}, {2, 18, -4}};

    public void renderCreeper(EntityCreeper creeper, float partialTicks) {
        double x = creeper.renderX(partialTicks), y = creeper.renderY(partialTicks), z = creeper.renderZ(partialTicks);
        float body = interpolateRotation(creeper.prevRenderYawOffset, creeper.renderYawOffset, partialTicks);
        float head = interpolateRotation(creeper.prevRotationYawHead, creeper.rotationYawHead, partialTicks);
        float headYaw = head - body;
        float pitch = creeper.prevRotationPitch + (creeper.rotationPitch - creeper.prevRotationPitch) * partialTicks;
        float amount = Math.min(1f, creeper.prevLimbSwingAmount + (creeper.limbSwingAmount - creeper.prevLimbSwingAmount) * partialTicks);
        float swing = creeper.limbSwing - creeper.limbSwingAmount * (1f - partialTicks);
        float[] light = light(creeper, x, y, z);
        StandardLighting.enable();
        glPushMatrix();
        glTranslated(x, y, z);
        rotateCorpse(creeper, body, partialTicks);
        glEnable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        glScalef(-1f, -1f, 1f);
        // RenderCreeper.preRenderCallback: swelling up (and wobbling) as the fuse burns.
        float flash = creeper.flashIntensity(partialTicks);
        float wobble = 1f + (float) Math.sin(flash * 100f) * flash * 0.01f;
        float f = Math.max(0f, Math.min(1f, flash));
        f *= f;
        f *= f;
        float wide = (1f + f * 0.4f) * wobble, tall = (1f + f * 0.1f) / wobble;
        glScalef(wide, tall, wide);
        glTranslatef(0f, -1.5078125f, 0f);
        glDisable(GL_CULL_FACE);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.1f);
        textures.bind(CREEPER);
        glColor4f(light[0], light[1], light[2], 1f);
        drawCreeper(swing, amount, headYaw, pitch);
        // RendererLivingEntity.setBrightness: hurt mobs are tinted 30% red; a creeper about to go off flashes white.
        float overlay = 0f, red = 1f, green = 1f, blue = 1f;
        if (creeper.hurtTime > 0 || creeper.deathTime > 0) {
            overlay = 0.3f;
            green = blue = 0f;
        } else if ((int) (flash * 10f) % 2 != 0) {
            overlay = Math.max(0, Math.min(255, (int) (flash * 0.2f * 255f))) / 255f;
        }
        if (overlay > 0f) {
            glDisable(GL_TEXTURE_2D);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glDepthFunc(GL_EQUAL);
            glColor4f(red, green, blue, overlay);
            drawCreeper(swing, amount, headYaw, pitch);
            glDepthFunc(GL_LEQUAL);
            glEnable(GL_TEXTURE_2D);
        }
        glDisable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        glPopMatrix();
        StandardLighting.disable();
        glColor4f(1, 1, 1, 1);
        if (creeper.isBurning()) fireRequests.add(new double[] {x, y, z, creeper.width, creeper.height});
    }

    // ---- animals ------------------------------------------------------------------------------

    /**
     * RenderPig, RenderCow, RenderSheep (with LayerSheepWool) and
     * RenderChicken through RendererLivingEntity: the model turned to the
     * body's yaw (tipping over when dead), lit where its eyes are, then a
     * 30% red pass over everything while hurt or dying. The fleece is the
     * fur texture tinted by the wool colour.
     */
    public void renderAnimal(net.minecraft.entity.passive.EntityAnimal animal, float partialTicks) {
        double x = animal.renderX(partialTicks), y = animal.renderY(partialTicks), z = animal.renderZ(partialTicks);
        float body = interpolateRotation(animal.prevRenderYawOffset, animal.renderYawOffset, partialTicks);
        float head = interpolateRotation(animal.prevRotationYawHead, animal.rotationYawHead, partialTicks);
        float pitch = animal.prevRotationPitch + (animal.rotationPitch - animal.prevRotationPitch) * partialTicks;
        float amount = Math.min(1f, animal.prevLimbSwingAmount + (animal.limbSwingAmount - animal.prevLimbSwingAmount) * partialTicks);
        float swing = animal.limbSwing - animal.limbSwingAmount * (1f - partialTicks);
        if (animal.isChild()) swing *= 3f;
        AnimalModels.Pose pose = new AnimalModels.Pose(swing, amount, head - body, pitch);
        boolean child = animal.isChild();
        float[] light = light(animal, x, y, z);
        StandardLighting.enable();
        glPushMatrix();
        glTranslated(x, y, z);
        rotateCorpse(animal, body, partialTicks);
        glEnable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        glScalef(-1f, -1f, 1f);
        glTranslatef(0f, -1.5078125f, 0f);
        glDisable(GL_CULL_FACE);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.1f);
        Runnable model;
        Runnable fur = null;
        ResourceLocation texture;
        if (animal instanceof net.minecraft.entity.passive.EntityPig) {
            texture = PIG;
            model = () -> AnimalModels.pig(pose, child);
        } else if (animal instanceof net.minecraft.entity.passive.EntityCow) {
            texture = COW;
            model = () -> AnimalModels.cow(pose, child);
        } else if (animal instanceof net.minecraft.entity.passive.EntitySheep sheep) {
            texture = SHEEP;
            float drop = sheep.headRotationPointY(partialTicks);
            float angle = sheep.headRotationAngleX(partialTicks, (float) Math.toRadians(pitch));
            model = () -> AnimalModels.sheep(pose, child, false, drop, angle);
            if (!sheep.sheared) fur = () -> AnimalModels.sheep(pose, child, true, drop, angle);
        } else if (animal instanceof net.minecraft.entity.passive.EntityChicken chicken) {
            texture = CHICKEN;
            // RenderChicken.handleRotationFloat: the wing flap.
            float wing = chicken.prevWingRotation + (chicken.wingRotation - chicken.prevWingRotation) * partialTicks;
            float dest = chicken.prevDestPos + (chicken.destPos - chicken.prevDestPos) * partialTicks;
            float wings = ((float) Math.sin(wing) + 1f) * dest;
            model = () -> AnimalModels.chicken(pose, child, wings);
        } else {
            glPopMatrix();
            StandardLighting.disable();
            return;
        }
        textures.bind(texture);
        glColor4f(light[0], light[1], light[2], 1f);
        model.run();
        if (fur != null) {
            float[] tint = net.minecraft.entity.passive.EntitySheep.FLEECE[((net.minecraft.entity.passive.EntitySheep) animal).fleeceColor];
            textures.bind(SHEEP_FUR);
            glColor4f(light[0] * tint[0], light[1] * tint[1], light[2] * tint[2], 1f);
            fur.run();
        }
        if (animal.hurtTime > 0 || animal.deathTime > 0) {
            glDisable(GL_TEXTURE_2D);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glDepthFunc(GL_EQUAL);
            glColor4f(1f, 0f, 0f, 0.3f);
            model.run();
            if (fur != null) fur.run();
            glDepthFunc(GL_LEQUAL);
            glEnable(GL_TEXTURE_2D);
        }
        glDisable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        glPopMatrix();
        StandardLighting.disable();
        glColor4f(1, 1, 1, 1);
        if (animal.isBurning()) fireRequests.add(new double[] {x, y, z, animal.width, animal.height});
    }

    // ---- monsters -----------------------------------------------------------------------------

    /**
     * RenderPigZombie, RenderSkeleton (wither skeletons 1.2x), RenderBlaze,
     * RenderGhast (4.5x, the shooting face while charging) and
     * RenderMagmaCube (squashed and stretched by its squish) through
     * RendererLivingEntity, with the held item in the right hand
     * (LayerHeldItem). Blazes and magma cubes are drawn fully lit.
     */
    public void renderMob(net.minecraft.entity.EntityLiving mob, float partialTicks) {
        double x = mob.renderX(partialTicks), y = mob.renderY(partialTicks), z = mob.renderZ(partialTicks);
        float body = interpolateRotation(mob.prevRenderYawOffset, mob.renderYawOffset, partialTicks);
        float head = interpolateRotation(mob.prevRotationYawHead, mob.rotationYawHead, partialTicks);
        float pitch = mob.prevRotationPitch + (mob.rotationPitch - mob.prevRotationPitch) * partialTicks;
        float amount = Math.min(1f, mob.prevLimbSwingAmount + (mob.limbSwingAmount - mob.prevLimbSwingAmount) * partialTicks);
        float swing = mob.limbSwing - mob.limbSwingAmount * (1f - partialTicks);
        if (mob.isChild()) swing *= 3f;
        float age = mob.ticksExisted + partialTicks;
        AnimalModels.Pose pose = new AnimalModels.Pose(swing, amount, head - body, pitch);
        float[] light = mob.fullBright() ? LightMap.color(15, 15) : light(mob, x, y, z);
        StandardLighting.enable();
        glPushMatrix();
        glTranslated(x, y, z);
        rotateCorpse(mob, body, partialTicks);
        glEnable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        glScalef(-1f, -1f, 1f);
        ResourceLocation texture;
        java.util.function.Supplier<MobModels.Arm> model;
        if (mob instanceof net.minecraft.entity.monster.EntityPigZombie pigman) {
            texture = PIGMAN;
            model = () -> MobModels.biped(pose, age, 0f, false, true, pigman.isChild());
        } else if (mob instanceof net.minecraft.entity.monster.EntitySkeleton skeleton) {
            texture = skeleton.skeletonType == 1 ? WITHER_SKELETON : SKELETON;
            if (skeleton.skeletonType == 1) glScalef(1.2f, 1.2f, 1.2f);
            model = () -> MobModels.biped(pose, age, 0f, true, true, false);
        } else if (mob instanceof net.minecraft.entity.monster.EntityBlaze) {
            texture = BLAZE;
            model = () -> {
                MobModels.blaze(age, head - body, pitch);
                return null;
            };
        } else if (mob instanceof net.minecraft.entity.monster.EntityGhast ghast) {
            texture = ghast.attacking ? GHAST_SHOOTING : GHAST;
            glScalef(4.5f, 4.5f, 4.5f);
            model = () -> {
                MobModels.ghast(age);
                return null;
            };
        } else if (mob instanceof net.minecraft.entity.monster.EntityMagmaCube cube) {
            texture = MAGMA_CUBE;
            int size = cube.size();
            float squish = cube.prevSquishFactor + (cube.squishFactor - cube.prevSquishFactor) * partialTicks;
            float f = squish / (size * 0.5f + 1f), f1 = 1f / (f + 1f);
            glScalef(f1 * size, 1f / f1 * size, f1 * size);
            model = () -> {
                MobModels.magmaCube(squish);
                return null;
            };
        } else {
            glPopMatrix();
            StandardLighting.disable();
            return;
        }
        glTranslatef(0f, -1.5078125f, 0f);
        glDisable(GL_CULL_FACE);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.1f);
        textures.bind(texture);
        glColor4f(light[0], light[1], light[2], 1f);
        MobModels.Arm arm = model.get();
        if (mob.hurtTime > 0 || mob.deathTime > 0) {
            glDisable(GL_TEXTURE_2D);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glDepthFunc(GL_EQUAL);
            glColor4f(1f, 0f, 0f, 0.3f);
            model.get();
            glDepthFunc(GL_LEQUAL);
            glEnable(GL_TEXTURE_2D);
        }
        if (arm != null && mob.heldItem != null && items != null) {
            glPushMatrix();
            if (mob.isChild()) {
                glTranslatef(0f, 0.625f, 0f);
                glRotatef(-20f, -1f, 0f, 0f);
                glScalef(0.5f, 0.5f, 0.5f);
            }
            arm.transform();
            glTranslatef(-0.0625f, 0.4375f, 0.0625f);
            glColor4f(1, 1, 1, 1);
            items.renderHeld(mob.heldItem, net.minecraft.client.renderer.ItemRenderer.Held.THIRD_PERSON, light[0], light[1], light[2]);
            glPopMatrix();
        }
        glDisable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        glPopMatrix();
        StandardLighting.disable();
        glColor4f(1, 1, 1, 1);
        if (mob.isBurning()) fireRequests.add(new double[] {x, y, z, mob.width, mob.height});
    }

    /**
     * RenderWither: ModelWither twice as big (shrunk by up to a quarter
     * while it swells, on the flashing blue texture), always lit, and below
     * half health LayerWitherAura: the model grown half a pixel on the
     * scrolling armour texture, added on top at half brightness. Sets the
     * boss bar.
     */
    public void renderWither(net.minecraft.entity.boss.EntityWither wither, float partialTicks) {
        double x = wither.renderX(partialTicks), y = wither.renderY(partialTicks), z = wither.renderZ(partialTicks);
        float body = interpolateRotation(wither.prevRenderYawOffset, wither.renderYawOffset, partialTicks);
        float head = interpolateRotation(wither.prevRotationYawHead, wither.rotationYawHead, partialTicks);
        float pitch = wither.prevRotationPitch + (wither.rotationPitch - wither.prevRotationPitch) * partialTicks;
        float age = wither.ticksExisted + partialTicks;
        float[] sideYaw = {wither.headYaw[0] - wither.renderYawOffset, wither.headYaw[1] - wither.renderYawOffset};
        float[] sidePitch = {wither.headPitch[0], wither.headPitch[1]};
        bossHealthScale = wither.health / wither.maxHealth();
        bossName = wither.displayName();
        bossRendered = true;
        float[] light = LightMap.color(15, 15);
        StandardLighting.enable();
        glPushMatrix();
        glTranslated(x, y, z);
        rotateCorpse(wither, body, partialTicks);
        glEnable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        glScalef(-1f, -1f, 1f);
        float scale = 2f;
        int invul = wither.invulTime;
        if (invul > 0) scale -= (invul - partialTicks) / 220f * 0.5f;
        glScalef(scale, scale, scale);
        glTranslatef(0f, -1.5078125f, 0f);
        glDisable(GL_CULL_FACE);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.1f);
        textures.bind(invul > 0 && (invul > 80 || invul / 5 % 2 != 1) ? WITHER_INVULNERABLE : WITHER);
        glColor4f(light[0], light[1], light[2], 1f);
        MobModels.wither(age, head - body, pitch, sideYaw, sidePitch, 0f);
        if (wither.isArmored()) {
            textures.bind(WITHER_ARMOR);
            glMatrixMode(GL_TEXTURE);
            glLoadIdentity();
            glTranslatef((float) Math.cos(age * 0.02f) * 3f, age * 0.01f, 0f);
            glMatrixMode(GL_MODELVIEW);
            glEnable(GL_BLEND);
            glBlendFunc(GL_ONE, GL_ONE);
            StandardLighting.disable();
            glColor4f(0.5f, 0.5f, 0.5f, 1f);
            MobModels.wither(age, head - body, pitch, sideYaw, sidePitch, 0.5f);
            glMatrixMode(GL_TEXTURE);
            glLoadIdentity();
            glMatrixMode(GL_MODELVIEW);
            StandardLighting.enable();
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glDisable(GL_BLEND);
        }
        if (wither.hurtTime > 0 || wither.deathTime > 0) {
            glDisable(GL_TEXTURE_2D);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glDepthFunc(GL_EQUAL);
            glColor4f(1f, 0f, 0f, 0.3f);
            MobModels.wither(age, head - body, pitch, sideYaw, sidePitch, 0f);
            glDepthFunc(GL_LEQUAL);
            glEnable(GL_TEXTURE_2D);
        }
        glDisable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        glPopMatrix();
        StandardLighting.disable();
        glColor4f(1, 1, 1, 1);
    }

    /** RenderWitherSkull: a head cut from the wither's sheet (the blue sheet for a dangerous skull) turned along its flight, fully lit. */
    public void renderWitherSkull(net.minecraft.entity.EntityWitherSkull skull, float partialTicks) {
        double x = skull.renderX(partialTicks), y = skull.renderY(partialTicks), z = skull.renderZ(partialTicks);
        float yaw = interpolateRotation(skull.prevRotationYaw, skull.rotationYaw, partialTicks);
        float pitch = skull.prevRotationPitch + (skull.rotationPitch - skull.prevRotationPitch) * partialTicks;
        glPushMatrix();
        glDisable(GL_CULL_FACE);
        glTranslated(x, y, z);
        glEnable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        glScalef(-1f, -1f, 1f);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.1f);
        textures.bind(skull.invulnerable ? WITHER_INVULNERABLE : WITHER);
        glColor4f(1f, 1f, 1f, 1f);
        // ModelSkeletonHead(): the head box at (0, 35) of the 64x64 wither sheet.
        AnimalModels.Part head = new AnimalModels.Part(0, 0, 0, new AnimalModels.Cube(-4, -8, -4, 8, 8, 8, 0, 0, 35, false));
        AnimalModels.textureWidth = 64f;
        AnimalModels.textureHeight = 64f;
        head.ry = (float) Math.toRadians(yaw);
        head.rx = (float) Math.toRadians(pitch);
        head.draw();
        AnimalModels.textureHeight = 32f;
        glDisable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        glPopMatrix();
    }

    /** A placed mob head (TileEntitySkullRenderer), lit by the light in its block. */
    public void renderSkullBlock(int bx, int by, int bz, int facing, net.minecraft.world.World.Skull skull) {
        float[] light = LightMap.color(world.getSkyLight(bx, by, bz), world.getBlockLight(bx, by, bz));
        StandardLighting.enable();
        SkullRenderer.render(textures, bx, by, bz, facing, skull.rotation() * 360f / 16f, skull.type(), light);
        StandardLighting.disable();
    }

    /** The shadow radius of a monster (Render.shadowSize; magma cubes 0.25 per size). */
    public static float shadowSize(net.minecraft.entity.EntityLiving mob) {
        if (mob instanceof net.minecraft.entity.monster.EntityMagmaCube cube) return 0.25f * cube.size();
        return mob.isChild() ? 0.25f : 0.5f;
    }

    /**
     * RenderArrow: a shaft of four crossed quads and a cross-shaped fletching,
     * turned along its flight and quivering after it sticks.
     */
    public void renderArrow(net.minecraft.entity.EntityArrow arrow, float partialTicks) {
        double x = arrow.renderX(partialTicks), y = arrow.renderY(partialTicks), z = arrow.renderZ(partialTicks);
        float yaw = arrow.prevRotationYaw + (arrow.rotationYaw - arrow.prevRotationYaw) * partialTicks;
        float pitch = arrow.prevRotationPitch + (arrow.rotationPitch - arrow.prevRotationPitch) * partialTicks;
        float[] light = light(arrow, x, y, z);
        glPushMatrix();
        glTranslated(x, y, z);
        glRotatef(yaw - 90f, 0f, 1f, 0f);
        glRotatef(pitch, 0f, 0f, 1f);
        textures.bind(ARROW);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.1f);
        glDisable(GL_CULL_FACE);
        glColor4f(light[0], light[1], light[2], 1f);
        float shake = arrow.arrowShake - partialTicks;
        if (shake > 0f) glRotatef(-(float) Math.sin(shake * 3f) * shake, 0f, 0f, 1f);
        glRotatef(45f, 1f, 0f, 0f);
        float s = 0.05625f;
        glScalef(s, s, s);
        glTranslatef(-4f, 0f, 0f);
        glBegin(GL_QUADS);
        glTexCoord2f(0f, 0.15625f); glVertex3f(-7f, -2f, -2f);
        glTexCoord2f(0.15625f, 0.15625f); glVertex3f(-7f, -2f, 2f);
        glTexCoord2f(0.15625f, 0.3125f); glVertex3f(-7f, 2f, 2f);
        glTexCoord2f(0f, 0.3125f); glVertex3f(-7f, 2f, -2f);
        glEnd();
        for (int i = 0; i < 4; i++) {
            glRotatef(90f, 1f, 0f, 0f);
            glBegin(GL_QUADS);
            glTexCoord2f(0f, 0f); glVertex3f(-8f, -2f, 0f);
            glTexCoord2f(0.5f, 0f); glVertex3f(8f, -2f, 0f);
            glTexCoord2f(0.5f, 0.15625f); glVertex3f(8f, 2f, 0f);
            glTexCoord2f(0f, 0.15625f); glVertex3f(-8f, 2f, 0f);
            glEnd();
        }
        glPopMatrix();
        glColor4f(1, 1, 1, 1);
    }

    /** RenderFireball: the fire charge sprite facing the camera, 2x for ghasts' and 0.5x for blazes', fully lit. */
    public void renderFireball(net.minecraft.entity.EntityFireball fireball, net.minecraft.item.ItemStack stack, float partialTicks) {
        if (stack == null) return;
        double x = fireball.renderX(partialTicks), y = fireball.renderY(partialTicks), z = fireball.renderZ(partialTicks);
        float[] light = LightMap.color(15, 15);
        glPushMatrix();
        glTranslated(x, y, z);
        glRotatef(180f - cameraYaw, 0f, 1f, 0f);
        glRotatef(-cameraPitch, 1f, 0f, 0f);
        float scale = fireball.renderScale() * 2f;
        glScalef(scale, scale, scale);
        items.renderItemInWorld(stack, light[0], light[1], light[2]);
        glPopMatrix();
        glColor4f(1, 1, 1, 1);
    }

    /** Burning entities the caller should draw flames on (it owns the fire sprites). */
    public final java.util.List<double[]> fireRequests = new java.util.ArrayList<>();

    private static void drawCreeper(float swing, float amount, float headYaw, float pitch) {
        glPushMatrix();
        glTranslatef(0f, 6f / 16f, 0f);
        glRotatef(headYaw, 0, 1, 0);
        glRotatef(pitch, 1, 0, 0);
        glBegin(GL_QUADS);
        PlayerModel.box(-4, -8, -4, 8, 8, 8, 0, 0, 0, 64f, 32f);
        glEnd();
        glPopMatrix();
        glPushMatrix();
        glTranslatef(0f, 6f / 16f, 0f);
        glBegin(GL_QUADS);
        PlayerModel.box(-4, 0, -2, 8, 12, 4, 0, 16, 16, 64f, 32f);
        glEnd();
        glPopMatrix();
        float[] angles = {
            (float) Math.cos(swing * 0.6662f) * 1.4f * amount,
            (float) Math.cos(swing * 0.6662f + Math.PI) * 1.4f * amount,
            (float) Math.cos(swing * 0.6662f + Math.PI) * 1.4f * amount,
            (float) Math.cos(swing * 0.6662f) * 1.4f * amount};
        for (int i = 0; i < 4; i++) {
            glPushMatrix();
            glTranslatef(CREEPER_LEGS[i][0] / 16f, CREEPER_LEGS[i][1] / 16f, CREEPER_LEGS[i][2] / 16f);
            glRotatef((float) Math.toDegrees(angles[i]), 1, 0, 0);
            glBegin(GL_QUADS);
            PlayerModel.box(-2, 0, -2, 4, 6, 4, 0, 0, 16, 64f, 32f);
            glEnd();
            glPopMatrix();
        }
    }

    /** RendererLivingEntity.rotateCorpse: turned to the body's yaw, tipping 90 degrees over 20 ticks of dying. */
    private static void rotateCorpse(EntityLiving entity, float body, float partialTicks) {
        glRotatef(180f - body, 0f, 1f, 0f);
        if (entity.deathTime > 0) {
            float f = (entity.deathTime + partialTicks - 1f) / 20f * 1.6f;
            f = (float) Math.sqrt(f);
            if (f > 1f) f = 1f;
            glRotatef(f * 90f, 0f, 0f, 1f);
        }
    }

    private static float interpolateRotation(float previous, float current, float partialTicks) {
        float change = current - previous;
        while (change < -180f) change += 360f;
        while (change >= 180f) change -= 360f;
        return previous + partialTicks * change;
    }

    /** The light where the entity's eyes are (Entity.getBrightnessForRender) through the lightmap. */
    private float[] light(MovingEntity entity, double x, double y, double z) {
        int[] l = entity.lightAt(x, y + entity.eyeHeight(), z);
        return LightMap.color(l[0], l[1]);
    }

    // ---- TNT ---------------------------------------------------------------------------------

    /**
     * RenderTNTPrimed: the TNT block, swelling by up to 30% over its last 10
     * ticks, shaded by the brightness where it is, flashing white every 5
     * ticks (fading as the fuse runs out).
     */
    public void renderTNT(EntityTNTPrimed tnt, BlockState block, float partialTicks) {
        double x = tnt.renderX(partialTicks), y = tnt.renderY(partialTicks), z = tnt.renderZ(partialTicks);
        float left = tnt.fuse - partialTicks + 1f;
        float scale = 1f;
        if (left < 10f) {
            float f = Math.max(0f, Math.min(1f, 1f - left / 10f));
            f *= f;
            f *= f;
            scale = 1f + f * 0.3f;
        }
        float flash = (1f - left / 100f) * 0.8f;
        float brightness = MobSpawner.brightness(world, worldTime, (int) Math.floor(x), (int) Math.floor(y + tnt.eyeHeight()), (int) Math.floor(z));
        glPushMatrix();
        glTranslated(x, y + 0.5, z);
        glScalef(scale, scale, scale);
        glTranslatef(-0.5f, -0.5f, -0.5f);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_CULL_FACE);
        items.renderBlockModel(block, brightness, brightness, brightness);
        if (tnt.fuse / 5 % 2 == 0) {
            glDisable(GL_TEXTURE_2D);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_DST_ALPHA);
            glColor4f(1f, 1f, 1f, flash);
            glPolygonOffset(-3f, -3f);
            glEnable(GL_POLYGON_OFFSET_FILL);
            cube();
            glPolygonOffset(0f, 0f);
            glDisable(GL_POLYGON_OFFSET_FILL);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glEnable(GL_TEXTURE_2D);
        }
        glColor4f(1, 1, 1, 1);
        glPopMatrix();
    }

    private static void cube() {
        glBegin(GL_QUADS);
        glVertex3f(0, 0, 0); glVertex3f(1, 0, 0); glVertex3f(1, 0, 1); glVertex3f(0, 0, 1);
        glVertex3f(0, 1, 0); glVertex3f(0, 1, 1); glVertex3f(1, 1, 1); glVertex3f(1, 1, 0);
        glVertex3f(0, 0, 0); glVertex3f(0, 1, 0); glVertex3f(1, 1, 0); glVertex3f(1, 0, 0);
        glVertex3f(0, 0, 1); glVertex3f(1, 0, 1); glVertex3f(1, 1, 1); glVertex3f(0, 1, 1);
        glVertex3f(0, 0, 0); glVertex3f(0, 0, 1); glVertex3f(0, 1, 1); glVertex3f(0, 1, 0);
        glVertex3f(1, 0, 0); glVertex3f(1, 1, 0); glVertex3f(1, 1, 1); glVertex3f(1, 0, 1);
        glEnd();
    }

    // ---- experience orbs ---------------------------------------------------------------------

    /**
     * RenderXPOrb: one of 11 sprites by value, facing the camera at 0.3
     * scale, pulsing between green and yellow, half transparent, lit a bit
     * brighter than where it is.
     */
    public void renderXPOrb(EntityXPOrb orb, float partialTicks) {
        double x = orb.renderX(partialTicks), y = orb.renderY(partialTicks), z = orb.renderZ(partialTicks);
        int index = orb.textureIndex();
        float u0 = (index % 4 * 16) / 64f, u1 = (index % 4 * 16 + 16) / 64f;
        float v0 = (index / 4 * 16) / 64f, v1 = (index / 4 * 16 + 16) / 64f;
        int[] l = orb.lightAt(x, y + orb.eyeHeight(), z);
        float[] light = LightMap.color(l[0], Math.min(15, l[1] + 7.5f));
        float time = (orb.xpColor + partialTicks) / 2f;
        float red = (float) ((Math.sin(time + 0.0) + 1.0) * 0.5);
        float blue = (float) ((Math.sin(time + 4.1887903) + 1.0) * 0.1);
        glPushMatrix();
        glTranslated(x, y, z);
        glRotatef(180f - cameraYaw, 0f, 1f, 0f);
        glRotatef(-cameraPitch, 1f, 0f, 0f);
        glScalef(0.3f, 0.3f, 0.3f);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_CULL_FACE);
        textures.bind(XP_ORB);
        glColor4f(red * light[0], light[1], blue * light[2], 128f / 255f);
        glBegin(GL_QUADS);
        glTexCoord2f(u0, v1); glVertex3f(-0.5f, -0.25f, 0f);
        glTexCoord2f(u1, v1); glVertex3f(0.5f, -0.25f, 0f);
        glTexCoord2f(u1, v0); glVertex3f(0.5f, 0.75f, 0f);
        glTexCoord2f(u0, v0); glVertex3f(-0.5f, 0.75f, 0f);
        glEnd();
        glPopMatrix();
        glColor4f(1, 1, 1, 1);
    }

    // ---- thrown items ------------------------------------------------------------------------

    /**
     * RenderSnowball (thrown potions): the item's flat sprite, a quarter
     * block wide, turned to face the camera, glinting if it would in hand.
     */
    public void renderThrownItem(net.minecraft.entity.MovingEntity entity, net.minecraft.item.ItemStack stack, float partialTicks) {
        if (stack == null) return;
        double x = entity.renderX(partialTicks), y = entity.renderY(partialTicks), z = entity.renderZ(partialTicks);
        int[] l = entity.lightAt(x, y + entity.eyeHeight(), z);
        float[] light = LightMap.color(l[0], l[1]);
        glPushMatrix();
        glTranslated(x, y, z);
        glRotatef(180f - cameraYaw, 0f, 1f, 0f);
        glRotatef(-cameraPitch, 1f, 0f, 0f);
        glScalef(0.5f, 0.5f, 0.5f);
        items.renderItemInWorld(stack, light[0], light[1], light[2]);
        glPopMatrix();
        glColor4f(1, 1, 1, 1);
    }

    // ---- shadows -----------------------------------------------------------------------------

    /**
     * Render.renderShadow: shadow.png laid on the tops of the full blocks
     * under the entity within {@code size} of it, fading with the height
     * above each block and the distance from the camera, darker in the light.
     */
    public void renderShadow(double x, double y, double z, float size, float opacity) {
        if (world == null) return;
        double dx = x - cameraX, dy = y - cameraY, dz = z - cameraZ;
        float alpha = (float) ((1.0 - (dx * dx + dy * dy + dz * dz) / 256.0) * opacity);
        if (alpha <= 0f) return;
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_TEXTURE_2D);
        glDisable(GL_CULL_FACE);
        textures.bind(SHADOW);
        glDepthMask(false);
        int minX = (int) Math.floor(x - size), maxX = (int) Math.floor(x + size);
        int minY = (int) Math.floor(y - size), maxY = (int) Math.floor(y);
        int minZ = (int) Math.floor(z - size), maxZ = (int) Math.floor(z + size);
        glBegin(GL_QUADS);
        for (int bx = minX; bx <= maxX; bx++)
            for (int by = minY; by <= maxY; by++)
                for (int bz = minZ; bz <= maxZ; bz++) {
                    if (by - 1 < 0 || by >= World.HEIGHT || world.generatedChunk(bx >> 4, bz >> 4) == null) continue;
                    Block below = world.get(bx, by - 1, bz);
                    if (below.isAir() || !below.isFullCube()) continue;
                    if (MobSpawner.lightFromNeighbors(world, worldTime, bx, by, bz) <= 3) continue;
                    double shade = (alpha - (y - by) / 2.0) * 0.5 * MobSpawner.brightness(world, worldTime, bx, by, bz);
                    if (shade < 0) continue;
                    if (shade > 1) shade = 1;
                    double x0 = bx, x1 = bx + 1, top = by + 0.015625, z0 = bz, z1 = bz + 1;
                    float u0 = (float) ((x - x0) / 2.0 / size + 0.5), u1 = (float) ((x - x1) / 2.0 / size + 0.5);
                    float v0 = (float) ((z - z0) / 2.0 / size + 0.5), v1 = (float) ((z - z1) / 2.0 / size + 0.5);
                    glColor4f(1f, 1f, 1f, (float) shade);
                    glTexCoord2f(u0, v0); glVertex3d(x0, top, z0);
                    glTexCoord2f(u0, v1); glVertex3d(x0, top, z1);
                    glTexCoord2f(u1, v1); glVertex3d(x1, top, z1);
                    glTexCoord2f(u1, v0); glVertex3d(x1, top, z0);
                }
        glEnd();
        glDepthMask(true);
        glColor4f(1, 1, 1, 1);
    }
}
