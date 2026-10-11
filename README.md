# Page Wallpaper

[![Build APK](https://github.com/Dathaze20/Android-home-screen-widget/actions/workflows/build-apk.yml/badge.svg)](https://github.com/Dathaze20/Android-home-screen-widget/actions/workflows/build-apk.yml)

**A different photo, GIF or video on every home screen page.**

Android lets you swipe between home screen pages but gives you one wallpaper for all of them.
Page Wallpaper gives each page its own. Set it up once; after that it runs by itself, with the
app closed.

- **Every page gets its own picture.** Photos, GIFs and videos, assigned page by page from a grid
  of your home screens.
- **It follows your swipes.** The wallpaper changes as you move between pages, crossfading
  between still pictures. Video pages cut straight in rather than fading, because the player
  owns the screen while a video is showing.
- **Samsung One UI is handled.** One UI reports a fixed scroll position, which defeats the method
  every other launcher allows, so the app watches the swipe itself instead. Built and used on a
  Galaxy A17, though nothing in it is Samsung-specific.
- **Backup and restore, with the media included.** One zip carries your pictures, videos and
  audio as well as the page assignments and settings — saved to Downloads, checked as it is
  written, and restored without overwriting anything already on a page.
- **It updates itself from GitHub.** **Menu → Settings → Check for updates** finds the newest
  release, downloads the APK and hands it to Android's installer. No store account, and nothing
  to uninstall.

One app, one APK, one update channel: **`io.github.dathaze20.pagewallpaper`**, signed with a
private release key.

## Download

**[⬇ Latest release](https://github.com/Dathaze20/Android-home-screen-widget/releases/latest)** — grab the APK on your phone and tap it.

**One file, one app, one update channel.**

| File | Application ID | Signed with | Who it is for |
| --- | --- | --- | --- |
| `page-wallpaper-public-vX.Y.Z.apk` | `io.github.dathaze20.pagewallpaper` | a private release key, held in GitHub Secrets | **everyone, including the maintainer** |

That is the whole download. Install it, and **Check for updates** inside the app keeps it current
from then on.

<details>
<summary><b>Earlier releases published a different app. If you installed one, read this.</b></summary>

What each release actually attached:

| Release | Attached | Which app |
| --- | --- | --- |
| v1.0.1 – v1.0.5 | one unmarked APK, `page-wallpaper-vX.Y.Z.apk` | personal (`com.dathaze.pagewall`) |
| v1.0.6 – v1.0.7 | `page-wallpaper-personal-vX.Y.Z.apk` only | personal |
| v1.0.8 – v1.0.9 | `page-wallpaper-personal-…` **and** `page-wallpaper-public-…` | both, side by side |
| **v1.0.10 onwards** | `page-wallpaper-public-vX.Y.Z.apk` only | **public** (`io.github.dathaze20.pagewallpaper`) |

So there was no public build at all before v1.0.8. Everything up to v1.0.7 is the personal build,
signed with the debug keystore checked into this repository — a key anyone can sign an APK with.

If what you installed is the personal build, it **cannot update into the public app**, and no
future release will offer it anything. The two have different application IDs and different
signing keys, so Android treats them as unrelated apps — which is exactly what they are. Nothing
is lost, but moving across is a copy, not an update: see
[Moving from a personal build](#moving-from-a-personal-build-to-the-public-app).

Every past release is left exactly as it was published, files included, so the record stays
honest. None of them is the app to install today.
</details>

---

## The simple explanation

Your home screen has several pages. You swipe between them. This app puts something different
behind each one.

It is **not** a widget. A widget is a little box the launcher draws *on top of* your home screen —
it can only paint inside its own square, so it can never change the background. The background is
a separate layer *underneath* everything, and the only kind of app allowed to draw there is a
**live wallpaper**.

Luckily, a live wallpaper gets told something no other app is told: exactly how far you have
scrolled sideways. That one piece of information is the whole trick.

```
You swipe ──▶ Android tells the wallpaper "you are 50% across"
                          │
                          ▼
              "50% across means page 2"
                          │
                          ▼
              show whatever is assigned to page 2
```

So: the app is a live wallpaper. There *is* a widget in here too, but it is only a shortcut — it
shows which page you are on and jumps you to that page's settings in one tap.

---

## What it looks like

The app is one screen: your home screen pages as a grid of numbered tiles, each showing what is
assigned to it, with the page you are on right now marked **NOW**. Tap a tile to change that
page, long-press to clear it. Buttons underneath open the photo picker and the settings, and the
menu at the top left holds the rest — page count, photo fit, crossfade, backup and restore, the
diagnostics panel, and the update check.

<!--
SCREENSHOTS — four real photographs of the app running on a phone. Not mock-ups, not renders.

docs/SCREENSHOTS.md lists which four to take, what each one has to show, and what to check
before publishing pictures of your own home screen. Once the files are in docs/screenshots/
under the exact names below, delete the two comment markers around this block to publish it.

Nothing below is linked while the files are absent, so the README never shows a broken image.

| | |
| --- | --- |
| ![The page grid](docs/screenshots/01-page-grid.png) | ![Settings](docs/screenshots/02-settings.png) |
| **Every home screen, as a tile.** Tap one to change it, long-press to clear it. | **The menu**, behind the button at the top left. |
| ![Home screen page 1](docs/screenshots/03-home-page-1.png) | ![Home screen page 2](docs/screenshots/04-home-page-2.png) |
| **Home screen, page 1.** | **Page 2 — a different wallpaper, same home screen.** |
-->

---

## Installing it on your phone

You do not need a computer.

1. On your phone, open the **[latest release](https://github.com/Dathaze20/Android-home-screen-widget/releases/latest)**.
2. Under **Assets**, tap **`page-wallpaper-public-vX.Y.Z.apk`** to download it.
3. Open the download. Android will say it cannot install from this source — tap **Settings** on
   that prompt and allow your browser or file manager to install apps. This prompt appears for
   anything not from the Play Store; it is not a warning about this app in particular.
4. Tap **Install**.

After the first install, the app updates itself: **Menu → Settings → Check for updates**.

### Moving from a personal build to the public app

Only if you installed `page-wallpaper-personal-*.apk` from an older release. Android will not let
the public APK update it — the application IDs and the signing keys differ, so they are two
unrelated apps — but nothing has to be lost. Your pages travel in a backup zip.

1. **In the old app:** **Menu → Settings → Export backup to Downloads.** It writes
   `page-wallpaper-backup-YYYY-MM-DD.zip` and verifies it before telling you it saved. The zip
   holds the actual photos, GIFs, videos, poster frames and audio, plus your page assignments and
   settings — not just a list of file names.
2. **Keep a second copy.** Share it to Drive, or to anywhere off the phone. One tap, and the
   backup stops depending on this phone surviving.
3. **Install the public APK.** It arrives as a *separate* app. **Do not uninstall the old one
   yet** — its photos live in its own private storage and uninstalling deletes them.
4. **In the new app:** **Menu → Settings → Import backup**, pick the zip. Every file is checked
   against its recorded size and SHA-256, and you are shown what restoring would do before it
   does anything. Tapping Cancel here changes nothing, and is a way to confirm a backup is
   readable.
5. **Restore.** Empty pages are filled; nothing already there is replaced unless you ask.
6. **Set the new app as your wallpaper** and check every page.
7. **Only then**, if you want to, uninstall the old app.

The backup format is identical in both builds and records no application ID it reads back, so the
same zip restores either way round. Both apps can stay installed indefinitely; they do not
interfere with each other, though only one can be the live wallpaper at a time.

### Building it yourself

The project has two product flavours, so the Gradle tasks name one:

```
./gradlew assemblePublicDebug      # the public build
./gradlew assemblePersonalDebug    # the maintainer's build
```

The APK lands in `app/build/outputs/apk/<flavour>/debug/`. Release variants
(`assemblePublicRelease`) need signing credentials that are not in this repository; the public
one is built only by CI, from GitHub Secrets.

Every push also builds an APK in Actions — repo → **Actions** → newest **Build APK** run →
**Artifacts** → `page-wallpaper-public-debug-apk`. That is a **development build**: its
application ID carries a `.debug` suffix and it is signed with the debug key, so it installs
*beside* the real app instead of on top of it and can never be mistaken for a release. Keeping
them separate is the point — a debug build holding the real package name would block the official
APK from installing at all. No release-variant APK is uploaded by that workflow; releases come
only from `release.yml`, and only from the private key.

---

## Yes to photos, GIFs and videos

| You pick | What happens |
| --- | --- |
| **Photo** (JPEG, PNG, HEIC) | Shrunk to screen size on import, drawn whole over a blurred copy of itself (see Photo fit), fades between pages |
| **GIF** (or animated WebP) | Copied as-is and looped on the page |
| **Video** (MP4, and whatever else your phone records) | Looped and center-cropped, silent by default. Photo fit does not apply: MediaPlayer draws video straight onto the surface, so it always crops to fill |

One picker covers all three — Android's built-in photo picker, which needs no storage permission.

Three things worth knowing about video pages:

- **They cut instead of fading.** A screen can be painted by a canvas *or* by a video decoder,
  never both at once. When you land on a video page the video player takes over the whole screen,
  so there is no way to blend it with the page you came from.
- **They use more battery** than a photo. There is an **Animate GIFs and videos** switch in
  Settings: turn it off and everything holds on its first frame, which costs nothing.
- **Keep them short.** A few looping seconds looks good and stays small. Anything over 250 MB is
  rejected with a message rather than silently eating your storage.

Sound from a video is muted unless you switch it on.

---

## Setting it up

1. Install the APK.
2. Open **Page Wallpaper**, tap **Change photos**, and pick several at once. They land on page 1,
   2, 3 and so on in the order you picked them. Each tile says which home screen it is and gets a
   tick once filled. Tap a tile to change it, long-press to empty it.
3. Tap **SET WALLPAPER** and confirm **Home screen**.
4. Swipe across your home screens.

That is the whole setup. One UI shows a single picture in its wallpaper preview and offers one
Home screen slot — that is normal, not a fault. You are installing one live wallpaper, and the
wallpaper itself decides which of your pictures to draw on each page.

### Backup and restore

Everything you assign is copied into the app's own private storage, where nothing else on the
phone can reach it. That is what keeps a page working after you delete the original from your
gallery — and it is why, if the app goes, your pages go with it. One file is the answer.

**To make one:** open the menu at the top left and tap **Export backup to Downloads**. It writes
`page-wallpaper-backup-YYYY-MM-DD.zip` into your Downloads folder, and checks that the file reads
back correctly before it tells you it saved. The zip holds the **actual photos, GIFs, videos,
poster frames and per-page audio**, together with the page assignments and your settings — not a
list of file names. No storage permission is needed on Android 10 and up.

**Keep a second copy off the phone.** A backup that exists only on the device it came from
protects you against very little. Open the zip in your **Files** (or **My Files**) app, tap
**Share**, and send it to **Google Drive** — or anywhere else you keep things. Import can read it
straight back out of Drive later, without downloading it first.

**To restore:** menu → **Import backup**, and pick the zip. Every file inside is checked against
its recorded size and checksum, and you are shown what restoring would do *before* anything is
written. Tapping **Cancel** at that point changes nothing — which is also how to confirm an old
backup is still readable.

**Nothing you already have is replaced unless you choose it.** Empty pages are filled without
asking. For any page that already has something on it, the dialog offers a choice and arrives on
**Keep my pages** — *"Only empty pages are filled. Nothing you have is touched."* Picking **Use
the backup's pages** is the only way to overwrite them. **Restore settings too** is a separate
tick covering crossfade, photo fit, page count and the rest; your diagnostics are never restored.

The same file restores onto a new phone or a fresh install. If you are coming from one of the
older personal builds, see
[Moving from a personal build](#moving-from-a-personal-build-to-the-public-app).

### How it follows the pages

Two methods, in order of preference:

| Method | When it runs |
| --- | --- |
| **Offset** | The launcher reports where it has scrolled to, and the page falls out of that |
| **Samsung compatibility** | The launcher reports a fixed position, so the swipe itself is watched and the page stepped by hand |

One UI Home reports a fixed offset of 0.5 with no page step, so on a Galaxy the second method
does the work. It switches itself on; there is an Auto / Always on / Off setting behind the
settings icon to force it either way.

**Confirmed working on a Galaxy A17 running Android 16 / One UI**, five pages each showing a
different photo, tested by the maintainer on their own phone. Anything in this README that is
*not* marked as device-tested has been checked only by the unit tests and the CI build.

### Photo fit

**Full image** (default) shows the whole picture at its own aspect ratio, nothing cropped, over a
blurred darkened copy of itself. A wide picture on a tall phone cannot be complete, undistorted
*and* reach all four corners, so the blurred copy fills the rest instead of black bars.

**Fill screen** zooms until the picture covers every corner, cutting off what does not fit.

### The screen itself

Every home screen page is a tile in a grid sized to the display, so there is nothing to scroll —
you see all your pages and what is on each at once.

### How many pages you have

Set behind the settings icon. The launcher's own count is used when it reports one, but One UI
does not, so the number you set is what your pictures are divided across. If pictures land on the
wrong screens, adjust that number first.

If your launcher sweeps only part of the scroll range, **Set left edge** / **Set right edge**
calibrate it: stand on the leftmost home screen and tap the first, the rightmost and tap the
second.

### Changing a page later, without hunting for the app

- **Share it in.** Gallery → share a photo or video → **Page Wallpaper** → tap a page.
- **Tap the widget.** Drop the Page Wallpaper widget on your home screen; it opens the settings for
  whichever page you are standing on.
- **Double-tap the wallpaper.** On launchers that pass the gesture along, a double tap on empty
  home screen space opens the same screen.

### Updating the app

Open **Settings** (the icon at the top of the app) and the first row is **Check for updates**. It
asks GitHub for the newest release, and if there is one newer than the build you are running it
offers **Download and install**. The APK is downloaded inside the app and handed to Android's own
installer, which still asks you to confirm — nothing installs silently. The first time, Android
will want permission to install apps from Page Wallpaper; the sheet has a button that opens that
switch.

Your page assignments, wallpapers and settings survive the update. Every public release is signed
with the same private key, so the new APK installs over the old one and there is never any need to
uninstall first. Uninstalling **would** lose your pages — the photos live in the app's own private
storage, where nothing else on the phone has a copy. Export a backup first if you ever need to.

Only the public build is published, so there is one release to check and one file in it.

Releases come from a tag: pushing `v1.0.1` runs `.github/workflows/release.yml`, which turns the
tag into a versionCode (`major × 10000 + minor × 100 + patch`, so `v1.0.1` is `10001`), builds the
APK with that number baked in, and publishes a GitHub Release with it attached. `UpdateVersion`
reads the tag back by the same rule — that agreement is what lets the app tell "newer" from
"same", so the two must never drift apart.

---

## How it works, for the curious

The engine runs in one of two modes, because a screen surface can only have one owner:

| Mode | Used for | How |
| --- | --- | --- |
| **Canvas** | Photos, GIFs, and video poster frames | The engine locks the surface and draws, with crossfade and parallax |
| **Video** | Video pages, when motion is on | `MediaPlayer` is handed the surface and the engine stops drawing |

Swiping between a photo page and a video page swaps modes: the player releases the surface, then
the engine starts drawing again.

| File | Role |
| --- | --- |
| `wallpaper/PageWallpaperService.kt` | The live wallpaper. Turns scroll offsets into a page index, picks the mode, drives crossfades, updates the widget |
| `wallpaper/PageRenderer.kt` | Draws a frame: fit the whole picture over a blurred backdrop, or scale to cover when Fill screen is chosen, alpha-blending during a fade |
| `wallpaper/PageMediaCache.kt` | Decodes via `ImageDecoder`, so a still photo and an animated GIF come back as the same kind of object |
| `wallpaper/VideoPageController.kt` | Owns `MediaPlayer` and the surface while a video page is showing |
| `data/PageStore.kt` | Page assignments, in SharedPreferences |
| `data/MediaImporter.kt` | Copies media into private storage; shrinks photos, leaves GIFs and videos untouched, saves a poster frame for videos |
| `audio/PageAudioController.kt` | Optional per-page song (off by default) |
| `ui/HomeScreen.kt` | The whole app: a grid of page tiles, with the pickers |
| `ui/GridLayout.kt` | How many columns the tiles get; pure arithmetic, so it is unit tested |
| `ui/SettingsSheet.kt` | The menu, behind the button at the top left |
| `widget/PageWidgetProvider.kt` | The widget |
| `update/UpdateVersion.kt` | The tag-to-versionCode rule and the update states; no Android imports, so it is unit tested |
| `update/AppUpdater.kt` | Asks GitHub for the latest release, downloads the APK, hands it to the installer |
| `update/UpdateAssets.kt` | Picks the release asset belonging to this build; pure, so it is unit tested |
| `wallpaper/FrameGate.kt` | Whether the engine may touch the surface or paint it; pure, so it is unit tested |
| `backup/BackupManager.kt` | Export to Downloads and restore from a picked file; the Android-shaped half |
| `backup/BackupArchive.kt` | The zip itself: writing, hashing, verifying, extracting; pure, so it is unit tested |
| `backup/BackupPlan.kt` | What a restore would change, worked out before anything is written; pure |
| `backup/MediaCommit.kt` | Puts restored files in place without overwriting or deleting anything; pure |

Everything you pick is **copied** into the app's own storage rather than linked by URI, so a page
keeps working after you delete the original from your gallery.

## Known limitations

- **One UI has no per-page wallpaper of its own.** It treats the whole home area as one wallpaper
  target, which is why this has to be a live wallpaper swapping its own picture.
- **Samsung compatibility mode needs the launcher to forward touches.** The diagnostics panel's
  `touch reports` line says whether yours does.
- **No crossfade to or from a video page**, and no parallax on video: MediaPlayer owns the whole
  surface while a video page is showing.
- **The lock screen is a separate wallpaper.** This app sets the home screen one only.

## Diagnostics

Behind the settings icon, a panel reports exactly what the launcher is doing: raw offset and
step, the widest range ever seen, both page counts, which detection mode is running, how many
touch events have arrived and the last swipe recognised. A screenshot of it is enough to diagnose
a phone whose pictures are not changing.

## Status

AGP 9.4 / Kotlin 2.4 / Gradle 9.8 / compileSdk 37, targetSdk 35, minSdk 28.

Unit tests cover the page arithmetic (`PageMathTest`), the swipe thresholds (`SwipeMathTest`), the
resync rules (`SyncPolicyTest`), the gesture tracker (`GestureTrackerTest`), the update version
rule (`UpdateVersionTest`), the release-asset matching (`UpdateAssetsTest`), the tile grid
(`GridLayoutTest`), the draw gate (`FrameGateTest`), and the whole backup format — the archive
and its verification (`BackupArchiveTest`), the manifest (`BackupManifestTest`), what a restore
would change (`BackupPlanTest`), which settings may travel (`BackupSettingsTest`), putting files
in place without losing any (`MediaCommitTest`), refusing an incomplete export
(`BackupExportTest`), and which pages a backup must carry (`PageSelectionTest`, `PageWriteTest`)
— **149 tests in all**. They run in CI against the public variant before every build; no APK is
produced if they fail.

Every screen is built as a fixed bar, a scrolling middle and a pinned bar, and tiles are sized
from their own width rather than from leftover space. That is a rule, not a style: an earlier
layout put the setup screen's only exit at the bottom of a column that did not scroll, so on a
phone with the display font turned up there was no way out of the app at all.

## Signing

The personal build is signed with a fixed debug key checked into the repo, so each build installs
over the last instead of forcing an uninstall. That key is public by definition, which is fine for
one person's own phone and not fine for strangers: on Android the signing key *is* the app's
identity, so a publicly known key means anyone can build something Android will accept as an
update to it.

The public build therefore has its own application ID, `io.github.dathaze20.pagewallpaper`, and a
private release key held in GitHub Secrets and never committed. It first appeared in **v1.0.8**,
alongside the personal APK, and the updater picked whichever matched its own build. From
**v1.0.10** it is the only build published, and the personal flavour is a development target that
nobody downloads.

The release workflow **cannot publish without the private key**. There is no fallback to the
debug-signed build: a missing, unreadable or wrong-password keystore fails the release outright,
before anything is built. Falling back would mean handing everyone an APK signed with a key
printed in this repository.

Before anything is attached to a release, CI checks the APK's signing certificate **against the
exact fingerprint every public release has carried since v1.0.8**, checks it is not the
repository's debug key, checks the application ID, checks the label, checks the versionCode
matches the tag, and checks the layout inspector is not packaged. Pinning the fingerprint rather
than just requiring one is deliberate: an APK signed with some *other* private key is not an
update to this app at all — Android refuses it, and the only way a user could take it is by
uninstalling first, which deletes every saved page. The fingerprint is not a secret; it can be
read out of any published APK. A split that fails
open is worse than no split, so it fails closed instead.

Debug builds carry a `.debug` application ID suffix, so a development APK installs beside the real
app and can never take its package name. Release builds are untouched by that — what is published
keeps the identity every installed copy already trusts.

The whole procedure, and every check that can stop a release, is written down in
**[docs/RELEASING.md](docs/RELEASING.md)**.

## License

[GPL-3.0](LICENSE). You may use, study, change and share this. If you distribute a modified
version, its source has to stay available under the same license — a fork can be sold, but it
cannot be closed.
