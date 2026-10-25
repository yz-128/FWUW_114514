package com.flansmod.common;

import com.flansmod.common.GunActionState.Operation;
import com.flansmod.common.types.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import java.util.List;

/** The same operation clock on both sides; inspection results come only from the server. */
public final class GunActions {
	private static final java.util.Map<GunType,com.flansmod.client.model.loader.ModelGeometry.Model> ACTION_MODELS=new java.util.concurrent.ConcurrentHashMap<>();
	private GunActions() { }
	public static GunType firearm(ItemStack stack) {
		return InfoType.getType(stack) instanceof GunType g && g.itsType != GunType.GunItsType.MAG ? g : null;
	}
	public static void ensureHeld(Player player, PlayerData data) {
		GunType gun = player.isAlive()?firearm(player.getMainHandItem()):null;
		int slot = player.getInventory().selected;
		int attachmentRevision=GunAttachments.revision(player.getMainHandItem());
		if(gun == data.actionGun && slot == data.actionSlot && player.level() == data.actionLevel && attachmentRevision==data.actionAttachmentRevision) return;
		if(data.gunReload.active()) GunReloading.cancel(player,data);
		data.actionGun = gun; data.actionSlot = slot; data.actionLevel = player.level();
		data.actionAttachmentRevision=attachmentRevision;
		data.sightFoldUntilTick=0;
		data.effectiveTakeOut = gun == null ? 0F : gun.takeOutTime * takeOutMultiplier(GunAttachments.read(gun,player.getMainHandItem()), 0);
		data.gunAction.equip(data.effectiveTakeOut);
		data.pendingGunReload = data.pendingGunTrigger = data.isShootingRight = false;
		data.reloadingRight = false; data.shootTimeRight = 0F;
		data.gunAimProgress=0F; data.gunWallRay=null;
		data.burstRoundsRemainingRight = 0;
		data.boltChargeDelay=-1;
		data.boltChargeStack=ItemStack.EMPTY;
	}
	private static float takeOutMultiplier(List<List<ModifiableType.DefaultAttachment>> groups, int depth) {
		float value = 1F;
		for(var nodes : groups) value *= takeOutNodes(nodes, depth);
		return value;
	}
	private static float takeOutNodes(List<ModifiableType.DefaultAttachment> nodes, int depth) {
		if(depth > 16) return 1F;
		float value = 1F;
		for(var node : nodes) {
			AttachmentType at = AttachmentType.getAttachment(node.name);
			if(at != null) value *= at.takeOutTimeMult * takeOutNodes(node.children, depth + 1);
		}
		return value;
	}
	public static boolean ready(Player player, PlayerData data) {
		ensureHeld(player, data);
		return !data.sightFolding(player) && data.gunAction.ready() && !nearWall(player, data.actionGun);
	}
	public static boolean request(Player player, Operation requested) {
		PlayerData data = PlayerHandler.getPlayerData(player);
		if(data == null) return false;
		ensureHeld(player, data);
		GunType gun = data.actionGun;
		if(gun == null || data.sightFolding(player) || data.isReloading() || data.pendingGunReload || data.pendingGunTrigger) return false;
		if(requested == Operation.REST) { data.gunAction.toggleRest(); return true; }
		float length;
		if(requested == Operation.CHECK_AMMO) {
			if(GunMagazine.get(player.getMainHandItem(), gun, 0).isEmpty()) return false;
			if(!gun.magazineNames.isEmpty() && GunMagazine.magazineType(player.getMainHandItem(),gun)==null) return false;
			length = gun.checkAmmoTime;
		} else if(requested == Operation.CHECK_CHAMBER || (player.level().isClientSide && requested == Operation.CHECK_CHAMBER_BC)) {
			if(requested != Operation.CHECK_CHAMBER_BC)
				requested = boltCaught(player.getMainHandItem()) ? Operation.CHECK_CHAMBER_BC : Operation.CHECK_CHAMBER;
			length = gun.checkChamberTime[requested == Operation.CHECK_CHAMBER_BC ? 1 : 0];
		} else return false;
		if(length <= 0F) return false;
		data.gunAction.request(requested, length);
		return true;
	}
	/**
	 * FMUM ItemGun dismissed a manual stow when the fire or the aim key was down
	 * ({@code ItemGun.java:198-202} in the 1.7.10 source): resting and pressing either key turns
	 * the rest into an unrest. Firing already does that through {@code GunFiring.onTriggerPull};
	 * aiming had no way out, so a gun stowed with the rest key could not be raised again.
	 *
	 * @return true when this call dismissed the stow
	 */
	public static boolean dismissManualRestOnAim(GunActionState state, boolean aiming) {
		if(!aiming || state.operation != Operation.REST || !state.manualRest) return false;
		state.resume();
		return true;
	}
	/**
	 * Whether the aim progress may rise this tick.
	 *
	 * <p>A sight fold or switch must not drop the player out of ADS: the gesture animates the optic
	 * while the eye stays on it, so the fold flag is deliberately not consulted here. Only the
	 * manual stow and the take-out motion stop the aim.
	 */
	public static boolean aimingAllowed(GunActionState state, boolean aiming) {
		return aiming && !state.manualRest && state.takeOut == 0F;
	}
	public static void tick(Player player, PlayerData data) {
		ensureHeld(player, data);
		if(data.actionTick == player.tickCount) return;
		data.actionTick = player.tickCount;
		GunType gun = data.actionGun;
		if(gun == null) return;
		dismissManualRestOnAim(data.gunAction, data.gunAimDown);
		data.gunAimProgress=Math.clamp(data.gunAimProgress+(aimingAllowed(data.gunAction,data.gunAimDown)?.2F:-.2F),0F,1F);
		Operation operation = data.gunAction.operation == Operation.NONE && data.gunAction.queued != Operation.NONE
				? data.gunAction.queued : data.gunAction.operation;
		boolean ammo = operation == Operation.CHECK_AMMO;
		int index = operation == Operation.CHECK_CHAMBER_BC ? 2 : 0;
		float[] times = ammo ? gun.checkAmmoSoundTime : gun.checkChamberSoundTime;
		data.gunAction.tick(data.effectiveTakeOut, gun.restTime, gun.unrestTime, nearWall(player, gun),
				player.isSprinting(), data.isReloading(), times[index], times[index + 1]);
		// A queued operation can start on this tick. Read its own sounds, not the previous one.
		operation = data.gunAction.operation;
		ammo = operation == Operation.CHECK_AMMO;
		index = operation == Operation.CHECK_CHAMBER_BC ? 2 : 0;
		if(!player.level().isClientSide) {
			String[] sounds = ammo ? gun.checkAmmoSound : gun.checkChamberSound;
			float range=ammo?gun.checkAmmoSoundRange:gun.checkChamberSoundRange;
			if((data.gunAction.events & 1) != 0) sound(player, sounds[index],range);
			if((data.gunAction.events & 2) != 0) sound(player, sounds[index + 1],range);
			if((data.gunAction.events & 4) != 0) inspectionResult(player, gun, ammo);
		}
		if(ready(player, data)) {
			if(data.pendingGunReload) { data.pendingGunReload = false; GunFiring.requestReload(player,false,data.pendingReloadAction); }
		}
	}
	private static void inspectionResult(Player player, GunType gun, boolean ammo) {
		ItemStack loaded = GunMagazine.get(player.getMainHandItem(), gun, 0);
		int count = GunMagazine.roundsRemaining(loaded);
		Component text;
		if(ammo) {
			GunType mag = GunMagazine.magazineType(player.getMainHandItem(), gun);
			text = Component.literal(Integer.toString(count));
			if(mag != null) for(var state : mag.checkAmmoStates)
				if(count >= state.min() && count <= state.max()) { text = stateText(state.text()); break; }
		} else {
			boolean chambered=!GunMagazine.getChamberedRound(player.getMainHandItem()).isEmpty();
			// Older direct-bullet guns retain their existing chamber storage.
			if(GunMagazine.magazineType(player.getMainHandItem(),gun)==null) chambered|=count>0;
			text=chambered?Component.translatable("message.hasammo"):GunCasings.retained(player.getMainHandItem())!=null
					?Component.translatable("message.boltcatch"):Component.translatable("message.noammo");
		}
		player.displayClientMessage(text, true);
	}

	/**
	 * One ammo-state label, translated when the pack wrote a language key.
	 *
	 * <p>EFT's guns name their states either as plain text ({@code Full}, {@code Not Much}) or as
	 * a language key ({@code message.fullammo}); the pack's {@code zh_CN.lang} translates only the
	 * latter. The key form went through {@code I18n} in 1.7.10 and must still, otherwise a Chinese
	 * player reads the raw key. Plain text is shown as written — deliberately, because that is
	 * what 1.7.10 did with it.
	 */
	private static Component stateText(String state)
	{
		if(state == null || state.isBlank())
			return Component.empty();
		if(!state.startsWith("message.") && !state.startsWith("item.") && !state.startsWith("gun.")
				&& !state.startsWith("mag."))
		{
			// EFT writes the magazine inspection states as plain English ("Empty", "Not Much",
			// "About Half", "Nearly full", "Full"). 1.7.10 printed them verbatim, which left a
			// Chinese client reading English. Map the authored vocabulary to language keys; an
			// unknown pack label still prints exactly as written.
			String key = switch(state.trim().toLowerCase(java.util.Locale.ROOT))
			{
				case "empty" -> "message.ammo.empty";
				case "not much" -> "message.ammo.not_much";
				case "about half" -> "message.ammo.about_half";
				case "nearly full" -> "message.ammo.nearly_full";
				case "almost full" -> "message.ammo.nearly_full";
				case "full" -> "message.ammo.full";
				case "low" -> "message.ammo.low";
				default -> null;
			};
			return key == null ? Component.literal(state)
					: Component.translatableWithFallback(key, state);
		}
		return Component.translatableWithFallback(state, state);
	}
	public static void sound(Player player, String name,float range) {
		var held=InfoType.getType(player.getMainHandItem());
		FmumSounds.play(player,name,range,held instanceof GunType gun && gun.distortSound,false);
	}
	public static boolean boltCaught(ItemStack stack) {
		var tag = stack.get(DataComponents.CUSTOM_DATA);
		return tag != null && tag.copyTag().getBoolean("flansmodBoltCatch");
	}
	public static void boltCaught(ItemStack stack, boolean value) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean("flansmodBoltCatch", value));
	}
	public static boolean nearWall(Player player, GunType gun) {
		if(gun == null) return false;
		float length = gunLength(GunAttachments.view(gun,player.getMainHandItem()), 0F, 0) + gun.gunLengthOffset;
		if(length <= 0F) return false;
		var data=PlayerHandler.getPlayerData(player);
		var model=ACTION_MODELS.computeIfAbsent(gun,com.flansmod.client.model.loader.ModelCache::load);
		if(model!=null && (data.gunWallRay==null || (!player.level().isClientSide && data.gunAction.operation==Operation.NONE && !player.isSprinting())))
			data.gunWallRay=com.flansmod.client.render.FmumCollisionRay.local(model,GunAttachments.view(gun,player.getMainHandItem()),data.gunAimProgress,com.flansmod.client.render.LegacyGunMotion.Frame.ZERO);
		var ray=data.gunWallRay==null?null:com.flansmod.client.render.FmumCollisionRay.world(data.gunWallRay,player.getYRot(),player.getXRot());
		var start=player.getEyePosition();
		var direction=player.getLookAngle();
		if(ray!=null) { start=start.add(ray.origin().x,ray.origin().y,ray.origin().z); direction=new net.minecraft.world.phys.Vec3(ray.direction().x,ray.direction().y,ray.direction().z); }
		return player.level().clip(new ClipContext(start, start.add(direction.scale(length)),
				// COLLIDER, not OUTLINE. The two shapes differ for every plant: short grass,
				// tall grass, flowers and crops are marked noCollission, so their collision shape
				// is empty while their outline is still a 14x13x14 box. 1.7.10's rayTrace passed
				// ignoreBlockWithoutBoundingBox=true, which skipped exactly those blocks, so a gun
				// pointed into grass never lowered. OUTLINE reintroduced the lowering.
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.BLOCK;
	}
	public static float gunLength(ModifiableType owner, float x, int depth) {
		if(depth > 16) return x;
		float max = bodyLength(owner,x);
		for(var nodes : owner.defaultAttachments) max = Math.max(max, nodeLength(owner, nodes, x, depth));
		return max;
	}
	private static float bodyLength(ModifiableType owner,float x) {
		float max=x;
		var boxes=owner.hitBoxes.isEmpty()?owner.bigHitBoxes:owner.hitBoxes;
		for(float[][] box:boxes) for(float[] point:box)max=Math.max(max,x+point[0]*owner.modelScale);
		return max;
	}
	private static float nodeLength(ModifiableType owner, List<ModifiableType.DefaultAttachment> nodes, float x, int depth) {
		float max = x;
		for(var node : nodes) {
			AttachmentType at = AttachmentType.getAttachment(node.name);
			if(at == null || node.slot < 0 || node.slot >= owner.slots.length) continue;
			float origin = x + owner.slots[node.slot].x + node.pos;
			if(depth>=16) continue;
			max = Math.max(max, Math.max(bodyLength(at, origin), nodeLength(at, node.children, origin, depth + 1)));
		}
		return max;
	}
}
