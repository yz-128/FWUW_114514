package com.flansmod.common.network;

import com.flansmod.common.FlansMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * One held-tick of a first-aid kit. Vanilla's item-use lifecycle is not reliable for a stack whose
 * content item is shared by every definition, so the client reports the held slot and the server
 * owns the actual heal/use spending.
 */
public record MedkitUsePayload(int slot) implements CustomPacketPayload
{
	public static final CustomPacketPayload.Type<MedkitUsePayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FlansMod.MODID, "medkit_use"));

	public static final StreamCodec<FriendlyByteBuf,MedkitUsePayload> STREAM_CODEC =
			StreamCodec.of((buffer,payload)->buffer.writeVarInt(payload.slot()),
					buffer->new MedkitUsePayload(buffer.readVarInt()));

	@Override
	public Type<? extends CustomPacketPayload> type()
	{
		return TYPE;
	}
}
