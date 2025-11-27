package com.slidr.stridr.ui

import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.slidr.stridr.R



import com.slidr.slidr.other.Constants.ACTION_SHOW_TRACKING_FRAGMENT
//import com.amg.run.ui.fragments.AppDisclosureDialog
import com.slidr.stridr.databinding.ActivityMainBinding
//import com.example.run.databinding.ActivityMainBinding
//import com.example.run.other.Constants.ACTION_SHOW_TRACKING_FRAGMENT
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

// Change the title programmatically
        binding.toolbarTitle.text = "Velo"


        // Correct NavHostFragment initialization
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
        val navController = navHostFragment.navController

        // Navigate via intent if needed
        navigateToTrackingFragmentIfNeeded(intent)

        // Navigation bar styling
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.navigationBarColor = ContextCompat.getColor(this, R.color.surfaceColor)
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }



    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        navigateToTrackingFragmentIfNeeded(intent)
    }
//
//    private fun showAppDisclosureDialog(){
//       val dialog = MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
//           .setTitle("App Disclosure")
//            .setMessage("RUN collects location data using Google Maps to enable access to current location updates requests even when the app is closed or not in use, for subsequent calculation of your total distance covered.")
//            .setIcon(R.drawable.ic_run)
//            .setPositiveButton("Ok"){_,_ ->
//                yesListener?.let {yes ->
//                    yes()
//                }
//            }
////            .setNegativeButton("Deny"){ dialogInterface,_ ->
////                dialogInterface.cancel()
////            }
//            .create()
//    }

    private fun navigateToTrackingFragmentIfNeeded(intent: Intent?){
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
        if (intent?.action == ACTION_SHOW_TRACKING_FRAGMENT){
            navHostFragment.findNavController().navigate(R.id.action_global_trackingFragment)
        }
    }
}