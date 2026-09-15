# Android-home-screen-widget

**Page Wallpaper** — a different photo (and optionally a different song) on every home screen page,
running silently in the background once it is set up.

Built for a Galaxy A17 / One UI, but nothing in it is Samsung-specific.

---

## Can this actually be done? Short answer

| What you asked for | Possible? | How |
| --- | --- | --- |
| A different photo per home screen page | **Yes** | A live wallpaper that listens to the launcher's scroll position |
| Done silently, without opening an app | **Yes** | Set up once; after that it runs with the app closed |
| Done *with a widget* | **No** | A widget can only paint inside its own cell — it cannot touch the background |
| A song per page | **Yes, with limits** | Plays while you are on the home screen; stops when you open an app or the screen turns off |

### Why not a widget

A widget is a `RemoteViews` tree the launcher inflates inside one cell of its grid. It has no access
to the wallpaper surface, which sits in a separate window *underneath* the launcher. No public
Android API lets a widget, an accessibility service, or a background job change the wallpaper in
response to a swipe.

The wallpaper surface itself, though, gets told exactly where the launcher has scrolled to, via
`WallpaperService.Engine.onOffsetsChanged()`. That single callback is the whole mechanism, and a
live wallpaper is the only thing that receives it. So the app is a live wallpaper. A widget is
included too, but only as a convenience: it shows which page you are on and jumps into that page's
settings with one tap.

### Why the music has limits

Android will not let an app hold the speaker indefinitely from the background without a foreground
service and a permanent notification — and a wallpaper is not in a state where it can reliably
start one. Rather than fight that, the audio here is deliberately scoped to *while you are looking
at the home screen*: land on a page, its track starts; open an app or turn off the screen, it
stops. It also stands down entirely if something else is already playing, so swiping home in the
middle of a podcast does not start a turf war over the speaker.

That is off by default. Turn it on in Settings inside the app.

---

## Setting it up

1. Open the project in Android Studio (Ladybug or newer) and run it on the phone, or build an APK:
   ```
   ./gradlew assembleDebug
   ```
   The APK lands in `app/build/outputs/apk/debug/`.
2. Open **Page Wallpaper**, set how many home screen pages you have, and assign a photo to each.
3. Tap **Set as wallpaper** and confirm in the system picker.
4. **Turn on wallpaper scrolling in your launcher.** On One UI: long-press the home screen →
   *Settings* → enable **Wallpaper scrolling** (older versions call it the parallax effect).
   With that switch off, Android never reports a scroll position to *any* live wallpaper, and every
   page will show the same picture. The app detects this and says so on its front screen.

After step 4 there is nothing else to do. Swipe and the photo changes.

### Changing a photo later, without hunting for the app

- **Share it in.** Gallery → share a photo → **Page Wallpaper** → tap a page.
- **Tap the widget.** Drop the Page Wallpaper widget on the home screen; it opens the settings for
  whichever page you are standing on.
- **Double-tap the wallpaper.** On launchers that forward the gesture, a double tap on empty home
  screen space opens the same screen.

---

## How it works

```
Launcher scrolls
      │
      ▼
Engine.onOffsetsChanged(xOffset, xOffsetStep, …)
      │   page = round(xOffset / xOffsetStep)
      ▼
page changed? ──yes──▶ crossfade to that page's photo  ──▶ widget refresh
      │                 start that page's track (if enabled)
      └──no───────────▶ pan the current photo with your finger (parallax)
```

| File | Role |
| --- | --- |
| `wallpaper/PageWallpaperService.kt` | The live wallpaper. Turns scroll offsets into a page index, drives the crossfade, updates the widget |
| `wallpaper/PageRenderer.kt` | Draws a frame: center-crop to the screen, pan by the scroll offset, alpha-blend during a fade |
| `wallpaper/PageBitmapCache.kt` | Byte-budgeted LRU of decoded pages, so flicking across pages does not thrash |
| `data/PageStore.kt` | The page → media assignments, in SharedPreferences |
| `data/MediaImporter.kt` | Copies picked media into private storage, downsampling photos to display size |
| `audio/PageAudioController.kt` | Per-page playback, scoped to the wallpaper being visible |
| `ui/` | Compose setup screen and the share target |
| `widget/PageWidgetProvider.kt` | The home screen widget |

Picked photos are **copied** into the app's private storage rather than referenced by URI, so a
page keeps working after you delete the original from your gallery. They are downsampled to roughly
screen size on import — a 50 MP photo decoded at full size is about 200 MB in memory, and the
wallpaper process gets a small heap.

## Known limitations

- **Wallpaper scrolling must be on in the launcher.** This is the single most common reason a
  per-page wallpaper appears not to work. Not fixable from inside the app.
- **Page count comes from you, not the launcher.** Android reports a page *fraction*, and launchers
  disagree about how to count pages, so the app asks rather than guesses.
- **Audio does not continue in the background.** By design — see above.
- **The lock screen is a separate wallpaper.** This app only sets the home screen one.

## Status

Written against AGP 8.7 / Kotlin 2.0 / compileSdk 35, minSdk 26. It has **not** been compiled or
run on a device yet — the environment it was written in has no Android SDK. Expect to fix a stray
import or two on the first build.
