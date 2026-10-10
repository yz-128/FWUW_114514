package com.flansmod.common;

import com.flansmod.common.GunReloadState.Step;
import com.flansmod.common.network.ReloadAnimationPayload;
import com.flansmod.common.network.ReloadPayload;
import com.flansmod.common.network.ReloadPayload.Action;
import com.flansmod.common.types.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** EFT magazine reloads: authoritative inventory effects at the actual animation steps. */
public final class GunReloading {
	private GunReloading() { }
	public static boolean supports(GunType gun) {
		return gun.itsType==GunType.GunItsType.MAG?gun.ammoCapacity>0 && !gun.reloadTimes.isEmpty()
				:!gun.magazineNames.isEmpty() && !gun.reloadSteps.isEmpty();
	}
	public static boolean start(Player player,PlayerData data,ItemStack stack,GunType gun) {
		return start(player,data,stack,gun,Integer.MAX_VALUE);
	}
	public static boolean start(Player player,PlayerData data,ItemStack stack,GunType gun,int compressionLimit) {
		return start(player,data,stack,gun,compressionLimit,Action.NORMAL);
	}
	public static boolean start(Player player,PlayerData data,ItemStack stack,GunType gun,int compressionLimit,Action action) {
		if(player.level().isClientSide || data.gunReload.active()) return false;
		GunActions.ensureHeld(player,data);
		Inventory inventory=player.getInventory();
		boolean special=action==Action.SPECIAL,manual=action==Action.CHARGE || action==Action.RELEASE_BOLT;
		boolean installed=GunMagazine.magazineType(stack,gun)!=null;
		ItemStack found=manual || (special && installed)?ItemStack.EMPTY
				:special?GunMagazine.firstMagazine(inventory,gun):GunMagazine.findAmmo(inventory,gun);
		if(found.isEmpty() && !manual && !(special && installed)) return false;
		int slot=-1;
		if(!found.isEmpty()) for(int i=0;i<inventory.getContainerSize();i++) if(inventory.getItem(i)==found) {slot=i;break;}
		GunReloadState.Plan plan;
		int rounds=0;
		boolean chamber=false,hasMagazine=false,caught=GunActions.boltCaught(stack);
		if(gun.itsType==GunType.GunItsType.MAG) {
			rounds=Math.min(compressionLimit,Math.min(Math.max(0,gun.ammoCapacity-GunMagazine.roundsRemaining(stack)),
					player.getAbilities().instabuild?gun.ammoCapacity:found.getCount()));
			if(rounds==0) return false;
			plan=GunReloadState.compress(gun,rounds,player.getAbilities().instabuild);
		} else {
			GunMagazine.normalizeChamber(stack,gun);
			chamber=!GunMagazine.getChamberedRound(stack).isEmpty();
			hasMagazine=installed;
			if(manual) {
				if(action==Action.CHARGE && gun.chargeTime<1) return false;
				if(action==Action.RELEASE_BOLT && (!caught || gun.releaseSlideTime<1)) return false;
				plan=GunReloadState.operate(gun,action==Action.CHARGE?Step.CHARGE:Step.RELEASE);
			} else if(special || InfoType.getType(found) instanceof GunType)
				plan=GunReloadState.firearm(gun,hasMagazine,chamber,caught,special);
			else {
				if(!gun.allowDirectAmmoLoading || chamber) return false;
				// A single loose round goes into the barrel, never the magazine slot.
				plan=new GunReloadState.Plan(java.util.List.of(new GunReloadState.Phase(Step.LOAD_AMMO,
						Math.max(1,gun.chargeTime),-1)));
				rounds=1;
			}
		}
		data.reloadGun=gun;data.reloadGunStack=stack;data.reloadSlot=inventory.selected;data.reloadIncomingSlot=slot;
		data.reloadAction=action;
		data.reloadIncomingMagazine=found.copy();if(!found.isEmpty()) data.reloadIncomingMagazine.setCount(1);
		data.reloadOutgoingMagazine=GunMagazine.get(stack,gun,0).copy();
		begin(data,plan);
		if(player instanceof ServerPlayer server) PacketDistributor.sendToPlayer(server,new ReloadAnimationPayload(
				data.reloadSlot,gun.shortName,true,hasMagazine,chamber,caught,rounds,data.reloadIncomingMagazine.copy(),action));
		return true;
	}
	private static void begin(PlayerData data,GunReloadState.Plan plan) {
		data.gunReload.start(plan);data.reloadEffectApplied=data.reloadSoundPlayed=false;
		data.reloadingRight=data.gunReload.active();data.shootTimeRight=plan.duration();
		data.burstRoundsRemainingRight=0;
	}
	public static void accept(Player player,ReloadAnimationPayload payload) {
		if(payload.slot()!=player.getInventory().selected) return;
		GunType gun=InfoType.getType(player.getMainHandItem()) instanceof GunType g?g:null;
		if(gun==null || !payload.gun().equals(gun.shortName)) return;
		PlayerData data=PlayerHandler.getPlayerData(player);
		GunActions.ensureHeld(player,data);
		if(!payload.active()) { cancel(player,data);return; }
		data.reloadGun=gun;data.reloadSlot=payload.slot();data.reloadGunStack=player.getMainHandItem();
		data.reloadAction=payload.action();
		data.reloadIncomingMagazine=payload.incoming().copy();
		data.reloadOutgoingMagazine=GunMagazine.get(player.getMainHandItem(),gun,0).copy();
		GunReloadState.Plan plan=payload.compressRounds()>0?GunReloadState.compress(gun,payload.compressRounds(),player.getAbilities().instabuild)
				:GunReloadState.firearm(gun,payload.hasMagazine(),payload.hasChamber(),payload.caught(),payload.action()==Action.SPECIAL);
		if(payload.action()==Action.CHARGE || payload.action()==Action.RELEASE_BOLT)
			plan=GunReloadState.operate(gun,payload.action()==Action.CHARGE?Step.CHARGE:Step.RELEASE);
		if(gun.itsType!=GunType.GunItsType.MAG && payload.compressRounds()>0)
			plan=new GunReloadState.Plan(java.util.List.of(new GunReloadState.Phase(Step.LOAD_AMMO,Math.max(1,gun.chargeTime),-1)));
		begin(data,plan);
	}
	public static void tick(Player player,PlayerData data) {
		if(!player.level().isClientSide && data.boltChargeDelay>=0) {
			if(data.gunReload.active() || player.getMainHandItem()!=data.boltChargeStack) {data.boltChargeDelay=-1;data.boltChargeStack=ItemStack.EMPTY;}
			else if(data.boltChargeDelay--==0) {
				var gun=data.actionGun;var stack=player.getMainHandItem();
				if(gun!=null && gun.itsType==GunType.GunItsType.BOLT_GUN && GunMagazine.roundsRemaining(GunMagazine.get(stack,gun,0))>0)
					start(player,data,stack,gun,1,Action.CHARGE);
				data.boltChargeStack=ItemStack.EMPTY;
			}
		}
		if(!player.level().isClientSide && data.compressionHeld) {
			if(!player.isAlive() || player.getInventory().selected!=data.compressionSlot
					|| player.getMainHandItem()!=data.compressionStack || InfoType.getType(player.getMainHandItem())!=data.compressionGun
					|| player.containerMenu!=player.inventoryMenu) stopCompression(player,data);
			else if(!data.gunReload.active()) start(player,data,player.getMainHandItem(),data.compressionGun,1);
		}
		if(!data.gunReload.active()) return;
		if(!player.isAlive() || player.getInventory().selected!=data.reloadSlot
				|| InfoType.getType(player.getMainHandItem())!=data.reloadGun
				|| (!player.level().isClientSide && player.getMainHandItem()!=data.reloadGunStack)) {
			cancel(player,data);return;
		}
		var phase=data.gunReload.phase();
		if(!player.level().isClientSide) {
			float progress=data.gunReload.frame(phase.step()==Step.LOAD_AMMO?1F:0F).progress();
			boolean soundDue=switch(phase.step()) {
				case LOAD_MAG -> progress<=data.reloadGun.pushMagSoundTime;
				case LOAD_AMMO -> progress>=data.reloadGun.pushBulletSoundTime;
				default -> true;
			};
			if(!data.reloadSoundPlayed && soundDue) {
				if(phase.soundIndex()>=0 && phase.soundIndex()<data.reloadGun.reloadSounds.size())
					GunActions.sound(player,data.reloadGun.reloadSounds.get(phase.soundIndex()),
							phase.soundIndex()<data.reloadGun.reloadSoundRanges.size()?data.reloadGun.reloadSoundRanges.get(phase.soundIndex()):8F);
				data.reloadSoundPlayed=true;
				if(data.reloadAction==Action.CHARGE) GunActions.sound(player,data.reloadGun.chargeSound[0],data.reloadGun.chargeSoundR[0]);
				if(data.reloadAction==Action.RELEASE_BOLT) GunActions.sound(player,data.reloadGun.releaseBoltSound[0],data.reloadGun.releaseBoltSoundR[0]);
			}
			boolean apply=phase.step()==Step.LOAD_AMMO?progress>=data.reloadGun.pushBulletSoundTime
					:(phase.step()==Step.CHARGE || phase.step()==Step.RELEASE) && data.gunReload.elapsed()>=phase.ticks()/2;
			if(apply && !data.reloadEffectApplied) {
				if(phase.step()==Step.LOAD_AMMO && !compress(player.getMainHandItem(),data.reloadGun,player.getInventory(),!player.getAbilities().instabuild)) {
					cancel(player,data);return;
				}
				if(phase.step()==Step.CHARGE || phase.step()==Step.RELEASE) {
					if(phase.step()==Step.CHARGE) {
						var old=GunCasings.retained(player.getMainHandItem());
						if(old==null && InfoType.getType(GunMagazine.getChamberedRound(player.getMainHandItem())) instanceof BulletType bullet)old=bullet;
						GunCasings.eject(player,data.reloadGun,old,true);
						charge(player.getMainHandItem(),data.reloadGun,round->player.drop(round,false));
					} else { GunMagazine.ensureChamber(player.getMainHandItem(),data.reloadGun);GunActions.boltCaught(player.getMainHandItem(),false); }
				}
				data.reloadEffectApplied=true;
			}
		}
		Step completed=data.gunReload.tick();
		if(completed!=Step.NONE) {
			if(!player.level().isClientSide) {
				if(completed==Step.CHARGE && data.reloadAction==Action.CHARGE) GunActions.sound(player,data.reloadGun.chargeSound[1],data.reloadGun.chargeSoundR[1]);
				if(completed==Step.RELEASE && data.reloadAction==Action.RELEASE_BOLT) GunActions.sound(player,data.reloadGun.releaseBoltSound[1],data.reloadGun.releaseBoltSoundR[1]);
				if(completed==Step.UNLOAD_MAG) returnItem(player,unload(player.getMainHandItem(),data.reloadGun));
				if(completed==Step.LOAD_MAG && !install(player.getMainHandItem(),data.reloadGun,player.getInventory(),
						data.reloadIncomingSlot,data.reloadIncomingMagazine,returned->returnItem(player,returned),data.reloadAction==Action.SPECIAL)) {
					cancel(player,data);return;
				}
			}
			data.reloadEffectApplied=data.reloadSoundPlayed=false;
		}
		if(!data.gunReload.active()) cancel(player,data);
	}
	/** Pure item/inventory operations used unchanged by the server and the offline regressions. */
	public static boolean compress(ItemStack stack,GunType gun,Inventory inventory) {
		return compress(stack,gun,inventory,true);
	}
	public static boolean compress(ItemStack stack,GunType gun,Inventory inventory,boolean consume) {
		if(gun.itsType==GunType.GunItsType.MAG) return GunMagazine.loadOneBullet(stack,gun,inventory,consume);
		if(!gun.allowDirectAmmoLoading) return false;
		if(!GunMagazine.getChamberedRound(stack).isEmpty()) return false;
		ItemStack found=GunMagazine.findAmmo(inventory,gun);
		if(!(InfoType.getType(found) instanceof BulletType)) return false;
		ItemStack bullet=found.copy();bullet.setCount(1);if(consume)found.shrink(1);GunMagazine.setChamberedRound(stack,bullet);
		return true;
	}
	public static ItemStack unload(ItemStack stack,GunType gun) {
		GunMagazine.normalizeChamber(stack,gun);
		ItemStack old=GunMagazine.get(stack,gun,0);GunMagazine.set(stack,gun,0,ItemStack.EMPTY);return old;
	}
	public static boolean install(ItemStack stack,GunType gun,Inventory inventory,int slot,ItemStack expected,
			java.util.function.Consumer<ItemStack> returnItem) {
		return install(stack,gun,inventory,slot,expected,returnItem,false);
	}
	public static boolean install(ItemStack stack,GunType gun,Inventory inventory,int slot,ItemStack expected,
			java.util.function.Consumer<ItemStack> returnItem,boolean allowEmpty) {
		if(slot<0 || slot>=inventory.getContainerSize()) return false;
		ItemStack found=inventory.getItem(slot);
		if(!ItemStack.isSameItemSameComponents(found,expected) || found.isEmpty()
				|| !(InfoType.getType(found) instanceof GunType mag) || !GunMagazine.acceptsMagazine(gun,mag)
				|| (!allowEmpty && GunMagazine.roundsRemaining(found)<=0)) return false;
		GunMagazine.normalizeChamber(stack,gun);
		ItemStack old=GunMagazine.get(stack,gun,0),incoming=found.copy();incoming.setCount(1);found.shrink(1);
		GunMagazine.set(stack,gun,0,incoming);
		if(!old.isEmpty()) returnItem.accept(old);
		return true;
	}
	/** Manual charge ejects the old live round before feeding, as the EFT core does. */
	public static void charge(ItemStack stack,GunType gun,java.util.function.Consumer<ItemStack> eject) {
		GunCasings.retain(stack,null);
		ItemStack old=GunMagazine.getChamberedRound(stack);
		if(!old.isEmpty()) { GunMagazine.setChamberedRound(stack,ItemStack.EMPTY);eject.accept(old); }
		boolean fed=GunMagazine.ensureChamber(stack,gun);
		GunActions.boltCaught(stack,!fed && gun.releaseSlideTime>0);
	}
	public static void handleRequest(Player player,ReloadPayload request) {
		PlayerData data=PlayerHandler.getPlayerData(player);
		if(request.action()==Action.COMPRESS_STOP) { stopCompression(player,data);return; }
		if(request.slot()!=player.getInventory().selected) return;
		ItemStack stack=player.getMainHandItem();
		if(request.action()==Action.COMPRESS_START) {
			if(request.offHand() || !(InfoType.getType(stack) instanceof GunType gun) || gun.itsType!=GunType.GunItsType.MAG) return;
			GunActions.ensureHeld(player,data);
			data.compressionHeld=true;data.compressionSlot=request.slot();data.compressionGun=gun;data.compressionStack=stack;
			return;
		}
		GunFiring.requestReload(player,request.offHand(),request.action());
	}
	public static void stopCompression(Player player,PlayerData data) {
		data.compressionHeld=false;data.compressionSlot=-1;data.compressionGun=null;data.compressionStack=ItemStack.EMPTY;
		if(data.reloadGun!=null && data.reloadGun.itsType==GunType.GunItsType.MAG) cancel(player,data);
	}
	private static void returnItem(Player player,ItemStack returned) {
		if(!returned.isEmpty() && !player.getInventory().add(returned)) player.drop(returned,false);
	}
	public static void cancel(Player player,PlayerData data) {
		if(!player.level().isClientSide && data.reloadGun!=null && player instanceof ServerPlayer server)
			PacketDistributor.sendToPlayer(server,new ReloadAnimationPayload(data.reloadSlot,data.reloadGun.shortName,
					false,false,false,false,0,ItemStack.EMPTY,Action.NORMAL));
		data.gunReload.reset();data.reloadingRight=false;data.shootTimeRight=0F;
		data.reloadGun=null;data.reloadGunStack=data.reloadIncomingMagazine=data.reloadOutgoingMagazine=ItemStack.EMPTY;
	}
}
