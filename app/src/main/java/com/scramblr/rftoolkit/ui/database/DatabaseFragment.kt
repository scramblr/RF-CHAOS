package com.scramblr.rftoolkit.ui.database

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.scramblr.rftoolkit.RFToolkitApp
import com.scramblr.rftoolkit.data.repository.NetworkRepository
import com.scramblr.rftoolkit.databinding.FragmentDatabaseBinding
import kotlinx.coroutines.launch

class DatabaseFragment : Fragment() {

    private var _binding: FragmentDatabaseBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var repository: NetworkRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDatabaseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        repository = NetworkRepository(RFToolkitApp.instance.database)
        
        loadStatistics()
        setupButtons()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadStatistics() {
        lifecycleScope.launch {
            val stats = repository.getStatistics()
            binding.tvTotalNetworks.text = stats.totalNetworks.toString()
            binding.tvWifiCount.text = stats.wifiCount.toString()
            binding.tvBluetoothCount.text = (stats.bluetoothCount + stats.bleCount).toString()
            binding.tvLocationCount.text = stats.totalLocations.toString()
            binding.tvSessionCount.text = stats.sessionsCount.toString()
        }
    }

    private fun setupButtons() {
        binding.btnExportCsv.setOnClickListener {
            exportData()
        }

        binding.btnClearData.setOnClickListener {
            confirmClearData()
        }
    }

    private fun exportData() {
        lifecycleScope.launch {
            try {
                val file = repository.exportToCsv(requireContext())
                
                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.provider",
                    file
                )
                
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                startActivity(Intent.createChooser(intent, "Share CSV Export"))
                
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Export failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun confirmClearData() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear All Data")
            .setMessage("This will permanently delete all scanned networks and locations. This action cannot be undone.")
            .setPositiveButton("Delete All") { _, _ ->
                lifecycleScope.launch {
                    repository.clearAllData()
                    loadStatistics()
                    Toast.makeText(requireContext(), "All data cleared", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
