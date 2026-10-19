package com.flansmod.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import com.flansmod.common.types.EnumType;
import com.flansmod.common.types.InfoType;
import com.flansmod.common.types.ItemFlan;
import com.flansmod.common.types.ArmourType;
import com.flansmod.common.types.ItemFlanArmour;

/**
 * Turns every parsed content-pack definition into a registered item.
 *
 * <p>1.7.10 did this inline: each {@code ItemXxx} constructor called
 * {@code GameRegistry.registerItem(this, type.shortName, MODID)} as the definition was read.
 * Modern Minecraft cannot register during mod construction, so the same wiring happens on
 * {@link RegisterEvent} — but the naming rule is unchanged: an item's registry path is the
 * definition's {@code ShortName}, so a pack's {@code 44_AK47.txt} becomes
 * {@code flansmod:44_ak47} exactly as it did before.
 */
@EventBusSubscriber(modid = FlansMod.MODID)
public final class ContentRegistry
{
	private ContentRegistry() { }

	/** Registry name -> the definition it came from, in the order packs were read. */
	public static final Map<String, InfoType> registered = new LinkedHashMap<>();

	private static final List<InfoType> pending = new ArrayList<>();
	private static final List<String> collisions = new ArrayList<>();

	/**
	 * Items are registered from here rather than from the mod constructor because the
	 * registry is not open yet at that point; {@link FlansMod} has already read the packs by
	 * the time this runs.
	 */
	@SubscribeEvent
	public static void onRegister(RegisterEvent event)
	{
		if(!event.getRegistryKey().equals(Registries.ITEM))
			return;
		if(!pending.isEmpty())
			return;                          // the event fires per registry; fill items once
		pending.addAll(InfoType.infoTypes);

		for(InfoType type : pending)
		{
			String path = registryPath(type);
			if(registered.containsKey(path))
			{
				// Two packs may define the same ShortName. 1.7.10 let the later one win; the
				// port keeps the first and reports the clash, so a pack cannot silently
				// replace another pack's item.
				collisions.add(path + "  (" + registered.get(path).contentPack + " vs " + type.contentPack + ")");
				continue;
			}

			// 1.7.10 made a round item damageable with setMaxDamage(type.roundsPerItem) and left it
			// stackable at the same time. Modern Minecraft forbids that combination outright
			// ("Item cannot be both damageable and stackable"), so the remaining rounds live in the
			// item's own data instead — which is exactly the choice the 1.20.1 build made too
			// (FlansModUltimate's ShootableItem stores them under an NBT key "rounds").
			Item.Properties properties = new Item.Properties().stacksTo(stackSize(type));
			Item item = type instanceof ArmourType armour
					? new ItemFlanArmour(armour, properties) : new ItemFlan(type, properties);
			event.register(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(FlansMod.MODID, path), () -> item);
			registered.put(path, type);
			ItemAssets.register(path, type);
		}

		if(!collisions.isEmpty())
		{
			FlansMod.log("Skipped " + collisions.size() + " duplicate item name(s):");
			for(String c : collisions)
				FlansMod.log("  " + c);
		}
		FlansMod.log("Registered " + registered.size() + " item(s) from content packs");
	}

	/**
	 * Every item that was registered, for the client to attach renderers to.
	 * The definitions are the source of truth; this is a convenience view.
	 */
	public static List<Item> registeredItems()
	{
		List<Item> items = new ArrayList<>(registered.size());
		for(InfoType type : registered.values())
			if(type.item != null)
				items.add(type.item);
		return items;
	}

	/**
	 * Registry path for a definition. Lower-cased because 1.21 rejects upper-case paths,
	 * while the packs freely mix case in {@code ShortName}.
	 */
	private static String registryPath(InfoType type)
	{
		String name = type.shortName != null && !type.shortName.isBlank()
				? type.shortName
				: (type.name != null ? type.name : "unnamed");
		return name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
	}

	private static int stackSize(InfoType type)
	{
		// Guns, vehicles and armour are singletons; parts, bullets and grenades stack. Bullets use
		// the pack's own MaxStackSize, which is what 1.7.10 did (setMaxStackSize(type.maxStackSize))
		// and what the ammunition model depends on: a StG-44 magazine item stacks to 2, so the
		// second one in a stack is a spare magazine, not 64 of them.
		if(type instanceof com.flansmod.common.types.ShootableType shootable)
			return Math.max(1, shootable.maxStackSize);

		return switch(type.type())
		{
			case gun, vehicle, plane, mecha, aa, armour -> 1;
			default -> 64;
		};
	}

	/** Separate firearm, ammunition and attachment tabs, plus the other legacy categories. */
	@EventBusSubscriber(modid = FlansMod.MODID)
	public static final class Tabs
	{
		private Tabs() { }

		@SubscribeEvent
		public static void onRegister(RegisterEvent event)
		{
			if(!event.getRegistryKey().equals(Registries.CREATIVE_MODE_TAB))
				return;

			register(event, "guns", Component.translatable("tab.flansmod.guns"),
					type -> (type.type() == EnumType.gun && !isMagazine(type))
							|| type.type() == EnumType.grenade || type.type() == EnumType.aa);
			register(event, "ammo", Component.translatable("tab.flansmod.ammo"),
					type -> type.type() == EnumType.bullet || isMagazine(type));
			register(event, "attachments", Component.translatable("tab.flansmod.attachments"),
					type -> type.type() == EnumType.attachment);
			register(event, "vehicles", "Flan's Mod Vehicles", EnumType.vehicle, EnumType.plane);
			register(event, "parts", "Flan's Mod Parts", EnumType.part, EnumType.tool, EnumType.box);
			register(event, "teams", "Flan's Mod Team Stuff",
					EnumType.armour, EnumType.armourBox, EnumType.team, EnumType.playerClass);
			register(event, "mechas", "Flan's Mod Mechas", EnumType.mecha, EnumType.mechaItem);
		}

		private static void register(RegisterEvent event, String path, String title, EnumType... kinds)
		{
			java.util.Set<EnumType> wanted = java.util.EnumSet.copyOf(java.util.Arrays.asList(kinds));
			register(event, path, Component.literal(title), type -> wanted.contains(type.type()));
		}

		private static boolean isMagazine(InfoType type)
		{
			return type instanceof com.flansmod.common.types.GunType gun
					&& gun.itsType == com.flansmod.common.types.GunType.GunItsType.MAG;
		}

		private static void register(RegisterEvent event, String path, Component title,
				java.util.function.Predicate<InfoType> filter)
		{
			event.register(Registries.CREATIVE_MODE_TAB,
					ResourceLocation.fromNamespaceAndPath(FlansMod.MODID, path),
					() -> CreativeModeTab.builder()
							.title(title)
							.icon(() -> firstIcon(filter))
							.displayItems((params, output) ->
							{
								for(Map.Entry<String, InfoType> e : registered.entrySet())
									if(filter.test(e.getValue()) && e.getValue().item != null)
										output.accept(new ItemStack(e.getValue().item));
							})
							.build());
		}

		private static ItemStack firstIcon(java.util.function.Predicate<InfoType> filter)
		{
			for(InfoType type : registered.values())
				if(filter.test(type) && type.item != null)
					return new ItemStack(type.item);
			return ItemStack.EMPTY;
		}
	}
}
