package net.minecraft.client.renderer;

import net.minecraft.block.BlockState;
import net.minecraft.block.state.ActualState;
import net.minecraft.client.entity.Player;
import net.minecraft.client.renderer.block.model.BlockModelManager;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;

/**
 * Draws the world the way 1.8.9's RenderGlobal does: in 16x16x16 sections,
 * each with its own vertex buffers, built on worker threads from snapshots
 * of the world and uploaded a few per frame, skipped when outside the view
 * frustum, and drawn in two passes: every opaque and cut-out face first,
 * then water, ice and stained glass, far to near and sorted within each
 * section, so translucent faces blend over whatever is behind them.
 *
 * <p>A section is only built once its chunk and all eight neighbours exist
 * (vanilla waits the same way), so building never generates terrain; the
 * renderer asks for nearby chunks under a per-frame time budget instead.
 * Edits the player makes are rebuilt on the spot so they show the same
 * frame; everything else (lighting and decoration changes) goes to the
 * workers.
 */
public final class WorldRenderer {
    /** A targeted block, the face the ray entered through, the hit point relative to that block and its world-space outline box. */
    public record Hit(int x, int y, int z, Direction face, float hitX, float hitY, float hitZ, AxisAlignedBB box) {
        public int placeX() { return x + face.xOffset; }
        public int placeY() { return y + face.yOffset; }
        public int placeZ() { return z + face.zOffset; }
    }

    private static final int CHUNK = Chunk.SIZE, SECTIONS = Chunk.HEIGHT / 16;
    private static final int BYTES_PER_VERTEX = SectionMesher.INTS_PER_VERTEX * 4;
    /** Milliseconds per frame spent generating chunks, uploading meshes and building edited sections. */
    private static final long GENERATE_BUDGET = 6_000_000L, UPLOAD_BUDGET = 3_000_000L;
    private static final int MAX_URGENT_PER_FRAME = 8, MAX_SUBMIT_PER_FRAME = 24, RESORTS_PER_FRAME = 6;

    private final World world;
    private final TextureAtlas atlas;
    private final SectionMesher mesher;
    private final Map<Long, Column> columns = new HashMap<>();
    private final ExecutorService workers;
    private final int maxInFlight;
    private int inFlight;
    private final ConcurrentLinkedQueue<Built> finished = new ConcurrentLinkedQueue<>();
    private IntBuffer upload = BufferUtils.createIntBuffer(SectionMesher.INTS_PER_QUAD * 4096);
    private final FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
    private final float[] projection = new float[16], modelView = new float[16], planes = new float[24];
    private int rebuilds, renderedSections;
    /** 16x16 (sky, block) lightmap on texture unit 1, rebuilt from {@link LightMap} when it changes. */
    private int lightmapTexture, lightmapVersion = -1;

    public WorldRenderer(World world, TextureAtlas atlas, BlockModelManager models, BiomeColors biomeColors) {
        this.world = world;
        this.atlas = atlas;
        this.mesher = new SectionMesher(atlas, models, biomeColors);
        int threads = Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors() - 2));
        workers = Executors.newFixedThreadPool(threads, task -> {
            Thread thread = new Thread(task, "Chunk mesher");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        });
        maxInFlight = threads * 3;
        world.addListener(listener);
    }

    private final World.Listener listener = new World.Listener() {
        @Override public void blockChanged(int x, int y, int z) { changed(x, y, z); }
        // Light spreads across chunk edges and faces there are lit from the neighbour, so neighbours rebuild too.
        @Override public void lightChanged(int chunkX, int chunkZ) { markColumnAndNeighbours(chunkX, chunkZ); }
        @Override public void chunkChanged(int chunkX, int chunkZ) { markColumnAndNeighbours(chunkX, chunkZ); }
    };

    private static final class Column {
        final int x, z;
        final Section[] sections = new Section[SECTIONS];
        Column(int x, int z) {
            this.x = x;
            this.z = z;
            for (int y = 0; y < SECTIONS; y++) sections[y] = new Section(x, y, z);
        }
    }

    private static final class Section {
        final int x, y, z;
        int opaqueBuffer, opaqueVertices, cutoutBuffer, cutoutVertices, translucentBuffer, translucentVertices;
        /** Kept for re-sorting as the camera moves. */
        SectionMesher.Buffer translucent;
        float sortedFromX = Float.NaN, sortedFromY, sortedFromZ;
        boolean dirty = true, urgent, pending, removed;
        int version;
        Section(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        void mark(boolean now) { dirty = true; urgent |= now; version++; }
    }

    private record Built(Section section, SectionMesher.Mesh mesh) {}

    public void render(Player player, int renderDistance) {
        long frameStart = System.nanoTime();
        int playerChunkX = World.floorDiv((int) Math.floor(player.x), CHUNK);
        int playerChunkZ = World.floorDiv((int) Math.floor(player.z), CHUNK);
        int radius = (renderDistance + CHUNK - 1) / CHUNK;
        generateAround(playerChunkX, playerChunkZ, radius + 1, frameStart + GENERATE_BUDGET);

        List<Column> visible = new ArrayList<>();
        double limit = (renderDistance + 14) * (double) (renderDistance + 14);
        for (int cz = playerChunkZ - radius; cz <= playerChunkZ + radius; cz++) {
            for (int cx = playerChunkX - radius; cx <= playerChunkX + radius; cx++) {
                double dx = cx * CHUNK + 8 - player.x, dz = cz * CHUNK + 8 - player.z;
                if (dx * dx + dz * dz > limit) continue;
                long key = World.chunkKey(cx, cz);
                Column column = columns.get(key);
                if (column == null) {
                    if (!neighbourhoodReady(cx, cz)) continue;
                    column = new Column(cx, cz);
                    columns.put(key, column);
                }
                visible.add(column);
            }
        }
        double eyeX = player.x, eyeY = player.eyeY(), eyeZ = player.z;
        uploadFinished(System.nanoTime() + UPLOAD_BUDGET, eyeX, eyeY, eyeZ);
        scheduleBuilds(visible, eyeX, eyeY, eyeZ);
        draw(visible, eyeX, eyeY, eyeZ);
        unloadFarColumns(playerChunkX, playerChunkZ, radius + 2);
    }

    // ---- Generation ------------------------------------------------------------------------------

    /** Generates missing chunks nearest-first, ring by ring, until the time budget runs out. */
    private void generateAround(int centerX, int centerZ, int radius, long deadline) {
        for (int ring = 0; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    if (world.generatedChunk(centerX + dx, centerZ + dz) != null) continue;
                    if (System.nanoTime() > deadline) return;
                    world.getChunk(centerX + dx, centerZ + dz);
                }
            }
        }
    }

    private boolean neighbourhoodReady(int chunkX, int chunkZ) {
        for (int dz = -1; dz <= 1; dz++)
            for (int dx = -1; dx <= 1; dx++)
                if (world.generatedChunk(chunkX + dx, chunkZ + dz) == null) return false;
        return true;
    }

    // ---- Building --------------------------------------------------------------------------------

    private void scheduleBuilds(List<Column> visible, double eyeX, double eyeY, double eyeZ) {
        List<Section> dirty = new ArrayList<>();
        for (Column column : visible)
            for (Section section : column.sections)
                if (section.dirty && !section.pending) dirty.add(section);
        if (dirty.isEmpty()) return;
        dirty.sort(Comparator.comparingDouble(section -> distanceSquared(section, eyeX, eyeY, eyeZ)));
        int urgentBuilt = 0, submitted = 0;
        for (Section section : dirty) {
            Chunk chunk = world.generatedChunk(section.x, section.z);
            if (chunk == null) continue;
            if (chunk.section(section.y) == null) {
                // Nothing in it: neighbours' faces belong to their own sections.
                section.dirty = section.urgent = false;
                upload(section, new SectionMesher.Mesh(), eyeX, eyeY, eyeZ);
                continue;
            }
            if (section.urgent && urgentBuilt < MAX_URGENT_PER_FRAME) {
                urgentBuilt++;
                section.dirty = section.urgent = false;
                rebuilds++;
                upload(section, mesher.build(new SectionSnapshot(world, section.x, section.y, section.z),
                    section.x, section.y, section.z), eyeX, eyeY, eyeZ);
            } else if (inFlight < maxInFlight && submitted < MAX_SUBMIT_PER_FRAME) {
                submitted++;
                inFlight++;
                section.dirty = section.urgent = false;
                section.pending = true;
                SectionSnapshot snapshot = new SectionSnapshot(world, section.x, section.y, section.z);
                workers.execute(() -> {
                    SectionMesher.Mesh mesh;
                    try {
                        mesh = mesher.build(snapshot, section.x, section.y, section.z);
                    } catch (RuntimeException error) {
                        error.printStackTrace();
                        mesh = new SectionMesher.Mesh();
                    }
                    finished.add(new Built(section, mesh));
                });
            }
        }
    }

    private void uploadFinished(long deadline, double eyeX, double eyeY, double eyeZ) {
        Built built;
        while (System.nanoTime() < deadline && (built = finished.poll()) != null) {
            inFlight--;
            built.section.pending = false;
            if (built.section.removed) continue;
            rebuilds++;
            upload(built.section, built.mesh, eyeX, eyeY, eyeZ);
        }
    }

    private void upload(Section section, SectionMesher.Mesh mesh, double eyeX, double eyeY, double eyeZ) {
        section.opaqueBuffer = store(section.opaqueBuffer, mesh.opaque);
        section.opaqueVertices = mesh.opaque.size / SectionMesher.INTS_PER_VERTEX;
        section.cutoutBuffer = store(section.cutoutBuffer, mesh.cutout);
        section.cutoutVertices = mesh.cutout.size / SectionMesher.INTS_PER_VERTEX;
        if (mesh.translucent.size > 0) {
            float fromX = (float) (eyeX - section.x * 16), fromY = (float) (eyeY - section.y * 16), fromZ = (float) (eyeZ - section.z * 16);
            SectionMesher.sortBackToFront(mesh.translucent, fromX, fromY, fromZ);
            section.sortedFromX = (float) eyeX;
            section.sortedFromY = (float) eyeY;
            section.sortedFromZ = (float) eyeZ;
            section.translucent = mesh.translucent;
        } else {
            section.translucent = null;
        }
        section.translucentBuffer = store(section.translucentBuffer, mesh.translucent);
        section.translucentVertices = mesh.translucent.size / SectionMesher.INTS_PER_VERTEX;
    }

    /** Uploads vertex data into a buffer object, creating or freeing it as needed; returns the buffer name. */
    private int store(int buffer, SectionMesher.Buffer data) {
        if (data.size == 0) {
            if (buffer != 0) glDeleteBuffers(buffer);
            return 0;
        }
        if (buffer == 0) buffer = glGenBuffers();
        if (upload.capacity() < data.size) upload = BufferUtils.createIntBuffer(Math.max(data.size, upload.capacity() * 2));
        upload.clear();
        upload.put(data.data, 0, data.size).flip();
        glBindBuffer(GL_ARRAY_BUFFER, buffer);
        glBufferData(GL_ARRAY_BUFFER, upload, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        return buffer;
    }

    // ---- Drawing ---------------------------------------------------------------------------------

    private void draw(List<Column> visible, double eyeX, double eyeY, double eyeZ) {
        readFrustum();
        List<Section> opaque = new ArrayList<>(), cutout = new ArrayList<>(), translucent = new ArrayList<>();
        for (Column column : visible) {
            for (Section section : column.sections) {
                if (section.opaqueVertices == 0 && section.cutoutVertices == 0 && section.translucentVertices == 0) continue;
                if (!inFrustum(section.x * 16, section.y * 16, section.z * 16)) continue;
                if (section.opaqueVertices > 0 || section.cutoutVertices > 0) opaque.add(section);
                if (section.cutoutVertices > 0) cutout.add(section);
                if (section.translucentVertices > 0) translucent.add(section);
            }
        }
        renderedSections = opaque.size();
        bindLightmap();
        atlas.bind();
        // Smooth lighting interpolates between vertices; menus switch to flat shading for their gradients.
        glShadeModel(GL_SMOOTH);
        glEnable(GL_TEXTURE_2D);
        // Solid and cut-out faces draw unblended (EntityRenderer.renderWorldPass): mipmap levels give leaf
        // edges partial alpha, which must not let the sky show through; the alpha test keeps anything above 0.1
        // and the mip generator already dropped what fell below 96/255.
        glDisable(GL_BLEND);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, .1f);
        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_COLOR_ARRAY);
        glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        // Opaque and cut-out faces, near to far so the depth test rejects hidden pixels early.
        opaque.sort(Comparator.comparingDouble(section -> distanceSquared(section, eyeX, eyeY, eyeZ)));
        // Back faces are culled like vanilla's terrain pass; without it the touching faces of neighbouring
        // leaves (fancy leaves draw both) z-fight. Menus and item rendering turn culling off, so set it here.
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        for (Section section : opaque)
            if (section.opaqueVertices > 0) drawBuffer(section, section.opaqueBuffer, section.opaqueVertices);
        // The CUTOUT layer without mipmaps (setBlurMipmap(false, false) around it), so plants keep their
        // full-resolution shape at any distance.
        if (!cutout.isEmpty()) {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            for (Section section : cutout) drawBuffer(section, section.cutoutBuffer, section.cutoutVertices);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, atlas.mipmapLevels() > 0 ? GL_NEAREST_MIPMAP_LINEAR : GL_NEAREST);
        }
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        // Translucent faces last, still culled like vanilla's translucent layer (the fluid renderer
        // adds the back-facing copies of water faces itself), far to near, re-sorted inside the nearest sections as the camera moves.
        translucent.sort(Comparator.comparingDouble(section -> -distanceSquared(section, eyeX, eyeY, eyeZ)));
        int resorts = 0;
        for (int i = translucent.size() - 1; i >= 0 && resorts < RESORTS_PER_FRAME; i--) {
            Section section = translucent.get(i);
            if (distanceSquared(section, eyeX, eyeY, eyeZ) > 48 * 48) break;
            if (resort(section, eyeX, eyeY, eyeZ)) resorts++;
        }
        for (Section section : translucent) drawBuffer(section, section.translucentBuffer, section.translucentVertices);
        glDisable(GL_CULL_FACE);
        glDisableClientState(GL_VERTEX_ARRAY);
        glDisableClientState(GL_COLOR_ARRAY);
        glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        glClientActiveTexture(GL_TEXTURE1);
        glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        glClientActiveTexture(GL_TEXTURE0);
        glActiveTexture(GL_TEXTURE1);
        glDisable(GL_TEXTURE_2D);
        glActiveTexture(GL_TEXTURE0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glDisable(GL_ALPHA_TEST);
        glColor4f(1, 1, 1, 1);
    }

    /** Puts the lightmap on texture unit 1, multiplying the block texture, re-uploading it if the light changed. */
    private void bindLightmap() {
        glActiveTexture(GL_TEXTURE1);
        if (lightmapTexture == 0) {
            lightmapTexture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, lightmapTexture);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP);
        }
        glBindTexture(GL_TEXTURE_2D, lightmapTexture);
        if (lightmapVersion != LightMap.version()) {
            lightmapVersion = LightMap.version();
            java.nio.ByteBuffer pixels = BufferUtils.createByteBuffer(16 * 16 * 4);
            // Texel (s, t) = (sky, block): the mesher writes sky light into the first coordinate.
            for (int block = 0; block < 16; block++) {
                for (int sky = 0; sky < 16; sky++) {
                    float[] color = LightMap.color(sky, block);
                    pixels.put((byte) Math.round(color[0] * 255)).put((byte) Math.round(color[1] * 255))
                        .put((byte) Math.round(color[2] * 255)).put((byte) 255);
                }
            }
            pixels.flip();
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 16, 16, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        }
        glEnable(GL_TEXTURE_2D);
        glTexEnvi(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_MODULATE);
        glActiveTexture(GL_TEXTURE0);
        glClientActiveTexture(GL_TEXTURE1);
        glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        glClientActiveTexture(GL_TEXTURE0);
    }

    private boolean resort(Section section, double eyeX, double eyeY, double eyeZ) {
        if (section.translucent == null) return false;
        double dx = eyeX - section.sortedFromX, dy = eyeY - section.sortedFromY, dz = eyeZ - section.sortedFromZ;
        if (dx * dx + dy * dy + dz * dz < 1.0) return false;
        SectionMesher.sortBackToFront(section.translucent, (float) (eyeX - section.x * 16),
            (float) (eyeY - section.y * 16), (float) (eyeZ - section.z * 16));
        section.sortedFromX = (float) eyeX;
        section.sortedFromY = (float) eyeY;
        section.sortedFromZ = (float) eyeZ;
        section.translucentBuffer = store(section.translucentBuffer, section.translucent);
        return true;
    }

    private void drawBuffer(Section section, int buffer, int vertices) {
        glBindBuffer(GL_ARRAY_BUFFER, buffer);
        glVertexPointer(3, GL_FLOAT, BYTES_PER_VERTEX, 0L);
        glColorPointer(4, GL_UNSIGNED_BYTE, BYTES_PER_VERTEX, 12L);
        glTexCoordPointer(2, GL_FLOAT, BYTES_PER_VERTEX, 16L);
        glClientActiveTexture(GL_TEXTURE1);
        glTexCoordPointer(2, GL_FLOAT, BYTES_PER_VERTEX, 24L);
        glClientActiveTexture(GL_TEXTURE0);
        glPushMatrix();
        glTranslatef(section.x * 16, section.y * 16, section.z * 16);
        glDrawArrays(GL_QUADS, 0, vertices);
        glPopMatrix();
    }

    private static double distanceSquared(Section section, double x, double y, double z) {
        double dx = section.x * 16 + 8 - x, dy = section.y * 16 + 8 - y, dz = section.z * 16 + 8 - z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** The six clip planes of the current projection x modelview matrix (Gribb-Hartmann). */
    private void readFrustum() {
        matrix.clear();
        glGetFloatv(GL_PROJECTION_MATRIX, matrix);
        matrix.get(projection);
        matrix.clear();
        glGetFloatv(GL_MODELVIEW_MATRIX, matrix);
        matrix.get(modelView);
        float[] clip = new float[16];
        for (int column = 0; column < 4; column++)
            for (int row = 0; row < 4; row++) {
                float sum = 0;
                for (int k = 0; k < 4; k++) sum += projection[k * 4 + row] * modelView[column * 4 + k];
                clip[column * 4 + row] = sum;
            }
        for (int plane = 0; plane < 6; plane++) {
            int row = plane / 2;
            float sign = plane % 2 == 0 ? 1 : -1;
            for (int column = 0; column < 4; column++)
                planes[plane * 4 + column] = clip[column * 4 + 3] + sign * clip[column * 4 + row];
        }
    }

    private boolean inFrustum(float minX, float minY, float minZ) {
        for (int plane = 0; plane < 6; plane++) {
            float a = planes[plane * 4], b = planes[plane * 4 + 1], c = planes[plane * 4 + 2], d = planes[plane * 4 + 3];
            // The box corner furthest along the plane's normal.
            float x = a >= 0 ? minX + 16 : minX, y = b >= 0 ? minY + 16 : minY, z = c >= 0 ? minZ + 16 : minZ;
            if (a * x + b * y + c * z + d < 0) return false;
        }
        return true;
    }

    private void unloadFarColumns(int centerX, int centerZ, int limit) {
        List<Long> remove = new ArrayList<>();
        for (var entry : columns.entrySet()) {
            Column column = entry.getValue();
            if (Math.max(Math.abs(column.x - centerX), Math.abs(column.z - centerZ)) <= limit) continue;
            for (Section section : column.sections) free(section);
            remove.add(entry.getKey());
        }
        for (long key : remove) columns.remove(key);
    }

    private static void free(Section section) {
        section.removed = true;
        if (section.opaqueBuffer != 0) glDeleteBuffers(section.opaqueBuffer);
        if (section.translucentBuffer != 0) glDeleteBuffers(section.translucentBuffer);
        if (section.cutoutBuffer != 0) glDeleteBuffers(section.cutoutBuffer);
        section.opaqueBuffer = section.cutoutBuffer = section.translucentBuffer = 0;
        section.opaqueVertices = section.cutoutVertices = section.translucentVertices = 0;
        section.translucent = null;
    }

    public Hit raycast(Player player,double reach){
        return raycast(world,player.x,player.eyeY(),player.z,player.dirX(),player.dirY(),player.dirZ(),reach);
    }

    /**
     * Walks the cells along a unit-length view ray and returns the first block
     * whose selection box the ray enters within reach (liquids, fire and air
     * are skipped; small blocks can be aimed past).
     */
    public static Hit raycast(IBlockAccess world,double ox,double oy,double oz,double dx,double dy,double dz,double reach){
        return raycast(world,ox,oy,oz,dx,dy,dz,reach,false);
    }

    /**
     * As above; with {@code sources} the ray also stops at liquid sources
     * (level 0) as full cubes, the way an empty bucket aims
     * (BlockLiquid.canCollideCheck with hitIfLiquid).
     */
    public static Hit raycast(IBlockAccess world,double ox,double oy,double oz,double dx,double dy,double dz,double reach,boolean sources){
        int x=floor(ox),y=floor(oy),z=floor(oz);
        int stepX=dx>0?1:-1,stepY=dy>0?1:-1,stepZ=dz>0?1:-1;
        double deltaX=dx==0?Double.POSITIVE_INFINITY:Math.abs(1/dx);
        double deltaY=dy==0?Double.POSITIVE_INFINITY:Math.abs(1/dy);
        double deltaZ=dz==0?Double.POSITIVE_INFINITY:Math.abs(1/dz);
        double nextX=dx==0?Double.POSITIVE_INFINITY:(dx>0?x+1-ox:ox-x)*deltaX;
        double nextY=dy==0?Double.POSITIVE_INFINITY:(dy>0?y+1-oy:oy-y)*deltaY;
        double nextZ=dz==0?Double.POSITIVE_INFINITY:(dz>0?z+1-oz:oz-z)*deltaZ;
        double travelled=0;
        // A selection box may poke into the next cell (fence posts are inset, piston arms reach out), so test a little past reach.
        while(travelled<=reach+1){
            BlockState stored=world.getState(x,y,z);
            if(sources&&stored.metadata()==0&&net.minecraft.block.Liquids.isLiquid(stored.block())){
                AxisAlignedBB box=AxisAlignedBB.FULL.offset(x,y,z);
                AxisAlignedBB.RayHit entry=box.rayEntry(ox,oy,oz,dx,dy,dz);
                if(entry!=null&&entry.distance()<=reach)
                    return new Hit(x,y,z,entry.face(),
                        (float)(ox+dx*entry.distance()-x),(float)(oy+dy*entry.distance()-y),(float)(oz+dz*entry.distance()-z),box);
            }
            if(!stored.block().isAir()&&stored.block().isTargetable()){
                ActualState state=stored.block().actualState(world,x,y,z,stored);
                AxisAlignedBB local=stored.block().selectionBox(state,world,x,y,z);
                if(local!=null){
                    AxisAlignedBB box=local.offset(x,y,z);
                    AxisAlignedBB.RayHit entry=box.rayEntry(ox,oy,oz,dx,dy,dz);
                    if(entry!=null&&entry.distance()<=reach){
                        return new Hit(x,y,z,entry.face(),
                            (float)(ox+dx*entry.distance()-x),(float)(oy+dy*entry.distance()-y),(float)(oz+dz*entry.distance()-z),box);
                    }
                }
            }
            if(nextX<nextY&&nextX<nextZ){x+=stepX;travelled=nextX;nextX+=deltaX;}
            else if(nextY<nextZ){y+=stepY;travelled=nextY;nextY+=deltaY;}
            else{z+=stepZ;travelled=nextZ;nextZ+=deltaZ;}
        }
        return null;
    }

    // ---- Change tracking -------------------------------------------------------------------------

    /** A block edit: its section (and any section it borders) rebuilds this frame. */
    public void changed(int x, int y, int z) {
        int cx = World.floorDiv(x, CHUNK), cz = World.floorDiv(z, CHUNK), sy = y >> 4;
        int lx = World.floorMod(x, CHUNK), lz = World.floorMod(z, CHUNK), ly = y & 15;
        for (int dx = lx == 0 ? -1 : 0; dx <= (lx == 15 ? 1 : 0); dx++)
            for (int dz = lz == 0 ? -1 : 0; dz <= (lz == 15 ? 1 : 0); dz++)
                for (int dy = ly == 0 ? -1 : 0; dy <= (ly == 15 ? 1 : 0); dy++)
                    markSection(cx + dx, sy + dy, cz + dz, true);
    }

    private void markSection(int chunkX, int sectionY, int chunkZ, boolean now) {
        if (sectionY < 0 || sectionY >= SECTIONS) return;
        Column column = columns.get(World.chunkKey(chunkX, chunkZ));
        if (column != null) column.sections[sectionY].mark(now);
    }

    private void markColumn(int chunkX, int chunkZ) {
        Column column = columns.get(World.chunkKey(chunkX, chunkZ));
        if (column != null) for (Section section : column.sections) section.mark(false);
    }

    private void markColumnAndNeighbours(int chunkX, int chunkZ) {
        markColumn(chunkX, chunkZ);
        markColumn(chunkX - 1, chunkZ);
        markColumn(chunkX + 1, chunkZ);
        markColumn(chunkX, chunkZ - 1);
        markColumn(chunkX, chunkZ + 1);
    }

    /** F3+A: rebuild every section. */
    public void markAllDirty() { for (Column column : columns.values()) for (Section section : column.sections) section.mark(false); }
    /** Sections holding a mesh (F3 "C:" total). */
    public int meshCount() {
        int count = 0;
        for (Column column : columns.values())
            for (Section section : column.sections) if (section.opaqueVertices > 0 || section.cutoutVertices > 0 || section.translucentVertices > 0) count++;
        return count;
    }
    /** Sections drawn last frame (F3 "C:" first number). */
    public int renderedCount() { return renderedSections; }
    /** Section rebuilds since the last call (F3 "chunk updates" per second). */
    public int takeRebuildCount() { int count = rebuilds; rebuilds = 0; return count; }

    public void destroy() {
        world.removeListener(listener);
        workers.shutdownNow();
        if (lightmapTexture != 0) glDeleteTextures(lightmapTexture);
        lightmapTexture = 0;
        for (Column column : columns.values()) for (Section section : column.sections) free(section);
        columns.clear();
    }

    private static int floor(double value) { return (int) Math.floor(value); }
}
