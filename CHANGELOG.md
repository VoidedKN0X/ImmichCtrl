# Changelog

All notable changes to ImmichCtrl. Builds 0.2.5 and earlier were numbered by GitHub Actions run, so the 0.2 section covers all of them.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## Unreleased

Not in a release yet; this goes out with the next version.

### Changed

- **All Ctrl apps in the README.** The README's Ctrl series table now lists all 14 apps. New in the table: [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) (Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app), [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) (scan and browse documents on Paperless-ngx), [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) (manage your Proxmox VE server) and [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) (monitor and manage your TrueNAS server). Every app in the table is also in the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) for automatic updates. No change to the app itself.

## 0.2.12

Released 2026-10-19.

### New

- **Optional anonymous usage stats.** After you sign in, ImmichCtrl asks once whether you'd like to share anonymous usage stats. Nothing is sent unless you tap **Share stats**, and you can switch it on or off any time in **Settings > Usage stats**
- **What's shared:** which screens you open (for example "Album", "Person" or "Backup", never which album, person or place), whether automatic backup is on, when you turn it on or off or start a backup by hand or from the Quick Settings tile, which kind of search you use (smart, file name, description or text in photo, and whether filters were on), and which photo actions you use (share, share a link, save to phone, add to an album, new album) with a rough number of photos ("1", "2-10", "11-100" or "100+"). Also the app version, Android version, phone model, language, and whether it's a phone or tablet
- **What's never shared:** your server address, email, username or passwords, your photos or anything about them (IDs, file names, dates, locations), album, people, place or tag names, what you search for, exact photo counts, or any ID that follows you between sessions. Each session gets a new random ID, and stats from before and after you switch them off and on again aren't linked
- **Where it goes:** to VKNXWorks' own self-hosted server (Aptabase), not to Google or any analytics company. Stats that can't be sent are dropped, never saved for later. Builds you make from source can't send stats at all, and the setting doesn't appear in them

### Changed

- **PrintCtrl in the Ctrl series list.** The README's table of Ctrl apps now includes [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl), the app for Bambu Lab printers through a self-hosted Bambuddy server, and Spoolman spools.
- The README has a new **Usage stats** section listing exactly what's collected

## 0.2.11

### Security

- **Nothing stays usable above the app lock.** When ImmichCtrl locked itself, the lock screen was drawn over the app, but anything that opens in its own window (a delete confirmation, the share options, the album picker, menus and other pop-ups) stayed on top of it and could still be used without unlocking. The screens underneath also kept running behind it: a video kept playing, and screen readers could still read what was there. While the app is locked, its screens are now closed completely; when you unlock, you're back on the same screen you left. The "Upload to Immich?" question for photos shared from another app waits until you've unlocked

## 0.2.10

A security follow-up that closes gaps in 0.2.7's protections, including one opened by moving the You page in 0.2.8.

### Security

- **Read-only mode keeps to Photos again.** Since the You tab moved behind your profile picture, going back from Settings after turning read-only mode on left the You page open, with sign out, accounts, backup and server administration still usable. Read-only mode now closes the You page, Settings, accounts, backup, the server dashboard, the locked folder, shared links and the other screens that change things, and goes back to Photos
- **Shared photos only go to the account you said yes to.** If you confirmed an upload from another app (or tapped Upload, "Back up now" or album sync) and then switched account or signed out before it started, for example while waiting for a connection, it could upload to whichever account was active by then. Each upload now remembers its account and is cancelled if that account isn't the one in use, and copies of shared files are deleted
- **The Quick Settings tile respects the backup account.** When automatic backup is set up for another account, the tile no longer starts a backup to the account that happens to be active (it shows "Set up for another account" instead). The check also works right after the phone starts, before the app has loaded its settings
- **Signing out turns off automatic backup** when it was set up for that account, so it doesn't quietly resume if the account is added again. Every clean-up step on sign out now runs even if an earlier one fails
- **"Remove location when sharing" no longer lets a location through when something goes wrong.** Each shared photo is now checked after the location is removed, including the copy of the position many cameras and editors keep in the photo's XMP data. If the location can't be removed for sure, the original isn't sent: you get Immich's preview of the photo instead (which has no location), and if even that can't be checked, the share stops with a message. Before, a failure was only logged and the photo was sent with its location
- **The locked folder always asks for your PIN** (or fingerprint) when you open it. Before, it opened without one if the server still counted the session as unlocked, for example after the app was closed while the folder was open
- **Another app can no longer pass ImmichCtrl a link to ImmichCtrl's own private files** by writing it in a different form ("content://0@…"). Shared and "Open with" links are now checked against the app that really provides them

## 0.2.9

### Fixed

- The profile picture on the Photos tab is easy to tap again. The timeline's fast-scroll strip along the right edge was covering it and catching most taps; that strip now starts below the title row
- The profile picture now sits in the same place on Photos, Search and Library, the same distance from the edge of the screen

## 0.2.8

### Changed

- The **You** tab is gone from the bar at the bottom. Tap your profile picture at the top right of Photos, Search or Library to open it instead. Everything that was there (your storage, backup, settings, the server dashboard, accounts and sign out) is still on that page, now with a back arrow
- The bottom bar now has three tabs: Photos, Search and Library. On tablets and unfolded foldables the side rail matches
- Search and Library now show your profile picture next to the search bar and the title, like Photos already did. Long-pressing the picture on Photos still turns read-only mode on or off

## 0.2.7

A security and privacy update. Private photos now leave fewer traces on the phone, and nothing is uploaded or sent anywhere without you choosing it.

### Security

- **Sharing to ImmichCtrl asks first.** Photos shared from another app are no longer uploaded straight away: a dialog shows how many items and which account and server they'll go to, and nothing is uploaded until you tap Upload. Another app can no longer quietly make ImmichCtrl upload photos, or pass it links to ImmichCtrl's own private files
- **Automatic backup only goes to the account you turned it on for.** Switching to, or adding, another account or server no longer starts backing up your phone's photos there. If you open Backup on a different account it says so; turn automatic backup off and on again there to move it
- **Your sign-in never follows a redirect to another site.** The access token, API key, cookies and your custom proxy headers are only sent to the exact server address they belong to; if the server (or something in front of it) redirects elsewhere, they're removed first
- **"Save to phone" downloads inside the app.** Originals are now fetched by ImmichCtrl itself and written to Pictures/ImmichCtrl, instead of handing your access token to Android's download manager (which stored it in its own database and could pass it on through redirects). The HTTPS rules and client certificate apply to these downloads too
- **Home network address is checked properly.** The app only switches to the home address when an actual Immich server answers there (not a hotel or café login page that answers everything), and never follows redirects while checking. Saving a plain-HTTP home address now asks you to confirm first, and Networking shows "Not encrypted (HTTP)" when you're connected over HTTP
- **Locked folder:** screenshots, screen recordings and screen casting are blocked while the locked folder or a photo opened from it is on screen, its photos are no longer written to the image cache on storage, and they're dropped from memory when you leave it
- **Fingerprint for the locked folder is now tied to the fingerprint.** The remembered PIN is encrypted with a key the phone's secure hardware only releases right after a strong fingerprint or face check, and that key is wiped if a new fingerprint is added. You'll need to type the PIN once more after updating so it can be saved this way
- **App lock uses strong biometrics.** Unlocking needs a fingerprint, secure face unlock or the phone's screen lock; weaker face unlock that can be fooled by a photo no longer counts. The lock screen also can't be screenshotted or recorded
- **Recent apps shows a blank card** instead of a snapshot of your photos
- **Remove location when sharing** (new setting in Settings, on by default): the GPS position is taken out of JPEG, PNG and WebP photos you send to other apps. Videos and HEIC photos keep theirs
- **Signing out cleans up.** Removing an account deletes its offline albums, its backup record and its remembered locked-folder PIN from the phone, and clears cached thumbnails and previews
- **Custom headers are encrypted on the phone.** Proxy headers (Cloudflare Access secrets and the like) are now stored encrypted with the phone's Keystore, like passwords and API keys; existing ones are converted on the first start. They're also no longer kept in the screen's saved state
- **Casting:** the short-lived key given to the TV is ended as soon as casting stops, and is never reused after switching accounts
- **Copied shared links and text copied from photos** are marked as sensitive, so Android hides them from the clipboard preview and keyboard clipboard history
- **Shared link passwords** are hidden while you type them (tap the eye to show)
- **Notifications on the lock screen** show backup and offline-album progress without file or album names
- **Quick Settings tile** asks you to unlock the phone before starting or stopping a backup from the lock screen
- **OAuth sign-in** ignores redirects that don't match the sign-in in progress, and only ever opens a web address in the browser
- Copies of shared photos are removed from the app's cache if an upload fails for good, and any left behind are cleaned up after a day. File names from other apps and the server are made safe before they're used on the phone

## 0.2.6

- Version numbers are now plain, with no build number on the end, and a new version only comes out when something in the app actually changes. Nothing else in the app has changed.

## 0.2

Everything the official Immich app does, plus duplicate review, offline albums, app lock, a Quick Settings tile and admin alerts.

### New

**Photos on this phone**
- Photos that aren't backed up yet appear in the timeline, with a cloud badge showing what's only on the phone and what's on both (both can be turned off in Settings)
- **On this device** in Library: your phone's albums and how much of each is backed up
- Upload just the photos you select, from the timeline, a phone album or the viewer
- **Free up space**: remove photos from the phone once they're confirmed on the server, optionally only ones older than a set age. They go to the phone's trash first
- Open photos and videos from other apps with ImmichCtrl to view them

**Viewer**
- The viewer's buttons now sit in a floating glass island, like the tab bar
- **Edit photos**: crop, rotate and mirror. Edits are saved on the server and can be undone
- **Copy text from photos** (OCR)
- **Faces**: see who's in a photo and tap a face to change who it is
- **Stacks**: browse a stack, choose its cover photo, remove photos from it or unstack it; stack selected photos from the grid
- **Change date and time, and location**, for one photo or a selection
- Mini map and the photo's owner in the details
- **Cast** to Chromecast and Google TV
- **Slideshow** with adjustable timing, shuffle and loop
- Choose the share size: original, or a smaller file (or ask each time)
- Moving a photo to the locked folder also removes the copy on the phone

**Library**
- **Duplicate review**: see the copies Immich found side by side with their size and resolution, choose which to keep, resolve one group or all of them, or mark a group as not duplicates
- **Offline albums**: keep any album on the phone (photos at preview size, videos at playback quality) and view it without a connection
- Albums can be sorted, searched and shown as a list or grid; album editors can rename albums and change descriptions
- Shared links: set an expiry date, password, download and upload permissions and description, and change them later
- Recently added now sorts by upload date on Immich 3
- People shared with you by other users are marked (Immich 3)
- Memories open full screen, story style
- Map: filter by favourites, include archived photos, limit to the last year or five years, and see how many photos are in view

**Search**
- Filters for people, country, state and city, camera make and model, date range, photos or videos, favourites, archived and photos not in any album

**Backup**
- **Back up everything** on the phone instead of picking albums
- **Album sync**: put backed-up photos in Immich albums named after their phone albums, and reorganise existing backups the same way
- **Quick Settings tile** to start a backup and see its progress
- Warnings when battery optimisation or turned-off notifications could stop backup
- Backup resumes after a lost connection and logs what it did

**Privacy and connection**
- **App lock** with fingerprint, face or screen lock, locking again after a time you choose
- Fingerprint unlock for the locked folder
- **Read-only mode** for children: only the timeline, no changes possible. Long-press your profile picture on Photos to leave it
- **Home network address**: on Wi-Fi the app uses your local server address when it answers, and the public address elsewhere
- **Custom headers** for Cloudflare Access and auth proxies, at sign-in and later
- **Client certificates** (mutual TLS) from Android's certificate store
- **Live updates** over Immich's websocket: changes from other devices appear on their own

**Everything else**
- **Home screen widgets**: today's memories and a random photo
- **Admin alerts** for admin accounts: failed jobs, disk filling up past a threshold you choose, and new Immich releases
- Change or remove your profile picture
- **Logs** screen to copy or share what the app has been doing
- Warning when the server runs an Immich version the app doesn't support

### Fixed (since 0.2.4)
- Duplicate review: "Resolve all" no longer trashes every copy in a group where none was ticked to keep; resolving works on Immich 2.5
- "Delete permanently" on a selection with phone-only photos sends those to the phone's trash instead of deleting them for good
- Moving photos to the locked folder sends the phone copies to the phone's trash rather than deleting them
- Crash in phone albums that contain identical copies of a photo
- Crash when uploading or sharing a very large number of photos at once
- An http:// home network address next to an https:// public one no longer blocks every request
- App lock no longer locks again right after unlocking with the phone's PIN or pattern, and can't trap you if the screen lock is removed
- Opening a photo from the locked folder no longer locks the folder under it
- Read-only mode also blocks editing in the details sheet
- Clearing a star rating works on Immich 2.5
- One item the server can't provide no longer stops an offline album from finishing
- "Save to phone" sends your custom headers, so it works behind Cloudflare Access and similar
- Lower memory use after browsing many albums and searches

### Changed
- Release notes on GitHub now come from this changelog
- Material icons that are mirrored for right-to-left languages use the auto-mirrored versions

## 0.1

First release.

- Timeline grouped by day or month, loading months as you scroll; ThumbHash placeholders; pinch for 2 to 7 photos per row; fast-scroll month bubble
- Drag to select, select a whole day from its header
- Memories ("On this day"), partners' photos in the timeline, pull to refresh
- Viewer with zoom, swipe to close, full-resolution originals, videos and live photos
- Photo details: date, file, size, resolution, camera, location, people, albums and tags; edit description and rating
- Share files or a link, save to the phone, favourite, archive, tag, add to an album, locked folder, trash or delete
- Albums: create, rename, describe, sort, delete or leave; share with other users as editor or viewer; comments and likes
- Favourites, archive, trash, videos, recently added, locked folder with PIN, tags and folders
- People (name, birthday, favourite, hide, merge), places and a clustered map using your server's map style
- Shared links, partner sharing
- Smart search, file name, description and text-in-photo search, explore people and places
- Automatic backup of chosen phone albums, Wi-Fi-only and charging-only options, duplicate check before upload, photo locations kept
- Share photos from any app to upload them
- Sign in with email and password, OAuth or an API key; several servers and accounts
- Storage use, admin tools for storage, users and jobs, change password
- System, dark, light, pure black and Material You themes; glass tab island on phones, side rail on tablets and foldables
