package com.flansmod.client.render;

import com.flansmod.client.model.loader.BakedRig;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** Preserve the actual hand projection and flame mesh through shader composition. */
public final class FmumShaderFlash {
	private FmumShaderFlash() { }
	// Post-composite colour is no longer tone-mapped by the shader pack. Keep the authored
	// muzzle anchor and mesh, but reduce its footprint and white core in this pass only.
	public static final float SIZE=.65F,OPACITY=.65F,RED=.92F,GREEN=.78F,BLUE=.52F;
	public record Frame(BakedRig rig,ResourceLocation texture,Matrix4f projection,Matrix4f viewPose,
			float alpha,FmumMuzzleGlow.Frame glow) { }
	public static Frame capture(BakedRig rig,ResourceLocation texture,Matrix4f projection,
			Matrix4f shaderView,Matrix4f gunPose,FmumMuzzlePose.Flash flash,float roll,float alpha) {
		var glow=FmumMuzzleGlow.frame(gunPose,shaderView,flash,alpha);
		if(rig==null || rig.quads.isEmpty() || texture==null || glow==null)return null;
		var viewPose=new Matrix4f(shaderView).mul(gunPose).mul(flash.pose(roll)).scale(SIZE);
		var viewGlow=new FmumMuzzleGlow.Frame(new Matrix4f(shaderView).mul(glow.pose()),glow.radius()*SIZE,glow.intensity()*.35F);
		return new Frame(rig,texture,new Matrix4f(projection),viewPose,Math.clamp(alpha,0F,1F)*OPACITY,viewGlow);
	}
	public static void emit(VertexConsumer buffer,Frame frame) {
		for(var quad:frame.rig.quads)for(int i=0;i<4;i++) {
			var point=quad.positions()[i];var uv=quad.uvs()[i];
			buffer.addVertex(frame.viewPose,point[0],point[1],point[2]).setUv(uv[0],uv[1])
						.setColor(RED,GREEN,BLUE,frame.alpha);
		}
	}
	/** One snapshot per hand per rendered frame; shader solid/translucent passes cannot duplicate it. */
	public static final class Frames {
		private final Frame[] hands=new Frame[2];
		public void put(boolean offHand,Frame frame) { hands[offHand?1:0]=frame; }
		public void clear() { hands[0]=hands[1]=null; }
		public List<Frame> take() {
			var result=new ArrayList<Frame>(2);
			for(var frame:hands)if(frame!=null)result.add(frame);
			clear();return result;
		}
	}
}
