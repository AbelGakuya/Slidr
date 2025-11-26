package com.slidr.slidr.ui.fragments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.slidr.stridr.R
import com.slidr.slidr.db.Run
import com.slidr.slidr.other.Constants.ACTION_PAUSE_SERVICE
import com.slidr.slidr.other.Constants.ACTION_START_OR_RESUME_SERVICE
import com.slidr.slidr.other.Constants.ACTION_STOP_SERVICE
import com.slidr.slidr.other.Constants.MAP_ZOOM
import com.slidr.slidr.other.Constants.POLYLINE_COLOR
import com.slidr.slidr.other.Constants.POLYLINE_WIDTH
import com.slidr.slidr.other.TrackingUtility
import com.slidr.stridr.services.TrackingService
import com.slidr.slidr.ui.viewmodels.MainViewModel
//import com.example.run.databinding.FragmentTrackingBinding
//import com.example.run.db.Run
import com.slidr.slidr.other.Constants
//import com.example.run.other.Constants.ACTION_PAUSE_SERVICE
//import com.example.run.other.Constants.ACTION_START_OR_RESUME_SERVICE
//import com.example.run.other.Constants.ACTION_STOP_SERVICE
//import com.example.run.other.Constants.MAP_ZOOM
//import com.example.run.other.Constants.POLYLINE_COLOR
//import com.example.run.other.Constants.POLYLINE_WIDTH
//import com.example.run.other.TrackingUtility
//import com.example.run.services.TrackingService
//import com.example.run.ui.viewmodels.MainViewModel
import com.slidr.stridr.services.Polyline
import com.slidr.stridr.databinding.FragmentTrackingBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import java.lang.Math.round
import java.util.Calendar
import javax.inject.Inject
import kotlin.jvm.java

const val CANCEL_TRACKING_DIALOG_TAG = "CancelDialog"

@AndroidEntryPoint
class TrackingFragment : Fragment(), EasyPermissions.PermissionCallbacks {

    private lateinit var binding: FragmentTrackingBinding

    private val viewModel: MainViewModel by viewModels()

    private var isTracking = false
    private var isPaused = false   // true if tracking paused
    private var pathPoints = mutableListOf<Polyline>()

    private var map: GoogleMap? = null
    private var curTimeInMillis = 0L

    private var menu: Menu? = null

    @set:Inject
    var weight = 80f

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentTrackingBinding.inflate(inflater,container,false)
        val view = binding.root
        setHasOptionsMenu(true)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.mapView.onCreate(savedInstanceState)
        binding.btnToggleRun.setOnClickListener {
            requestPermissions()
        }

        if (savedInstanceState != null){
            val cancelTrackingDialog = parentFragmentManager.findFragmentByTag(
                CANCEL_TRACKING_DIALOG_TAG
            ) as CancelTrackingDialog?

            cancelTrackingDialog?.setYesListener {
                stopRun()
            }
        }

        binding.btnFinishRun.setOnClickListener {
            zoomToSeeWholeTrack()
            endRunAndSaveToDb()
     //       sendCommandToService(ACTION_STOP_SERVICE)
        }
        binding.mapView.getMapAsync {
            map = it

            // Enable MyLocation layer safely
            try {
                map?.isMyLocationEnabled = true
            } catch (e: SecurityException) {
                e.printStackTrace()
            }

            addAllPolyLines()
        }

        subcribeToObserves()
    }

    private fun subcribeToObserves(){
       TrackingService.isTracking.observe(viewLifecycleOwner, Observer{
           updateTracking(it)
       })

//        TrackingService.isTracking.observe(viewLifecycleOwner) {
//            updateTracking(it)
//        }

        TrackingService.pathPoints.observe(viewLifecycleOwner, Observer {
            pathPoints = it
            addLatestPolyLine()
            moveCameraToUser()
        })

//        TrackingService.pathPoints.observe(viewLifecycleOwner) {
//            pathPoints = it
//            addLatestPolyLine()
//            moveCameraToUser()
//        }

        TrackingService.timeRunInMillis.observe(viewLifecycleOwner, Observer {
            curTimeInMillis = it
            val formattedTime = TrackingUtility.getFormattedStopWatchTime(curTimeInMillis, true)
            binding.tvTimer.text = formattedTime
        })
    }

    private fun toggleRun() {
        if (isTracking) {
            // Tracking is ongoing → pause it
            sendCommandToService(ACTION_PAUSE_SERVICE)
            isPaused = true
            isTracking = false
            binding.btnToggleRun.text = "Continue"
        } else {
            // Start or resume tracking
            sendCommandToService(ACTION_START_OR_RESUME_SERVICE)
            isTracking = true
            isPaused = false
            binding.btnToggleRun.text = "Stop"
        }
    }

    // 🔥 Checks if PRECISE location is allowed
    private fun hasPreciseLocation(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // 🔥 Requests proper permissions (Precise + Background)
    private fun requestPermissions() {
        if (TrackingUtility.hasLocationPermissions(requireContext()) &&
            hasPreciseLocation()
        ) {
            toggleRun() // start or pause/resume tracking
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            EasyPermissions.requestPermissions(
                this,
                "This app requires PRECISE location to track your run.",
                Constants.REQUEST_CODE_LOCATION_PERMISSIONS,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        } else {
            EasyPermissions.requestPermissions(
                this,
                "This app requires PRECISE location to track your run.",
                Constants.REQUEST_CODE_LOCATION_PERMISSIONS,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        if (!hasPreciseLocation()) {
            Toast.makeText(
                requireContext(),
                "Please enable PRECISE location in Settings.",
                Toast.LENGTH_LONG
            ).show()
            AppSettingsDialog.Builder(this).build().show()
            return
        }
        toggleRun()
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            AppSettingsDialog.Builder(this).build().show()
        } else {
            requestPermissions()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }


    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.toolbar_tracking_menu, menu)
        this.menu = menu
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        if (curTimeInMillis > 0L){
            this.menu?.getItem(0)?.isVisible = true

        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId){
            R.id.miCancelTracking -> {
                showCancelTrackingDialogue()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showCancelTrackingDialogue(){
        CancelTrackingDialog().apply {
            setYesListener {
                stopRun()
            }
        }.show(parentFragmentManager,CANCEL_TRACKING_DIALOG_TAG)


    }

    private fun stopRun(){
        binding.tvTimer.text = "00:00:00:00"
        sendCommandToService(ACTION_STOP_SERVICE)
        findNavController().navigate(R.id.action_trackingFragment_to_runFragment)

    }

    private fun updateTracking(isTracking: Boolean){
        this.isTracking = isTracking
        if (!isTracking && curTimeInMillis > 0L){
            binding.btnToggleRun.text = "Start"
            binding.btnFinishRun.visibility = View.VISIBLE
        } else if (isTracking){
            binding.btnToggleRun.text = "Stop"
            menu?.getItem(0)?.isVisible = true
            binding.btnFinishRun.visibility = View.GONE
        }
    }

    private fun moveCameraToUser() {
        if (pathPoints.isEmpty()) return
        val lastPolyline = pathPoints.last()
        if (lastPolyline.isEmpty()) return

        val lastLatLng = lastPolyline.last()

        map?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(lastLatLng, MAP_ZOOM)
        )
    }



    private fun zoomToSeeWholeTrack(){
        val bounds = LatLngBounds.Builder()
        for (polyline in pathPoints){
            for (pos in polyline){
                bounds.include(pos)
            }
        }
        map?.moveCamera(
            CameraUpdateFactory.newLatLngBounds(
                bounds.build(),
                binding.mapView.width,
                binding.mapView.height,
                (binding.mapView.height * 0.05f).toInt()
            )
        )
    }

    private fun endRunAndSaveToDb() {
        map?.snapshot { bmp ->
            var distanceInMeters = 0
            for (polyline in pathPoints) {
                distanceInMeters += TrackingUtility.calculatePolylineLength(polyline).toInt()
            }

            val avgSpeed = round(
                (distanceInMeters / 1000f) /
                        (curTimeInMillis / 1000f / 60 / 60) * 10
            ) / 10f

            val dateTimeStamp = Calendar.getInstance().timeInMillis
            val caloriesBurned = ((distanceInMeters / 1000f) * weight).toInt()

            val run = Run(
                bmp,
                dateTimeStamp,
                avgSpeed,
                distanceInMeters,
                curTimeInMillis,
                caloriesBurned
            )

            viewModel.insertRun(run)

            // FIXED SNACKBAR
            Snackbar.make(binding.root, "Run saved successfully", Snackbar.LENGTH_LONG).show()

            stopRun()
        }
    }


    private fun addAllPolyLines(){
        for (polyLine in pathPoints){
            val polylineOptions = PolylineOptions()
                .color(POLYLINE_COLOR)
                .width(POLYLINE_WIDTH)
                .addAll(polyLine)
            map?.addPolyline(polylineOptions)
        }
    }

    private fun addLatestPolyLine(){
        if (pathPoints.isNotEmpty() && pathPoints.last().size > 1 ){
            val preLastLatLng = pathPoints.last()[pathPoints.last().size - 2]
            val lastLatLng = pathPoints.last().last()
            val polylineOptions = PolylineOptions()
                .color(POLYLINE_COLOR)
                .width(POLYLINE_WIDTH)
                .add(preLastLatLng)
                .add(lastLatLng)
            map?.addPolyline(polylineOptions)
        }
    }

    private fun sendCommandToService(action: String) =
        Intent(requireContext(),TrackingService::class.java).also {
            it.action = action
            requireContext().startService(it)
        }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        binding.mapView.onStop()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }




}