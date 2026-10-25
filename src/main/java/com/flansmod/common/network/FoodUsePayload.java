package com.flansmod.common.network;

import com.flansmod.common.FlansMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One held tick of a food/drink item, owned by the server because the use lifecycle is shared. */
public record FoodUsePayload(int slot) implements CustomPacketPayload
{
	public static final CustomPacketPayload.Type<FoodUsePayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FlansMod.MODID, "food_use"));

	public static final StreamCodec<FriendlyByteBuf,FoodUsePayload> STREAM_CODEC =
			StreamCodec.of((buffer,payload)->buffer.writeVarInt(payload.slot()),
					buffer->new FoodUsePayload(buffer.readVarInt()));

	@Override
	public Type<? extends CustomPacketPayload> type()
	{
		return TYPE;
	}
}
