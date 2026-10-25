import com.flansmod.client.model.loader.BakedRig;
import com.flansmod.client.model.loader.BytecodeModel;
import com.flansmod.client.model.loader.ModelGeometry;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Pure CPU selector audit and allocation benchmark; no game bootstrap, graphics or pack writes. */
public final class VerifySelectorCache {
    private static int checks, selectors, switchStates, slideStates, parsed;
    private static volatile Object sliceSink, slideSink, hiddenSink;
    private static void check(boolean condition,String message) {
        checks++; if(!condition) throw new AssertionError(message);
    }
    private static Set<BakedRig.Quad> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }
    private static List<List<BakedRig.Quad>> rendererOracle(BakedRig rig,String name) {
        var result=new ArrayList<List<BakedRig.Quad>>();
        for(int i=0;;i++) {
            var quads=rig.quadsByRenderer.get(name+"#"+i);
            if(quads==null || quads.isEmpty()) return result;
            result.add(quads);
        }
    }
    private static List<BakedRig.Quad> sliceOracle(List<List<BakedRig.Quad>> renderers,int states,int mode) {
        if(states<=0 || renderers.isEmpty() || renderers.size()%states!=0) return null;
        int count=renderers.size()/states;
        int selected=Math.max(0,Math.min(states-1,mode));
        var result=new ArrayList<BakedRig.Quad>();
        for(int i=selected*count;i<(selected+1)*count;i++) result.addAll(renderers.get(i));
        return result;
    }
    private static void sameSequence(List<BakedRig.Quad> actual,List<BakedRig.Quad> expected,String message) {
        check((actual==null)==(expected==null),message+" null state");
        if(expected==null) return;
        check(actual.size()==expected.size(),message+" face count");
        for(int i=0;i<expected.size();i++) check(actual.get(i)==expected.get(i),message+" order/identity #"+i);
    }
    private static void immutable(Runnable mutation,String message) {
        try {mutation.run();throw new AssertionError(message);}
        catch(UnsupportedOperationException expected) {checks++;}
    }
    private static void verify(BakedRig rig,String name) {
        var switchRenderers=rendererOracle(rig,"switchModel");
        var slideRenderers=rendererOracle(rig,"slideSwitchModel");
        check(rig.switchModelRendererCount()==switchRenderers.size(),name+" switch count");
        check(rig.slideSwitchModelRendererCount()==slideRenderers.size(),name+" slide count");
        var modes=new LinkedHashSet<Integer>(List.of(Integer.MIN_VALUE,-1,0,1,Integer.MAX_VALUE));
        for(int mode=0;mode<Math.max(rig.switchModelGroups,rig.slideSwitchModelGroups)+1;mode++) modes.add(mode);
        for(int mode:modes) {
            var expected=sliceOracle(switchRenderers,rig.switchModelGroups,mode);
            var actual=rig.switchModelSlice(mode);
            sameSequence(actual,expected,name+" switch "+mode);
            check(actual==rig.switchModelSlice(mode),name+" switch snapshot reused");
            var slideExpected=sliceOracle(slideRenderers,rig.slideSwitchModelGroups,mode);
            var slideActual=rig.slideSwitchModelSlice(mode);
            sameSequence(slideActual,slideExpected,name+" slide "+mode);
            check(slideActual==rig.slideSwitchModelSlice(mode),name+" slide snapshot reused");
            var shown=identitySet(); if(expected!=null) shown.addAll(expected);
            var excluded=identitySet();
            if(mode>=0 && expected!=null) for(var group:switchRenderers)
                for(var quad:group) if(!shown.contains(quad)) excluded.add(quad);
            var hidden=rig.switchModelHidden(mode);
            check(hidden.size()==excluded.size(),name+" hidden face count "+mode);
            for(var quad:rig.quads) check(hidden.contains(quad)==excluded.contains(quad),name+" hidden identity membership");
            check(hidden==rig.switchModelHidden(mode),name+" hidden snapshot reused");
            if(!hidden.isEmpty()) {
                var quad=hidden.iterator().next();
                var equalButDistinct=new BakedRig.Quad(quad.positions(),quad.uvs(),quad.nx(),quad.ny(),quad.nz());
                check(equalButDistinct.equals(quad),name+" identity control has record equality");
                check(!hidden.contains(equalButDistinct),name+" hidden membership uses identity");
                immutable(()->hidden.remove(quad),name+" hidden collection must be immutable");
            }
            if(actual!=null) immutable(()->actual.add(rig.quads.getFirst()),name+" switch collection must be immutable");
            if(slideActual!=null) immutable(()->slideActual.add(rig.quads.getFirst()),name+" slide collection must be immutable");
        }
        if(!switchRenderers.isEmpty() && rig.switchModelsPerState()>0) switchStates+=rig.switchModelGroups;
        if(!slideRenderers.isEmpty() && rig.slideSwitchModelGroups>0 && slideRenderers.size()%rig.slideSwitchModelGroups==0)
            slideStates+=rig.slideSwitchModelGroups;
    }
    private static ModelGeometry.Model synthetic(int switches,int switchCount,int slides,int slideCount) {
        var model=new ModelGeometry.Model();
        model.switchModelGroups=switches; model.slideSwitchModelGroups=slides;
        add(model,"gunModel",1); add(model,"switchModel",switchCount); add(model,"slideSwitchModel",slideCount);
        return model;
    }
    private static void add(ModelGeometry.Model model,String name,int count) {
        var part=new ModelGeometry.Part();part.name=name;
        for(int i=0;i<count;i++) {
            var box=new ModelGeometry.Box();box.renderer=name+"#"+i;
            box.x=i;box.w=box.h=box.d=1;part.boxes.add(box);
        }
        model.parts.put(name,part);
    }
    private static void regression(Path root) throws Exception {
        var model=synthetic(3,6,2,4);
        var first=BakedRig.bake(model,1F);verify(first,"synthetic 3/2 states");
        var second=BakedRig.bake(model,1F);verify(second,"fresh bake");
        check(first.switchModelSlice(0)!=second.switchModelSlice(0),"rebaked rigs do not retain previous snapshot");
        check(first.switchModelSlice(0).getFirst()!=second.switchModelSlice(0).getFirst(),"rebaked geometry identity is fresh");
        verify(BakedRig.bake(synthetic(0,0,0,0),1F),"no selector");
        verify(BakedRig.bake(synthetic(0,6,-1,4),1F),"disabled groups");
        verify(BakedRig.bake(synthetic(4,6,3,4),1F),"indivisible arrays");
        verify(BakedRig.bake(synthetic(8,6,6,4),1F),"more states than renderers");
        verify(BakedRig.bake(synthetic(1,6,1,4),1F),"single state");
        switchStates=slideStates=0;
        try(var files=Files.walk(root)) {
            for(var file:files.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".class"))
                    .filter(p->!root.relativize(p).startsWith("_disabled")).sorted().toList()) {
                var geometry=BytecodeModel.fromFile(file);parsed++;
                if(geometry.switchModelGroups<=0 && geometry.slideSwitchModelGroups<=0) continue;
                verify(BakedRig.bake(geometry,1F),root.relativize(file).toString());selectors++;
            }
        }
        check(selectors>0,"real content packs contain selector geometry");
        System.out.println("PASS VerifySelectorCache: "+checks+" assertions; "+parsed+" active pack classes parsed, "
                +selectors+" selector model sources, "+switchStates+" production switch states and "+slideStates
                +" production slide states; synthetic slide/boundary checks included; immutable identity-safe caches, fresh re-bakes. No game/GPU/pack writes.");
    }
    private static void consume(BakedRig rig,int iteration) {
        int mode=iteration%Math.max(1,rig.switchModelGroups);
        sliceSink=rig.switchModelSlice(mode);slideSink=rig.slideSwitchModelSlice(mode);hiddenSink=rig.switchModelHidden(mode);
    }
    private static void benchmark(Path source,int iterations) throws Exception {
        var rig=BakedRig.bake(BytecodeModel.fromFile(source),1F);
        if(rig.switchModelsPerState()<=0) throw new AssertionError("benchmark source must have a selector");
        var bean=ManagementFactory.getThreadMXBean();
        if(!(bean instanceof com.sun.management.ThreadMXBean allocated) || !allocated.isThreadAllocatedMemorySupported())
            throw new AssertionError("thread allocation measurements unavailable");
        allocated.setThreadAllocatedMemoryEnabled(true);
        long thread=Thread.currentThread().threadId();
        for(int i=0;i<Math.min(iterations,10000);i++) consume(rig,i);
        long[] bytes=new long[7], nanos=new long[7];
        for(int sample=0;sample<bytes.length;sample++) {
            long beforeBytes=allocated.getThreadAllocatedBytes(thread), beforeTime=System.nanoTime();
            for(int i=0;i<iterations;i++) consume(rig,i);
            nanos[sample]=System.nanoTime()-beforeTime;bytes[sample]=allocated.getThreadAllocatedBytes(thread)-beforeBytes;
        }
        Arrays.sort(bytes);Arrays.sort(nanos);
        String sourceHash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source)));
        System.out.println("{\"iterations\":"+iterations+",\"samples\":7,\"quads\":"+rig.quads.size()
                +",\"states\":"+rig.switchModelGroups+",\"sourceSha256\":\""+sourceHash+"\",\"medianBytes\":"+bytes[3]
                +",\"medianNanos\":"+nanos[3]+",\"bytesPerIteration\":"+(bytes[3]/(double)iterations)
                +",\"nanosPerIteration\":"+(nanos[3]/(double)iterations)+"}");
    }
    public static void main(String[] args) throws Exception {
        if(args.length<2) throw new IllegalArgumentException("--verify <active pack root> or --benchmark <model class> [iterations]");
        if(args[0].equals("--verify")) regression(Path.of(args[1]));
        else if(args[0].equals("--benchmark")) benchmark(Path.of(args[1]),args.length>2?Integer.parseInt(args[2]):10000);
        else throw new IllegalArgumentException(args[0]);
    }
}
