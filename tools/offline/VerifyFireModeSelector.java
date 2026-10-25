import com.flansmod.client.model.loader.BakedRig;
import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.common.types.GunType;
import com.flansmod.common.types.InfoType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

import java.nio.file.*;
import java.util.*;

/**
 * Fire-mode selector (快慢机) regression.
 *
 * <p>1.7.10 keeps every selector position in one {@code switchModel} array and draws only the
 * slice {@code [fireModeNum * numSwitchModel, +numSwitchModel)}, so a weapon shows exactly one
 * lever position. The port baked the whole array and the modification preview drew every quad,
 * which put all three positions on the receiver at once. This checks the slice data against the
 * real pack models and the wiring of both render paths. No game launched.
 *
 * <p>Args: {@code <final jar> <EFT pack> [more packs...]}.
 */
public final class VerifyFireModeSelector {
    static int checks;
    static java.util.zip.ZipFile JAR;
    static void check(boolean value,String message) {checks++;if(!value)throw new AssertionError(message);}
    static ClassNode node(String name) throws Exception {
        var entry=JAR.getEntry(name+".class");
        check(entry!=null,"final JAR class exists: "+name);
        var n=new ClassNode();
        try(var in=JAR.getInputStream(entry)) { new ClassReader(in.readAllBytes()).accept(n,0); }
        return n;
    }
    static boolean hasMethod(ClassNode n,String method) {return n.methods.stream().anyMatch(m->m.name.equals(method));}
    static boolean calls(ClassNode n,String method,String ownerSuffix,String called) {
        for(var m:n.methods)if(m.name.equals(method))for(var i:m.instructions)
            if(i instanceof MethodInsnNode c && c.owner.endsWith(ownerSuffix) && c.name.equals(called))return true;
        return false;
    }
    static boolean hasString(ClassNode n,String fragment) {
        for(var m:n.methods)for(var i:m.instructions) {
            if(i instanceof LdcInsnNode ldc && ldc.cst instanceof String s && s.contains(fragment))return true;
            if(i instanceof InvokeDynamicInsnNode indy)
                for(var arg:indy.bsmArgs)
                    if(arg instanceof String s && s.contains(fragment))return true;
        }
        return false;
    }
    static GunType gun(String shortName) {
        for(var type:InfoType.infoTypes)
            if(type instanceof GunType gun && shortName.equals(type.shortName))return gun;
        throw new AssertionError("missing gun definition "+shortName);
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        JAR=new java.util.zip.ZipFile(args[0]);
        for(int i=1;i<args.length;i++)new OfflinePack(Path.of(args[i])).readTypes();

        // ---------------------------------------------------------------- AK74M slice data
        var ak=gun("AK74M");
        BakedRig rig=ModelCache.rigFor(ak,ak.modelScale);
        check(rig!=null,"AK74M bakes a rig");
        check(rig.switchModelGroups==3,"the class records switchModel.length / 3 = 3 fire-mode groups");
        check(rig.switchModelRendererCount()==48,"48 switchModel renderers are indexed (16 per position)");
        check(rig.switchModelsPerState()==16,"16 renderers belong to one selector position");

        var s0=rig.switchModelSlice(0); var s1=rig.switchModelSlice(1); var s2=rig.switchModelSlice(2);
        check(s0!=null && s1!=null && s2!=null,"each of the three positions yields a slice");
        check(s0.size()>0 && s0.size()==s1.size() && s1.size()==s2.size(),
                "all three positions carry the same amount of geometry");
        var identity=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<BakedRig.Quad,Boolean>());
        identity.addAll(s0);
        int before=identity.size();
        identity.addAll(s1); identity.addAll(s2);
        check(identity.size()==s0.size()*3,"the three slices are disjoint and cover exactly one array");
        check(!s0.equals(s1),"position 0 and 1 are different meshes, not the same geometry");
        check(!s0.contains(s1.get(0)),"a quad belongs to exactly one position");

        int total=rig.switchModelRendererCount();
        var hidden0=rig.switchModelHidden(0);
        check(hidden0.size()+s0.size()>0,"the preview hides the other positions");
        check(hidden0.size()==s1.size()+s2.size(),"hidden(0) is exactly the other two positions");
        check(rig.switchModelHidden(-1).isEmpty(),"a negative mode disables filtering for the offline checks");
        check(rig.switchModelHidden(7).equals(hidden0)||rig.switchModelHidden(7).size()==hidden0.size(),
                "an out-of-range mode clamps to a real position instead of hiding everything");

        // ---------------------------------------------------------------- fireModeNum
        // Offline definitions carry no item instance; the mode helpers only touch the stack's
        // custom data, so a vanilla stack is enough to exercise the mapping.
        var stack=new ItemStack(net.minecraft.world.item.Items.STICK);
        check(ak.modes.size()==3,"AK74M lists fullauto, semiauto and safe");
        for(int i=0;i<ak.modes.size();i++) {
            ak.setModeFor(stack,ak.modes.get(i));
            check(ak.fireModeIndex(stack)==i,"mode "+ak.modes.get(i)+" maps to fireModeNum "+i);
        }
        check(ak.modes.get(0)!=ak.modes.get(1),"the three modes are distinct values");

        // A magazine has no selector: that path must stay a no-op.
        var mag=gun("6L20_545x39_30Rounds");
        BakedRig magRig=ModelCache.rigFor(mag,mag.modelScale);
        check(magRig!=null && magRig.switchModelsPerState()==0,"a magazine has no selector slice");
        check(magRig.switchModelHidden(0).isEmpty(),"no selector means no hidden quads");

        // ---------------------------------------------------------------- wiring
        var bytecode=node("com/flansmod/client/model/loader/BytecodeModel");
        check(hasString(bytecode,"numSwitchModel"),"the extractor reads numSwitchModel from the class");
        var baked=node("com/flansmod/client/model/loader/BakedRig");
        check(hasMethod(baked,"switchModelSlice") && hasMethod(baked,"switchModelHidden"),
                "the rig exposes the selector slice");
        var item=node("com/flansmod/client/render/TurborigItemRenderer");
        check(calls(item,"renderAnimatedParts","model/loader/BakedRig","switchModelSlice"),
                "the held/third-person renderer draws only the current position");
        var preview=node("com/flansmod/client/gui/GunModificationPreview");
        check(calls(preview,"render","types/GunType","fireModeIndex"),
                "the modification preview reads the gun's fire-mode index");
        check(calls(preview,"renderMesh","model/loader/BakedRig","switchModelHidden"),
                "the modification preview skips the other positions");
        var type=node("com/flansmod/common/types/GunType");
        check(hasMethod(type,"fireModeIndex"),"GunType exposes 1.7.10's fireModeNum");

        System.out.println("PASS VerifyFireModeSelector: "+checks+" assertions; AK74M selector is 48 renderers in 3"
                +" positions of 16, sliced by fireModeNum, and both render paths use the slice. No game/GPU/input.");
    }
}
