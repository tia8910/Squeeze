"""Fails when a native library in an app bundle cannot load on 16 KB page devices.

Google Play rejects bundles whose 64-bit .so files have an ELF LOAD segment aligned below
16 KB (2**14). Usage: python3 tools/check_16kb.py path/to/app.aab
Prints one line per library; exits 1 if any is misaligned.
"""
import struct
import sys
import zipfile

PAGE = 1 << 14


def load_alignments(elf: bytes):
    if elf[:4] != b"\x7fELF" or elf[4] != 2:  # 64-bit only; Play checks 64-bit ABIs
        return []
    endian = "<" if elf[5] == 1 else ">"
    phoff = struct.unpack_from(endian + "Q", elf, 0x20)[0]
    phentsize, phnum = struct.unpack_from(endian + "HH", elf, 0x36)
    aligns = []
    for i in range(phnum):
        off = phoff + i * phentsize
        p_type = struct.unpack_from(endian + "I", elf, off)[0]
        if p_type == 1:  # PT_LOAD
            aligns.append(struct.unpack_from(endian + "Q", elf, off + 0x30)[0])
    return aligns


def main(path: str) -> int:
    bad = []
    with zipfile.ZipFile(path) as bundle:
        for name in sorted(n for n in bundle.namelist() if n.endswith(".so")):
            aligns = load_alignments(bundle.read(name))
            if not aligns:
                continue
            ok = min(aligns) >= PAGE
            print(f"{'ok ' if ok else 'BAD'} {name} (LOAD align {min(aligns)})")
            if not ok:
                bad.append(name.rsplit("/", 1)[-1])
    for lib in bad:
        print(f"::error::{lib} is not 16 KB aligned; update the library that ships it")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
