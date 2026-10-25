package com.flansmod.common.network;

import com.flansmod.common.ContentPackManifest;
import com.flansmod.common.FlansMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** The content-pack manifest exchange used to align a client with the server on join. */
public record ContentPackManifestPayload(List<ContentPackManifest.Entry> packs) implements CustomPacketPayload
{
	public static final CustomPacketPayload.Type<ContentPackManifestPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FlansMod.MODID, "content_packs"));
	private static final int MAX_PACKS = 256;

	public static final StreamCodec<FriendlyByteBuf,ContentPackManifestPayload> STREAM_CODEC = StreamCodec.of(
			(buffer,payload) -> {
				var entries=payload.packs();
				buffer.writeVarInt(entries.size());
				for(var entry : entries)
				{
					buffer.writeUtf(entry.name(),128);
					buffer.writeVarInt(entry.files());
					buffer.writeUtf(entry.hash(),64);
				}
			},
			buffer -> {
				int count=Math.min(buffer.readVarInt(),MAX_PACKS);
				var entries=new ArrayList<ContentPackManifest.Entry>(count);
				for(int i=0;i<count;i++)
				{
					String name=buffer.readUtf(128);
					int files=buffer.readVarInt();
					String hash=buffer.readUtf(64);
					entries.add(new ContentPackManifest.Entry(name,files,hash));
				}
				return new ContentPackManifestPayload(List.copyOf(entries));
			});

	@Override
	public Type<? extends CustomPacketPayload> type()
	{
		return TYPE;
	}
}
