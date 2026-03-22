package com.example.har;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

// riceve messaggi dal timertask e aggiorna la UI
public class UIUpdateHandler extends Handler {

    // riferimento alla main activity usato per aggionrare effettivamente UI
    private MainActivity activity;

    public UIUpdateHandler(MainActivity activity) {
        this.activity = activity;
    }

    // callback invocata dal sistema quando arriva nuovo messaggio all'handler
    @Override
    public void handleMessage(Message msg) {
        if (activity == null) return;

        Bundle b = msg.getData();
        if (b != null) {
            String attivita = b.getString(HARTimerTask.KEY_ACTIVITY);
            float confidenza = b.getFloat(HARTimerTask.KEY_CONFIDENCE);

            // aggionro UI
            activity.updateRecognitionUI(attivita, confidenza);
        }
    }

    // rimuove riferimento all'activity, chiamata in onDestroy main activity
    public void dispose() {
        activity = null;
    }
}