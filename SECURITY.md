# Security policy

Thank you for helping keep ImmichCtrl and its users safe.

## Reporting a problem

**Please don't report security problems in a public issue.** Report them privately instead:

1. Go to the [Security tab](https://github.com/VoidedKN0X/ImmichCtrl/security) of this repo.
2. Click **Report a vulnerability** and describe what you found, how to reproduce it, and which version of ImmichCtrl you used.

Only the maintainer can see the report. I'll reply as soon as I can, work on a fix, and credit you in the changelog if you'd like.

## Supported versions

Only the latest release gets security fixes. Please update before reporting, and check the [changelog](CHANGELOG.md) to see whether it's already fixed.

## What counts

- Your password, access token, API key, custom proxy headers or photos leaking to anyone other than your own Immich server
- Ways around the app lock or the locked folder, or private photos showing up in widgets, notifications, screenshots or the recent-apps screen when they shouldn't
- Another app on the phone being able to make ImmichCtrl upload, delete or share photos you didn't choose
- Shared links being created with different options than the ones you picked
- Anything that lets someone run code or read files they shouldn't

Problems in Immich itself should go to the [Immich project](https://github.com/immich-app/immich).

## How releases are protected

Every release is built from this repo by GitHub Actions and signed with the same release key. Its SHA-256 certificate fingerprint is:

```
B0:14:DB:86:AC:B9:84:3B:30:70:27:F9:23:3B:A4:45:89:E2:6D:7C:0A:55:2B:D1:93:A0:96:75:56:AE:C9:23
```

The [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) only accepts APKs signed with this key.
