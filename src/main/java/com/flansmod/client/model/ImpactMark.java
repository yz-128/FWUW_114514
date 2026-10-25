package com.flansmod.client.model;

import com.flansmod.common.network.BlockImpactPayload;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Small irregular core, chipped rim and cracks, clipped to actual collision-shape faces. */
public final class ImpactMark {
    private ImpactMark() { }
    public static final int LIFETIME=600,FADE_TICKS=100,MAX_MARKS=1024;
    public static final double OFFSET=.0015;
    public record Vertex(double x,double y,double z,int shade,float opacity) { }
    private record Point(double u,double v) { }
    public static float alpha(int age,float partial) {return Math.clamp((LIFETIME-age-partial)/FADE_TICKS,0F,1F);}

    public static List<Vertex> mesh(BlockImpactPayload hit,List<AABB> boxes) {
        if(!hit.valid()||boxes.isEmpty())return List.of();
        Vec3 local=hit.point().subtract(hit.block().getX(),hit.block().getY(),hit.block().getZ());
        var random=new Random(hit.seed());double radius=.048+random.nextDouble()*.018,roll=random.nextDouble()*Math.PI*2;
        var inner=new Point[12];var outer=new Point[12];
        for(int i=0;i<12;i++) {
            double angle=roll+i*Math.PI/6,r=radius*(.67+random.nextDouble()*.33);
            outer[i]=new Point(Math.cos(angle)*r,Math.sin(angle)*r);
            inner[i]=new Point(Math.cos(angle)*r*.48,Math.sin(angle)*r*.48);
        }
        var vertices=new ArrayList<Vertex>();
        for(var box:boxes) {
            if(Math.abs(plane(box,hit.face())-normal(local,hit.face()))>.002)continue;
            double minU=lowU(box,hit.face())-u(local,hit.face()),maxU=highU(box,hit.face())-u(local,hit.face());
            double minV=lowV(box,hit.face())-v(local,hit.face()),maxV=highV(box,hit.face())-v(local,hit.face());
            if(minU>radius||maxU< -radius||minV>radius||maxV< -radius)continue;
            for(int i=0;i<12;i++) {
                int next=(i+1)%12;
                triangle(vertices,local,hit.face(),minU,maxU,minV,maxV,18,.92F,new Point(0,0),inner[i],inner[next]);
                int shade=86+i%3*10;
                triangle(vertices,local,hit.face(),minU,maxU,minV,maxV,shade,.64F,inner[i],outer[i],outer[next]);
                triangle(vertices,local,hit.face(),minU,maxU,minV,maxV,shade,.64F,inner[i],outer[next],inner[next]);
                if(i%3==0)triangle(vertices,local,hit.face(),minU,maxU,minV,maxV,25,.65F,
                        outer[i],new Point(outer[i].u*1.4,outer[i].v*1.4),outer[next]);
            }
        }
        return List.copyOf(vertices);
    }
    private static void triangle(List<Vertex> out,Vec3 center,Direction face,double minU,double maxU,double minV,double maxV,
            int shade,float alpha,Point a,Point b,Point c) {
        List<Point> points=List.of(a,b,c);
        points=clip(points,0,minU,true);points=clip(points,0,maxU,false);
        points=clip(points,1,minV,true);points=clip(points,1,maxV,false);
        for(int i=1;i+1<points.size();i++)for(var p:List.of(points.getFirst(),points.get(i),points.get(i+1))) {
            Vec3 position=switch(face.getAxis()) {
                case X->center.add(0,p.u,p.v);
                case Y->center.add(p.u,0,p.v);
                case Z->center.add(p.u,p.v,0);
            };
            out.add(new Vertex(position.x+face.getStepX()*OFFSET,position.y+face.getStepY()*OFFSET,
                    position.z+face.getStepZ()*OFFSET,shade,alpha));
        }
    }
    private static List<Point> clip(List<Point> input,int axis,double edge,boolean minimum) {
        if(input.isEmpty())return input;var result=new ArrayList<Point>();var previous=input.getLast();
        boolean prior=inside(previous,axis,edge,minimum);
        for(var point:input) {
            boolean current=inside(point,axis,edge,minimum);
            if(prior!=current) {
                double a=axis==0?previous.u:previous.v,b=axis==0?point.u:point.v,t=(edge-a)/(b-a);
                result.add(new Point(previous.u+(point.u-previous.u)*t,previous.v+(point.v-previous.v)*t));
            }
            if(current)result.add(point);previous=point;prior=current;
        }
        return result;
    }
    private static boolean inside(Point p,int axis,double edge,boolean minimum){double v=axis==0?p.u:p.v;return minimum?v>=edge:v<=edge;}
    private static double normal(Vec3 p,Direction d){return switch(d.getAxis()){case X->p.x;case Y->p.y;case Z->p.z;};}
    private static double u(Vec3 p,Direction d){return d.getAxis()==Direction.Axis.X?p.y:p.x;}
    private static double v(Vec3 p,Direction d){return d.getAxis()==Direction.Axis.Z?p.y:p.z;}
    private static double lowU(AABB b,Direction d){return d.getAxis()==Direction.Axis.X?b.minY:b.minX;}
    private static double highU(AABB b,Direction d){return d.getAxis()==Direction.Axis.X?b.maxY:b.maxX;}
    private static double lowV(AABB b,Direction d){return d.getAxis()==Direction.Axis.Z?b.minY:b.minZ;}
    private static double highV(AABB b,Direction d){return d.getAxis()==Direction.Axis.Z?b.maxY:b.maxZ;}
    private static double plane(AABB b,Direction d){return switch(d){case EAST->b.maxX;case WEST->b.minX;case UP->b.maxY;case DOWN->b.minY;case SOUTH->b.maxZ;case NORTH->b.minZ;};}
}
