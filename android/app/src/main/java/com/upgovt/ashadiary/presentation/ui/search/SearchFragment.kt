package com.upgovt.ashadiary.presentation.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.upgovt.ashadiary.R
import com.upgovt.ashadiary.utils.voice.VoiceInputCallback
import com.upgovt.ashadiary.utils.voice.VoiceInputHelper

/**
 * SearchFragment - Search beneficiaries by name, village, or mobile
 */
class SearchFragment : Fragment() {
    
    private lateinit var voiceHelper: VoiceInputHelper
    
    private lateinit var etSearchQuery: EditText
    private lateinit var btnVoiceSearch: ImageButton
    private lateinit var spinnerVillage: Spinner
    private lateinit var btnSearch: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoResults: TextView
    private lateinit var progressBar: ProgressBar
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupVoiceInput()
        setupRecyclerView()
        setupClickListeners()
    }
    
    private fun initViews(view: View) {
        etSearchQuery = view.findViewById(R.id.et_search_query)
        btnVoiceSearch = view.findViewById(R.id.btn_voice_search)
        spinnerVillage = view.findViewById(R.id.spinner_village)
        btnSearch = view.findViewById(R.id.btn_search)
        recyclerView = view.findViewById(R.id.recycler_view)
        tvNoResults = view.findViewById(R.id.tv_no_results)
        progressBar = view.findViewById(R.id.progress_bar)
    }
    
    private fun setupVoiceInput() {
        voiceHelper = VoiceInputHelper(requireContext())
    }
    
    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        // TODO: Set adapter with beneficiary list
    }
    
    private fun setupClickListeners() {
        btnVoiceSearch.setOnClickListener {
            startVoiceSearch()
        }
        
        btnSearch.setOnClickListener {
            performSearch()
        }
    }
    
    private fun startVoiceSearch() {
        voiceHelper.startListening(object : VoiceInputCallback {
            override fun onResult(text: String) {
                etSearchQuery.setText(text)
                performSearch()
            }
            
            override fun onError(error: String) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        })
    }
    
    private fun performSearch() {
        val query = etSearchQuery.text.toString()
        
        if (query.isBlank()) {
            Toast.makeText(requireContext(), "खोज शब्द दर्ज करें", Toast.LENGTH_SHORT).show()
            return
        }
        
        showLoading()
        
        // TODO: Call SearchViewModel.searchBeneficiaries(query, villageId)
        // For now, just show message
        hideLoading()
        tvNoResults.visibility = View.VISIBLE
        tvNoResults.text = "\"$query\" के लिए खोज परिणाम यहां दिखाई देंगे"
    }
    
    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        tvNoResults.visibility = View.GONE
    }
    
    private fun hideLoading() {
        progressBar.visibility = View.GONE
    }
    
    override fun onDestroy() {
        super.onDestroy()
        voiceHelper.destroy()
    }
}
