import com.flansmod.common.*;
import com.flansmod.common.types.*;
import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.mojang.blaze3d.vertex.*;
import java.nio.file.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.*;
import java.util.List;

/** Exact worn meshes with original skins; generates only missing bundled inventory icons. */
public final class RenderAllArmourIcons {
    static class Sink implements VertexConsumer {
        final List<Verify556Series.Vertex> points=new ArrayList<>();
        public VertexConsumer addVertex(float x,float y,float z){points.add(new Verify556Series.Vertex(x,-y,-z));return this;}
        public VertexConsumer setUv(float u,float v){var p=points.getLast();p.u=u;p.v=v;return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        public VertexConsumer setUv1(int u,int v){return this;}public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setNormal(float x,float y,float z){return this;}
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();AuditAllArmour.load(Path.of(args[0]));
        Render556RepairPreview.CAMERA.identity().rotateX(-.12F).rotateY(-.45F);
        var types=ArmourType.armours.stream().filter(a->a.item!=null).toList();int columns=9,cardWidth=160,cardHeight=176;
        var sheet=new BufferedImage(columns*cardWidth,60+((types.size()+columns-1)/columns)*cardHeight,BufferedImage.TYPE_INT_RGB);
        var g=sheet.createGraphics();g.setColor(new Color(12,21,29));g.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
        g.setFont(new Font("SansSerif",Font.BOLD,19));g.setColor(Color.WHITE);g.drawString("All registered armour / original worn meshes / CPU preview",18,29);
        int index=0,generated=0;
        for(var a:types){
            var source=ArmourResources.source(a).orElseThrow();
            var skin=ImageIO.read(new java.io.ByteArrayInputStream(source.entry().readAllBytes()));
            var geometry=ModelCache.load(a);var uv=ModelCache.uvScaleForDimensions(geometry,skin.getWidth(),skin.getHeight());
            var rig=ArmourGeometry.bake(geometry,a.modelScale,uv[0],uv[1]);var sink=new Sink();var pose=VerifyArmourRendering.body();pose.young=false;
            new LegacyArmourModel(rig,pose).renderToBuffer(new PoseStack(),sink,0xF000F0,0,-1);
            var faces=new ArrayList<Render556RepairPreview.Face>();Render556RepairPreview.append(faces,sink.points,skin);
            var bounds=Render556RepairPreview.bounds(faces);int x=(index%columns)*cardWidth,y=60+(index/columns)*cardHeight;
            g.setColor(new Color(25,39,50));g.fillRect(x+4,y+4,152,168);Render556RepairPreview.draw(g,faces,x+14,y+10,132,132,bounds);
            g.setFont(new Font("SansSerif",Font.PLAIN,10));g.setColor(new Color(186,207,211));g.drawString(a.shortName.substring(0,Math.min(24,a.shortName.length())),x+9,y+157);
            String icon=a.iconPath==null?a.shortName:a.iconPath;
            var iconId=net.minecraft.resources.ResourceLocation.parse("flansmod:textures/"+(icon.contains("/")?icon:"items/"+icon).toLowerCase(Locale.ROOT)+".png");
            if(args.length>2&&!AuditAllArmour.has(iconId)){
                var image=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);var ig=image.createGraphics();Render556RepairPreview.draw(ig,faces,3,3,58,58,bounds);ig.dispose();
                var output=Path.of(args[2]).resolve("assets/flansmod/"+iconId.getPath());Files.createDirectories(output.getParent());ImageIO.write(image,"PNG",output.toFile());generated++;
            }
            index++;
        }
        g.dispose();var output=Path.of(args[1]);Files.createDirectories(output.toAbsolutePath().getParent());ImageIO.write(sheet,"PNG",output.toFile());
        System.out.println("Rendered "+types.size()+" registered armours; "+generated+" missing icons generated from original models. No pack files written.");
    }
}
