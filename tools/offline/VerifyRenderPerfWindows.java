import com.flansmod.client.render.RenderPerf;
import java.lang.reflect.*;

/** Deterministic completed-frame/window/scene tests against final-JAR production code. */
public final class VerifyRenderPerfWindows {
    static int checks;
    static Method begin, finish, dump;
    static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    static Object get(String name) throws Exception {
        Field f=RenderPerf.class.getDeclaredField(name); f.setAccessible(true); return f.get(null);
    }
    static long count(String name) throws Exception { return (Long)get(name); }
    static void set(String name, Object value) throws Exception {
        Field f=RenderPerf.class.getDeclaredField(name); f.setAccessible(true); f.set(null,value);
    }
    static Method method(String name) throws Exception {
        Method m=RenderPerf.class.getDeclaredMethod(name,long.class); m.setAccessible(true); return m;
    }
    public static void main(String[] args) throws Exception {
        check(RenderPerf.ENABLED,"test requires opt-in JVM");
        begin=method("begin"); finish=method("finish"); dump=method("dump");
        long now=1_000_000_000L;
        RenderPerf.scene("ads");
        RenderPerf.addScopePass(500); RenderPerf.addRigQuads(99);
        check(RenderPerf.totalScopeNanos()==0,"outside-frame hooks ignored");
        begin.invoke(null,now);
        check(RenderPerf.frames()==0,"pre cannot count unfinished frame");
        RenderPerf.addScopePass(100); RenderPerf.addRigQuads(9);
        finish.invoke(null,now+10_000_000);
        check(RenderPerf.frames()==1 && count("windowFrames")==1,"post counts completed frame");
        check(RenderPerf.totalScopeNanos()==100 && RenderPerf.totalRigQuads()==9,"paired work counts together");
        check(count("windowCpuNanos")==10_000_000,"CPU/wall submission duration uses pre/post pair");
        finish.invoke(null,now+20_000_000);
        check(RenderPerf.frames()==1,"duplicate post ignored");
        begin.invoke(null,now+1_100_000_000);
        check(!(Boolean)get("warming") && count("windowFrames")==0,"warmup flushes before measured frame");
        finish.invoke(null,now+1_101_000_000);
        check(count("windowScopeNanos")==0,"warmup work never enters measured window");
        RenderPerf.scene("gunsmith");
        check(get("activeScene").equals("ads"),"scene request waits for next pre");
        begin.invoke(null,now+1_200_000_000);
        check(get("activeScene").equals("gunsmith") && (Boolean)get("warming"),"new scene starts separate warmup");
        check(count("windowFrames")==0 && count("windowRigQuads")==0,"old scene counters flushed");
        RenderPerf.addScopePass(999); RenderPerf.addRigQuads(123);
        begin.invoke(null,now+1_210_000_000);
        finish.invoke(null,now+1_211_000_000);
        check(count("windowScopeNanos")==0 && count("windowRigQuads")==0,"unpaired pre discarded");
        dump.invoke(null,now+1_220_000_000);
        set("warming",false);
        long totalFrames=RenderPerf.frames(), totalScope=RenderPerf.totalScopeNanos(), totalQuads=RenderPerf.totalRigQuads();
        for (int i=0;i<RenderPerf.WINDOW_FRAMES;i++) {
            long t=now+2_000_000_000L+i*10_000_000L;
            begin.invoke(null,t);
            RenderPerf.addScopePass(2_000_000); RenderPerf.addScopePass(-1);
            RenderPerf.addRigQuads(17); RenderPerf.addRigQuads(-100);
            finish.invoke(null,t+4_000_000);
            if(i==RenderPerf.WINDOW_FRAMES-2) {
                check(count("windowFrames")==RenderPerf.WINDOW_FRAMES-1,"window stays open until last completed frame");
                check(count("windowScopeNanos")==2_000_000L*(i+1),"last in-window work uses same divisor");
            }
        }
        check(count("windowFrames")==0 && count("windowScopeNanos")==0 && count("windowRigQuads")==0,"full window resets together");
        check(RenderPerf.frames()==totalFrames+RenderPerf.WINDOW_FRAMES,"full window exact completed-frame total");
        check(RenderPerf.totalScopeNanos()==totalScope+2_000_000L*RenderPerf.WINDOW_FRAMES,"no boundary scope shifted/lost");
        check(RenderPerf.totalRigQuads()==totalQuads+17L*RenderPerf.WINDOW_FRAMES,"negative inputs cannot underflow samples");
        begin.invoke(null,now+5_000_000_000L); finish.invoke(null,now+5_004_000_000L);
        check(count("windowFrames")==1 && count("windowScopeNanos")==0,"next window has no prior numerator or cumulative divisor");
        System.out.println("PASS VerifyRenderPerfWindows: "+checks+" checks; completed frames, warmup, scene boundaries and exact window accounting. No game/GPU.");
    }
}
