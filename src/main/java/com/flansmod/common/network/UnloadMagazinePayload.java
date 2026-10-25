package com.flansmod.common.network;

import com.flansmod.common.FlansMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** G while holding a magazine: the server pulls one round out of it into the inventory. */
public record UnloadMagazinePayload(int slot) implements CustomPacketPayload
{
	public static final CustomPacketPayload.Type<UnloadMagazinePayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FlansMod.MODID, "unload_magazine"));

	public static final StreamCodec<FriendlyByteBuf,UnloadMagazinePayload> STREAM_CODEC =
			StreamCodec.of((buffer,payload)->buffer.writeVarInt(payload.slot()),
					buffer->new UnloadMagazinePayload(buffer.readVarInt()));

	@Override
	public Type<? extends CustomPacketPayload> type()
	{
		return TYPE;
	}
}
