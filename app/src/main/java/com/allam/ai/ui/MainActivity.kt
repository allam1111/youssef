package com.allam.ai.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.allam.ai.service.VoiceAssistantService
import com.allam.ai.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.allam.ai.logic.ActionHandler
import com.allam.ai.logic.SpeechManager
import com.allam.ai.logic.TtsManager

class MainActivity : ComponentActivity() {
    private lateinit var speechManager: SpeechManager
    private lateinit var ttsManager: TtsManager
    private lateinit var actionHandler: ActionHandler
    private val apiKey = "AIzaSyC503C9IVomUJEs-KWEvPmssdHMz1anszc"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        actionHandler = ActionHandler(this)
        ttsManager = TtsManager(this) {
            // Initial greeting in Egyptian Arabic
            ttsManager.speak("أهلاً بيك يا ريس، أنا علام مساعدك الشخصي. أؤمرني؟")
        }

        setContent {
            AllamTheme {
                VoiceAssistantScreen(
                    onStartSpeech = { onRms ->
                        speechManager = SpeechManager(this, 
                            onResult = { text -> handleSpeechResult(text) },
                            onRmsChanged = { rms -> onRms(rms) }
                        )
                        speechManager.startListening()
                    },
                    onStopSpeech = {
                        speechManager.stopListening()
                    }
                )
            }
        }
    }

    private val messagesState = mutableStateListOf<Pair<String, Boolean>>()

    private fun handleSpeechResult(text: String) {
        messagesState.add(text to true)
        
        val intentResult = actionHandler.handleCommand(text)
        if (intentResult.first) {
            val response = intentResult.second ?: "تمام يا باشا"
            messagesState.add(response to false)
            ttsManager.speak(response)
        } else {
            // Ask AI
            lifecycleScope.launch {
                val aiResponse = com.allam.ai.data.AiClient.askAi(apiKey, text)
                messagesState.add(aiResponse to false)
                ttsManager.speak(aiResponse)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsManager.destroy()
        if (::speechManager.isInitialized) speechManager.destroy()
    }

    @Composable
    fun VoiceAssistantScreen(
        onStartSpeech: ((Float) -> Unit) -> Unit,
        onStopSpeech: () -> Unit
    ) {
        var isListening by remember { mutableStateOf(false) }
        var currentRms by remember { mutableFloatOf(0f) }
        
        val messages = remember { messagesState }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "ALLAM AI",
                style = MaterialTheme.typography.headlineLarge.copy(
                    color = CyanNeon,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 4.sp
                ),
                modifier = Modifier.padding(top = 16.dp)
            )
            
            Divider(color = CyanNeon.copy(alpha = 0.3f), thickness = 1.dp, modifier = Modifier.padding(vertical = 16.dp))

            // Chat History
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                reverseLayout = false
            ) {
                items(messages) { message ->
                    ChatBubble(message.first, message.second)
                }
            }

            // Voice Wave Animation & Mic Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isListening) {
                    VoiceWaveform(currentRms)
                }
                
                FloatingMicButton(
                    isListening = isListening,
                    onClick = { 
                        isListening = !isListening 
                        if (isListening) {
                            onStartSpeech { rms -> currentRms = rms }
                        } else {
                            onStopSpeech()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun VoiceWaveform(rms: Float) {
    val infiniteTransition = rememberInfiniteTransition()
    
    // Normalize RMS to a scale factor (rms is usually -2 to 10)
    val rmsNormalized = ((rms + 2f) / 12f).coerceIn(0.1f, 1.5f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(7) { index ->
            val heightScale by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 0.8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(300 + index * 50, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .width(6.dp)
                    .height(80.dp * heightScale * rmsNormalized)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(CyanNeon, PurpleNeon)
                        )
                    )
            )
        }
    }
}

@Composable
fun FloatingMicButton(isListening: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition()
    val glowScale by if (isListening) {
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    val buttonColor = if (isListening) Color.Red else CyanNeon

    Button(
        onClick = onClick,
        modifier = Modifier
            .size(90.dp)
            .shadow(25.dp, CircleShape, spotColor = buttonColor)
            .graphicsLayer(scaleX = glowScale, scaleY = glowScale)
            .border(2.dp, if (isListening) Color.White.copy(alpha = 0.5f) else Color.Transparent, CircleShape),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(if (isListening) "⏹️" else "🎙️", fontSize = 36.sp)
    }
}
