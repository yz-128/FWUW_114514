package com.flansmod.client.model;

import org.joml.Matrix4f;

/** Stationary world-space smoke with the actual EFT GunSmoke decay and scale formula. */
public final class FmumSmoke {
	public final String model,texture;
	public final Matrix4f origin;
	public final float scale,scaleEx,fade;
	public float alpha=1F;
	public int age;
	public FmumSmoke(String model,String texture,Matrix4f origin,float scale,float scaleEx,float fade) {
		this.model=model;this.texture=texture;this.origin=new Matrix4f(origin);
		this.scale=scale;this.scaleEx=scaleEx;this.fade=fade;
	}
	public void tick() { alpha*=fade;age++; }
	public float scale() { return scale+scaleEx*alpha; }
	public boolean alive() { return alpha>=1F/255F && age<200; }
	public Matrix4f pose() { return new Matrix4f(origin).scale(scale()); }
}
