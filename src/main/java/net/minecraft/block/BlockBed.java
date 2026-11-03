package net.minecraft.block;

import net.minecraft.util.Direction;
import net.minecraft.world.IBlockAccess;

import java.util.List;

/** Beds: two halves that need each other; the head lies one block along the facing from the foot. */
public class BlockBed extends Block {
    public BlockBed(Properties properties) { super(properties); }

    /**
     * ItemBed.onItemUse: only on the top of a block; the foot goes in the
     * block above (or in place of a replaceable one), the head one block
     * further along the player's facing, both into air or replaceable blocks
     * with a solid top under each.
     */
    @Override
    public List<BlockPlacement.Placement> placements(IBlockAccess world, int x, int y, int z, int itemMetadata,
                                                     Direction face, BlockPlacement.Context context) {
        if (context.face() != Direction.UP) return List.of();
        Direction facing = context.playerFacing();
        int headX = x + facing.xOffset, headZ = z + facing.zOffset;
        if (!BlockPlacement.isReplaceable(world.getState(headX, y, headZ))) return List.of();
        if (!hasSolidTop(world.getState(x, y - 1, z)) || !hasSolidTop(world.getState(headX, y - 1, headZ))) return List.of();
        BlockState foot = BlockState.of(this, 0).with("facing", facing.getName()).with("occupied", "false").with("part", "foot");
        return List.of(new BlockPlacement.Placement(x, y, z, foot),
            new BlockPlacement.Placement(headX, y, headZ, foot.with("part", "head")));
    }

    @Override
    public boolean canStay(IBlockAccess world, int x, int y, int z, BlockState state) {
        Direction facing = facing(state);
        boolean head = state.get("part").equals("head");
        int sign = head ? -1 : 1;
        BlockState other = world.getState(x + facing.xOffset * sign, y, z + facing.zOffset * sign);
        return other.block() == this && other.get("part").equals(head ? "foot" : "head");
    }

    /** The head half of the bed at (x, y, z) (BlockBed.onBlockActivated moves to it from the foot), or null if it isn't a bed. */
    public static int[] head(IBlockAccess world, int x, int y, int z) {
        BlockState state = world.getState(x, y, z);
        if (!(state.block() instanceof BlockBed)) return null;
        if (state.get("part").equals("head")) return new int[]{x, y, z};
        Direction facing = facing(state);
        return new int[]{x + facing.xOffset, y, z + facing.zOffset};
    }

    /**
     * BlockBed.getSafeExitLocation: the first spot in the 3x3 around the head,
     * then around the foot, with a solid top below and room for a player, or null.
     */
    public static int[] exitLocation(IBlockAccess world, int x, int y, int z) {
        BlockState state = world.getState(x, y, z);
        if (!(state.block() instanceof BlockBed)) return null;
        Direction facing = facing(state);
        for (int l = 0; l <= 1; l++) {
            int x0 = x - facing.xOffset * l - 1, z0 = z - facing.zOffset * l - 1;
            for (int ex = x0; ex <= x0 + 2; ex++)
                for (int ez = z0; ez <= z0 + 2; ez++)
                    if (hasSolidTop(world.getState(ex, y - 1, ez)) && !Liquids.isSolid(world.getState(ex, y, ez).block())
                        && !Liquids.isSolid(world.getState(ex, y + 1, ez).block()))
                        return new int[]{ex, y, ez};
        }
        return null;
    }

    /** Where a sleeper lies (EntityPlayer.trySleep): at the head, shifted toward the headboard, 0.6875 up. */
    public static double[] sleepPosition(IBlockAccess world, int x, int y, int z) {
        Direction facing = facing(world.getState(x, y, z));
        return new double[]{x + 0.5 + facing.xOffset * 0.4, y + 0.6875, z + 0.5 + facing.zOffset * 0.4};
    }

    /** The way the sleeper looks: from the head toward the foot. */
    public static Direction footward(IBlockAccess world, int x, int y, int z) {
        return facing(world.getState(x, y, z)).opposite();
    }

    /**
     * !World.isDaytime: the sky light subtracted (World.calculateSkylightSubtracted,
     * darkened 5/16 by rain and again by thunder) reaches 4, so a thunderstorm counts as night.
     */
    public static boolean canSleepNow(long worldTime, boolean raining, boolean thundering) {
        float f = 1f - ((float) Math.cos(net.minecraft.client.renderer.SkyRenderer.celestialAngle(worldTime, 1f) * Math.PI * 2) * 2f + 0.5f);
        f = 1f - Math.max(0f, Math.min(1f, f));
        if (raining) f *= 1f - 5f / 16f;
        if (raining && thundering) f *= 1f - 5f / 16f;
        return (int) ((1f - f) * 11f) >= 4;
    }
}
