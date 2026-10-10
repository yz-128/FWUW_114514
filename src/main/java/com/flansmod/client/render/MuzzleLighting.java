package com.flansmod.client.render;

import com.flansmod.client.GunAnimationRegistry;
import com.flansmod.client.model.FmumShotEffects;
import com.flansmod.common.types.GunType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Brief held-light input for shader packs. The shader supplies the surface lighting itself. */
public final class MuzzleLighting {
	private MuzzleLighting() { }
	public static int emission(Player player, ItemStack stack, GunType gun) {
		if(gun.itsType==GunType.GunItsType.MAG || player!=Minecraft.getInstance().player)return 0;
		boolean offHand=stack==player.getOffhandItem();
		if(!offHand && stack!=player.getMainHandItem())return 0;
		return level(gun,GunAnimationRegistry.get(player,offHand).effects,
				Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
	}
	public static int level(GunType gun,FmumShotEffects shot,float partial) {
		if(gun.itsType==GunType.GunItsType.MAG || shot.gun!=gun || shot.fade<=0F)return 0;
		float alpha=shot.alpha(partial);
		if(alpha<1F/255F)return 0;
		// Use the configuration selected when the shot was confirmed, including suppressors.
		// AS VAL/VSS deliberately have no flash model even though their fade constant is nonzero.
		var flash=FmumMuzzlePose.flash(gun,null);
		if(flash==null || flash.model()==null || flash.scale()<=0F || flash.fade()<=0F)return 0;
		return Math.clamp((int)Math.ceil(15F*alpha),0,15);
	}
}
