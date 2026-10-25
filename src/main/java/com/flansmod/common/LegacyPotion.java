package com.flansmod.common;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

/**
 * 1.7.10 addressed potion effects by number ({@code Potion.field_76415_N} and friends), and the
 * content packs still write those numbers: {@code SmokeEffect 15 100 3} is blindness for five
 * seconds at amplifier 3. Modern Minecraft removed the numeric ids, so the old table is spelled
 * out here rather than guessed per call site.
 */
public final class LegacyPotion
{
	private LegacyPotion() { }

	/** The 1.7.10 {@code Potion} id to the modern effect, or {@code null} for an unknown id. */
	public static Holder<MobEffect> byId(int id)
	{
		return switch(id)
		{
			case 1 -> MobEffects.MOVEMENT_SPEED;
			case 2 -> MobEffects.MOVEMENT_SLOWDOWN;
			case 3 -> MobEffects.DIG_SPEED;
			case 4 -> MobEffects.DIG_SLOWDOWN;
			case 5 -> MobEffects.DAMAGE_BOOST;
			case 6 -> MobEffects.HEAL;
			case 7 -> MobEffects.HARM;
			case 8 -> MobEffects.JUMP;
			case 9 -> MobEffects.CONFUSION;
			case 10 -> MobEffects.REGENERATION;
			case 11 -> MobEffects.DAMAGE_RESISTANCE;
			case 12 -> MobEffects.FIRE_RESISTANCE;
			case 13 -> MobEffects.WATER_BREATHING;
			case 14 -> MobEffects.INVISIBILITY;
			case 15 -> MobEffects.BLINDNESS;
			case 16 -> MobEffects.NIGHT_VISION;
			case 17 -> MobEffects.HUNGER;
			case 18 -> MobEffects.WEAKNESS;
			case 19 -> MobEffects.POISON;
			case 20 -> MobEffects.WITHER;
			case 21 -> MobEffects.HEALTH_BOOST;
			case 22 -> MobEffects.ABSORPTION;
			case 23 -> MobEffects.SATURATION;
			default -> null;
		};
	}
}
