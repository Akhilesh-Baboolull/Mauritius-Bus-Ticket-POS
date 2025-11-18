package com.ayb.busticketpos;

import android.nfc.NfcAdapter;
import android.content.Intent;
import android.provider.Settings;

public class NfcChecker {

    public static boolean isNfcEnabled(android.content.Context context) {
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(context);

        if (nfcAdapter == null) {
            // NFC is not available on this device
            return false;
        } else if (!nfcAdapter.isEnabled()) {
            // NFC is available but not enabled.  You can optionally launch settings.
            // Intent intent = new Intent(Settings.ACTION_NFC_SETTINGS);
            // context.startActivity(intent);
            return false;
        } else {
            // NFC is enabled
            return true;
        }
    }

    // Example Usage (in an Activity):
    public void checkNfcAndPromptIfNeeded(android.content.Context context) {
        if (!isNfcEnabled(context)) {
            // NFC is not enabled, inform the user and provide a way to enable it
            android.widget.Toast.makeText(context, "NFC is not enabled. Please enable it in settings.", android.widget.Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_NFC_SETTINGS);
            context.startActivity(intent);


        } else {
            // NFC is enabled, proceed with NFC operations
            android.widget.Toast.makeText(context, "NFC is enabled", android.widget.Toast.LENGTH_SHORT).show();

            // Add your NFC code here, such as starting an NFC reader mode
            // or handling NFC intents.
        }
    }
}