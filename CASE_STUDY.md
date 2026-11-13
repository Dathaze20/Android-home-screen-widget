# Case Study: Page Wallpaper

*Facts below describe **v1.0.13** (November 2026). Test counts will change as the app does; the [README](README.md) is always current.*

## The problem

Android lets you swipe between home screen pages but gives every page the same wallpaper. I wanted a different picture behind each page. Samsung's One UI, on my Galaxy A17, offers nothing for this.

## The solution

A live wallpaper that tracks which home screen page you are on and draws a different photo, GIF or video behind each one. You assign media from a grid of your pages, and after setup it runs by itself with the app closed. A home screen widget jumps to the current page's settings. A ZIP backup carries the actual media along with the page assignments.

## The engineering challenges

- **Samsung reports nothing useful.** One UI tells a live wallpaper it is always at the same scroll position, which defeats the standard method. The app falls back to watching the swipe itself, and switches modes automatically.
- **Photos and video cannot share the screen.** A screen surface is painted either by drawing code or by the video player, never both, so the app swaps between two modes as you swipe.
- **Backups that cannot lose data.** Every file is checked against its recorded size and SHA-256. Restoring shows what will change before it writes anything and never overwrites a page unless asked.
- **Release safety.** The public app is signed with a private key held in GitHub Secrets. CI checks every release against a pinned certificate fingerprint and refuses to fall back to the public debug key.
- **An app ID change handled honestly.** Early releases shipped a personal build. The README documents which release contained which app and how to move across with a backup.

## The development process

I direct the development with AI tools, mainly Claude Code, from my Samsung Galaxy A17. I test every release on that phone, and the repository history shows the AI involvement openly.

**An external bug report.** On October 28, 2026 a user with a Sony Xperia 10 VII reported ([#7](https://github.com/Dathaze20/Android-home-screen-widget/issues/7)) that a single tap on the home screen opened the app, which blocked Sony's own long-press menu. I don't own that phone. The investigation found that the app treated any wallpaper tap from the launcher as a double tap, and Sony's launcher sends one for every tap. The fix shipped the next day ([#8](https://github.com/Dathaze20/Android-home-screen-widget/pull/8)): the shortcut is now off unless switched on, and a test pins the behaviour.

I left the issue open on purpose. The fix is reasoned from the code and the report, but it has not been confirmed on the reporter's phone, so it isn't marked as resolved.

## The results

- **Confirmed on hardware:** a Galaxy A17 running Android 16 and One UI, with five pages each showing a different photo.
- **Reported by an outside user:** page switching working on a Sony Xperia 10 VII. The tap fix is awaiting their confirmation.
- 209 automated tests at v1.0.13. CI runs them before every build, and no APK is produced if they fail.
- The app tells users when an update exists and installs it after they confirm.

## The lessons

- Software used by other people meets devices you don't have. Clear diagnostics and a precise bug report made a fix possible without the phone.
- A comment in the code was wrong about what Android sends. Pinning behaviour with tests is more reliable than trusting a comment.
- Not claiming a fix is confirmed until it is.

## The next steps

- Reporter confirmation for issue #7.
- Real screenshots of the page grid and two home screen pages ([docs/SCREENSHOTS.md](docs/SCREENSHOTS.md) lists exactly which).
- Wider device testing beyond Samsung and the one Sony report.
