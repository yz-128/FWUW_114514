package com.flansmod.client.model;

import com.flansmod.client.model.loader.ModelGeometry.Model;
import com.flansmod.client.render.FmumActionPose;
import com.flansmod.client.render.FmumMuzzlePose;
import com.flansmod.common.types.BulletType;
import java.util.Random;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.Direction;

/**
 * One ejected case in the world.
 *
 * <p>1.7.10's {@code GunCase} was pure ballistics — gravity, drag and a spin, with no collision at
 * all, so a case fell straight through the ground and vanished when its timer ran out. This adds
 * what the original lacked: the case stops at terrain, bounces, loses its spin and comes to rest,
 * and it stays on the ground considerably longer than the model's own {@code caseTime}.
 */
public final class FmumCasing {
	/** Per-tick gravity, exactly as 1.7.10 wrote it. */
	private static final float GRAVITY = -1.96F / 160F;
	/** Speed kept each tick in flight. */
	private static final float AIR_DRAG = 0.99F;
	/** Speed kept each tick while resting on a surface. */
	private static final float GROUND_FRICTION = 0.55F;
	/** Spin kept each tick while resting. */
	private static final float GROUND_SPIN_FRICTION = 0.6F;
	/** Fraction of the impact speed returned by a bounce. */
	private static final float BOUNCE = 0.32F;
	/** Below this speed a grounded case is treated as stopped. */
	private static final float SLEEP_SPEED = 0.004F;
	/** Half-extent used when testing terrain against the case. */
	private static final float RADIUS = 0.02F;
	/**
	 * How much longer than the model's {@code caseTime} a case stays in the world.
	 *
	 * <p>The packs all author 60 ticks (3 seconds), which is just long enough to watch the case
	 * fly. Multiplying rather than replacing keeps a pack that asks for a shorter or longer case
	 * proportionally longer too.
	 */
	public static final float LIFETIME_MULTIPLIER = 4F;

	public final BulletType type;
	public final Vector3f position=new Vector3f(),lastPosition=new Vector3f(),velocity,center,axis;
	public final Matrix4f rotation;
	public final int lifetime;
	/** The launch speed, before drag; kept so the decay can be measured against it. */
	public final float launchSpeed;
	public float spin,lastSpin,spinVelocity;
	public int age;
	/** True while the case is touching terrain, which switches it to ground friction. */
	public boolean grounded;
	/** True once it has stopped completely; it then keeps its resting pose until it expires. */
	private boolean settled;
	public record Landing(BlockPos block,Vec3 point,BlockState surface,float speed) { }
	private Landing landing;
	private int landingSounds,lastLandingSound=-100;
	/** Consumed once by the client tick, never by a render frame. */
	public Landing takeLanding() {var result=landing;landing=null;return result;}
	/** Ignore resting gravity contacts and small/repeated bounces. At most three audible landings. */
	public boolean audibleLanding(Landing hit) {
		if(hit==null||!Float.isFinite(hit.speed())||hit.speed()<.04F||age-lastLandingSound<4||landingSounds>=3)return false;
		lastLandingSound=age;landingSounds++;return true;
	}

	public FmumCasing(BulletType type,Model model,Matrix4f gun,boolean manual,long seed,float roll) {
		this.type=type;
		var random=new Random(seed);
		var direction=new Matrix4f(gun).normalize3x3();
		// Actual EFT RenderGun overwrites preSet's WC vectors with these normal vectors,
		// even during manual charge. Only rotVelocityWC survives that overwrite.
		velocity=direction.transformDirection(sample(model,"caseEjectVelocity","caseEjectRandV",
				new float[]{-.025F,.025F,.25F},new float[]{.075F,.05625F,.03125F},random));
		axis=direction.transformDirection(sample(model,"caseEjectAngularV","caseEjectRandAV",
				new float[]{-.3F,-.7F,-.3F},new float[]{.6F,-.8F,.6F},random));
		center=direction.transformDirection(new Vector3f(type.bulletRotCenter).mul(type.bulletScale/16F));
		rotation=FmumMuzzlePose.smokeOrigin(gun,roll).setTranslation(0,0,0);
		spinVelocity=FmumActionPose.scalar(model,manual?"rotVelocityWC":"rotVelocity",manual?30F:55F);
		lifetime=Math.max(1,(int)(FmumActionPose.scalar(model,"caseTime",30F)*LIFETIME_MULTIPLIER));
		launchSpeed=velocity.length();
	}
	private static Vector3f sample(Model model,String base,String spread,float[] fallback,float[] range,Random random) {
		float[] a=model==null?fallback:model.fmumPose.getOrDefault(base,fallback);
		float[] b=model==null?range:model.fmumPose.getOrDefault(spread,range);
		return new Vector3f(a[0]+b[0]*random.nextFloat(),a[1]+b[1]*random.nextFloat(),a[2]+b[2]*random.nextFloat());
	}
	public static Vector3f origin(Model model,Matrix4f gun) {
		return gun.transformPosition(new Vector3f(FmumActionPose.vector(model,"caseOrigin")));
	}

	/**
	 * Advances one tick.
	 *
	 * @param level  the client level, or null when there is no world to collide with
	 * @param worldX world-space X of {@link #position}'s origin
	 * @param worldY world-space Y of {@link #position}'s origin
	 * @param worldZ world-space Z of {@link #position}'s origin
	 */
	public void tick(BlockGetter level,double worldX,double worldY,double worldZ) {
		if(!alive())return;
		landing=null;
		age++;lastPosition.set(position);lastSpin=spin;
		if(settled)return;

		velocity.y+=GRAVITY;velocity.mul(AIR_DRAG);

		float dx=velocity.x,dy=velocity.y,dz=velocity.z;
		boolean onGround=false;
		if(level!=null) {
			// A case ejected into a wall starts inside a block. Testing each axis then rejects
			// every direction at once and welds it into the wall for its whole lifetime, so a
			// case that is already embedded ignores collision and simply drifts back out.
			boolean embedded=inside(level,worldX+position.x,worldY+position.y,worldZ+position.z);
			if(!embedded) {
				// One axis at a time, so a case sliding along a wall keeps the other component.
				var floor=floor(level,worldX+position.x,worldY+position.y,worldZ+position.z,dy);
				if(floor!=null) {
					onGround=true;
					landing=new Landing(floor.getBlockPos(),floor.getLocation(),level.getBlockState(floor.getBlockPos()),-velocity.y);
					dy=(float)(floor.getLocation().y+RADIUS-worldY-position.y);
					velocity.y=-velocity.y*BOUNCE;
					if(Math.abs(velocity.y)<SLEEP_SPEED)velocity.y=0F;
				} else if(dy>0F&&solid(level,worldX+position.x,worldY+position.y+dy,worldZ+position.z)) {
					dy=0F;velocity.y=-velocity.y*BOUNCE;
					if(Math.abs(velocity.y)<SLEEP_SPEED)velocity.y=0F;
				}
				if(solid(level,worldX+position.x+dx,worldY+position.y+dy,worldZ+position.z)) {
					dx=0F;velocity.x=-velocity.x*BOUNCE;
				}
				if(solid(level,worldX+position.x+dx,worldY+position.y+dy,worldZ+position.z+dz)) {
					dz=0F;velocity.z=-velocity.z*BOUNCE;
				}
			}
		}
		position.add(dx,dy,dz);
		grounded=onGround;

		if(onGround) {
			velocity.x*=GROUND_FRICTION;velocity.z*=GROUND_FRICTION;
			spinVelocity*=GROUND_SPIN_FRICTION;
			if(velocity.lengthSquared()<SLEEP_SPEED*SLEEP_SPEED&&Math.abs(spinVelocity)<0.5F) {
				velocity.set(0F,0F,0F);spinVelocity=0F;settled=true;
			}
		} else spinVelocity*=AIR_DRAG;
		spin+=spinVelocity;
	}

	/** Sweep the foot to avoid tunnelling through carpet/slabs during a fast fall. */
	private static BlockHitResult floor(BlockGetter level,double x,double y,double z,float dy) {
		if(dy>=0F)return null;
		var from=new Vec3(x,y-RADIUS+.0001,z);var to=new Vec3(x,y+dy-RADIUS,z);
		return BlockGetter.traverseBlocks(from,to,Boolean.TRUE,(ignored,pos)->{
			var hit=level.getBlockState(pos).getCollisionShape(level,pos).clip(from,to,pos);
			return hit!=null&&!hit.isInside()&&hit.getDirection()==Direction.UP
					?new BlockHitResult(hit.getLocation(),hit.getDirection(),pos.immutable(),false):null;
		},ignored->null);
	}

	/** True when the point is inside a block's collision shape, with a little slack for the case. */
	private static boolean solid(BlockGetter level,double x,double y,double z) {
		var pos=net.minecraft.core.BlockPos.containing(x,y,z);
		var state=level.getBlockState(pos);
		if(state.isAir())return false;
		var shape=state.getCollisionShape(level,pos);
		if(shape.isEmpty())return false;
		return shape.toAabbs().stream().anyMatch(box->box.move(pos).inflate(RADIUS).contains(x,y,z));
	}

	/** True when the point is inside a solid block at all, without the extra radius. */
	private static boolean inside(BlockGetter level,double x,double y,double z) {
		var pos=net.minecraft.core.BlockPos.containing(x,y,z);
		var state=level.getBlockState(pos);
		if(state.isAir())return false;
		var shape=state.getCollisionShape(level,pos);
		return shape.toAabbs().stream().anyMatch(box->box.move(pos).contains(x,y,z));
	}

	/**
	 * True when a landing contact sits under the water line.
	 *
	 * <p>Both the block the case landed on and the block above it are checked, and each column
	 * only counts as water while the contact point is below that column's fluid surface. That
	 * keeps a case landing on a waterlogged slab inside a pond silent, while a dry top face that
	 * clears the water line — a waterlogged fence top, the upper half of a waterlogged stair —
	 * still rings.
	 */
	public static boolean submerged(BlockGetter level,Vec3 contact) {
		var below=net.minecraft.core.BlockPos.containing(contact.x,contact.y-1.0E-4,contact.z);
		var above=net.minecraft.core.BlockPos.containing(contact.x,contact.y+1.0E-3,contact.z);
		return wet(level,below,contact)||wet(level,above,contact);
	}

	private static boolean wet(BlockGetter level,net.minecraft.core.BlockPos pos,Vec3 contact) {
		var fluid=level.getFluidState(pos);
		if(fluid.isEmpty()||!fluid.is(net.minecraft.tags.FluidTags.WATER))return false;
		return contact.y<pos.getY()+fluid.getHeight(level,pos)-1.0E-3;
	}

	public boolean alive() {return age<lifetime;}
	public Matrix4f pose(float partial) {
		float p=Math.clamp(partial,0F,1F);
		var location=new Vector3f(lastPosition).lerp(position,p).add(center);
		var result=new Matrix4f().translate(location);
		if(axis.lengthSquared()>1E-12F)result.rotate(FmumActionPose.rad(lastSpin+(spin-lastSpin)*p),new Vector3f(axis).normalize());
		return result.translate(-center.x,-center.y,-center.z).mul(rotation).scale(type.bulletScale);
	}
}
