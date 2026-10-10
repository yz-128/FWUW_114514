package com.flansmod.common.network;

import com.flansmod.common.FlansMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The trigger going down or up, sent to the server.
 *
 * <p>This is 1.7.10's {@code PacketGunFire}: the client watches the button and tells the server
 * both edges, and the server is the side that actually fires, spends rounds and broadcasts the
 * shot. The client also runs the same code locally as a prediction so the gun reacts on the same
 * frame the button is pressed; the server's copy remains the one that counts.
 */
public record TriggerPayload(boolean mainHand, boolean down) implements CustomPacketPayload
{
	public static final CustomPacketPayload.Type<TriggerPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FlansMod.MODID, "trigger"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TriggerPayload> STREAM_CODEC =
			StreamCodec.of((buf, payload) ->
					{
						buf.writeBoolean(payload.mainHand());
						buf.writeBoolean(payload.down());
					},
					buf -> new TriggerPayload(buf.readBoolean(), buf.readBoolean()));

	@Override
	public Type<? extends CustomPacketPayload> type()
	{
		return TYPE;
	}
}
