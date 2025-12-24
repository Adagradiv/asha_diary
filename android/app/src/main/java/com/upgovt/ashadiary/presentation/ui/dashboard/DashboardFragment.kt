package com.upgovt.ashadiary.presentation.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.upgovt.ashadiary.R
import com.upgovt.ashadiary.utils.network.NetworkMonitor
import com.upgovt.ashadiary.presentation.viewmodel.SyncViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * DashboardFragment - Home screen with statistics cards
 */
class DashboardFragment : Fragment() {
    
    private val syncViewModel: SyncViewModel by viewModels()
    private lateinit var networkMonitor: NetworkMonitor
    
    // Stats TextViews
    private lateinit var tvTotalBeneficiaries: TextView
    private lateinit var tvPendingSync: TextView
    private lateinit var tvPendingDuplicates: TextView
    private lateinit var tvPendingEdits: TextView
    
    // Offline indicator
    private lateinit var tvStatus: TextView
    private lateinit var tvLastSync: TextView
    private lateinit var indicatorDot: View
    
    // Buttons
    private lateinit var btnRegisterBeneficiary: Button
    private lateinit var btnManualSync: Button
    
    // Cards (for click navigation)
    private lateinit var cardPendingDuplicates: MaterialCardView
    private lateinit var cardPendingEdits: MaterialCardView
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupNetworkMonitor()
        setupObservers()
        setupClickListeners()
        
        // Load initial stats
        loadDashboardStats()
    }
    
    private fun initViews(view: View) {
        // Stats
        tvTotalBeneficiaries = view.findViewById(R.id.tv_total_beneficiaries)
        tvPendingSync = view.findViewById(R.id.tv_pending_sync)
        tvPendingDuplicates = view.findViewById(R.id.tv_pending_duplicates)
        tvPendingEdits = view.findViewById(R.id.tv_pending_edits)
        
        // Offline indicator
        val offlineIndicator = view.findViewById<View>(R.id.offline_indicator)
        indicatorDot = offlineIndicator.findViewById(R.id.indicator_dot)
        tvStatus = offlineIndicator.findViewById(R.id.tv_status)
        tvLastSync = offlineIndicator.findViewById(R.id.tv_last_sync)
        
        // Buttons
        btnRegisterBeneficiary = view.findViewById(R.id.btn_register_beneficiary)
        btnManualSync = view.findViewById(R.id.btn_manual_sync)
        
        // Cards
        cardPendingDuplicates = view.findViewById(R.id.card_pending_duplicates)
        cardPendingEdits = view.findViewById(R.id.card_pending_edits)
    }
    
    private fun setupNetworkMonitor() {
        networkMonitor = NetworkMonitor(requireContext())
        
        lifecycleScope.launch {
            networkMonitor.observeNetworkConnectivity().collect { status ->
                updateNetworkStatus(status.isConnected)
            }
        }
    }
    
    private fun setupObservers() {
        // Sync status
        syncViewModel.syncState.observe(viewLifecycleOwner) { state ->
            when {
                state.isSyncing -> {
                    btnManualSync.isEnabled = false
                    btnManualSync.text = "सिंक हो रहा है..."
                }
                else -> {
                    btnManualSync.isEnabled = true
                    btnManualSync.text = getString(R.string.action_sync)
                }
            }
        }
        
        // Last sync time
        syncViewModel.lastSyncTime.observe(viewLifecycleOwner) { timestamp ->
            timestamp?.let {
                val formatted = formatTimestamp(it)
                tvLastSync.text = getString(R.string.status_last_sync, formatted)
            }
        }
        
        // Sync queue count (pending sync)
        syncViewModel.pendingSyncCount.observe(viewLifecycleOwner) { count ->
            tvPendingSync.text = count.toString()
        }
    }
    
    private fun setupClickListeners() {
        btnRegisterBeneficiary.setOnClickListener {
            // Navigate to registration
            // TODO: Use Navigation component
        }
        
        btnManualSync.setOnClickListener {
            syncViewModel.triggerManualSync()
        }
        
        cardPendingDuplicates.setOnClickListener {
            // Navigate to duplicate review
        }
        
        cardPendingEdits.setOnClickListener {
            // Navigate to edit requests
        }
    }
    
    private fun loadDashboardStats() {
        // TODO: Load from local database initially
        // Then refresh from API when online
        
        // Mock data for now
        tvTotalBeneficiaries.text = "1,250"
        tvPendingDuplicates.text = "5"
        tvPendingEdits.text = "3"
    }
    
    private fun updateNetworkStatus(isConnected: Boolean) {
        if (isConnected) {
            indicatorDot.setBackgroundResource(R.color.online_green)
            tvStatus.text = getString(R.string.status_online)
        } else {
            indicatorDot.setBackgroundResource(R.color.offline_red)
            tvStatus.text = getString(R.string.status_offline)
        }
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale("hi", "IN"))
        return sdf.format(Date(timestamp))
    }
}
