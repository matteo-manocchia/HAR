package com.example.har;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

// legge tutte le rilevazioni salvate su firebase e le mostra in una lista
public class HistoryActivity extends Activity {

    private static final String TAG = "HistoryActivity";

    private ListView lvHistory;
    private TextView tvEmpty;

    // lista dei record firebase che metteremo nella view
    private ArrayList<ActivityRecord> recordList;

    // Adapter per la ListView riferimento a iner class
    private HistoryAdapter adapter;

    private DatabaseReference dbRef;

    // listener firebase
    private ValueEventListener valueEventListener;

    private SimpleDateFormat dateFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        lvHistory = findViewById(R.id.lvHistory);
        tvEmpty   = findViewById(R.id.tvEmpty);

        // inizializzo lista e adpter
        recordList = new ArrayList<>();
        dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
        adapter = new HistoryAdapter();
        lvHistory.setAdapter(adapter);

        // prendo riferimento al nodo rilevazioni di firebase
        dbRef = FirebaseDatabase.getInstance().getReference().child("rilevazioni");

        // funzione per caricare entry
        caricaStoricoDaFirebase();
    }

    // lettura dal databse
    private void caricaStoricoDaFirebase() {
        valueEventListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                recordList.clear();
                for (DataSnapshot postSnapshot : snapshot.getChildren()) {
                    if (postSnapshot != null && postSnapshot.getValue() != null) {
                        // converte i dati dal json alla struttura legibile nell'app
                        ActivityRecord record = postSnapshot.getValue(ActivityRecord.class);
                        if (record != null) {
                            recordList.add(record);
                        }
                    }
                }
                // ordina dal più recente al più vecchio
                Collections.reverse(recordList);
                // notifica l'adapter che i dati sono cambiati
                adapter.notifyDataSetChanged();

                // mostra/nasconde il messaggio "lista vuota"
                if (recordList.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    lvHistory.setVisibility(View.GONE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                    lvHistory.setVisibility(View.VISIBLE);
                }
            }
            // chiamata se lettura fallisce
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Errore lettura Firebase: " + error.getMessage());
            }
        };

        // Esecuzione della query con ordinamento per timestamp
        dbRef.orderByChild("timestamp").addValueEventListener(valueEventListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbRef != null && valueEventListener != null) {
            dbRef.removeEventListener(valueEventListener);
        }
    }


    // ha accesso ai campi di questa activity principale, cioè recordList e dateFormat
    private class HistoryAdapter extends ArrayAdapter<ActivityRecord> {

        public HistoryAdapter() {
            super(HistoryActivity.this, R.layout.history_item, recordList);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inflater = LayoutInflater.from(getContext());
                convertView = inflater.inflate(R.layout.history_item, parent, false);
            }

            // recupera il record alla posizione corrente
            ActivityRecord record = recordList.get(position);

            // popola le view della riga
            TextView tvItemActivity   = convertView.findViewById(R.id.tvItemActivity);
            TextView tvItemConfidence = convertView.findViewById(R.id.tvItemConfidence);
            TextView tvItemTimestamp  = convertView.findViewById(R.id.tvItemTimestamp);

            tvItemActivity.setText(setLabelAttivita(record.getAttivita()));
            tvItemConfidence.setText(String.format("%.1f%%", record.getConfidenza()));
            tvItemTimestamp.setText(dateFormat.format(new Date(record.getTimestamp())));

            return convertView;
        }
    }

    private String setLabelAttivita(String nomeEn) {
        switch (nomeEn) {
            case "WALKING":   return getString(R.string.activity_walking);
            case "JOGGING":   return getString(R.string.activity_jogging);
            case "STANDING":  return getString(R.string.activity_standing);
            case "SEDENTARY": return getString(R.string.activity_sedentary);
            default:          return getString(R.string.activity_unknown);
        }
    }
}
