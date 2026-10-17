# ImmichCtrl

**Your Immich photo library and backup, in a native Android app.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/ImmichCtrl?label=download)](https://github.com/VoidedKN0X/ImmichCtrl/releases/latest) ![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

ImmichCtrl is an Android app for self-hosted [Immich](https://immich.app/) photo servers. Browse your timeline, back up your phone, edit and organise your photos, and keep albums for offline viewing. Works with Immich 2.x and 3.x.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/ImmichCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

## Features

**Photos**
- **Timeline** grouped by day or month that opens instantly even with huge libraries, with a fast-scroll month bubble and pinch for 2 to 7 photos per row
- **Photos on this phone** in the same timeline: ones not backed up yet show a cloud badge, and you can upload just the ones you pick
- **Memories** ("On this day") along the top, played full screen like stories
- **Slideshow** of any album, search or timeline, with timing, shuffle and loop options
- **Live updates**: new uploads, edits and deletions from other devices appear without refreshing

**Viewer**
- Zoom, videos, live photos, swipe down to close; the actions sit in a floating glass island
- **Editing**: crop, rotate and mirror, saved on the server and undoable
- **Copy text from photos** (OCR) and see who's in a photo; tap a face to change who it is
- **Stacks**: browse, choose the cover photo, unstack
- **Change the date, time and location** of one photo or many
- Full details with a mini map and the photo's owner
- **Cast** photos and videos to a Chromecast or Google TV
- Share at full size or as a smaller file, share a link with an expiry date, password and download options

**Library**
- **Albums** with sort, search and list or grid view; shared albums with comments, likes and editors
- **Offline albums**: keep an album on the phone and view it without a connection
- **Duplicate review**: see the copies Immich found side by side and choose which to keep
- **On this device**: your phone's albums, with what's backed up and what isn't
- Favourites, archive, trash, recently added (by upload date on Immich 3), videos, tags and folders
- **Locked folder** with PIN or fingerprint; moving a photo there also removes the copy on the phone
- **People** (including people shared with you), **places** and a **map** with date and favourite filters
- **Partner sharing** and shared links you can edit later

**Search**
- **Smart search** ("dog on a beach") plus file name, description and text-in-photo search
- **Filters**: people, place, camera, date range, photos or videos, favourites, archived and photos not in any album

**Backup**
- **Automatic backup** of the albums you choose, or everything on the phone, with Wi-Fi-only and charging-only options and no duplicate uploads
- **Album sync**: phone albums become matching Immich albums
- **Free up space**: remove photos from the phone once they're safely on the server (they go to the phone's trash first)
- Picks up where it left off after an interruption, and warns you if battery saving could stop it
- **Quick Settings tile** to start a backup and see its progress
- Share photos from any app to upload them, or open them with ImmichCtrl to view them

**Privacy and control**
- **App lock** with your fingerprint, face or screen lock
- **Read-only mode** for handing the phone to a child: just the timeline, nothing can be changed
- **Home network address**: the app switches to your local server address on Wi-Fi and back when you leave
- **Custom headers** (Cloudflare Access, auth proxies) and **client certificates** (mutual TLS)

**Everything else**
- **Home screen widgets**: today's memories, or a random photo
- **Admin alerts** (admin accounts only): failed jobs, low disk space, new Immich releases
- Admins can see storage use, manage users and run jobs
- Several servers and accounts; change your profile picture and password
- Sign in with email and password, OAuth or an API key; tablet and foldable layout
- Built-in log viewer, and a warning when the server's Immich version isn't supported

## Requirements

- Android 13 or newer
- An Immich server, version 2.x or 3.x

## Install

1. Download the latest `.apk` file from the [Releases page](https://github.com/VoidedKN0X/ImmichCtrl/releases/latest).
2. Open it on your phone. Android may ask you to allow installing apps from your browser or file manager.
3. Updates install over the old version. Your settings and login are kept.

**Automatic updates:** on your phone, tap one of the buttons below.

- **Obtainium:** install [Obtainium](https://github.com/ImranR98/Obtainium) first. The button adds ImmichCtrl and installs every new release for you.
- **F-Droid:** adds the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) to F-Droid, Droid-ify or Neo Store, with every Ctrl app and automatic updates.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/ImmichCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

If the Obtainium button doesn't open the app, add `https://github.com/VoidedKN0X/ImmichCtrl` in Obtainium by hand.

## Getting started

1. Enter your Immich server address, e.g. `https://photos.example.com`.
2. Sign in with email and password, OAuth, or an API key.
3. To back up your phone, open **Backup**, pick the albums to upload and turn it on.

## Tips and troubleshooting

- **OAuth** uses the same redirect as Immich's official app, so servers already set up for it work. If both apps are installed, Android may ask which one should finish the sign-in.
- **Plain HTTP** works, but the app warns you first because your password would be sent unencrypted.
- **Server behind Cloudflare Access or another proxy?** Tap **Advanced: custom headers** on the sign-in screen and add the headers there. You can change them later in **Settings → Networking**.
- **Home network address and client certificates** are in **Settings → Networking**. For a client certificate, install it in Android's settings first (Security → Encryption & credentials), then pick it in the app.
- **Quick Settings tile:** pull down the notification shade, tap the pencil and drag **Back up photos** into your tiles.
- **Widgets:** long-press your home screen, choose **Widgets** and look for ImmichCtrl.
- **Backup stops in the background?** Open **Backup** and follow the battery warning to let ImmichCtrl run without restrictions.
- **Read-only mode** is in Settings. To leave it, long-press your profile picture on the Photos tab (and unlock with your fingerprint if one is set up).
- **Duplicate review** needs Immich's duplicate detection, which needs smart search (machine learning) on the server.
- **Something not working?** **Settings → Logs** shows what the app has been doing; copy or share it when reporting a bug.
- See [CHANGELOG.md](CHANGELOG.md) for what changed in each version.

## Build from source

You need [Android Studio](https://developer.android.com/studio), or JDK 17 and the Android SDK.

1. Clone the repo:
   ```bash
   git clone https://github.com/VoidedKN0X/ImmichCtrl.git
   cd ImmichCtrl
   ```
2. Build the app:
   ```bash
   ./gradlew assembleDebug
   ```
   On Windows, use `gradlew.bat assembleDebug`.
3. The APK is in `app/build/outputs/apk/debug/`. Copy it to your phone and install it.

Or open the folder in Android Studio and press **Run** with your phone connected.

## Support the project

ImmichCtrl is free and open source. If it's useful to you, you can support development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). Starring the repo and [reporting bugs](https://github.com/VoidedKN0X/ImmichCtrl/issues) helps too.

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same Material You design.

| App | What it's for |
| --- | --- |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| [ElementCtrl](https://github.com/VoidedKN0X/ElementCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| **ImmichCtrl** (this app) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |

## License

ImmichCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

The Manrope font is licensed under the SIL Open Font License; see `FONT-LICENSE-Manrope.txt`.
