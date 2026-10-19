package com.flansmod.client;

import com.flansmod.common.*;
import com.flansmod.common.network.GunSightPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.network.PacketDistributor;

/** Finite server-validated sight controls; Alt+V remains the existing rest binding. */
@EventBusSubscriber(modid=FlansMod.MODID,value=Dist.CLIENT)
public final class SightKeyHandler {
    public static final KeyMapping GROUP=key("sight_group",InputConstants.KEY_N),ZOOM=key("sight_zoom",InputConstants.KEY_Z),
            DOT=key("sight_dot",InputConstants.KEY_C),FOLD=key("sight_fold",InputConstants.KEY_V);
    private SightKeyHandler() { }
    private static KeyMapping key(String name,int key) {
        return new KeyMapping("key.flansmod."+name,KeyConflictContext.IN_GAME,InputConstants.Type.KEYSYM,key,"key.categories.flansmod");
    }
    @SubscribeEvent public static void register(RegisterKeyMappingsEvent event) {
        event.register(GROUP);event.register(ZOOM);event.register(DOT);event.register(FOLD);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        send(GROUP,GunSightState.NEXT_GROUP);send(ZOOM,GunSightState.ZOOM);send(DOT,GunSightState.DOT);send(FOLD,GunSightState.TOGGLE);
    }
    private static void send(KeyMapping key,int operation) {
        var mc=Minecraft.getInstance();
        while(key.consumeClick()) {
            if(mc.screen!=null || mc.player==null || !key.isConflictContextAndModifierActive())continue;
            // Explicit guard also protects Alt+V if the engine changes modified-key precedence.
            if(key==FOLD && key.getKeyModifier()==KeyModifier.NONE && key.getKey().getValue()==InputConstants.KEY_V
                    && net.minecraft.client.gui.screens.Screen.hasAltDown())continue;
            var stack=mc.player.getMainHandItem();var gun=GunActions.firearm(stack);if(gun==null)continue;
            if(PlayerHandler.getPlayerData(mc.player).sightFolding(mc.player))continue;
            if(!GunSightState.canChange(GunAttachments.view(gun,stack),operation))continue;
            if(operation==GunSightState.TOGGLE)SightFoldAnimations.warm(GunAttachments.view(gun,stack));
            PacketDistributor.sendToServer(new GunSightPayload(operation,mc.player.getInventory().selected,
                    GunAttachments.revision(stack),GunAttachments.sightRevision(stack)));
        }
    }
}
