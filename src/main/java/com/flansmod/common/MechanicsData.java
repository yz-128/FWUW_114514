package com.flansmod.common;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.flansmod.common.config.MechanicsConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Data-pack override for the mechanics settings.
 *
 * <p>A pack drops {@code data/<namespace>/flansmod/mechanics/settings.json} with the same keys the
 * config file uses, and the values apply on top of the config (data pack &gt; config &gt; built-in
 * default). Nested objects are accepted too, so both of these work:
 *
 * <pre>
 * { "healthPerHardness": 6, "metalToughness": 3 }
 * { "mechanics": { "blockBreaking": { "healthPerHardness": 6 } } }
 * </pre>
 */
@EventBusSubscriber(modid = FlansMod.MODID)
public final class MechanicsData
{
	private static final Gson GSON = new Gson();

	private MechanicsData() { }

	@SubscribeEvent
	public static void onAddReloadListener(AddReloadListenerEvent event)
	{
		event.addListener(new Listener());
	}

	/** A config file was written or reloaded: rebuild the base values, then reapply the pack. */
	@SubscribeEvent
	public static void onConfig(ModConfigEvent.Loading event)
	{
		MechanicsConfig.reload();
	}

	@SubscribeEvent
	public static void onConfig(ModConfigEvent.Reloading event)
	{
		MechanicsConfig.reload();
	}

	private static final class Listener extends SimpleJsonResourceReloadListener
	{
		Listener()
		{
			super(GSON, "flansmod/mechanics");
		}

		@Override
		protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager resources, ProfilerFiller profiler)
		{
			Map<String, Object> overrides = new HashMap<>();
			for(JsonElement element : objects.values())
			{
				if(!element.isJsonObject())
					continue;
				collect("", element.getAsJsonObject(), overrides);
			}
			MechanicsSettings.setOverrides(overrides);
			FlansMod.LOGGER.info("[Mechanics] data pack overrides applied: " + overrides.size()
					+ (overrides.isEmpty() ? "" : " " + overrides));
		}

		/** Flattens a JSON tree to leaf keys; both {@code key} and {@code parent.key} are recorded. */
		private static void collect(String prefix, JsonObject object, Map<String, Object> out)
		{
			for(Map.Entry<String, JsonElement> entry : object.entrySet())
			{
				JsonElement value = entry.getValue();
				String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
				if(value.isJsonObject())
				{
					collect(key, value.getAsJsonObject(), out);
					continue;
				}
				if(value.isJsonPrimitive())
				{
					JsonObject primitive = new JsonObject();
					primitive.add(entry.getKey(), value);
					if(value.getAsJsonPrimitive().isBoolean())
						out.put(entry.getKey(), value.getAsBoolean());
					else if(value.getAsJsonPrimitive().isNumber())
						out.put(entry.getKey(), value.getAsDouble());
					else
						continue;
					// The dotted form also applies, so packs can mirror the config paths exactly.
					out.put(key, out.get(entry.getKey()));
				}
			}
		}
	}
}
