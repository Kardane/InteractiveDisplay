#!/usr/bin/env python3
"""Regression-check a real Java 1.21.8 terrain region without modifying it.

Usage:
  python tests/real_region_regression.py /path/to/r.0.0.mca
"""
import argparse, importlib.util, json, shutil, struct, subprocess, sys, tempfile, time
from pathlib import Path

HERE=Path(__file__).resolve().parent
TOOL=HERE.parent/'scripts'/'mcworld_nbt.py'
spec=importlib.util.spec_from_file_location('mw',TOOL)
mw=importlib.util.module_from_spec(spec); sys.modules['mw']=mw; spec.loader.exec_module(mw)

def run(*args, ok=True):
    p=subprocess.run([sys.executable,str(TOOL),*map(str,args)],text=True,capture_output=True)
    if ok and p.returncode:
        raise RuntimeError(f"{args}\nSTDOUT:{p.stdout}\nSTDERR:{p.stderr}")
    return p

def main():
    ap=argparse.ArgumentParser(); ap.add_argument('region'); a=ap.parse_args(); reg=Path(a.region)
    t=time.time(); pf=mw.validate_region_for_write(reg,deep=True); deep_s=time.time()-t
    rx,rz=mw.parse_region_coords(reg); offsets,_=mw.read_region_header(reg)
    rt_bad=0; raw_bytes=0
    observed={}; boundary=None
    for idx,e in enumerate(offsets):
        if not e: continue
        cx,cz=mw.local_to_global(rx,rz,idx); rec=mw.read_chunk_record(reg,cx,cz); root=mw.read_nbt(rec.nbt_raw)
        raw_bytes += len(rec.nbt_raw)
        if mw.write_nbt(root)!=rec.nbt_raw: rt_bad+=1
        if root.tag.type_id!=10 or 'sections' not in root.tag.value: continue
        for sec in root.tag.value['sections'].value.items:
            try: _bs,pal,data=mw._block_states_parts(sec)
            except Exception: continue
            sts=[mw._state_from_palette_tag(x) for x in pal]
            for st in sts: observed[mw._canonical_state(st)]=st
            if boundary is None and len(sts)==16:
                boundary=(cx,cz,int(sec.value['Y'].value),sts,data)
    if rt_bad: raise RuntimeError(f'NBT byte-exact roundtrip mismatches: {rt_bad}')
    result={'sha256':mw.sha256_file(reg),'chunks':len(pf.allocations),'deep_preflight_seconds':round(deep_s,3),'raw_nbt_bytes':raw_bytes,'nbt_roundtrip_mismatches':rt_bad}
    with tempfile.TemporaryDirectory() as td:
        d=Path(td)
        # Invalid state must fail closed.
        fakeout=d/'fake'/reg.name; fakeout.parent.mkdir(parents=True)
        fake=run('block-set',reg,rx*512,64,rz*512,'--state','{"Name":"minecraft:definitely_not_a_real_block"}','--out',fakeout,ok=False)
        if fake.returncode==0: raise RuntimeError('invalid block id was accepted')
        result['invalid_block_rejected']=True
        # Corrupt an unrelated location entry and ensure a write is refused.
        corrupt=d/'corrupt'/reg.name; corrupt.parent.mkdir(parents=True); shutil.copy2(reg,corrupt)
        b=bytearray(corrupt.read_bytes())
        present=[i for i,e in enumerate(offsets) if e]
        if len(present)>=2:
            victim=present[1]; struct.pack_into('>I',b,victim*4,(0xFFFFFE<<8)|1); corrupt.write_bytes(b)
            target=present[0]; tcx,tcz=mw.local_to_global(rx,rz,target)
            cout=d/'corrupt-out'/reg.name; cout.parent.mkdir(parents=True)
            p=run('set',corrupt,'--cx',tcx,'--cz',tcz,'/Status','--type','string','--value','"minecraft:full"','--out',cout,ok=False)
            if p.returncode==0 or cout.exists(): raise RuntimeError('corrupt unrelated entry did not block write')
            result['corrupt_region_write_rejected']=True
        # Exercise a 16->17 palette transition using a state already observed in this region.
        if boundary is not None:
            cx,cz,sy,states,data=boundary; local={mw._canonical_state(x) for x in states}
            candidates=[st for key,st in observed.items() if key not in local and not mw._is_probable_block_entity_state(st)]
            candidates.sort(key=lambda st:(1 if st.get('Properties') else 0,len(str(st))))
            if candidates:
                st=candidates[0]; x=cx*16; y=sy*16; z=cz*16
                out=d/'boundary'/reg.name; out.parent.mkdir(parents=True)
                p=run('block-set',reg,x,y,z,'--state',json.dumps(st,separators=(',',':')),'--out',out)
                after=mw.read_nbt(mw.read_chunk_record(out,cx,cz).nbt_raw); sec=mw._section_for_y(after,y); _bs,pal2,data2=mw._block_states_parts(sec)
                if len(pal2)!=17 or len(data2.value)!=342: raise RuntimeError(f'unexpected palette boundary result: {len(pal2)}, {len(data2.value)}')
                mw.validate_region_for_write(out,deep=True)
                result['palette_16_to_17']={'state':st,'longs_before':len(data.value) if data else 0,'longs_after':len(data2.value),'status':'ok'}
    # exact 255-sector boundary formula unit check
    import io
    length=256*4096-4; f=io.BytesIO(struct.pack('>I',length))
    if mw.actual_sector_count(f,0,255)!=257: raise RuntimeError('255-sector boundary formula mismatch')
    result['oversized_boundary_sectors']=257
    print(json.dumps(result,ensure_ascii=False,indent=2))

if __name__=='__main__': main()
