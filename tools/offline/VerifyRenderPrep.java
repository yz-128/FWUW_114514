import com.flansmod.client.render.RenderPerf;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

import java.util.zip.ZipFile;

/**
 * Checks the compatibility/optimization preparation layer that ships in the final jar:
 *
 * <ol>
 *   <li>the render instrumentation is opt-in and, while off, records nothing;</li>
 *   <li>the two hot hooks (scope camera pass, rig quad emission) are actually wired;</li>
 *   <li>the compatibility report exists and watches the mods this dev client ships.</li>
 * </ol>
 *
 * Args: {@code <final jar>}. No game launched.
 */
public final class VerifyRenderPrep {
    static int checks;
    static ZipFile JAR;
    static void check(boolean value,String message) {checks++;if(!value)throw new AssertionError(message);}
    static ClassNode node(String name) throws Exception {
        var entry=JAR.getEntry(name+".class");
        check(entry!=null,"final JAR class exists: "+name);
        var n=new ClassNode();
        try(var in=JAR.getInputStream(entry)) { new ClassReader(in.readAllBytes()).accept(n,0); }
        return n;
    }
    static boolean hasMethod(ClassNode n,String name) {
        return n.methods.stream().anyMatch(m->m.name.equals(name));
    }
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

    public static void main(String[] args) throws Exception {
        JAR=new ZipFile(args[0]);

        var perf=node("com/flansmod/client/render/RenderPerf");
        check(perf.fields.stream().anyMatch(f->f.name.equals("ENABLED")),"instrumentation has a single static switch");
        check(hasString(perf,"flansmod.renderPerf"),"the switch reads -Dflansmod.renderPerf");
        check(hasMethod(perf,"addScopePass") && hasMethod(perf,"addRigQuads") && hasMethod(perf,"frame"),
                "the three hooks exist");
        check(hasMethod(perf,"endFrame") && hasMethod(perf,"scene"),"completed frames and scene boundaries are instrumented");
        check(hasString(perf,"scene=") && hasString(perf,"phase=") && hasString(perf,"windowNs="),"windows carry scene, warmup phase and elapsed wall time");

        // The switch is off in this JVM, so the hooks must be inert.
        check(!RenderPerf.ENABLED,"instrumentation is off unless asked for");
        RenderPerf.addScopePass(1_000_000L);
        RenderPerf.addRigQuads(1234);
        RenderPerf.frame();
        check(RenderPerf.totalScopeNanos()==0L,"scope pass timing is not recorded while off");
        check(RenderPerf.totalRigQuads()==0L,"rig quad counting is not recorded while off");

        var scope=node("com/flansmod/client/render/ScopeWorldTexture");
        check(calls(scope,"begin","render/RenderPerf","frame"),"the scope renderer announces each frame to the counters");
        check(calls(scope,"prepare","render/RenderPerf","addScopePass"),"the independent camera pass is timed");

        var rig=node("com/flansmod/client/render/RigRenderer");
        check(calls(rig,"renderPart","render/RenderPerf","addRigQuads"),"per-part quad emission is counted");
        check(calls(rig,"render","render/RenderPerf","addRigQuads"),"whole-rig quad emission is counted");

        var compat=node("com/flansmod/client/CompatReport");
        check(hasString(compat,"[Compat]"),"the environment report has a log prefix");
        check(hasString(compat,"iris") && hasString(compat,"sodium"),"shader and optimizer mods are watched by name");
        check(hasString(compat,"sclp") && hasString(compat,"versions="),"SCLP and actual loaded versions are reported");
        check(calls(compat,"describe","render/ShaderMuzzleRenderer","shadersEnabled"),
                "the report asks Iris whether shaders are on");
        check(calls(compat,"describe","render/ScopeWorldTexture","dualRenderEnabled"),
                "the report says which scope-camera branch is active");
        check(hasString(compat,"GL_RENDERER") || hasString(compat,"renderer"),"the report carries the GL strings");

        System.out.println("PASS VerifyRenderPrep: "+checks+" assertions; opt-in render instrumentation and the"
                +" compatibility report are wired in the shipped jar. No game/GPU/input.");
    }
}
