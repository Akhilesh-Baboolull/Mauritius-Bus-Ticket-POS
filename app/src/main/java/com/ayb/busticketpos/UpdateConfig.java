package com.ayb.busticketpos;

public final class UpdateConfig {
    private UpdateConfig() {}

    // Must match server config.php BASE_URL (e.g. https://cloud.aybway.com/api/releases)
    public static final String BASE = "https://cloud.aybway.com/api/releases";

    public static final String CHECK_URL  = BASE + "/check.php";
    public static final String REPORT_URL = BASE + "/report.php";

    public static final String PROVISION_URL = BASE + "/provision.php";
}
