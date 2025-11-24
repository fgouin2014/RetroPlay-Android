package com.retroplay

import android.app.ProgressDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * Activity pour gérer le téléchargement et la suppression des cores Libretro
 * Utilise CoreMetadataManager pour détection automatique depuis cores.json
 */
class CoreManagerActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CoreAdapter
    private var cores: List<CoreDownloader.CoreInfo> = emptyList()
    
    companion object {
        private const val TAG = "CoreManagerActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_core_manager)
        
        // Setup toolbar
        findViewById<TextView>(R.id.backButton)?.setOnClickListener {
            finish()
        }
        
        // Device info
        val deviceAbi = CoreDownloader.getDeviceABI()
        val deviceInfoText = findViewById<TextView>(R.id.deviceInfoText)
        deviceInfoText?.text = "Device: $deviceAbi (Android ${android.os.Build.VERSION.SDK_INT})"
        
        // Setup RecyclerView (vide pour l'instant, sera rempli après chargement)
        recyclerView = findViewById(R.id.coresRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = CoreAdapter(emptyList())
        recyclerView.adapter = adapter
        
        // Charger les cores depuis CoreMetadataManager (détection automatique)
        loadCoresFromMetadata()
        
        // Download All button
        findViewById<MaterialButton>(R.id.downloadAllButton)?.setOnClickListener {
            downloadAllCores()
        }
        
        // Delete All button
        findViewById<MaterialButton>(R.id.deleteAllButton)?.setOnClickListener {
            deleteAllCores()
        }
    }
    
    /**
     * Charge les cores depuis CoreMetadataManager (détection automatique)
     * Fallback sur liste hardcodée en cas d'erreur
     */
    private fun loadCoresFromMetadata() {
        val progressDialog = ProgressDialog(this)
        progressDialog.setTitle("Loading Cores")
        progressDialog.setMessage("Fetching cores from buildbot...")
        progressDialog.setCancelable(false)
        progressDialog.show()
        
        lifecycleScope.launch {
            try {
                // Utiliser CoreMetadataManager pour détection automatique
                cores = CoreDownloader.getAvailableCoresFromMetadata(
                    this@CoreManagerActivity,
                    CoreMetadataManager.BuildType.NIGHTLY
                )
                
                runOnUiThread {
                    progressDialog.dismiss()
                    adapter = CoreAdapter(cores)
                    recyclerView.adapter = adapter
                    Log.i(TAG, "Loaded ${cores.size} cores from metadata (automatic detection)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading cores from metadata: ${e.message}", e)
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(
                        this@CoreManagerActivity,
                        "Error loading cores. Using fallback list.",
                        Toast.LENGTH_LONG
                    ).show()
                    // Fallback sur liste hardcodée en cas d'erreur réseau
                    cores = CoreDownloader.getAvailableCores()
                    adapter = CoreAdapter(cores)
                    recyclerView.adapter = adapter
                    Log.i(TAG, "Using fallback hardcoded list: ${cores.size} cores")
                }
            }
        }
    }
    
    private fun downloadAllCores() {
        val missingCores = cores.filter { !CoreDownloader.isCoreInstalled(this, it) }
        
        if (missingCores.isEmpty()) {
            Toast.makeText(this, "All cores are already installed", Toast.LENGTH_SHORT).show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("Download All Cores")
            .setMessage("Download ${missingCores.size} missing cores?\n\nThis may take several minutes.")
            .setPositiveButton("DOWNLOAD") { _, _ ->
                downloadCoresSequentially(missingCores)
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }
    
    private fun downloadCoresSequentially(coresList: List<CoreDownloader.CoreInfo>) {
        if (coresList.isEmpty()) {
            adapter.notifyDataSetChanged()
            return
        }
        
        val progressDialog = ProgressDialog(this)
        progressDialog.setTitle("Downloading Cores")
        progressDialog.setMessage("Downloading ${coresList[0].displayName}...")
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        progressDialog.max = 100
        progressDialog.setCancelable(false)
        progressDialog.show()
        
        var currentIndex = 0
        
        fun downloadNext() {
            if (currentIndex >= coresList.size) {
                progressDialog.dismiss()
                Toast.makeText(this, "All cores downloaded!", Toast.LENGTH_LONG).show()
                adapter.notifyDataSetChanged()
                return
            }
            
            val core = coresList[currentIndex]
            progressDialog.setMessage("(${currentIndex + 1}/${coresList.size}) ${core.displayName}")
            
            Thread {
                val success = CoreDownloader.downloadCore(this, core) { progress, status ->
                    runOnUiThread {
                        progressDialog.progress = progress
                    }
                }
                
                runOnUiThread {
                    if (success) {
                        currentIndex++
                        downloadNext()
                    } else {
                        progressDialog.dismiss()
                        AlertDialog.Builder(this)
                            .setTitle("Download Failed")
                            .setMessage("Failed to download ${core.displayName}.\n\nContinue with remaining cores?")
                            .setPositiveButton("CONTINUE") { _, _ ->
                                currentIndex++
                                progressDialog.show()
                                downloadNext()
                            }
                            .setNegativeButton("STOP") { _, _ ->
                                adapter.notifyDataSetChanged()
                            }
                            .show()
                    }
                }
            }.start()
        }
        
        downloadNext()
    }
    
    private fun deleteAllCores() {
        val installedCores = cores.filter { CoreDownloader.isCoreInstalled(this, it) }
        
        if (installedCores.isEmpty()) {
            Toast.makeText(this, "No cores installed", Toast.LENGTH_SHORT).show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("Delete All Cores")
            .setMessage("Delete ${installedCores.size} installed cores?")
            .setPositiveButton("DELETE") { _, _ ->
                var deletedCount = 0
                installedCores.forEach { core ->
                    if (CoreDownloader.deleteCore(this, core)) {
                        deletedCount++
                    }
                }
                Toast.makeText(this, "Deleted $deletedCount cores", Toast.LENGTH_SHORT).show()
                adapter.notifyDataSetChanged()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }
    
    /**
     * Adapter pour la liste des cores
     */
    inner class CoreAdapter(private val cores: List<CoreDownloader.CoreInfo>) :
        RecyclerView.Adapter<CoreAdapter.ViewHolder>() {
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_core_manager, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val core = cores[position]
            val isInstalled = CoreDownloader.isCoreInstalled(this@CoreManagerActivity, core)
            
            holder.coreName.text = core.displayName
            holder.coreId.text = core.id
            holder.coreFileName.text = core.fileName
            
            if (isInstalled) {
                holder.statusBadge.visibility = View.VISIBLE
                holder.statusBadge.text = "INSTALLED"
                holder.statusBadge.setBackgroundColor(0xFF4CAF50.toInt()) // Vert
                holder.downloadButton.text = "DELETE"
                holder.downloadButton.setBackgroundColor(0xFFF44336.toInt()) // Rouge
            } else {
                holder.statusBadge.visibility = View.GONE
                holder.downloadButton.text = "DOWNLOAD"
                holder.downloadButton.setBackgroundColor(0xFF2196F3.toInt()) // Bleu
            }
            
            holder.downloadButton.setOnClickListener {
                if (isInstalled) {
                    deleteCore(core, position)
                } else {
                    downloadCore(core, position)
                }
            }
        }
        
        override fun getItemCount() = cores.size
        
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val coreName: TextView = view.findViewById(R.id.coreName)
            val coreId: TextView = view.findViewById(R.id.coreId)
            val coreFileName: TextView = view.findViewById(R.id.coreFileName)
            val statusBadge: TextView = view.findViewById(R.id.statusBadge)
            val downloadButton: MaterialButton = view.findViewById(R.id.downloadButton)
        }
    }
    
    private fun downloadCore(core: CoreDownloader.CoreInfo, position: Int) {
        val progressDialog = ProgressDialog(this)
        progressDialog.setTitle("Downloading Core")
        progressDialog.setMessage(core.displayName)
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        progressDialog.max = 100
        progressDialog.setCancelable(false)
        progressDialog.show()
        
        Thread {
            val success = CoreDownloader.downloadCore(this, core) { progress, status ->
                runOnUiThread {
                    progressDialog.progress = progress
                    progressDialog.setMessage("${core.displayName}\n$status")
                }
            }
            
            runOnUiThread {
                progressDialog.dismiss()
                if (success) {
                    Toast.makeText(this, "Core installed: ${core.displayName}", Toast.LENGTH_SHORT).show()
                    adapter.notifyItemChanged(position)
                } else {
                    Toast.makeText(this, "Download failed: ${core.displayName}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
    
    private fun deleteCore(core: CoreDownloader.CoreInfo, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Delete Core")
            .setMessage("Delete ${core.displayName}?")
            .setPositiveButton("DELETE") { _, _ ->
                if (CoreDownloader.deleteCore(this, core)) {
                    Toast.makeText(this, "Core deleted", Toast.LENGTH_SHORT).show()
                    adapter.notifyItemChanged(position)
                } else {
                    Toast.makeText(this, "Failed to delete core", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }
}

