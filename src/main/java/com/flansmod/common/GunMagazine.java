package com.flansmod.common;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import com.flansmod.common.types.GunType;

/**
 * The rounds inside a gun, ported from 1.7.10's {@code ItemGun.getBulletItemStack}.
 *
 * <p>1.7.10 kept the magazine in the gun's own NBT as a list of item stacks under the key
 * {@code ammo}, one entry per chamber, and each entry was a real {@link ItemStack} with its own
 * damage counting down as the round was used. That is what made different ammunition types in one
 * gun, and partially-spent magazines, work without any separate container item.
 *
 * <p>The port keeps the same shape and the same key so packs' saved data stays readable, but moves
 * it onto the item component that replaced direct NBT access in 1.21. {@code CustomData} is the
 * component vanilla provides exactly for this — mod-owned data on an item stack — so the storage
 * is a {@code "ammo"} list inside it rather than a bespoke component.
 */
public final class GunMagazine
{
	/** The key 1.7.10 used, kept so existing guns keep their rounds. */
	public static final String AMMO_KEY = "ammo";
	public static final String CHAMBER_KEY = "flansmodChamber";

	private GunMagazine() { }

	/** The rounds in the gun, resized to the gun's chamber count. Empty on a gun with no data. */
	private static ListTag ammoList(ItemStack gun, GunType type, boolean create)
	{
		CustomData data = gun.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = data == null ? null : data.copyTag();
		ListTag list = tag != null && tag.contains(AMMO_KEY, Tag.TAG_LIST)
				? tag.getList(AMMO_KEY, Tag.TAG_COMPOUND) : null;

		if(list == null && create)
		{
			list = new ListTag();
			for(int i = 0; i < type.numAmmoItemsInGun; i++)
				list.add(new CompoundTag());
			setList(gun, tag, list);
		}
		return list;
	}

	private static void setList(ItemStack gun, CompoundTag existing, ListTag list)
	{
		CompoundTag tag = existing == null ? new CompoundTag() : existing;
		tag = tag.copy();
		tag.put(AMMO_KEY, list);
		gun.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	/**
	 * The round in one chamber, or {@link ItemStack#EMPTY}.
	 *
	 * <p>An entry is a serialized item stack; an empty chamber is an empty compound, which is how
	 * 1.7.10 represented a slot with nothing in it.
	 */
	public static ItemStack get(ItemStack gun, GunType type, int chamber)
	{
		return readEntry(ammoList(gun,type,false),chamber);
	}
	private static ItemStack readEntry(ListTag list,int chamber) {
		if(list == null || chamber < 0 || chamber >= list.size())
			return ItemStack.EMPTY;

		CompoundTag entry = list.getCompound(chamber);
		if(entry.isEmpty())
			return ItemStack.EMPTY;
		return ItemStack.parseOptional(net.minecraft.core.RegistryAccess.EMPTY, entry);
	}

	/** Stores a round, or empties the chamber when given {@link ItemStack#EMPTY}. */
	public static void set(ItemStack gun, GunType type, int chamber, ItemStack round)
	{
		if(type.itsType==GunType.GunItsType.MAG && chamber==0) {
			ListTag replacement=new ListTag();
			if(!round.isEmpty()) replacement.add(round.save(net.minecraft.core.RegistryAccess.EMPTY));
			writeMagazine(gun,replacement);return;
		}
		ListTag list = ammoList(gun, type, true);
		if(list == null || chamber < 0 || chamber >= list.size())
			return;

		ListTag copy = list.copy();
		copy.set(chamber, round.isEmpty() ? new CompoundTag() : (Tag) round.save(net.minecraft.core.RegistryAccess.EMPTY));
		setList(gun, gun.get(DataComponents.CUSTOM_DATA) == null ? null
				: gun.get(DataComponents.CUSTOM_DATA).copyTag(), copy);
	}

	// ---------------------------------------------------------------- rounds inside an item
	//
	// Two ammunition models exist in the packs and both have to work:
	//
	//   RoundsPerItem > 1  the item IS a magazine; its remaining rounds are item data, and the
	//                      stack count is how many such magazines the player carries;
	//   RoundsPerItem <= 1 the item is a single round; the stack count IS the number of rounds.
	//
	// 1.7.10 expressed the first with item durability, but modern Minecraft refuses an item that
	// is both damageable and stackable, so the count goes into the item's own data under the same
	// key the 1.20.1 build uses ("rounds") — that build made exactly the same move.
	//
	// Getting this wrong is silent and total: a magazine item whose round count always reads zero
	// makes the gun unfireable and reload forever.

	/** The data key holding a magazine's remaining rounds; the 1.20.1 build's choice, kept. */
	public static final String ROUNDS_KEY = "rounds";

	/** Rounds a full item of this ammunition holds; 1 for loose rounds. */
	public static int roundsPerItem(ItemStack ammo)
	{
		if(com.flansmod.common.types.InfoType.getType(ammo) instanceof GunType mag && mag.itsType == GunType.GunItsType.MAG)
			return Math.max(1, mag.ammoCapacity);
		if(ammo.getItem() instanceof com.flansmod.common.types.ItemFlan flan
				&& flan.type instanceof com.flansmod.common.types.ShootableType shootable)
			return Math.max(1, shootable.roundsPerItem);
		return 1;
	}

	/** Rounds left in one ammunition item: its data for a magazine, its stack count for loose. */
	public static int roundsRemaining(ItemStack ammo)
	{
		if(ammo.isEmpty())
			return 0;
		if(com.flansmod.common.types.InfoType.getType(ammo) instanceof GunType mag && mag.itsType == GunType.GunItsType.MAG) {
			int total = 0;
			ListTag list=ammoList(ammo,mag,false);
			if(list!=null) for(int i=0;i<list.size();i++) total+=roundsRemaining(readEntry(list,i));
			return total;
		}
		int per = roundsPerItem(ammo);
		if(per <= 1)
			return ammo.getCount();
		CompoundTag tag = ammo.get(DataComponents.CUSTOM_DATA) == null ? null
				: ammo.get(DataComponents.CUSTOM_DATA).copyTag();
		return tag != null && tag.contains(ROUNDS_KEY) ? tag.getInt(ROUNDS_KEY) : per;
	}

	/** Writes the round count back. Loose rounds change the stack size instead. */
	public static void setRoundsRemaining(ItemStack ammo, int rounds)
	{
		int per = roundsPerItem(ammo);
		if(per <= 1)
		{
			ammo.setCount(Math.max(0, rounds));
			return;
		}
		CompoundTag tag = ammo.get(DataComponents.CUSTOM_DATA) == null ? new CompoundTag()
				: ammo.get(DataComponents.CUSTOM_DATA).copyTag();
		tag.putInt(ROUNDS_KEY, Math.max(0, Math.min(rounds, per)));
		ammo.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	/**
	 * Spends one round from an ammunition stack, rolling onto the next magazine in the stack when
	 * one runs out — the 1.20.1 build's {@code consumeRound}, byte for byte.
	 *
	 * @return false when the stack had nothing to spend
	 */
	public static boolean consumeRound(ItemStack ammo)
	{
		if(com.flansmod.common.types.InfoType.getType(ammo) instanceof GunType mag && mag.itsType == GunType.GunItsType.MAG) {
			int chamber = findLoaded(ammo, mag);
			if(chamber < 0) return false;
			ItemStack bullet = get(ammo, mag, chamber);
			boolean spent = consumeRound(bullet);
			ListTag list=ammoList(ammo,mag,false).copy();
			if(bullet.isEmpty()) list.remove(chamber);
			else list.set(chamber,bullet.save(net.minecraft.core.RegistryAccess.EMPTY));
			writeMagazine(ammo,list);
			return spent;
		}
		int per = roundsPerItem(ammo);
		if(per > 1)
		{
			int current = roundsRemaining(ammo);
			if(current > 0)
			{
				setRoundsRemaining(ammo, current - 1);
				if(current - 1 <= 0 && ammo.getCount() > 1)
				{
					ammo.shrink(1);
					setRoundsRemaining(ammo, per);
				}
				return true;
			}
			if(ammo.getCount() > 1)
			{
				ammo.shrink(1);
				setRoundsRemaining(ammo, per - 1);
				return true;
			}
			return false;
		}
		if(ammo.getCount() > 0)
		{
			ammo.shrink(1);
			return true;
		}
		return false;
	}

	/**
	 * True when a chamber's stack still has a round in it.
	 *
	 * <p>Both ammunition models report correctly here; a check that only looked at durability
	 * would call every loose-round stack empty and the gun would never fire.
	 */
	public static boolean hasRound(ItemStack round)
	{
		return roundsRemaining(round) > 0;
	}

	/** The first chamber holding a round that can still fire, or -1. */
	public static int findLoaded(ItemStack gun, GunType type)
	{
		if(type.itsType==GunType.GunItsType.MAG) {
			ListTag list=ammoList(gun,type,false);
			if(list!=null) for(int i=list.size()-1;i>=0;i--) if(hasRound(readEntry(list,i))) return i;
			return -1;
		}
		if(type.itsType!=GunType.GunItsType.MAG && !getChamberedRound(gun).isEmpty()) return 0;
		for(int i = 0; i < type.numAmmoItemsInGun; i++)
		{
			ItemStack loaded=get(gun,type,i);
			if(type.itsType!=GunType.GunItsType.MAG && !type.allowDirectAmmoLoading
					&& !(com.flansmod.common.types.InfoType.getType(loaded) instanceof GunType magazine
							&& acceptsMagazine(type,magazine))) continue;
			if(hasRound(loaded))
				return i;
		}
		return -1;
	}

	/** True when nothing in the gun can fire. */
	public static boolean isEmpty(ItemStack gun, GunType type)
	{
		return findLoaded(gun, type) < 0;
	}

	/**
	 * Loads one round per chamber from the player's inventory, the way 1.7.10's {@code reload} did.
	 *
	 * <p>The original searched the whole inventory for items matching the gun's {@code Ammo}
	 * definition, took them out stack by stack, and filled chambers until it ran out. It also
	 * filled each chamber with a full stack of rounds rather than a single one, which is why a
	 * 30-round magazine in Flan's is one item with 30 uses rather than 30 items.
	 *
	 * @return true if at least one chamber was filled
	 */
	public static boolean reload(ItemStack gun, GunType type, net.minecraft.world.entity.player.Player player)
	{
		return reload(gun, type, player, false);
	}

	/**
	 * The same reload, with the manual-reload meaning of {@code force}.
	 *
	 * <p>1.7.10's {@code reload} took a {@code forceReload} flag: a manual reload refilled every
	 * chamber even if it already held rounds, while an automatic (empty-gun) reload only filled
	 * what was missing. That distinction is what makes the reload key useful with a part-spent
	 * magazine.
	 */
	public static boolean reload(ItemStack gun, GunType type, net.minecraft.world.entity.player.Player player,
								 boolean force)
	{
		return reload(gun,type,player.getInventory(),force,returned->{
			if(!player.getInventory().add(returned)) player.drop(returned,false);
		});
	}

	/** The production inventory transaction, also usable by isolated ItemStack regression checks. */
	public static boolean reload(ItemStack gun,GunType type,net.minecraft.world.entity.player.Inventory inventory,
			boolean force,java.util.function.Consumer<ItemStack> returnItem)
	{
		if(type.itsType == GunType.GunItsType.MAG) {
			boolean loaded=false;
			while(loadOneBullet(gun,type,inventory)) loaded=true;
			return loaded;
		}
		if(type.ammo.isEmpty())
			return false;

		boolean loaded = false;
		for(int chamber = 0; chamber < type.numAmmoItemsInGun; chamber++)
		{
			ItemStack current = get(gun, type, chamber);
			boolean spent = current.isEmpty() || roundsRemaining(current) <= 0;

			if(!spent && !force)
				continue;
			ItemStack found = findAmmo(inventory, type);
			if(found.isEmpty()) break;
			// Earlier port saves stored a directly loaded round in the magazine slot. That is
			// the chamber round, so migrate it before returning/replacing the magazine.
			if(chamber==0) { normalizeChamber(gun,type); current=get(gun,type,chamber); }
			ItemStack round = found.copy();
			round.setCount(1);
			found.shrink(1);

			// A partly-used magazine comes back to the player before the fresh one goes in, which
			// is what the original did (1.7.10 gave the old stack back to the inventory, or dropped
			// it when there was no room).
			if(!current.isEmpty())
			{
				ItemStack returned = current.copy();
				returnItem.accept(returned);
			}

			// take one item, not the whole stack: a magazine is one item, and a loose-round stack
			// keeps the rest.
			set(gun, type, chamber, round);
			ensureChamber(gun,type);
			loaded = true;
		}
		return loaded;
	}

	/** The best inventory stack of the ammunition types this gun accepts, or empty.
	 *
	 * <p>"Best" is 1.7.10's rule, and it matters once magazines can be part-used: the original
	 * scanned the whole inventory for the stack with the most rounds left and loaded that one, so
	 * a reload never picks a nearly-empty magazine while a full one sits next to it.
	 */
	private static ItemStack findAmmo(net.minecraft.world.entity.player.Player player, GunType type)
	{
		return findAmmo(player.getInventory(),type);
	}
	/** The fullest actual magazine wins; direct-bullet fallback requires explicit opt-in. */
	public static ItemStack findAmmo(net.minecraft.world.entity.player.Inventory inventory, GunType type)
	{
		ItemStack best = ItemStack.EMPTY;
		int bestRounds = 0;
		boolean bestIsMagazine=false;
		for(int i = 0; i < inventory.getContainerSize(); i++)
		{
			ItemStack candidate = inventory.getItem(i);
			if(candidate.isEmpty() || !matchesAmmo(candidate, type))
				continue;
			// FMUM compression takes the first compatible inventory slot, then moves on when depleted.
			if(type.itsType==GunType.GunItsType.MAG && hasRound(candidate)) return candidate;
			boolean isMagazine=com.flansmod.common.types.InfoType.getType(candidate) instanceof GunType;
			int rounds = isMagazine?roundsRemaining(candidate):roundsIn(candidate);
			if(rounds>0 && ((isMagazine && !bestIsMagazine) || (isMagazine==bestIsMagazine && rounds > bestRounds)))
			{
				bestRounds = rounds;
				best = candidate;
				bestIsMagazine=isMagazine;
			}
		}
		return best;
	}
	/** Advanced reload takes the first compatible actual magazine, including an empty one. */
	public static ItemStack firstMagazine(net.minecraft.world.entity.player.Inventory inventory,GunType gun) {
		for(int i=0;i<inventory.getContainerSize();i++) {
			ItemStack stack=inventory.getItem(i);
			if(!stack.isEmpty() && com.flansmod.common.types.InfoType.getType(stack) instanceof GunType mag
					&& acceptsMagazine(gun,mag)) return stack;
		}
		return ItemStack.EMPTY;
	}

	/** Rounds left in an ammunition stack, counting every magazine and loose round in it. */
	private static int roundsIn(ItemStack stack)
	{
		int per = roundsPerItem(stack);
		if(per <= 1)
			return stack.getCount();
		// The 1.20.1 build's total: every full magazine in the stack, plus the part-used one.
		return (stack.getCount() - 1) * per + roundsRemaining(stack);
	}

	private static boolean matchesAmmo(ItemStack candidate, GunType type)
	{
		if(!(candidate.getItem() instanceof com.flansmod.common.types.ItemFlan flan))
			return false;
		if(flan.type instanceof GunType mag && mag.itsType == GunType.GunItsType.MAG)
			return acceptsMagazine(type, mag);
		if(type.itsType!=GunType.GunItsType.MAG && !type.allowDirectAmmoLoading)
			return false;
		for(com.flansmod.common.types.BulletType accepted : type.ammo)
			if(flan.type == accepted)
				return true;
		if(flan.type instanceof com.flansmod.common.types.BulletType bullet)
			return type.ammoNames.stream().anyMatch(n -> n.equalsIgnoreCase(bullet.shortName) || n.equalsIgnoreCase(bullet.ammoType));
		return false;
	}
	public static boolean acceptsMagazine(GunType gun, GunType mag) {
		return gun.itsType != GunType.GunItsType.MAG && mag.itsType == GunType.GunItsType.MAG
				&& gun.magazineNames.stream().anyMatch(n -> n.equalsIgnoreCase(mag.shortName)
						|| mag.magazineNames.stream().anyMatch(n::equalsIgnoreCase));
	}
	public static GunType magazineType(ItemStack gun, GunType type) {
		var definition = com.flansmod.common.types.InfoType.getType(get(gun, type, 0));
		return definition instanceof GunType mag && mag.itsType == GunType.GunItsType.MAG ? mag : null;
	}
	public static ItemStack chamberedBullet(ItemStack loaded) {
		if(com.flansmod.common.types.InfoType.getType(loaded) instanceof GunType mag && mag.itsType == GunType.GunItsType.MAG) {
			int index = findLoaded(loaded, mag);
			if(index<0) return ItemStack.EMPTY;
			ItemStack round=get(loaded,mag,index);round.setCount(1);return round;
		}
		return loaded;
	}
	/** Moves the old direct-round representation into the independent chamber once, without loss. */
	public static boolean normalizeChamber(ItemStack gun,GunType type) {
		if(type.itsType==GunType.GunItsType.MAG || type.magazineNames.isEmpty() || !getChamberedRound(gun).isEmpty()) return false;
		ItemStack current=get(gun,type,0);
		if(!(com.flansmod.common.types.InfoType.getType(current) instanceof com.flansmod.common.types.BulletType) || current.isEmpty()) return false;
		ItemStack bullet=current.copy();bullet.setCount(1);current.shrink(1);
		set(gun,type,0,current);setChamberedRound(gun,bullet);
		return true;
	}
	/** One compression stroke adds one actual round, never an implicit full magazine. */
	public static boolean loadOneBullet(ItemStack stack,GunType mag,net.minecraft.world.entity.player.Inventory inventory) {
		return loadOneBullet(stack,mag,inventory,true);
	}
	public static boolean loadOneBullet(ItemStack stack,GunType mag,net.minecraft.world.entity.player.Inventory inventory,boolean consume) {
		if(mag.itsType!=GunType.GunItsType.MAG || roundsRemaining(stack)>=mag.ammoCapacity) return false;
		ItemStack found=findAmmo(inventory,mag);
		if(found.isEmpty()) return false;
		ItemStack loaded=found.copy();loaded.setCount(1);
		ListTag existing=ammoList(stack,mag,false),list=existing==null?new ListTag():existing.copy();
		list.add(loaded.save(net.minecraft.core.RegistryAccess.EMPTY));
		if(consume) found.shrink(1);
		writeMagazine(stack,list);
		return true;
	}
	private static void writeMagazine(ItemStack stack,ListTag rounds) {
		CustomData data=stack.get(DataComponents.CUSTOM_DATA);
		setList(stack,data==null?null:data.copyTag(),rounds);
	}
	/** Bottom-to-mouth index, matching FMUM's ammo array; legacy counted stacks remain readable. */
	public static ItemStack magazineRoundAt(ItemStack stack,GunType mag,int index) {
		if(mag.itsType!=GunType.GunItsType.MAG || index<0) return ItemStack.EMPTY;
		ListTag list=ammoList(stack,mag,false);
		if(list==null) return ItemStack.EMPTY;
		for(int i=0;i<list.size();i++) {
			ItemStack round=readEntry(list,i);int count=roundsRemaining(round);
			if(index<count) { round.setCount(1);return round; }
			index-=count;
		}
		return ItemStack.EMPTY;
	}
	/** The chamber is independent of the installed magazine, as FMUM's barrel tag was. */
	public static ItemStack getChamberedRound(ItemStack gun) {
		var data=gun.get(DataComponents.CUSTOM_DATA);
		if(data==null) return ItemStack.EMPTY;
		var tag=data.copyTag();
		return tag.contains(CHAMBER_KEY,Tag.TAG_COMPOUND)?ItemStack.parseOptional(net.minecraft.core.RegistryAccess.EMPTY,tag.getCompound(CHAMBER_KEY)):ItemStack.EMPTY;
	}
	public static void setChamberedRound(ItemStack gun,ItemStack bullet) {
		CustomData.update(DataComponents.CUSTOM_DATA,gun,tag->tag.put(CHAMBER_KEY,
				bullet.isEmpty()?new CompoundTag():bullet.save(net.minecraft.core.RegistryAccess.EMPTY)));
	}
	/** Feed once. A tactical reload preserves an already chambered round. */
	public static boolean ensureChamber(ItemStack gun,GunType type) {
		if(GunCasings.retained(gun)!=null)return false;
		if(type.allowDirectAmmoLoading && normalizeChamber(gun,type)) return true;
		if(type.itsType==GunType.GunItsType.MAG || !getChamberedRound(gun).isEmpty()) return false;
		ItemStack mag=get(gun,type,0);
		if(!(com.flansmod.common.types.InfoType.getType(mag) instanceof GunType magType) || magType.itsType!=GunType.GunItsType.MAG) return false;
		ItemStack bullet=chamberedBullet(mag).copy();
		if(bullet.isEmpty() || !consumeRound(mag)) return false;
		bullet.setCount(1);set(gun,type,0,mag);setChamberedRound(gun,bullet);
		return true;
	}
	/** Automatic guns feed immediately; bolt guns hold their spent case until charge. */
	public static ItemStack spendChamberedRound(ItemStack gun,GunType type) {
		ItemStack bullet=getChamberedRound(gun);
		if(bullet.isEmpty()) return ItemStack.EMPTY;
		setChamberedRound(gun,ItemStack.EMPTY);
		if(type.itsType==GunType.GunItsType.BOLT_GUN) {
			var round=com.flansmod.common.types.InfoType.getType(bullet);
			GunCasings.retain(gun,round instanceof com.flansmod.common.types.BulletType b?GunCasings.casing(b):null);
		} else ensureChamber(gun,type);
		return bullet;
	}
}
