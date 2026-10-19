import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.flansmod.common.types.*;
import org.joml.Matrix4f;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Font;
import javax.imageio.ImageIO;
public class RenderSightFoldGeometry {
 public static void main(String[] args)throws Exception {
  var pack=new OfflinePack(Path.of(args[0]));pack.readTypes();var out=Path.of(args[1]);Files.createDirectories(out);
  String[][] names={{"G33Enabled","G33Disabled"},{"MP7Frontsight","MP7FrontsightFolded"},{"MP7Rearsight","MP7RearsightFolded"},{"QBZ191FrontSight","QBZ191FrontSightFolded"},{"QBZ191RearSight","QBZ191RearSightFolded"}};
  var sheet=new BufferedImage(1500,1600,BufferedImage.TYPE_INT_RGB);var g=sheet.createGraphics();g.setColor(new Color(18,25,35));g.fillRect(0,0,1500,1600);g.setFont(new Font("SansSerif",0,17));
  for(int row=0;row<names.length;row++) {
   var from=AttachmentType.getAttachment(names[row][0]);var to=AttachmentType.getAttachment(names[row][1]);var a=ModelCache.rigFor(from,from.modelScale);var b=ModelCache.rigFor(to,to.modelScale);
   float x0=Math.min(a.minX,b.minX),x1=Math.max(a.maxX,b.maxX),y0=Math.min(a.minY,b.minY),y1=Math.max(a.maxY,b.maxY),z0=Math.min(a.minZ,b.minZ),z1=Math.max(a.maxZ,b.maxZ);
   float scale=.8F/Math.max(x1-x0,Math.max(y1-y0,z1-z0));var base=new Matrix4f().translate(0,0,-1.1F).rotateX(.2F).rotateY(-.6F).scale(scale).translate(-(x0+x1)/2,-(y0+y1)/2,-(z0+z1)/2);
   int column=0;for(float p:new float[]{0,.5F,1}) {
    var parts=new ArrayList<FPPreview.Part>();for(var t:SightFoldGeometry.plan(a,b).tracks()) {
     if(!t.source().isEmpty()) {var mesh=new BakedRig();mesh.quads.addAll(t.source());parts.add(new FPPreview.Part(new Matrix4f(base).mul(t.pose(p)),mesh,null,t.fixed()?0xFFADB7CA:0xFF4DDAC8,t.sourceAlpha(p),false,false));}
     if(!t.matched()&&!t.target().isEmpty()){var mesh=new BakedRig();mesh.quads.addAll(t.target());parts.add(new FPPreview.Part(base,mesh,null,0xFFF5B948,p,false,false));}
    }
    var file=out.resolve("geometry-"+row+"-"+column+".png");FPPreview.render(parts,file.toFile());var im=ImageIO.read(file.toFile());g.drawImage(im,column*500,row*320+32,500,281,null);g.setColor(Color.WHITE);g.drawString(names[row][0]+"  "+(int)(p*100)+"%",column*500+10,row*320+24);column++;
   }
  }g.dispose();ImageIO.write(sheet,"png",out.resolve("geometry-contact-sheet.png").toFile());
 }
}
