package com.flansmod.common;

import com.flansmod.common.types.ItemFlanArmour;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Restores the original additive percentage Defence without inventing vanilla armour points. */
@EventBusSubscriber(modid = FlansMod.MODID)
public final class ArmourProtection
{
	private ArmourProtection() { }

	/** Original getArmorDisplay's double-to-int conversion is HUD data, not armour points. */
	public static int displayPoints(double defence)
	{
		return Double.isFinite(defence) ? (int)(Math.clamp(defence,0D,1D) * 20D) : 0;
	}

	@SubscribeEvent
	public static void onIncomingDamage(LivingIncomingDamageEvent event)
	{
		if(event.getEntity().level().isClientSide() || event.getSource().is(DamageTypeTags.BYPASSES_ARMOR))
			return;
		if(event.getSource().getDirectEntity() instanceof com.flansmod.common.entity.EntityBullet bullet
				&&bullet.armourHit()!=null&&bullet.bulletType()!=null) {
			var living=event.getEntity();boolean player=living instanceof net.minecraft.world.entity.player.Player;
			int part=player?ArmourBallistics.part(bullet.armourHit().subtract(living.position()),living.yBodyRot,living.getEyeHeight(),living.getBbWidth()):1;
			float amount=event.getAmount()*(player?(part==0?2F:part>1?.6F:1F):1F);
			var worn=new java.util.ArrayList<net.minecraft.world.item.ItemStack>();
			for(var slot:new EquipmentSlot[]{EquipmentSlot.FEET,EquipmentSlot.LEGS,EquipmentSlot.CHEST,EquipmentSlot.HEAD}) {
				var stack=living.getItemBySlot(slot);
				if(stack.getItem() instanceof ItemFlanArmour item&&item.getEquipmentSlot()==slot)worn.add(stack);
			}
			var result=ArmourBallistics.resolve(worn,bullet.bulletType(),part,amount,player,()->living.getRandom().nextFloat());
			for(var impact:result.impacts()) {
				ArmourBallistics.applyWear(impact);
				var type=((ItemFlanArmour)impact.stack().getItem()).armour;int sound=impact.soundPart();
				if(type.numHitSounds[sound]>0) {
                    var eventSound=FmumSounds.event(ArmourSounds.hit(type,sound,living.getRandom().nextInt(type.numHitSounds[sound])),type.hitSoundRanges[sound]);
					if(eventSound!=null)living.level().playSound(null,living.getX(),living.getY(),living.getZ(),eventSound,net.minecraft.sounds.SoundSource.PLAYERS,10F,1F);
				}
			}
			if(result.stopped())bullet.stopAtArmour();event.setAmount(result.damage());return;
		}
		double defence = 0D;
		for(EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
				EquipmentSlot.LEGS, EquipmentSlot.FEET})
		{
			var stack = event.getEntity().getItemBySlot(slot);
			if(stack.getItem() instanceof ItemFlanArmour item && item.getEquipmentSlot() == slot
					&& Double.isFinite(item.armour.defence))
				defence += Math.max(0D, item.armour.defence);
		}
		if(defence <= 0D)
			return;
		double fraction = Math.min(1D, defence);
		// This callback runs after vanilla has worn the equipment and computed its own armour
		// reduction, but before enchantments, resistance and absorption. Capture the equipped
		// fraction now so a piece that breaks on this hit still protects against this hit.
		event.addReductionModifier(DamageContainer.Reduction.ARMOR,
				(container, vanillaReduction) -> reduction(container.getNewDamage(), vanillaReduction, fraction));
	}

	@SubscribeEvent public static void armourWear(net.neoforged.neoforge.event.entity.living.ArmorHurtEvent event) {
		boolean plate=event.getDamageSource().getDirectEntity() instanceof com.flansmod.common.entity.EntityBullet bullet&&bullet.armourHit()!=null;
		for(var entry:event.getArmorMap().entrySet())if(entry.getValue().armorItemStack.getItem() instanceof ItemFlanArmour item
				&&(plate||!item.armour.breakable))event.setNewDamage(entry.getKey(),0F);
	}

	/** Combined reduction, preserving other armour's existing reduction and preventing healing. */
	public static float reduction(float damage, float previousReduction, double defence)
	{
		if(!Float.isFinite(damage) || damage <= 0F)
			return 0F;
		float previous = Float.isFinite(previousReduction)
				? Math.max(0F, Math.min(damage, previousReduction)) : 0F;
		double fraction = Double.isFinite(defence) ? Math.max(0D, Math.min(1D, defence)) : 0D;
		return (float)(previous + (damage - previous) * fraction);
	}
}
