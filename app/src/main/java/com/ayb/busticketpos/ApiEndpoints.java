// ApiEndpoints.java
package com.ayb.busticketpos;

public final class ApiEndpoints {
    public static final String BASE = "https://cloud.aybway.com/api/sync";
    public static final String CREATE_DAY = BASE + "/create_day.php";
    public static final String END_DAY    = BASE + "/end_day.php";
    public static final String START_TRIP = BASE + "/start_trip.php";
    public static final String END_TRIP   = BASE + "/end_trip.php";
    public static final String RECORD_TICKET   = BASE + "/record_ticket.php";
    public static final String BLANK_TICKET = BASE + "/blank_ticket.php";
    private ApiEndpoints() {}
}
