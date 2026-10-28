# Changelog

All notable changes to the AYB WAY BusTicketPOS Android app are documented in this file.

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
