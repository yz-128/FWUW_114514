package com.flansmod.client.render;

import com.flansmod.common.types.AttachmentType;

/**
 * The world FOV the current frame is rendered with, plus the optic zoom applied to it.
 *
 * <p>Older code copied a rectangle of the finished world image into the ocular. That cannot gain
 * detail: at 4x it sampled about a fifth of the screen width and stretched it back over the lens,
 * so a 1080p frame offered roughly 380 px behind a 700 px lens. Both modern implementations in this
 * workspace magnify the world pass itself — TACZ through {@code ViewportEvent.ComputeFov}, FMU 2.0
 * through {@code ModClient.cameraFov} — and 1.7.10 re-rendered the world at the magnified FOV into a
 * 256x256 target. Magnifying the world pass keeps full screen resolution.
 *
 * <p>The hand pass keeps the undivided FOV, so the weapon and optic keep their physical size while
 * only the world beyond them is magnified.
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
