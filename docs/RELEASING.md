# Releasing

One app is published: the **public** build, `io.github.dathaze20.pagewallpaper`, signed with a
private key that lives in GitHub Secrets and nowhere else.

## Making a release

1. Make sure `main` is green.
2. Push a tag, `v<major>.<minor>.<patch>`:

   ```
   git tag -a v1.0.10 -m "Page Wallpaper v1.0.10" && git push origin v1.0.10
   ```

   Or, if pushing tags is awkward from where you are: **Actions → Release → Run workflow**, with
   the tag as the input. Either way the build happens on the commit the workflow checks out, and
   the release is created at that commit.

That is the whole procedure. `.github/workflows/release.yml` does the rest.

## What the workflow will not let you publish

Each of these stops the release rather than producing something questionable:

| Check | Why it exists |
| --- | --- |
| The tag is `vX.Y.Z`, minor and patch under 100 | The updater reads the tag back as a number; anything else is unreadable to it |
| All four signing secrets present, and the keystore actually opens with that password and alias | No fallback. The alternative to publishing with the real key is not publishing |
| The APK is signed, and **not** with the repository's debug key | That key is in this repository. Publishing an APK signed with it would let anyone build an update to it |
| The signing certificate is **exactly** `434c6877…49003898` | The fingerprint every public release has carried since v1.0.8. A different private key produces an APK that installed copies refuse outright — recoverable only by uninstalling, which deletes every saved page. Pinned in `EXPECTED_PUBLIC_CERT_SHA256` at the top of the workflow. Not a secret: it is readable from any published APK |
| `applicationId` is `io.github.dathaze20.pagewallpaper` | A different ID is a different app; installed copies would never see the update |
| Label is `Page Wallpaper` | |
| versionCode in the APK equals `major × 10000 + minor × 100 + patch` | The updater compares this number. If it disagrees with the tag, the app either misses the release or offers one it already runs |
| The Compose layout inspector is not packaged | Developer tooling, and megabytes of it |
| The APK is under 50 MB | A guard against the release variant silently reverting to a debug-shaped build |
| Unit tests ran, and at least one of them | A green Gradle task proves the task succeeded, not that anything executed |

## The one name that cannot change

```
page-wallpaper-public-v<major>.<minor>.<patch>.apk
```

`UpdateAssets.pick` looks for `-public-` in the asset name. Every copy of the app already
installed does this, including ones that will never be updated again if it stops matching — the
app would simply report that there is no update, with nothing anywhere saying why.

`UpdateAssetsTest` pins the exact shape. Change the naming step and that test fails, which is the
intent.

## Version codes

`major × 10000 + minor × 100 + patch`, computed identically in two places:

- `.github/workflows/release.yml`, in shell, to stamp the APK
- `UpdateVersion.codeFromTag`, in Kotlin, to read a tag back

`UpdateVersionTest` holds them together. They must never drift apart.

Note that `v1.0.9` and `v1.0.10` are `10009` and `10010`: as text `"1.0.10"` sorts *below*
`"1.0.9"`, and only the arithmetic gets the order right.

## Development builds

`.github/workflows/build-apk.yml` builds on every push and uploads
`page-wallpaper-public-debug-apk`. That APK carries a `.debug` application ID suffix and the debug
key, so it installs **beside** the real app rather than over it.

That suffix is load-bearing. A debug build holding the real application ID would occupy the
package name with the wrong signature, and the official release could then not be installed over
it at all — the only way out being an uninstall, which deletes every saved page. CI checks the
suffix is present on every run.

No release-variant APK is uploaded by that workflow. The only installable file that carries the
real identity is the one attached to a GitHub Release.

## The personal flavour

`com.dathaze.pagewall`, signed with the checked-in debug key. It still builds, and CI still
compiles it so it does not rot, but **it is no longer published**. Every release from v1.0.1 to
v1.0.9 carried it — on its own up to v1.0.7, beside the public APK in v1.0.8 and v1.0.9. Nothing
after that will.

Anyone still running it cannot be updated into the public app — different application ID,
different signing key — and has to move across with a backup zip. The README has the procedure.

## Historical releases

Left alone, with their original files. What each one actually attached:

| Release | Attached | Which app |
| --- | --- | --- |
| v1.0.1 – v1.0.5 | one unmarked APK, `page-wallpaper-vX.Y.Z.apk` | personal |
| v1.0.6 – v1.0.7 | `page-wallpaper-personal-vX.Y.Z.apk` only | personal |
| v1.0.8 – v1.0.9 | personal **and** public, one each | both |
| v1.0.10 onwards | `page-wallpaper-public-vX.Y.Z.apk` only | public |

There was no public build before v1.0.8. All three shapes are understood by `UpdateAssets.pick`:
an unmarked lone APK is claimed by the personal build only, a marked one by whichever build it
names, and a release with nothing for this build offers nothing rather than guessing.

The record of what was actually published stays as it was.
