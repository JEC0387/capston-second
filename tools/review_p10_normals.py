"""Compare actual pre-fix/current P10 buffers under identical light; synthetic CPU evidence only."""
import argparse
import json
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from render_avatar_review import render


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('before',type=Path);p.add_argument('after',type=Path)
    args=p.parse_args()
    def read(folder,name):return np.fromfile(folder/name,dtype='>f4').astype(float).reshape(-1,9)
    old=read(args.before,'standard-p10.bin');new=read(args.after,'standard-p10.bin')
    ref=read(args.after,'standard-field-normal.bin')
    def ordered(data):
        return data[np.lexsort((data[:,2],data[:,1],data[:,0]))]
    old,new,ref=map(ordered,(old,new,ref))
    # Winding can reorder vertices, but a normals-only repair must not move the surface.
    assert old.shape==new.shape==ref.shape
    assert np.array_equal(old[:,:3],new[:,:3]) and np.array_equal(new[:,:3],ref[:,:3])
    profile=json.loads((args.after/'profiles.json').read_text())[0]['body']['values']
    hip=profile['LEG']['cm']/170+.04
    shoulder=hip+profile['TORSO']['cm']/170
    waist=hip+(shoulder-hip)*.4
    chest=min(max(waist+.025,shoulder-.095),shoulder-.060)
    y=new[:,1]/1.7+.5
    mask=(abs(new[:,0]/1.7)<.035)&(y>=hip)&(y<=chest)&(abs(new[:,2]/1.7)>.03)
    unchanged=[]
    for path in sorted(args.before.glob('*-p10.bin')):
        if not (args.after/path.name).is_file():continue
        a=ordered(read(args.before,path.name));b=ordered(read(args.after,path.name))
        assert a.shape==b.shape and np.array_equal(a[:,:3],b[:,:3]),path.name
        unchanged.append(path.stem)
    result={'positionsExactlyUnchanged':True,'unchangedProfiles':unchanged,'torsoSamples':int(mask.sum())}
    for name,data in [('before',old),('after',new)]:
        angles=np.rad2deg(np.arccos(np.clip(np.sum(data[mask,3:6]*ref[mask,3:6],axis=1),-1,1)))
        result[name]={'meanDegrees':float(angles.mean()),'p95Degrees':float(np.percentile(angles,95)),'maxDegrees':float(angles.max())}
    (args.after/'normal-before-after.json').write_text(json.dumps(result,indent=2)+'\n')
    # Render original triangle ordering, never the position-sorted diagnostic arrays.
    panel=Image.new('RGB',(1440,700),(250,248,245));draw=ImageDraw.Draw(panel)
    font=ImageFont.load_default(size=18)
    for row,(label,folder,name) in enumerate([
        ('Before',args.before,'standard-p10.bin'),('Corrected',args.after,'standard-p10.bin'),
        ('Field reference',args.after,'standard-field-normal.bin')]):
        mesh=read(folder,name)
        for view,angle in enumerate([0,180]):
            x=(row*2+view)*240
            panel.paste(render(mesh,angle,size=(240,620),cull_backfaces=True),(x,48))
            draw.text((x+6,15),f'{label} {angle}',font=font,fill=(100,78,55))
    draw.text((10,678),'Same positions, camera, light and material. CPU only; no blur or lighting reduction.',font=font,fill=(100,78,55))
    panel.save(args.after/'normal-before-after.png')
    print(json.dumps(result))


if __name__=='__main__':main()
