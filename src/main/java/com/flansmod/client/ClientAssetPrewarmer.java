package com.flansmod.client;

import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.client.model.loader.TextureSizes;
import com.flansmod.client.render.Textures;
import com.flansmod.common.FlansMod;
import com.flansmod.common.GunMagazine;
import com.flansmod.common.types.AttachmentType;
import com.flansmod.common.types.GunType;
import com.flansmod.common.types.InfoType;
import com.flansmod.common.types.ItemFlan;
import com.flansmod.common.types.ModifiableType;
import com.flansmod.common.types.ShootableType;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/** Selected hands first, then hotbar. No game object or resource manager is sent to a worker. */
@EventBusSubscriber(modid=FlansMod.MODID,value=Dist.CLIENT)
public final class ClientAssetPrewarmer {
    private ClientAssetPrewarmer() { }
    public static final int RIGS_PER_TICK = 2;
    private record Slot(Item item, CustomData data, ResourceLocation skin) { }
    // Fixed slots retain at most eleven immutable component values, never an unbounded stack history.
    private static final Slot[] previous = new Slot[11];

    @SubscribeEvent public static void registerReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SimplePreparableReloadListener<Void>() {
            @Override protected Void prepare(ResourceManager resources,ProfilerFiller profiler) { return null; }
            @Override protected void apply(Void ignored,ResourceManager resources,ProfilerFiller profiler) {
                Textures.clear(); TextureSizes.clear(); ModelCache.clear(); Arrays.fill(previous,null);
            }
        });
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if(mc.player == null || mc.level == null) return;
        var skin = mc.player.getSkin().texture();
        visit(0,mc.player.getMainHandItem(),skin);
        visit(1,mc.player.getOffhandItem(),skin);
        for(int slot=0;slot<9;slot++) visit(slot+2,mc.player.getInventory().getItem(slot),skin);
        ModelCache.bakePending(RIGS_PER_TICK);
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        Arrays.fill(previous,null);
    }

    private static void visit(int slot,ItemStack stack,ResourceLocation skin) {
        var fingerprint = new Slot(stack.getItem(),stack.get(DataComponents.CUSTOM_DATA),skin);
        if(fingerprint.equals(previous[slot])) return;
        previous[slot] = fingerprint;
        warmStack(stack,skin,0);
    }

    private static void warmStack(ItemStack stack,ResourceLocation skin,int depth) {
        if(depth>8 || stack.isEmpty() || !(stack.getItem() instanceof ItemFlan item)) return;
        InfoType type = item.type;
        // Flat icon-only items do not need their unused legacy model parsed.
        if(type.rendersInHand()) ModelCache.prewarm(type);
        if(type instanceof GunType gun) {
            for(var group:gun.defaultAttachments) attachments(gun,group,0);
            arm(gun.leftArmModel,gun.leftArmTexture,skin);
            arm(gun.rightArmModel,gun.rightArmTexture,skin);
            reference(gun.magFollowerModel,1F,gun.magFollowerTexture,true);
            flash(gun.flashModel,gun.flashTexture,gun.numFlashTexture);
            smoke(gun.muzzleSmoke); smoke(gun.chamberSmoke);
        }
        if(type instanceof ShootableType bullet) {
            // The visible seated round and the ejected casing use two scale values of this same mesh.
            reference(bullet.bulletModel,bullet.bulletScale,bullet.bulletTexture,true);
            reference(bullet.bulletModel,1F,bullet.bulletTexture,true);
        }
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if(data == null) return;
        var tag = data.copyTag();
        var ammo = tag.getList(GunMagazine.AMMO_KEY,Tag.TAG_COMPOUND);
        for(int i=0;i<ammo.size();i++) warmStack(ItemStack.parseOptional(RegistryAccess.EMPTY,ammo.getCompound(i)),skin,depth+1);
        if(tag.contains(GunMagazine.CHAMBER_KEY,Tag.TAG_COMPOUND))
            warmStack(ItemStack.parseOptional(RegistryAccess.EMPTY,tag.getCompound(GunMagazine.CHAMBER_KEY)),skin,depth+1);
    }

    private static void attachments(GunType gun,List<ModifiableType.DefaultAttachment> nodes,int depth) {
        if(depth>8) return;
        for(var node:nodes) {
            var type = AttachmentType.getAttachment(node.name);
            if(type == null) continue;
            ModelCache.prewarm(type);
            String texture = type.flashTextureName == null ? gun.flashTexture : type.flashTextureName;
            int variants = type.numFlashTexture>0 ? type.numFlashTexture : gun.numFlashTexture;
            flash(type.flashModelName,texture,variants);
            smoke(type.muzzleSmoke);
            attachments(gun,node.children,depth+1);
        }
    }

    private static void arm(String model,String texture,ResourceLocation skin) {
        var chosen = texture == null || texture.isBlank() || texture.equalsIgnoreCase("none")
                ? skin : Textures.of(texture,false);
        ModelCache.prewarmReference(model,1F,chosen == null ? skin : chosen,true);
    }
    private static void reference(String model,float scale,String texture,boolean fallback) {
        ModelCache.prewarmReference(model,scale,Textures.of(texture,false),fallback);
    }
    private static void flash(String model,String texture,int count) {
        if(texture == null) return;
        for(int i=0;i<Math.max(1,count);i++)
            ModelCache.prewarmEffectReference(model,1F,Textures.of(texture+"_"+i,false));
    }
    private static void smoke(GunType.Smoke smoke) {
        if(smoke.texture == null) return;
        for(int i=0;i<Math.max(1,smoke.numTexture);i++)
            ModelCache.prewarmEffectReference(smoke.model,1F,Textures.of(smoke.texture+i,false));
    }
}
