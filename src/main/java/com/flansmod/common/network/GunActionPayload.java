package com.flansmod.common.network;

import com.flansmod.common.FlansMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client requests an operation; the server echoes only accepted requests to the same player. */
public record GunActionPayload(int operation, int slot) implements CustomPacketPayload {
	public static final Type<GunActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FlansMod.MODID, "gun_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, GunActionPayload> STREAM_CODEC = StreamCodec.of(
			(buf, value) -> { buf.writeVarInt(value.operation); buf.writeVarInt(value.slot); },
			buf -> new GunActionPayload(buf.readVarInt(), buf.readVarInt()));
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
