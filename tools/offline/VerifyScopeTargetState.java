import com.flansmod.client.render.ScopeWorldTexture;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Runs the production target-write guard and colour-copy code in a hidden GPU context.
 * --legacy-control reconstructs the pre-fix allocation/clear sequence without the guard;
 * it is an explicit negative control, not a claim to execute a full Minecraft renderLevel. */
public final class VerifyScopeTargetState {
    private static int checks, failures;
    private static boolean legacy;
    private static void check(boolean ok,String label) {
        checks++;
        if(!ok) {failures++;System.out.println("FAIL: "+label);}
    }
    private static AutoCloseable enter() throws Exception {
        if(legacy)return ()->{};
        var type=Class.forName("com.flansmod.client.render.ScopeWorldTexture$TargetWrites");
        var constructor=type.getDeclaredConstructor();constructor.setAccessible(true);
        return (AutoCloseable)constructor.newInstance();
    }
    private static void normalWrites() {
        RenderSystem.disableScissor();RenderSystem.depthMask(true);RenderSystem.colorMask(true,true,true,true);
    }
    private static void guardWiring() throws Exception {
        if(legacy)return;
        var node=new ClassNode();
        try(var input=ScopeWorldTexture.class.getResourceAsStream("ScopeWorldTexture.class")) {
            new ClassReader(input).accept(node,0);
        }
        int guard=-1,target=Integer.MAX_VALUE,closes=0;
        for(var method:node.methods)if(method.name.equals("prepare")) {
            int index=0;
            for(var instruction:method.instructions) {
                if(instruction instanceof TypeInsnNode type&&type.getOpcode()==Opcodes.NEW) {
                    if(type.desc.endsWith("ScopeWorldTexture$TargetWrites"))guard=index;
                    if(type.desc.equals("com/mojang/blaze3d/pipeline/TextureTarget"))target=index;
                }
                if(instruction instanceof MethodInsnNode call&&call.owner.endsWith("ScopeWorldTexture$TargetWrites")&&call.name.equals("close"))closes++;
                index++;
            }
        }
        check(guard>=0&&guard<target,"production prepare enters the tested guard before target allocation");
        check(closes>=2,"production prepare closes guard on normal and exceptional exits");
    }
    private static void restoreChecks(boolean scissor,boolean depth,boolean[] color,String label) {
        check(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)==scissor,label+" scissor enabled restored");
        int[] box=new int[4];GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,box);
        check(java.util.Arrays.equals(box,new int[]{2,3,5,7}),label+" scissor box restored");
        check(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)==depth,label+" depth write restored");
        var mask=BufferUtils.createByteBuffer(4);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,mask);
        for(int c=0;c<4;c++)check((mask.get(c)!=0)==color[c],label+" color mask "+c+" restored");
        float[] clear=new float[4];GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE,clear);
        check(Math.abs(clear[0]-.13F)<.00001F&&Math.abs(clear[1]-.27F)<.00001F
                &&Math.abs(clear[2]-.39F)<.00001F&&Math.abs(clear[3]-.51F)<.00001F,label+" clear color restored");
        check(Math.abs(GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE)-.375)<.00001,label+" clear depth restored");
    }
    private static void clearCase(boolean resized,boolean scissor,boolean depth,boolean[] mask,boolean fail) throws Exception {
        String label=(resized?"resize":"allocate")+" scissor="+scissor+" depthWrite="+depth+" fail="+fail;
        TextureTarget target=null;
        try {
            normalWrites();
            if(resized) {
                target=new TextureTarget(16,16,true,false);
                target.setClearColor(1,0,1,1);target.clear(false);
            }
            RenderSystem.enableScissor(2,3,5,7);if(!scissor)RenderSystem.disableScissor();
            RenderSystem.depthMask(depth);RenderSystem.colorMask(mask[0],mask[1],mask[2],mask[3]);
            RenderSystem.clearColor(.13F,.27F,.39F,.51F);RenderSystem.clearDepth(.375);
            try(var state=enter()) {
                check(!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),label+" complete viewport enabled before allocation");
                check(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),label+" depth writes enabled before allocation");
                if(target==null)target=new TextureTarget(32,24,true,false);else target.resize(32,24,false);
                target.setClearColor(.2F,.4F,.6F,.8F);target.clear(false);
                // A nested renderer can also leave its own state; restoration must not merely
                // undo the guard's entry values. Preserve the box even if scissor was disabled.
                RenderSystem.enableScissor(0,0,1,1);RenderSystem.depthMask(true);
                RenderSystem.colorMask(true,false,true,false);
                if(fail)throw new IllegalStateException("simulated renderer failure after target clear");
            } catch(IllegalStateException expected) {
                if(!fail||!expected.getMessage().startsWith("simulated"))throw expected;
            }
            restoreChecks(scissor,depth,mask,label);
            target.bindWrite(false);
            for(int[] p:new int[][]{{0,0},{31,23},{16,12},{4,5}}) {
                var pixel=BufferUtils.createByteBuffer(4);
                GL11.glReadPixels(p[0],p[1],1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
                boolean color=true;int[] expected={51,102,153,204};
                for(int c=0;c<4;c++)color&=Math.abs((pixel.get(c)&255)-expected[c])<=1;
                check(color,label+" complete color clear at "+p[0]+","+p[1]);
                var z=BufferUtils.createFloatBuffer(1);GL11.glReadPixels(p[0],p[1],1,1,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,z);
                check(Math.abs(z.get(0)-1F)<.00001F,label+" complete depth clear at "+p[0]+","+p[1]);
            }
            check(GL11.glGetError()==GL11.GL_NO_ERROR,label+" no GL error");
        } finally {normalWrites();if(target!=null)target.destroyBuffers();}
    }
    private static void samplerCases() throws Exception {
        TextureTarget world=new TextureTarget(48,32,true,false);
        var field=ScopeWorldTexture.class.getDeclaredField("TEXTURE");field.setAccessible(true);
        AbstractTexture texture=(AbstractTexture)field.get(null);
        try {
            world.bindWrite(true);RenderSystem.clearColor(.2F,.4F,.6F,0);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT,false);
            ScopeWorldTexture.copyColor(48,32);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);RenderSystem.bindTexture(texture.getId());
            // RenderGun:112-126 sets GL_NEAREST for both filters and leaves the GL default
            // GL_REPEAT wrapping. Authored scopeGlassModel UVs may extend beyond 0..1.
            check(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER)==GL11.GL_NEAREST,"allocated texture native nearest min filter");
            check(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER)==GL11.GL_NEAREST,"allocated texture native nearest mag filter");
            check(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S)==GL11.GL_REPEAT,"allocated texture native repeat S");
            check(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T)==GL11.GL_REPEAT,"allocated texture native repeat T");
            // Deliberately change the sampler, so the reuse assertion can detect an
            // unintended reinitialization instead of merely rechecking its initial value.
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
            int sentinel=GL11.glGenTextures();RenderSystem.bindTexture(sentinel);RenderSystem.activeTexture(GL13.GL_TEXTURE3);
            ScopeWorldTexture.copyColor(48,32);
            check(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)==GL13.GL_TEXTURE3,"same-size copy restores active texture");
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            check(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)==sentinel,"same-size copy restores texture binding");
            RenderSystem.bindTexture(texture.getId());
            check(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER)==GL11.GL_LINEAR,"same-size copy does not redundantly reset sampler parameters");
            var pixels=BufferUtils.createByteBuffer(48*32*4);GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
            check(Math.abs((pixels.get(0)&255)-51)<=1&&Math.abs((pixels.get(1)&255)-102)<=1,"reused texture still receives fresh pixels");
            for(int i=3;i<pixels.capacity();i+=4)check((pixels.get(i)&255)==255,"native RGB scope copy has opaque alpha even when world alpha is zero");
            ScopeWorldTexture.copySquareColor(48,32);RenderSystem.bindTexture(texture.getId());
            check(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH)==32,"resized square texture width");
            check(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER)==GL11.GL_NEAREST,"resize initializes native nearest sampler again");
            texture.load(null);ScopeWorldTexture.copyColor(48,32);RenderSystem.bindTexture(texture.getId());
            check(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER)==GL11.GL_NEAREST,"resource reload initializes native nearest sampler on new texture id");
            check(GL11.glGetError()==GL11.GL_NO_ERROR,"sampler reuse, resize and reload have no GL errors");
            GL11.glDeleteTextures(sentinel);
        } finally {texture.releaseId();world.destroyBuffers();}
    }
    public static void main(String[] args) throws Exception {
        legacy=args.length>0&&args[0].equals("--legacy-control");
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();RenderSystem.initRenderThread();
        if(!GLFW.glfwInit())throw new AssertionError("GLFW init");
        GLFW.glfwDefaultWindowHints();GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window=GLFW.glfwCreateWindow(64,48,"offline scope target compatibility",0,0);
        if(window==0)throw new AssertionError("hidden context");
        GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
        System.out.println("GPU="+GL11.glGetString(GL11.GL_RENDERER)+"; mode="+(legacy?"pre-fix negative control":"production guard"));
        try {
            guardWiring();
            for(boolean resize:new boolean[]{false,true})for(boolean scissor:new boolean[]{false,true})
                for(boolean fail:new boolean[]{false,true})
                    clearCase(resize,scissor,false,new boolean[]{false,true,false,false},fail);
            clearCase(true,true,true,new boolean[]{true,true,true,true},false);
            samplerCases();
        } finally {GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
        System.out.printf("VerifyScopeTargetState: %d GPU checks; %d failures; no Minecraft window or world started%n",checks,failures);
        if(failures>0)throw new AssertionError(failures+" failed GPU checks");
    }
}
