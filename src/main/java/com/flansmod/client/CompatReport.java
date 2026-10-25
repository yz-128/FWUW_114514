package com.flansmod.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import org.lwjgl.opengl.GL11;

import com.flansmod.client.render.ScopeWorldTexture;
import com.flansmod.client.render.ShaderMuzzleRenderer;
import com.flansmod.common.FlansMod;

/**
 * One log line describing the render environment, for the compatibility pass.
 *
 * <p>The port already branches on Iris (shader on/off, shadow pass) and on Fabulous, and it has
 * fallbacks for both. Nothing printed which branch a given installation actually took, so a
 * report about "the scope looks wrong" could not be tied to an environment. This writes the
 * matrix once, on the first rendered frame (render thread, GL context current):
 *
 * <ul>
 *   <li>which optimisation/shader mods are loaded — the ones this dev client ships;</li>
 *   <li>Iris: present, shaders enabled, shadow pass;</li>
 *   <li>Fabulous graphics and the resulting independent-scope-camera availability;</li>
 *   <li>the OpenGL version/vendor/renderer strings the driver reports.</li>
 * </ul>
 */
@EventBusSubscriber(modid = FlansMod.MODID, value = Dist.CLIENT)
public final class CompatReport
{
	/** Mod ids worth knowing about when a rendering report arrives. */
	public static final String[] WATCHED = {
			"iris", "sodium", "embeddium", "oculus",
			"acceleratedrendering", "entityculling", "cull_leaves", "modernfix",
			"ferritecore", "lithium", "c2me", "flerovium", "sclp"};

	private static boolean logged;
	private static String lastLine = "";

	private CompatReport() { }

	@SubscribeEvent
	public static void onFrame(RenderFrameEvent.Pre event)
	{
		if(logged)
			return;
		refresh();
	}

	/** Debug capture can refresh after shader/options changes without per-frame polling. */
	public static void refresh()
	{
		String line = describe();
		if(!logged || !line.equals(lastLine))
			FlansMod.LOGGER.info(line);
		logged = true;
		lastLine = line;
	}

	/** Builds the one line; pure enough to be called from a debug path or a check. */
	public static String describe()
	{
		StringBuilder mods = new StringBuilder();
		StringBuilder versions = new StringBuilder();
		for(String id : WATCHED)
		{
			if(!isLoaded(id))
				continue;
			if(mods.length() > 0)
				mods.append(',');
			mods.append(id);
			if(versions.length() > 0) versions.append(',');
			versions.append(id).append('@').append(version(id));
		}

		String iris = isLoaded("iris")
				? (ShaderMuzzleRenderer.shadersEnabled()
						? (ShaderMuzzleRenderer.shadowPass() ? "on(shadow)" : "on")
						: "present-off")
				: "absent";
		boolean fabulous = Minecraft.useShaderTransparency();
		boolean dual = ScopeWorldTexture.dualRenderEnabled();

		return "[Compat] mods={" + mods + "} iris=" + iris + " fabulous=" + fabulous
				+ " scopeDualCamera=" + dual
				+ " versions={" + versions + "}"
				+ " gl=" + glString(GL11.GL_VERSION)
				+ " vendor=" + glString(GL11.GL_VENDOR)
				+ " renderer=" + glString(GL11.GL_RENDERER);
	}

	/** The last line written, for a diagnostics command. */
	public static String lastLine()
	{
		return lastLine;
	}

	private static boolean isLoaded(String modid)
	{
		try
		{
			return ModList.get() != null && ModList.get().isLoaded(modid);
		}
		catch(LinkageError | RuntimeException unavailable)
		{
			return false;
		}
	}

	private static String glString(int name)
	{
		try
		{
			String value = GL11.glGetString(name);
			return value == null ? "?" : value.replace('\n', ' ');
		}
		catch(RuntimeException unavailable)
		{
			return "?";
		}
	}

	private static String version(String id)
	{
		try
		{
			return ModList.get().getModContainerById(id).map(mod -> mod.getModInfo().getVersion().toString()).orElse("?");
		}
		catch(LinkageError | RuntimeException unavailable)
		{
			return "?";
		}
	}
}
