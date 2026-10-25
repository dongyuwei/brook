# Brook VPN for Android

An Android VPN client built on the [brook](https://github.com/txthinking/brook)
Go core in this repository. Only apps you whitelist are proxied through a
brook server; everything else uses the underlying network directly.

## Architecture

```
┌─ Android app (Kotlin, VpnService) ─────────────────────────────┐
│ whitelisted apps ──► TUN fd ──┐                                │
│ app itself never routed       │                                │
└───────────────────────────────┼────────────────────────────────┘
                                 │ fd + brook link text
┌─ brook.aar (gomobile) ────────▼────────────────────────────────┐
│ tun2socks (gVisor netstack): TUN fd ↔ local SOCKS5 127.0.0.1   │
│ brook.BrookLink: SOCKS5 ↔ brook server (tcp+udp, all links)    │
└────────────────────────────────────────────────────────────────┘
```

- Supported links: `brook://server?...`, `wsserver`, `wssserver`,
  `quicserver` — any plain brook link text, parameters passed through
  verbatim (`udpovertcp=true`, `withoutBrookProtocol=true`, ...).
- DNS: the VPN announces public resolvers (223.5.5.5 / 8.8.8.8); app DNS
  queries travel through the tunnel and are resolved remotely by the
  brook server (no local resolution). Note: Android "Private DNS"
  (DoT) bypasses VPN DNS — leave it off.

## Layout

- `mobile/` — Go package bound with gomobile: `Mobile.start(fd, link, mtu)`,
  `Mobile.stop()`, `Mobile.testLink(link)`, `Mobile.running()`. It uses the
  local brook module via a `replace` directive.
- `app/` — Android Studio project (Kotlin + Compose).
- `build-aar.sh` — builds `app/libs/brook.aar` (arm, arm64, x86, x86_64).

## Build

Requirements: Go >= 1.24 (or `GOTOOLCHAIN=auto`), gomobile + gobind
(`go install golang.org/x/mobile/cmd/gomobile@latest` and
`.../cmd/gobind@latest`), Android NDK (set `ANDROID_NDK_HOME`), JDK 17.

```
./build-aar.sh
./gradlew :app:assembleDebug
# release: ./gradlew :app:assembleRelease (bring your own signing config)
```

On restricted networks `build-aar.sh` honors `HTTPS_PROXY` for Go downloads
and Gradle uses `systemProp.*` proxy settings.

## Use

1. Paste a brook link (or open a `brook://` URL, or share text into the app).
2. Tap the whitelist row and check the apps that should be proxied.
3. Connect. Android shows a one-time VPN consent dialog.

## Notes & limitations

- Whitelist-only routing is implemented with
  `VpnService.Builder.addAllowedApplication`; the app excludes itself, so
  its own connection to the brook server never loops through the TUN.
- `QUERY_ALL_PACKAGES` is required to list apps; fine for personal
  sideloading, restricted on Google Play.
- The debug APK is ~112 MB because it ships uncompressed gVisor runtime
  for four ABIs; a release build with `abiFilters` for the devices you
  care about is much smaller.
- GPL-3.0, same as brook (tun2socks is MIT).
