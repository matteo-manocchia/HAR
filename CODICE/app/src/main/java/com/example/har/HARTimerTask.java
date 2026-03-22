package com.example.har;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import java.util.TimerTask;

// classe che implementa un timertask per la raccolta di dati e
// comunica i risultati alla UI tramite handler
public class HARTimerTask extends TimerTask {

    private static final String TAG = "HARTimerTask";
    public static final String KEY_ACTIVITY   = "attivita";
    public static final String KEY_CONFIDENCE = "confidenza";

    // usato per madare messaggi al thread main
    private Handler handler;

    // riferimento alla classe gestore del modello
    private HARClassifier classifier;

    // buffer dove si accumulano i campioni rilevati
    private float[][] sensorBuffer;

    // serve per controllare quando il buffer è pieno
    private int sampleCount;

    // lock che servirà sul sensorBuffer e sampleCount
    private final Object bufferLock = new Object();

    // true -> run esegue
    // false -> thread in attesa
    private boolean sveglio;

    // serve per mettere in pausa e risvegliare il thread del timer
    private final Object waitLock;


    public HARTimerTask(Handler handler, HARClassifier classifier) {
        this.handler    = handler;
        this.classifier = classifier;
        this.sensorBuffer = new float[HARClassifier.getTimesteps()][HARClassifier.getFeatures()];
        this.sampleCount  = 0;
        this.sveglio      = false;
        this.waitLock     = new Object();
    }


    //chiamato dal main thread ogni volta che riceve un nuovo dato dall'accelerometro
    public void addSample(float[] sample) {
        // verifica che il campione abbia x,y,z
        if (sample.length != HARClassifier.getFeatures()) {
            Log.w(TAG, "addSample: dimensione campione errata (" + sample.length +
                    " invece di " + HARClassifier.getFeatures() + ")");
            return;
        }

        // conversione da m/s^2 dell'accelerometro andorid a g utilizzata dal modello
        // 1 g = 9,81 m/s^2
        final float G = 9.81f;
        float[] converted = new float[HARClassifier.getFeatures()];
        converted[0] = sample[0] / G;  // x
        converted[1] = sample[1] / G;  // y
        converted[2] = sample[2] / G;  // z

        // sezione critica di accesso al buffer in scrittura
        synchronized (bufferLock) {
            int idx = sampleCount % HARClassifier.getTimesteps();
            sensorBuffer[idx][0] = converted[0];
            sensorBuffer[idx][1] = converted[1];
            sensorBuffer[idx][2] = converted[2];
            sampleCount++;
        }
    }

    // chiamato periodicamente dal timer
    @Override
    public void run() {
        if (handler == null || classifier == null)
            return;

        // l'utente ha attivato il riconoscimento
        if (sveglio) {
            boolean bufferPieno;
            float[][] bufferCopia = null;

            synchronized (bufferLock) {
                bufferPieno = (sampleCount >= HARClassifier.getTimesteps());
                if (bufferPieno) {
                    // copia del buffer per non bloccare onSensorChanged()
                    // così poi posso fare la classificazione e lasciare libero il buffer
                    // per farci scrivere dal main thread
                    bufferCopia = new float[HARClassifier.getTimesteps()][HARClassifier.getFeatures()];
                    for (int i = 0; i < HARClassifier.getTimesteps(); i++) {
                        System.arraycopy(sensorBuffer[i], 0, bufferCopia[i], 0, HARClassifier.getFeatures());
                    }
                    sampleCount = 0;
                }
            }

            // inferenza del modello
            if (bufferPieno && bufferCopia != null) {
                float[] result      = classifier.classify(bufferCopia);
                int classIndex      = (int) result[0];
                float confidence    = result[1];
                String activityName = HARClassifier.getActivityName(classIndex);

                // invio risultato all'handel che gira su main thread
                Message msg = handler.obtainMessage();
                Bundle b    = msg.getData();
                b.putString(KEY_ACTIVITY, activityName);
                b.putFloat(KEY_CONFIDENCE, confidence);
                msg.setData(b);
                handler.sendMessage(msg);
            }

        } else {
            // riconoscimento in pausa
            // il thread si mette in attesa
            synchronized (waitLock) {
                try {
                    waitLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // chiamato quando l'utente ferma il riconoscimento o l'activity main va in pausa
    public void addormenta() {
        sveglio = false;
        // svuota la coda di messaggi vecchi mandati all'handler prima dello stop
        handler.removeCallbacksAndMessages(null);
        // svuoto il contatore da eventuali parziali
        synchronized (bufferLock) {
            sampleCount = 0;
        }
    }

    // chiamato quando l'utente avvia il riconoscimento o activity va in oonStart
    public void sveglia() {
        sveglio = true;
        // per sicurezza svuoto la coda
        handler.removeCallbacksAndMessages(null);
        synchronized (bufferLock) {
            sampleCount = 0;
        }
        synchronized (waitLock) {
            waitLock.notify();
        }
    }

    // chiamato in onDestroy delle main activity per evitare memory leak
    // pulisco ed evito che ci siano task attivi anche se l'activity non lo è più
    public void dispose() {
        handler    = null;
        classifier = null;
    }
}