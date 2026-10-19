package com.flansmod.client;

import com.flansmod.client.gui.GunModificationScreen;
import com.flansmod.common.FlansMod;
import com.flansmod.common.inventory.GunModificationMenus;
import com.flansmod.common.network.GunModificationPayload;
import com.flansmod.common.types.GunType;
import com.flansmod.common.types.InfoType;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;

/** FMUM's I binding opens the authoritative menu; a client never manufactures a gun stack. */
@EventBusSubscriber(modid = FlansMod.MODID, value = Dist.CLIENT)
public final class ModifyKeyHandler {
    public static final KeyMapping MODIFY = new KeyMapping("key.flansmod.modify", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, InputConstants.KEY_I, "key.categories.flansmod");
    private ModifyKeyHandler() { }
    @SubscribeEvent public static void registerKey(RegisterKeyMappingsEvent event) { event.register(MODIFY); }
    @SubscribeEvent public static void registerScreen(RegisterMenuScreensEvent event) {
        event.register(GunModificationMenus.TYPE.get(), GunModificationScreen::new);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        while(MODIFY.consumeClick()) {
            if(mc.screen != null || mc.player == null || !MODIFY.isConflictContextAndModifierActive()) continue;
            if(InfoType.getType(mc.player.getMainHandItem()) instanceof GunType gun && gun.itsType != GunType.GunItsType.MAG)
                PacketDistributor.sendToServer(GunModificationPayload.open());
        }
    }
}
