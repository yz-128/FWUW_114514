package com.flansmod.client.render;

import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.client.model.loader.ModelGeometry.Model;
import com.flansmod.common.types.AttachmentType;
import com.flansmod.common.types.GunType;
import com.flansmod.common.types.ModifiableType;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;

/** EFT core RenderGun's flash selection and attachment-local muzzle placement. */
public final class FmumMuzzlePose {
	private FmumMuzzlePose() { }
	public record Flash(String model,String texture,float scale,float fade,int textures,
			Matrix4f mount,boolean freeRotate,String anchor) {
		public String texture(int variant) { return texture==null?null:texture+"_"+Math.floorMod(variant,Math.max(1,textures)); }
		public Matrix4f pose(float roll) {
			var pose=new Matrix4f(mount);
			if(freeRotate)pose.rotateX(FmumActionPose.rad(roll));
			return pose.scale(scale);
		}
	}
	private record Mount(AttachmentType type,Matrix4f pose) { }
	public static Flash flash(GunType gun,Model model) {
		if(gun.itsType==GunType.GunItsType.MAG)return null;
		var before=new ArrayList<Mount>();var after=new ArrayList<Mount>();
		collect(gun,gun.defaultAttachments,new Matrix4f(),before,after,0);
		float fade=gun.muzzleFlashVanishConst;
		AttachmentType selected=null;
		// setGunStates accumulates every attachment's fade, then its children; last barrel/muzzle wins.
		for(var node:before) {
			fade*=node.type.muzzleFlashVanishConst;
			if(muzzle(node.type))selected=node.type;
		}
		String reference=selected==null || selected.flashModelName==null?gun.flashModel:selected.flashModelName;
		String texture=selected==null || selected.flashModelName==null || selected.flashTextureName==null
				?gun.flashTexture:selected.flashTextureName;
		int count=selected==null || selected.numFlashTexture<1?gun.numFlashTexture:selected.numFlashTexture;
		float[] gunPoint=FmumActionPose.vector(model,"muzzleFlashPoint");
		Matrix4f position=new Matrix4f().translate(gunPoint[0],gunPoint[1],gunPoint[2]);
		boolean free=FmumActionPose.scalar(model,"flashFreeRotate",1F)!=0;
		String anchor=gun.shortName;
		// renderAttachments visits children first; the first outer muzzle/barrel consumes renderFlash.
		for(var node:after)if(muzzle(node.type)) {
			var rig=ModelCache.rigFor(node.type,node.type.modelScale);
			var at=rig==null?null:rig.animation;
			float[] point=FmumActionPose.vector(at,"muzzleFlashPoint");
			position=new Matrix4f(node.pose).translate(point[0]*node.type.modelScale,point[1]*node.type.modelScale,point[2]*node.type.modelScale);
			free=FmumActionPose.scalar(at,"flashFreeRotate",1F)!=0;anchor=node.type.shortName;break;
		}
		return new Flash(reference,texture,gun.muzzleFlashScale,fade,Math.max(1,count),position,free,anchor);
	}
	public static Matrix4f smoke(GunType gun,Model model,boolean chamber) {
		if(chamber) {
			float[] point=FmumActionPose.vector(model,"chamberSmokeOri");
			return new Matrix4f().translate(point[0],point[1],point[2]);
		}
		var before=new ArrayList<Mount>();var after=new ArrayList<Mount>();
		collect(gun,gun.defaultAttachments,new Matrix4f(),before,after,0);
		Mount selected=null;
		for(var node:before)if(node.type.itsType.equalsIgnoreCase("muzzle")){selected=node;break;}
		if(selected==null)for(var node:before)if(node.type.itsType.equalsIgnoreCase("barrel")){selected=node;break;}
		float distance=selected==null?gun.muzzleSmoke.at:selected.pose.m30()+selected.type.muzzleSmoke.at;
		float[] barrel=FmumActionPose.vector(model,"barrelOrigin");
		return new Matrix4f().translate(barrel[0]+distance+gun.gunLengthOffset,barrel[1],barrel[2]);
	}
	private static boolean muzzle(AttachmentType at) {
		return at.itsType.equalsIgnoreCase("muzzle") || at.itsType.equalsIgnoreCase("barrel");
	}
	/** Strip hand/model scale, retaining the actual barrel's world direction (RY then RZ then RX). */
	public static Matrix4f smokeOrigin(Matrix4f point,float roll) {
		var p=point.transformPosition(new org.joml.Vector3f());
		var d=point.transformDirection(new org.joml.Vector3f(1,0,0)).normalize();
		return new Matrix4f().translation(p).rotateY((float)Math.atan2(-d.z,d.x))
				.rotateZ((float)Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))).rotateX(FmumActionPose.rad(roll));
	}
	private static void collect(ModifiableType owner,List<List<ModifiableType.DefaultAttachment>> groups,Matrix4f parent,
			List<Mount> before,List<Mount> after,int depth) {
		for(int i=groups.size();--i>=0;)nodes(owner,groups.get(i),parent,before,after,depth);
	}
	private static void nodes(ModifiableType owner,List<ModifiableType.DefaultAttachment> nodes,Matrix4f parent,
			List<Mount> before,List<Mount> after,int depth) {
		if(depth>8)return;
		for(int i=nodes.size();--i>=0;) {
			var node=nodes.get(i);var at=AttachmentType.getAttachment(node.name);
			if(at==null || node.slot<0 || node.slot>=owner.slots.length)continue;
			var slot=owner.slots[node.slot];
			var pose=new Matrix4f(parent).translate(slot.x+node.pos,slot.y,slot.z).rotateX(FmumActionPose.rad(slot.rotate));
			var mount=new Mount(at,pose);before.add(mount);
			nodes(at,node.children,pose,before,after,depth+1);after.add(mount);
		}
	}
}
