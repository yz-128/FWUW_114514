package com.flansmod.client.render;

import com.flansmod.client.model.loader.ModelGeometry;
import org.joml.Vector3f;

/** Optical geometry in attachment coordinates, independent of Minecraft and OpenGL state. */
public final class FmumSightPicture {
	private FmumSightPicture() { }
	public static Vector3f vector(ModelGeometry.Model model,String key,float x,float y,float z) {
		float[] value=model.fmumPose.get(key);
		return value==null?new Vector3f(x,y,z):new Vector3f(value);
	}
	public static float pupilDistance(ModelGeometry.Model model,float scale,Vector3f eye) {
		return Math.max(.001F,vector(model,"pupilRefCenter",0,0,0).mul(scale).distance(eye));
	}
	/** The distant reticle point specifies a ray, never a model translation. */
	public static Vector3f intersection(Vector3f eye,Vector3f far,float[][] border,Vector3f normal) {
		if(border==null || border.length<3 || !eye.isFinite() || !far.isFinite() || !normal.isFinite())return null;
		Vector3f direction=new Vector3f(far).sub(eye);
		float denominator=direction.dot(normal);
		if(Math.abs(denominator)<1e-7F)return null;
		float t=new Vector3f(border[0]).sub(eye).dot(normal)/denominator;
		if(!Float.isFinite(t) || t<0)return null;
		Vector3f point=direction.mul(t).add(eye);
		if(!point.isFinite())return null;
		// 1.7.10 tested visibility with FlansMod.insideBorder, an even-odd ray crossing count over
		// the polygon projected onto the sight plane. A convex-only sign test is not equivalent:
		// the Crossline sight ships an eight-point non-convex outline with two reflex corners,
		// which a sign test rejects wholesale.
		return insideBorder(point,border,normal)?point:null;
	}

	/** Even-odd point-in-polygon on the sight plane, matching the original insideBorder. */
	static boolean insideBorder(Vector3f point,float[][] border,Vector3f normal) {
		Vector3f n=new Vector3f(normal).normalize();
		// Build a plane basis; seed with the axis least aligned with the normal for stability.
		Vector3f seed=Math.abs(n.y)<.9F?new Vector3f(0,1,0):new Vector3f(1,0,0);
		Vector3f u=new Vector3f(seed).cross(n).normalize();
		Vector3f v=new Vector3f(n).cross(u).normalize();
		float px=point.dot(u),py=point.dot(v);
		// A ray landing exactly on the outline counts as visible. 1.7.10 tested visibility with an
		// edge-intersection routine that included the endpoints; even-odd crossing alone leaves the
		// boundary undefined, which would blink a reticle that sits on the aperture rim.
		if(onOutline(px,py,border,u,v))return true;
		int crossings=0;
		for(int i=0,j=border.length-1;i<border.length;j=i++) {
			float ax=border[j][0]*u.x+border[j][1]*u.y+border[j][2]*u.z;
			float ay=border[j][0]*v.x+border[j][1]*v.y+border[j][2]*v.z;
			float bx=border[i][0]*u.x+border[i][1]*u.y+border[i][2]*u.z;
			float by=border[i][0]*v.x+border[i][1]*v.y+border[i][2]*v.z;
			if((ay>py)!=(by>py)) {
				float cross=ax+(py-ay)/(by-ay)*(bx-ax);
				if(px<cross)crossings++;
			}
		}
		return (crossings&1)==1;
	}

	/** True when the projected point lies on one of the outline's edges, within a small tolerance. */
	private static boolean onOutline(float px,float py,float[][] border,Vector3f u,Vector3f v) {
		final float tolerance=1e-4F;
		for(int i=0,j=border.length-1;i<border.length;j=i++) {
			float ax=border[j][0]*u.x+border[j][1]*u.y+border[j][2]*u.z;
			float ay=border[j][0]*v.x+border[j][1]*v.y+border[j][2]*v.z;
			float bx=border[i][0]*u.x+border[i][1]*u.y+border[i][2]*u.z;
			float by=border[i][0]*v.x+border[i][1]*v.y+border[i][2]*v.z;
			float dx=bx-ax,dy=by-ay,lengthSq=dx*dx+dy*dy;
			if(lengthSq<1e-12F)continue;
			float t=((px-ax)*dx+(py-ay)*dy)/lengthSq;
			if(t<0F || t>1F)continue;
			float distance=Math.abs((px-ax)*dy-(py-ay)*dx)/(float)Math.sqrt(lengthSq);
			if(distance<=tolerance)return true;
		}
		return false;
	}
	/** Apply a mouse-speed multiplier after vanilla's cubic sensitivity curve. */
	public static double sensitivity(double configured,double multiplier) {
		return (Math.cbrt(Math.pow(configured*.6+.2,3)*Math.clamp(multiplier,.001,100))-.2)/.6;
	}
}
