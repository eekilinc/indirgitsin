"""Package tracked sources, including the pinned cipher submodule, without local secrets/build files."""
from pathlib import Path
import subprocess
import sys
import zipfile

root = Path(__file__).resolve().parents[1]
destination = Path(sys.argv[1]).resolve()
with zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED) as archive:
    for repository in (root, root / "cipher"):
        tracked = subprocess.check_output(["git", "-C", str(repository), "ls-files", "-z"]).decode().split("\0")
        for name in filter(None, tracked):
            path = repository / name
            if path.is_file():
                archive.write(path, "indir-gitsin-source/" + path.relative_to(root).as_posix())
print(f"Source archive: {destination.name}")
