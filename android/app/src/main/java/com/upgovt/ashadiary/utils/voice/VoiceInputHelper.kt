package com.upgovt.ashadiary.utils.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * VoiceInputHelper - Hindi voice input using SpeechRecognizer
 * No-keyboard policy - all text input via voice
 */
class VoiceInputHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "VoiceInputHelper"
    }
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    
    /**
     * Start listening for voice input
     */
    fun startListening(callback: VoiceInputCallback) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            callback.onError("आवाज़ पहचान उपलब्ध नहीं है")
            return
        }
        
        if (is Listening) {
            Log.w(TAG, "Already listening")
            return
        }
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    callback.onReadyForSpeech()
                }
                
                override fun onBeginningOfSpeech() {
                    callback.onBeginningOfSpeech()
                }
                
                override fun onRmsChanged(rmsdB: Float) {
                    // Volume indicator
                }
                
                override fun onBufferReceived(buffer: ByteArray?) {}
                
                override fun onEndOfSpeech() {
                    isListening = false
                    callback.onEndOfSpeech()
                }
                
                override fun onError(error: Int) {
                    isListening = false
                    val errorMessage = getErrorMessage(error)
                    callback.onError(errorMessage)
                }
                
                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (matches != null && matches.isNotEmpty()) {
                        callback.onResult(matches[0])
                    } else {
                        callback.onError("कोई पाठ नहीं पहचाना गया")
                    }
                }
                
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (matches != null && matches.isNotEmpty()) {
                        callback.onPartialResult(matches[0])
                    }
                }
                
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")  // Hindi
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "अभी बोलें...")
        }
        
        speechRecognizer?.startListening(intent)
    }
    
    /**
     * Stop listening
     */
    fun stopListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
        }
    }
    
    /**
     * Destroy speech recognizer
     */
    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
    }
    
    /**
     * Get error message in Hindi
     */
    private fun getErrorMessage(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "ऑडियो रिकॉर्डिंग में त्रुटि"
            SpeechRecognizer.ERROR_CLIENT -> "क्लाइंट त्रुटि"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "माइक्रोफोन अनुमति नहीं है"
            SpeechRecognizer.ERROR_NETWORK -> "नेटवर्क त्रुटि"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "नेटवर्क टाइमआउट"
            SpeechRecognizer.ERROR_NO_MATCH -> "कोई मिलान नहीं मिला"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "पहचानकर्ता व्यस्त है"
            SpeechRecognizer.ERROR_SERVER -> "सर्वर त्रुटि"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "कोई भाषण नहीं सुना गया"
            else -> "आवाज़ पहचान विफल"
        }
    }
}

/**
 * Voice input callback interface
 */
interface VoiceInputCallback {
    fun onReadyForSpeech() {}
    fun onBeginningOfSpeech() {}
    fun onEndOfSpeech() {}
    fun onResult(text: String)
    fun onPartialResult(text: String) {}
    fun onError(error: String)
}
