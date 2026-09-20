#!/usr/bin/env python3
import importlib.util, gzip, zlib, struct, tempfile, subprocess, json
from pathlib import Path

HERE=Path(__file__).resolve().parent
TOOL=HERE.parent/'scripts'/'mcworld_nbt.py'
spec=importlib.util.spec_from_file_location('mw', TOOL)
mw=importlib.util.module_from_spec(spec); import sys; sys.modules['mw']=mw; spec.loader.exec_module(mw)

def make_root():
    return mw.RootTag('', mw.Tag(10, mw.OrderedDict([
        ('DataVersion', mw.Tag(3, 4440)),
        ('Data', mw.Tag(10, mw.OrderedDict([
            ('LevelName', mw.Tag(8,'Deepthink 테스트 🌏')),
            ('DayTime', mw.Tag(4,12345)),
            ('raining', mw.Tag(1,0)),
        ]))),
        ('Byte', mw.Tag(1,-7)),
        ('Short', mw.Tag(2,1234)),
        ('Float', mw.Tag(5,1.25)),
        ('Double', mw.Tag(6,-2.5)),
        ('Bytes', mw.Tag(7,[-128,-1,0,1,127])),
        ('Ints', mw.Tag(11,[1,-2,3])),
        ('Longs', mw.Tag(12,[0,-1,9223372036854775807])),
        ('List', mw.Tag(9,mw.ListValue(8,[mw.Tag(8,'a'),mw.Tag(8,'b')]))),
        ('block_entities', mw.Tag(9,mw.ListValue(10,[mw.Tag(10,mw.OrderedDict([
            ('id',mw.Tag(8,'minecraft:chest')),('x',mw.Tag(3,2)),('y',mw.Tag(3,0)),('z',mw.Tag(3,0))
        ]))]))),
        ('sections', mw.Tag(9,mw.ListValue(10,[mw.Tag(10,mw.OrderedDict([
            ('Y',mw.Tag(1,0)),
            ('block_states',mw.Tag(10,mw.OrderedDict([
                ('palette',mw.Tag(9,mw.ListValue(10,[
                    mw._palette_tag_from_state({'Name':'minecraft:stone'}),
                    mw._palette_tag_from_state({'Name':'minecraft:dirt'})
                ]))),
                ('data',mw.Tag(12,mw._encode_palette_indices([1]+[0]*4095,2)))
            ])))
        ]))]))),
    ])))

def run(*args):
    p=subprocess.run(['python',str(TOOL),*map(str,args)],text=True,capture_output=True)
    if p.returncode:
        raise RuntimeError(f'{args}\nSTDOUT:{p.stdout}\nSTDERR:{p.stderr}')
    return p.stdout

def make_region(path:Path, raw_nbt:bytes, codec=2):
    payload=mw.compress_region_payload(codec,raw_nbt)
    rec=struct.pack('>I',len(payload)+1)+bytes([codec])+payload
    sectors=(len(rec)+4095)//4096
    hdr=bytearray(8192)
    entry=(2<<8)|sectors
    struct.pack_into('>I',hdr,0,entry)
    struct.pack_into('>I',hdr,4096,1700000000)
    path.write_bytes(bytes(hdr)+rec+b'\0'*(sectors*4096-len(rec)))

with tempfile.TemporaryDirectory() as td:
    d=Path(td); root=make_root(); raw=mw.write_nbt(root)
    # Minimal Mojang reports/blocks.json-shaped fixture for deterministic validator tests.
    report=d/'blocks.json'
    report.write_text(json.dumps({
        'minecraft:stone': {'properties': {}, 'states':[{'id':1,'default':True}]},
        'minecraft:dirt': {'properties': {}, 'states':[{'id':2,'default':True}]},
        'minecraft:grass_block': {
            'properties': {'snowy':['true','false']},
            'states':[{'properties':{'snowy':'true'},'id':3},{'properties':{'snowy':'false'},'id':4,'default':True}]
        },
        'minecraft:chest': {
            'properties': {'facing':['north','south','west','east'],'type':['single','left','right'],'waterlogged':['true','false']},
            'states':[{'properties':{'facing':'north','type':'single','waterlogged':'false'},'id':5,'default':True}]
        }
    }),encoding='utf-8')
    # NBT round trip and modified UTF-8 / surrogate pair behavior
    assert mw.write_nbt(mw.read_nbt(raw))==raw
    # all region codecs round-trip (LZ4 only if installed)
    for codec in (1,2,3,4):
        if codec==4 and mw._lz4_block is None: continue
        comp=mw.compress_region_payload(codec,raw)
        assert mw.decompress_region_payload(codec,comp)==raw, codec
    # .dat read/edit/diff
    dat=d/'level.dat'; dat.write_bytes(gzip.compress(raw))
    assert 'Deepthink' in run('get',dat,'/Data/LevelName')
    outdat=d/'level.edited.dat'
    # type preservation guard
    bad=subprocess.run(['python',str(TOOL),'set',str(dat),'/Data/DayTime','--type','int','--value','6000','--out',str(d/'bad.dat')],text=True,capture_output=True)
    assert bad.returncode!=0 and 'allow-type-change' in bad.stderr
    run('set',dat,'/Data/DayTime','--type','long','--value','6000','--out',outdat)
    diff=json.loads(run('diff',dat,outdat))
    assert any(x['path']=='/Data/DayTime' and x['to']==6000 for x in diff)
    # .mca scan/read/edit, canonical filename in different output dir
    regdir=d/'world'/'region'; regdir.mkdir(parents=True); reg=regdir/'r.0.0.mca'; make_region(reg,raw,2)
    inv=json.loads(run('region-list',reg,'--inspect-nbt'))
    assert inv[0]['cx']==0 and inv[0]['cz']==0 and inv[0]['DataVersion']==4440
    assert 'LevelName' in run('tree',reg,'--cx','0','--cz','0','--path','/Data','--depth','2')
    outdir=d/'edited'/'region'; outdir.mkdir(parents=True); outreg=outdir/'r.0.0.mca'
    run('set',reg,'--cx','0','--cz','0','/Data/raining','--type','byte','--value','1','--out',outreg)
    got=json.loads(run('get',outreg,'--cx','0','--cz','0','/Data/raining'))
    assert got['value']==1
    diff=json.loads(run('diff',reg,outreg,'--cx','0','--cz','0'))
    assert any(x['path']=='/Data/raining' and x['to']==1 for x in diff)
    # block palette decode/repack: (0,0,0) dirt -> grass_block, neighboring block remains stone
    bg=json.loads(run('block-get',reg,'0','0','0')); assert bg['state']['Name']=='minecraft:dirt'
    blockdir=d/'blockedit'/'region'; blockdir.mkdir(parents=True); blockreg=blockdir/'r.0.0.mca'
    run('block-set',reg,'0','0','0','--state','{\"Name\":\"minecraft:grass_block\",\"Properties\":{\"snowy\":\"false\"}}','--blocks-report',report,'--out',blockreg)
    bg2=json.loads(run('block-get',blockreg,'0','0','0')); assert bg2['state']['Name']=='minecraft:grass_block'
    neighbor=json.loads(run('block-get',blockreg,'1','0','0')); assert neighbor['state']['Name']=='minecraft:stone'
    # existing block-entity target is refused without the expert override
    refuse_dir=d/'refuse'/'region'; refuse_dir.mkdir(parents=True); refuse=refuse_dir/'r.0.0.mca'
    pr=subprocess.run(['python',str(TOOL),'block-set',str(reg),'2','0','0','--state','{\"Name\":\"minecraft:stone\"}','--out',str(refuse)],text=True,capture_output=True)
    assert pr.returncode!=0 and 'block_entity' in pr.stderr

    # invalid/nonexistent block ids fail closed under registry validation
    invalid_dir=d/'invalid'/'region'; invalid_dir.mkdir(parents=True); invalid=invalid_dir/'r.0.0.mca'
    badid=subprocess.run(['python',str(TOOL),'block-set',str(reg),'0','0','0','--state','{"Name":"minecraft:definitely_not_real"}','--blocks-report',str(report),'--out',str(invalid)],text=True,capture_output=True)
    assert badid.returncode!=0 and 'not present' in badid.stderr
    # invalid property values are rejected
    badprop=subprocess.run(['python',str(TOOL),'block-set',str(reg),'0','0','0','--state','{"Name":"minecraft:grass_block","Properties":{"snowy":"banana"}}','--blocks-report',str(report),'--out',str(invalid)],text=True,capture_output=True)
    assert badprop.returncode!=0 and 'invalid value' in badprop.stderr
    # creating a known block-entity-backed block is refused even with a valid registry state
    chest_state='{"Name":"minecraft:chest","Properties":{"facing":"north","type":"single","waterlogged":"false"}}'
    be_new=subprocess.run(['python',str(TOOL),'block-set',str(reg),'0','0','0','--state',chest_state,'--blocks-report',str(report),'--out',str(invalid)],text=True,capture_output=True)
    assert be_new.returncode!=0 and 'requires block-entity' in be_new.stderr
    # structural corruption in an unrelated header entry must abort writes (fail closed)
    corrupt=d/'corrupt'/'r.0.0.mca'; corrupt.parent.mkdir(parents=True); corrupt.write_bytes(reg.read_bytes())
    bb=bytearray(corrupt.read_bytes()); struct.pack_into('>I',bb,4,(0xFFFFFE<<8)|1); corrupt.write_bytes(bb)
    corrupt_out=d/'corruptout'/'r.0.0.mca'; corrupt_out.parent.mkdir(parents=True)
    cp=subprocess.run(['python',str(TOOL),'set',str(corrupt),'--cx','0','--cz','0','/Data/raining','--type','byte','--value','1','--out',str(corrupt_out)],text=True,capture_output=True)
    assert cp.returncode!=0 and 'preflight' in cp.stderr and not corrupt_out.exists()
    # 255-sector boundary follows Paper/Spigot 1.21.8 integer formula exactly.
    boundary=d/'boundary.bin'; length=256*4096-4; boundary.write_bytes(struct.pack('>I',length)+b'\0'*4092)
    with boundary.open('rb') as bf: assert mw.actual_sector_count(bf,0,255)==257

    # negative coordinate + external .mcc read/edit path (use zlib framing externally)
    negdir=d/'neg'/'region'; negdir.mkdir(parents=True); neg=negdir/'r.-1.-1.mca'
    payload=mw.compress_region_payload(2,raw); hdr=bytearray(8192); idx=mw.chunk_index(-1,-1)
    struct.pack_into('>I',hdr,idx*4,(2<<8)|1); struct.pack_into('>I',hdr,4096+idx*4,1700000001)
    extrec=struct.pack('>I',1)+bytes([0x80|2]); neg.write_bytes(bytes(hdr)+extrec+b'\0'*(4096-len(extrec)))
    (negdir/'c.-1.-1.mcc').write_bytes(payload)
    ng=json.loads(run('get',neg,'--cx','-1','--cz','-1','/Data/LevelName')); assert 'Deepthink' in ng['value']
    negoutdir=d/'negout'/'region'; negoutdir.mkdir(parents=True); negout=negoutdir/'r.-1.-1.mca'
    run('set',neg,'--cx','-1','--cz','-1','/Data/raining','--type','byte','--value','1','--out',negout)
    assert (negoutdir/'c.-1.-1.mcc').exists()
    ng2=json.loads(run('get',negout,'--cx','-1','--cz','-1','/Data/raining')); assert ng2['value']==1
print('smoke_test: OK')
