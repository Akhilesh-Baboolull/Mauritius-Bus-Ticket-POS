# Changelog

All notable changes to the AYB WAY BusTicketPOS Android app are documented in this file.

---

## [2.2.0] – 2026-10-31 (versionCode 220)

Offline-first, ordered sync. Requires the cloud sync scripts from cloud v1.3.2 (deploy the server first).

### Fixed

- **Start Trip FK error / lost tickets after offline use** – Requests no longer carry the server day / trip id that happened to be saved when the event occurred (often `0` or the previous trip's id). Jobs now reference local Room ids; the server ids are filled in at send time.
- **Tickets on the wrong trip** – Tickets are linked to their own local trip, not the last server trip id kept in Prefs.
- **Out-of-order uploads** – All day / trip / ticket / blank ticket / end trip / end day uploads go through one sender in the order they happened. A child is never sent before its parent; if a Start Day or Start Trip is missing (or was lost), it is rebuilt from the local row and sent first.
- **Duplicate days on retry** – create_day / start_trip use the local start time, so retries and re-sends match the server's duplicate check.

### Changed

- **No automatic deletion of business data** – Day, trip, ticket, waybill and blank-ticket uploads are no longer dropped after 24 hours, at the start of a new day or after 80 retries. They retry with a growing delay (5 s, 15 s, 1 min, 3 min, then every 5 min) until the server accepts them. Location pings and audit events keep the old clean-up rules.
- **Strict order** – A failed business upload pauses the ones after it until it goes through.
- **No direct sends** – Every upload is queued first and sent within about 3 seconds when online.
- **Settings → Send pending data now** – Skips the waiting delay and sends immediately.
- **Settings → Clear sync queue** – Asks a second time, with the count, if unsent day / trip / ticket uploads would be deleted.
- **Old queued uploads** – Jobs queued by older versions are matched to their local day / trip / ticket and sent the new way; unmatched ones are sent unchanged.
- **Database v23** – Adds server ids (days, trips) and uploaded flags (tickets, blank tickets) with a real migration, so upgrading keeps all local data. Downgrade or very old schemas (below 22) are still recreated.
- **Version** – 2.2.0 (220).

---

## [2.1.4] – 2026-10-30 (versionCode 214)

### Fixed

- **Build error in Back blocker** – `addCallback` now receives the activity as a `ComponentActivity` (a `LifecycleOwner`) instead of a plain `Activity`, so the app compiles (`KioskBackBlocker`).

### Changed

- **Version** – 2.1.4 (214).

---

## [2.1.3] – 2026-10-29 (versionCode 213)

### Fixed

- **False "Install failed status=1"** – The install result PendingIntent was immutable, so Android dropped the status the installer adds and every successful update was logged as an App Error and reported as `INSTALL_FAILED`. It is now mutable and the real result is received (`UpdateInstaller`). Results without a status are ignored, `INSTALL_SUCCESS` is reported only once (shared with `PackageReplacedReceiver`), and a real failure clears the pending report (`InstallResultReceiver`). The update from 2.1.2 to 2.1.3 is still installed by 2.1.2's code, so it may log the false error one last time.

### Changed

- **Back button disabled** – The system Back button / gesture does nothing on every screen; screens are left only through their own buttons (`KioskBackBlocker`, registered in `App`). The keyboard still closes with Back, and cancellable dialogs still close with Back.
- **Home and Recents** – Lock task features are now set explicitly to the power menu only, so Home, Recents and notifications stay disabled in kiosk mode (`KioskHelper.applyLockTaskOnly`).
- **Version** – 2.1.3 (213).

---

## [2.1.2] – 2026-10-29 (versionCode 212)

### Fixed

- **Update download errors** – When the APK download fails, the server's reason (e.g. `apk_missing_on_server`) is now included in the error, so it shows in the Audit Log and in the update report sent to the server instead of only "Download failed HTTP 500" (`HttpUtil.downloadToFile`).

### Changed

- **Version** – 2.1.2 (212). Devices on 2.0.0 (200) and 2.0.1 (201) accept it as an update.
