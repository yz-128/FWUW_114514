package com.flansmod.common;

import com.flansmod.common.network.GunEjectionPayload;
import com.flansmod.common.types.*;
import com.flansmod.common.types.EnumType;
import com.flansmod.common.types.TypeFile;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.PacketDistributor;

/** Spent cases are not ammunition; a bolt gun retains its case until it is charged. */
public final class GunCasings {
	private static final String KEY="flansmodSpentCase";
	private static boolean casesLinked;
	private GunCasings() { }

	/**
	 * The EFT Reforged bullets name {@code ItsCase 9x18BulletCase} / {@code 12GBulletCase}, but the
	 * installed content packs only ship 5.56 and 9x19 case definitions — those two names do not
	 * exist anywhere in {@code run/flan}. 1.7.10 would simply eject nothing for those rounds.
	 *
	 * <p>Rather than leave half the ammunition silent, build the missing case definitions at load
	 * time from the closest authored brass: pistol rounds borrow the 9×19 case and shotgun shells
	 * borrow it scaled up. They live only in {@link BulletType#bullets} (so the casing renderer and
	 * the retain/charge path can find them), not in {@code InfoType.infoTypes}, so they never become
	 * items or appear in creative tabs.
	 */
	public static void linkMissingCases() {
		if(casesLinked) return;
		casesLinked=true;
		BulletType pistol=firstCase("9x19mmCasing","9x19mmCase","9x19");
		BulletType rifle=firstCase("556x45BulletCase","556x45mmCasing","556x45");
		if(pistol==null || rifle==null) return;
		var targets=new java.util.LinkedHashSet<String>();
		for(var bullet : BulletType.bullets) {
			var target=bullet.dropItemOnShoot;
			if(target!=null && !target.isBlank() && BulletType.getBullet(target)==null) targets.add(target);
		}
		for(var target : targets) {
			String lower=target.toLowerCase(java.util.Locale.ROOT);
			boolean shotgun=lower.contains("12g") || lower.contains("shotgun");
			BulletType base=shotgun ? pistol
					: lower.contains("9x18") || lower.contains("9x19") || lower.contains("45acp")
							|| lower.contains("46x30") || lower.contains("57x28") ? pistol : rifle;
			var synthetic=new BulletType(new TypeFile(EnumType.bullet,target,false));
			synthetic.name=target;synthetic.shortName=target;synthetic.isCase=true;
			synthetic.bulletModel=base.bulletModel;synthetic.bulletTexture=base.bulletTexture;
			synthetic.bulletScale=base.bulletScale*(shotgun?2.6F:1F);
			System.arraycopy(base.bulletRotCenter,0,synthetic.bulletRotCenter,0,3);
			// Casings are world effects, not items: keep the synthetic type out of the content
			// registry so it cannot be spawned, shown in creative tabs or saved as a new item id.
			InfoType.infoTypes.remove(synthetic);
			FlansMod.LOGGER.info("[Casings] missing case definition '{}' built from '{}' ({}), scale {}",
					target,base.shortName,shotgun?"shotgun":"pistol",synthetic.bulletScale);
		}
	}

	private static BulletType firstCase(String... names) {
		for(var name : names)
			for(var bullet : BulletType.bullets)
				if(bullet.isCase && (bullet.shortName!=null && bullet.shortName.equalsIgnoreCase(name)
						|| bullet.bulletModel!=null && bullet.bulletModel.toLowerCase(java.util.Locale.ROOT).contains(name.toLowerCase(java.util.Locale.ROOT))))
					return bullet;
		return null;
	}
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
