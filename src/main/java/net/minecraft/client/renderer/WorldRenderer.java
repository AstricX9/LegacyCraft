package net.minecraft.client.renderer;

import net.minecraft.block.Block;
import net.minecraft.client.entity.Player;
import net.minecraft.client.renderer.block.model.BakedModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelManager;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.util.Direction;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

public final class WorldRenderer {
    public record Hit(int x,int y,int z,int placeX,int placeY,int placeZ) {}
    private static final int CHUNK = Chunk.SIZE;
    private final Map<Long,RenderChunk> renderChunks = new HashMap<>();
    private final World world;
    private final TextureAtlas atlas;
    private final BlockModelManager models;

    public WorldRenderer(World world, TextureAtlas atlas, BlockModelManager models) {
        this.world=world; this.atlas=atlas; this.models=models;
    }

    public void render(Player player,int renderDistance) {
        atlas.bind(); glEnable(GL_TEXTURE_2D); glEnable(GL_ALPHA_TEST); glAlphaFunc(GL_GREATER,.1f);
        int pcx=World.floorDiv((int)Math.floor(player.x),CHUNK);
        int pcz=World.floorDiv((int)Math.floor(player.z),CHUNK);
        int radius=(renderDistance+CHUNK-1)/CHUNK;
        for(int cz=pcz-radius;cz<=pcz+radius;cz++) for(int cx=pcx-radius;cx<=pcx+radius;cx++){
            double dx=cx*CHUNK+8-player.x,dz=cz*CHUNK+8-player.z;
            if(dx*dx+dz*dz>(renderDistance+14)*(renderDistance+14))continue;
            long key=World.chunkKey(cx,cz);
            RenderChunk renderChunk=renderChunks.get(key);
            if(renderChunk==null){
                renderChunk=new RenderChunk(cx,cz);
                renderChunks.put(key,renderChunk);
            }
            if(renderChunk.dirty) rebuild(renderChunk);
            glCallList(renderChunk.list);
        }
        unloadFarMeshes(pcx,pcz,radius+2);
        glDisable(GL_ALPHA_TEST); glColor4f(1,1,1,1);
    }

    private void unloadFarMeshes(int centerX,int centerZ,int limit){
        ArrayList<Long> remove=new ArrayList<>();
        for(var entry:renderChunks.entrySet()){
            RenderChunk chunk=entry.getValue();
            if(Math.max(Math.abs(chunk.x-centerX),Math.abs(chunk.z-centerZ))>limit){
                if(chunk.list!=0)glDeleteLists(chunk.list,1);
                remove.add(entry.getKey());
            }
        }
        for(long key:remove)renderChunks.remove(key);
    }

    private void rebuild(RenderChunk renderChunk){
        world.getChunk(renderChunk.x,renderChunk.z);
        if(renderChunk.list==0)renderChunk.list=glGenLists(1);
        glNewList(renderChunk.list,GL_COMPILE); glBegin(GL_QUADS);
        int startX=renderChunk.x*CHUNK,startZ=renderChunk.z*CHUNK;
        for(int y=0;y<World.HEIGHT;y++) for(int z=startZ;z<startZ+CHUNK;z++) for(int x=startX;x<startX+CHUNK;x++){
            Block block=world.get(x,y,z);
            if(block.isAir())continue;
            BakedModel model=models.getModel(block);
            if(model==null)continue;
            for(BakedQuad quad:model.quads()){
                if(quad.cullFace()!=null){
                    Direction cull=quad.cullFace();
                    Block neighbor=world.get(x+cull.xOffset,y+cull.yOffset,z+cull.zOffset);
                    if(!faceVisible(block,neighbor))continue;
                }
                drawQuad(x,y,z,block,quad);
            }
        }
        glEnd();glEndList();renderChunk.dirty=false;
    }

    private boolean faceVisible(Block block,Block neighbor){
        if(neighbor.isAir())return true;
        if(neighbor==block)return false;
        return !neighbor.isOpaque();
    }

    private void drawQuad(float x,float y,float z,Block block,BakedQuad quad){
        TextureAtlas.Tile tile=atlas.tile(quad.texture());
        Direction face=quad.face();
        float shade=face==Direction.UP?1f:face==Direction.DOWN?.5f:
            (face==Direction.NORTH||face==Direction.SOUTH)?.8f:.65f;
        int tint=quad.tintIndex()>=0||block.material().equals("water")?block.tintColor():0xffffff;
        float red=((tint>>16)&255)/255f*shade,green=((tint>>8)&255)/255f*shade,blue=(tint&255)/255f*shade;
        float alpha=block.material().equals("water")?.58f:1f;
        glColor4f(red,green,blue,alpha);
        float fx=quad.fromX(),fy=quad.fromY(),fz=quad.fromZ(),tx=quad.toX(),ty=quad.toY(),tz=quad.toZ();
        float[][] vertices=switch(face){
            case UP->new float[][]{{fx,ty,fz},{fx,ty,tz},{tx,ty,tz},{tx,ty,fz}};
            case DOWN->new float[][]{{fx,fy,tz},{fx,fy,fz},{tx,fy,fz},{tx,fy,tz}};
            case NORTH->new float[][]{{tx,fy,fz},{fx,fy,fz},{fx,ty,fz},{tx,ty,fz}};
            case SOUTH->new float[][]{{fx,fy,tz},{tx,fy,tz},{tx,ty,tz},{fx,ty,tz}};
            case WEST->new float[][]{{fx,fy,fz},{fx,fy,tz},{fx,ty,tz},{fx,ty,fz}};
            case EAST->new float[][]{{tx,fy,tz},{tx,fy,fz},{tx,ty,fz},{tx,ty,tz}};
        };
        float u0=mix(tile.u0(),tile.u1(),quad.u0()),u1=mix(tile.u0(),tile.u1(),quad.u1());
        float v0=mix(tile.v0(),tile.v1(),quad.v0()),v1=mix(tile.v0(),tile.v1(),quad.v1());
        float[][] uv={{u0,v1},{u1,v1},{u1,v0},{u0,v0}};
        for(int i=0;i<4;i++){
            float[] transformed=rotate(vertices[i],quad.rotationX(),quad.rotationY());
            glTexCoord2f(uv[i][0],uv[i][1]);glVertex3f(x+transformed[0],y+transformed[1],z+transformed[2]);
        }
    }

    private float[] rotate(float[] point,int xDegrees,int yDegrees){
        double x=point[0]-.5,y=point[1]-.5,z=point[2]-.5;
        if(xDegrees!=0){double r=Math.toRadians(xDegrees),ny=y*Math.cos(r)-z*Math.sin(r),nz=y*Math.sin(r)+z*Math.cos(r);y=ny;z=nz;}
        if(yDegrees!=0){double r=Math.toRadians(yDegrees),nx=x*Math.cos(r)+z*Math.sin(r),nz=-x*Math.sin(r)+z*Math.cos(r);x=nx;z=nz;}
        return new float[]{(float)(x+.5),(float)(y+.5),(float)(z+.5)};
    }
    private static float mix(float a,float b,float t){return a+(b-a)*t;}

    public Hit raycast(Player player,double reach){
        int lastX=floor(player.x),lastY=floor(player.eyeY()),lastZ=floor(player.z);
        for(double distance=0;distance<=reach;distance+=.025){
            int x=floor(player.x+player.dirX()*distance),y=floor(player.eyeY()+player.dirY()*distance),z=floor(player.z+player.dirZ()*distance);
            Block block=world.get(x,y,z);
            if(!block.isAir()&&!block.material().equals("water"))return new Hit(x,y,z,lastX,lastY,lastZ);
            lastX=x;lastY=y;lastZ=z;
        }
        return null;
    }

    public void changed(int x,int z){
        int cx=World.floorDiv(x,CHUNK),cz=World.floorDiv(z,CHUNK),lx=World.floorMod(x,CHUNK),lz=World.floorMod(z,CHUNK);
        mark(cx,cz);if(lx==0)mark(cx-1,cz);if(lx==15)mark(cx+1,cz);if(lz==0)mark(cx,cz-1);if(lz==15)mark(cx,cz+1);
    }
    private void mark(int x,int z){RenderChunk chunk=renderChunks.get(World.chunkKey(x,z));if(chunk!=null)chunk.dirty=true;}
    public void destroy(){for(RenderChunk chunk:renderChunks.values())if(chunk.list!=0)glDeleteLists(chunk.list,1);renderChunks.clear();}
    private static int floor(double value){return(int)Math.floor(value);}
    private static final class RenderChunk{final int x,z;int list;boolean dirty=true;RenderChunk(int x,int z){this.x=x;this.z=z;}}
}
