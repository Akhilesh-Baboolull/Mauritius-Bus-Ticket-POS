// ApiEndpoints.java — single place for all API URLs
package com.ayb.busticketpos;

public final class ApiEndpoints {
    private ApiEndpoints() {}

    public static final String BASE = "https://cloud.aybway.com/api";

    // ---- Sync (trip/day/ticket) ----
    public static final String CREATE_DAY   = BASE + "/sync/create_day.php";
    public static final String END_DAY      = BASE + "/sync/end_day.php";
    public static final String START_TRIP   = BASE + "/sync/start_trip.php";
    public static final String END_TRIP     = BASE + "/sync/end_trip.php";
    public static final String RECORD_TICKET = BASE + "/sync/record_ticket.php";
    public static final String BLANK_TICKET = BASE + "/sync/blank_ticket.php";

    // ---- App update (releases) ----
    public static final String CHECK_URL    = BASE + "/releases/check.php";
    public static final String REPORT_URL   = BASE + "/releases/report.php";
    public static final String PROVISION_URL = BASE + "/releases/provision.php";

    // ---- DB sync (master data) ----
    public static final String MASTER_TARIFFS = BASE + "/master_tariffs.php";
    public static final String ROUTES         = BASE + "/routes.php";
    public static final String BUSES          = BASE + "/buses.php";

    // ---- Positioning ----
    public static final String POSITIONING   = BASE + "/sync/positioning.php";

    // ---- Settings ----
    public static final String SETTINGS_FAILED_LOGIN = BASE + "/settings/failed_login.php";
}
