package com.example.har;

import android.util.Log;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.modeldownloader.CustomModel;
import com.google.firebase.ml.modeldownloader.CustomModelDownloadConditions;
import com.google.firebase.ml.modeldownloader.DownloadType;
import com.google.firebase.ml.modeldownloader.FirebaseModelDownloader;
import org.tensorflow.lite.Interpreter;
import java.io.File;

// serve per il caricamento e l'utilizzo del modello
public class HARClassifier {

    private static final String TAG = "HARClassifier";

    // numero di sample in ogni finestra di input
    private static final int N_TIMESTEPS = 200;
    // numero di canali utilizzati per singolo segnale
    private static final int N_FEATURES  = 3;
    // classi che il modello riconosce
    private static final int N_CLASSES   = 4;

    private static final String[] LABELS = {
            "WALKING",
            "JOGGING",
            "STANDING",
            "SEDENTARY"
    };

    private static final String FIREBASE_MODEL_NAME = "har_model_quantized";

    // servirà per eseguire il modello
    private Interpreter interpreter;

    // interfaccia usata per gestire se il modello è pronto o no
    public interface ModelReadyListener {
        void onModelReady();
        void onModelError(String errorMessage);
    }


    public HARClassifier(ModelReadyListener listener) {
        downloadModelFromFirebase(listener);
    }

    private void downloadModelFromFirebase(ModelReadyListener listener) {
        // condizioni per il downolad del modello
        CustomModelDownloadConditions conditions = new CustomModelDownloadConditions.Builder()
                .build();
                //.requireWifi()

        // download
        FirebaseModelDownloader.getInstance()
                .getModel(FIREBASE_MODEL_NAME, DownloadType.LATEST_MODEL, conditions)
                .addOnSuccessListener(new OnSuccessListener<CustomModel>() {
                    @Override
                    public void onSuccess(CustomModel model) {
                        File modelFile = model.getFile();
                        if (modelFile != null) {
                            // creazione interprete modello
                            Interpreter.Options opts = new Interpreter.Options();
                            opts.setNumThreads(2);
                            interpreter = new Interpreter(modelFile, opts);

                            Log.v(TAG, "Modello scaricato e interprete creato con successo");
                            listener.onModelReady();
                        } else {
                            Log.e(TAG, "File modello null dopo il download");
                            listener.onModelError("Modello non disponibile");
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Errore download modello: " + e.getMessage());
                    listener.onModelError(e.getMessage());
                });
    }


    // inferenza modello
    public float[] classify(float[][] sensorData) {
        if (interpreter == null) {
            Log.e(TAG, "Interprete non inizializzato!");
            return new float[]{-1, 0};
        }

        //preparazione tensore di input
        float[][][] input = new float[1][N_TIMESTEPS][N_FEATURES];
        for (int t = 0; t < N_TIMESTEPS; t++) {
            for (int f = 0; f < N_FEATURES; f++) {
                input[0][t][f] = sensorData[t][f];
            }
        }

        // creazione tensore di output
        // conterrà 4 valori che rappresentano la probabilità per le 4 azioni
        float[][] output = new float[1][N_CLASSES];

        interpreter.run(input, output);

        // scorro i risultati e scelgo quello con il valore più alto
        int bestIndex = 0;
        float bestProb = output[0][0];
        for (int i = 1; i < N_CLASSES; i++) {
            if (output[0][i] > bestProb) {
                bestProb = output[0][i];
                bestIndex = i;
            }
        }
        return new float[]{bestIndex, bestProb * 100f};
    }

    // prende l'indice e mi ritorna il nome dell'attività rilevata
    public static String getActivityName(int index) {
        if (index >= 0 && index < LABELS.length) {
            return LABELS[index];
        }
        return "UNKNOWN";
    }

    public static String[] getLabels() {
        return LABELS;
    }

    public static int getTimesteps()  { return N_TIMESTEPS; }
    public static int getFeatures()   { return N_FEATURES; }
    public static int getNumClasses() { return N_CLASSES; }

    // metodo che rilascia le risorse allocate dall'interprete
    // chiamato dalla onDestroy della main activity
    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
            Log.v(TAG, "Interprete chiuso.");
        }
    }
}
