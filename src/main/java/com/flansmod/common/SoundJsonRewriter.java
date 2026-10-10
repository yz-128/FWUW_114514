package com.flansmod.common;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Rewrites a content pack's {@code sounds.json} on the fly so modern Minecraft accepts it.
 *
 * <p>1.7.10's sound system took any string as a sound name, so the packs are full of mixed
 * case: {@code "AAfire"}, {@code "flansmod:bulletHit"}, even paths ending in {@code .ogg}.
 * 1.21 parses a sound event into a {@link net.minecraft.resources.ResourceLocation} and throws
 * {@code ResourceLocationException: Non [a-z0-9/._-] character} on any capital — which aborts
 * the whole registration phase ("Failed to wait for future Registration events") and leaves the
 * mod in a broken state where no model event is delivered at all.
 *
 * <p>The rewrite happens as the resource is served: keys and sound paths are lower-cased and an
 * {@code .ogg} suffix is dropped, matching the file names, and the game never sees the original.
 * This keeps the packs untouched on disk, which matters because they are the user's files.
 */
final class SoundJsonRewriter
{
	private SoundJsonRewriter() { }

	private static final Pattern PATH = Pattern.compile("\"([^\"]*)\"");
	private static final Pattern KEY = Pattern.compile("^\\s*\"([^\"]+)\"\\s*:");

	/** True if the resource is a sounds.json that needs fixing. */
	static boolean needsRewrite(String path)
	{
		return path.endsWith("sounds.json");
	}

	/**
	 * @param bytes the pack's sounds.json
	 * @return the same document with every key and sound path lower-cased
	 */
	static byte[] rewrite(byte[] bytes)
	{
		String text = new String(bytes, StandardCharsets.UTF_8);
		StringBuilder out = new StringBuilder(text.length() + 64);

		for(String line : text.split("\n", -1))
		{
			// Keys are the object names; sound paths are the values inside "sounds" arrays.
			// Both must be lower-case, and both are quoted strings, so every quoted token on a
			// line is normalised except the ones that are structural values ("category", etc.),
			// which are already lower-case and therefore unchanged by this.
			StringBuilder rebuilt = new StringBuilder(line.length());
			Matcher m = PATH.matcher(line);
			int last = 0;
			while(m.find())
			{
				rebuilt.append(line, last, m.start());
				String value = m.group(1);
				// A category value like "player" is harmless to lower-case; a namespace and path
				// must be. Only the last path segment can carry the .ogg the packs append.
				String fixed = value.toLowerCase(java.util.Locale.ROOT);
				if(fixed.endsWith(".ogg"))
					fixed = fixed.substring(0, fixed.length() - 4);
				rebuilt.append('"').append(fixed).append('"');
				last = m.end();
			}
			rebuilt.append(line.substring(last));
			out.append(rebuilt).append('\n');
		}
		// Trailing newline added above; trim the extra if the source had none.
		if(!text.endsWith("\n") && out.length() > 0)
			out.setLength(out.length() - 1);

		return out.toString().getBytes(StandardCharsets.UTF_8);
	}

	/** Combine the registrations that vanilla would normally read from a resource stack. */
	static void mergeInto(JsonObject target, byte[] document)
	{
		JsonObject source = JsonParser.parseString(new String(rewrite(document), StandardCharsets.UTF_8)).getAsJsonObject();
		for(var entry : source.entrySet())
		{
			JsonObject incoming = entry.getValue().getAsJsonObject();
			JsonObject current = target.has(entry.getKey()) ? target.getAsJsonObject(entry.getKey()) : null;
			boolean replace = incoming.has("replace") && incoming.get("replace").getAsBoolean();
			if(current == null || replace)
				target.add(entry.getKey(), incoming.deepCopy());
			else
			{
				JsonArray sounds = current.getAsJsonArray("sounds");
				if(sounds == null)
				{
					sounds = new JsonArray();
					current.add("sounds", sounds);
				}
				JsonArray additions = incoming.getAsJsonArray("sounds");
				if(additions != null)
					for(var sound : additions)
						sounds.add(sound.deepCopy());
				// A preceding replace still applies to sound registrations outside this bundle.
				// Vanilla keeps the first subtitle until a replacing registration arrives.
			}
		}
	}
}
