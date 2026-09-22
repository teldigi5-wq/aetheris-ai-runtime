package io.aetheris.orchestrator.voice;public interface SpeechToTextAdapter {String id();boolean available();String transcribe(byte[] pcm16,int sampleRateHz);}
