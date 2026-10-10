package com.flansmod.client.render;

import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.client.model.loader.ModelGeometry.Model;
import com.flansmod.common.types.AttachmentType;
import com.flansmod.common.types.GunType;
import com.flansmod.common.types.ModifiableType;
import java.util.List;
import org.joml.Matrix4f;

/** FMUM's attachment hand priority and gun-local holding transform, for EFT default trees. */
public final class FmumHoldingArmPose {
	private FmumHoldingArmPose() { }
	public record Mount(AttachmentType type,float x,float y,float z,float roll,int priority) { }
	public static Mount forGun(GunType gun,boolean left) {
		Mount result=null;
		for(int i=gun.defaultAttachments.size();--i>=0;)
			result=collect(gun,gun.defaultAttachments.get(i),left,0,0,0,0,result,0);
		return result;
	}
	private static Mount collect(ModifiableType owner,List<ModifiableType.DefaultAttachment> nodes,boolean left,
			float x,float y,float z,float roll,Mount best,int depth) {
		if(depth>8)return best;
		float sin=(float)Math.sin(Math.toRadians(roll)),cos=(float)Math.cos(Math.toRadians(roll));
		for(int i=nodes.size();--i>=0;) {
			var node=nodes.get(i);var type=AttachmentType.getAttachment(node.name);
			if(type==null || node.slot<0 || node.slot>=owner.slots.length)continue;
			var slot=owner.slots[node.slot];
			float nx=x+slot.x+node.pos,ny=y+slot.y*cos-slot.z*sin,nz=z+slot.y*sin+slot.z*cos,nr=roll+slot.rotate;
			// Original renderer visits children before claiming a hand; ties retain that winner.
			best=collect(type,node.children,left,nx,ny,nz,nr,best,depth+1);
			int priority=left?type.leftHandPriority:type.rightHandPriority;
			if(priority>(best==null?-128:best.priority()))best=new Mount(type,nx,ny,nz,nr,priority);
		}
		return best;
	}
	public static Matrix4f arm(Matrix4f pose,GunType gun,Model model,boolean left) {
		String hand=left?"left":"right";
		var mount=gun==null?null:forGun(gun,left);
		if(mount==null) {
			FmumActionPose.rotate(FmumActionPose.translate(pose,FmumActionPose.vector(model,hand+"ArmPos"),1F),FmumActionPose.vector(model,hand+"ArmRot"));
		} else {
			pose.translate(mount.x(),mount.y(),mount.z()).rotateX(FmumActionPose.rad(mount.roll()));
			var rig=ModelCache.rigFor(mount.type(),mount.type().modelScale);
			var attachment=rig==null?null:rig.animation;
			// EFT declares no TwoVector3f hand-guide maps. Its core therefore uses these
			// attachment vectors directly, outside the attachment's geometry scale.
			FmumActionPose.rotate(FmumActionPose.translate(pose,FmumActionPose.vector(attachment,"armTranslate"),1F),FmumActionPose.vector(attachment,"armRotate"));
		}
		float[] scale=model==null?null:model.fmumPose.get(hand+"ArmScale");
		return scale==null?pose:pose.scale(scale[0],scale[1],scale[2]);
	}
}
