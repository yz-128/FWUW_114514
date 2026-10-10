package com.flansmod.common.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The requested soft-block pass-through policy, shared by every fired round. */
public final class BulletBlockTrace {
	public static final TagKey<Block> PASS_THROUGH = TagKey.create(Registries.BLOCK,
			ResourceLocation.fromNamespaceAndPath("flansmod", "bullet_pass_through"));
	private BulletBlockTrace() { }

	public static BlockHitResult clip(BlockGetter world, Vec3 from, Vec3 to, Entity projectile) {
		return world.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile) {
			@Override
			public VoxelShape getBlockShape(BlockState state, BlockGetter level, BlockPos pos) {
				return state.is(PASS_THROUGH) ? Shapes.empty() : super.getBlockShape(state, level, pos);
			}
		});
	}
}
