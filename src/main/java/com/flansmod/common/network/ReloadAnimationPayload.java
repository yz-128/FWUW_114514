package com.flansmod.common.network;

import com.flansmod.common.FlansMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Server-accepted reload plan. The client animates it and leaves inventory changes to the server. */
public record ReloadAnimationPayload(int slot,String gun,boolean active,boolean hasMagazine,
		boolean hasChamber,boolean caught,int compressRounds,ItemStack incoming,ReloadPayload.Action action) implements CustomPacketPayload {
	public static final Type<ReloadAnimationPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(FlansMod.MODID,"reload_animation"));
	public static final StreamCodec<RegistryFriendlyByteBuf,ReloadAnimationPayload> STREAM_CODEC=StreamCodec.of(
		(buf,p)->{
			buf.writeVarInt(p.slot());buf.writeUtf(p.gun());buf.writeBoolean(p.active());buf.writeBoolean(p.hasMagazine());
			buf.writeBoolean(p.hasChamber());buf.writeBoolean(p.caught());buf.writeVarInt(p.compressRounds());
			ItemStack.OPTIONAL_STREAM_CODEC.encode(buf,p.incoming());
			buf.writeEnum(p.action());
		},buf->new ReloadAnimationPayload(buf.readVarInt(),buf.readUtf(),buf.readBoolean(),buf.readBoolean(),
			buf.readBoolean(),buf.readBoolean(),buf.readVarInt(),ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),buf.readEnum(ReloadPayload.Action.class)));
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
