package com.flansmod.client;

import java.io.File;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import com.flansmod.common.DebugCapture;
import com.flansmod.common.FlansMod;

/**
 * Takes a few screenshots during a debug run so the port can be verified on a machine that is not
 * being driven by hand. Inert unless {@code -Dflansmod.debugCapture=true} is set, the same switch
 * {@link DebugCapture} uses.
 *
 * <p>It writes nothing that the normal game does not already write — the frames go through vanilla's
 * own {@code Screenshot.grab}, to the ordinary screenshots folder — and it never synthesises input:
 * it only observes.
 */
@EventBusSubscriber(modid = FlansMod.MODID, value = Dist.CLIENT)
public final class DebugAutoShot
{
	/**
	 * Ticks after the client joins the world at which a frame is kept.
	 *
	 * <p>The sequence is chosen around the current kit's timeline (the EFT AK-74M): the gun is in
	 * hand by tick 60, the reload runs 70–140, the trigger is held 200–240, and the parked display
	 * rounds appear at 205. So:
	 * <ul>
	 *   <li>65 and 90 — the gun at rest, and the reload starting: the attachment assembly and the
	 *       whole-gun reload pose are both readable here;</li>
	 *   <li>110, 130 — mid-reload;</li>
	 *   <li>145 — the instant the reload finishes, when the magazine is full;</li>
	 *   <li>160, 190 — the loaded gun at rest, the cleanest frame for judging the assembly;</li>
	 *   <li>202–250 — the shots and the parked rounds;</li>
	 *   <li>300 — after the trigger is released.</li>
	 * </ul>
	 */
	private static final int[] GRAB_TICKS = { 65, 90, 110, 130, 145, 160, 190, 202, 210, 220, 226, 240, 250, 300 };

	/**
	 * The frames taken with the camera pulled back, so the third-person pose is on screen.
	 *
	 * <p>The camera is moved by setting the client's own camera option, not by pressing the key
	 * that normally does it — no input reaches the keyboard or the mouse. That is the same kind of
	 * change as the harness setting the time of day, and it is the only way to photograph a pose
	 * that is by definition what another player would see.
	 */
	private static final int[] THIRD_PERSON_TICKS = { 330, 345, 360 };
	private static int tick = -1;
	private static int next = 0;

	private DebugAutoShot() { }

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event)
	{
		if(!DebugCapture.ENABLED)
			return;
		Minecraft mc = Minecraft.getInstance();
		if(mc.player == null || mc.level == null)
		{
			tick = -1;
			next = 0;
			return;
		}
		if(tick < 0)
		{
			tick = 0;
			FlansMod.LOGGER.info("[DebugAutoShot] Joined the world; frames will be taken at ticks "
					+ java.util.Arrays.toString(GRAB_TICKS));
		}
		tick++;
		updateCameraType(mc);

		// A one-off whole-pack model audit while the client is up, so the check runs where the
		// resource manager lives. See auditModels for what it is looking for.
		if(tick == com.flansmod.common.DebugCapture.MODEL_AUDIT_AT_TICK)
			auditModels();

		// Two tick lists, one capture path: the early frames are first person, the later ones are
		// taken with the camera pulled back. A tick is captured if it is in either list, so the
		// camera only has to be in the right state at the moment of the grab.
		if(isThirdPersonTick(tick))
		{
			capture(mc, "flansmod_debug_3p_t" + tick + ".png");
			return;
		}
		if(next >= GRAB_TICKS.length || tick != GRAB_TICKS[next])
			return;
		next++;
		capture(mc, "flansmod_debug_t" + tick + ".png");
	}

	/**
	 * Bakes every gun's model once and reports what came back.
	 *
	 * <p>Two pack defects are silent on screen and both are worth a number: a definition whose model
	 * class is missing (it draws as its flat icon, which looks intentional) and one whose model
	 * declares a texture size its skin does not have (the gun samples the wrong part of its skin —
	 * the baker logs each correction, this counts them). Doing it for the whole pack once means the
	 * check does not depend on which weapon the harness happens to hand out.
	 */
	private static void auditModels()
	{
		int baked = 0, noModelLine = 0, classMissing = 0;
		java.util.List<String> missing = new java.util.ArrayList<>();
		java.util.List<String> textureless = new java.util.ArrayList<>();
		for(com.flansmod.common.types.InfoType type : com.flansmod.common.types.InfoType.infoTypes)
		{
			if(!(type instanceof com.flansmod.common.types.GunType gun))
				continue;

			// Every definition that names a texture is checked for it, whether or not it has a
			// model: a gun whose skin is missing draws in the missing-texture magenta, which is
			// the single most obvious defect in a pack and the one a model audit would miss.
			if(type.texture != null && !type.texture.isBlank())
			{
				var resolved = com.flansmod.client.render.Textures.forType(type);
				if(resolved == null || !com.flansmod.client.render.Textures.has(resolved))
					textureless.add(gun.shortName + " -> " + type.texture + " (looked for " + resolved + ")");
			}

			if(type.modelString == null || type.modelString.isBlank())
			{
				noModelLine++;
				continue;
			}
			if(com.flansmod.client.model.loader.ModelCache.rigFor(gun, gun.modelScale) == null)
			{
				classMissing++;
				missing.add(gun.shortName + " -> " + type.modelString);
			}
			else
				baked++;
		}
		FlansMod.LOGGER.info("[DebugAutoShot] Model audit: " + baked + " baked, " + noModelLine
				+ " with no Model line, " + classMissing + " whose class is missing from every pack, "
				+ textureless.size() + " whose texture is missing");
		for(String s : missing)
			FlansMod.LOGGER.info("[DebugAutoShot]   missing model: " + s);
		for(String s : textureless)
			FlansMod.LOGGER.info("[DebugAutoShot]   missing texture: " + s);
	}

	/** Takes one frame through vanilla's own screenshot path. */
	private static void capture(Minecraft mc, String name)
	{
		// Screenshot.grab appends "screenshots" to the directory it is given, so the game directory
		// itself is what goes in — passing the folder made a screenshots/screenshots nesting.
		net.minecraft.client.Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(),
				msg -> FlansMod.LOGGER.info("[DebugAutoShot] " + msg.getString()));
		FlansMod.LOGGER.info("[DebugAutoShot] Captured " + name
				+ " (held=" + mc.player.getMainHandItem().getItem() + ")");
	}

	private static boolean isThirdPersonTick(int at)
	{
		for(int t : THIRD_PERSON_TICKS)
			if(at == t)
				return true;
		return false;
	}

	/**
	 * Pulls the camera back for the third-person frames, then puts it back.
	 *
	 * <p>Setting the camera type is a client option, so nothing is pressed and no event is
	 * synthesized — the harness only changes a value in the same options object the controls
	 * screen writes to. The camera is restored afterwards so the rest of the run (and any later
	 * session using this config) is unaffected.
	 */
	private static void updateCameraType(Minecraft mc)
	{
		// The window is the span the third-person ticks fall in, with a little lead-in so the
		// ground under the player has resolved before the first frame is taken.
		int first = THIRD_PERSON_TICKS[0], last = THIRD_PERSON_TICKS[THIRD_PERSON_TICKS.length - 1];
		boolean wantThird = tick >= first - 2 && tick <= last + 2;

		var camera = mc.options.getCameraType();
		if(wantThird)
		{
			if(camera != net.minecraft.client.CameraType.THIRD_PERSON_BACK)
			{
				mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
				FlansMod.LOGGER.info("[DebugAutoShot] Camera pulled back for the third-person frames.");
			}
		}
		else if(camera != net.minecraft.client.CameraType.FIRST_PERSON)
		{
			// Compare the enum itself: isMirrored() is a different question and did not reliably
			// restore the camera, which left the later compatibility scenes in third person.
			mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
			FlansMod.LOGGER.info("[DebugAutoShot] Camera restored to first person.");
		}
	}
}
