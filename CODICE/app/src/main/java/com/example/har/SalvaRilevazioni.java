package com.example.har;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

// classe che si occupa di andare a salvare i risultati sul db firebase
public class SalvaRilevazioni {

    private static final String TAG = "SalvaRilevazioni";

    private DatabaseReference dbRef;

    public SalvaRilevazioni() {
        this.dbRef = FirebaseDatabase.getInstance().getReference().child("rilevazioni");
    }

    // crea istanza del record e lo salva
    public void salvaRilevazione(String attivita, float confidenza) {
        String stringID = String.valueOf(System.currentTimeMillis());
        ActivityRecord record = new ActivityRecord(
                attivita,
                confidenza,
                System.currentTimeMillis()
        );

        dbRef.child(stringID).setValue(record).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                    Log.v(TAG, "Rilevazione salvata su Firebase: " + attivita);
                } else {
                    Log.e(TAG, "Errore Firebase: " + task.getException().getMessage());
                }
            }
        });
    }
}