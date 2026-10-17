# ImmichCtrl

**Your Immich photo library and backup, in a native Android app.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/ImmichCtrl?label=download)](https://github.com/VoidedKN0X/ImmichCtrl/releases/latest) ![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

ImmichCtrl is an Android app for self-hosted [Immich](https://immich.app/) photo servers. Browse your timeline, back up your phone, manage albums and search your photos. Works with Immich 2.x and 3.x.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/ImmichCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

## Features

- **Timeline** grouped by day or month that opens instantly even with huge libraries
- **Viewer** with zoom, videos, live photos and full photo details
- **Automatic backup** of the phone albums you choose, with Wi-Fi-only and charging-only options and no duplicate uploads
- **Albums**, including shared albums with comments and likes
- **Smart search** ("dog on a beach"), text-in-photo search and filters
- **People, places and a map** of all your geotagged photos
- **Memories**, favourites, archive, trash and a locked folder
- **Partner sharing** and shared links
- **Several servers and accounts**; admins can see storage use and run jobs
- Sign in with email and password, OAuth or an API key; tablet and foldable layout

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
