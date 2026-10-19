package com.flansmod.client.render;

import com.flansmod.client.model.loader.ModelGeometry.Model;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;

/** EFT 1.1 RenderGun.renderBulletInMag: pick the alternating seating array before model scale. */
public final class FmumBulletPose {
	private FmumBulletPose() { }
	public record Seat(int ammunitionIndex,Matrix4f pose) { }
	/** FMUM uses the round count, saturated at the last authored follower position. */
	public static Matrix4f follower(Model model,int rounds) {
		if(model==null) return null;
		float[][] positions=model.fmumVectorArrays.get("magFollowerAttachPos");
		if(positions==null || positions.length==0) return null;
		int index=Math.clamp(rounds,0,positions.length-1);
		Matrix4f pose=FmumActionPose.translate(new Matrix4f(),positions[index],1F);
		float[][] rotations=model.fmumVectorArrays.get("magFollowerAttachRot");
		if(rotations!=null && index<rotations.length) FmumActionPose.rotate(pose,rotations[index]);
		return pose;
	}
	public static List<Seat> seats(Model model,int rounds) {
		List<Seat> result=new ArrayList<>();
		if(model==null || rounds<=0) return result;
		int first=rounds-1;
		String side=(first&1)==1?"2":"1";
		float[][] positions=model.fmumVectorArrays.get("bulletAttachPoint"+side);
		float[][] rotations=model.fmumVectorArrays.get("bulletAttachRotate"+side);
		if(positions==null) return result;
		for(int i=Math.min(rounds,positions.length)-1;i>=0;i--) {
			Matrix4f pose=FmumActionPose.translate(new Matrix4f(),positions[i],1F);
			if(rotations!=null && i<rotations.length) FmumActionPose.rotate(pose,rotations[i]);
			result.add(new Seat(first-i,pose));
		}
		return result;
	}
}
