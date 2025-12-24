package com.upgovt.ashadiary.presentation.ui.editrequests

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.upgovt.ashadiary.R
import com.upgovt.ashadiary.presentation.viewmodel.EditRequestViewModel

/**
 * EditRequestListFragment - Supervisors review pending edit requests
 */
class EditRequestListFragment : Fragment() {
    
    private val viewModel: EditRequestViewModel by viewModels()
    
    private lateinit var chipGroup: ChipGroup
    private lateinit var chipPending: Chip
    private lateinit var chipApproved: Chip
    private lateinit var chipRejected: Chip
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoRequests: TextView
    private lateinit var progressBar: ProgressBar
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_edit_request_list, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupRecyclerView()
        setupChipFilters()
        setupObservers()
        
        // Load pending requests by default
        loadEditRequests("PENDING")
    }
    
    private fun initViews(view: View) {
        chipGroup = view.findViewById(R.id.chip_group_status)
        chipPending = view.findViewById(R.id.chip_pending)
        chipApproved = view.findViewById(R.id.chip_approved)
        chipRejected = view.findViewById(R.id.chip_rejected)
        recyclerView = view.findViewById(R.id.recycler_view)
        tvNoRequests = view.findViewById(R.id.tv_no_requests)
        progressBar = view.findViewById(R.id.progress_bar)
    }
    
    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        // TODO: Set adapter with edit request list
    }
    
    private fun setupChipFilters() {
        chipGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.chip_pending -> loadEditRequests("PENDING")
                R.id.chip_approved -> loadEditRequests("APPROVED")
                R.id.chip_rejected -> loadEditRequests("REJECTED")
            }
        }
    }
    
    private fun setupObservers() {
        viewModel.editRequests.observe(viewLifecycleOwner) { requests ->
            hideLoading()
            
            if (requests.isEmpty()) {
                showNoRequests()
            } else {
                showRequests(requests.size)
            }
        }
    }
    
    private fun loadEditRequests(status: String) {
        showLoading()
        // TODO: viewModel.loadEditRequestsByStatus(status)
        
        // Mock for now
        hideLoading()
        if (status == "PENDING") {
            showRequests(3)
        } else {
            showNoRequests()
        }
    }
    
    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        tvNoRequests.visibility = View.GONE
    }
    
    private fun hideLoading() {
        progressBar.visibility = View.GONE
    }
    
    private fun showRequests(count: Int) {
        recyclerView.visibility = View.VISIBLE
        tvNoRequests.visibility = View.GONE
    }
    
    private fun showNoRequests() {
        recyclerView.visibility = View.GONE
        tvNoRequests.visibility = View.VISIBLE
    }
}
