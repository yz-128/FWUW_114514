package com.flansmod.common.network;

import com.flansmod.common.FlansMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The client's "reload now" request, sent when the reload key goes down.
 *
 * <p>1.7.10 sent the same thing as {@code PacketReload}: the key handler only asked the server,
 * and the server did the loading and told the client back so the animation could play. The port
 * keeps the server as the authority — it is the side that owns the gun's saved rounds — while the
 * client predicts the animation so the key feels instant.
 */
public record ReloadPayload(boolean offHand,Action action,int slot) implements CustomPacketPayload
{
	public enum Action { NORMAL,SPECIAL,COMPRESS_START,COMPRESS_STOP,CHARGE,RELEASE_BOLT }
	public static final CustomPacketPayload.Type<ReloadPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FlansMod.MODID, "reload"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ReloadPayload> STREAM_CODEC =
			StreamCodec.of((buf, payload) -> {
				buf.writeBoolean(payload.offHand());buf.writeEnum(payload.action());buf.writeVarInt(payload.slot());
			},buf -> new ReloadPayload(buf.readBoolean(),buf.readEnum(Action.class),buf.readVarInt()));

	@Override
	public Type<? extends CustomPacketPayload> type()
	{
		return TYPE;
	}
}
