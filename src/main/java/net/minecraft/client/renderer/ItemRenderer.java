package net.minecraft.client.renderer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextures;
import net.minecraft.client.renderer.block.model.BakedModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelManager;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

/**
 * Draws item stacks in the GUI the way 1.8.9's RenderItem does (master
 * context §20, §63):
 * <ul>
 *   <li>items whose model is {@code builtin/generated} are their layer
 *       textures drawn flat at 16x16 (layer 0 tinted for leather armour,
 *       potions, grass-coloured plants);</li>
 *   <li>block items are the block model their item model inherits from
 *       (the piston's inventory model, the fence's two posts), turned 210°
 *       about X and -135° about Y (plus the item model's own "gui" rotation,
 *       e.g. stairs and fences), 10 GUI pixels per block and shaded per face;</li>
 *   <li>the overlay is the stack count bottom-right and the durability bar.</li>
 * </ul>
 * Chests, banners and mob heads use {@code builtin/entity} models that need
 * tile-entity renderers (TileEntityItemStackRenderer), drawn by
 * {@link #builtin}. HYPOTHESIS until screenshot-compared.
 */
public final class ItemRenderer {
    /** Resolved item model: flat layers, or a block rendered in 3D with an extra GUI rotation. */
    private record ItemModel(List<ResourceLocation> layers, boolean block, boolean builtinEntity, float[] guiRotation,
                             float[] firstPerson, float[] thirdPerson, ResourceLocation blockModel) {}

    /** Where a held item is drawn, picking the item model's "display" transform. */
    public enum Held { FIRST_PERSON, THIRD_PERSON }

    private static final float[] NO_ROTATION = {0, 0, 0};
    private final IResourceManager resources;
    private final GuiTextures textures;
    private final TextureAtlas atlas;
    private final BlockModelManager blockModels;
    private final int grassColor, foliageColor;
    private final Map<Item, ItemModel> models = new HashMap<>();
    /** Models of item states that aren't items of their own (the drawn bow), by model location. */
    private final Map<ResourceLocation, ItemModel> stateModels = new HashMap<>();
    /**
     * The model a held item is drawn with right now instead of its own
     * (Item.getModel in 1.8.9: the bow's three pulling stages while drawn,
     * the cast fishing rod), or null; set by the game.
     */
    public java.util.function.Function<Item, ResourceLocation> modelState = item -> null;

    /** What draws {@code builtin/entity} items (TileEntityItemStackRenderer): in the unit block at the origin, coloured. */
    public interface Builtin {
        boolean handles(Item item);
        void draw(Item item, float red, float green, float blue);
    }

    /** Set by the game; mob heads so far. */
    public Builtin builtin;
    private final Map<Item, float[]> guiTransforms = new HashMap<>();

    private boolean drawsBuiltin(Item item, ItemModel model) { return model.builtinEntity() && builtin != null && builtin.handles(item); }

    /**
     * RenderItem.renderItemIntoGUI for a built-in model: the GUI's 3D frame
     * (40x, 210 degrees about X, -135 about Y), the model's "gui" display
     * transform, then the half-size model turned round and centred, under
     * the GUI item lighting.
     */
    private void renderBuiltinGui(Item item, int x, int y) {
        float[] t = guiTransforms.computeIfAbsent(item, this::guiTransform);
        glPushMatrix();
        glTranslatef(x, y, 100);
        glTranslatef(8, 8, 0);
        glScalef(1, 1, -1);
        glScalef(0.5f, 0.5f, 0.5f);
        glScalef(40, 40, 40);
        glRotatef(210, 1, 0, 0);
        glRotatef(-135, 0, 1, 0);
        glPushMatrix();
        glRotatef(-30f, 0f, 1f, 0f);
        glRotatef(165f, 1f, 0f, 0f);
        StandardLighting.enable();
        glPopMatrix();
        glTranslatef(t[3], t[4], t[5]);
        glRotatef(t[1], 0, 1, 0);
        glRotatef(t[0], 1, 0, 0);
        glRotatef(t[2], 0, 0, 1);
        glScalef(t[6], t[7], t[8]);
        glScalef(0.5f, 0.5f, 0.5f);
        glRotatef(180f, 0f, 1f, 0f);
        glTranslatef(-0.5f, -0.5f, -0.5f);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        builtin.draw(item, guiBrightness, guiBrightness, guiBrightness);
        StandardLighting.disable();
        glDisable(GL_DEPTH_TEST);
        glColor4f(1, 1, 1, 1);
        glPopMatrix();
    }

    /** The item model's full "gui" display transform (rotation, translation, scale), following parents. */
    private float[] guiTransform(Item item) {
        String location = item.model().toString();
        for (int depth = 0; depth < 8 && location != null; depth++) {
            ResourceLocation id = new ResourceLocation(location);
            if (id.path().startsWith("builtin/")) break;
            JsonObject json = read(new ResourceLocation(id.namespace(), "models/" + id.path() + ".json"));
            if (json == null) break;
            if (json.has("display") && json.getAsJsonObject("display").has("gui"))
                return displayTransform(json.getAsJsonObject("display").getAsJsonObject("gui"));
            JsonElement parent = json.get("parent");
            location = parent == null ? null : parent.getAsString().contains(":") ? parent.getAsString() : "minecraft:" + parent.getAsString();
        }
        return new float[] {0, 0, 0, 0, 0, 0, 1, 1, 1};
    }

    public ItemRenderer(IResourceManager resources, GuiTextures textures, TextureAtlas atlas, BlockModelManager blockModels,
                        BiomeColors colors) {
        this.resources = resources;
        this.textures = textures;
        this.atlas = atlas;
        this.blockModels = blockModels;
        // Item colours use the colour maps at temperature 0.5, rainfall 1.0 (ColorizerGrass/Foliage defaults).
        this.grassColor = colors.grassColor(0.5f, 1.0f);
        this.foliageColor = colors.foliageColor(0.5f, 1.0f);
    }

    /** Multiplies the colour of GUI item icons (the achievement map darkens locked icons); 1 normally. */
    public float guiBrightness = 1f;

    /** An item in a slot, with the enchantment glint over flat enchanted items and enchanted books. */
    public void renderItemAndEffectIntoGUI(ItemStack stack, int x, int y) {
        if (stack == null) return;
        Item item = stack.item();
        ItemModel model = models.computeIfAbsent(item, this::resolve);
        boolean glint = net.minecraft.enchantment.EnchantmentHelper.hasGlint(stack);
        BlockState state = item.placedState();
        if (drawsBuiltin(item, model)) {
            renderBuiltinGui(item, x, y);
            return;
        }
        if (renderBlock(blockShape(model, state), state, model.guiRotation(), x, y)) return;
        if (model.layers().isEmpty()) return;
        if (!glint) {
            renderFlat(item, model.layers(), x, y);
            return;
        }
        // The item writes its own depth where its pixels are, so the glint (depth EQUAL) lands only on them.
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
        glDepthFunc(GL_ALWAYS);
        renderFlat(item, model.layers(), x, y);
        glDepthFunc(GL_LEQUAL);
        Glint.item(textures, () -> {
            glBegin(GL_QUADS);
            glTexCoord2f(0, 0); glVertex3f(x, y, 100);
            glTexCoord2f(0, 1); glVertex3f(x, y + 16, 100);
            glTexCoord2f(1, 1); glVertex3f(x + 16, y + 16, 100);
            glTexCoord2f(1, 0); glVertex3f(x + 16, y, 100);
            glEnd();
        });
        glDisable(GL_DEPTH_TEST);
    }

    public void renderItemIntoGUI(Item item, int x, int y) {
        if (item == null) return;
        ItemModel model = models.computeIfAbsent(item, this::resolve);
        BlockState state = item.placedState();
        if (drawsBuiltin(item, model)) {
            renderBuiltinGui(item, x, y);
            return;
        }
        if (renderBlock(blockShape(model, state), state, model.guiRotation(), x, y)) return;
        if (!model.layers().isEmpty()) renderFlat(item, model.layers(), x, y);
    }

    /** Stack size (when not 1) and the durability bar, drawn over an item. */
    public void renderItemOverlays(FontRenderer font, ItemStack stack, int x, int y) { renderItemOverlays(font, stack, x, y, null); }

    /** As above, with {@code text} (when not null) shown in place of the stack size (renderItemOverlayIntoGUI). */
    public void renderItemOverlays(FontRenderer font, ItemStack stack, int x, int y, String text) {
        if (stack == null) return;
        glDisable(GL_DEPTH_TEST);
        if (stack.count() != 1 || text != null) {
            String count = text != null ? text : String.valueOf(stack.count());
            font.drawStringWithShadow(count, x + 19 - 2 - font.getStringWidth(count), y + 6 + 3, 0xFFFFFF);
        }
        Item item = stack.item();
        if (item.isDamageable() && stack.damage() > 0) {
            int length = Math.round(13.0f - stack.damage() * 13.0f / item.maxDamage());
            int health = Math.round(255.0f - stack.damage() * 255.0f / item.maxDamage());
            glDisable(GL_TEXTURE_2D);
            quad(x + 2, y + 13, 13, 2, 0, 0, 0);
            quad(x + 2, y + 13, 12, 1, (255 - health) / 4, 64, 0);
            quad(x + 2, y + 13, length, 1, 255 - health, health, 0);
            glEnable(GL_TEXTURE_2D);
        }
        glColor4f(1, 1, 1, 1);
    }

    private static void quad(int x, int y, int width, int height, int red, int green, int blue) {
        glColor4f(red / 255f, green / 255f, blue / 255f, 1);
        glBegin(GL_QUADS);
        glVertex2f(x, y); glVertex2f(x, y + height); glVertex2f(x + width, y + height); glVertex2f(x + width, y);
        glEnd();
    }

    private void renderFlat(Item item, List<ResourceLocation> layers, int x, int y) {
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.1f);
        for (int layer = 0; layer < layers.size(); layer++) {
            ResourceLocation texture = layers.get(layer);
            textures.bind(texture);
            int[] size = textures.size(texture);
            // Animated sprites (clock, compass) are vertical strips; show the first frame.
            float v1 = size[1] > size[0] ? (float) size[0] / size[1] : 1f;
            int tint = layerTint(item, layer);
            float b = guiBrightness;
            glColor4f((tint >> 16 & 255) / 255f * b, (tint >> 8 & 255) / 255f * b, (tint & 255) / 255f * b, 1);
            glBegin(GL_QUADS);
            glTexCoord2f(0, 0); glVertex3f(x, y, 100);
            glTexCoord2f(0, v1); glVertex3f(x, y + 16, 100);
            glTexCoord2f(1, v1); glVertex3f(x + 16, y + 16, 100);
            glTexCoord2f(1, 0); glVertex3f(x + 16, y, 100);
            glEnd();
        }
        glColor4f(1, 1, 1, 1);
    }

    /** Colour multiplier for a flat item's layer (1.8.9 getColorFromItemStack, HYPOTHESIS values). */
    private int layerTint(Item item, int layer) {
        // ItemMonsterPlacer.getColorFromItemStack: the egg's base colour, then its spots.
        if (item.kind() == Item.Kind.SPAWN_EGG) {
            net.minecraft.item.VanillaItems.Egg egg = net.minecraft.item.VanillaItems.egg(item.vanillaData());
            return egg == null ? 0xFFFFFF : layer == 0 ? egg.primary() : egg.secondary();
        }
        if (layer != 0) return 0xFFFFFF;
        String path = item.name().path();
        if (path.startsWith("leather_")) return 0xA06540;
        if (item.vanillaId().path().equals("potion")) return net.minecraft.item.Potions.color(item.vanillaData());
        if (path.equals("tall_grass") || path.equals("fern") || path.equals("double_grass") || path.equals("double_fern")) return grassColor;
        if (path.equals("vine")) return foliageColor;
        if (path.equals("waterlily")) return 0x208030;
        return 0xFFFFFF;
    }

    /** Block tint for a quad with a tint index (grass/leaves in their default inventory colours). */
    private int blockTint(BlockState state) {
        if (state == null) return 0xFFFFFF;
        String block = state.block().name().path();
        return switch (block) {
            case "grass", "tallgrass" -> grassColor;
            case "leaves" -> switch (state.metadata() & 3) { case 1 -> 0x619961; case 2 -> 0x80A755; default -> foliageColor; };
            case "leaves2", "vine" -> foliageColor;
            case "double_plant" -> grassColor;
            default -> state.block().tintColor();
        };
    }

    /**
     * The block model an item is drawn as: the block model its item model
     * inherits from (ModelBakery bakes item models from their own parents, so
     * a piston shows piston_inventory_normal and a fence fence_inventory, not
     * the placed block's state), else the placed block's model; null for
     * flat items.
     */
    private BakedModel blockShape(ItemModel model, BlockState state) {
        if (!model.block() && !model.builtinEntity()) return null;
        BakedModel shape = model.blockModel() != null ? blockModels.itemModel(model.blockModel()) : null;
        if (shape == null && state != null) shape = blockModels.getModel(state);
        return shape == null || shape.quads().isEmpty() ? null : shape;
    }

    private boolean renderBlock(BakedModel model, BlockState state, float[] guiRotation, int x, int y) {
        if (model == null) return false;
        glPushMatrix();
        glTranslatef(x, y, 100);
        glTranslatef(8, 8, 0);
        glScalef(1, 1, -1);
        glScalef(0.5f, 0.5f, 0.5f);
        glScalef(40, 40, 40);
        glRotatef(210, 1, 0, 0);
        glRotatef(-135, 0, 1, 0);
        glRotatef(guiRotation[1], 0, 1, 0);
        glRotatef(guiRotation[0], 1, 0, 0);
        glRotatef(guiRotation[2], 0, 0, 1);
        glScalef(0.5f, 0.5f, 0.5f);
        glTranslatef(-0.5f, -0.5f, -0.5f);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glDisable(GL_CULL_FACE);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.1f);
        atlas.bind();
        int tint = blockTint(state);
        glBegin(GL_QUADS);
        for (BakedQuad quad : model.quads()) {
            TextureAtlas.Tile tile = atlas.tile(quad.texture());
            if (tile == null) continue;
            Direction face = quad.face();
            float shade = !quad.shade() ? 1f : face == Direction.UP ? 1f : face == Direction.DOWN ? .5f
                : (face == Direction.NORTH || face == Direction.SOUTH) ? .8f : .6f;
            int color = quad.tintIndex() >= 0 ? tint : 0xFFFFFF;
            float lit = shade * guiBrightness;
            glColor4f((color >> 16 & 255) / 255f * lit, (color >> 8 & 255) / 255f * lit, (color & 255) / 255f * lit, 1);
            for (int i = 0; i < 4; i++) {
                glTexCoord2f(tile.u0() + (tile.u1() - tile.u0()) * quad.u(i), tile.v0() + (tile.v1() - tile.v0()) * quad.v(i));
                glVertex3f(quad.x(i), quad.y(i), quad.z(i));
            }
        }
        glEnd();
        glDisable(GL_DEPTH_TEST);
        glColor4f(1, 1, 1, 1);
        glPopMatrix();
        return true;
    }

    /**
     * Draws an item the way a dropped item entity shows it, centred on the
     * origin: a block item as a cube 0.25 blocks wide (shaded per face), any
     * other item as its flat texture 0.5 blocks wide, visible from both sides.
     * The colour multiplier carries the world light at the entity.
     */
    /** A dropped stack: {@link #renderItemInWorld(Item, float, float, float)}, with the glint when enchanted. */
    public void renderItemInWorld(ItemStack stack, float red, float green, float blue) {
        if (stack == null) return;
        renderItemInWorld(stack.item(), red, green, blue);
        ItemModel model = models.computeIfAbsent(stack.item(), this::resolve);
        if (!net.minecraft.enchantment.EnchantmentHelper.hasGlint(stack) || isBlockModel(stack.item()) || model.layers().isEmpty()) return;
        Glint.item(textures, () -> {
            glBegin(GL_QUADS);
            glTexCoord2f(0, 1); glVertex3f(-0.25f, -0.25f, 0);
            glTexCoord2f(1, 1); glVertex3f(0.25f, -0.25f, 0);
            glTexCoord2f(1, 0); glVertex3f(0.25f, 0.25f, 0);
            glTexCoord2f(0, 0); glVertex3f(-0.25f, 0.25f, 0);
            glEnd();
        });
    }

    public void renderItemInWorld(Item item, float red, float green, float blue) {
        if (item == null) return;
        ItemModel model = models.computeIfAbsent(item, this::resolve);
        BlockState state = item.placedState();
        if (drawsBuiltin(item, model)) {
            // RenderEntityItem (half size for 3D models) then RenderItem.renderItem (half again, turned round, centred).
            glPushMatrix();
            glScalef(0.25f, 0.25f, 0.25f);
            glRotatef(180f, 0f, 1f, 0f);
            glTranslatef(-0.5f, -0.5f, -0.5f);
            builtin.draw(item, red, green, blue);
            glPopMatrix();
            return;
        }
        if (renderBlockInWorld(blockShape(model, state), state, red, green, blue)) return;
        if (model.layers().isEmpty()) return;
        glDisable(GL_CULL_FACE);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.1f);
        for (int layer = 0; layer < model.layers().size(); layer++) {
            ResourceLocation texture = model.layers().get(layer);
            textures.bind(texture);
            int[] size = textures.size(texture);
            float v1 = size[1] > size[0] ? (float) size[0] / size[1] : 1f;
            int tint = layerTint(item, layer);
            glColor4f((tint >> 16 & 255) / 255f * red, (tint >> 8 & 255) / 255f * green, (tint & 255) / 255f * blue, 1);
            float z = layer * 0.001f;
            glBegin(GL_QUADS);
            glTexCoord2f(0, v1); glVertex3f(-0.25f, -0.25f, z);
            glTexCoord2f(1, v1); glVertex3f(0.25f, -0.25f, z);
            glTexCoord2f(1, 0); glVertex3f(0.25f, 0.25f, z);
            glTexCoord2f(0, 0); glVertex3f(-0.25f, 0.25f, z);
            glEnd();
        }
        glColor4f(1, 1, 1, 1);
    }

    /**
     * A dropped item entity's model, centred on the origin (1.8.9
     * RenderEntityItem then RenderItem.renderItem): block items as their
     * cube a quarter block wide, any other item as its layer textures
     * extruded one pixel thick and half a block wide, so it keeps its depth
     * when seen edge-on. The extruded faces carry normals for
     * {@link StandardLighting}, which the caller enables; block items keep
     * their own per-face shading and are drawn unlit.
     */
    public void renderDropped(ItemStack stack, float red, float green, float blue) {
        if (stack == null) return;
        Item item = stack.item();
        ItemModel model = models.computeIfAbsent(item, this::resolve);
        if (isBlockModel(item)) {
            boolean lit = glIsEnabled(GL_LIGHTING);
            if (lit) glDisable(GL_LIGHTING);
            renderItemInWorld(stack, red, green, blue);
            if (lit) glEnable(GL_LIGHTING);
            return;
        }
        if (model.layers().isEmpty()) return;
        glPushMatrix();
        glScalef(0.5f, 0.5f, 0.5f);
        glTranslatef(-0.5f, -0.5f, -0.5f);
        glDisable(GL_CULL_FACE);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.1f);
        for (int layer = 0; layer < model.layers().size(); layer++) {
            ResourceLocation texture = model.layers().get(layer);
            ItemMesh mesh = meshes.computeIfAbsent(texture, this::extrude);
            if (mesh == null) continue;
            textures.bind(texture);
            int tint = layerTint(item, layer);
            glColor4f((tint >> 16 & 255) / 255f * red, (tint >> 8 & 255) / 255f * green, (tint & 255) / 255f * blue, 1);
            mesh.draw();
        }
        if (net.minecraft.enchantment.EnchantmentHelper.hasGlint(stack)) {
            Glint.item(textures, () -> {
                for (ResourceLocation texture : model.layers()) {
                    ItemMesh mesh = meshes.get(texture);
                    if (mesh != null) mesh.draw();
                }
            });
        }
        glColor4f(1, 1, 1, 1);
        glPopMatrix();
    }

    /**
     * A block's model filling the unit cube at the current origin, shaded
     * per face and tinted by the colour (BlockRendererDispatcher.renderBlockBrightness,
     * as primed TNT is drawn).
     */
    public void renderBlockModel(BlockState state, float red, float green, float blue) {
        glPushMatrix();
        glScalef(4f, 4f, 4f);
        glTranslatef(0.125f, 0.125f, 0.125f);
        renderBlockInWorld(blockModels.getModel(state), state, red, green, blue);
        glPopMatrix();
    }

    private boolean renderBlockInWorld(BakedModel model, BlockState state, float red, float green, float blue) {
        if (model == null || model.quads().isEmpty()) return false;
        glPushMatrix();
        glScalef(0.25f, 0.25f, 0.25f);
        glTranslatef(-0.5f, -0.5f, -0.5f);
        glDisable(GL_CULL_FACE);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.1f);
        atlas.bind();
        int tint = blockTint(state);
        glBegin(GL_QUADS);
        for (BakedQuad quad : model.quads()) {
            TextureAtlas.Tile tile = atlas.tile(quad.texture());
            if (tile == null) continue;
            Direction face = quad.face();
            float shade = !quad.shade() ? 1f : face == Direction.UP ? 1f : face == Direction.DOWN ? .5f
                : (face == Direction.NORTH || face == Direction.SOUTH) ? .8f : .6f;
            int color = quad.tintIndex() >= 0 ? tint : 0xFFFFFF;
            glColor4f((color >> 16 & 255) / 255f * shade * red, (color >> 8 & 255) / 255f * shade * green,
                (color & 255) / 255f * shade * blue, 1);
            for (int i = 0; i < 4; i++) {
                glTexCoord2f(tile.u0() + (tile.u1() - tile.u0()) * quad.u(i), tile.v0() + (tile.v1() - tile.v0()) * quad.v(i));
                glVertex3f(quad.x(i), quad.y(i), quad.z(i));
            }
        }
        glEnd();
        glColor4f(1, 1, 1, 1);
        glPopMatrix();
        return true;
    }

    /** The sprite an item's crumbs are cut from (its first layer), or null for block-shaped items. */
    public ResourceLocation particleTexture(Item item) {
        if (item == null) return null;
        ItemModel model = models.computeIfAbsent(item, this::resolve);
        if (model.layers().isEmpty()) return null;
        // ItemPotion.getIconFromDamage: potion crumbs are the glass bottle, not the coloured contents.
        if (item.vanillaId().path().equals("potion")) return model.layers().get(model.layers().size() - 1);
        return model.layers().get(0);
    }

    /** Whether the item shows as a small block (true) or a flat sprite when dropped. */
    public boolean isBlockModel(Item item) {
        ItemModel model = models.computeIfAbsent(item, this::resolve);
        return blockShape(model, item.placedState()) != null || drawsBuiltin(item, model);
    }

    /** Follows the item model's parent chain to builtin/generated (layers), a block model, or builtin/entity. */
    private ItemModel resolve(Item item) { return resolve(item, item.model()); }

    private ItemModel heldModel(Item item) {
        ResourceLocation state = modelState.apply(item);
        if (state == null) return models.computeIfAbsent(item, this::resolve);
        return stateModels.computeIfAbsent(state, location -> resolve(item, location));
    }

    private ItemModel resolve(Item item, ResourceLocation model) {
        List<ResourceLocation> layers = new ArrayList<>();
        float[] rotation = null;
        float[][] display = new float[2][];
        String location = model.toString();
        for (int depth = 0; depth < 8 && location != null; depth++) {
            ResourceLocation id = new ResourceLocation(location);
            if (id.path().startsWith("builtin/generated")) return new ItemModel(layers, false, false, orDefault(rotation), display[0], display[1], null);
            if (id.path().startsWith("builtin/entity")) return new ItemModel(layers, false, true, orDefault(rotation), display[0], display[1], null);
            if (id.path().startsWith("block/")) return new ItemModel(layers, true, false, orDefault(rotation), display[0], display[1], id);
            JsonObject json = read(new ResourceLocation(id.namespace(), "models/" + id.path() + ".json"));
            if (json == null) break;
            if (layers.isEmpty() && json.has("textures")) {
                JsonObject textures = json.getAsJsonObject("textures");
                for (int layer = 0; layer < 5 && textures.has("layer" + layer); layer++)
                    layers.add(texture(textures.get("layer" + layer).getAsString()));
            }
            if (rotation == null && json.has("display") && json.getAsJsonObject("display").has("gui")) {
                JsonObject gui = json.getAsJsonObject("display").getAsJsonObject("gui");
                if (gui.has("rotation")) {
                    JsonArray values = gui.getAsJsonArray("rotation");
                    rotation = new float[]{values.get(0).getAsFloat(), values.get(1).getAsFloat(), values.get(2).getAsFloat()};
                }
            }
            if (json.has("display")) {
                JsonObject views = json.getAsJsonObject("display");
                if (display[0] == null && views.has("firstperson")) display[0] = displayTransform(views.getAsJsonObject("firstperson"));
                if (display[1] == null && views.has("thirdperson")) display[1] = displayTransform(views.getAsJsonObject("thirdperson"));
            }
            JsonElement parent = json.get("parent");
            location = parent == null ? null : parent.getAsString().contains(":") ? parent.getAsString() : "minecraft:" + parent.getAsString();
        }
        return new ItemModel(layers, item.placedState() != null && layers.isEmpty(), false, orDefault(rotation), display[0], display[1], null);
    }

    /**
     * A model "display" entry as {rotation xyz, translation xyz, scale xyz}:
     * translation in sixteenths of a block, clamped to 1.5 blocks, scale to
     * 4 (1.8.9's ItemTransformVec3f, HYPOTHESIS).
     */
    private static float[] displayTransform(JsonObject entry) {
        float[] result = {0, 0, 0, 0, 0, 0, 1, 1, 1};
        String[] keys = {"rotation", "translation", "scale"};
        for (int k = 0; k < 3; k++) {
            if (!entry.has(keys[k])) continue;
            JsonArray values = entry.getAsJsonArray(keys[k]);
            for (int i = 0; i < 3; i++) {
                float value = values.get(i).getAsFloat();
                if (k == 1) value = Math.max(-1.5f, Math.min(1.5f, value / 16f));
                if (k == 2) value = Math.max(-4f, Math.min(4f, value));
                result[k * 3 + i] = value;
            }
        }
        return result;
    }

    /**
     * Draws an item as held (1.8.9 ItemRenderer.renderItem and RenderItem):
     * the model is doubled in size, then its first- or
     * third-person display transform (translate, turn about Y, X, Z, scale),
     * then the half-size model centred on the origin. Block items are their
     * block model; other items are their layer textures extruded one pixel
     * thick with an edge wherever an opaque pixel meets a transparent one.
     * Faces carry normals for {@link StandardLighting}; the colour carries
     * the light at the holder.
     */
    public void renderHeld(Item item, Held view, float red, float green, float blue) { renderHeld(item, view, red, green, blue, false); }

    /** A held stack, with the glint over a flat enchanted item. */
    public void renderHeld(ItemStack stack, Held view, float red, float green, float blue) {
        if (stack == null) return;
        renderHeld(stack.item(), view, red, green, blue, net.minecraft.enchantment.EnchantmentHelper.hasGlint(stack));
    }

    private void renderHeld(Item item, Held view, float red, float green, float blue, boolean glint) {
        if (item == null) return;
        ItemModel model = heldModel(item);
        BlockState state = item.placedState();
        BakedModel blockModel = blockShape(model, state);
        boolean block = blockModel != null;
        boolean builtinModel = drawsBuiltin(item, model);
        if (!block && !builtinModel && model.layers().isEmpty()) return;
        float[] transform = view == Held.FIRST_PERSON ? model.firstPerson() : model.thirdPerson();
        glPushMatrix();
        // Held items are drawn at twice the size of their model's half-block frame, flat items included
        // (HYPOTHESIS: matches 1.9's converted first-person scale 0.68 = 1.8's 0.4 x 1.7, and the
        // user's comparison that flat items were too small at half this size).
        glScalef(2f, 2f, 2f);
        if (transform != null) {
            glTranslatef(transform[3], transform[4], transform[5]);
            glRotatef(transform[1], 0, 1, 0);
            glRotatef(transform[0], 1, 0, 0);
            glRotatef(transform[2], 0, 0, 1);
            glScalef(transform[6], transform[7], transform[8]);
        }
        glScalef(0.5f, 0.5f, 0.5f);
        glTranslatef(-0.5f, -0.5f, -0.5f);
        glDisable(GL_CULL_FACE);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.1f);
        if (builtinModel) {
            // RenderItem.renderItem for a built-in model: turned round about the block's middle first.
            glTranslatef(0.5f, 0.5f, 0.5f);
            glRotatef(180f, 0f, 1f, 0f);
            glTranslatef(-0.5f, -0.5f, -0.5f);
            builtin.draw(item, red, green, blue);
        } else if (block) drawBlockQuads(blockModel, state, red, green, blue);
        else {
            for (int layer = 0; layer < model.layers().size(); layer++) {
                ResourceLocation texture = model.layers().get(layer);
                ItemMesh mesh = meshes.computeIfAbsent(texture, this::extrude);
                if (mesh == null) continue;
                textures.bind(texture);
                int tint = layerTint(item, layer);
                glColor4f((tint >> 16 & 255) / 255f * red, (tint >> 8 & 255) / 255f * green, (tint & 255) / 255f * blue, 1);
                mesh.draw();
            }
            if (glint) {
                Glint.item(textures, () -> {
                    for (ResourceLocation texture : model.layers()) {
                        ItemMesh mesh = meshes.get(texture);
                        if (mesh != null) mesh.draw();
                    }
                });
            }
        }
        glColor4f(1, 1, 1, 1);
        glPopMatrix();
    }

    private void drawBlockQuads(BakedModel model, BlockState state, float red, float green, float blue) {
        atlas.bind();
        int tint = blockTint(state);
        glBegin(GL_QUADS);
        for (BakedQuad quad : model.quads()) {
            TextureAtlas.Tile tile = atlas.tile(quad.texture());
            if (tile == null) continue;
            Direction face = quad.face();
            int color = quad.tintIndex() >= 0 ? tint : 0xFFFFFF;
            glColor4f((color >> 16 & 255) / 255f * red, (color >> 8 & 255) / 255f * green, (color & 255) / 255f * blue, 1);
            glNormal3f(face.xOffset, face.yOffset, face.zOffset);
            for (int i = 0; i < 4; i++) {
                glTexCoord2f(tile.u0() + (tile.u1() - tile.u0()) * quad.u(i), tile.v0() + (tile.v1() - tile.v0()) * quad.v(i));
                glVertex3f(quad.x(i), quad.y(i), quad.z(i));
            }
        }
        glEnd();
    }

    /** An extruded item layer: xyz, uv and normal per vertex, four vertices per quad. */
    private record ItemMesh(float[] data) {
        void draw() {
            glBegin(GL_QUADS);
            for (int i = 0; i < data.length; i += 8) {
                glNormal3f(data[i + 5], data[i + 6], data[i + 7]);
                glTexCoord2f(data[i + 3], data[i + 4]);
                glVertex3f(data[i], data[i + 1], data[i + 2]);
            }
            glEnd();
        }
    }

    private final Map<ResourceLocation, ItemMesh> meshes = new HashMap<>();

    /**
     * Builds the one-pixel-thick item from its texture (the first frame of an
     * animated strip): the full picture front and back at z 7.5 and 8.5
     * sixteenths, and a one-pixel edge face on every side of an opaque pixel
     * that borders a transparent one or the edge (1.8.9 ItemModelGenerator,
     * HYPOTHESIS: edges show the pixel's own colour).
     */
    private ItemMesh extrude(ResourceLocation texture) {
        java.awt.image.BufferedImage image;
        try (var stream = resources.open(texture)) {
            image = javax.imageio.ImageIO.read(stream);
        } catch (Exception unreadable) {
            return null;
        }
        if (image == null) return null;
        int width = image.getWidth(), height = Math.min(image.getHeight(), width);
        float frame = height / (float) image.getHeight();
        boolean[][] opaque = new boolean[width][height];
        for (int px = 0; px < width; px++)
            for (int py = 0; py < height; py++) opaque[px][py] = (image.getRGB(px, py) >>> 24) != 0;
        List<Float> out = new ArrayList<>();
        float back = 7.5f / 16f, front = 8.5f / 16f;
        quad(out, 0, 0, 1, new float[][]{{0, 1, front, 0, 0}, {0, 0, front, 0, frame}, {1, 0, front, 1, frame}, {1, 1, front, 1, 0}});
        quad(out, 0, 0, -1, new float[][]{{1, 1, back, 1, 0}, {1, 0, back, 1, frame}, {0, 0, back, 0, frame}, {0, 1, back, 0, 0}});
        for (int px = 0; px < width; px++) {
            for (int py = 0; py < height; py++) {
                if (!opaque[px][py]) continue;
                float x0 = px / (float) width, x1 = (px + 1) / (float) width;
                float y1 = 1f - py / (float) height, y0 = 1f - (py + 1) / (float) height;
                float u = (px + 0.5f) / width, v = (py + 0.5f) / height * frame;
                if (px == 0 || !opaque[px - 1][py])
                    quad(out, -1, 0, 0, new float[][]{{x0, y1, back, u, v}, {x0, y0, back, u, v}, {x0, y0, front, u, v}, {x0, y1, front, u, v}});
                if (px == width - 1 || !opaque[px + 1][py])
                    quad(out, 1, 0, 0, new float[][]{{x1, y1, front, u, v}, {x1, y0, front, u, v}, {x1, y0, back, u, v}, {x1, y1, back, u, v}});
                if (py == 0 || !opaque[px][py - 1])
                    quad(out, 0, 1, 0, new float[][]{{x0, y1, back, u, v}, {x0, y1, front, u, v}, {x1, y1, front, u, v}, {x1, y1, back, u, v}});
                if (py == height - 1 || !opaque[px][py + 1])
                    quad(out, 0, -1, 0, new float[][]{{x0, y0, front, u, v}, {x0, y0, back, u, v}, {x1, y0, back, u, v}, {x1, y0, front, u, v}});
            }
        }
        float[] data = new float[out.size()];
        for (int i = 0; i < data.length; i++) data[i] = out.get(i);
        return new ItemMesh(data);
    }

    private static void quad(List<Float> out, float nx, float ny, float nz, float[][] corners) {
        for (float[] c : corners) {
            out.add(c[0]); out.add(c[1]); out.add(c[2]); out.add(c[3]); out.add(c[4]);
            out.add(nx); out.add(ny); out.add(nz);
        }
    }

    private static float[] orDefault(float[] rotation) { return rotation == null ? NO_ROTATION : rotation; }

    private static ResourceLocation texture(String name) {
        ResourceLocation raw = new ResourceLocation(name.contains(":") ? name : "minecraft:" + name);
        return new ResourceLocation(raw.namespace(), "textures/" + raw.path() + ".png");
    }

    private JsonObject read(ResourceLocation location) {
        if (!resources.exists(location)) return null;
        try (var reader = new InputStreamReader(resources.open(location), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception unreadable) {
            return null;
        }
    }
}
