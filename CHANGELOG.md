# Changelog

All notable changes to the AYB WAY BusTicketPOS Android app are documented in this file.

---

## [2.1.2] – 2026-10-29 (versionCode 212)

### Fixed

- **Update download errors** – When the APK download fails, the server's reason (e.g. `apk_missing_on_server`) is now included in the error, so it shows in the Audit Log and in the update report sent to the server instead of only "Download failed HTTP 500" (`HttpUtil.downloadToFile`).

### Changed

- **Version** – 2.1.2 (212). Devices on 2.0.0 (200) and 2.0.1 (201) accept it as an update.
