package com.flansmod.client.render;

import com.flansmod.common.FlansMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Opt-in, scene-separated CPU/wall-clock instrumentation. These are not GPU timestamps. */
@EventBusSubscriber(modid = FlansMod.MODID, value = Dist.CLIENT)
public final class RenderPerf
{
	public static final boolean ENABLED = Boolean.getBoolean("flansmod.renderPerf");
	public static final int WINDOW_FRAMES = 240;
	private static final long WARMUP_NANOS = 1_000_000_000L;
	private static String requestedScene = "unscoped", activeScene = "unscoped";
	private static boolean frameOpen, warming = true;
	private static long frameStart, frameScopeNanos, frameQuads, sceneStart, windowStart;
	private static long frames, windowFrames, windowScopeNanos, windowRigQuads, windowCpuNanos;
	private static long totalScopeNanos, totalRigQuads;

	private RenderPerf() { }

	/** Labels apply at the next frame boundary, never halfway through a sample. */
	public static void scene(String scene)
	{
		if(ENABLED) requestedScene = scene;
	}

	/** Called by the scope frame hook, before the outer frame renders. */
	public static void frame()
	{
		if(ENABLED) begin(System.nanoTime());
	}

	private static void begin(long now)
	{
		if(!requestedScene.equals(activeScene))
		{
			dump(now);
			activeScene = requestedScene;
			sceneStart = now;
			warming = true;
		}
		if(sceneStart == 0L) sceneStart = now;
		if(warming && now - sceneStart >= WARMUP_NANOS)
		{
			dump(now);
			warming = false;
		}
		if(windowStart == 0L) windowStart = now;
		// A pre without a matching post is incomplete; discard its counters.
		frameStart = now;
		frameScopeNanos = frameQuads = 0L;
		frameOpen = true;
	}

	@SubscribeEvent
	public static void endFrame(RenderFrameEvent.Post event)
	{
		if(ENABLED) finish(System.nanoTime());
	}

	private static void finish(long now)
	{
		if(!frameOpen) return;
		frameOpen = false;
		frames++;
		windowFrames++;
		windowScopeNanos += frameScopeNanos;
		windowRigQuads += frameQuads;
		windowCpuNanos += Math.max(0L, now - frameStart);
		totalScopeNanos += frameScopeNanos;
		totalRigQuads += frameQuads;
		if(windowFrames >= WINDOW_FRAMES) dump(now);
	}

	/** CPU elapsed time around the world-render call, including driver waits. */
	public static void addScopePass(long nanos)
	{
		if(ENABLED && frameOpen) frameScopeNanos += Math.max(0L, nanos);
	}

	public static void addRigQuads(int count)
	{
		if(ENABLED && frameOpen) frameQuads += Math.max(0, count);
	}

	public static void dump()
	{
		if(ENABLED) dump(System.nanoTime());
	}

	private static void dump(long now)
	{
		if(windowFrames > 0L)
		{
			long elapsed = Math.max(1L, now - windowStart);
			FlansMod.LOGGER.info("[RenderPerf] frames={} fps={} scope={}ms/frame rigQuads={}/frame scene={} phase={} cpuFrame={}ms/frame windowNs={} (totals: frames={} scope={}ms rigQuads={})",
					windowFrames, decimal(windowFrames * 1_000_000_000D / elapsed),
					decimal(windowScopeNanos / 1_000_000D / windowFrames), windowRigQuads / windowFrames,
					activeScene, warming ? "warmup" : "measure",
					decimal(windowCpuNanos / 1_000_000D / windowFrames), elapsed,
					frames, totalScopeNanos / 1_000_000L, totalRigQuads);
		}
		windowFrames = windowScopeNanos = windowRigQuads = windowCpuNanos = windowStart = 0L;
	}

	private static String decimal(double value)
	{
		return String.format(java.util.Locale.ROOT, "%.3f", value);
	}

	public static long totalScopeNanos() { return totalScopeNanos; }
	public static long totalRigQuads() { return totalRigQuads; }
	public static long frames() { return frames; }
}
