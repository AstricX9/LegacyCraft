package net.minecraft.client.renderer;

import net.minecraft.client.gui.GuiTextures;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;

/**
 * The books floating over enchanting tables (1.8.9 TileEntityEnchantmentTable.update
 * and TileEntityEnchantmentTableRenderer; HYPOTHESIS: recalled constants).
 * With the player within 3 blocks the book turns to face them, opens and
 * now and then flips pages; otherwise it slowly spins and closes. It bobs
 * a hundredth of a block, 0.85 above the table's base.
 */
public final class EnchantingTableBooks {
    /** One table's book (the tile entity's animation fields). */
    public static final class Book {
        public int tickCount;
        public float pageFlip, pageFlipPrev, flipTarget, flipSpeed;
        public float bookSpread, bookSpreadPrev;
        public float bookRotation, bookRotationPrev, targetRotation;

        /** One tick with the nearest player at ({@code px}, {@code pz}) when {@code near}. */
        public void tick(boolean near, double px, double pz, double tableX, double tableZ, Random random) {
            bookSpreadPrev = bookSpread;
            bookRotationPrev = bookRotation;
            if (near) {
                targetRotation = (float) Math.atan2(pz - (tableZ + 0.5), px - (tableX + 0.5));
                bookSpread += 0.1f;
                if (bookSpread < 0.5f || random.nextInt(40) == 0) {
                    float was = flipTarget;
                    do {
                        flipTarget += random.nextInt(4) - random.nextInt(4);
                    } while (was == flipTarget);
                }
            } else {
                targetRotation += 0.02f;
                bookSpread -= 0.1f;
            }
            bookRotation = wrap(bookRotation);
            targetRotation = wrap(targetRotation);
            bookRotation += wrap(targetRotation - bookRotation) * 0.4f;
            bookSpread = Math.max(0f, Math.min(1f, bookSpread));
            tickCount++;
            pageFlipPrev = pageFlip;
            float step = Math.max(-0.2f, Math.min(0.2f, (flipTarget - pageFlip) * 0.4f));
            flipSpeed += (step - flipSpeed) * 0.9f;
            pageFlip += flipSpeed;
        }
    }

    private final Map<World.BlockPos, Book> books = new HashMap<>();
    private final Random random = new Random();

    /** Every table's book ticks; books of tables that are gone are dropped. */
    public void tick(World world, double playerX, double playerY, double playerZ) {
        books.keySet().retainAll(world.enchantingTables());
        for (World.BlockPos pos : world.enchantingTables()) {
            Book book = books.computeIfAbsent(pos, key -> new Book());
            double dx = playerX - (pos.x() + 0.5), dy = playerY - (pos.y() + 0.5), dz = playerZ - (pos.z() + 0.5);
            book.tick(dx * dx + dy * dy + dz * dz < 9.0, playerX, playerZ, pos.x(), pos.z(), random);
        }
    }

    public Map<World.BlockPos, Book> books() { return books; }

    /** Draws the books within {@code range} blocks, in world coordinates, lit by the light at each table. */
    public void render(World world, GuiTextures textures, float partialTicks, double viewX, double viewZ, double range) {
        if (books.isEmpty()) return;
        textures.bind(BookModel.TEXTURE);
        glEnable(GL_CULL_FACE);
        for (Map.Entry<World.BlockPos, Book> entry : books.entrySet()) {
            World.BlockPos pos = entry.getKey();
            double dx = pos.x() + 0.5 - viewX, dz = pos.z() + 0.5 - viewZ;
            if (dx * dx + dz * dz > range * range) continue;
            Book book = entry.getValue();
            float[] light = LightMap.color(world.getSkyLight(pos.x(), pos.y(), pos.z()), world.getBlockLight(pos.x(), pos.y(), pos.z()));
            glColor4f(light[0], light[1], light[2], 1f);
            glPushMatrix();
            glTranslatef(pos.x() + 0.5f, pos.y() + 0.75f, pos.z() + 0.5f);
            float time = book.tickCount + partialTicks;
            glTranslatef(0f, 0.1f + (float) Math.sin(time * 0.1f) * 0.01f, 0f);
            float turn = wrap(book.bookRotation - book.bookRotationPrev);
            float rotation = book.bookRotationPrev + turn * partialTicks;
            glRotatef((float) -Math.toDegrees(rotation), 0f, 1f, 0f);
            glRotatef(80f, 0f, 0f, 1f);
            float flip = book.pageFlipPrev + (book.pageFlip - book.pageFlipPrev) * partialTicks;
            float spread = book.bookSpreadPrev + (book.bookSpread - book.bookSpreadPrev) * partialTicks;
            BookModel.render(time, BookModel.flipFraction(flip, 0.25f), BookModel.flipFraction(flip, 0.75f), spread);
            glPopMatrix();
        }
        glColor4f(1f, 1f, 1f, 1f);
    }

    private static float wrap(float angle) {
        float twoPi = (float) (Math.PI * 2);
        while (angle >= (float) Math.PI) angle -= twoPi;
        while (angle < (float) -Math.PI) angle += twoPi;
        return angle;
    }
}
