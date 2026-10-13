"""Compare two mod jars class by class (used to confirm which upstream commit a release jar came from).

usage: compare_jars.py <ours.jar> <theirs.jar>   (prints a Markdown summary)
"""
import hashlib
import sys
import zipfile


def entries(path):
    with zipfile.ZipFile(path) as z:
        return {n: hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if not n.endswith("/")}


ours, theirs = entries(sys.argv[1]), entries(sys.argv[2])
classes = lambda d: {n for n in d if n.endswith(".class")}
common = classes(ours) & classes(theirs)
same = sorted(n for n in common if ours[n] == theirs[n])
diff = sorted(n for n in common if ours[n] != theirs[n])
only_ours = sorted(classes(ours) - classes(theirs))
only_theirs = sorted(classes(theirs) - classes(ours))
other_diff = sorted(n for n in (set(ours) & set(theirs)) - common if ours[n] != theirs[n])

print(f"### `{sys.argv[1].split('/')[-1]}` vs `{sys.argv[2].split('/')[-1]}`")
print(f"- classes identical: **{len(same)}** / {len(common)} common")
print(f"- classes differing: **{len(diff)}**, only in ours: {len(only_ours)}, only in release: {len(only_theirs)}")
print(f"- other differing files (resources, manifest): {len(other_diff)}")
for title, items in (("differing classes", diff), ("only in ours", only_ours),
                     ("only in release", only_theirs)):
    if items:
        print(f"\n<details><summary>{title} ({len(items)})</summary>\n")
        print("\n".join(f"- `{n}`" for n in items[:200]))
        print("\n</details>")
