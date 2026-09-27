package net.minecraft.block;

import net.minecraft.block.state.ActualState;
import net.minecraft.block.state.BlockStateDefinition;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;

import java.util.ArrayList;
import java.util.List;

/**
 * A registry entry and the base of the per-block classes. Its data (material,
 * opacity, bounds...) comes from the block's data file; its states from the
 * separate states file ({@link #stateDefinition()}). Subclasses override the
 * behaviour hooks below, one class per kind of block (stairs, fences, doors,
 * torches...). The plain {@code Block} is a static-shaped block with no
 * neighbour logic, placement orientation or support requirement.
 */
public class Block {
    /** MODEL: blockstate/JSON model; FLUID: fluid renderer; ENTITY: tile-entity special renderer; INVISIBLE: not drawn. */
    public enum RenderType { MODEL, FLUID, ENTITY, INVISIBLE }
    /** When a face against a neighbour of the same kind is hidden even though neither is opaque. */
    public enum CullSame { NONE, BLOCK, STATE, MATERIAL }

    /** Everything a block's data file defines, handed to the block class that implements it. */
    public record Properties(int registryId, ResourceLocation name, String material, boolean solid, boolean opaque,
                             boolean fullCube, boolean translucent, float hardness, RenderType renderType,
                             CullSame cullSame, int tintColor, int lightLevel, int lightOpacity,
                             boolean useNeighborBrightness, ResourceLocation modelOverride,
                             BlockStateDefinition stateDefinition, AxisAlignedBB bounds,
                             List<AxisAlignedBB> collision, boolean targetable) {}

    private final Properties properties;

    protected Block(Properties properties) {
        this.properties = properties;
    }

    public int registryId() { return properties.registryId(); }
    public ResourceLocation name() { return properties.name(); }
    public String material() { return properties.material(); }
    public boolean isSolid() { return properties.solid(); }
    /** Vanilla "opaque cube": hides every neighbouring face that touches it. */
    public boolean isOpaque() { return properties.opaque(); }
    public boolean isFullCube() { return properties.fullCube(); }
    /** Approximation of vanilla's "normal cube": a full cube that blocks movement. */
    public boolean isNormalCube() { return isSolid() && isFullCube(); }
    public boolean isTranslucent() { return properties.translucent(); }
    public boolean isAir() { return material().equals("air"); }
    public float hardness() { return properties.hardness(); }
    public RenderType renderType() { return properties.renderType(); }
    public CullSame cullSame() { return properties.cullSame(); }
    public int tintColor() { return properties.tintColor(); }
    public int lightLevel() { return properties.lightLevel(); }
    /** How much light passing through loses (0-15); 15 stops it. Vanilla stores 255 for opaque cubes, which acts as 15. */
    public int lightOpacity() { return properties.lightOpacity(); }
    /** Partial blocks that render with their brightest neighbour's light instead of their own (slabs, stairs, farmland). */
    public boolean useNeighborBrightness() { return properties.useNeighborBrightness(); }
    public ResourceLocation modelOverride() { return properties.modelOverride(); }
    public BlockStateDefinition stateDefinition() { return properties.stateDefinition(); }
    /** Static selection bounds from the data file, in block-local coordinates. */
    public AxisAlignedBB bounds() { return properties.bounds(); }
    /** Whether the crosshair can target this block (liquids and fire cannot). */
    public boolean isTargetable() { return properties.targetable(); }
    /** The vanilla default state, which is not always metadata 0 (torches default to facing=up, metadata 5). */
    public BlockState defaultState() { return BlockState.of(this, stateDefinition().defaultMetadata()); }

    // ---- Behaviour hooks -------------------------------------------------------------------------

    /** The state with its neighbour-derived ("actual") properties filled in. */
    public ActualState actualState(IBlockAccess world, int x, int y, int z, BlockState state) {
        return ActualState.withoutWorld(state);
    }

    /** The outline/targeting box in block-local coordinates. */
    public AxisAlignedBB selectionBox(ActualState state, IBlockAccess world, int x, int y, int z) {
        return bounds();
    }

    /** Collision boxes in block-local coordinates; empty for blocks entities pass through. */
    public List<AxisAlignedBB> collisionBoxes(ActualState state, IBlockAccess world, int x, int y, int z) {
        if (!isSolid()) return List.of();
        return properties.collision() != null ? properties.collision() : List.of(bounds());
    }

    /**
     * Clicking a block of this kind with this item may change the clicked block
     * itself (a slab on a matching slab); {@code null} means place normally.
     */
    public BlockPlacement.Placement placeOnto(IBlockAccess world, BlockState clicked, int itemMetadata, BlockPlacement.Context context) {
        return null;
    }

    /**
     * The state(s) this block places at the target position, oriented from the
     * click; an empty list refuses the placement.
     */
    public List<BlockPlacement.Placement> placements(IBlockAccess world, int x, int y, int z, int itemMetadata,
                                                     Direction face, BlockPlacement.Context context) {
        return List.of(new BlockPlacement.Placement(x, y, z, BlockState.of(this, itemMetadata)));
    }

    /** Whether the state could exist here given its neighbours (support, soil, the other half...). */
    public boolean canStay(IBlockAccess world, int x, int y, int z, BlockState state) {
        return true;
    }

    /** The state after a neighbour changed: unchanged, adjusted, or air when it can no longer stay. */
    public BlockState afterNeighborChange(IBlockAccess world, int x, int y, int z, BlockState state) {
        return canStay(world, x, y, z, state) ? state : air(world);
    }

    // ---- Shared helpers for block classes --------------------------------------------------------

    /** Collision boxes of the block at a position, in world coordinates. */
    public static List<AxisAlignedBB> collisionBoxesAt(IBlockAccess world, int x, int y, int z) {
        BlockState stored = world.getState(x, y, z);
        if (!stored.block().isSolid()) return List.of();
        ActualState state = stored.block().actualState(world, x, y, z, stored);
        List<AxisAlignedBB> result = new ArrayList<>();
        for (AxisAlignedBB box : stored.block().collisionBoxes(state, world, x, y, z)) result.add(box.offset(x, y, z));
        return result;
    }

    /** "Solid top surface": opaque cubes, upper slabs, upside-down stairs, hoppers and full snow. */
    public static boolean hasSolidTop(BlockState state) {
        Block block = state.block();
        if (block.isOpaque()) return true;
        return (block instanceof BlockSlab || block instanceof BlockStairs) && state.get("half").equals("top")
            || block instanceof BlockHopper
            || block instanceof BlockSnowLayer && state.get("layers").equals("8");
    }

    protected static BlockState air(IBlockAccess world) { return BlockState.of(world.registry().air(), 0); }

    protected static BlockState neighbour(IBlockAccess world, int x, int y, int z, Direction side) {
        return world.getState(x + side.xOffset, y + side.yOffset, z + side.zOffset);
    }

    /** Wall-mounted blocks hang from the block behind them, opposite their facing. */
    protected static boolean attachedTo(IBlockAccess world, int x, int y, int z, Direction facing) {
        return world.getState(x - facing.xOffset, y - facing.yOffset, z - facing.zOffset).block().isNormalCube();
    }

    /** The first horizontal side (north, east, south, west) backed by a normal cube, or {@code null}. */
    protected static Direction firstSupportedSide(IBlockAccess world, int x, int y, int z) {
        for (Direction side : Direction.HORIZONTALS)
            if (attachedTo(world, x, y, z, side)) return side;
        return null;
    }

    /** A plate of the given thickness on the side a wall-attached block hangs from (the side opposite its facing). */
    protected static AxisAlignedBB against(Direction facing, double thickness, double minY, double maxY,
                                           double minSide, double maxSide) {
        return switch (facing) {
            case NORTH -> new AxisAlignedBB(minSide, minY, 1 - thickness, maxSide, maxY, 1);
            case SOUTH -> new AxisAlignedBB(minSide, minY, 0, maxSide, maxY, thickness);
            case WEST -> new AxisAlignedBB(1 - thickness, minY, minSide, 1, maxY, maxSide);
            case EAST -> new AxisAlignedBB(0, minY, minSide, thickness, maxY, maxSide);
            default -> new AxisAlignedBB(0, minY, 0, 1, maxY, 1);
        };
    }

    protected static Direction facing(BlockState state) { return Direction.fromName(state.get("facing")); }
    protected static Direction facing(ActualState state) { return Direction.fromName(state.get("facing")); }
    protected static String flag(boolean value) { return value ? "true" : "false"; }
    protected static boolean isTrue(ActualState state, String property) { return state.get(property).equals("true"); }

    @Override public String toString() { return name().toString(); }
}
