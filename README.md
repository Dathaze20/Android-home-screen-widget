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
   2, 3 and so on in the order you picked them.
3. Tap **Set as wallpaper** and confirm.
4. **Turn on wallpaper scrolling in your launcher.** On One UI: long-press the home screen →
   *Settings* → enable **Wallpaper scrolling** (older versions call it the parallax effect).

Step 4 is the one that catches everybody. With that switch off, Android never tells *any* live
wallpaper where you scrolled to, so every page shows the same thing. The app watches for this and
says so instead of just looking broken.

After that, you are done. Swipe and it changes.

### The screen itself

Every home screen page is a tile in a grid that is sized to the display, so there is nothing to
scroll — you see all your pages and what is on each at once. **Tap a tile** to change that page,
**long-press** to empty it. Settings live behind the icon in the corner, because you touch them
roughly never.

### How many pages you have

The app works it out for you, with one catch: it can only do so **after** the wallpaper is
running. The launcher reports how wide one page is as a fraction of the scroll range, and the page
count falls out of that — but nothing tells an app before then, so until you have applied the
wallpaper and swiped once, the app says it is guessing. After that it corrects itself. There is a
manual override in settings for a launcher that reports something odd.

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

- **Wallpaper scrolling must be on in the launcher.** The single most common reason a per-page
  wallpaper appears not to work, and not fixable from inside the app.
- **Page count comes from you, not the launcher.** Android reports a scroll *fraction*, and
  launchers disagree about how to count pages, so the app asks rather than guesses.
- **No crossfade to or from a video page**, and no parallax on video — see above.
- **The lock screen is a separate wallpaper.** This app sets the home screen one only.

## Status

AGP 8.7 / Kotlin 2.0 / compileSdk 35, minSdk 28 (needed for the GIF decoder).

**Compiles cleanly** — the GitHub Actions workflow builds a debug APK on every push, and the
badge above reflects the latest run.

**Not yet run on a phone.** Everything below this line is still unverified against real hardware,
and these are the parts most likely to need a tweak:

- whether One UI's launcher reports scroll offsets the way the page maths expects
- whether `SCALE_TO_FIT_WITH_CROPPING` crops video the way it should on this device
- how cleanly the surface hands back and forth between MediaPlayer and the canvas when you swipe
  between a video page and a photo page

If something looks wrong on the phone, that list is where to look first.
