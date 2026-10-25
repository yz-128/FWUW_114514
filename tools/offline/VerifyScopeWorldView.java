import com.flansmod.client.render.ScopeWorldTexture;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.nio.file.*;
import java.util.*;

/** Non-identity outer hand camera -> nested world camera -> restored hand camera.
 * Calls the actual production WorldView guard. With an old JAR lacking it, records the
 * unchanged old model-view path and expected failures. This is a synthetic GPU scene,
 * not a Minecraft screenshot or reproduction of a particular user's weapon mesh. */
public final class VerifyScopeWorldView {
    private static final int W=640,H=360;
    private static int checks,failures,cases,program,vao,vbo;
    private static Constructor<?> guard;
    private static boolean diagnose;
    private static Path output;
    private static void check(boolean good,String label) {
        checks++;
        if(!good){failures++;System.out.println("FAIL: "+label);}
    }
    private static AutoCloseable enter() throws Exception {
        return guard==null?()->{}:(AutoCloseable)guard.newInstance();
    }
    private static void wiring() throws Exception {
        System.out.println("Production origin="+ScopeWorldTexture.class.getProtectionDomain().getCodeSource().getLocation());
        try {
            guard=Class.forName("com.flansmod.client.render.ScopeWorldTexture$WorldView").getDeclaredConstructor();
            guard.setAccessible(true);
        } catch(ClassNotFoundException absent) {System.out.println("Old JAR has no WorldView guard; negative control uses its unreset hand stack.");}
        var node=new ClassNode();
        try(var input=ScopeWorldTexture.class.getResourceAsStream("ScopeWorldTexture.class")) {
            new ClassReader(Objects.requireNonNull(input)).accept(node,0);
        }
        int enter=-1,render=Integer.MAX_VALUE,close=0,push=0,pop=0,flush=-1,index=0;
        for(var method:node.methods)if(method.name.equals("prepare"))for(var instruction:method.instructions) {
            if(instruction instanceof TypeInsnNode type&&type.getOpcode()==Opcodes.NEW&&type.desc.endsWith("ScopeWorldTexture$WorldView"))enter=index;
            if(instruction instanceof MethodInsnNode call) {
                if(call.owner.endsWith("GameRenderer")&&call.name.equals("renderLevel"))render=index;
                if(call.owner.endsWith("ScopeWorldTexture$WorldView")&&call.name.equals("close"))close++;
                if(call.owner.equals("org/joml/Matrix4fStack")&&call.name.equals("pushMatrix"))push++;
                if(call.owner.equals("org/joml/Matrix4fStack")&&call.name.equals("popMatrix"))pop++;
                if(call.owner.endsWith("ScopeWorldTexture")&&call.name.equals("flushPendingHandGeometry"))flush=index;
            }
            index++;
        }
        check(guard!=null&&enter>=0&&enter<render,"actual prepare enters tested WorldView before recursive renderLevel");
        check(close>=2,"actual prepare closes WorldView normally and on exceptional exits");
        check(push>0&&pop>0,"existing outer model-view stack protection retained");
        check(flush>=0&&flush<enter,"existing hand-buffer flush precedes world-view isolation");
    }
    private static int shader(int kind,String source) {
        int s=GL20.glCreateShader(kind);GL20.glShaderSource(s,source);GL20.glCompileShader(s);
        if(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)==0)throw new IllegalStateException(GL20.glGetShaderInfoLog(s));
        return s;
    }
    private static void init() {
        int vs=shader(GL20.GL_VERTEX_SHADER,"#version 150\nin vec3 Position; in vec3 Color; uniform mat4 ProjMat; uniform mat4 ModelViewMat; out vec3 rgb; void main(){gl_Position=ProjMat*ModelViewMat*vec4(Position,1.0);rgb=Color;}");
        int fs=shader(GL20.GL_FRAGMENT_SHADER,"#version 150\nin vec3 rgb; out vec4 frag; void main(){frag=vec4(rgb,1.0);}");
        program=GL20.glCreateProgram();GL20.glAttachShader(program,vs);GL20.glAttachShader(program,fs);
        GL20.glBindAttribLocation(program,0,"Position");GL20.glBindAttribLocation(program,1,"Color");GL20.glLinkProgram(program);
        if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(program));
        GL20.glDeleteShader(vs);GL20.glDeleteShader(fs);vao=GL30.glGenVertexArrays();vbo=GL15.glGenBuffers();
    }
    private static void quad(List<Float> data,float x,float y,float z,float size,float r,float g,float b) {
        float[][] points={{x-size,y-size,z},{x+size,y-size,z},{x+size,y+size,z},{x-size,y+size,z}};
        for(int i:new int[]{0,1,2,0,2,3})for(float v:new float[]{points[i][0],points[i][1],points[i][2],r,g,b})data.add(v);
    }
    private static BufferedImage raster(Matrix4f modelView) {
        RenderSystem.disableScissor();RenderSystem.colorMask(true,true,true,true);RenderSystem.depthMask(true);
        RenderSystem.clearColor(.30F,.43F,.59F,1);RenderSystem.clearDepth(1);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,false);
        RenderSystem.disableBlend();RenderSystem.disableCull();RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);
        var vertices=new ArrayList<Float>();
        // Fixed, independently authored world landmarks at multiple depths. Their expected
        // matrix below is the clean world camera, never derived from production's frame.
        quad(vertices,-1.2F,-.2F,-5,.28F,.95F,.17F,.12F);
        quad(vertices,.0F,.35F,-4,.22F,.9F,.85F,.16F);
        quad(vertices,1.2F,-.15F,-6,.4F,.15F,.85F,.35F);
        quad(vertices,-.35F,-.6F,-3,.17F,.04F,.04F,.04F);
        var data=BufferUtils.createFloatBuffer(vertices.size());for(float v:vertices)data.put(v);data.flip();
        GL20.glUseProgram(program);GL30.glBindVertexArray(vao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER,data,GL15.GL_STREAM_DRAW);
        GL20.glEnableVertexAttribArray(0);GL20.glVertexAttribPointer(0,3,GL11.GL_FLOAT,false,24,0);
        GL20.glEnableVertexAttribArray(1);GL20.glVertexAttribPointer(1,3,GL11.GL_FLOAT,false,24,12);
        var matrix=BufferUtils.createFloatBuffer(16);
        new Matrix4f().perspective((float)Math.toRadians(64),W/(float)H,.05F,100).get(matrix);
        GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program,"ProjMat"),false,matrix);
        modelView.get(matrix);GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program,"ModelViewMat"),false,matrix);
        GL11.glDrawArrays(GL11.GL_TRIANGLES,0,vertices.size()/6);
        var pixels=BufferUtils.createByteBuffer(W*H*4);GL11.glReadPixels(0,0,W,H,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        var image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=(y*W+x)*4;image.setRGB(x,H-1-y,((pixels.get(i)&255)<<16)|((pixels.get(i+1)&255)<<8)|(pixels.get(i+2)&255));}
        return image;
    }
    private static int difference(BufferedImage a,BufferedImage b) {
        int different=0;for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(a.getRGB(x,y)!=b.getRGB(x,y))different++;
        return different;
    }
    private static void sample(float yaw,float pitch,boolean throwsAfterDraw) throws Exception {
        String name=String.format(Locale.ROOT,"hand-yaw%+.0f-pitch%+.0f-%s",yaw,pitch,throwsAfterDraw?"throw":"normal");
        var hand=new Matrix4f().rotateY((float)Math.toRadians(yaw)).rotateX((float)Math.toRadians(pitch));
        // A clean normal world pass has only this camera. It is not built by WorldView.
        var worldCamera=new Matrix4f().rotationY(.13F).rotateX(-.07F);
        var reference=raster(new Matrix4f().rotationY(.13F).rotateX(-.07F));
        ImageIO.write(reference,"png",output.resolve(name+"-reference.png").toFile());
        var expectedOld=new Matrix4f(hand).mul(worldCamera);
        var legacy=raster(expectedOld);ImageIO.write(legacy,"png",output.resolve(name+"-old-hand-times-world.png").toFile());
        var stack=RenderSystem.getModelViewStack();stack.pushMatrix();stack.set(hand);RenderSystem.applyModelViewMatrix();
        BufferedImage production=null;
        try {
            try(var state=enter()) {
                check(RenderSystem.getModelViewMatrix().equals(new Matrix4f(),0),name+" world entry is identity");
                // Mirror LevelRenderer's real push/mul(worldCamera)/apply/pop sequence.
                stack.pushMatrix();
                try {stack.mul(worldCamera);RenderSystem.applyModelViewMatrix();production=raster(new Matrix4f(RenderSystem.getModelViewMatrix()));}
                finally {stack.popMatrix();RenderSystem.applyModelViewMatrix();}
                if(throwsAfterDraw)throw new IllegalStateException("simulated nested world failure");
            } catch(IllegalStateException expected) {
                if(!throwsAfterDraw||!expected.getMessage().startsWith("simulated"))throw expected;
            }
            check(stack.equals(hand,0),name+" hand stack restored exactly");
            check(RenderSystem.getModelViewMatrix().equals(hand,0),name+" applied hand shader matrix restored exactly");
            check(production!=null,name+" production image exists");
            if(production!=null) {
                ImageIO.write(production,"png",output.resolve(name+"-production.png").toFile());
                int mismatch=difference(reference,production),oldDifference=difference(reference,legacy);
                check(mismatch==0,name+" production matches independently clean world pixels");
                if(yaw!=0||pitch!=0)check(oldDifference>1000,name+" old hand-camera inheritance reproduces >1000 wrong pixels");
                System.out.printf(Locale.ROOT,"%s oldWrongPixels=%d productionWrongPixels=%d%n",name,oldDifference,mismatch);
            }
            check(GL11.glGetError()==GL11.GL_NO_ERROR,name+" no GL errors");cases++;
        } finally {stack.popMatrix();RenderSystem.applyModelViewMatrix();}
    }
    public static void main(String[] args) throws Exception {
        output=Path.of(args[0]);Files.createDirectories(output);diagnose=Arrays.asList(args).contains("--diagnose");
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();RenderSystem.initRenderThread();
        if(!GLFW.glfwInit())throw new AssertionError("GLFW init");GLFW.glfwDefaultWindowHints();GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,2);GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window=GLFW.glfwCreateWindow(W,H,"offline nested scope world-view",0,0);if(window==0)throw new AssertionError("hidden context");
        GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();System.out.println("GPU="+GL11.glGetString(GL11.GL_RENDERER));
        TextureTarget target=null;
        try {
            wiring();init();target=new TextureTarget(W,H,true,false);target.bindWrite(true);
            for(float[] hand:new float[][]{{0,0},{23,-12},{85,27},{170,-35}})for(boolean fail:new boolean[]{false,true})sample(hand[0],hand[1],fail);
        } finally {
            if(target!=null)target.destroyBuffers();GL15.glDeleteBuffers(vbo);GL30.glDeleteVertexArrays(vao);GL20.glDeleteProgram(program);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();
        }
        System.out.printf("VerifyScopeWorldView: %d checks, %d GPU cases, %d failures; synthetic scene, no Minecraft/window/world started%n",checks,cases,failures);
        if(failures>0&&!diagnose)throw new AssertionError(failures+" failures");
    }
}
