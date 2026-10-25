package com.flansmod.common;

import com.flansmod.common.network.ContentPackManifestPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Aligns a joining client with the server's content packs.
 *
 * <p>On login the server sends its manifest; the client compares it, reports a readable list and
 * sends its own manifest back. The server compares that reply and disconnects a client whose packs
 * are missing, extra or different, so a multiplayer session cannot start with items and models the
 * other side cannot resolve.
 */
@EventBusSubscriber(modid = FlansMod.MODID)
public final class ContentPackSync
{
	private ContentPackSync() { }

	@SubscribeEvent
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event)
	{
		if(event.getEntity() instanceof ServerPlayer player)
			PacketDistributor.sendToPlayer(player,new ContentPackManifestPayload(ContentPackManifest.local()));
	}

	/** Handles a manifest from either direction; called on the game thread. */
	public static void accept(ContentPackManifestPayload payload,net.minecraft.world.entity.player.Player player)
	{
		List<String> differences=ContentPackManifest.compare(ContentPackManifest.local(),payload.packs());
		String details=String.join("; ",differences);
		if(player.level().isClientSide)
		{
			// Always answer so the server can verify this side too, even when the first comparison
			// already matched. An answer is small: one short hash per pack.
			PacketDistributor.sendToServer(new ContentPackManifestPayload(ContentPackManifest.local()));
			if(!differences.isEmpty())
			{
				FlansMod.LOGGER.warn("Content packs differ from the server: {}",details);
				player.displayClientMessage(Component.translatable("message.flansmod.packs.mismatch",details),false);
			}
			return;
		}
		if(!(player instanceof ServerPlayer server)) return;
		if(differences.isEmpty())
		{
			FlansMod.LOGGER.info("Content packs aligned with {}",server.getGameProfile().getName());
			return;
		}
		FlansMod.LOGGER.warn("Rejecting {}: content packs differ: {}",server.getGameProfile().getName(),details);
		server.connection.disconnect(Component.translatable("message.flansmod.packs.kick",details));
	}
}
