package com.flansmod.common.entity;

import com.flansmod.common.types.BulletType;
import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Requested cover attenuation, using the pack's penetration level and actual traversed thickness. */
public final class SoftBlockBallistics {
	private SoftBlockBallistics() { }
	private record Interval(double from, double to) { }

	/** The tooltip's penetration bracket, kept deterministic for a round's entire flight. */
	public static float penetrationLevel(BulletType round) {
		if(round == null) return 0F;
		float low = Float.isFinite(round.minPenetrationLevel) ? Math.max(0F, round.minPenetrationLevel) : 0F;
		float high = Float.isFinite(round.maxPenetrationLevel) ? Math.max(0F, round.maxPenetrationLevel) : 0F;
		return low * .5F + high * .5F;
	}

	/**
	 * Call with the segment ending at the first entity or hard-block hit, never the unbounded
	 * intended end. Integrating thickness makes this independent of tick speed and starting
	 * inside cover; a block split across successive ticks is not charged twice in full.
	 */
	public static float remainingDamage(BlockGetter world, Vec3 from, Vec3 to, Entity projectile,
			BulletType round, float damage) {
		if(damage <= 0F || from.distanceToSqr(to) < 1E-20D) return damage;
		var context = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile);
		double[] resistance = {0D};
		BlockGetter.traverseBlocks(from, to, context, (clip, pos) -> {
			var state = world.getBlockState(pos);
			if(state.is(BulletBlockTrace.PASS_THROUGH)) {
				var shape = clip.getBlockShape(state, world, pos);
				resistance[0] += thickness(shape, pos, from, to) * resistance(state);
			}
			return null;
		}, clip -> null);
		if(resistance[0] == 0D) return damage;
		double retained = Math.exp(-resistance[0] / (1D + penetrationLevel(round) / 30D));
		return (float)(damage * retained);
	}

	private static double resistance(BlockState state) {
		if(state.is(BlockTags.LEAVES)) return .06D;
		if(state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS) || state.is(BlockTags.BEDS)) return .18D;
		return .35D; // Wood and any additional blocks explicitly added to the pass-through tag.
	}
	private static double thickness(VoxelShape shape, BlockPos pos, Vec3 from, Vec3 to) {
		if(shape.isEmpty()) return 0D;
		var intervals = new ArrayList<Interval>();
		for(var box : shape.toAabbs()) {
			var interval = intersection(box.move(pos), from, to);
			if(interval != null) intervals.add(interval);
		}
		if(intervals.isEmpty()) return 0D;
		intervals.sort(Comparator.comparingDouble(Interval::from));
		double start = intervals.getFirst().from, end = intervals.getFirst().to, length = 0D;
		for(int i = 1; i < intervals.size(); i++) {
			var next = intervals.get(i);
			if(next.from <= end) end = Math.max(end, next.to);
			else { length += end - start; start = next.from; end = next.to; }
		}
		return (length + end - start) * from.distanceTo(to);
	}
	private static Interval intersection(AABB box, Vec3 from, Vec3 to) {
		double entry = 0D, exit = 1D;
		for(int axis = 0; axis < 3; axis++) {
			double origin = axis == 0 ? from.x : axis == 1 ? from.y : from.z;
			double delta = (axis == 0 ? to.x : axis == 1 ? to.y : to.z) - origin;
			double min = axis == 0 ? box.minX : axis == 1 ? box.minY : box.minZ;
			double max = axis == 0 ? box.maxX : axis == 1 ? box.maxY : box.maxZ;
			if(delta == 0D) {
				if(origin <= min || origin >= max) return null;
				continue;
			}
			double a = (min - origin) / delta, b = (max - origin) / delta;
			entry = Math.max(entry, Math.min(a, b)); exit = Math.min(exit, Math.max(a, b));
			if(exit <= entry) return null;
		}
		return new Interval(entry, exit);
	}
}
