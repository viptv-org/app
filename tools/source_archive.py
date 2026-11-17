from pathlib import Path


def extract_source_archive(archive, destination):
    root = Path(destination).resolve()
    entries = archive.getmembers()
    for entry in entries:
        target = (root / entry.name).resolve()
        if target != root and root not in target.parents:
            raise ValueError("Source archive path escapes its destination")
        if not entry.isfile() and not entry.isdir():
            raise ValueError("Source archive contains a non-file entry")
    archive.extractall(root, entries)
