package com.flansmod.common;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads a content pack's text file without knowing what encoding it is in.
 *
 * <p>The definition files were written by many hands over a decade, and the bytes show it: the
 * packs contain UTF-8 with and without a byte-order mark, UTF-16, GBK/GB18030 for the Chinese
 * packs, and plain Windows-1252 for the odd file with a German umlaut in a name. A reader that
 * assumes one of them turns the others into mojibake — and a BOM, which is invisible, becomes a
 * character glued to the front of the first key, so the file's very first line silently stops
 * parsing. That is exactly how a pack ends up "loading" with none of its definitions.
 *
 * <p>The order is the 1.20.1 lineage's ({@code TextDecoding}), and each step exists for a reason:
 * <ol>
 *   <li>a BOM is authoritative — strip it and decode as what it says;</li>
 *   <li>UTF-8, strictly: anything that decodes is right, because legal GBK rarely decodes as
 *       valid UTF-8;</li>
 *   <li>BOM-less UTF-16, detected by the zero-byte pattern rather than guessed;</li>
 *   <li>GB18030, but only when the high bytes are shaped like Chinese text. GB18030 accepts almost
 *       any byte sequence, so trying it eagerly would turn {@code Sturmgeschütz} into CJK
 *       characters — this is the step that needs the shape test;</li>
 *   <li>Windows-1252 as the last guess, then Latin-1 as the never-fails fallback.</li>
 * </ol>
 */
public final class TextDecoding
{
	private TextDecoding() { }

	private static final Charset GB18030 = Charset.forName("GB18030");
	private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

	/** The file's lines, decoded; line endings are normalised by {@link String#lines()}. */
	public static List<String> readLines(Path file) throws IOException
	{
		return readString(file).lines().toList();
	}

	/** The whole file as text. */
	public static String readString(Path file) throws IOException
	{
		return decode(Files.readAllBytes(file));
	}

	/** Decodes a byte array, choosing an encoding as described in the class comment. */
	public static String decode(byte[] bytes)
	{
		if(bytes.length == 0)
			return "";

		// 1. A byte-order mark is the file telling us what it is. Stripping it is not cosmetic:
		// left in place it becomes U+FEFF at the head of the first line, and that line's key then
		// matches nothing.
		if(startsWith(bytes, 0xEF, 0xBB, 0xBF))
			return stripBom(new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8));
		if(startsWith(bytes, 0xFF, 0xFE) || startsWith(bytes, 0xFE, 0xFF))
			return stripBom(new String(bytes, StandardCharsets.UTF_16));

		// 2. Strict UTF-8. The report-on-error decoder is what makes this a test rather than a
		// guess: a permissive one replaces the bad bytes and "succeeds" on everything.
		String text = tryDecode(bytes, StandardCharsets.UTF_8);
		if(text != null)
			return text;

		// 3. UTF-16 with no mark, recognised by which half of each pair the zero bytes fall in.
		Charset utf16 = guessBomlessUtf16(bytes);
		if(utf16 != null && (text = tryDecode(bytes, utf16)) != null)
			return text;

		// 4. Chinese, and only when it really looks Chinese.
		if(looksLikeGb18030(bytes) && (text = tryDecode(bytes, GB18030)) != null)
			return text;

		// 5. Western European, then a fallback that cannot fail.
		text = tryDecode(bytes, WINDOWS_1252);
		return text != null ? text : new String(bytes, StandardCharsets.ISO_8859_1);
	}

	/**
	 * Whether the high bytes are shaped like Chinese text.
	 *
	 * <p>GBK's trail bytes are mostly above 0x80, while a Latin-1 word such as
	 * {@code Sturmgeschütz} produces a lone high byte next to ASCII letters. Counting that
	 * difference is what stops the two from being confused, since GB18030 would otherwise decode
	 * both.
	 */
	static boolean looksLikeGb18030(byte[] bytes)
	{
		int highTrailSequences = 0;
		int asciiTrailSequences = 0;
		int index = 0;
		while(index < bytes.length)
		{
			int lead = bytes[index] & 0xFF;
			if(lead < 0x80)
			{
				index++;
				continue;
			}
			if(lead == 0x80 || lead == 0xFF || index + 1 >= bytes.length)
				return false;

			int trail = bytes[index + 1] & 0xFF;
			if(trail >= 0x30 && trail <= 0x39)
			{
				// Four-byte form: lead, digit, high byte, digit.
				if(index + 3 >= bytes.length || !isGbLead(bytes[index + 2] & 0xFF)
						|| !isDigit(bytes[index + 3] & 0xFF))
					return false;
				highTrailSequences++;
				index += 4;
				continue;
			}
			if(trail >= 0x40 && trail <= 0x7E)
				asciiTrailSequences++;
			else if(trail >= 0x80 && trail <= 0xFE)
				highTrailSequences++;
			else
				return false;
			index += 2;
		}
		// A few GBK extension characters have ASCII trail bytes; Latin-1 words almost only
		// produce those, so the high-byte sequences have to dominate by a wide margin.
		return highTrailSequences > 0 && asciiTrailSequences * 4 <= highTrailSequences;
	}

	/** UTF-16 with no BOM, told by whether the zero bytes land on even or odd offsets. */
	private static Charset guessBomlessUtf16(byte[] bytes)
	{
		if(bytes.length < 2)
			return null;
		int evenZeros = 0, oddZeros = 0;
		for(int index = 0; index < bytes.length; index++)
		{
			if(bytes[index] != 0)
				continue;
			if(index % 2 == 0) evenZeros++;
			else oddZeros++;
		}
		int half = bytes.length / 2;
		if(oddZeros * 3 >= half && oddZeros > evenZeros)
			return StandardCharsets.UTF_16LE;
		if(evenZeros * 3 >= half && evenZeros > oddZeros)
			return StandardCharsets.UTF_16BE;
		return null;
	}

	/** Decodes strictly: returns null rather than substituting for bytes that are not valid. */
	private static String tryDecode(byte[] bytes, Charset charset)
	{
		try
		{
			return charset.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(bytes))
					.toString();
		}
		catch(CharacterCodingException e)
		{
			return null;
		}
	}

	private static boolean isGbLead(int value) { return value >= 0x81 && value <= 0xFE; }
	private static boolean isDigit(int value) { return value >= 0x30 && value <= 0x39; }

	private static boolean startsWith(byte[] bytes, int... prefix)
	{
		if(bytes.length < prefix.length)
			return false;
		for(int i = 0; i < prefix.length; i++)
			if((bytes[i] & 0xFF) != prefix[i])
				return false;
		return true;
	}

	private static String stripBom(String text)
	{
		return !text.isEmpty() && text.charAt(0) == (char) 0xFEFF ? text.substring(1) : text;
	}
}
