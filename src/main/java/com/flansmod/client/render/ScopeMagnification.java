package com.flansmod.client.render;

import com.flansmod.common.types.AttachmentType;

/**
 * The undivided camera FOV and the FOV of the scope image. The ordinary world keeps its normal
 * field of view when the independent scope pass is available. Shader-pack compatibility keeps
 * the existing single-pass zoom rather than recursively entering a shader pipeline.
 */
public final class ScopeMagnification {
	private ScopeMagnification() { }
	private static double baseFov = 70;
	private static double effectiveFov = 70;

	/** Records the frame's FOV before and after optic zoom; called from the world camera event. */
	public static void frame(double base, double zoom) {
		if(!Double.isFinite(base) || base <= 0 || !Double.isFinite(zoom) || zoom < 1) return;
		baseFov = base;
		effectiveFov = base / zoom;
	}
	/** FOV before the optic zoom, which is also the hand-pass FOV. */
	public static double baseFov() { return baseFov; }
	/** FOV the world pass is actually rendered with; the scope image is seen at this angle. */
	public static double scopeFov() { return effectiveFov; }
	/** SightCoor.getFOVZoom: the measured eye distance is part of the native camera contract. */
	public static double nativeFov(double base,AttachmentType type,int step,float distance) {
		if(type==null || !Double.isFinite(base) || !Float.isFinite(distance) || distance<=0)return Double.NaN;
		int index=Math.clamp(step,0,Math.max(0,type.fovZoomLevels.length-1));
		float raw=type.fovZoomLevels.length==0?type.FOVZoomLevel:type.fovZoomLevels[index];
		return base/Math.max(.001F,raw*distance);
	}
	/** Centre-cropping a rectangular render to a square must preserve the square camera's FOV. */
	public static double squareRenderFov(double squareFov,int width,int height) {
		if(width<=0 || height<=0)return Double.NaN;
		return Math.toDegrees(2*Math.atan(Math.tan(Math.toRadians(squareFov)/2)*height/Math.min(width,height)));
	}

	/** FOV actually retained by a centre-square crop of an existing rectangular world image.
	 * This is the inverse of squareRenderFov; a portrait crop removes part of the vertical view. */
	public static double squareImageFov(double renderFov,int width,int height) {
		if(width<=0 || height<=0 || !Double.isFinite(renderFov) || renderFov<=0 || renderFov>=180)
			return Double.NaN;
		return Math.toDegrees(2*Math.atan(Math.tan(Math.toRadians(renderFov)/2)*Math.min(width,height)/height));
	}

	/** Only the secondary world camera uses optical power in the native dual-render path. */
	public static double worldZoom(double opticalPower,boolean hasScope,boolean dualRender,boolean scopePass) {
		return hasScope && dualRender && !scopePass ? 1 : opticalPower;
	}

	/**
	 * Optical magnification the pack declares for a step.
	 *
	 * <p>{@code ZoomLevel}'s numbers are ten times the magnification in these packs (PSO1 40 = 4x,
	 * NF7x 70 = 7x). 1.7.10 multiplied them by the measured pupil distance because it re-rendered
	 * the world from the ocular; that product lands near the same value and is not the optical power
	 * itself.
	 */
	public static double zoom(AttachmentType type, int step) {
		return Math.max(1, type.zoomDivisor(step));
	}
}
