# Changelog

All notable changes to ImmichCtrl. Version numbers are `major.minor.build`, where the build number comes from the GitHub Actions run, so each section below covers every build of that version.

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
