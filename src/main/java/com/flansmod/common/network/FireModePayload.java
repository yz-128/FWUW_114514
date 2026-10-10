package com.flansmod.common.network;

import com.flansmod.common.FlansMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The client's "switch firing mode" request, sent when that key goes down.
 *
 * <p>1.7.10 sent {@code PacketGunMode} the same way and the 1.20.1 build still does
 * ({@code PacketGunFireMode}): the key asks, the server walks the mode forward on the stack it
 * owns, and the change survives because it was written to the item on the authoritative side.
 *
 * <p>The payload carries no mode name on purpose. Which mode comes next is a property of the
 * definition and of where the stack currently sits, so sending "the next one" would let a client
 * that has fallen behind pick a mode the gun does not have — the server decides instead, which is
 * also what stops a modified client from selecting a mode the pack never listed.
 */
public record FireModePayload(boolean offHand) implements CustomPacketPayload
{
	public static final CustomPacketPayload.Type<FireModePayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FlansMod.MODID, "fire_mode"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FireModePayload> STREAM_CODEC =
			StreamCodec.of((buf, payload) -> buf.writeBoolean(payload.offHand()),
					buf -> new FireModePayload(buf.readBoolean()));

	@Override
	public Type<? extends CustomPacketPayload> type()
	{
		return TYPE;
	}
}
