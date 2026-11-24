package com.cet4.cards;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

/** 单词发音（系统 TTS，无需联网下载词库） */
public class Tts {
    private TextToSpeech tts;

    public Tts(Context c) {
        tts = new TextToSpeech(c.getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS && tts != null) {
                try {
                    tts.setLanguage(Locale.US);
                    tts.setSpeechRate(0.9f);
                } catch (Exception ignored) {
                }
            }
        });
    }

    public void speak(String word) {
        if (tts == null || word == null || word.length() == 0) return;
        try {
            tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, "w");
        } catch (Exception ignored) {
        }
    }

    public void shutdown() {
        if (tts != null) {
            try { tts.shutdown(); } catch (Exception ignored) { }
            tts = null;
        }
    }
}
