package com.greenaltambe.vela

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greenaltambe.vela.extractor.chain.stubTiers
import com.greenaltambe.vela.extractor.newpipe.NewPipeExtractor

class MainActivity : ComponentActivity() {
    private val tiers by lazy { stubTiers(anonymous = NewPipeExtractor()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(tiers)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}