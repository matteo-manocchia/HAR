package com.example.har;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Timer;

// gestisce oltre al ciclo di vita, anche accelerometro (SensorEventListener),
// interazione utente, navogazione verso seconda activity
public class MainActivity extends Activity implements SensorEventListener {

    private static final String TAG = "MainActivity";

    // periodo ogni quanto il timer task controlla se il buffer è pieno
    private static final int TIMER_PERIOD_MS = 250;

    // frequenza di campionamento del sensore, corrisponde a quella del modello 100Hz, ma
    // espressa in secondi
    private static final int SENSOR_DELAY = 10000;

    private TextView tvActivity;
    private TextView tvConfidence;
    private TextView tvStatus;
    private Button btnStartStop;

    private Timer timer;
    private HARTimerTask harTask;
    private UIUpdateHandler handler;

    private HARClassifier classifier;

    private SalvaRilevazioni salvaRilevazioni;

    // manager dei sensori
    private SensorManager sensorManager;
    // specifico sensore
    private Sensor sensorAccelerometer;

    // serve per accumulare i dati del sensore e mandarli al timertask
    private final float[] currentSample = new float[HARClassifier.getFeatures()];

    // inidca se riconoscimento attivo o no
    private boolean isRecognizing = false;

    //ultima attività rilevata, serve per salvare
    private String ultimaAttivitaSalvata = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvActivity   = findViewById(R.id.tvActivity);
        tvConfidence = findViewById(R.id.tvConfidence);
        tvStatus     = findViewById(R.id.tvStatus);
        btnStartStop = findViewById(R.id.btnStartStop);


        // ottengo riferimento al sensor manager e all'accelerometro
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        if (sensorAccelerometer == null) {
            Toast.makeText(this, "Accelerometro non disponibile sul dispositivo!", Toast.LENGTH_LONG).show();
            btnStartStop.setEnabled(false);
            Log.e(TAG, "TYPE_ACCELEROMETER non disponibile");
        }

        // nuova istanza della classe che salva i risultati su db
        salvaRilevazioni = new SalvaRilevazioni();

        // disabilito pulsante finchè modello non è pronto
        btnStartStop.setEnabled(false);
        tvStatus.setText("Caricamento modello da Firebase...");

        // creo il classificatore che scarica il modello
        classifier = new HARClassifier(new HARClassifier.ModelReadyListener() {
            @Override
            public void onModelReady() {
                // Il modello è stato scaricato e l'interprete è pronto
                Log.d(TAG, "Modello pronto da Firebase ML");
                btnStartStop.setEnabled(true);
                tvStatus.setText(getString(R.string.label_stato_attesa));

                // istanzazione handler e timer e task
                handler = new UIUpdateHandler(MainActivity.this);
                timer   = new Timer();
                harTask = new HARTimerTask(handler, classifier);
                // scehdula il task con un delay di 10 ms e il periodo indicato prima
                // il task viene creato con sveglio = false, quindi viene creato, ma va subito in wait
                timer.scheduleAtFixedRate(harTask, 10, TIMER_PERIOD_MS);
            }

            @Override
            public void onModelError(String errorMessage) {
                Log.e(TAG, "Errore caricamento modello: " + errorMessage);
                Toast.makeText(MainActivity.this,
                        "Errore caricamento modello",
                        Toast.LENGTH_LONG).show();
                tvStatus.setText("Errore caricamento modello. Connettiti ad internet!");
            }
        });
    }

    // faccio partire qui il task e attivo il sensore
    @Override
    protected void onResume() {
        super.onResume();
        if (isRecognizing) {
            registraSensori();
            harTask.sveglia();
        }
    }

    // deregistro e addormento task
    @Override
    protected void onPause() {
        super.onPause();
        if (isRecognizing) {
            harTask.addormenta();
            deregistraSensori();
        }
    }

    // chiamo tutti iv ari metodi per pulire
    @Override
    protected void onDestroy() {
        if (harTask != null) {
            harTask.addormenta();   // prima metto sveglio=false e pulisco
            harTask.dispose();      // rimuovo riferimenti
            harTask.sveglia();      // sblocco il thread dal wait
            harTask.cancel();       // cancello
        }

        if (timer != null) {
            timer.purge();
            timer.cancel();
        }

        if (handler != null) {
            handler.dispose();
        }

        if (classifier != null) {
            classifier.close();
        }

        deregistraSensori();

        harTask    = null;
        timer      = null;
        handler    = null;
        classifier = null;

        super.onDestroy();
    }


    // funzione chiamata dal bottone per fare start/stop
    public void onStartStopClick(View v) {
        if (!isRecognizing) {
            isRecognizing = true;
            btnStartStop.setText(getString(R.string.btn_ferma));
            tvStatus.setText(getString(R.string.label_stato_raccolta));
            tvActivity.setText(getString(R.string.label_attivita_default));
            tvConfidence.setText(getString(R.string.label_confidenza_default));
            registraSensori();
            harTask.sveglia();

        } else {
            isRecognizing = false;
            ultimaAttivitaSalvata = "";
            btnStartStop.setText(getString(R.string.btn_avvia));
            tvStatus.setText(getString(R.string.label_stato_attesa));

            harTask.addormenta();
            deregistraSensori();
        }
    }

    // collegata a bottone per la visulaizzazione dello storico
    // chiama intent esplicito verso activity che mostra stroico rilevazioni
    public void onHistoryClick(View v) {
        Intent intent = new Intent(this, HistoryActivity.class);
        startActivity(intent);
    }

    // registra il sensore passando activity stessa, sensore da ascoltare,
    // intervallo tra ogni campione (100Hz)
    private void registraSensori() {
        if (sensorAccelerometer != null) {
            sensorManager.registerListener(this, sensorAccelerometer, SENSOR_DELAY);
        }
    }

    // ferma ricezione degli eventi
    private void deregistraSensori() {
        sensorManager.unregisterListener(this);
    }

    // callback chiamata quando il sensore vede un nuovo campione
    // i value contengono i valori x,y,z in m/s^2
    // vengono passati al timertask per l'elaborazione
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            currentSample[0] = event.values[0];
            currentSample[1] = event.values[1];
            currentSample[2] = event.values[2];

            harTask.addSample(currentSample);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    // oltre ad aggiornare la ui, chiamo anche il metodo per salvare su db
    public void updateRecognitionUI(String attivita, float confidenza) {
        //tvActivity.setText(attivita);

        String act;
        switch (attivita) {
            case "WALKING":
                act = getString(R.string.activity_walking);
                break;
            case "JOGGING":
                act = getString(R.string.activity_jogging);
                break;
            case "STANDING":
                act = getString(R.string.activity_standing);
                break;
            case "SEDENTARY":
                act = getString(R.string.activity_sedentary);
                break;
            default:
                act = getString(R.string.activity_unknown);
                break;
        }

        tvActivity.setText(act);
        tvConfidence.setText(String.format("Confidenza: %.1f%%", confidenza));
        tvStatus.setText(getString(R.string.label_stato_attivo));

        // salvataggio
        if (!attivita.equals(ultimaAttivitaSalvata)) {
            ultimaAttivitaSalvata = attivita;
            salvaRilevazioni.salvaRilevazione(attivita, confidenza);
        }
    }
}
