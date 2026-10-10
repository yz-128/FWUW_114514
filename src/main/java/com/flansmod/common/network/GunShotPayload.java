package com.flansmod.common.network;

import com.flansmod.common.FlansMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One server-fired round. Shared randomness gives shooter and observers the same flash. */
public record GunShotPayload(int entity,String gun,boolean offHand,long seed) implements CustomPacketPayload {
	public static final Type<GunShotPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(FlansMod.MODID,"gun_shot"));
	public static final StreamCodec<RegistryFriendlyByteBuf,GunShotPayload> STREAM_CODEC=StreamCodec.of(
			(buf,p)->{buf.writeVarInt(p.entity);buf.writeUtf(p.gun,128);buf.writeBoolean(p.offHand);buf.writeLong(p.seed);},
			buf->new GunShotPayload(buf.readVarInt(),buf.readUtf(128),buf.readBoolean(),buf.readLong()));
	@Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
