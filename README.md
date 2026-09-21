# Android-home-screen-widget

[![Build APK](https://github.com/Dathaze20/Android-home-screen-widget/actions/workflows/build-apk.yml/badge.svg)](https://github.com/Dathaze20/Android-home-screen-widget/actions/workflows/build-apk.yml)

**Page Wallpaper** — put a different **photo, GIF or video** on every home screen page.
Set it up once; after that it runs by itself with the app closed.

Built for a Galaxy A17 / One UI, but nothing in it is Samsung-specific.

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
| **Photo** (JPEG, PNG, HEIC) | Shrunk to screen size on import, drawn with center-crop, fades between pages, drifts slightly as you swipe |
| **GIF** (or animated WebP) | Copied as-is and looped on the page |
| **Video** (MP4, and whatever else your phone records) | Looped and center-cropped, silent by default |

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
| `wallpaper/PageRenderer.kt` | Draws a frame: scale to cover, pan by the scroll offset, alpha-blend during a fade |
| `wallpaper/PageMediaCache.kt` | Decodes via `ImageDecoder`, so a still photo and an animated GIF come back as the same kind of object |
| `wallpaper/VideoPageController.kt` | Owns `MediaPlayer` and the surface while a video page is showing |
| `data/PageStore.kt` | Page assignments, in SharedPreferences |
| `data/MediaImporter.kt` | Copies media into private storage; shrinks photos, leaves GIFs and videos untouched, saves a poster frame for videos |
| `audio/PageAudioController.kt` | Optional per-page song (off by default) |
| `ui/HomeScreen.kt` | The whole app: a non-scrolling grid of page tiles, with the pickers |
| `ui/SettingsSheet.kt` | The knobs, behind one icon |
| `widget/PageWidgetProvider.kt` | The widget |

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

AGP 8.7 / Kotlin 2.0 / compileSdk 35, minSdk 28.

Unit tests cover the page arithmetic (`PageMathTest`) and the swipe thresholds (`SwipeMathTest`)
and run in CI before every build; no APK is produced if they fail.

Signed with a fixed debug key checked into the repo, so each build installs over the last instead
of forcing an uninstall. A debug key with the standard debug password, not a release key.
