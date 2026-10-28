package com.example.brickearthquakesapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.brickearthquakesapp.ui.EarthquakesRoute
import com.example.brickearthquakesapp.ui.EarthquakesViewModel
import com.example.brickearthquakesapp.ui.theme.BrickEarthquakesAppTheme

class MainActivity : ComponentActivity() {

    private val viewModel: EarthquakesViewModel by viewModels {
        val container = (application as BrickApp).container
        viewModelFactory {
            initializer { EarthquakesViewModel(container.earthquakeRepository, container.locationProvider) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BrickEarthquakesAppTheme {
                EarthquakesRoute(viewModel)
            }
        }
    }
}
