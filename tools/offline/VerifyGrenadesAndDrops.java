import com.flansmod.common.*;
import com.flansmod.common.entity.*;
import com.flansmod.common.types.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

import java.nio.file.*;
import java.util.*;

/** Grenade definitions/ballistics, tool repair maths and the final-JAR wiring. No game launched. */
public final class VerifyGrenadesAndDrops {
    static int checks;
    static void check(boolean value,String message) {checks++;if(!value)throw new AssertionError(message);}
    static void near(double actual,double expected,double epsilon,String message) {
        check(Double.isFinite(actual) && Math.abs(actual-expected)<=epsilon,message+" actual="+actual+" expected="+expected);
    }
    static GrenadeType grenade(String shortName) {
        for(var type:InfoType.infoTypes)if(type instanceof GrenadeType grenade && shortName.equals(type.shortName))return grenade;
        throw new AssertionError("missing grenade definition "+shortName);
    }
    static java.util.zip.ZipFile JAR;
    /** Read from the shipped jar: the verifier's own classpath also carries the offline FlansMod
     *  stub, which would shadow the real class through getResourceAsStream. */
    static ClassNode node(String name) throws Exception {
        var entry=JAR.getEntry(name+".class");
        check(entry!=null,"final JAR class exists: "+name);
        var n=new ClassNode();
        try(var in=JAR.getInputStream(entry)) { new ClassReader(in.readAllBytes()).accept(n,0); }
        return n;
    }
    static boolean calls(ClassNode n,String method,String ownerSuffix,String called) {
        for(var m:n.methods)if(m.name.equals(method))for(var i:m.instructions)
            if(i instanceof MethodInsnNode c && c.owner.endsWith(ownerSuffix) && c.name.equals(called))return true;
        return false;
    }
    static boolean hasField(ClassNode n,String field) {return n.fields.stream().anyMatch(f->f.name.equals(field));}
    /** Entity renderers are registered as method references, which compile to invokedynamic. */
    static boolean makesRenderer(ClassNode n,String method,String ownerSuffix) {
        for(var m:n.methods)if(m.name.equals(method))for(var i:m.instructions) {
            if(i instanceof InvokeDynamicInsnNode indy) {
                for(var arg:indy.bsmArgs)
                    if(arg instanceof org.objectweb.asm.Handle h && "<init>".equals(h.getName())
                            && h.getOwner().endsWith(ownerSuffix))return true;
            }
            if(i instanceof MethodInsnNode c && "<init>".equals(c.name) && c.owner.endsWith(ownerSuffix))return true;
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        JAR=new java.util.zip.ZipFile(args[0]);
        for(int i=1;i<args.length;i++)new OfflinePack(Path.of(args[i])).readTypes();

        // ---------------------------------------------------------------- definitions
        var rgd=grenade("RGD5");
        check(rgd.fuse==70,"RGD5 fuse");
        near(rgd.bounciness,.4,1E-6,"RGD5 bounciness");
        near(rgd.throwSpeed,1.8,1E-6,"RGD5 throw speed");
        near(rgd.fallSpeed,1,1E-6,"RGD5 fall speed");
        near(rgd.explosionRadius,5,1E-6,"RGD5 explosion radius");
        check(!rgd.explosionBreaksBlocks,"RGD5 leaves blocks");
        check(rgd.breaksGlass,"RGD5 breaks glass");
        check(rgd.spinWhenThrown,"RGD5 tumbles");
        near(rgd.hitBoxSize,.5,1E-6,"RGD5 hit box");
        check(!rgd.detonateWhenShot && !rgd.sticky && rgd.smokeTime==0,"RGD5 plain fragmentation grenade");

        var m67=grenade("M67Grenade3s");
        check(m67.fuse==60 && m67.detonateWhenShot && m67.explosionBreaksBlocks,"M67 3s fuse/shot/block rules");
        near(m67.bounciness,.6,1E-6,"M67 bounciness");
        near(m67.explosionRadius,4,1E-6,"M67 explosion radius");
        check(!grenade("M67Grenade4s").spinWhenThrown,"M67 4s does not tumble");
        check(grenade("M67Grenade4s").fuse==80,"M67 4s fuse");

        var smoke=grenade("M83SmokeBomb2s");
        check(smoke.smokeTime==400,"smoke lasts 400 ticks");
        near(smoke.smokeRadius,5,1E-6,"smoke radius");
        check(smoke.fuse==40 && smoke.explosionRadius<=.1F,"smoke grenade has a fuse and no blast");
        check(grenade("M83SmokeBomb4s").fuse==80,"smoke 4s fuse");

        var flash=grenade("M84FlashBomb1s");
        check(flash.smokeTime==4,"flash smoke counter");
        check(flash.smokePotions.size()==2,"two potion effects parsed from SmokeEffect lines");
        check(flash.smokePotions.get(0).id()==15 && flash.smokePotions.get(0).duration()==100
                && flash.smokePotions.get(0).amplifier()==3,"first flash effect is 1.7.10 blindness 100t amp 3");
        check(flash.smokePotions.get(1).id()==2,"second flash effect is 1.7.10 slowness");
        check(flash.fuse==20 && !flash.explosionBreaksBlocks,"flash fuse and no block damage");
        check(grenade("M84FlashBomb2s").fuse==40,"flash 2s fuse");

        // The world renderer draws the baked rig directly, so the rig itself must be hand-sized.
        var rgdRig=com.flansmod.client.model.loader.ModelCache.rigFor(rgd,rgd.modelScale);
        check(rgdRig!=null,"RGD5 bakes a rig");
        var rgdHeight=rgdRig.maxY-rgdRig.minY;
        check(rgdHeight>0.02F,"RGD5 bakes a rig with real extent");
        var m67Rig=com.flansmod.client.model.loader.ModelCache.rigFor(m67,m67.modelScale);
        check(m67Rig!=null && m67Rig.maxY-m67Rig.minY>0.02F,"M67 bakes a rig with real extent");

        var grenadeRenderer=node("com/flansmod/client/render/GrenadeRenderer");
        check(calls(grenadeRenderer,"render","PoseStack","scale"),"grenade renderer scales the authored rig down to the hit box");
        check(!calls(grenadeRenderer,"render","ItemRenderer","renderStatic"),"grenade renderer no longer uses the held-item GROUND transform");
        check(LegacyPotion.byId(15)==MobEffects.BLINDNESS,"legacy id 15 maps to blindness");
        check(LegacyPotion.byId(2)==MobEffects.MOVEMENT_SLOWDOWN,"legacy id 2 maps to slowness");
        check(LegacyPotion.byId(999)==null,"unknown legacy potion id is rejected");

        // ---------------------------------------------------------------- ballistics
        near(GrenadeBallistics.GRAVITY,9.81/400,1E-9,"gravity is 1.7.10's 9.81/400 per tick");
        var fallen=GrenadeBallistics.applyGravity(Vec3.ZERO,1F);
        near(fallen.x,0,1E-9,"gravity keeps X");near(fallen.y,-9.81/400,1E-9,"gravity pulls down");
        var slowed=GrenadeBallistics.applyGravity(Vec3.ZERO,.8F);
        near(slowed.y,-9.81/400*.8,1E-9,"FallSpeed scales gravity");

        // A ball dropped onto a floor: the reflected step keeps the speed times bounciness/2.
        var down=new Vec3(0,-.2,0);
        var bounced=GrenadeBallistics.bounce(down,new Vec3(0,1,0),new Vec3(0,.9,0),Direction.UP,.4F);
        near(bounced.y,.04,1E-9,"Y face bounce keeps bounciness/2 of the travel speed");
        near(bounced.x,0,1E-12,"Y face bounce leaves X alone");
        var sideways=GrenadeBallistics.bounce(new Vec3(.2,0,0),Vec3.ZERO,new Vec3(.1,0,0),Direction.WEST,.5F);
        near(sideways.x,-.05,1E-9,"X face bounce mirrors and scales");
        check(GrenadeBallistics.bounce(Vec3.ZERO,Vec3.ZERO,Vec3.ZERO,Direction.UP,.4F).lengthSqr()==0,
                "zero motion cannot bounce into NaN");
        var bouncedPos=GrenadeBallistics.bouncePosition(new Vec3(0,1,0),new Vec3(0,.9,0),down,Direction.UP,.4F);
        near(bouncedPos.y,.92,1E-9,"bounce position is pre-hit travel plus the scaled reflected step");

        check(GrenadeBallistics.detonatesByFuse(71,70),"fuse fires one tick after it elapses");
        check(!GrenadeBallistics.detonatesByFuse(70,70),"fuse does not fire early");
        check(!GrenadeBallistics.detonatesByFuse(999,0),"fuse 0 means no timed detonation");
        check(!GrenadeBallistics.armed(3,10) && GrenadeBallistics.armed(10,10),"prime delay gates detonation");

        // ---------------------------------------------------------------- tools
        var file=new TypeFile(EnumType.tool,"toolFixture",false);
        file.lines.add("ShortName ToolFixture");
        file.lines.add("HealPlayers true");
        file.lines.add("HealAmount 4");
        file.lines.add("ToolLife 5");
        file.lines.add("DestroyOnEmpty true");
        var tool=new ToolType(file);tool.read(file);
        check(Tools.healFor(tool)==4,"HealAmount is read");
        check(Tools.nextToolLife(tool,5)==4,"one use spends one point");
        check(Tools.nextToolLife(tool,1)==0,"last use empties the tool");
        check(Tools.shouldDestroy(tool,0),"empty tool is destroyed");
        check(!Tools.shouldDestroy(tool,2),"a used tool is kept");
        file.lines.add("DestroyOnEmpty false");
        var refill=new ToolType(new TypeFile(EnumType.tool,"toolRefill",false));
        refill.toolLife=5;refill.destroyOnEmpty=false;
        check(Tools.nextToolLife(refill,1)==5,"a refillable tool cycles back to full life");

        // ---------------------------------------------------------------- final JAR wiring
        var item=node("com/flansmod/common/types/ItemFlan");
        check(calls(item,"use","Grenades","throwGrenade"),"item right-click throws a grenade");
        check(calls(item,"use","Tools","use"),"item right-click uses a tool");
        var mod=node("com/flansmod/common/FlansMod");
        check(hasField(mod,"GRENADE") && hasField(mod,"GUN_ITEM"),"both entity types are registered");
        var client=node("com/flansmod/client/ClientExtensions");
        check(makesRenderer(client,"onRegisterRenderers","client/render/GrenadeRenderer"),"grenade renderer registered");
        check(makesRenderer(client,"onRegisterRenderers","client/render/GunItemRenderer"),"fallen-gun renderer registered");
        var grenadeEntity=node("com/flansmod/common/entity/EntityGrenade");
        check(calls(grenadeEntity,"detonate","world/level/Level","explode"),"detonation uses the level explosion");
        // ExplosionBreaksBlocks must select the interaction kind: RGD5 says false, M67 says true.
        var interactions=new java.util.HashSet<String>();
        for(var m:grenadeEntity.methods)if(m.name.equals("detonate"))for(var i:m.instructions)
            if(i instanceof FieldInsnNode f && f.owner.endsWith("Level$ExplosionInteraction"))interactions.add(f.name);
        check(interactions.contains("BLOCK") && interactions.contains("NONE"),
                "blast selects BLOCK or NONE from ExplosionBreaksBlocks instead of always breaking");
        check(!interactions.contains("TNT"),"no unconditional TNT interaction is left");
        check(calls(grenadeEntity,"tick","GrenadeBallistics","applyGravity"),"gravity comes from the shared ballistics");
        var gunItem=node("com/flansmod/common/entity/EntityGunItem");
        check(gunItem.methods.stream().anyMatch(m->m.name.equals("playerTouch")),"dropped guns can be picked up by walking over them");
        var dropped=node("com/flansmod/common/DroppedGuns");
        check(calls(dropped,"onItemToss","ItemTossEvent","setCanceled"),"dropping a gun replaces the vanilla item entity");
        check(calls(dropped,"onItemToss","EntityGunItem","<init>"),"dropping a gun spawns the fallen-gun entity");

        System.out.println("PASS VerifyGrenadesAndDrops: "+checks+" assertions; 7 real pack definitions, 1.7.10 ballistic reflection,"
                +" legacy potion ids, tool wear, and final-JAR wiring. No game/GPU/input.");
    }
}
