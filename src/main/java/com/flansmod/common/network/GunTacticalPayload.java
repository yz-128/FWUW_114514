package com.flansmod.common.network;

import com.flansmod.common.FlansMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The client's "toggle this tactical device" request (U = flashlight, Y = laser).
 *
 * <p>Like the fire-mode request, the client does not send the new state: the server owns the gun
 * stack, verifies the part is really mounted and flips it there. The item data replication then
 * brings the result back to every client that renders the gun.
 */
public record GunTacticalPayload(byte device) implements CustomPacketPayload
{
	public static final CustomPacketPayload.Type<GunTacticalPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FlansMod.MODID, "gun_tactical"));

	public static final StreamCodec<RegistryFriendlyByteBuf, GunTacticalPayload> STREAM_CODEC =
			StreamCodec.of((buf, payload) -> buf.writeByte(payload.device()),
					buf -> new GunTacticalPayload(buf.readByte()));

	@Override
	public Type<? extends CustomPacketPayload> type()
	{
		return TYPE;
	}
}
