#!/usr/bin/env python3
"""Minecraft Java 1.21.8 NBT / Anvil region inspection and targeted editing.

Pure-Python NBT parser/writer. Region compression supports gzip, zlib, none,
and Mojang/Paper's lz4-java LZ4Block stream when python-lz4 is installed.

Safety model:
- Read operations never modify inputs.
- Write operations require a distinct --out path.
- Existing output files are refused unless --force is supplied.
- Region edits preserve all untouched chunk bytes and their timestamps.
- Modified chunks preserve their original compression codec.
"""
from __future__ import annotations

import argparse
import copy
import dataclasses
import gzip
import hashlib
import io
import json
import math
import os
from pathlib import Path
import re
import shutil
import struct
import sys
import time
import zlib
from collections import OrderedDict
from typing import Any, Iterable

try:
    import lz4.block as _lz4_block
except Exception:
    _lz4_block = None

TAG_NAMES = {
    0: "end", 1: "byte", 2: "short", 3: "int", 4: "long",
    5: "float", 6: "double", 7: "byte_array", 8: "string",
    9: "list", 10: "compound", 11: "int_array", 12: "long_array",
}
NAME_TO_TAG = {v: k for k, v in TAG_NAMES.items()}
MAX_DEPTH = 512
MAX_CONTAINER_ITEMS = 64_000_000
SECTOR_BYTES = 4096
REGION_HEADER_BYTES = 8192
EXTERNAL_STREAM_FLAG = 0x80
REGION_RE = re.compile(r"^r\.(-?\d+)\.(-?\d+)\.mca$")

class NBTError(Exception):
    pass

class RegionError(Exception):
    pass

@dataclasses.dataclass
class ListValue:
    element_type: int
    items: list["Tag"]

@dataclasses.dataclass
class Tag:
    type_id: int
    value: Any

@dataclasses.dataclass
class RootTag:
    name: str
    tag: Tag

# -------------------- binary helpers / MUTF-8 --------------------

def _read_exact(f: io.BufferedIOBase | io.BytesIO, n: int) -> bytes:
    if n < 0:
        raise NBTError(f"negative read length: {n}")
    b = f.read(n)
    if len(b) != n:
        raise NBTError(f"unexpected EOF: wanted {n}, got {len(b)}")
    return b

def _unpack(f, fmt: str):
    size = struct.calcsize(fmt)
    return struct.unpack(fmt, _read_exact(f, size))[0]

def decode_mutf8(data: bytes) -> str:
    # Java DataInput.readUTF compatible modified UTF-8 -> UTF-16 code units.
    units: list[int] = []
    i = 0
    n = len(data)
    while i < n:
        c = data[i]
        if c <= 0x7F and c != 0:
            units.append(c); i += 1
        elif (c & 0xE0) == 0xC0:
            if i + 1 >= n:
                raise NBTError("truncated modified UTF-8 sequence")
            c2 = data[i+1]
            if (c2 & 0xC0) != 0x80:
                raise NBTError("invalid modified UTF-8 continuation")
            units.append(((c & 0x1F) << 6) | (c2 & 0x3F)); i += 2
        elif (c & 0xF0) == 0xE0:
            if i + 2 >= n:
                raise NBTError("truncated modified UTF-8 sequence")
            c2, c3 = data[i+1], data[i+2]
            if (c2 & 0xC0) != 0x80 or (c3 & 0xC0) != 0x80:
                raise NBTError("invalid modified UTF-8 continuation")
            units.append(((c & 0x0F) << 12) | ((c2 & 0x3F) << 6) | (c3 & 0x3F)); i += 3
        else:
            raise NBTError("invalid modified UTF-8 lead byte")
    raw = b"".join(struct.pack(">H", u) for u in units)
    return raw.decode("utf-16-be", errors="surrogatepass")

def encode_mutf8(s: str) -> bytes:
    raw = s.encode("utf-16-be", errors="surrogatepass")
    out = bytearray()
    for i in range(0, len(raw), 2):
        u = (raw[i] << 8) | raw[i+1]
        if 0x0001 <= u <= 0x007F:
            out.append(u)
        elif u <= 0x07FF:
            out.extend((0xC0 | ((u >> 6) & 0x1F), 0x80 | (u & 0x3F)))
        else:
            out.extend((0xE0 | ((u >> 12) & 0x0F), 0x80 | ((u >> 6) & 0x3F), 0x80 | (u & 0x3F)))
    if len(out) > 65535:
        raise NBTError("NBT string exceeds modified UTF-8 unsigned-short limit")
    return bytes(out)

def _read_string(f) -> str:
    n = _unpack(f, ">H")
    return decode_mutf8(_read_exact(f, n))

def _write_string(f, s: str) -> None:
    b = encode_mutf8(s)
    f.write(struct.pack(">H", len(b)))
    f.write(b)

# -------------------- NBT parse/write --------------------

def _check_length(n: int, kind: str) -> int:
    if n < 0:
        raise NBTError(f"negative {kind} length: {n}")
    if n > MAX_CONTAINER_ITEMS:
        raise NBTError(f"refusing implausibly large {kind} length: {n}")
    return n

def read_payload(f, type_id: int, depth: int = 0) -> Tag:
    if depth > MAX_DEPTH:
        raise NBTError("NBT nesting exceeds safety limit")
    if type_id == 1: return Tag(type_id, _unpack(f, ">b"))
    if type_id == 2: return Tag(type_id, _unpack(f, ">h"))
    if type_id == 3: return Tag(type_id, _unpack(f, ">i"))
    if type_id == 4: return Tag(type_id, _unpack(f, ">q"))
    if type_id == 5: return Tag(type_id, _unpack(f, ">f"))
    if type_id == 6: return Tag(type_id, _unpack(f, ">d"))
    if type_id == 7:
        n = _check_length(_unpack(f, ">i"), "byte array")
        b = _read_exact(f, n)
        vals = [x if x < 128 else x - 256 for x in b]
        return Tag(type_id, vals)
    if type_id == 8: return Tag(type_id, _read_string(f))
    if type_id == 9:
        elem = _unpack(f, ">B")
        if elem not in TAG_NAMES:
            raise NBTError(f"unknown list element tag id {elem}")
        n = _check_length(_unpack(f, ">i"), "list")
        if elem == 0 and n != 0:
            raise NBTError("TAG_End list element type with non-empty list")
        items = [read_payload(f, elem, depth + 1) for _ in range(n)]
        return Tag(type_id, ListValue(elem, items))
    if type_id == 10:
        d: OrderedDict[str, Tag] = OrderedDict()
        while True:
            child_type = _unpack(f, ">B")
            if child_type == 0:
                break
            if child_type not in TAG_NAMES:
                raise NBTError(f"unknown compound tag id {child_type}")
            name = _read_string(f)
            if name in d:
                raise NBTError(f"duplicate compound key {name!r}; refusing lossy parse")
            d[name] = read_payload(f, child_type, depth + 1)
        return Tag(type_id, d)
    if type_id == 11:
        n = _check_length(_unpack(f, ">i"), "int array")
        return Tag(type_id, list(struct.unpack(f">{n}i", _read_exact(f, n * 4))) if n else [])
    if type_id == 12:
        n = _check_length(_unpack(f, ">i"), "long array")
        return Tag(type_id, list(struct.unpack(f">{n}q", _read_exact(f, n * 8))) if n else [])
    raise NBTError(f"unsupported tag id {type_id}")

def read_nbt(data: bytes) -> RootTag:
    f = io.BytesIO(data)
    root_type = _unpack(f, ">B")
    if root_type == 0:
        raise NBTError("root tag cannot be TAG_End")
    if root_type not in TAG_NAMES:
        raise NBTError(f"unknown root tag id {root_type}")
    name = _read_string(f)
    tag = read_payload(f, root_type, 0)
    trailing = f.read()
    if trailing and any(b != 0 for b in trailing):
        # Preserve strictness: compressed payload should contain exactly one root tag.
        raise NBTError(f"non-zero trailing bytes after root NBT: {len(trailing)}")
    return RootTag(name, tag)

def _write_payload(f, tag: Tag, depth: int = 0) -> None:
    if depth > MAX_DEPTH:
        raise NBTError("NBT nesting exceeds safety limit")
    t, v = tag.type_id, tag.value
    if t == 1: f.write(struct.pack(">b", int(v))); return
    if t == 2: f.write(struct.pack(">h", int(v))); return
    if t == 3: f.write(struct.pack(">i", int(v))); return
    if t == 4: f.write(struct.pack(">q", int(v))); return
    if t == 5: f.write(struct.pack(">f", float(v))); return
    if t == 6: f.write(struct.pack(">d", float(v))); return
    if t == 7:
        f.write(struct.pack(">i", len(v)))
        f.write(bytes((int(x) & 0xFF) for x in v)); return
    if t == 8: _write_string(f, str(v)); return
    if t == 9:
        if not isinstance(v, ListValue): raise NBTError("malformed TAG_List")
        f.write(struct.pack(">B", v.element_type)); f.write(struct.pack(">i", len(v.items)))
        for child in v.items:
            if child.type_id != v.element_type:
                raise NBTError("TAG_List child type mismatch")
            _write_payload(f, child, depth + 1)
        return
    if t == 10:
        for name, child in v.items():
            if child.type_id == 0: raise NBTError("TAG_End cannot be named compound child")
            f.write(struct.pack(">B", child.type_id)); _write_string(f, name); _write_payload(f, child, depth + 1)
        f.write(b"\x00"); return
    if t == 11:
        f.write(struct.pack(">i", len(v)))
        if v: f.write(struct.pack(f">{len(v)}i", *map(int, v)))
        return
    if t == 12:
        f.write(struct.pack(">i", len(v)))
        if v: f.write(struct.pack(f">{len(v)}q", *map(int, v)))
        return
    raise NBTError(f"cannot write tag id {t}")

def write_nbt(root: RootTag) -> bytes:
    f = io.BytesIO()
    f.write(struct.pack(">B", root.tag.type_id)); _write_string(f, root.name); _write_payload(f, root.tag)
    return f.getvalue()

# -------------------- typed JSON / tree --------------------

def tag_to_typed(tag: Tag, max_array: int | None = None) -> dict[str, Any]:
    t = tag.type_id
    out: dict[str, Any] = {"type": TAG_NAMES[t]}
    if t == 9:
        lv: ListValue = tag.value
        out["element_type"] = TAG_NAMES[lv.element_type]
        out["value"] = [tag_to_typed(x, max_array) for x in lv.items]
    elif t == 10:
        out["value"] = {k: tag_to_typed(v, max_array) for k, v in tag.value.items()}
    elif t in (7, 11, 12) and max_array is not None and len(tag.value) > max_array:
        out["length"] = len(tag.value)
        out["preview"] = tag.value[:max_array]
        out["truncated"] = True
    else:
        out["value"] = tag.value
    return out

def typed_to_tag(obj: dict[str, Any]) -> Tag:
    if not isinstance(obj, dict) or "type" not in obj:
        raise NBTError("typed JSON tag requires object with 'type'")
    type_name = obj["type"]
    if type_name not in NAME_TO_TAG or type_name == "end":
        raise NBTError(f"invalid typed JSON tag type: {type_name!r}")
    t = NAME_TO_TAG[type_name]
    if t == 9:
        et = obj.get("element_type")
        if et not in NAME_TO_TAG or et == "end" and obj.get("value"):
            raise NBTError("invalid list element_type")
        items = [typed_to_tag(x) for x in obj.get("value", [])]
        etid = NAME_TO_TAG[et]
        if any(x.type_id != etid for x in items): raise NBTError("list child type mismatch")
        return Tag(t, ListValue(etid, items))
    if t == 10:
        val = obj.get("value", {})
        if not isinstance(val, dict): raise NBTError("compound value must be object")
        return Tag(t, OrderedDict((k, typed_to_tag(v)) for k, v in val.items()))
    return make_scalar_tag(type_name, obj.get("value"))

def make_scalar_tag(type_name: str, value: Any) -> Tag:
    if type_name not in NAME_TO_TAG or type_name in ("end", "list", "compound"):
        raise NBTError(f"set-scalar does not accept type {type_name!r}")
    t = NAME_TO_TAG[type_name]
    if t in (1,2,3,4):
        if isinstance(value, bool) or not isinstance(value, int): raise NBTError(f"{type_name} requires integer JSON value")
        ranges = {1:(-128,127),2:(-32768,32767),3:(-2147483648,2147483647),4:(-9223372036854775808,9223372036854775807)}
        lo, hi = ranges[t]
        if not lo <= value <= hi: raise NBTError(f"{type_name} out of range")
    elif t in (5,6):
        if isinstance(value, bool) or not isinstance(value, (int,float)): raise NBTError(f"{type_name} requires numeric JSON value")
        value = float(value)
    elif t == 8:
        if not isinstance(value, str): raise NBTError("string requires JSON string")
    elif t in (7,11,12):
        if not isinstance(value, list) or any(isinstance(x,bool) or not isinstance(x,int) for x in value):
            raise NBTError(f"{type_name} requires JSON integer array")
        if t == 7 and any(x < -128 or x > 255 for x in value): raise NBTError("byte_array values must be -128..255")
        if t == 11 and any(x < -2147483648 or x > 2147483647 for x in value): raise NBTError("int_array value out of range")
        if t == 12 and any(x < -9223372036854775808 or x > 9223372036854775807 for x in value): raise NBTError("long_array value out of range")
    return Tag(t, value)

def pointer_parts(path: str) -> list[str]:
    if path in ("", "/"): return []
    if not path.startswith("/"): raise NBTError("NBT path must be JSON-pointer style, e.g. /Data/LevelName")
    return [x.replace("~1", "/").replace("~0", "~") for x in path[1:].split("/")]

def resolve_tag(root: Tag, path: str) -> Tag:
    cur = root
    for p in pointer_parts(path):
        if cur.type_id == 10:
            if p not in cur.value: raise NBTError(f"compound key not found at {p!r}")
            cur = cur.value[p]
        elif cur.type_id == 9:
            try: idx = int(p)
            except ValueError: raise NBTError(f"list path component must be integer, got {p!r}")
            lv: ListValue = cur.value
            if idx < 0: idx += len(lv.items)
            if not 0 <= idx < len(lv.items): raise NBTError(f"list index out of range: {idx}")
            cur = lv.items[idx]
        else:
            raise NBTError(f"cannot descend through {TAG_NAMES[cur.type_id]}")
    return cur

def replace_tag(root: Tag, path: str, replacement: Tag, create: bool = False) -> None:
    parts = pointer_parts(path)
    if not parts: raise NBTError("refusing to replace root with set; use typed export/import workflow")
    cur = root
    for p in parts[:-1]:
        if cur.type_id == 10:
            if p not in cur.value: raise NBTError(f"compound key not found at {p!r}")
            cur = cur.value[p]
        elif cur.type_id == 9:
            lv: ListValue = cur.value
            try: idx = int(p)
            except ValueError: raise NBTError(f"list path component must be integer, got {p!r}")
            cur = lv.items[idx]
        else: raise NBTError(f"cannot descend through {TAG_NAMES[cur.type_id]}")
    last = parts[-1]
    if cur.type_id == 10:
        if last not in cur.value and not create: raise NBTError(f"compound key not found at {last!r}; use --create explicitly")
        cur.value[last] = replacement
    elif cur.type_id == 9:
        lv = cur.value
        try: idx = int(last)
        except ValueError: raise NBTError("list target must be integer")
        if replacement.type_id != lv.element_type: raise NBTError("replacement type does not match list element type")
        lv.items[idx] = replacement
    else: raise NBTError(f"cannot replace child of {TAG_NAMES[cur.type_id]}")

def _preview_scalar(tag: Tag, max_chars=120) -> str:
    t, v = tag.type_id, tag.value
    if t == 8:
        r = json.dumps(v, ensure_ascii=False)
        return r if len(r) <= max_chars else r[:max_chars-3] + "..."
    if t in (7,11,12):
        return f"len={len(v)} preview={v[:8]}"
    if t == 9: return f"len={len(v.items)} element={TAG_NAMES[v.element_type]}"
    if t == 10: return f"keys={len(v)}"
    return repr(v)

def tree_lines(tag: Tag, path: str = "", depth: int = 0, max_depth: int = 4, max_children: int = 40) -> Iterable[str]:
    yield f"{path or '/'} [{TAG_NAMES[tag.type_id]}] {_preview_scalar(tag)}"
    if depth >= max_depth: return
    if tag.type_id == 10:
        items = list(tag.value.items())
        for i, (k, child) in enumerate(items):
            if i >= max_children:
                yield f"{path or '/'} ... ({len(items)-max_children} more keys)"; break
            esc = k.replace("~","~0").replace("/","~1")
            child_path = (path + "/" + esc) if path else "/" + esc
            yield from tree_lines(child, child_path, depth+1, max_depth, max_children)
    elif tag.type_id == 9:
        lv: ListValue = tag.value
        for i, child in enumerate(lv.items[:max_children]):
            child_path = (path + f"/{i}") if path else f"/{i}"
            yield from tree_lines(child, child_path, depth+1, max_depth, max_children)
        if len(lv.items) > max_children: yield f"{path or '/'} ... ({len(lv.items)-max_children} more items)"


def find_nan_paths(tag: Tag, path: str = "") -> list[str]:
    out: list[str] = []
    def rec(t: Tag, p: str) -> None:
        if t.type_id in (5, 6) and isinstance(t.value, float) and math.isnan(t.value):
            out.append(p or "/"); return
        if t.type_id == 10:
            for k, v in t.value.items():
                esc=k.replace("~","~0").replace("/","~1")
                rec(v, (p+"/"+esc) if p else "/"+esc)
        elif t.type_id == 9:
            for i, v in enumerate(t.value.items): rec(v, (p+f"/{i}") if p else f"/{i}")
    rec(tag, path)
    return out

def assert_write_safe_nbt(root: RootTag) -> None:
    nan=find_nan_paths(root.tag)
    if nan:
        preview=", ".join(nan[:5])
        raise NBTError(f"refusing write because NaN float/double payloads may not round-trip bit-exactly; paths: {preview}")

# -------------------- compression --------------------

def xxh32(data: bytes, seed: int = 0) -> int:
    # xxHash32 reference algorithm, little-endian input.
    P1=0x9E3779B1; P2=0x85EBCA77; P3=0xC2B2AE3D; P4=0x27D4EB2F; P5=0x165667B1
    def rol(x,r): return ((x << r) | (x >> (32-r))) & 0xFFFFFFFF
    def rnd(acc, inp):
        acc = (acc + inp * P2) & 0xFFFFFFFF; acc = rol(acc,13); return (acc * P1) & 0xFFFFFFFF
    n=len(data); i=0
    if n >= 16:
        v1=(seed+P1+P2)&0xFFFFFFFF; v2=(seed+P2)&0xFFFFFFFF; v3=seed&0xFFFFFFFF; v4=(seed-P1)&0xFFFFFFFF
        limit=n-16
        while i <= limit:
            v1=rnd(v1, struct.unpack_from("<I",data,i)[0]); i+=4
            v2=rnd(v2, struct.unpack_from("<I",data,i)[0]); i+=4
            v3=rnd(v3, struct.unpack_from("<I",data,i)[0]); i+=4
            v4=rnd(v4, struct.unpack_from("<I",data,i)[0]); i+=4
        h=(rol(v1,1)+rol(v2,7)+rol(v3,12)+rol(v4,18))&0xFFFFFFFF
    else: h=(seed+P5)&0xFFFFFFFF
    h=(h+n)&0xFFFFFFFF
    while i+4 <= n:
        h=(h + struct.unpack_from("<I",data,i)[0]*P3)&0xFFFFFFFF; h=(rol(h,17)*P4)&0xFFFFFFFF; i+=4
    while i<n:
        h=(h + data[i]*P5)&0xFFFFFFFF; h=(rol(h,11)*P1)&0xFFFFFFFF; i+=1
    h ^= h >> 15; h=(h*P2)&0xFFFFFFFF; h ^= h >> 13; h=(h*P3)&0xFFFFFFFF; h ^= h >> 16
    return h & 0xFFFFFFFF

def lz4_java_decompress(data: bytes) -> bytes:
    if _lz4_block is None: raise RegionError("compression id 4 requires python package 'lz4'")
    pos=0; out=bytearray(); magic=b"LZ4Block"; seed=0x9747B28C
    while True:
        if pos + 21 > len(data): raise RegionError("truncated LZ4Block header")
        if data[pos:pos+8] != magic: raise RegionError("invalid LZ4Block magic")
        token=data[pos+8]; method=token & 0xF0; level=10+(token&0x0F)
        clen, olen, check = struct.unpack_from("<III", data, pos+9); pos += 21
        if olen > (1 << level) or (olen==0)!=(clen==0): raise RegionError("invalid LZ4Block lengths")
        if olen == 0:
            if check != 0: raise RegionError("invalid LZ4Block terminator checksum")
            break
        if pos + clen > len(data): raise RegionError("truncated LZ4Block payload")
        block=data[pos:pos+clen]; pos += clen
        if method == 0x10:
            if clen != olen: raise RegionError("raw LZ4Block length mismatch")
            raw=block
        elif method == 0x20:
            try: raw=_lz4_block.decompress(block, uncompressed_size=olen)
            except Exception as e: raise RegionError(f"LZ4 block decompression failed: {e}") from e
            if len(raw)!=olen: raise RegionError("LZ4 decompressed length mismatch")
        else: raise RegionError(f"unknown LZ4Block method 0x{method:02x}")
        if xxh32(raw, seed) != check: raise RegionError("LZ4Block checksum mismatch")
        out.extend(raw)
    return bytes(out)

def lz4_java_compress(data: bytes, block_size: int = 1<<16) -> bytes:
    if _lz4_block is None: raise RegionError("compression id 4 requires python package 'lz4'")
    if block_size < 64 or block_size > (1 << 25): raise RegionError("invalid LZ4 block size")
    level=max(0, math.ceil(math.log2(block_size))-10)
    magic=b"LZ4Block"; seed=0x9747B28C; out=bytearray()
    for start in range(0,len(data),block_size):
        raw=data[start:start+block_size]
        comp=_lz4_block.compress(raw, mode="default", store_size=False)
        if len(comp) >= len(raw): method=0x10; payload=raw
        else: method=0x20; payload=comp
        out += magic + bytes([method | level]) + struct.pack("<III", len(payload), len(raw), xxh32(raw,seed)) + payload
    out += magic + bytes([0x10 | level]) + struct.pack("<III",0,0,0)
    return bytes(out)

def decompress_region_payload(codec: int, payload: bytes) -> bytes:
    if codec == 1: return gzip.decompress(payload)
    if codec == 2: return zlib.decompress(payload)
    if codec == 3: return payload
    if codec == 4: return lz4_java_decompress(payload)
    raise RegionError(f"unknown region compression id {codec}")

def compress_region_payload(codec: int, raw: bytes) -> bytes:
    if codec == 1: return gzip.compress(raw, mtime=0)
    if codec == 2: return zlib.compress(raw)
    if codec == 3: return raw
    if codec == 4: return lz4_java_compress(raw)
    raise RegionError(f"unknown region compression id {codec}")

def decompress_dat(raw: bytes) -> tuple[str, bytes]:
    if raw.startswith(b"\x1f\x8b"):
        return "gzip", gzip.decompress(raw)
    try:
        if len(raw)>=2 and (raw[0] & 0x0F)==8 and ((raw[0]<<8)+raw[1])%31==0:
            return "zlib", zlib.decompress(raw)
    except Exception: pass
    return "raw", raw

def compress_dat(kind: str, raw: bytes) -> bytes:
    if kind == "gzip": return gzip.compress(raw, mtime=0)
    if kind == "zlib": return zlib.compress(raw)
    if kind == "raw": return raw
    raise NBTError(f"unknown dat compression {kind}")

# -------------------- region --------------------

def parse_region_coords(path: Path) -> tuple[int,int]:
    m=REGION_RE.match(path.name)
    if not m: raise RegionError("region filename must be r.<rx>.<rz>.mca to resolve global chunk coords")
    return int(m.group(1)), int(m.group(2))

def chunk_index(cx:int, cz:int) -> int: return (cx & 31) + ((cz & 31) << 5)
def local_to_global(rx:int,rz:int,index:int)->tuple[int,int]: return rx*32+(index&31), rz*32+(index>>5)

def sha256_file(path: Path) -> str:
    h=hashlib.sha256()
    with path.open("rb") as f:
        for b in iter(lambda:f.read(1024*1024),b""): h.update(b)
    return h.hexdigest()

def read_region_header(path: Path) -> tuple[list[int], list[int]]:
    with path.open("rb") as f: hdr=f.read(REGION_HEADER_BYTES)
    if len(hdr)<REGION_HEADER_BYTES: raise RegionError("region file shorter than 8 KiB header")
    offsets=[struct.unpack_from(">I",hdr,i*4)[0] for i in range(1024)]
    timestamps=[struct.unpack_from(">I",hdr,4096+i*4)[0] for i in range(1024)]
    return offsets,timestamps

def entry_parts(entry:int)->tuple[int,int]: return entry>>8, entry&0xFF

def actual_sector_count(f, offset_sector:int, header_count:int)->int:
    if header_count != 255: return header_count
    f.seek(offset_sector*SECTOR_BYTES); b=f.read(4)
    if len(b)!=4: raise RegionError("cannot read oversized chunk length")
    length=struct.unpack(">I",b)[0]
    # Match the 1.21.8 Paper/Spigot RegionFile behavior exactly.  The +1 is
    # intentional even when (length + 4) lands exactly on a sector boundary.
    return ((length + 4) // SECTOR_BYTES) + 1

@dataclasses.dataclass
class ChunkRecord:
    cx:int; cz:int; index:int; timestamp:int; offset_sector:int; sector_count:int
    codec:int; external:bool; payload:bytes; nbt_raw:bytes

def read_chunk_record(path: Path, cx:int, cz:int) -> ChunkRecord:
    rx,rz=parse_region_coords(path)
    if not (rx*32 <= cx < rx*32+32 and rz*32 <= cz < rz*32+32):
        raise RegionError(f"chunk ({cx},{cz}) does not belong to region ({rx},{rz})")
    idx=chunk_index(cx,cz); offsets,timestamps=read_region_header(path); entry=offsets[idx]
    if entry==0: raise RegionError(f"chunk ({cx},{cz}) is absent")
    off,count=entry_parts(entry)
    if off<2 or count==0: raise RegionError("invalid chunk location entry")
    file_size=path.stat().st_size
    if off*SECTOR_BYTES >= file_size: raise RegionError("chunk offset beyond EOF")
    with path.open("rb") as f:
        real_count=actual_sector_count(f,off,count)
        f.seek(off*SECTOR_BYTES); length_b=_read_exact(f,4); length=struct.unpack(">I",length_b)[0]
        if length<1 or length>real_count*SECTOR_BYTES-4: raise RegionError(f"invalid chunk length {length}")
        type_byte=_unpack(f,">B"); external=bool(type_byte & EXTERNAL_STREAM_FLAG); codec=type_byte & 0x7F
        inline=f.read(length-1)
    if external:
        ext=path.parent/f"c.{cx}.{cz}.mcc"
        if not ext.exists(): raise RegionError(f"external chunk file missing: {ext.name}")
        payload=ext.read_bytes()
    else: payload=inline
    nbt_raw=decompress_region_payload(codec,payload)
    return ChunkRecord(cx,cz,idx,timestamps[idx],off,real_count,codec,external,payload,nbt_raw)

def region_inventory(path:Path, inspect_nbt:bool=False)->list[dict[str,Any]]:
    rx,rz=parse_region_coords(path); offsets,timestamps=read_region_header(path); rows=[]
    with path.open("rb") as f:
        for idx,entry in enumerate(offsets):
            if entry==0: continue
            cx,cz=local_to_global(rx,rz,idx); off,count=entry_parts(entry)
            row={"cx":cx,"cz":cz,"index":idx,"offset_sector":off,"header_sectors":count,"timestamp":timestamps[idx]}
            try:
                real=actual_sector_count(f,off,count); row["sectors"]=real
                f.seek(off*SECTOR_BYTES); length=struct.unpack(">I",_read_exact(f,4))[0]; type_byte=_unpack(f,">B")
                row.update({"length":length,"codec":type_byte&0x7F,"external":bool(type_byte&0x80)})
                if inspect_nbt:
                    rec=read_chunk_record(path,cx,cz); root=read_nbt(rec.nbt_raw)
                    row["root_name"]=root.name
                    if root.tag.type_id==10 and "DataVersion" in root.tag.value:
                        row["DataVersion"]=root.tag.value["DataVersion"].value
                    for k in ("xPos","zPos","Status"):
                        if root.tag.type_id==10 and k in root.tag.value:
                            row[k]=root.tag.value[k].value
            except Exception as e: row["error"]=str(e)
            rows.append(row)
    return rows

@dataclasses.dataclass
class RegionPreflight:
    region: tuple[int,int]
    file_sectors: int
    allocations: dict[int, tuple[int,int]]
    used_sectors: set[int]
    external_chunks: list[tuple[int,int]]

def validate_region_for_write(path:Path, *, region_coords:tuple[int,int]|None=None, deep:bool=False)->RegionPreflight:
    """Fail-closed structural validation before any region write.

    Reject malformed location entries, truncated allocations, sector overlap, unknown
    compression ids, missing external .mcc payloads, and malformed chunk lengths.
    With deep=True, also decompress and parse every present chunk NBT payload.
    """
    rx,rz = region_coords if region_coords is not None else parse_region_coords(path)
    size=path.stat().st_size
    if size < REGION_HEADER_BYTES:
        raise RegionError("region file shorter than 8 KiB header")
    if size % SECTOR_BYTES != 0:
        raise RegionError(f"region file size is not sector-aligned: {size} bytes")
    total=size//SECTOR_BYTES
    offsets,_timestamps=read_region_header(path)
    allocations:dict[int,tuple[int,int]]={}
    owners:dict[int,int]={0:-1,1:-1}
    external_chunks:list[tuple[int,int]]=[]
    with path.open("rb") as f:
        for idx,entry in enumerate(offsets):
            if entry==0:
                continue
            cx,cz=local_to_global(rx,rz,idx)
            off,header_count=entry_parts(entry)
            if off < 2 or header_count == 0:
                raise RegionError(f"preflight: invalid location entry for chunk ({cx},{cz}): offset={off}, sectors={header_count}")
            real_count=actual_sector_count(f,off,header_count)
            if header_count==255 and real_count < 255:
                raise RegionError(f"preflight: inconsistent oversized allocation for chunk ({cx},{cz}): {real_count} sectors")
            if real_count <= 0 or off + real_count > total:
                raise RegionError(f"preflight: chunk ({cx},{cz}) allocation [{off},{off+real_count}) exceeds file sectors {total}")
            for sec in range(off,off+real_count):
                prev=owners.get(sec)
                if prev is not None:
                    pcx,pcz=local_to_global(rx,rz,prev) if prev>=0 else (None,None)
                    where="region header" if prev<0 else f"chunk ({pcx},{pcz})"
                    raise RegionError(f"preflight: sector overlap at {sec}: chunk ({cx},{cz}) overlaps {where}")
                owners[sec]=idx
            allocations[idx]=(off,real_count)
            f.seek(off*SECTOR_BYTES)
            length=struct.unpack(">I",_read_exact(f,4))[0]
            if length < 1:
                raise RegionError(f"preflight: chunk ({cx},{cz}) has invalid length {length}")
            type_byte=_unpack(f,">B")
            external=bool(type_byte & EXTERNAL_STREAM_FLAG)
            codec=type_byte & 0x7F
            if codec not in (1,2,3,4):
                raise RegionError(f"preflight: chunk ({cx},{cz}) uses unknown compression id {codec}")
            if external:
                if length != 1:
                    raise RegionError(f"preflight: external chunk ({cx},{cz}) must have inline length 1, got {length}")
                ext=path.parent/f"c.{cx}.{cz}.mcc"
                if not ext.is_file():
                    raise RegionError(f"preflight: external chunk file missing: {ext.name}")
                payload=ext.read_bytes() if deep else None
                external_chunks.append((cx,cz))
            else:
                max_len=real_count*SECTOR_BYTES-4
                if length > max_len:
                    raise RegionError(f"preflight: chunk ({cx},{cz}) length {length} exceeds allocation capacity {max_len}")
                payload=_read_exact(f,length-1) if deep else None
            if deep:
                raw=decompress_region_payload(codec,payload if payload is not None else b"")
                read_nbt(raw)
    return RegionPreflight((rx,rz),total,allocations,set(owners),external_chunks)

def collect_used_sectors(path:Path, offsets:list[int]|None=None, skip_index:int|None=None, *, preflight:RegionPreflight|None=None)->set[int]:
    # Never infer free space from a partially readable region.  A malformed
    # unrelated entry must abort the write instead of being silently skipped.
    pf=preflight or validate_region_for_write(path)
    used=set(pf.used_sectors)
    if skip_index is not None and skip_index in pf.allocations:
        off,count=pf.allocations[skip_index]
        used.difference_update(range(off,off+count))
    return used

def find_run(used:set[int], need:int, start:int=2)->int:
    s=start; run=0; run_start=s
    while True:
        if s not in used:
            if run==0: run_start=s
            run += 1
            if run>=need: return run_start
        else: run=0
        s += 1
        if s >= (1<<24): raise RegionError("region sector offset exceeds 24-bit location field")

def copy_external_siblings(src:Path,dst:Path,offsets:list[int],exclude_idx:int|None=None)->None:
    if src.parent.resolve()==dst.parent.resolve(): return
    rx,rz=parse_region_coords(src)
    with src.open("rb") as f:
        for idx,entry in enumerate(offsets):
            if idx==exclude_idx or entry==0: continue
            off,count=entry_parts(entry)
            if off<2 or count==0: continue
            f.seek(off*SECTOR_BYTES+4); b=f.read(1)
            if b and (b[0]&0x80):
                cx,cz=local_to_global(rx,rz,idx); a=src.parent/f"c.{cx}.{cz}.mcc"; bpath=dst.parent/a.name
                if not a.is_file(): raise RegionError(f"external chunk file missing during copy: {a.name}")
                shutil.copy2(a,bpath)

def write_modified_chunk(src:Path,dst:Path,cx:int,cz:int,new_root:RootTag,force:bool=False,preserve_timestamp:bool=False)->dict[str,Any]:
    if src.resolve()==dst.resolve(): raise RegionError("--out must differ from input; never overwrite the source directly")
    if dst.name != src.name: raise RegionError("region output must keep the canonical r.<rx>.<rz>.mca filename; place it in a different directory")
    if dst.exists() and not force: raise RegionError(f"output exists: {dst}; use --force to replace")
    # Critical safety gate: validate every allocation before calculating free space.
    pf=validate_region_for_write(src)
    rec=read_chunk_record(src,cx,cz); offsets,timestamps=read_region_header(src)
    assert_write_safe_nbt(new_root)
    raw=write_nbt(new_root); payload=compress_region_payload(rec.codec,raw)
    inline_record=struct.pack(">I",1+len(payload))+bytes([rec.codec])+payload
    inline_need=math.ceil(len(inline_record)/SECTOR_BYTES)
    keep_external=rec.external or inline_need>255
    if keep_external and dst.exists():
        # A region + .mcc pair cannot be replaced atomically as two independent
        # paths. Refuse force-replacement rather than expose a transient mismatch.
        raise RegionError("refusing to replace an existing output region when the edited chunk is external; use a fresh output directory")
    dst.parent.mkdir(parents=True,exist_ok=True); tmp=dst.with_name(dst.name+".tmp")
    if tmp.exists(): tmp.unlink()
    shutil.copy2(src,tmp)
    copy_external_siblings(src,tmp,offsets,exclude_idx=rec.index)
    used=collect_used_sectors(src,skip_index=rec.index,preflight=pf)
    if keep_external:
        record=struct.pack(">I",1)+bytes([rec.codec|EXTERNAL_STREAM_FLAG]); need=1
    else:
        record=inline_record; need=inline_need
    original_slots=set(range(rec.offset_sector,rec.offset_sector+rec.sector_count))
    can_reuse=need<=rec.sector_count and not (original_slots & used)
    off=rec.offset_sector if can_reuse else find_run(used,need)
    target_ext:Path|None=None
    try:
        with tmp.open("r+b") as f:
            target_size=(off+need)*SECTOR_BYTES
            if f.seek(0,os.SEEK_END)<target_size: f.truncate(target_size)
            f.seek(off*SECTOR_BYTES); f.write(record); pad=need*SECTOR_BYTES-len(record)
            if pad>0: f.write(b"\x00"*pad)
            header_count=need if need<255 else 255
            entry=(off<<8)|header_count
            f.seek(rec.index*4); f.write(struct.pack(">I",entry))
            ts=rec.timestamp if preserve_timestamp else int(time.time())
            f.seek(4096+rec.index*4); f.write(struct.pack(">I",ts & 0xFFFFFFFF)); f.flush(); os.fsync(f.fileno())
        if keep_external:
            # For a fresh output path, publish the external payload first. Until
            # the region rename occurs it is only an orphan, never a broken live reference.
            target_ext=tmp.parent/f"c.{cx}.{cz}.mcc"
            ext_tmp=target_ext.with_name(target_ext.name+".tmp")
            with ext_tmp.open("wb") as ef:
                ef.write(payload); ef.flush(); os.fsync(ef.fileno())
            os.replace(ext_tmp,target_ext)
        check_raw=decompress_region_payload(rec.codec,payload); check_root=read_nbt(check_raw)
        if write_nbt(check_root)!=write_nbt(new_root): raise RegionError("post-write NBT validation mismatch")
        # Validate the complete staged region, not just the edited chunk.
        validate_region_for_write(tmp,region_coords=pf.region,deep=False)
        os.replace(tmp,dst)
    except Exception:
        tmp.unlink(missing_ok=True)
        if target_ext is not None and not dst.exists(): target_ext.unlink(missing_ok=True)
        raise
    return {"input":str(src),"output":str(dst),"chunk":[cx,cz],"codec":rec.codec,"external":keep_external,
            "old_sha256":sha256_file(src),"new_sha256":sha256_file(dst),"old_nbt_bytes":len(rec.nbt_raw),"new_nbt_bytes":len(raw),
            "timestamp_preserved":preserve_timestamp,"preflight":"passed"}


# -------------------- 1.21.x terrain block-state helpers --------------------
def _section_for_y(root: RootTag, y: int) -> Tag:
    if root.tag.type_id != 10 or "sections" not in root.tag.value:
        raise RegionError("chunk does not expose a root /sections list; this may be entities/poi data rather than terrain region data")
    sections = root.tag.value["sections"]
    if sections.type_id != 9 or sections.value.element_type != 10:
        raise RegionError("unexpected /sections NBT shape")
    sy = y // 16
    for sec in sections.value.items:
        if sec.type_id == 10 and "Y" in sec.value and int(sec.value["Y"].value) == sy:
            return sec
    raise RegionError(f"section Y={sy} is absent; block creation in missing sections is intentionally refused")

def _state_from_palette_tag(tag: Tag) -> dict[str, Any]:
    if tag.type_id != 10 or "Name" not in tag.value or tag.value["Name"].type_id != 8:
        raise RegionError("malformed block-state palette entry")
    out={"Name":tag.value["Name"].value}
    if "Properties" in tag.value:
        p=tag.value["Properties"]
        if p.type_id != 10: raise RegionError("malformed block-state Properties")
        props={}
        for k,v in p.value.items():
            if v.type_id != 8: raise RegionError("block-state property value is not a string")
            props[k]=v.value
        out["Properties"]=props
    return out

def _palette_tag_from_state(state: dict[str, Any]) -> Tag:
    if not isinstance(state,dict) or not isinstance(state.get("Name"),str):
        raise RegionError("block state JSON must be an object with string Name")
    d=OrderedDict(); d["Name"]=Tag(8,state["Name"])
    if "Properties" in state:
        props=state["Properties"]
        if not isinstance(props,dict) or any(not isinstance(k,str) or not isinstance(v,str) for k,v in props.items()):
            raise RegionError("Properties must be a JSON object of string:string pairs")
        d["Properties"]=Tag(10,OrderedDict((k,Tag(8,v)) for k,v in props.items()))
    extra=set(state)-{"Name","Properties"}
    if extra: raise RegionError(f"unsupported block-state JSON keys: {sorted(extra)}")
    return Tag(10,d)

def _block_states_parts(section: Tag) -> tuple[Tag,list[Tag],Tag|None]:
    if "block_states" not in section.value or section.value["block_states"].type_id != 10:
        raise RegionError("section has no block_states compound")
    bs=section.value["block_states"]
    if "palette" not in bs.value or bs.value["palette"].type_id != 9 or bs.value["palette"].value.element_type != 10:
        raise RegionError("section block_states has malformed palette")
    palette=bs.value["palette"].value.items
    if not palette: raise RegionError("empty block-state palette")
    data=bs.value.get("data")
    if data is not None and data.type_id != 12: raise RegionError("block_states/data is not TAG_Long_Array")
    return bs,palette,data

def _decode_palette_indices(palette_len:int,data:Tag|None,count:int=4096,min_bits:int=4)->list[int]:
    if palette_len == 1:
        return [0]*count
    bits=max(min_bits,(palette_len-1).bit_length())
    per=64//bits
    need=math.ceil(count/per)
    if data is None: raise RegionError("palette has multiple entries but packed data is absent")
    if len(data.value) < need: raise RegionError(f"packed block-state data too short: need {need} longs, got {len(data.value)}")
    mask=(1<<bits)-1; out=[]
    for i in range(count):
        u=int(data.value[i//per]) & 0xFFFFFFFFFFFFFFFF
        out.append((u >> ((i%per)*bits)) & mask)
    if any(v>=palette_len for v in out): raise RegionError("packed block-state palette index is out of range")
    return out

def _encode_palette_indices(indices:list[int],palette_len:int,min_bits:int=4)->list[int]:
    if palette_len == 1: return []
    bits=max(min_bits,(palette_len-1).bit_length()); per=64//bits; mask=(1<<bits)-1
    arr=[0]*math.ceil(len(indices)/per)
    for i,v in enumerate(indices):
        if v<0 or v>=palette_len or v>mask: raise RegionError("cannot encode palette index")
        arr[i//per] |= (v & mask) << ((i%per)*bits)
    # NBT longs are signed two's complement.
    return [x if x < (1<<63) else x-(1<<64) for x in arr]

def _canonical_state(state:dict[str,Any])->tuple[str,tuple[tuple[str,str],...]]:
    name=state.get("Name")
    if not isinstance(name,str): raise RegionError("block state must contain string Name")
    props=state.get("Properties") or {}
    if not isinstance(props,dict): raise RegionError("block state Properties must be an object")
    return name,tuple(sorted((str(k),str(v)) for k,v in props.items()))

def _validate_state_with_mojang_report(state:dict[str,Any], report:dict[str,Any])->None:
    name,props_tuple=_canonical_state(state); entry=report.get(name)
    if entry is None: raise RegionError(f"block id {name!r} is not present in the supplied 1.21.8 blocks report")
    props=dict(props_tuple)
    schemas=entry.get("properties",{}) if isinstance(entry,dict) else {}
    if not isinstance(schemas,dict): schemas={}
    expected=set(schemas); provided=set(props)
    if provided!=expected:
        missing=sorted(expected-provided); extra=sorted(provided-expected)
        raise RegionError(f"invalid properties for {name}: missing={missing}, extra={extra}")
    for k,v in props.items():
        allowed=[str(x) for x in schemas.get(k,[])]
        if allowed and v not in allowed:
            raise RegionError(f"invalid value for {name}.{k}: {v!r}; allowed={allowed}")
    states=entry.get("states",[]) if isinstance(entry,dict) else []
    if states:
        wanted=dict(props_tuple)
        if not any(isinstance(st,dict) and {str(k):str(v) for k,v in st.get("properties",{}).items()}==wanted for st in states):
            raise RegionError(f"property combination is not a valid registered state for {name}: {wanted}")

def _validate_state_with_prismarine_report(state:dict[str,Any], report:list[Any])->None:
    name,props_tuple=_canonical_state(state); short=name.split(":",1)[1] if name.startswith("minecraft:") else name
    entry=next((x for x in report if isinstance(x,dict) and x.get("name")==short),None)
    if entry is None: raise RegionError(f"block id {name!r} is not present in the supplied 1.21.8 block registry")
    props=dict(props_tuple); specs=entry.get("states",[]) or []
    expected={x.get("name") for x in specs if isinstance(x,dict) and isinstance(x.get("name"),str)}
    if set(props)!=expected:
        raise RegionError(f"invalid properties for {name}: missing={sorted(expected-set(props))}, extra={sorted(set(props)-expected)}")
    for spec in specs:
        k=spec.get("name"); v=props[k]; typ=spec.get("type"); allowed=None
        if isinstance(spec.get("values"),list): allowed=[str(x) for x in spec["values"]]
        elif typ=="bool": allowed=["false","true"]
        elif typ=="int" and isinstance(spec.get("num_values"),int): allowed=[str(i) for i in range(spec["num_values"])]
        if allowed is not None and v not in allowed:
            raise RegionError(f"invalid value for {name}.{k}: {v!r}; allowed={allowed}")

def validate_block_state(state:dict[str,Any], *, region:Path|None=None, blocks_report:Path|None=None, allow_unvalidated:bool=False)->str:
    # Shape validation first.
    _palette_tag_from_state(state)
    if blocks_report is not None:
        try: report=json.loads(blocks_report.read_text(encoding="utf-8"))
        except Exception as e: raise RegionError(f"cannot load blocks report {blocks_report}: {e}") from e
        if isinstance(report,dict): _validate_state_with_mojang_report(state,report)
        elif isinstance(report,list): _validate_state_with_prismarine_report(state,report)
        else: raise RegionError("unsupported blocks report format; expected Mojang reports/blocks.json object or Prismarine blocks.json array")
        return f"registry:{blocks_report}"
    if region is not None:
        # Safe offline fallback: accept only an exact block state already observed
        # somewhere in this region. This is intentionally conservative.
        wanted=_canonical_state(state); rx,rz=parse_region_coords(region); offsets,_=read_region_header(region)
        for idx,entry in enumerate(offsets):
            if entry==0: continue
            cx,cz=local_to_global(rx,rz,idx)
            try:
                root=read_nbt(read_chunk_record(region,cx,cz).nbt_raw)
                if root.tag.type_id!=10 or "sections" not in root.tag.value: continue
                secs=root.tag.value["sections"]
                if secs.type_id!=9 or secs.value.element_type!=10: continue
                for sec in secs.value.items:
                    try: _bs,palette,_data=_block_states_parts(sec)
                    except RegionError: continue
                    for p in palette:
                        if _canonical_state(_state_from_palette_tag(p))==wanted: return "region-observed"
            except (NBTError,RegionError,struct.error,OSError):
                # Structural corruption is caught by the write preflight; do not
                # make validation permissive because an unrelated chunk failed.
                continue
    if allow_unvalidated: return "explicitly-unvalidated"
    raise RegionError("block state is not validated. Supply Mojang 1.21.8 reports/blocks.json with --blocks-report, use a state already observed in this region, or explicitly pass --allow-unvalidated-state")

_BLOCK_ENTITY_EXACT={
    "minecraft:barrel","minecraft:beacon","minecraft:beehive","minecraft:bee_nest","minecraft:bell",
    "minecraft:blast_furnace","minecraft:brewing_stand","minecraft:campfire","minecraft:chest",
    "minecraft:chiseled_bookshelf","minecraft:command_block","minecraft:chain_command_block","minecraft:conduit",
    "minecraft:crafter","minecraft:decorated_pot","minecraft:dispenser","minecraft:dropper","minecraft:enchanting_table",
    "minecraft:ender_chest","minecraft:end_gateway","minecraft:end_portal","minecraft:furnace","minecraft:hopper",
    "minecraft:jigsaw","minecraft:jukebox","minecraft:lectern","minecraft:mob_spawner","minecraft:spawner","minecraft:repeating_command_block",
    "minecraft:sculk_catalyst","minecraft:sculk_sensor","minecraft:calibrated_sculk_sensor","minecraft:sculk_shrieker",
    "minecraft:smoker","minecraft:structure_block","minecraft:trapped_chest","minecraft:trial_spawner","minecraft:vault",
    "minecraft:moving_piston","minecraft:suspicious_sand","minecraft:suspicious_gravel"
}
def _is_probable_block_entity_state(state:dict[str,Any])->bool:
    n=state.get("Name","")
    return n in _BLOCK_ENTITY_EXACT or n.endswith("_sign") or n.endswith("_hanging_sign") or n.endswith("_shulker_box") or n.endswith("_banner") or n.endswith("_bed") or n.endswith("_head") or n.endswith("_skull")

def get_block_state(region:Path,x:int,y:int,z:int)->tuple[dict[str,Any],dict[str,Any]]:
    cx=x//16; cz=z//16; rec=read_chunk_record(region,cx,cz); root=read_nbt(rec.nbt_raw); sec=_section_for_y(root,y)
    _bs,palette,data=_block_states_parts(sec); indices=_decode_palette_indices(len(palette),data)
    idx=((y&15)<<8)|((z&15)<<4)|(x&15); pi=indices[idx]
    return _state_from_palette_tag(palette[pi]), {"chunk":[cx,cz],"section_y":y//16,"local":[x&15,y&15,z&15],"palette_index":pi,"palette_size":len(palette),"codec":rec.codec}

def set_block_state(region:Path,out:Path,x:int,y:int,z:int,state:dict[str,Any],force=False,preserve_timestamp=False,allow_existing_block_entity=False,blocks_report:Path|None=None,allow_unvalidated_state:bool=False,allow_new_block_entity=False)->dict[str,Any]:
    # Validate the entire source region before any state scan or allocation work.
    validate_region_for_write(region)
    validation=validate_block_state(state,region=region,blocks_report=blocks_report,allow_unvalidated=allow_unvalidated_state)
    if _is_probable_block_entity_state(state) and not allow_new_block_entity:
        raise RegionError("target block type normally requires block-entity NBT; block-set only edits block_states. Use --allow-new-block-entity only when coordinating the block entity separately")
    cx=x//16; cz=z//16; rec=read_chunk_record(region,cx,cz); root=read_nbt(rec.nbt_raw); sec=_section_for_y(root,y)
    # Refuse by default if the target coordinate already owns block-entity NBT.
    if root.tag.type_id==10 and "block_entities" in root.tag.value:
        be=root.tag.value["block_entities"]
        if be.type_id==9 and be.value.element_type==10:
            for ent in be.value.items:
                if ent.type_id!=10: continue
                try:
                    ex=int(ent.value["x"].value); ey=int(ent.value["y"].value); ez=int(ent.value["z"].value)
                except Exception:
                    continue
                if (ex,ey,ez)==(x,y,z) and not allow_existing_block_entity:
                    eid=ent.value.get("id")
                    eidv=eid.value if eid is not None and eid.type_id==8 else "unknown"
                    raise RegionError(f"target block has existing block_entity id={eidv!r}; pass --allow-existing-block-entity only when coordinating its NBT separately")
    bs,palette,data=_block_states_parts(sec); indices=_decode_palette_indices(len(palette),data)
    old_states=[_state_from_palette_tag(p) for p in palette]
    idx=((y&15)<<8)|((z&15)<<4)|(x&15); old_pi=indices[idx]; old_state=old_states[old_pi]
    # canonical equality: Name + exact property map
    target={"Name":state["Name"]}
    if state.get("Properties"): target["Properties"]=dict(state["Properties"])
    new_pi=None
    for i,s in enumerate(old_states):
        normalized={"Name":s["Name"]}
        if s.get("Properties"): normalized["Properties"]=dict(s["Properties"])
        if normalized==target: new_pi=i; break
    if new_pi is None:
        palette.append(_palette_tag_from_state(target)); new_pi=len(palette)-1
    indices[idx]=new_pi
    packed=_encode_palette_indices(indices,len(palette))
    if len(palette)==1:
        bs.value.pop("data",None)
    else:
        bs.value["data"]=Tag(12,packed)
    report=write_modified_chunk(region,out,cx,cz,root,force,preserve_timestamp)
    report["block_edit"]={"position":[x,y,z],"old_state":old_state,"new_state":target,"old_palette_index":old_pi,"new_palette_index":new_pi,"palette_size":len(palette),"state_validation":validation}
    report["consistency_warning"]="block-set modifies section block_states only; it does not recalculate heightmaps, lighting, scheduled ticks, fluids, or block-entity NBT"
    return report

# -------------------- generic file load --------------------

def is_mca(path:Path)->bool: return path.suffix.lower()==".mca"

def load_dat(path:Path)->tuple[str,RootTag]:
    kind,raw=decompress_dat(path.read_bytes()); return kind,read_nbt(raw)

def save_dat(src:Path,dst:Path,kind:str,root:RootTag,force=False)->dict[str,Any]:
    if src.resolve()==dst.resolve(): raise NBTError("--out must differ from input")
    if dst.exists() and not force: raise NBTError(f"output exists: {dst}; use --force")
    dst.parent.mkdir(parents=True,exist_ok=True); tmp=dst.with_name(dst.name+".tmp")
    assert_write_safe_nbt(root)
    raw=write_nbt(root); packed=compress_dat(kind,raw); tmp.write_bytes(packed)
    # round-trip verify
    k2,r2=decompress_dat(tmp.read_bytes()); parsed=read_nbt(r2)
    if write_nbt(parsed)!=raw: tmp.unlink(missing_ok=True); raise NBTError("round-trip verification failed")
    os.replace(tmp,dst)
    return {"input":str(src),"output":str(dst),"compression":kind,"old_sha256":sha256_file(src),"new_sha256":sha256_file(dst)}

# -------------------- semantic diff --------------------

def semantic_diff(a:Tag,b:Tag,path="",limit=500)->list[dict[str,Any]]:
    out=[]
    def rec(x:Tag,y:Tag,p:str):
        if len(out)>=limit:return
        if x.type_id!=y.type_id:
            out.append({"path":p or "/","change":"type","from":TAG_NAMES[x.type_id],"to":TAG_NAMES[y.type_id]}); return
        if x.type_id==10:
            xk=set(x.value); yk=set(y.value)
            for k in sorted(xk-yk): out.append({"path":f"{p}/{k}","change":"removed","from":tag_to_typed(x.value[k],8)})
            for k in sorted(yk-xk): out.append({"path":f"{p}/{k}","change":"added","to":tag_to_typed(y.value[k],8)})
            for k in sorted(xk&yk): rec(x.value[k],y.value[k],f"{p}/{k}")
        elif x.type_id==9:
            lx,ly=x.value,y.value
            if lx.element_type!=ly.element_type: out.append({"path":p or "/","change":"list_element_type","from":TAG_NAMES[lx.element_type],"to":TAG_NAMES[ly.element_type]}); return
            if len(lx.items)!=len(ly.items): out.append({"path":p or "/","change":"list_length","from":len(lx.items),"to":len(ly.items)})
            for i,(xi,yi) in enumerate(zip(lx.items,ly.items)): rec(xi,yi,f"{p}/{i}")
        elif x.value!=y.value:
            if x.type_id in (7,11,12): out.append({"path":p or "/","change":"array","from_len":len(x.value),"to_len":len(y.value),"from_preview":x.value[:8],"to_preview":y.value[:8]})
            else: out.append({"path":p or "/","change":"value","from":x.value,"to":y.value})
    rec(a,b,path); return out

# -------------------- CLI --------------------

def parse_json_value(s:str)->Any:
    try:return json.loads(s)
    except json.JSONDecodeError as e: raise NBTError(f"--value must be valid JSON: {e}")

def root_for_args(path:Path,args)->tuple[RootTag,dict[str,Any]]:
    if is_mca(path):
        if args.cx is None or args.cz is None: raise RegionError(".mca operation requires --cx and --cz")
        rec=read_chunk_record(path,args.cx,args.cz); return read_nbt(rec.nbt_raw),{"record":rec}
    kind,root=load_dat(path); return root,{"kind":kind}


def cmd_block_get(args):
    state,meta=get_block_state(Path(args.file),args.x,args.y,args.z)
    print(json.dumps({"position":[args.x,args.y,args.z],"state":state,**meta},ensure_ascii=False,indent=2))

def cmd_block_set(args):
    try: state=json.loads(args.state)
    except json.JSONDecodeError as e: raise RegionError(f"--state must be valid JSON: {e}")
    # validate shape before any file work
    _palette_tag_from_state(state)
    report=set_block_state(Path(args.file),Path(args.out),args.x,args.y,args.z,state,args.force,args.preserve_timestamp,args.allow_existing_block_entity,Path(args.blocks_report) if args.blocks_report else None,args.allow_unvalidated_state,args.allow_new_block_entity)
    print(json.dumps(report,ensure_ascii=False,indent=2))

def cmd_probe(args):
    p=Path(args.file)
    base={"file":str(p),"size":p.stat().st_size,"sha256":sha256_file(p)}
    if is_mca(p):
        rx,rz=parse_region_coords(p); rows=region_inventory(p,False)
        codecs={}; ext=0
        for r in rows:
            c=r.get("codec"); codecs[str(c)]=codecs.get(str(c),0)+1
            ext += int(bool(r.get("external")))
        base.update({"kind":"anvil_region","region":[rx,rz],"present_chunks":len(rows),"codecs":codecs,"external_chunks":ext,"errors":sum("error" in r for r in rows)})
    else:
        kind,root=load_dat(p); base.update({"kind":"nbt","compression":kind,"root_name":root.name,"root_type":TAG_NAMES[root.tag.type_id]})
        if root.tag.type_id==10:
            # common version indicators, deliberately observational rather than hard-coded
            for pp in ("DataVersion","Data/DataVersion","Version/Id"):
                cur=root.tag
                ok=True
                for k in pp.split('/'):
                    if cur.type_id!=10 or k not in cur.value: ok=False; break
                    cur=cur.value[k]
                if ok: base[pp]=cur.value
    print(json.dumps(base,ensure_ascii=False,indent=2))

def cmd_tree(args):
    root,_=root_for_args(Path(args.file),args); target=resolve_tag(root.tag,args.path)
    for line in tree_lines(target,args.path if args.path!="/" else "",0,args.depth,args.max_children): print(line)

def cmd_get(args):
    root,_=root_for_args(Path(args.file),args); tag=resolve_tag(root.tag,args.path)
    print(json.dumps(tag_to_typed(tag,args.max_array),ensure_ascii=False,indent=2))

def cmd_export(args):
    root,_=root_for_args(Path(args.file),args)
    obj={"root_name":root.name,"tag":tag_to_typed(root.tag,None)}
    text=json.dumps(obj,ensure_ascii=False,indent=2)
    if args.out: Path(args.out).write_text(text,encoding="utf-8")
    else: print(text)

def cmd_region_list(args):
    print(json.dumps(region_inventory(Path(args.file),args.inspect_nbt),ensure_ascii=False,indent=2))

def cmd_region_check(args):
    p=Path(args.file); pf=validate_region_for_write(p,deep=args.deep)
    print(json.dumps({
        "file":str(p),"sha256":sha256_file(p),"region":list(pf.region),
        "chunks":len(pf.allocations),"file_sectors":pf.file_sectors,
        "used_sectors":len(pf.used_sectors),"external_chunks":len(pf.external_chunks),
        "deep_nbt_validation":bool(args.deep),"status":"ok"
    },ensure_ascii=False,indent=2))

def cmd_set(args):
    src=Path(args.file); root,meta=root_for_args(src,args); old=copy.deepcopy(resolve_tag(root.tag,args.path))
    replacement=make_scalar_tag(args.type,parse_json_value(args.value))
    if not args.allow_type_change and old.type_id!=replacement.type_id:
        raise NBTError(f"existing tag is {TAG_NAMES[old.type_id]}, replacement is {TAG_NAMES[replacement.type_id]}; pass --allow-type-change explicitly")
    replace_tag(root.tag,args.path,replacement,args.create)
    if is_mca(src): report=write_modified_chunk(src,Path(args.out),args.cx,args.cz,root,args.force,args.preserve_timestamp)
    else: report=save_dat(src,Path(args.out),meta["kind"],root,args.force)
    report["edit"]={"path":args.path,"old":tag_to_typed(old,16),"new":tag_to_typed(replacement,16)}
    print(json.dumps(report,ensure_ascii=False,indent=2))

def cmd_diff(args):
    p1,p2=Path(args.a),Path(args.b)
    if is_mca(p1) or is_mca(p2):
        if not (is_mca(p1) and is_mca(p2)): raise RegionError("diff requires both files to be same kind")
        if args.cx is None or args.cz is None: raise RegionError("region diff requires --cx --cz")
        a=read_nbt(read_chunk_record(p1,args.cx,args.cz).nbt_raw).tag; b=read_nbt(read_chunk_record(p2,args.cx,args.cz).nbt_raw).tag
    else: a=load_dat(p1)[1].tag; b=load_dat(p2)[1].tag
    print(json.dumps(semantic_diff(a,b,limit=args.limit),ensure_ascii=False,indent=2))

def build_parser():
    ap=argparse.ArgumentParser(description="Minecraft Java 1.21.8 NBT/Anvil targeted inspection/edit tool")
    sub=ap.add_subparsers(dest="cmd",required=True)
    p=sub.add_parser("probe"); p.add_argument("file"); p.set_defaults(func=cmd_probe)
    p=sub.add_parser("tree"); p.add_argument("file"); p.add_argument("--cx",type=int); p.add_argument("--cz",type=int); p.add_argument("--path",default="/"); p.add_argument("--depth",type=int,default=4); p.add_argument("--max-children",type=int,default=40); p.set_defaults(func=cmd_tree)
    p=sub.add_parser("get"); p.add_argument("file"); p.add_argument("--cx",type=int); p.add_argument("--cz",type=int); p.add_argument("path"); p.add_argument("--max-array",type=int,default=64); p.set_defaults(func=cmd_get)
    p=sub.add_parser("export"); p.add_argument("file"); p.add_argument("--cx",type=int); p.add_argument("--cz",type=int); p.add_argument("--out"); p.set_defaults(func=cmd_export)
    p=sub.add_parser("region-list"); p.add_argument("file"); p.add_argument("--inspect-nbt",action="store_true"); p.set_defaults(func=cmd_region_list)
    p=sub.add_parser("region-check"); p.add_argument("file"); p.add_argument("--deep",action="store_true",help="also decompress and parse every present chunk NBT payload"); p.set_defaults(func=cmd_region_check)
    p=sub.add_parser("set"); p.add_argument("file"); p.add_argument("--cx",type=int); p.add_argument("--cz",type=int); p.add_argument("path"); p.add_argument("--type",required=True,choices=[x for x in NAME_TO_TAG if x not in ("end","list","compound")]); p.add_argument("--value",required=True); p.add_argument("--out",required=True); p.add_argument("--create",action="store_true"); p.add_argument("--allow-type-change",action="store_true"); p.add_argument("--preserve-timestamp",action="store_true"); p.add_argument("--force",action="store_true"); p.set_defaults(func=cmd_set)
    p=sub.add_parser("diff"); p.add_argument("a"); p.add_argument("b"); p.add_argument("--cx",type=int); p.add_argument("--cz",type=int); p.add_argument("--limit",type=int,default=500); p.set_defaults(func=cmd_diff)

    p=sub.add_parser("block-get"); p.add_argument("file"); p.add_argument("x",type=int); p.add_argument("y",type=int); p.add_argument("z",type=int); p.set_defaults(func=cmd_block_get)
    p=sub.add_parser("block-set"); p.add_argument("file"); p.add_argument("x",type=int); p.add_argument("y",type=int); p.add_argument("z",type=int); p.add_argument("--state",required=True,help='JSON, e.g. {"Name":"minecraft:stone"}'); p.add_argument("--out",required=True); p.add_argument("--blocks-report",help="Mojang 1.21.8 generated reports/blocks.json (preferred) or Prismarine 1.21.8 blocks.json"); p.add_argument("--allow-unvalidated-state",action="store_true",help="explicitly bypass registry/observed-state validation (unsafe)"); p.add_argument("--allow-new-block-entity",action="store_true",help="allow block types that normally require block-entity NBT; caller must coordinate NBT separately"); p.add_argument("--preserve-timestamp",action="store_true"); p.add_argument("--allow-existing-block-entity",action="store_true"); p.add_argument("--force",action="store_true"); p.set_defaults(func=cmd_block_set)
    return ap

def main()->int:
    try:
        args=build_parser().parse_args(); args.func(args); return 0
    except (NBTError,RegionError,FileNotFoundError,ValueError,struct.error,OSError) as e:
        print(f"ERROR: {e}",file=sys.stderr); return 2

if __name__=="__main__": raise SystemExit(main())
