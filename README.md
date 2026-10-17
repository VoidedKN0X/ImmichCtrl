# ImmichCtrl

A full Android app for self-hosted [Immich](https://immich.app/) photo servers, part of the VKNXWorks Ctrl series (CraftyCtrl, FrigateCtrl, SeerrCtrl, JellyCtrl, FilesCtrl, NaviCtrl, BeamCtrl). Android 13+. Works with Immich 2.x and 3.x.

## Features

**Photos**
- Your whole timeline, newest first, grouped by day or month; months load as you scroll so huge libraries open instantly
- ThumbHash blur placeholders while thumbnails load
- Pinch the grid for 2 to 7 photos per row
- Fast-scroll thumb with a month bubble
- Long-press to select, keep dragging to select a run (the grid scrolls at the edges); select a whole day from its header
- Memories ("On this day") along the top
- Partners' photos in your timeline (optional)
- Pull to refresh

**Viewer**
- Swipe through the grid you came from; pinch or double-tap to zoom; swipe down to close, up for details
- Optional full-resolution original when zoomed in
- Videos with Media3 (autoplay and loop are settings), motion part of live photos
- Details: date, file, size, resolution, camera and lens settings, location (opens in your maps app), people, albums, tags
- Edit the description and star rating
- Share files, share a link, save to the phone, favourite, archive, tag, add to an album, move to the locked folder, trash or delete permanently, use as album cover, refresh metadata or thumbnails

**Library**
- Albums (all, mine, shared with me): create, rename, describe, sort order, delete or leave
- Share albums with other users as editor or viewer, change roles, remove people
- Comments and likes on shared albums
- Add to albums from your library or upload straight from the phone
- Favourites, Archive, Trash (restore, empty), Videos, Recently added
- Locked folder with PIN (set up the PIN from the app if you haven't)
- People: name, birthday, favourite, hide, merge
- Places and a map of every geotagged photo (MapLibre with clustering, using your server's map style)
- Shared links: copy, share, delete
- Partners: share with someone, see what they share, choose whether it shows in Photos
- Tags and the server's folders

**Search**
- Smart search ("dog on a beach") when the server has machine learning on
- File name, description and text-in-photo (OCR) search
- Filter by photos/videos and favourites
- Explore people and places

**Backup**
- Pick which phone albums to back up; videos optional
- Automatic in the background when new photos appear and every few hours, with Wi-Fi-only and charging-only options
- Duplicate check by SHA-1 before uploading, so nothing already on the server is sent again
- Keeps photo locations (asks for media location access)
- Progress notification; "Back up now" and "Check everything again"
- Share photos from any app to ImmichCtrl to upload them

**Everything else**
- Sign in with email and password, OAuth (PKCE, same redirect as Immich's own app), or an API key
- Several servers and accounts, switch between them
- Storage used (your quota or the server's disk), photo and video counts
- Admins: disk and per-user usage, job queues (run, run all, pause, resume, clear failed)
- Change your password
- Themes: system, dark, light, pure black, or Material You colours
- Phone layout with the floating glass tab island; tablet and unfolded foldable layout with a side rail

## Building

GitHub Actions builds a release APK on every push to `main` and publishes it on the Releases page.

Release signing uses the same setup as the other Ctrl apps:

| Name | Where |
| --- | --- |
| `KEYSTORE_BASE64` | Secret: the keystore, base64 encoded |
| `KEYSTORE_PASSWORD` | Secret |
| `KEY_PASSWORD` | Secret |
| `KEY_ALIAS` | Variable (or secret): `immich` |

Without them, CI fails instead of publishing a debug-signed release (local builds still fall back to the debug key).

Locally: `./gradlew assembleRelease` (JDK 17, Android SDK 36).

## Notes

- Requests only use endpoints that Immich 2.x and 3.x both have. The few places that differ (video durations, upload fields, album owners) are handled for both.
- Images, video and downloads go through the app's own HTTP client, which adds your credentials only for your server. Tokens and API keys are encrypted with an Android Keystore key.
- Plain HTTP servers work, but only when you type http:// or confirm the switch after HTTPS fails (the app warns that your password is sent unencrypted), and user-installed certificates are trusted for self-signed reverse proxies.
- OAuth uses the redirect `app.immich:///oauth-callback`, the one Immich's mobile app uses, so servers already set up for the official app work. If the official Immich app is also installed, Android may ask which app should finish the sign-in.

Manrope font: SIL Open Font License, see `FONT-LICENSE-Manrope.txt`.
