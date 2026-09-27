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
 *   <li>block items are their block model, turned 210° about X and -135°
 *       about Y (plus the item model's own "gui" rotation, e.g. stairs and
 *       fences), 10 GUI pixels per block and shaded per face;</li>
 *   <li>the overlay is the stack count bottom-right and the durability bar.</li>
 * </ul>
 * Chests, banners and mob heads use {@code builtin/entity} models that need
 * tile-entity renderers, which do not exist yet; they show their block model
 * when there is one and nothing otherwise (DEV-015). HYPOTHESIS until
 * screenshot-compared.
 */
public final class ItemRenderer {
    /** Resolved item model: flat layers, or a block rendered in 3D with an extra GUI rotation. */
    private record ItemModel(List<ResourceLocation> layers, boolean block, boolean builtinEntity, float[] guiRotation) {}

    private static final float[] NO_ROTATION = {0, 0, 0};
    private final IResourceManager resources;
    private final GuiTextures textures;
    private final TextureAtlas atlas;
    private final BlockModelManager blockModels;
    private final int grassColor, foliageColor;
    private final Map<Item, ItemModel> models = new HashMap<>();

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

    public void renderItemAndEffectIntoGUI(ItemStack stack, int x, int y) {
        if (stack == null) return;
        renderItemIntoGUI(stack.item(), x, y);
    }

    public void renderItemIntoGUI(Item item, int x, int y) {
        if (item == null) return;
        ItemModel model = models.computeIfAbsent(item, this::resolve);
        BlockState state = item.placedState();
        if ((model.block() || model.builtinEntity()) && state != null && renderBlock(state, model.guiRotation(), x, y)) return;
        if (!model.layers().isEmpty()) renderFlat(item, model.layers(), x, y);
    }

    /** Stack size (when not 1) and the durability bar, drawn over an item. */
    public void renderItemOverlays(FontRenderer font, ItemStack stack, int x, int y) {
        if (stack == null) return;
        glDisable(GL_DEPTH_TEST);
        if (stack.count() != 1) {
            String count = String.valueOf(stack.count());
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
            glColor4f((tint >> 16 & 255) / 255f, (tint >> 8 & 255) / 255f, (tint & 255) / 255f, 1);
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
        if (layer != 0) return 0xFFFFFF;
        String path = item.name().path();
        if (path.startsWith("leather_")) return 0xA06540;
        if (path.equals("bottle_drinkable") || path.equals("bottle_splash")) return 0x385DC6;
        if (path.equals("tall_grass") || path.equals("fern") || path.equals("double_grass") || path.equals("double_fern")) return grassColor;
        if (path.equals("vine")) return foliageColor;
        if (path.equals("waterlily")) return 0x208030;
        return 0xFFFFFF;
    }

    /** Block tint for a quad with a tint index (grass/leaves in their default inventory colours). */
    private int blockTint(BlockState state) {
        String block = state.block().name().path();
        return switch (block) {
            case "grass", "tallgrass" -> grassColor;
            case "leaves" -> switch (state.metadata() & 3) { case 1 -> 0x619961; case 2 -> 0x80A755; default -> foliageColor; };
            case "leaves2", "vine" -> foliageColor;
            case "double_plant" -> grassColor;
            default -> state.block().tintColor();
        };
    }

    private boolean renderBlock(BlockState state, float[] guiRotation, int x, int y) {
        BakedModel model = blockModels.getModel(state);
        if (model == null || model.quads().isEmpty()) return false;
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
            glColor4f((color >> 16 & 255) / 255f * shade, (color >> 8 & 255) / 255f * shade, (color & 255) / 255f * shade, 1);
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
    public void renderItemInWorld(Item item, float red, float green, float blue) {
        if (item == null) return;
        ItemModel model = models.computeIfAbsent(item, this::resolve);
        BlockState state = item.placedState();
        if ((model.block() || model.builtinEntity()) && state != null && renderBlockInWorld(state, red, green, blue)) return;
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

    private boolean renderBlockInWorld(BlockState state, float red, float green, float blue) {
        BakedModel model = blockModels.getModel(state);
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

    /** Whether the item shows as a small block (true) or a flat sprite when dropped. */
    public boolean isBlockModel(Item item) {
        ItemModel model = models.computeIfAbsent(item, this::resolve);
        return (model.block() || model.builtinEntity()) && item.placedState() != null;
    }

    /** Follows the item model's parent chain to builtin/generated (layers), a block model, or builtin/entity. */
    private ItemModel resolve(Item item) {
        List<ResourceLocation> layers = new ArrayList<>();
        float[] rotation = null;
        String location = item.model().toString();
        for (int depth = 0; depth < 8 && location != null; depth++) {
            ResourceLocation id = new ResourceLocation(location);
            if (id.path().startsWith("builtin/generated")) return new ItemModel(layers, false, false, orDefault(rotation));
            if (id.path().startsWith("builtin/entity")) return new ItemModel(layers, false, true, orDefault(rotation));
            if (id.path().startsWith("block/")) return new ItemModel(layers, true, false, orDefault(rotation));
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
            JsonElement parent = json.get("parent");
            location = parent == null ? null : parent.getAsString().contains(":") ? parent.getAsString() : "minecraft:" + parent.getAsString();
        }
        return new ItemModel(layers, item.placedState() != null && layers.isEmpty(), false, orDefault(rotation));
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
