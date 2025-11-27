package com.slidr.slidr.ui.fragments

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.slidr.stridr.R
import com.slidr.slidr.adapters.RunAdapter
import com.slidr.slidr.other.Constants.REQUEST_CODE_LOCATION_PERMISSIONS
import com.slidr.slidr.other.SortType
import com.slidr.slidr.other.TrackingUtility
import com.slidr.slidr.ui.viewmodels.MainViewModel
import com.slidr.stridr.databinding.FragmentRunBinding
//import com.example.run.adapters.RunAdapter
//import com.example.run.databinding.FragmentRunBinding
//import com.example.run.other.Constants.REQUEST_CODE_LOCATION_PERMISSIONS
//import com.example.run.other.SortType
//import com.example.run.other.TrackingUtility
//import com.example.run.ui.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions


@AndroidEntryPoint
class RunFragment : Fragment(), EasyPermissions.PermissionCallbacks {

    private val viewModel: MainViewModel by viewModels()

    private lateinit var runAdapter: RunAdapter

    private lateinit var binding: FragmentRunBinding



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentRunBinding.inflate(inflater,container,false)
        val view = binding.root




        when(viewModel.sortType) {
            SortType.DATE -> binding.spFilter.setSelection(0)
            SortType.RUNNING_TIME -> binding.spFilter.setSelection(1)
            SortType.DISTANCE -> binding.spFilter.setSelection(2)
            SortType.AVG_SPEED -> binding.spFilter.setSelection(3)
            SortType.CALORIES_BURNED -> binding.spFilter.setSelection(4)
        }

        binding.spFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                adapterView: AdapterView<*>?,
                view: View?,
                pos: Int,
                id: Long
            ) {
                when(pos){
                    0 -> viewModel.sortRuns(SortType.DATE)
                    1 -> viewModel.sortRuns(SortType.RUNNING_TIME)
                    2 -> viewModel.sortRuns(SortType.DISTANCE)
                    3 -> viewModel.sortRuns(SortType.AVG_SPEED)
                    4 -> viewModel.sortRuns(SortType.CALORIES_BURNED)
                }


            }

            override fun onNothingSelected(parent: AdapterView<*>?) {

            }
        }


        viewModel.runs.observe(viewLifecycleOwner, Observer {
            runAdapter.submitList(it)
        })


        val fab = binding.fabCenter
        fab.setOnClickListener {
            findNavController().navigate(R.id.action_runFragment_to_trackingFragment)
        }

        // Navigate to Statistics
        binding.navStats.setOnClickListener {
            findNavController().navigate(R.id.action_runFragment_to_statisticsFragment)
        }

// Navigate to Runs (this fragment itself) – optional
        binding.navRun.setOnClickListener {
            // do nothing or refresh data
        }

        binding.navRun.alpha = 1f
        binding.navStats.alpha = 0.4f




        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

       // showAppDisclosureDialog()
        requestPermissions()
        setupRecyclerView()
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_settings, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                findNavController().navigate(R.id.action_runFragment_to_settingsFragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }






    private fun showAppDisclosureDialog(){
        AppDisclosureDialog()
//            .apply {
//            setYesListener {
//                requestPermissions()
//            }
//        }
            .show(parentFragmentManager,CANCEL_TRACKING_DIALOG_TAG)
    }

    private fun setupRecyclerView() = binding.rvRuns.apply {
        runAdapter = RunAdapter()
        adapter = runAdapter
        layoutManager = LinearLayoutManager(requireContext())
    }

    private fun requestPermissions(){
        if (TrackingUtility.hasLocationPermissions(requireContext())){
            return
        }

        showAppDisclosureDialog()

//        EasyPermissions.requestPermissions(
//            this,
//            "You need to accept location permissions to use this app.",
//            REQUEST_CODE_LOCATION_PERMISSIONS,
//            Manifest.permission.ACCESS_COARSE_LOCATION,
//            Manifest.permission.ACCESS_FINE_LOCATION
//        )

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q){
            EasyPermissions.requestPermissions(
                this,
                "You need to accept location permissions to use this app.",
                REQUEST_CODE_LOCATION_PERMISSIONS,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            EasyPermissions.requestPermissions(
                this,
                "You need to accept Location Permissions to use this app.",
                REQUEST_CODE_LOCATION_PERMISSIONS,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        }
    }



    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        if (EasyPermissions.somePermissionPermanentlyDenied(this,perms)){
            AppSettingsDialog.Builder(this).build().show()
        } else{
            requestPermissions()
        }
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
       // Toast.makeText(requireContext(),"Permission Granted",Toast.LENGTH_LONG).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode,permissions,grantResults,this)
    }
}
