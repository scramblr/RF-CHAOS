package com.scramblr.rftoolkit.ui.dashboard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.scramblr.rftoolkit.R
import com.scramblr.rftoolkit.RFToolkitApp
import com.scramblr.rftoolkit.data.models.Network
import com.scramblr.rftoolkit.data.models.NetworkType
import com.scramblr.rftoolkit.data.repository.NetworkRepository
import com.scramblr.rftoolkit.databinding.FragmentDashboardBinding
import com.scramblr.rftoolkit.databinding.ItemNetworkBinding
import com.scramblr.rftoolkit.databinding.BottomSheetNetworkDetailBinding
import com.scramblr.rftoolkit.services.ScanningService
import com.scramblr.rftoolkit.utils.SignalUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var repository: NetworkRepository
    private lateinit var adapter: NetworkAdapter
    
    private var scanningService: ScanningService? = null
    private var serviceBound = false
    private var statsUpdateJob: Job? = null
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as ScanningService.LocalBinder
            scanningService = localBinder.getService()
            serviceBound = true
            observeServiceState()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            scanningService = null
            serviceBound = false
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        repository = NetworkRepository(RFToolkitApp.instance.database)
        
        setupRecyclerView()
        setupButtons()
        observeNetworks()
        startStatsUpdates()
    }
    
    override fun onStart() {
        super.onStart()
        Intent(requireContext(), ScanningService::class.java).also { intent ->
            requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }
    
    override fun onStop() {
        super.onStop()
        if (serviceBound) {
            requireContext().unbindService(serviceConnection)
            serviceBound = false
        }
    }
    
    override fun onDestroyView() {
        statsUpdateJob?.cancel()
        super.onDestroyView()
        _binding = null
    }
    
    private fun setupRecyclerView() {
        adapter = NetworkAdapter { network ->
            showNetworkDetail(network)
        }
        binding.recyclerNetworks.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@DashboardFragment.adapter
            setHasFixedSize(true)
            itemAnimator = null // Disable animations for performance
        }
    }
    
    private fun setupButtons() {
        binding.btnStartStop.setOnClickListener {
            if (scanningService?.isScanning?.value == true) {
                stopScanning()
            } else {
                startScanning()
            }
        }
    }
    
    private fun startScanning() {
        val intent = Intent(requireContext(), ScanningService::class.java).apply {
            action = ScanningService.ACTION_START
        }
        requireContext().startForegroundService(intent)
        updateScanButton(true)
    }
    
    private fun stopScanning() {
        val intent = Intent(requireContext(), ScanningService::class.java).apply {
            action = ScanningService.ACTION_STOP
        }
        requireContext().startService(intent)
        updateScanButton(false)
    }
    
    private fun updateScanButton(isScanning: Boolean) {
        binding.btnStartStop.text = if (isScanning) "STOP SCAN" else "START SCAN"
        binding.btnStartStop.setBackgroundColor(
            resources.getColor(if (isScanning) R.color.error else R.color.primary, null)
        )
        binding.statusIndicator.visibility = if (isScanning) View.VISIBLE else View.GONE
    }
    
    private fun observeServiceState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    scanningService?.isScanning?.collectLatest { isScanning ->
                        updateScanButton(isScanning)
                    }
                }
                
                launch {
                    scanningService?.currentPosition?.collectLatest { location ->
                        location?.let {
                            binding.tvGps.text = String.format(
                                Locale.US,
                                "%.6f, %.6f (±%.0fm)",
                                it.latitude, it.longitude, it.accuracy
                            )
                        }
                    }
                }
            }
        }
    }
    
    private fun startStatsUpdates() {
        statsUpdateJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                loadStatistics()
                delay(1000) // Update every second
            }
        }
    }
    
    private fun loadStatistics() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val stats = repository.getStatistics()
                _binding?.let { b ->
                    b.tvWifiCount.text = formatNumber(stats.wifiCount)
                    b.tvBleCount.text = formatNumber(stats.bluetoothCount + stats.bleCount)
                    b.tvNewCount.text = formatNumber(scanningService?.newNetworkCount?.value ?: 0)
                    b.tvTotalCount.text = formatNumber(stats.totalNetworks)
                    b.tvNetworkCount.text = formatNumber(scanningService?.networkCount?.value ?: stats.totalNetworks)
                }
            } catch (e: Exception) {
                // Ignore errors during stats loading
            }
        }
    }
    
    private fun formatNumber(num: Int): String {
        return when {
            num >= 1000000 -> String.format(Locale.US, "%.1fM", num / 1000000.0)
            num >= 10000 -> String.format(Locale.US, "%.1fK", num / 1000.0)
            else -> num.toString()
        }
    }
    
    private fun observeNetworks() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.getNetworks(50, 0).collectLatest { networks ->
                    adapter.submitList(networks.toList())
                }
            }
        }
    }
    
    private fun showNetworkDetail(network: Network) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = BottomSheetNetworkDetailBinding.inflate(layoutInflater)
        
        sheetBinding.apply {
            tvSsid.text = network.ssid.ifEmpty { "(Hidden Network)" }
            tvBssid.text = network.bssid
            tvType.text = network.type.name
            tvSecurity.text = network.security.name
            tvRssi.text = "${network.bestLevel} dBm"
            tvChannel.text = network.channel?.toString() ?: "N/A"
            tvFrequency.text = if (network.frequency != null) "${network.frequency} MHz" else "N/A"
            
            val distance = SignalUtils.estimateDistance(network.bestLevel)
            tvDistance.text = String.format("~%.1f m", distance)
            
            val quality = SignalUtils.rssiToQuality(network.bestLevel)
            tvQuality.text = "$quality%"
            progressQuality.progress = quality
            
            // Color RSSI based on signal
            val rssiColor = when {
                quality >= 80 -> R.color.signal_excellent
                quality >= 60 -> R.color.signal_good
                quality >= 40 -> R.color.signal_fair
                quality >= 20 -> R.color.signal_weak
                else -> R.color.signal_poor
            }
            tvRssi.setTextColor(resources.getColor(rssiColor, null))
            
            tvFirstSeen.text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(Date(network.firstSeen))
            tvLastSeen.text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(Date(network.lastSeen))
            tvTimesObserved.text = network.timesObserved.toString()
            
            if (network.bestLat != 0.0 && network.bestLon != 0.0) {
                tvLocation.text = String.format("%.6f, %.6f", network.bestLat, network.bestLon)
            } else {
                tvLocation.text = "No location data"
            }
            
            tvManufacturer.text = network.manufacturer ?: "Unknown"
            
            // RPA indicator
            if (network.isRpa) {
                tvRpa.visibility = View.VISIBLE
                tvRpa.text = "RPA (Randomized Private Address)"
            } else {
                tvRpa.visibility = View.GONE
            }
            
            // Close button
            btnClose.setOnClickListener {
                dialog.dismiss()
            }
            
            // Navigate button - hide since we're in dashboard
            btnNavigate.visibility = View.GONE
        }
        
        dialog.setContentView(sheetBinding.root)
        dialog.show()
    }
}

class NetworkDiffCallback : DiffUtil.ItemCallback<Network>() {
    override fun areItemsTheSame(oldItem: Network, newItem: Network): Boolean {
        return oldItem.bssid == newItem.bssid
    }
    
    override fun areContentsTheSame(oldItem: Network, newItem: Network): Boolean {
        return oldItem.lastSeen == newItem.lastSeen && oldItem.bestLevel == newItem.bestLevel
    }
}

class NetworkAdapter(
    private val onNetworkClick: (Network) -> Unit
) : ListAdapter<Network, NetworkAdapter.ViewHolder>(NetworkDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNetworkBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding, onNetworkClick)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class ViewHolder(
        private val binding: ItemNetworkBinding,
        private val onNetworkClick: (Network) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
        
        fun bind(network: Network) {
            binding.apply {
                root.setOnClickListener { onNetworkClick(network) }
                
                tvSsid.text = network.ssid.ifEmpty { "(Hidden)" }
                tvBssid.text = network.bssid
                tvRssi.text = "${network.bestLevel} dBm"
                tvLastSeen.text = timeFormat.format(Date(network.lastSeen))
                
                val quality = SignalUtils.rssiToQuality(network.bestLevel)
                val color = when {
                    quality >= 80 -> R.color.signal_excellent
                    quality >= 60 -> R.color.signal_good
                    quality >= 40 -> R.color.signal_fair
                    quality >= 20 -> R.color.signal_weak
                    else -> R.color.signal_poor
                }
                tvRssi.setTextColor(root.context.getColor(color))
                
                val icon = when (network.type) {
                    NetworkType.WIFI -> R.drawable.ic_wifi
                    NetworkType.BLUETOOTH -> R.drawable.ic_bluetooth
                    NetworkType.BLE -> R.drawable.ic_ble
                    NetworkType.CELLULAR -> R.drawable.ic_cell
                }
                ivType.setImageResource(icon)
                
                if (network.type == NetworkType.WIFI) {
                    tvSecurity.visibility = View.VISIBLE
                    tvSecurity.text = network.security.name
                } else {
                    tvSecurity.visibility = View.GONE
                }
                
                tvRpa.visibility = if (network.isRpa) View.VISIBLE else View.GONE
                
                if (network.channel != null && network.channel > 0) {
                    tvChannel.visibility = View.VISIBLE
                    tvChannel.text = "Ch ${network.channel}"
                } else {
                    tvChannel.visibility = View.GONE
                }
                
                val distance = SignalUtils.estimateDistance(network.bestLevel)
                tvDistance.text = if (distance > 0) "~${String.format(Locale.US, "%.1f", distance)}m" else ""
            }
        }
    }
}
