package com.example

import android.content.Intent
import android.net.Uri
import android.nfc.NdefMessage
import timber.log.Timber
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.ui.DeviceAPIScreen
import com.example.ui.DeviceAPIViewModel
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  private val viewModel: DeviceAPIViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (BuildConfig.DEBUG) {
      Timber.plant(timber.log.Timber.DebugTree())
    }
    Timber.plant(CrashlyticsTree())
    com.example.ui.theme.ThemeManager.init(applicationContext)
    enableEdgeToEdge()
    handleIntent(intent)
    setContent {
      MyApplicationTheme {
        DeviceAPIScreen(
          viewModel = viewModel,
          modifier = Modifier.fillMaxSize()
        )
      }
    }
  }

  private class CrashlyticsTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
      if (t != null) {
        FirebaseCrashlytics.getInstance().recordException(t)
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleIntent(intent)
  }

  private fun handleIntent(intent: Intent?) {
    if (intent == null) return
    val action = intent.action
    if ("com.example.ACTION_SHORTCUT" == action) {
      val tab = intent.getStringExtra("tab")
      if (tab != null) {
        viewModel.requestTab(tab)
      }
    } else if (NfcAdapter.ACTION_NDEF_DISCOVERED == action) {
      val dataUri: Uri? = intent.data
      viewModel.evaluateTriggers("NFC Tag Detected", "Physical NDEF tag tapped: ${dataUri?.toString() ?: "Raw NDEF Payload"}")
      if (dataUri != null) {
        viewModel.initiatePairing(dataUri.toString(), "")
      } else {
        // Fallback: Parse raw NDEF payload
        val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        if (rawMsgs != null && rawMsgs.isNotEmpty()) {
          val msg = rawMsgs[0] as NdefMessage
          val record = msg.records.getOrNull(0)
          record?.payload?.let { payloadBytes ->
            try {
              val payloadString = String(payloadBytes, Charsets.UTF_8)
              if (payloadString.contains("deviceapi://pair")) {
                val urlStart = payloadString.indexOf("deviceapi://pair")
                viewModel.initiatePairing(payloadString.substring(urlStart), "")
              }
            } catch (e: Exception) {
              e.printStackTrace()
            }
          }
        }
      }
    }
  }
}
