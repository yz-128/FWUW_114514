import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.HashMap;
import java.util.Map;

/**
 * The gun's "muzzle is against a wall" test must use collision shapes, not outline shapes.
 *
 * <p>Every plant in modern Minecraft is registered {@code noCollission()}: its collision shape is
 * empty while its outline is still a box (short grass 2..14 x 0..13 x 2..14). Using
 * {@code ClipContext.Block.OUTLINE} therefore made a gun pointed into grass lower itself, which
 * 1.7.10 did not do — its {@code rayTrace} passed {@code ignoreBlockWithoutBoundingBox = true}.
 *
 * <p>Exercises the real vanilla shapes and the real {@code BlockGetter.clip} traversal against an
 * in-memory block grid; Minecraft is bootstrapped but no game is started.
 */
public final class VerifyRestWallShape {
    private static int checks;

    private static void check(boolean value, String detail) {
        checks++;
        if (!value) throw new AssertionError(detail);
    }

    /** The world's block storage only; every shape query comes from the real block class. */
    static final class Grid implements BlockGetter {
        final Map<BlockPos, BlockState> blocks = new HashMap<>();
        Grid put(int x, int y, int z, Block block) {
            blocks.put(new BlockPos(x, y, z), block.defaultBlockState());
            return this;
        }
        @Override public BlockState getBlockState(BlockPos pos) {
            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public int getHeight() { return 384; }
        @Override public int getMinBuildHeight() { return -64; }
    }

    private static final Vec3 FROM = new Vec3(0.5, 0.5, 0.5);
    private static final Vec3 TO = new Vec3(0.5, 0.5, 4.5);

    /**
     * A ray through a block's own outline box, so a short plant (wheat at age 0 is 2/16 tall) is
     * actually crossed instead of being missed over the top.
     */
    private static HitResult.Type hit(Grid grid, ClipContext.Block mode, net.minecraft.world.phys.AABB box) {
        double y = (box.minY + box.maxY) / 2, z = (box.minZ + box.maxZ) / 2 + 2;
        Vec3 from = new Vec3(0.5, y, z - 3.5), to = new Vec3(0.5, y, z + 0.5);
        return grid.clip(new ClipContext(from, to, mode, ClipContext.Fluid.NONE,
                CollisionContext.empty())).getType();
    }

    /** Both shape modes against one block: outline must see it, collision must not (or vice versa). */
    private static void plant(String name, Block block) {
        Grid grid = new Grid().put(0, 0, 2, block);
        var state = block.defaultBlockState();
        var pos = new BlockPos(0, 0, 2);
        var collision = state.getCollisionShape(grid, pos, CollisionContext.empty());
        var outline = state.getShape(grid, pos, CollisionContext.empty());
        check(collision.isEmpty(), name + " has an empty collision shape (noCollission)");
        check(!outline.isEmpty(), name + " still has a non-empty outline shape");
        var box = outline.bounds().move(0, 0, 2);
        check(hit(grid, ClipContext.Block.OUTLINE, box) == HitResult.Type.BLOCK,
                "OUTLINE hits " + name + " (the bug: this lowered the gun)");
        check(hit(grid, ClipContext.Block.COLLIDER, box) == HitResult.Type.MISS,
                "COLLIDER passes through " + name + " (matches 1.7.10 ignoreBlockWithoutBoundingBox)");
    }

    /** A solid block must stop the ray in both modes, so the fix cannot disable the feature. */
    private static void solid(String name, Block block) {
        Grid grid = new Grid().put(0, 0, 2, block);
        var box = new net.minecraft.world.phys.AABB(0, 0, 0, 1, 1, 1);
        check(hit(grid, ClipContext.Block.COLLIDER, box) == HitResult.Type.BLOCK,
                "COLLIDER still stops at " + name);
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        plant("short grass", Blocks.SHORT_GRASS);
        plant("tall grass", Blocks.TALL_GRASS);
        plant("fern", Blocks.FERN);
        plant("large fern", Blocks.LARGE_FERN);
        plant("poppy", Blocks.POPPY);
        plant("dandelion", Blocks.DANDELION);
        plant("wheat", Blocks.WHEAT);
        plant("sugar cane", Blocks.SUGAR_CANE);
        plant("vine", Blocks.VINE);
        plant("sea grass", Blocks.SEAGRASS);

        solid("stone", Blocks.STONE);
        solid("dirt", Blocks.DIRT);
        solid("oak planks", Blocks.OAK_PLANKS);
        solid("oak fence", Blocks.OAK_FENCE);
        solid("glass pane", Blocks.GLASS_PANE);
        solid("slab", Blocks.STONE_SLAB);

        System.out.println("PASS: " + checks
                + " assertions; real vanilla plant collision/outline shapes and native clip traversal."
                + " Plants are ignored by COLLIDER (as 1.7.10's ignoreBlockWithoutBoundingBox did)"
                + " while solid blocks still stop the muzzle. Bootstrap only; no game started.");
    }
}
