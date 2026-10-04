# Android-home-screen-widget

[![Build APK](https://github.com/Dathaze20/Android-home-screen-widget/actions/workflows/build-apk.yml/badge.svg)](https://github.com/Dathaze20/Android-home-screen-widget/actions/workflows/build-apk.yml)

**Page Wallpaper** — put a different **photo, GIF or video** on every home screen page.
Set it up once; after that it runs by itself with the app closed.

Built for a Galaxy A17 / One UI, but nothing in it is Samsung-specific.

> [!WARNING]
> **Releases v1.0.1 to v1.0.5 are personal builds, not a public distribution channel.**
> They are signed with the debug keystore checked into this repository, whose password is the
> standard Android debug one. Anyone can sign an APK with that same identity, and Android would
> accept it as an *update* to an installed copy — inheriting its data. Those releases are kept so
> the history stays honest, but do not install them expecting the signature to mean anything.
> The public build, with a private release key and its own application ID, is still being set up.

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

## Yes, it is an APK

An APK is just the Android app file. Two ways to get one:

**Without a computer (easiest).** Every push to GitHub builds the APK automatically.
Go to the repo → **Actions** tab → click the newest **Build APK** run → scroll to **Artifacts** →
download **page-wallpaper-debug-apk** → unzip → tap the `.apk` on your phone.
Android will ask permission to install from an unknown source; allow it for your browser or
file manager.

**With a computer.** Open the project in Android Studio and press Run, or:
```
./gradlew assembleDebug
```
The APK lands in `app/build/outputs/apk/debug/`.

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
2. Open **Page Wallpaper**, tap **Choose photos**, and pick several at once. They land on page 1,
   2, 3 and so on in the order you picked them. Each tile says which home screen it is and gets a
   tick once filled. Tap a tile to change it, long-press to empty it.
3. Tap **Set as wallpaper** and confirm **Home screen**.
4. Swipe across your home screens.

That is the whole setup. One UI shows a single picture in its wallpaper preview and offers one
Home screen slot — that is normal, not a fault. You are installing one live wallpaper, and the
wallpaper itself decides which of your pictures to draw on each page.

### How it follows the pages

Two methods, in order of preference:

| Method | When it runs |
| --- | --- |
| **Offset** | The launcher reports where it has scrolled to, and the page falls out of that |
| **Samsung compatibility** | The launcher reports a fixed position, so the swipe itself is watched and the page stepped by hand |

One UI Home reports a fixed offset of 0.5 with no page step, so on a Galaxy the second method
does the work. It switches itself on; there is an Auto / Always on / Off setting behind the
settings icon to force it either way.

**Confirmed working on a Galaxy A17 running One UI**, five pages each showing a different photo.
Verified by the person this was built for, on their own phone — not by the author, who has no
device.

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

Your page assignments and settings survive the update. Every build is signed with the same key, so
the new APK installs over the old one and there is never any need to uninstall first.

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
rule (`UpdateVersionTest`), the release-asset matching (`UpdateAssetsTest`) and the tile
grid (`GridLayoutTest`). They run in CI before every
build; no APK is produced if they fail.

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

The public build therefore gets its own application ID and a private release key held in GitHub
Secrets. Each release carries one APK per build, named for which it is, and the in-app updater
picks the one matching its own build rather than the first file it finds — installing the other
one cannot work, because the application ID and the signing key both differ.

## License

[GPL-3.0](LICENSE). You may use, study, change and share this. If you distribute a modified
version, its source has to stay available under the same license — a fork can be sold, but it
cannot be closed.
