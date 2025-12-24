package com.upgovt.ashadiary.presentation.ui.duplicates

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.upgovt.ashadiary.R
import com.upgovt.ashadiary.presentation.viewmodel.DuplicateReviewViewModel

/**
 * DuplicateReviewFragment - Block Nodal reviews and merges duplicates
 */
class DuplicateReviewFragment : Fragment() {
    
    private val viewModel: DuplicateReviewViewModel by viewModels()
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoResults: TextView
    private lateinit var progressBar: ProgressBar
    
    // Detail cards (shown when item selected)
    private lateinit var cardBeneficiary1: MaterialCardView
    private lateinit var cardBeneficiary2: MaterialCardView
    private lateinit var tvMatchScore: TextView
    private lateinit var btnMerge: MaterialButton
    private lateinit var btnNotDuplicate: MaterialButton
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_duplicate_review, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        
        loadPendingDuplicates()
    }
    
    private fun initViews(view: View) {
        recyclerView = view.findViewById(R.id.recycler_view)
        tvNoResults = view.findViewById(R.id.tv_no_results)
        progressBar = view.findViewById(R.id.progress_bar)
        
        cardBeneficiary1 = view.findViewById(R.id.card_beneficiary_1)
        cardBeneficiary2 = view.findViewById(R.id.card_beneficiary_2)
        tvMatchScore = view.findViewById(R.id.tv_match_score)
        btnMerge = view.findViewById(R.id.btn_merge)
        btnNotDuplicate = view.findViewById(R.id.btn_not_duplicate)
        
        // Hide detail cards initially
        cardBeneficiary1.visibility = View.GONE
        cardBeneficiary2.visibility = View.GONE
    }
    
    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        // TODO: Set adapter with duplicate flag list
    }
    
    private fun setupObservers() {
        viewModel.duplicateFlags.observe(viewLifecycleOwner) { flags ->
            hideLoading()
            
            if (flags.isEmpty()) {
                showNoResults()
            } else {
                showResults(flags.size)
            }
        }
        
        viewModel.mergeResult.observe(viewLifecycleOwner) { result ->
            if (result.isSuccess) {
                Toast.makeText(requireContext(), "मर्ज सफल!", Toast.LENGTH_SHORT).show()
                loadPendingDuplicates()
            } else {
                Toast.makeText(requireContext(), result.error, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun setupClickListeners() {
        btnMerge.setOnClickListener {
            // TODO: Show dialog to select master record
            // viewModel.mergeDuplicates(duplicateId, masterId)
            Toast.makeText(requireContext(), "मर्ज डायलॉग खोलें", Toast.LENGTH_SHORT).show()
        }
        
        btnNotDuplicate.setOnClickListener {
            // TODO: Mark as not duplicate
            // viewModel.markNotDuplicate(duplicateId)
            Toast.makeText(requireContext(), "डुप्लिकेट नहीं के रूप में चिह्नित", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadPendingDuplicates() {
        showLoading()
        // TODO: viewModel.loadPendingDuplicates()
        
        // Mock for now
        hideLoading()
        showResults(5)
    }
    
    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        tvNoResults.visibility = View.GONE
    }
    
    private fun hideLoading() {
        progressBar.visibility = View.GONE
    }
    
    private fun showResults(count: Int) {
        recyclerView.visibility = View.VISIBLE
        tvNoResults.visibility = View.GONE
    }
    
    private fun showNoResults() {
        recyclerView.visibility = View.GONE
        tvNoResults.visibility = View.VISIBLE
    }
}
