"""Render the actual Kotlin-exported XYZ/normal/RGB buffers with a CPU z-buffer.
Not an Android/GLES screenshot. No user photos, network, external assets, or inferred performance.
Usage: python tools/render_avatar_review.py fashion_app/app/build/p9-visual
Requires numpy and Pillow in the development/test environment only.
"""
from pathlib import Path
import argparse
import numpy as np
from PIL import Image, ImageDraw, ImageFont


def render(data, yaw, size=(320, 600), legacy=False, cull_backfaces=False, stage=False, foot_x=.095):
    vertices=np.asarray(data,dtype=np.float64).reshape(-1,9)
    assert len(vertices)%3==0 and np.isfinite(vertices).all()
    angle=np.deg2rad(yaw);c,s=np.cos(angle),np.sin(angle)
    rotation=np.array([[c,0,s],[0,1,0],[-s,0,c]])
    xyz=vertices[:,:3]@rotation.T;normals=vertices[:,3:6]@rotation.T
    w,h=size;distance=3.05
    scale=h/(2*np.tan(np.deg2rad(19)))
    z=distance-xyz[:,2]
    screen=np.column_stack((w/2+xyz[:,0]*scale/z,h/2-xyz[:,1]*scale/z))
    canvas=np.full((h,w,3),[250,248,245],dtype=np.uint8);depth=np.full((h,w),np.inf)
    if stage:
        # Same analytic floor/contact-shadow idea as GLES; not a real light-space shadow map.
        xx,yy=np.meshgrid(np.arange(w)+.5,np.arange(h)+.5)
        dy=-(yy-h/2)/scale
        ground=float(vertices[:,1].min())-.0034
        t=np.divide(ground,dy,out=np.zeros_like(dy),where=dy<-.0001)
        floor_x=t*(xx-w/2)/scale;floor_z=distance-t
        local_x=floor_x*c-floor_z*s;local_z=floor_x*s+floor_z*c
        p_x=local_x/1.7;p_z=local_z/1.7;foot=foot_x/1.7
        contact=np.maximum(np.exp(-1.5*(((p_x-foot)/.035)**2+((p_z-.018)/.068)**2)),
                           np.exp(-1.5*(((p_x+foot)/.035)**2+((p_z-.018)/.068)**2)))
        shade=.17*contact+.045*np.exp(-((p_x/.19)**2+(p_z/.12)**2))
        mask=(dy<-.0001)&(np.abs(local_x)<.816)&(local_z>-.544)&(local_z<.714)
        canvas[mask]=(np.array([250,248,245])*(1-shade[mask,None])).astype(np.uint8)
        depth[mask]=t[mask]
    light=np.array([-.4,.7,1.0]);light/=np.linalg.norm(light)
    fill=np.array([.8,.15,.5]);fill/=np.linalg.norm(fill)
    for start in range(0,len(vertices),3):
        p=screen[start:start+3]
        x0=max(0,int(np.floor(p[:,0].min())));x1=min(w-1,int(np.ceil(p[:,0].max())))
        y0=max(0,int(np.floor(p[:,1].min())));y1=min(h-1,int(np.ceil(p[:,1].max())))
        if x1<x0 or y1<y0:continue
        a,b,c=p;den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
        if abs(den)<1e-8:continue
        if cull_backfaces and den>0:continue
        xx,yy=np.meshgrid(np.arange(x0,x1+1)+.5,np.arange(y0,y1+1)+.5)
        u=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/den
        v=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/den;t=1-u-v
        mask=(u>=-1e-6)&(v>=-1e-6)&(t>=-1e-6)
        distance_pixel=u*z[start]+v*z[start+1]+t*z[start+2]
        mask&=distance_pixel<depth[y0:y1+1,x0:x1+1]
        if not mask.any():continue
        weights=np.stack([u,v,t],axis=-1)
        n=weights@normals[start:start+3];n/=np.maximum(np.linalg.norm(n,axis=-1,keepdims=True),1e-8)
        color=weights@vertices[start:start+3,6:9]
        if legacy:
            lit=.35+.65*np.maximum(n@light,0)
        elif stage:
            lit=.50+.38*np.maximum(n@light,0)+.10*np.maximum(n@fill,0)+.06*(n[...,1]*.5+.5)
        else:
            lit=.48+.43*np.maximum(n@light,0)+.12*np.maximum(n@fill,0)+.06*np.maximum(n[...,1],0)
        rgb=np.clip(color*lit[...,None]*255,0,255).astype(np.uint8)
        canvas[y0:y1+1,x0:x1+1][mask]=rgb[mask]
        depth[y0:y1+1,x0:x1+1][mask]=distance_pixel[mask]
    return Image.fromarray(canvas)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('directory',type=Path);args=p.parse_args()
    folder=args.directory;premium=np.fromfile(folder/'premium.bin',dtype='>f4');old=np.fromfile(folder/'legacy.bin',dtype='>f4')
    panels=[('P8 geometry',old,0,True),('P9 front',premium,0,False),('P9 side',premium,90,False),('P9 back',premium,180,False)]
    canvas=Image.new('RGB',(1280,660),(250,248,245));draw=ImageDraw.Draw(canvas)
    font=ImageFont.load_default(size=18)
    for index,(label,data,yaw,legacy) in enumerate(panels):
        result=render(data,yaw,legacy=legacy);canvas.paste(result,(index*320,40))
        draw.text((index*320+20,12),label,font=font,fill=(139,107,74))
        result.save(folder/f'{"legacy" if legacy else "premium"}-{yaw}.png')
    draw.text((20,638),'Actual Kotlin mesh / CPU review only / S25+ GPU and visual quality remain unverified',font=font,fill=(100,85,65))
    canvas.save(folder/'avatar-comparison.png')
    print(folder/'avatar-comparison.png')


if __name__=='__main__':main()
