import com.flansmod.common.*;
import com.flansmod.common.types.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import java.nio.file.Path;

/** Isolated item/NBT round-trip checks, without a client, player, world or simulated input. */
public final class VerifyMagazineData {
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
    public static void main(String[] args)throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
		// NeoForge exposes this for mod registration; only this isolated JVM is changed.
		((net.minecraft.core.MappedRegistry<?>)BuiltInRegistries.ITEM).unfreeze();
        OfflinePack pack=new OfflinePack(Path.of(args[0]));pack.readTypes();
		int defaultGuns=0;
		for(var type:pack.types) if(type instanceof GunType g && g.itsType!=GunType.GunItsType.MAG && !g.magazineNames.isEmpty()) {
			check(!g.allowDirectAmmoLoading,"EFT gun defaults to actual magazine supply: "+g.shortName);defaultGuns++;
		}
		check(defaultGuns==31,"31 EFT firearms covered by default magazine-only policy");
        var gun=(GunType)pack.types.stream().filter(t->"AK74M".equals(t.shortName)).findFirst().orElseThrow();
        var mag=(GunType)pack.types.stream().filter(t->"6L20_545x39_30Rounds".equals(t.shortName)).findFirst().orElseThrow();
        var bullet=(BulletType)pack.types.stream().filter(t->"5.45x39mm7n39".equals(t.shortName)).findFirst().orElseThrow();
        Item gunItem=register(gun,"fixture_gun"),magItem=register(mag,"fixture_mag"),bulletItem=register(bullet,"fixture_bullet");
        var stack=new ItemStack(gunItem);var magazine=new ItemStack(magItem);
        check(GunMagazine.roundsRemaining(magazine)==0,"new EFT magazine is empty");
        GunMagazine.set(magazine,mag,0,new ItemStack(bulletItem,30));
        check(GunMagazine.roundsRemaining(magazine)==30,"real EFT magazine capacity");
        check(GunMagazine.acceptsMagazine(gun,mag),"MagType chain accepts 6L20");
        GunActions.boltCaught(stack,true);GunMagazine.set(stack,gun,0,magazine);
        check(GunActions.boltCaught(stack),"writing ammo retains other gun state");
        var loaded=GunMagazine.get(stack,gun,0);
        check(GunMagazine.magazineType(stack,gun)==mag,"magazine identity survives serialization");
        check(InfoType.getType(GunMagazine.chamberedBullet(loaded))==bullet,"fired bullet comes from the magazine");
        var before=stack.get(DataComponents.CUSTOM_DATA).copyTag();
        GunMagazine.roundsRemaining(loaded);GunMagazine.magazineType(stack,gun);GunMagazine.chamberedBullet(loaded);
        check(before.equals(stack.get(DataComponents.CUSTOM_DATA).copyTag()),"inspection reads never mutate ammo");
        for(int i=29;i>=0;i--) {check(GunMagazine.consumeRound(loaded),"round available "+i);check(GunMagazine.roundsRemaining(loaded)==i,"one shot spends exactly one round");}
        GunMagazine.set(stack,gun,0,loaded);
        check(!GunMagazine.get(stack,gun,0).isEmpty(),"empty EFT magazine remains installed");
        check(GunMagazine.magazineType(stack,gun)==mag && GunMagazine.isEmpty(stack,gun),"empty inspection keeps model and correct count");
        check(!GunMagazine.consumeRound(loaded),"empty mag cannot manufacture rounds");
		var inventory=new net.minecraft.world.entity.player.Inventory(null);
		gun.ammo.add(bullet);inventory.setItem(0,new ItemStack(bulletItem,64));
		var emptyMags=new ItemStack(magItem,2);inventory.setItem(1,emptyMags);
		GunMagazine.set(magazine,mag,0,new ItemStack(bulletItem,30));inventory.setItem(2,magazine.copy());
		check(GunMagazine.findAmmo(inventory,gun)==inventory.getItem(2),"filled magazine outranks 64 loose rounds and empty stacked magazines");
		inventory.setItem(2,ItemStack.EMPTY);
		check(GunMagazine.findAmmo(inventory,gun).isEmpty(),"empty stacked mags and loose bullets cannot replace actual magazine supply");
        GunMagazine.set(magazine,mag,0,new ItemStack(bulletItem,30));GunMagazine.set(stack,gun,0,magazine);
        check(GunMagazine.ensureChamber(stack,gun),"loading the chamber feeds one round");
        check(GunMagazine.roundsRemaining(GunMagazine.get(stack,gun,0))==29,"30-round mag has 29 after chambering");
        check(InfoType.getType(GunMagazine.getChamberedRound(stack))==bullet,"chamber preserves actual bullet type");
        check(!GunMagazine.ensureChamber(stack,gun),"tactical reload does not replace chambered round");
        for(int i=0;i<29;i++) check(!GunMagazine.spendChamberedRound(stack,gun).isEmpty(),"chambered shot "+i);
        check(GunMagazine.roundsRemaining(GunMagazine.get(stack,gun,0))==0,"empty mag before last chambered shot");
        check(!GunMagazine.getChamberedRound(stack).isEmpty() && !GunMagazine.isEmpty(stack,gun),"empty mag and loaded chamber coexist");
        check(!GunMagazine.spendChamberedRound(stack,gun).isEmpty(),"30th shot is the chambered round");
        check(GunMagazine.isEmpty(stack,gun) && GunMagazine.spendChamberedRound(stack,gun).isEmpty(),"31st shot cannot manufacture ammo");
        System.out.println("PASS: actual ItemStack/NBT round trips, real magazine/bullet identity, empty-mag preservation, 30 exact spends, read-only inspection and other gun-state preservation.");
        System.out.println("PASS: independent chamber, 29+1 initial load, tactical chamber preservation, empty-mag/loaded-chamber distinction, 30 total shots with no extra round.");
		regressions(gun,mag,bulletItem,gunItem,magItem);
		reequipRegression(gun,mag,gunItem,magItem,bulletItem);
		magazineOnlyRegression(gun,gunItem,bulletItem);
		mixedMagazineRegression(pack,gun,mag,gunItem,magItem,bulletItem);
		standardFlanAmmoRegression(bulletItem);
    }
	/** Standard Flan packs use Ammo/RoundsPerItem, without FMUM's Mag/MagType layer. */
	static void standardFlanAmmoRegression(Item foreignItem) {
		var ammoFile=new TypeFile(EnumType.bullet,"standard_flan_ammo",false);
		ammoFile.lines.addAll(java.util.List.of("ShortName standard_flan_ammo","RoundsPerItem 5"));
		var ammo=new BulletType(ammoFile);ammo.read(ammoFile);
		var ammoItem=register(ammo,"fixture_standard_flan_ammo");
		var gunFile=new TypeFile(EnumType.gun,"standard_flan_gun",false);
		gunFile.lines.addAll(java.util.List.of("ShortName standard_flan_gun","Ammo standard_flan_ammo","ReloadTime 20"));
		var gun=new GunType(gunFile);gun.read(gunFile);gun.ammo.add(ammo);
		var gunItem=register(gun,"fixture_standard_flan_gun");
		check(gun.magazineNames.isEmpty()&&!gun.allowDirectAmmoLoading,"standard Flan data does not declare FMUM detachable magazines or port-only opt-in");
		var inventory=new net.minecraft.world.entity.player.Inventory(null);
		inventory.setItem(0,new ItemStack(ammoItem,2));
		var held=new ItemStack(gunItem);var returned=new java.util.ArrayList<ItemStack>();
		check(GunMagazine.findAmmo(inventory,gun)==inventory.getItem(0),"ordinary Ammo definition accepts its declared five-round item without a Mag line");
		check(GunMagazine.reload(held,gun,inventory,false,returned::add),"standard Flan reload loads one actual ammunition item");
		check(inventory.getItem(0).getCount()==1&&returned.isEmpty(),"standard reload consumes exactly one item and returns nothing from empty gun");
		check(GunMagazine.findLoaded(held,gun)==0&&GunFiring.liveRound(held,gun,0)==ammo,"stored standard Flan ammo is available to the actual firing selector");
		for(int i=0;i<3;i++) {
			var loaded=GunMagazine.get(held,gun,0);check(GunMagazine.consumeRound(loaded),"standard ammo shot "+i);GunMagazine.set(held,gun,0,loaded);
		}
		check(GunMagazine.roundsRemaining(GunMagazine.get(held,gun,0))==2,"three shots leave two real uses in the ammunition item");
		check(!GunMagazine.reload(held,gun,inventory,false,returned::add),"automatic reload does not replace usable standard ammo");
		check(GunMagazine.reload(held,gun,inventory,true,returned::add),"manual reload exchanges standard Flan partially spent ammunition");
		check(inventory.getItem(0).isEmpty()&&returned.size()==1&&GunMagazine.roundsRemaining(returned.getFirst())==2,"manual reload returns exactly the two unused rounds");
		int shots=0;
		while(GunMagazine.findLoaded(held,gun)>=0) {
			var loaded=GunMagazine.get(held,gun,0);check(GunMagazine.consumeRound(loaded),"standard second ammunition item can fire");GunMagazine.set(held,gun,0,loaded);shots++;
		}
		check(shots==5&&3+shots+GunMagazine.roundsRemaining(returned.getFirst())==10,"two five-round items conserve all ten rounds across shots and tactical reload");
		var before=held.copy();inventory.setItem(0,new ItemStack(foreignItem,10));
		check(GunMagazine.findAmmo(inventory,gun).isEmpty()&&!GunMagazine.reload(held,gun,inventory,true,returned::add),"no-Mag fallback still rejects ammunition absent from the gun's Ammo list");
		check(ItemStack.matches(before,held)&&inventory.getItem(0).getCount()==10,"wrong standard ammunition leaves gun and reserve unchanged");
		System.out.println("PASS: standard Flan Ammo/RoundsPerItem format reloads and fires without FMUM Mag; partial return and ten-round conservation; wrong ammunition rejected; EFT magazine-only policy remains covered separately.");
	}
	static void reequipRegression(GunType gun,GunType mag,Item gunItem,Item magItem,Item bulletItem) {
		var full=new ItemStack(magItem);GunMagazine.set(full,mag,0,new ItemStack(bulletItem,30));
		var held=new ItemStack(gunItem);GunMagazine.set(held,gun,0,full);
		GunMagazine.setChamberedRound(held,new ItemStack(bulletItem));
		var original=held.copy();
		var oldMagazine=GunReloading.unload(held,gun);var afterUnload=held.copy();
		check(!ItemStack.matches(original,afterUnload),"actual unload changes held item components");
		check(!gunItem.shouldCauseReequipAnimation(original,afterUnload,false),"unloading must not replay vanilla equip dip");
		var inventory=new net.minecraft.world.entity.player.Inventory(null);inventory.setItem(2,full.copy());
		check(GunReloading.install(held,gun,inventory,2,full,ignored->{}),"actual insert for equip regression");
		var afterInsert=held.copy();
		check(!ItemStack.matches(afterUnload,afterInsert),"actual insert changes held item components");
		check(!gunItem.shouldCauseReequipAnimation(afterUnload,afterInsert,false),"inserting must not replay second vanilla equip dip");
		check(GunMagazine.roundsRemaining(oldMagazine)==30 && GunMagazine.roundsRemaining(GunMagazine.get(held,gun,0))==30
				&& GunMagazine.getChamberedRound(held).getCount()==1,"equip correction preserves real 30+1 reload data");
		GunMagazine.spendChamberedRound(held,gun);
		check(!gunItem.shouldCauseReequipAnimation(afterInsert,held.copy(),false),"firing updates do not replay equip dip");
		GunMagazine.setChamberedRound(held,ItemStack.EMPTY);
		check(!gunItem.shouldCauseReequipAnimation(afterInsert,held.copy(),false),"chamber updates do not replay equip dip");
		var empty=new ItemStack(magItem);var compressed=empty.copy();inventory.setItem(0,new ItemStack(bulletItem,30));
		check(GunReloading.compress(compressed,mag,inventory),"compression updates actual magazine components");
		check(!magItem.shouldCauseReequipAnimation(empty,compressed.copy(),false),"compression does not dip held magazine each round");
		for(int i=1;i<30;i++) {
			var previous=compressed.copy();
			check(GunReloading.compress(compressed,mag,inventory),"actual compression stroke "+(i+1));
			var synced=compressed.copy();
			check(!ItemStack.matches(previous,synced),"each actual round updates synced magazine data");
			check(!magItem.shouldCauseReequipAnimation(previous,synced,false),"compression stroke must keep magazine raised "+(i+1));
		}
		check(GunMagazine.roundsRemaining(compressed)==30 && inventory.getItem(0).isEmpty(),"30 raised compression strokes conserve all rounds");
		check(gunItem.shouldCauseReequipAnimation(original,original.copy(),true),"changing hotbar slots retains equip transition");
		check(gunItem.shouldCauseReequipAnimation(original,new ItemStack(magItem),false),"changing held item retains equip transition");
		check(gunItem.shouldCauseReequipAnimation(original,ItemStack.EMPTY,false),"putting away gun retains equip transition");
		check(bulletItem.shouldCauseReequipAnimation(new ItemStack(bulletItem),new ItemStack(bulletItem),false),"non-gun default equip policy retained");
		System.out.println("PASS: actual unload/insert component updates no longer trigger two vanilla equip dips; shot/chamber updates and 30 real compression strokes stay raised; slot/item/empty transitions and non-gun equip policy retained.");
	}
	static void regressions(GunType gun,GunType mag,Item bulletItem,Item gunItem,Item magItem)throws Exception {
		var inventory=new net.minecraft.world.entity.player.Inventory(null);
		var full=new ItemStack(magItem);GunMagazine.set(full,mag,0,new ItemStack(bulletItem,30));
		var legacy=new ItemStack(gunItem);GunMagazine.set(legacy,gun,0,new ItemStack(bulletItem));
		inventory.setItem(2,full.copy());var returned=new java.util.ArrayList<ItemStack>();
		check(GunMagazine.reload(legacy,gun,inventory,true,returned::add),"actual reload of legacy one-round gun");
		check(GunMagazine.roundsRemaining(GunMagazine.get(legacy,gun,0))==30,"legacy chamber plus incoming full mag stays 30/30");
		check(GunMagazine.getChamberedRound(legacy).getCount()==1 && returned.isEmpty(),"legacy chamber never returned to backpack");
		int shots=0;while(!GunMagazine.spendChamberedRound(legacy,gun).isEmpty())shots++;
		check(shots==31,"legacy migration preserves exactly 30+1 shots");
		var tactical=new ItemStack(gunItem);GunMagazine.setChamberedRound(tactical,new ItemStack(bulletItem));
		GunMagazine.set(tactical,gun,0,full);inventory.setItem(2,full.copy());
		returned.clear();returned.add(GunReloading.unload(tactical,gun));
		check(!GunMagazine.getChamberedRound(tactical).isEmpty(),"out-mag step leaves chamber loaded");
		check(GunReloading.install(tactical,gun,inventory,2,full,returned::add),"in-mag step consumes actual pinned magazine");
		check(!GunMagazine.ensureChamber(tactical,gun),"tactical charge never consumes a new mag round");
		check(GunMagazine.roundsRemaining(GunMagazine.get(tactical,gun,0))==30,"phase reload displays full 30/30 plus chamber");
		check(returned.size()==1 && GunMagazine.roundsRemaining(returned.get(0))==30,"only old magazine returned");
		shots=0;while(!GunMagazine.spendChamberedRound(tactical,gun).isEmpty())shots++;
		check(shots==31,"real phase exchange preserves exactly 31 shots");
		var emptyGun=new ItemStack(gunItem);inventory.setItem(2,full.copy());
		check(GunReloading.install(emptyGun,gun,inventory,2,full,returned::add),"empty chamber installation");
		check(GunMagazine.roundsRemaining(GunMagazine.get(emptyGun,gun,0))==30 && GunMagazine.getChamberedRound(emptyGun).isEmpty(),"in-mag stage does not chamber before charge stage");
		check(GunMagazine.ensureChamber(emptyGun,gun),"charge phase feeds first round");
		check(GunMagazine.roundsRemaining(GunMagazine.get(emptyGun,gun,0))==29,"empty chamber correctly yields 29+1");
		var cancelled=new ItemStack(gunItem);var before=cancelled.copy();inventory.setItem(2,ItemStack.EMPTY);
		check(!GunReloading.install(cancelled,gun,inventory,2,full,returned::add),"moved spare magazine cannot be manufactured from preview snapshot");
		check(ItemStack.isSameItemSameComponents(cancelled,before),"failed insert leaves firearm unchanged");
		var compressed=new ItemStack(magItem);inventory.setItem(0,new ItemStack(bulletItem,64));
		var clock=new GunReloadState();clock.start(GunReloadState.compress(mag,30));
		int ticks=0,rounds=0;boolean applied=false;
		while(clock.active()) {
			if(!applied) {check(GunReloading.compress(compressed,mag,inventory),"compression adds real round "+rounds);applied=true;rounds++;}
			if(clock.tick()!=GunReloadState.Step.NONE) applied=false;
			ticks++;
		}
		check(rounds==30 && ticks==240,"30 round compression follows real eight-tick steps");
		check(GunMagazine.roundsRemaining(compressed)==30 && inventory.getItem(0).getCount()==34,"compression conserves all 64 rounds");
		check(!GunReloading.compress(compressed,mag,inventory),"full magazine cannot exceed its real capacity");
		var creative=new ItemStack(magItem);int reserve=inventory.getItem(0).getCount();
		check(GunReloading.compress(creative,mag,inventory,false),"creative compression adds one real round");
		check(GunMagazine.roundsRemaining(creative)==1 && inventory.getItem(0).getCount()==reserve,"creative compression leaves reserve untouched");
		check(GunReloadState.compress(mag,1,true).duration()==1,"creative compression bypasses survival delay");
		var direct=new ItemStack(gunItem);int directReserve=inventory.getItem(0).getCount();
		check(!GunReloading.compress(direct,gun,inventory),"default policy rejects direct loose-round loading");
		check(GunMagazine.getChamberedRound(direct).isEmpty() && inventory.getItem(0).getCount()==directReserve,"rejected direct loading changes no ammo");
		gun.allowDirectAmmoLoading=true;
		try {
			check(GunReloading.compress(direct,gun,inventory),"explicit opt-in enables direct loose round into chamber");
			check(GunMagazine.get(direct,gun,0).isEmpty() && !GunMagazine.getChamberedRound(direct).isEmpty(),"opt-in direct loading never occupies mag slot");
			check(!GunReloading.compress(direct,gun,inventory),"opt-in direct loading cannot overwrite occupied chamber");
		} finally {gun.allowDirectAmmoLoading=false;}
		verifyPayload(full,gun,mag);
		System.out.println("PASS: reported legacy x1 -> full magazine regression, no chamber return, 30+1/31 exact shots, phase insert then charge 29+1, moved-spare cancellation, timed compression, creative reserve preservation and reload payload/NBT round trip.");
	}
	static void magazineOnlyRegression(GunType gun,Item gunItem,Item bulletItem) {
		var inventory=new net.minecraft.world.entity.player.Inventory(null);inventory.setItem(0,new ItemStack(bulletItem,30));
		var emptyGun=new ItemStack(gunItem);var before=emptyGun.copy();
		check(GunMagazine.findAmmo(inventory,gun).isEmpty(),"manual and automatic reload selectors reject loose-round-only reserve");
		check(!GunMagazine.reload(emptyGun,gun,inventory,true,ignored->{throw new AssertionError("no item should be returned");}),"legacy reload entry also rejects direct feeding");
		check(ItemStack.isSameItemSameComponents(before,emptyGun) && inventory.getItem(0).getCount()==30,"failed default reload leaves gun and reserve unchanged");
		var legacy=new ItemStack(gunItem);GunMagazine.set(legacy,gun,0,new ItemStack(bulletItem,30));before=legacy.copy();
		check(GunMagazine.findLoaded(legacy,gun)<0 && !GunMagazine.ensureChamber(legacy,gun),"legacy loose-round slot is not an automatic direct supply");
		check(ItemStack.isSameItemSameComponents(before,legacy),"disabled legacy supply does not delete saved ammo");
		GunMagazine.setChamberedRound(emptyGun,new ItemStack(bulletItem));
		check(GunMagazine.findLoaded(emptyGun,gun)==0,"existing chamber round remains usable without a magazine");
		check(!GunMagazine.spendChamberedRound(emptyGun,gun).isEmpty(),"existing chamber round fires once");
		check(GunMagazine.isEmpty(emptyGun,gun) && inventory.getItem(0).getCount()==30,"empty chamber cannot feed from loose reserve afterward");
		var file=new TypeFile(EnumType.gun,"direct_feed_opt_in",false);
		file.lines.add("ShortName direct_feed_opt_in");file.lines.add("AllowDirectAmmoLoading true");
		var optIn=new GunType(file);optIn.read(file);check(optIn.allowDirectAmmoLoading,"definition may explicitly opt in to direct loading");
		System.out.println("PASS: all 31 EFT firearms default to magazine supply; manual/automatic/legacy selectors and direct chamber loading reject loose ammo; saved ammo and existing chamber preserved; explicit definition opt-in parsed.");
	}
	static void verifyPayload(ItemStack full,GunType gun,GunType mag)throws Exception {
		// This isolated bootstrap never runs NeoForge's registry setup. Initialize the two
		// ID-synced registries used by the real ItemStack codec, only inside this test JVM.
		var sync=net.neoforged.neoforge.registries.BaseMappedRegistry.class.getDeclaredMethod("setSync",boolean.class);
		sync.setAccessible(true);sync.invoke(BuiltInRegistries.ITEM,true);sync.invoke(BuiltInRegistries.DATA_COMPONENT_TYPE,true);
		var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
				net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
		try {
			var sent=new com.flansmod.common.network.ReloadAnimationPayload(2,gun.shortName,true,true,true,false,0,full,
					com.flansmod.common.network.ReloadPayload.Action.SPECIAL);
			var codec=com.flansmod.common.network.ReloadAnimationPayload.STREAM_CODEC;
			codec.encode(buffer,sent);var received=codec.decode(buffer);
			check(received.slot()==2 && received.gun().equals(gun.shortName) && received.active() && received.hasMagazine()
					&& received.hasChamber() && !received.caught() && received.compressRounds()==0
					&& received.action()==com.flansmod.common.network.ReloadPayload.Action.SPECIAL,"reload payload preserves accepted special plan flags");
			check(InfoType.getType(received.incoming())==mag && GunMagazine.roundsRemaining(received.incoming())==GunMagazine.roundsRemaining(full),
					"reload payload preserves actual incoming magazine and nested bullet data");
			for(int i=0;i<GunMagazine.roundsRemaining(full);i++)
				check(ItemStack.isSameItemSameComponents(GunMagazine.magazineRoundAt(full,mag,i),GunMagazine.magazineRoundAt(received.incoming(),mag,i)),"network retains mixed round order and components "+i);
			check(!buffer.isReadable(),"reload payload round trip consumes exactly its data");
			for(var action:com.flansmod.common.network.ReloadPayload.Action.values()) {
				var request=new com.flansmod.common.network.ReloadPayload(false,action,2);
				com.flansmod.common.network.ReloadPayload.STREAM_CODEC.encode(buffer,request);
				check(request.equals(com.flansmod.common.network.ReloadPayload.STREAM_CODEC.decode(buffer)) && !buffer.isReadable(),"observed reload command wire round trip "+action);
			}
		} finally {buffer.release();}
	}
	static void mixedMagazineRegression(OfflinePack pack,GunType gun,GunType mag,Item gunItem,Item magItem,Item bulletItem)throws Exception {
		var tracer=(BulletType)pack.types.stream().filter(t->"5.45x39mmBT".equals(t.shortName)).findFirst().orElseThrow();
		var foreign=(BulletType)pack.types.stream().filter(t->t instanceof BulletType b && "556x45".equals(b.ammoType)).findFirst().orElseThrow();
		Item tracerItem=register(tracer,"fixture_tracer"),foreignItem=register(foreign,"fixture_foreign");
		var inventory=new net.minecraft.world.entity.player.Inventory(null);
		inventory.setItem(0,new ItemStack(bulletItem,2));inventory.setItem(1,new ItemStack(tracerItem,2));
		var mixed=new ItemStack(magItem);
		for(int i=0;i<4;i++) check(GunReloading.compress(mixed,mag,inventory),"load differing compatible ammo from first inventory slot "+i);
		inventory.setItem(0,new ItemStack(bulletItem));check(GunReloading.compress(mixed,mag,inventory),"return to first bullet type in mixed mag");
		Item[] loadedOrder={bulletItem,bulletItem,tracerItem,tracerItem,bulletItem};
		check(GunMagazine.roundsRemaining(mixed)==5 && inventory.getItem(0).isEmpty() && inventory.getItem(1).isEmpty(),"mixed compression preserves exact total and reserve");
		for(int i=0;i<5;i++) check(GunMagazine.magazineRoundAt(mixed,mag,i).getItem()==loadedOrder[i],"actual bottom-to-mouth mixed sequence "+i);
		check(GunMagazine.magazineRoundAt(mixed,mag,5).isEmpty(),"no imaginary seated round");
		verifyPayload(mixed,gun,mag);
		var held=new ItemStack(gunItem);GunMagazine.set(held,gun,0,mixed);
		check(GunMagazine.ensureChamber(held,gun),"mixed mag first chambering");
		for(int i=4;i>=0;i--) check(GunMagazine.spendChamberedRound(held,gun).getItem()==loadedOrder[i],"mixed gun fires last loaded round first per EFT core "+i);
		check(GunMagazine.isEmpty(held,gun) && GunMagazine.roundsRemaining(GunMagazine.get(held,gun,0))==0,"five mixed rounds produce exactly five shots, retaining empty real mag");
		inventory.setItem(0,new ItemStack(foreignItem,10));var before=mixed.copy();
		check(!GunReloading.compress(mixed,mag,inventory) && ItemStack.isSameItemSameComponents(before,mixed) && inventory.getItem(0).getCount()==10,"wrong caliber rejected without mutating mag or reserve");
		var legacy=new ItemStack(magItem);GunMagazine.set(legacy,mag,0,new ItemStack(bulletItem,2));
		inventory.setItem(0,new ItemStack(tracerItem));check(GunReloading.compress(legacy,mag,inventory),"new mixed bullet can be added to legacy counted magazine");
		check(GunMagazine.roundsRemaining(legacy)==3 && GunMagazine.chamberedBullet(legacy).getItem()==tracerItem,"legacy counted rounds preserved beneath newly added tracer");
		check(GunMagazine.consumeRound(legacy) && GunMagazine.roundsRemaining(legacy)==2 && GunMagazine.chamberedBullet(legacy).getItem()==bulletItem,"feeding tracer exposes legacy original type");
		var full=new ItemStack(magItem);inventory.setItem(0,new ItemStack(bulletItem,15));inventory.setItem(1,new ItemStack(tracerItem,15));
		for(int i=0;i<30;i++) check(GunReloading.compress(full,mag,inventory),"full mixed magazine stroke "+i);
		check(!GunReloading.compress(full,mag,inventory) && GunMagazine.roundsRemaining(full)==30,"full mixed capacity strictly enforced");
		verifyPayload(full,gun,mag);
		var returned=new java.util.ArrayList<ItemStack>();held=new ItemStack(gunItem);GunMagazine.set(held,gun,0,full);
		GunMagazine.setChamberedRound(held,new ItemStack(bulletItem));
		var unloadPlan=GunReloadState.firearm(gun,true,true,false,true);
		check(unloadPlan.phases().stream().anyMatch(p->p.step()==GunReloadState.Step.UNLOAD_MAG)
				&& unloadPlan.phases().stream().noneMatch(p->p.step()==GunReloadState.Step.LOAD_MAG || p.step()==GunReloadState.Step.CHARGE || p.step()==GunReloadState.Step.RELEASE),"advanced operation unloads only, never loads or charges");
		returned.add(GunReloading.unload(held,gun));
		check(GunMagazine.magazineType(held,gun)==null && GunMagazine.roundsRemaining(returned.getFirst())==30
				&& GunMagazine.getChamberedRound(held).getItem()==bulletItem,"advanced unload returns actual mixed mag and preserves old chamber");
		var empty=new ItemStack(magItem);inventory.setItem(0,empty);inventory.setItem(1,full.copy());
		check(GunMagazine.firstMagazine(inventory,gun)==inventory.getItem(0) && GunMagazine.findAmmo(inventory,gun)==inventory.getItem(1),"advanced chooses first actual mag including empty, normal chooses loaded mag");
		var loadPlan=GunReloadState.firearm(gun,false,false,false,true);
		check(loadPlan.phases().stream().anyMatch(p->p.step()==GunReloadState.Step.LOAD_MAG)
				&& loadPlan.phases().stream().noneMatch(p->p.step()==GunReloadState.Step.UNLOAD_MAG || p.step()==GunReloadState.Step.CHARGE || p.step()==GunReloadState.Step.RELEASE),"advanced operation with no magazine inserts only");
		var emptyGun=new ItemStack(gunItem);
		check(GunReloading.install(emptyGun,gun,inventory,0,empty,returned::add,true) && GunMagazine.getChamberedRound(emptyGun).isEmpty(),"advanced can install actual empty magazine while preserving empty chamber");
		GunReloading.unload(emptyGun,gun);check(GunReloading.install(emptyGun,gun,inventory,1,full,returned::add,true),"advanced inserts actual full mixed mag");
		check(GunMagazine.getChamberedRound(emptyGun).isEmpty() && GunMagazine.roundsRemaining(GunMagazine.get(emptyGun,gun,0))==30,"advanced insert does not silently chamber or spend ammunition");
		GunReloading.charge(emptyGun,gun,returned::add);
		check(GunMagazine.getChamberedRound(emptyGun).getItem()==tracerItem && GunMagazine.roundsRemaining(GunMagazine.get(emptyGun,gun,0))==29,"manual charge feeds actual top tracer after special insert");
		var ejected=new java.util.ArrayList<ItemStack>();GunReloading.charge(emptyGun,gun,ejected::add);
		check(ejected.size()==1 && ejected.getFirst().getItem()==tracerItem && GunMagazine.roundsRemaining(GunMagazine.get(emptyGun,gun,0))==28,"manual charge ejects old live round once, then feeds next actual round");
		System.out.println("PASS: actual mixed 7n39/BT compression, exact last-in-first-out firing types, wrong-caliber rejection, legacy counted-mag compatibility, 30-round mixed capacity and network preservation; advanced unload-only/insert-only including empty mag, no silent chambering, manual charge and exact live-round ejection.");
	}
    static Item register(InfoType type,String name) {
        return Registry.register(BuiltInRegistries.ITEM,ResourceLocation.fromNamespaceAndPath("flansmod",name),new ItemFlan(type,new Item.Properties()));
    }
}
