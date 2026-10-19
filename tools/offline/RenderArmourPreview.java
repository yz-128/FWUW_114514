import com.flansmod.client.model.loader.BakedRig;
import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.client.render.LegacyArmourModel;
import com.flansmod.client.render.Textures;
import com.flansmod.common.types.ArmourType;
import com.flansmod.common.types.InfoType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** CPU rasterisation of production armour output, with real skins and animated humanoid joints.
 * No game, window, world, GL context or shader is started. The sheet explicitly says offline. */
public final class RenderArmourPreview {
    private static final int WIDTH=1920,HEIGHT=1600,LEFT=24,TOP=128,CELL_W=468,CELL_H=670;
    private static final Font FONT=new Font("Microsoft YaHei UI",Font.PLAIN,20);
    private static final Color TEXT=new Color(226,234,244),MUTED=new Color(153,173,194),ACCENT=new Color(106,225,198);
    private static final Vector3f LIGHT=new Vector3f(-.4F,-.6F,-.8F).normalize();
    private static final List<Outfit> EQUIPMENT=new ArrayList<>();
    private record Outfit(ArmourType type,BakedRig rig,BufferedImage texture,ResourceLocation skin) { }
    private record Mesh(List<Vertex> vertices,BufferedImage texture,String name) { }
    private record Panel(String title,String subtitle,float yaw,boolean walking,boolean equipped) { }
    private static final class Vertex {
        float x,y,z,u,v,nx,ny,nz;int color=0xFFFFFFFF;
        Vertex(float x,float y,float z){this.x=x;this.y=y;this.z=z;}
    }
    private static final class Recorder implements VertexConsumer {
        final List<Vertex> vertices=new ArrayList<>();private Vertex current;
        public VertexConsumer addVertex(float x,float y,float z){current=new Vertex(x,y,z);vertices.add(current);return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){current.color=(a<<24)|(r<<16)|(g<<8)|b;return this;}
        public VertexConsumer setUv(float u,float v){current.u=u;current.v=v;return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setNormal(float x,float y,float z){current.nx=x;current.ny=y;current.nz=z;return this;}
    }
    private static HumanoidModel<?> body(boolean walking){
        var pose=new HumanoidModel<>(HumanoidModel.createMesh(CubeDeformation.NONE,0).getRoot().bake(64,32));
        pose.young=false;pose.hat.visible=false;
        pose.leftArm.zRot=-.045F;pose.rightArm.zRot=.045F;
        if(walking){
            pose.leftArm.xRot=-.68F;pose.rightArm.xRot=.68F;
            pose.leftLeg.xRot=.68F;pose.rightLeg.xRot=-.68F;
            pose.head.yRot=-.25F;pose.head.xRot=-.08F;
        }
        return pose;
    }
    private static List<Mesh> mesh(Panel panel){
        var pose=body(panel.walking);var result=new ArrayList<Mesh>();
        var base=new Recorder();pose.renderToBuffer(new PoseStack(),base,15728880,0,0xFF798594);
        result.add(new Mesh(base.vertices,null,"vanilla humanoid reference"));
        if(panel.equipped)for(var outfit:EQUIPMENT){
            var output=new Recorder();new LegacyArmourModel(outfit.rig,pose).renderToBuffer(new PoseStack(),output,15728880,0,-1);
            if(output.vertices.isEmpty()||output.vertices.size()%4!=0)throw new AssertionError("Incomplete armour output "+outfit.type.shortName);
            result.add(new Mesh(output.vertices,outfit.texture,outfit.type.shortName));
        }
        return result;
    }
    private static Matrix4f rotation(float degrees){return new Matrix4f().rotateX(.035F).rotateY((float)Math.toRadians(degrees));}
    private static List<Vector3f> transformed(Mesh mesh,Matrix4f camera){
        var result=new ArrayList<Vector3f>(mesh.vertices.size());
        for(var vertex:mesh.vertices){
            var point=camera.transformPosition(new Vector3f(vertex.x,vertex.y,vertex.z));
            if(!point.isFinite())throw new AssertionError("Non-finite production vertex: "+mesh.name);
            result.add(point);
        }
        return result;
    }
    private static BufferedImage render(Panel panel,List<Mesh> meshes,float scale){
        int w=CELL_W-20,h=CELL_H-104;var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        var graphics=image.createGraphics();graphics.setPaint(new GradientPaint(0,0,new Color(37,50,66),0,h,new Color(24,34,47)));graphics.fillRect(0,0,w,h);
        graphics.setColor(new Color(52,66,83));
        for(int x=14;x<w;x+=32)graphics.drawLine(x,0,x,h);
        for(int y=15;y<h;y+=32)graphics.drawLine(0,y,w,y);
        int ground=(int)(h/2F+(1.5F-.5F)*scale);graphics.setColor(new Color(90,111,133));graphics.drawLine(0,ground,w,ground);graphics.dispose();
        float[] depth=new float[w*h];Arrays.fill(depth,-Float.MAX_VALUE);var camera=rotation(panel.yaw);
        for(var mesh:meshes){
            var vertices=transformed(mesh,camera);
            for(int i=0;i<vertices.size();i+=4){
                Vector3f[] p=new Vector3f[4];float[][] uv=new float[4][2];
                for(int k=0;k<4;k++){
                    var value=vertices.get(i+k);p[k]=new Vector3f(w/2F+value.x*scale,h/2F+(value.y-.5F)*scale,-value.z);
                    if(p[k].x<2||p[k].x>w-2||p[k].y<2||p[k].y>h-2)
                        throw new AssertionError("Preview framing clips actual output: "+mesh.name+" in "+panel.title+" at "+p[k]);
                    var source=mesh.vertices.get(i+k);uv[k][0]=source.u;uv[k][1]=source.v;
                }
                var source=mesh.vertices.get(i);var normal=camera.transformDirection(new Vector3f(source.nx,source.ny,source.nz));
                float shade=.54F+.46F*Math.abs(normal.normalize().dot(LIGHT));
                triangle(image,depth,p[0],p[1],p[2],uv[0],uv[1],uv[2],mesh.texture,source.color,shade);
                triangle(image,depth,p[0],p[2],p[3],uv[0],uv[2],uv[3],mesh.texture,source.color,shade);
            }
        }
        return image;
    }
    private static float edge(Vector3f a,Vector3f b,float x,float y){return (x-a.x)*(b.y-a.y)-(y-a.y)*(b.x-a.x);}
    private static void triangle(BufferedImage target,float[] depth,Vector3f a,Vector3f b,Vector3f c,float[] ua,float[] ub,float[] uc,BufferedImage tex,int color,float shade){
        int w=target.getWidth(),h=target.getHeight();float area=edge(a,b,c.x,c.y);if(Math.abs(area)<1E-5F)return;
        int left=Math.max(0,(int)Math.floor(Math.min(a.x,Math.min(b.x,c.x)))),right=Math.min(w-1,(int)Math.ceil(Math.max(a.x,Math.max(b.x,c.x))));
        int top=Math.max(0,(int)Math.floor(Math.min(a.y,Math.min(b.y,c.y)))),bottom=Math.min(h-1,(int)Math.ceil(Math.max(a.y,Math.max(b.y,c.y))));
        for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++){
            float wa=edge(b,c,x+.5F,y+.5F)/area,wb=edge(c,a,x+.5F,y+.5F)/area,wc=1-wa-wb;
            if(wa<0||wb<0||wc<0)continue;float z=wa*a.z+wb*b.z+wc*c.z;if(z<=depth[y*w+x])continue;
            float u=wa*ua[0]+wb*ub[0]+wc*uc[0],v=wa*ua[1]+wb*ub[1]+wc*uc[1];
            int sampled=tex==null?0xFFFFFFFF:tex.getRGB(Math.max(0,Math.min(tex.getWidth()-1,(int)(u*tex.getWidth()))),Math.max(0,Math.min(tex.getHeight()-1,(int)(v*tex.getHeight()))));
            if((sampled>>>24)<100||(color>>>24)<100)continue;
            int r=Math.min(255,(int)(((sampled>>>16)&255)*((color>>>16)&255)/255F*shade));
            int g=Math.min(255,(int)(((sampled>>>8)&255)*((color>>>8)&255)/255F*shade));
            int blue=Math.min(255,(int)((sampled&255)*(color&255)/255F*shade));
            target.setRGB(x,y,0xFF000000|(r<<16)|(g<<8)|blue);depth[y*w+x]=z;
        }
    }
    private static void text(Graphics2D g,String text,int x,int y,int size,Color color){g.setFont(FONT.deriveFont((float)size));g.setColor(color);g.drawString(text,x,y);}
    public static void main(String[] args)throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();
        Path base=Path.of(args.length>0?args[0]:"run/flan/[FMUM]EscapeFromTarCraft-Armor");
        Path scav=Path.of(args.length>1?args[1]:"run/flan/[FMUM]EscapeFromTarCraft-Armor(Scav)");
        Path out=Path.of(args.length>2?args[2]:"reports/eft/armour-mounts-0.4.6/armour-preview.png");
        var main=new OfflinePack(base);main.readTypes();new OfflinePack(scav).readTypes();
        for(String name:List.of("adidasCloth","6B43","ComTac2HeadsetFDE","FastMT")){
            var armour=(ArmourType)main.types.stream().filter(t->t instanceof ArmourType&&name.equals(t.shortName)).findFirst().orElseThrow();
            var skin=Textures.forArmour(armour,main::has);if(skin==null||main.path(skin)==null)throw new AssertionError("Actual skin missing: "+name);
            var rig=ModelCache.rigForArmour(armour,skin);if(rig==null||rig.quads.isEmpty())throw new AssertionError("Actual geometry missing: "+name);
            var texture=ImageIO.read(main.path(skin).toFile());if(texture==null)throw new AssertionError("Unreadable original skin: "+skin);
            EQUIPMENT.add(new Outfit(armour,rig,texture,skin));
            System.out.printf(Locale.ROOT,"%s slot=%d modelScale=%.3f skin=%s pixels=%dx%d quads=%d groups=%s rawBakedBounds=(%.4f %.4f %.4f)-(%.4f %.4f %.4f)%n",name,armour.type,armour.modelScale,skin,texture.getWidth(),texture.getHeight(),rig.quads.size(),rig.quadsByPart.keySet(),rig.minX,rig.minY,rig.minZ,rig.maxX,rig.maxY,rig.maxZ);
        }
        var panels=List.of(
            new Panel("灰色人体参考","站姿 · 无护甲 · 正面",0,false,false),new Panel("穿戴 · 正面","FastMT / 6B43 / ComTac2 / adidas",0,false,true),
            new Panel("穿戴 · 45°","站姿 · 同一比例 / 同一关节",45,false,true),new Panel("穿戴 · 侧面","站姿 · 同一比例 / 同一关节",90,false,true),
            new Panel("灰色人体参考","摆臂迈步 · 头部向左转",0,true,false),new Panel("穿戴 · 正面","迈步 · 手臂与腿相向摆动",0,true,true),
            new Panel("穿戴 · 45°","迈步 · 耳机随头部转动",45,true,true),new Panel("穿戴 · 侧面","迈步 · 衣物随四肢关节",90,true,true));
        var all=new ArrayList<List<Mesh>>();for(var panel:panels)all.add(mesh(panel));
        // Fixed whole-person frame: never normalise individual armour pieces, which could hide a bad scale.
        float scale=212F;
        var sheet=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_RGB);var g=sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setColor(new Color(13,21,32));g.fillRect(0,0,WIDTH,HEIGHT);
        text(g,"护甲穿戴 / 生产渲染输出",28,44,30,TEXT);text(g,"离线 CPU 预览 · 原始网格与贴图 · 非游戏截图",28,80,21,ACCENT);
        text(g,"FastMT：头槽     6B43：胸槽     ComTac2：原腿槽 / 绑定头部     adidasCloth：原靴槽 / 覆盖服装",28,111,18,MUTED);
        for(int i=0;i<panels.size();i++){
            int x=LEFT+(i%4)*CELL_W,y=TOP+(i/4)*CELL_H;
            g.setColor(new Color(27,39,54));g.fillRoundRect(x,y,CELL_W-12,CELL_H-14,10,10);
            text(g,panels.get(i).title,x+14,y+31,22,TEXT);text(g,panels.get(i).subtitle,x+14,y+60,15,MUTED);
            g.drawImage(render(panels.get(i),all.get(i),scale),x+4,y+80,null);
        }
        text(g,"所有人物共用固定比例；灰色为原生 HumanoidModel。护甲顶点 / UV 直接取自 LegacyArmourModel.renderToBuffer。",28,1513,18,MUTED);
        text(g,"用于检查绑定、尺寸、朝向与遮挡；本图不验证游戏光照、动画时序或最终着色器。",28,1550,18,MUTED);
        g.dispose();Files.createDirectories(out.toAbsolutePath().getParent());ImageIO.write(sheet,"png",out.toFile());
        System.out.println("OFFLINE PRODUCTION-OUTPUT PREVIEW -> "+out.toAbsolutePath());
    }
}
