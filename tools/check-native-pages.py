"""Audit ELF PT_LOAD segments and APK ZIP alignment for 16 KB Android devices."""
import argparse
import json
from pathlib import Path
import struct
from zipfile import ZipFile, ZIP_STORED


def audit(apk):
    results = []
    with ZipFile(apk) as archive, open(apk, 'rb') as raw:
        for entry in archive.infolist():
            if not entry.filename.startswith('lib/') or not entry.filename.endswith('.so'):
                continue
            data = archive.read(entry)
            if data[:4] != b'\x7fELF' or data[4] not in (1, 2):
                raise ValueError('Invalid ELF: ' + entry.filename)
            bits = 64 if data[4] == 2 else 32
            endian = '<' if data[5] == 1 else '>'
            if bits == 64:
                offset = struct.unpack_from(endian + 'Q', data, 32)[0]
                size, count = struct.unpack_from(endian + 'HH', data, 54)
            else:
                offset = struct.unpack_from(endian + 'I', data, 28)[0]
                size, count = struct.unpack_from(endian + 'HH', data, 42)
            segments = []
            for i in range(count):
                pos = offset + i * size
                if struct.unpack_from(endian + 'I', data, pos)[0] != 1:
                    continue
                if bits == 64:
                    file_offset, address = struct.unpack_from(endian + 'QQ', data, pos + 8)
                    alignment = struct.unpack_from(endian + 'Q', data, pos + 48)[0]
                else:
                    file_offset, address = struct.unpack_from(endian + 'II', data, pos + 4)
                    alignment = struct.unpack_from(endian + 'I', data, pos + 28)[0]
                segments.append(alignment >= 16384 and file_offset % 16384 == address % 16384)
            raw.seek(entry.header_offset + 26)
            name_length, extra_length = struct.unpack('<HH', raw.read(4))
            data_offset = entry.header_offset + 30 + name_length + extra_length
            zip_ok = entry.compress_type != ZIP_STORED or data_offset % 16384 == 0
            results.append(dict(library=entry.filename, bits=bits, elf_16kb=bool(segments) and all(segments), zip_16kb=zip_ok))
    return results


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('apk', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    report = audit(args.apk)
    result = json.dumps(report, indent=2)
    print(result)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(result + '\n', encoding='utf-8')
    if any(item['bits'] == 64 and not (item['elf_16kb'] and item['zip_16kb']) for item in report):
        raise SystemExit('64-bit native library is not 16 KB aligned')
