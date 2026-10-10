package com.flansmod.client.render;

import com.flansmod.client.model.loader.ModelGeometry.Model;
import com.flansmod.common.GunActionState;
import com.flansmod.common.GunReloadState.Frame;
import com.flansmod.common.GunReloadState.Step;
import org.joml.Matrix4f;

/** FMUM reload magazine/hand transforms, independent of the old four-phase clip animation. */
public final class FmumReloadPose {
	private FmumReloadPose() { }
	public static Matrix4f magazine(Matrix4f pose,Model model,Frame reload,GunActionState.Frame action) {
		if(reload.step()!=Step.UNLOAD_MAG && reload.step()!=Step.LOAD_MAG)
			return FmumActionPose.magazine(pose,model,action);
		float p=reload.progress();
		float[] pos=FmumActionPose.vector(model,"magAttachPoint").clone(),rot=FmumActionPose.vector(model,"magAttachRotate").clone();
		float[] trans=FmumActionPose.vector(model,"magTranslate"),rotate=FmumActionPose.vector(model,"magRotate");
		for(int i=0;i<3;i++) {pos[i]+=trans[i]*p;rot[i]+=rotate[i]*p;}
		return FmumActionPose.rotate(FmumActionPose.translate(pose,pos,1F),rot);
	}
	public static FmumActionPose.Charge charge(Model model,Frame reload,GunActionState.Frame action) {
		return reload.step()==Step.CHARGE?FmumActionPose.chargeProgress(model,reload.progress()):FmumActionPose.charge(model,action);
	}
	public static float slide(Model model,Frame reload,GunActionState.Frame action,float recoil,boolean caught) {
		if(reload.step()==Step.CHARGE) return charge(model,reload,action).slide();
		if(reload.step()==Step.RELEASE) return -FmumActionPose.scalar(model,"slideLockDistance",0F)*(1F-reload.progress());
		return FmumActionPose.slide(model,action,recoil,caught);
	}
	public static Matrix4f arm(Matrix4f pose,Model model,boolean left,Frame reload,GunActionState.Frame action,float gunScale) {
		return arm(pose,model,left,reload,action,gunScale,null);
	}
	public static Matrix4f arm(Matrix4f pose,Model model,boolean left,Frame reload,GunActionState.Frame action,float gunScale,com.flansmod.common.types.GunType gun) {
		String hand=left?"left":"right",pos=null,rot=null;
		if(FmumActionPose.scalar(model,hand+"HandAmmo",0F)!=0F && (reload.step()==Step.UNLOAD_MAG || reload.step()==Step.LOAD_MAG)) {
			float p=reload.progress();
			float[] point=FmumActionPose.vector(model,"magAttachPoint"),translate=FmumActionPose.vector(model,"magTranslate");
			float[] rotation=FmumActionPose.vector(model,"magAttachRotate").clone(),rotate=FmumActionPose.vector(model,"magRotate");
			pose.translate(point[0]+translate[0]*p,point[1]+translate[1]*p,point[2]-translate[2]*p);
			rotation[0]-=rotate[0]*p;rotation[1]-=rotate[1]*p;rotation[2]+=rotate[2]*p;
			FmumActionPose.rotate(pose,rotation);FmumActionPose.translate(pose,point,-1F);
			pos=hand+"ArmAmmoPos";rot=hand+"ArmAmmoRot";
		} else if(reload.step()==Step.CHARGE && FmumActionPose.scalar(model,hand+"HandReloadingCharge",0F)!=0F) {
			FmumActionPose.pump2(pose,model,charge(model,reload,action),gunScale,true);
			pos=hand+"ArmReloadingChargePos";rot=hand+"ArmReloadingChargeRot";
		} else if(reload.step()==Step.RELEASE && FmumActionPose.scalar(model,hand+"HandReloadingRelease",0F)!=0F) {
			pos=hand+"ArmReloadingReleasePos";rot=hand+"ArmReloadingReleaseRot";
		}
		if(pos==null) return FmumActionPose.arm(pose,model,left,action,gunScale,gun);
		FmumActionPose.rotate(FmumActionPose.translate(pose,FmumActionPose.vector(model,pos),1F),FmumActionPose.vector(model,rot));
		float[] scale=FmumActionPose.vector(model,hand+"ArmScale");
		return pose.scale(scale[0],scale[1],scale[2]);
	}
}
