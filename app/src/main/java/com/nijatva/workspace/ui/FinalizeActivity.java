package com.nijatva.workspace.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Nijatva Work Space — the last hop of provisioning.
 *
 * <p>After the system has created the work profile, the profile-owner receiver launches this
 * Activity <em>inside</em> the new profile. Its only job is to hand control to
 * {@link DummyActivity} with {@link DummyActivity#FINALIZE_PROVISION}, which is where the real
 * work happens (writing the first settings, starting the work-profile service). This Activity
 * exists as a separate, stable entry point because that component name is referenced from the
 * provisioning flow and from the receiver.</p>
 *
 * <p>It finishes immediately: there is no UI here on purpose, so the user never sees a flash of
 * an empty screen.</p>
 */
public class FinalizeActivity extends AppCompatActivity {

    private static final String TAG = "FinalizeActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        relayToDummyActivity();
        finish();
    }

    private void relayToDummyActivity() {
        Intent relay = new Intent(getApplicationContext(), DummyActivity.class);
        relay.setAction(DummyActivity.FINALIZE_PROVISION);
        // Started from an Activity context, but NEW_TASK keeps the relay out of our own task so
        // that finishing here does not tear the relay down with it.
        relay.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(relay);
        } catch (ActivityNotFoundException e) {
            // Manifest mismatch — should be impossible, but a missing relay must not crash the
            // provisioning flow at its last step.
            Log.e(TAG, "DummyActivity is not declared in this profile's manifest", e);
        }
    }
}
