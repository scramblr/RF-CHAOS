package com.scramblr.rftoolkit.ui.settings

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.scramblr.rftoolkit.R
import com.scramblr.rftoolkit.RFToolkitApp
import com.scramblr.rftoolkit.data.models.Irk
import com.scramblr.rftoolkit.data.repository.NetworkRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SettingsFragment : PreferenceFragmentCompat() {
    
    private lateinit var repository: NetworkRepository
    
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        
        repository = NetworkRepository(RFToolkitApp.instance.database)
        
        // Handle IRK management click
        findPreference<Preference>("manage_irks")?.setOnPreferenceClickListener {
            showIrkManagementDialog()
            true
        }
        
        // Handle extract IRKs click
        findPreference<Preference>("extract_irks_root")?.setOnPreferenceClickListener {
            showExtractIrksDialog()
            true
        }
    }
    
    private fun showIrkManagementDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_irk_management, null)
        
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recyclerIrks)
        val tvEmpty = dialogView.findViewById<TextView>(R.id.tvEmpty)
        val btnAdd = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAddIrk)
        
        val adapter = IrkAdapter(
            onDelete = { irk -> deleteIrk(irk, recyclerView, tvEmpty) },
            onEdit = { irk -> showEditIrkDialog(irk) }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        
        // Load IRKs
        viewLifecycleOwner.lifecycleScope.launch {
            repository.getAllIrks().collectLatest { irks ->
                adapter.submitList(irks)
                tvEmpty.visibility = if (irks.isEmpty()) View.VISIBLE else View.GONE
                recyclerView.visibility = if (irks.isEmpty()) View.GONE else View.VISIBLE
            }
        }
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Manage IRKs")
            .setView(dialogView)
            .setNegativeButton("Close", null)
            .create()
        
        btnAdd.setOnClickListener {
            showAddIrkDialog()
        }
        
        dialog.show()
    }
    
    private fun showAddIrkDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_irk, null)
        
        val etIrk = dialogView.findViewById<TextInputEditText>(R.id.etIrk)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etName)
        val etDeviceType = dialogView.findViewById<TextInputEditText>(R.id.etDeviceType)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add IRK")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val irkValue = etIrk.text?.toString()?.trim() ?: ""
                val name = etName.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                val deviceType = etDeviceType.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                
                if (irkValue.isNotEmpty()) {
                    addIrk(irkValue, name, deviceType)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showEditIrkDialog(irk: Irk) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_irk, null)
        
        val etIrk = dialogView.findViewById<TextInputEditText>(R.id.etIrk)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etName)
        val etDeviceType = dialogView.findViewById<TextInputEditText>(R.id.etDeviceType)
        
        // Pre-fill with existing values
        etIrk.setText(irk.irk)
        etIrk.isEnabled = false // Can't change IRK, only metadata
        etName.setText(irk.name ?: "")
        etDeviceType.setText(irk.deviceType ?: "")
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit IRK")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                val deviceType = etDeviceType.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        repository.updateIrk(irk.id, name, deviceType)
                        Toast.makeText(requireContext(), "IRK updated", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun addIrk(irkValue: String, name: String?, deviceType: String?) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                repository.addIrk(irkValue, name, deviceType)
                Toast.makeText(requireContext(), "IRK added successfully", Toast.LENGTH_SHORT).show()
            } catch (e: IllegalArgumentException) {
                Toast.makeText(requireContext(), "Invalid IRK: ${e.message}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error adding IRK: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun deleteIrk(irk: Irk, recyclerView: RecyclerView, tvEmpty: TextView) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete IRK")
            .setMessage("Delete IRK \"${irk.name ?: irk.irk.take(16)}...\"?")
            .setPositiveButton("Delete") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        repository.deleteIrk(irk.id)
                        Toast.makeText(requireContext(), "IRK deleted", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showExtractIrksDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Extract IRKs")
            .setMessage("""
                This requires ROOT access to read IRKs from:
                /data/misc/bluedroid/bt_config.conf
                
                Alternative methods:
                • Use btrpa-scan on Linux
                • Use btmgmt from BlueZ
                • Extract from iOS keychain backup
                
                IRK format: 32 hex characters (16 bytes)
                Example: 0102030405060708090a0b0c0d0e0f10
            """.trimIndent())
            .setPositiveButton("OK", null)
            .show()
    }
}

// IRK RecyclerView Adapter
class IrkAdapter(
    private val onDelete: (Irk) -> Unit,
    private val onEdit: (Irk) -> Unit
) : RecyclerView.Adapter<IrkAdapter.ViewHolder>() {
    
    private var items: List<Irk> = emptyList()
    
    fun submitList(list: List<Irk>) {
        items = list
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_irk, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }
    
    override fun getItemCount() = items.size
    
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvName: TextView = view.findViewById(R.id.tvIrkName)
        private val tvIrk: TextView = view.findViewById(R.id.tvIrkValue)
        private val tvInfo: TextView = view.findViewById(R.id.tvIrkInfo)
        private val btnEdit: ImageButton = view.findViewById(R.id.btnEdit)
        private val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
        
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        
        fun bind(irk: Irk) {
            tvName.text = irk.name ?: "Unnamed IRK"
            tvIrk.text = irk.irk.take(8) + "..." + irk.irk.takeLast(8)
            
            val info = buildString {
                irk.deviceType?.let { append("$it • ") }
                append("Added ${dateFormat.format(Date(irk.addedAt))}")
                irk.timesResolved.takeIf { it > 0 }?.let { append(" • $it resolutions") }
            }
            tvInfo.text = info
            
            itemView.setOnClickListener { onEdit(irk) }
            btnEdit.setOnClickListener { onEdit(irk) }
            btnDelete.setOnClickListener { onDelete(irk) }
        }
    }
}
