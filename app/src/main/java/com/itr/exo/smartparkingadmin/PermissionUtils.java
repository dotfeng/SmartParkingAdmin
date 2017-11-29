package com.itr.exo.smartparkingadmin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.nfc.NfcAdapter;

public class PermissionUtils {
    public static void checkNfcPermissions(final Activity activity, NfcAdapter nfcAdapter) {
        if (nfcAdapter != null) {
            if (!nfcAdapter.isEnabled()) {
                new AlertDialog.Builder(activity)
                        .setTitle("NFC not enabled")
                        .setMessage("Go to Settings?")
                        .setPositiveButton("Yes",
                                (dialog, which) -> {
                                    if (android.os.Build.VERSION.SDK_INT >= 16) {
                                        activity.startActivity(new Intent(android.provider.Settings.ACTION_NFC_SETTINGS));
                                    } else {
                                        activity.startActivity(new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS));
                                    }
                                })
                        .setNegativeButton("No",
                                (dialog, which) -> System.exit(0)).show();
            }
        } else {
            new AlertDialog.Builder(activity)
                    .setTitle("No NFC available. App is going to be closed.")
                    .setNeutralButton("Ok",
                            (dialog, which) -> System.exit(0)).show();
        }
    }
}
