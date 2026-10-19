package com.flansmod.client;

import com.flansmod.common.*;
import com.flansmod.common.network.GunSightAnimationPayload;
import com.flansmod.common.types.*;
import com.flansmod.client.render.SightFoldGeometry;
import com.flansmod.client.render.SightFoldPose;
import com.flansmod.client.model.loader.ModelCache;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;

/** Only an accepted server operation starts a gesture; inventory loads and selecting a gun never do. */
@EventBusSubscriber(modid=FlansMod.MODID,value=Dist.CLIENT)
public final class SightFoldAnimations {
    private SightFoldAnimations() { }
    private static final Map<Player,State> STATES=new WeakHashMap<>();
    private static final Map<Player,GunSightAnimationPayload> SEEN=new WeakHashMap<>();
    public record Frame(float progress,List<GunSightAnimationPayload.Part> parts,GunType aimView) {
        public static final Frame IDLE=new Frame(1,List.of(),null);
        public boolean active() {return !parts.isEmpty() && progress<1;}
        public GunSightAnimationPayload.Part part(int[] path) {
            if(active())for(var part:parts)if(Arrays.equals(part.path(),path))return part;
            return null;
        }
        public float phase() {return SightFoldPose.turn(progress);}
    }
    private static final class State {
        final GunSightAnimationPayload event;final int start;
        GunType lastView,aimView;
        State(GunSightAnimationPayload event,int start) {this.event=event;this.start=start;}
        boolean valid(Player player,ItemStack stack) {
            if(!player.isAlive() || !matches(event,stack) || player.tickCount>=start+GunSightState.FOLD_TICKS)return false;
            if(player==Minecraft.getInstance().player && player.getInventory().selected!=event.slot())return false;
            return PlayerHandler.getPlayerData(player).sightFoldUntilTick==start+GunSightState.FOLD_TICKS;
        }
        Frame frame(GunType view,float now) {
            if(lastView!=view) {
                lastView=view;var tree=GunAttachments.copy(view,view.defaultAttachments);
                for(var part:event.parts()) {var node=GunAttachments.nodeAt(tree,part.path());if(node!=null)node.name=part.from();}
                aimView=view.definition().withAttachments(tree);aimView.selectedSightGroup=event.fromGroup();
            }
            return new Frame(Math.clamp((now-start)/GunSightState.FOLD_TICKS,0,1),event.parts(),aimView);
        }
    }
    /** Also used by offline verification; an equipment packet may arrive immediately before or after this event. */
    public static boolean matches(GunSightAnimationPayload event,ItemStack stack) {
        var gun=GunActions.firearm(stack);if(gun==null || !gun.shortName.equals(event.gun()) || event.parts().isEmpty())return false;
        if(GunAttachments.revision(stack)!=event.structureRevision())return false;
        int revision=GunAttachments.sightRevision(stack);
        if(revision!=event.sightRevision() && revision!=((event.sightRevision()-1)&0x7fff))return false;
        var view=GunAttachments.view(gun,stack);
        for(var part:event.parts()) {
            var from=AttachmentType.getAttachment(part.from());var to=AttachmentType.getAttachment(part.to());
            if(!GunAttachments.validPath(part.path()) || from==null || to==null
                    || !from.shadowType.equalsIgnoreCase(to.shortName) || !to.shadowType.equalsIgnoreCase(from.shortName))return false;
            var node=GunAttachments.nodeAt(view.defaultAttachments,part.path());
            if(node==null || (!node.name.equals(part.from()) && !node.name.equals(part.to())))return false;
        }
        return true;
    }
    public static void accept(GunSightAnimationPayload event) {
        var mc=Minecraft.getInstance();if(mc.level==null || !(mc.level.getEntity(event.entity()) instanceof Player player))return;
        if(player==mc.player && player.getInventory().selected!=event.slot())return;
        if(!matches(event,player.getMainHandItem()))return;
        var previous=SEEN.get(player);
        if(previous!=null && sameEvent(previous,event))return;
        var data=PlayerHandler.getPlayerData(player);GunActions.ensureHeld(player,data);
        data.sightFoldUntilTick=player.tickCount+GunSightState.FOLD_TICKS;
        STATES.put(player,new State(event,player.tickCount));
        SEEN.put(player,event);
        for(var part:event.parts())warm(part);
    }
    public static boolean sameEvent(GunSightAnimationPayload a,GunSightAnimationPayload b) {
        return a.entity()==b.entity() && a.slot()==b.slot() && a.gun().equals(b.gun())
                && a.structureRevision()==b.structureRevision() && a.sightRevision()==b.sightRevision() && a.sequence()==b.sequence();
    }
    /** Warm both endpoints while the key request is in flight, without predicting success or touching NBT. */
    public static void warm(GunType view) {
        warmNodes(view.defaultAttachments.stream().flatMap(List::stream).toList());
    }
    private static void warmNodes(List<ModifiableType.DefaultAttachment> nodes) {
        for(var node:nodes) {
            var type=AttachmentType.getAttachment(node.name);
            if(type!=null && (type.isCoSight() || type.isToggle())) {
                var shadow=AttachmentType.getAttachment(type.shadowType);
                ModelCache.rigFor(type,type.modelScale);if(shadow!=null)ModelCache.rigFor(shadow,shadow.modelScale);
            }
            warmNodes(node.children);
        }
    }
    private static void warm(GunSightAnimationPayload.Part part) {
        var from=AttachmentType.getAttachment(part.from());var to=AttachmentType.getAttachment(part.to());
        var a=ModelCache.rigFor(from,from.modelScale);var b=ModelCache.rigFor(to,to.modelScale);
        if(a!=null && b!=null)SightFoldGeometry.plan(a,b);
    }
    public static Frame forStack(ItemStack stack,GunType view,ItemDisplayContext context,float partial) {
        if(!context.firstPerson() && context!=ItemDisplayContext.THIRD_PERSON_LEFT_HAND && context!=ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)return Frame.IDLE;
        for(var entry:STATES.entrySet()) {
            var player=entry.getKey();var state=entry.getValue();
            if(context.firstPerson() && player!=Minecraft.getInstance().player)continue;
            if(player.getMainHandItem()!=stack)continue;
            if(state.valid(player,stack))return state.frame(view,player.tickCount+partial);
        }
        return Frame.IDLE;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(mc.level==null) {STATES.clear();SEEN.clear();return;}
        STATES.entrySet().removeIf(e->e.getKey().level()!=mc.level || !e.getValue().valid(e.getKey(),e.getKey().getMainHandItem()));
        SEEN.keySet().removeIf(player->player.level()!=mc.level || !player.isAlive());
    }
}
