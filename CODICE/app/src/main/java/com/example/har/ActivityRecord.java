package com.example.har;

// rappresenta il modello dati per la singola rilevazione
public class ActivityRecord {
    private String attivita;
    private float confidenza;
    private long timestamp;

    // costruttore vuoto obbligatorio per firebase
    public ActivityRecord() {
    }

    public ActivityRecord(String attivita, float confidenza, long timestamp) {
        this.attivita = attivita;
        this.confidenza = confidenza;
        this.timestamp = timestamp;
    }

    public String getAttivita() {
        return attivita;
    }

    public void setAttivita(String attivita) {
        this.attivita = attivita;
    }

    public float getConfidenza() {
        return confidenza;
    }

    public void setConfidenza(float confidenza) {
        this.confidenza = confidenza;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}