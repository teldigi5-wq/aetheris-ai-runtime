package io.aetheris.orchestrator.voice;public interface TextToSpeechAdapter {String id();boolean available();byte[] synthesize(String text,int sampleRateHz);}
