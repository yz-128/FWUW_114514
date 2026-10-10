package com.flansmod.common.network;

import com.flansmod.common.FlansMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** The actual ejected type, sampled before chamber/mixed-magazine contents change. */
public record GunEjectionPayload(String gun,String round,boolean manual,long seed) implements CustomPacketPayload {
	public static final Type<GunEjectionPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(FlansMod.MODID,"gun_ejection"));
	public static final StreamCodec<RegistryFriendlyByteBuf,GunEjectionPayload> STREAM_CODEC=StreamCodec.of(
			(buf,p)->{buf.writeUtf(p.gun,128);buf.writeUtf(p.round,128);buf.writeBoolean(p.manual);buf.writeLong(p.seed);},
			buf->new GunEjectionPayload(buf.readUtf(128),buf.readUtf(128),buf.readBoolean(),buf.readLong()));
	@Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
