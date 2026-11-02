"""Matched P9/P10 actual Kotlin mesh review; synthetic bodies, never an Android/GPU capture.
Each profile has the same camera/light/material on both versions, normalized to the same height.
Requires development-only numpy/Pillow. No external assets/network.
"""
import argparse
import json
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from render_avatar_review import render


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory',type=Path)
    parser.add_argument('--only',help='Comma-separated synthetic profile IDs for a quick local review')
    args=parser.parse_args();folder=args.directory
    profiles=json.loads((folder/'profiles.json').read_text(encoding='utf-8'))
    if args.only:
        profiles=[p for p in profiles if p['id'] in args.only.split(',')]
        if not profiles:raise ValueError('No matching synthetic profile')
    font=ImageFont.load_default(size=17)
    previews=[]
    for p in profiles:
        panel=Image.new('RGB',(1320,570),(250,248,245));draw=ImageDraw.Draw(panel)
        draw.text((15,7),f"{p['id']}: {p['heightCm']}cm / {p['weightKg']}kg - synthetic illustrative shape",fill=(100,78,55),font=font)
        for version in ['p9','p10']:
            data=np.fromfile(folder/f"{p['id']}-{version}.bin",dtype='>f4').astype(np.float64).reshape(-1,9)
            data[:,:3]*=170/p['heightCm']
            # Use identical review material so silhouette differences cannot be attributed to recoloring.
            data[:,6:9]=[.91,.89,.84]
            for view,angle in enumerate([0,90,180]):
                index=(0 if version=='p9' else 3)+view
                result=render(data,angle,size=(220,490),cull_backfaces=True)
                panel.paste(result,(220*index,52))
                draw.text((220*index+15,32),f"{version.upper()} {['front','side','back'][view]}",fill=(139,107,74),font=font)
                if view==0:previews.append((p['id'],version,result.resize((180,400))))
        draw.text((15,548),'Actual mesh / matched camera+light / CPU only; device appearance & real body accuracy unverified',fill=(100,78,55),font=font)
        panel.save(folder/f"comparison-{p['id']}.png")
        if p['id']=='standard':
            current=np.fromfile(folder/'standard-p10.bin',dtype='>f4').astype(np.float64).reshape(-1,9)
            current[:,:3]*=170/p['heightCm']
            hip=p['body']['values']['HIP']['cm']
            thigh=p['body']['values']['THIGH']['cm']
            # Radii use the unchanged P9 cm ellipse contract; this is for floor contact only.
            def radius(cm,ratio):
                t=(1-ratio)**2/(1+ratio)**2
                return cm/(np.pi*(1+ratio)*(1+3*t/(10+np.sqrt(4-3*t))))
            foot=max(radius(hip,.76)/170*.50,radius(thigh,.92)/170+.007)*.94*1.7
            render(current,30,size=(420,700),cull_backfaces=True,stage=True,foot_x=foot).save(folder/'p10-ivory-stage.png')
    sheet=Image.new('RGB',(len(profiles)*180,840),(250,248,245));draw=ImageDraw.Draw(sheet)
    for index,p in enumerate(profiles):
        draw.text((index*180+8,4),p['id'],fill=(100,78,55),font=font)
        for name,version,im in previews:
            if name==p['id']:sheet.paste(im,(index*180,30 if version=='p9' else 430))
    draw.text((8,30),'P9',fill=(139,107,74),font=font);draw.text((8,430),'P10',fill=(139,107,74),font=font)
    sheet.save(folder/'overview-front.png')
    print(f"Rendered {len(profiles)} matched front/side/back comparisons in {folder}")


if __name__=='__main__':main()
