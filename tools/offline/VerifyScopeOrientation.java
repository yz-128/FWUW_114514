import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.flansmod.common.types.*;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;

/** Independent screen-space quadrant oracle through real glass meshes and production submission.
 * The uploaded GL image is red/green on its top half and blue/yellow on its bottom half.
 * Expected pixels use screen left/right/up/down, never imageUv or framebufferUv.
 * Runs in a hidden GPU context without Minecraft, a world, or a resource-pack edit.
 */
public final class VerifyScopeOrientation {
    static final int W=VerifyScopeComposition.W,H=VerifyScopeComposition.H;
    static final int RED=0xE83238,GREEN=0x31CE66,BLUE=0x3469E8,YELLOW=0xE9CE35;
    static int cases,failures,checks;
    static Path output;
    static void check(boolean value,String label){checks++;if(!value){failures++;System.out.println("FAIL "+label);}}
    static int expected(int x,int y){return y<H/2?(x<W/2?RED:GREEN):(x<W/2?BLUE:YELLOW);}
    static int distance(int a,int b){return Math.abs((a>>16&255)-(b>>16&255))+Math.abs((a>>8&255)-(b>>8&255))+Math.abs((a&255)-(b&255));}
    static void sample(AttachmentType type,int scene,boolean mirrored)throws Exception {
        var glass=ModelCache.rigForReference(type.scopeGlassModel,1F);
        if(glass==null || glass.quads.isEmpty())throw new IllegalStateException("No glass "+type.shortName);
        var min=new Vector3f(Float.POSITIVE_INFINITY);var max=new Vector3f(Float.NEGATIVE_INFINITY);
        int apertureFaces=0;
        for(var quad:glass.quads)if(quad.nx()<-.9F){apertureFaces++;for(var vertex:quad.positions()){min.min(new Vector3f(vertex));max.max(new Vector3f(vertex));}}
        if(apertureFaces==0)throw new IllegalStateException("No eye-facing glass "+type.shortName);
        var center=new Vector3f(min).add(max).mul(.5F);
        float radius=Math.max(max.z-min.z,max.y-min.y)*.65F;
        var pose=new PoseStack();
        // Native +X forward, +Y up, +Z right. A mirrored hand reflects the model's screen X.
        pose.mulPose(new Matrix4f().translate(0,0,-radius*4).scale(mirrored?-1:1,1,1)
                .rotateY((float)Math.PI/2).translate(-center.x,-center.y,-center.z));
        var projection=new Matrix4f().ortho(-radius*W/H,radius*W/H,-radius,radius,.001F,radius*8);
        var capture=new VerifyScopeComposition.Capture();
        FmumSightRenderer.renderLensSurface(pose,capture,glass,new Matrix4f(projection).mul(pose.last().pose()),1,mirrored);
        check(capture.vertices.size()==glass.quads.size()*4,type.shortName+" preserves every authored face");
        RenderSystem.clearColor(.1F,.1F,.1F,1);GL11.glClearDepth(1);RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,false);
        // Opaque scope IMAGE writes depth and culls back faces in the production lens layer.
        ScopeRenderLayers.glass(()->VerifyScopeComposition.draw(capture,scene,projection,true));
        var image=VerifyScopeComposition.pixels();String label=type.shortName+(mirrored?"-mirrored-hand":"-normal-hand");
        ImageIO.write(image,"png",output.resolve(label+".png").toFile());
        int[] good=new int[4],total=new int[4],horizontalMirror=new int[4];
        // A central disk excludes lens outlines, circular clipping and authored wrap shells.
        int sampleRadius=(int)(H*.17F),gap=12;
        for(int y=H/2-sampleRadius;y<H/2+sampleRadius;y++)for(int x=W/2-sampleRadius;x<W/2+sampleRadius;x++){
            int dx=x-W/2,dy=y-H/2;if(dx*dx+dy*dy>sampleRadius*sampleRadius || Math.abs(dx)<gap || Math.abs(dy)<gap)continue;
            int c=image.getRGB(x,y)&0xFFFFFF;
            // Ignore actual geometric holes, but require a substantial independently colored sample per quadrant.
            if(distance(c,RED)>20 && distance(c,GREEN)>20 && distance(c,BLUE)>20 && distance(c,YELLOW)>20)continue;
            int quadrant=(dy<0?0:2)+(dx<0?0:1);total[quadrant]++;
            if(distance(c,expected(x,y))<20)good[quadrant]++;
            if(distance(c,expected(W-1-x,y))<20)horizontalMirror[quadrant]++;
        }
        System.out.printf(Locale.ROOT,"%s glass=%s faces=%d center=%s radius=%.7f total=%s correct=%s horizontallyMirrored=%s%n",
                label,type.scopeGlassModel,apertureFaces,center,radius,Arrays.toString(total),Arrays.toString(good),Arrays.toString(horizontalMirror));
        for(int q=0;q<4;q++){
            check(total[q]>100,label+" quadrant "+q+" has real pixels");
            check(good[q]>=total[q]*.98F,label+" quadrant "+q+" agrees with independent screen orientation");
        }
        check(GL11.glGetError()==GL11.GL_NO_ERROR,"no GPU error "+label);cases++;
    }
    public static void main(String[] args)throws Exception {
        if(args.length<2)throw new IllegalArgumentException("output pack...; requires EFT and Octagon for every sample");
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();output=Path.of(args[0]);Files.createDirectories(output);
        for(int i=1;i<args.length;i++){var pack=new OfflinePack(Path.of(args[i]));pack.readTypes();}
        RenderSystem.initRenderThread();if(!GLFW.glfwInit())throw new IllegalStateException("GLFW");
        GLFW.glfwDefaultWindowHints();GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window=GLFW.glfwCreateWindow(W,H,"scope orientation audit",0,0);if(window==0)throw new IllegalStateException("hidden GPU window");
        GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();VerifyScopeComposition.initShader();
        var target=new TextureTarget(W,H,true,false);int scene=0,white=0;
        try {
            var sceneImage=new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);
            // texture() uploads source row zero to GL V=0, the bottom of the intended image.
            for(int y=0;y<256;y++)for(int x=0;x<256;x++)sceneImage.setRGB(x,y,0xFF000000|(y<128?(x<128?BLUE:YELLOW):(x<128?RED:GREEN)));
            scene=VerifyScopeComposition.texture(sceneImage);var w=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);w.setRGB(0,0,0xFFFFFFFF);white=VerifyScopeComposition.texture(w);
            RenderSystem.activeTexture(GL13.GL_TEXTURE1);RenderSystem.bindTexture(white);RenderSystem.activeTexture(GL13.GL_TEXTURE0);target.bindWrite(true);
            for(String name:List.of("PSO1","NF7x","SeecterDR1x4x","ELCANSpecterDR1x4x","G33Enabled","G33")){
                var type=AttachmentType.getAttachment(name);if(type==null)throw new IllegalStateException("Required optic missing "+name);
                for(boolean mirrored:new boolean[]{false,true})sample(type,scene,mirrored);
            }
        }finally{
            target.destroyBuffers();GL11.glDeleteTextures(scene);GL11.glDeleteTextures(white);
            GL15.glDeleteBuffers(VerifyScopeComposition.vbo);GL30.glDeleteVertexArrays(VerifyScopeComposition.vao);GL20.glDeleteProgram(VerifyScopeComposition.program);
            GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();
        }
        System.out.println("VerifyScopeOrientation: "+cases+" real-mesh cases, "+checks+" checks, "+failures+" failures; independent quadrants, CULL and depth writes, no game.");
        if(failures!=0)throw new AssertionError("Scope orientation failures: "+failures);
    }
}
