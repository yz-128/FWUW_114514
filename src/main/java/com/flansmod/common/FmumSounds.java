package com.flansmod.common;

import com.flansmod.common.types.*;
import java.util.List;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/** Content-pack sound events live in sounds.json, independently of the built-in event registry. */
public final class FmumSounds {
	private FmumSounds() { }
	public static ResourceLocation id(String name) {
		if(name==null || name.isBlank() || name.equalsIgnoreCase("None"))return null;
		String path=name.toLowerCase(Locale.ROOT);
		if(path.endsWith(".ogg"))path=path.substring(0,path.length()-4);
		return ResourceLocation.tryParse(path.contains(":")?path:FlansMod.MODID+":"+path);
	}
	public static SoundEvent event(String name,float range) {
		var id=id(name);if(id==null)return null;
		return range>0?SoundEvent.createFixedRangeEvent(id,range):SoundEvent.createVariableRangeEvent(id);
	}
	public static void play(Player player,String name,float range) { play(player,name,range,false,false); }
	public static void play(Player player,String name,float range,boolean distort,boolean silenced) {
		if(player.level().isClientSide)return;
		var event=event(name,range);if(event==null)return;
		float pitch=(distort?1F/(player.getRandom().nextFloat()*.4F+.8F):1F)*(silenced?2F:1F);
		// PacketPlaySound used volume 10 (5 when suppressed) and included the shooter.
		player.level().playSound(null,player.getX(),player.getY(),player.getZ(),event,SoundSource.PLAYERS,silenced?5F:10F,pitch);
	}
	public record Shot(String name,float range,boolean silenced) { }
	public static Shot shot(GunType gun) {
		float range=gun.gunSoundRange>0?gun.gunSoundRange:50F;
		var state=new float[]{range,0};collect(gun.defaultAttachments,state,0);
		int index=(int)state[1];
		String name=gun.shootSounds.isEmpty()?gun.shootSound:gun.shootSounds.get(index>=0 && index<gun.shootSounds.size()?index:0);
		// EFT tryShoot sends silenced=false; the alternate .ogg itself contains the suppressed sound.
		return new Shot(name,state[0],false);
	}
	private static void collect(List<List<ModifiableType.DefaultAttachment>> groups,float[] state,int depth) {
		for(int g=groups.size();--g>=0;)nodes(groups.get(g),state,depth);
	}
	private static void nodes(List<ModifiableType.DefaultAttachment> nodes,float[] state,int depth) {
		if(depth>16)return;
		for(int i=nodes.size();--i>=0;) {
			var node=nodes.get(i);var at=AttachmentType.getAttachment(node.name);
			if(at!=null) {
				state[0]*=at.soundRangeMultiplier;
				if("muzzle".equalsIgnoreCase(at.itsType) || "barrel".equalsIgnoreCase(at.itsType))state[1]=Math.max(0,at.enableShootSoundIndex);
			}
			nodes(node.children,state,depth+1);
		}
	}
}
