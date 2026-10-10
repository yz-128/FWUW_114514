package com.flansmod.common;

import com.flansmod.common.network.GunEjectionPayload;
import com.flansmod.common.types.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.PacketDistributor;

/** Spent cases are not ammunition; a bolt gun retains its case until it is charged. */
public final class GunCasings {
	private static final String KEY="flansmodSpentCase";
	private GunCasings() { }
	public static BulletType casing(BulletType round) {
		var type=round==null || round.isCase?null:BulletType.getBullet(round.dropItemOnShoot);
		return type!=null && type.isCase?type:null;
	}
	public static BulletType retained(ItemStack gun) {
		var data=gun.get(DataComponents.CUSTOM_DATA);
		return data==null?null:BulletType.getBullet(data.copyTag().getString(KEY));
	}
	public static void retain(ItemStack gun,BulletType casing) {
		CustomData.update(DataComponents.CUSTOM_DATA,gun,tag->{if(casing==null)tag.remove(KEY);else tag.putString(KEY,casing.shortName);});
	}
	public static void eject(Player player,GunType gun,BulletType round,boolean manual) {
		if(round!=null && player instanceof ServerPlayer server)
			PacketDistributor.sendToPlayer(server,new GunEjectionPayload(gun.shortName,round.shortName,manual,player.getRandom().nextLong()));
	}
}
