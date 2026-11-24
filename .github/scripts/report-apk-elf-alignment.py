import argparse
import shutil
import subprocess
import tempfile
from pathlib import Path, PurePosixPath
from zipfile import ZipFile


def report(apk: Path) -> None:
    with ZipFile(apk) as archive, tempfile.TemporaryDirectory() as directory:
        libraries = sorted(
            name
            for name in archive.namelist()
            if PurePosixPath(name).parts[:-1] == ("lib", "arm64-v8a")
            and name.endswith(".so")
        )
        if not libraries:
            raise ValueError("APK has no ARM64 native libraries")

        binary = Path(directory) / "library.so"
        aligned = 0
        for name in libraries:
            with archive.open(name) as source, binary.open("wb") as destination:
                shutil.copyfileobj(source, destination)
            headers = subprocess.check_output(["readelf", "-lW", str(binary)], text=True)
            alignments = [
                int(line.split()[-1], 16)
                for line in headers.splitlines()
                if line.lstrip().startswith("LOAD ")
            ]
            if not alignments:
                print(f"::warning::{name} has no ELF LOAD segments")
            elif min(alignments) < 0x4000:
                print(f"::warning::{name} has an ELF LOAD alignment of {min(alignments):#x}")
            else:
                aligned += 1

        print(f"{aligned}/{len(libraries)} ARM64 libraries have 16 KB ELF alignment")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Report ARM64 ELF alignment in an APK")
    parser.add_argument("apk", type=Path)
    report(parser.parse_args().apk)
