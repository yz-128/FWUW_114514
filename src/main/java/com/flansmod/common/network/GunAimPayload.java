package com.flansmod.common.network;
import com.flansmod.common.FlansMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** ADS edge for the server's non-operating wall ray. */
public record GunAimPayload(boolean aiming,int slot) implements CustomPacketPayload {
	public static final Type<GunAimPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(FlansMod.MODID,"gun_aim"));
	public static final StreamCodec<RegistryFriendlyByteBuf,GunAimPayload> STREAM_CODEC=StreamCodec.of(
			(buf,p)->{buf.writeBoolean(p.aiming);buf.writeVarInt(p.slot);},buf->new GunAimPayload(buf.readBoolean(),buf.readVarInt()));
	@Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
