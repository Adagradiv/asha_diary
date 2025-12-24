package com.upgovt.ashadiary.presentation.ui.registration

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.upgovt.ashadiary.R
import com.upgovt.ashadiary.presentation.viewmodel.BeneficiaryRegistrationViewModel
import com.upgovt.ashadiary.presentation.viewmodel.BiometricViewModel
import com.upgovt.ashadiary.presentation.viewmodel.RegistrationState
import com.upgovt.ashadiary.domain.usecases.BeneficiaryRegistrationRequest
import com.upgovt.ashadiary.utils.voice.VoiceInputCallback
import com.upgovt.ashadiary.utils.voice.VoiceInputHelper
import kotlinx.coroutines.launch

/**
 * BeneficiaryRegistrationFragment - Registration screen with voice input
 * Integrates: Voice, Biometric, Duplicate Detection, Offline-first
 */
class BeneficiaryRegistrationFragment : Fragment() {
    
    private val registrationViewModel: BeneficiaryRegistrationViewModel by viewModels()
    private val biometricViewModel: BiometricViewModel by viewModels()
    
    private lateinit var voiceHelper: VoiceInputHelper
    
    // Views
    private lateinit var etName: EditText
    private lateinit var etFatherHusbandName: EditText
    private lateinit var etAddress: EditText
    private lateinit var etMobile: EditText
    private lateinit var chipGroupGender: ChipGroup
    private lateinit var pickerAge: NumberPicker
    private lateinit var spinnerVillage: Spinner
    private lateinit var tvFingerprintStatus: TextView
    private lateinit var btnCaptureFingerprint: Button
    private lateinit var btnRegister: Button
    
    private var capturedBiometricTemplate: ByteArray? = null
    private var biometricQuality: Int = 0
    
    // Permissions
    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(requireContext(), "माइक्रोफोन अनुमति आवश्यक है", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_beneficiary_registration, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupVoiceInput()
        setupAgePicker()
        setupObservers()
        checkMicPermission()
    }
    
    private fun initViews(view: View) {
        etName = view.findViewById(R.id.et_name)
        etFatherHusbandName = view.findViewById(R.id.et_father_husband_name)
        etAddress = view.findViewById(R.id.et_address)
        etMobile = view.findViewById(R.id.et_mobile)
        chipGroupGender = view.findViewById(R.id.chip_group_gender)
        pickerAge = view.findViewById(R.id.picker_age)
        spinnerVillage = view.findViewById(R.id.spinner_village)
        tvFingerprintStatus = view.findViewById(R.id.tv_fingerprint_status)
        btnCaptureFingerprint = view.findViewById(R.id.btn_capture_fingerprint)
        btnRegister = view.findViewById(R.id.btn_register)
        
        // Voice buttons
        view.findViewById<ImageButton>(R.id.btn_voice_name).setOnClickListener {
            startVoiceInput { text -> etName.setText(text) }
        }
        
        view.findViewById<ImageButton>(R.id.btn_voice_father).setOnClickListener {
            startVoiceInput { text -> etFatherHusbandName.setText(text) }
        }
        
        view.findViewById<ImageButton>(R.id.btn_voice_address).setOnClickListener {
            startVoiceInput { text -> etAddress.setText(text) }
        }
        
        btnCaptureFingerprint.setOnClickListener {
            captureFingerprint()
        }
        
        btnRegister.setOnClickListener {
            submitRegistration()
        }
    }
    
    private fun setupVoiceInput() {
        voiceHelper = VoiceInputHelper(requireContext())
    }
    
    private fun setupAgePicker() {
        pickerAge.minValue = 0
        pickerAge.maxValue = 120
        pickerAge.value = 25
    }
    
    private fun setupObservers() {
        // Registration state
        registrationViewModel.registrationState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is RegistrationState.Registering -> {
                    showLoading()
                }
                is RegistrationState.Success -> {
                    hideLoading()
                    showSuccess(state.message)
                    clearForm()
                }
                is RegistrationState.DuplicatesDetected -> {
                    hideLoading()
                    showDuplicateWarning(state.count, state.highestScore)
                }
                is RegistrationState.Error -> {
                    hideLoading()
                    showError(state.message)
                }
                else -> hideLoading()
            }
        }
        
        // Duplicate matches
        registrationViewModel.duplicateMatches.observe(viewLifecycleOwner) { matches ->
            // Could show list of matches in dialog
        }
    }
    
    private fun checkMicPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    
    private fun startVoiceInput(onResult: (String) -> Unit) {
        voiceHelper.startListening(object : VoiceInputCallback {
            override fun onResult(text: String) {
                onResult(text)
            }
            
            override fun onPartialResult(text: String) {
                // Could show interim results
            }
            
            override fun onError(error: String) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        })
    }
    
    private fun captureFingerprint() {
        // Launch Mantra RD Service intent
        // In production, use BiometricHelper from Phase 2
        biometricViewModel.captureFingerprint(
            activity = requireActivity(),
            requestCode = 100
        )
    }
    
    private fun submitRegistration() {
        // Validate inputs
        if (etName.text.isBlank()) {
            showError("नाम आवश्यक है")
            return
        }
        
        if (etFatherHusbandName.text.isBlank()) {
            showError("पिता/पति का नाम आवश्यक है")
            return
        }
        
        val gender = when (chipGroupGender.checkedChipId) {
            R.id.chip_male -> "M"
            R.id.chip_female -> "F"
            else -> "O"
        }
        
        val selectedVillage = spinnerVillage.selectedItem as? String
        if (selectedVillage == null) {
            showError("गाँव चुनें")
            return
        }
        
        // Create registration request
        val request = BeneficiaryRegistrationRequest(
            nameHindi = etName.text.toString(),
            fatherHusbandNameHindi = etFatherHusbandName.text.toString(),
            gender = gender,
            dateOfBirth = System.currentTimeMillis(), // Calculate from age
            ageYears = pickerAge.value,
            beneficiaryType = "PREGNANT_WOMAN", // TODO: Add type selector
            villageId = "village_uuid", // TODO: Get from spinner
            addressHindi = etAddress.text.toString(),
            mobileNumber = etMobile.text.toString().takeIf { it.isNotBlank() },
            registeredByUserId = "current_user_id", // TODO: Get from session
            registeredByRoleId = 1, // TODO: Get from session
            workerCode = "A", // TODO: Get from user
            gpsLat = 0.0, // TODO: Get actual GPS
            gpsLng = 0.0,
            biometricTemplate = capturedBiometricTemplate,
            biometricQuality = biometricQuality,
            fingerPosition = "RIGHT_THUMB",
            deviceSerial = null,
            confirmDuplicateOverride = false
        )
        
        registrationViewModel.registerBeneficiary(request)
    }
    
    private fun showDuplicateWarning(count: Int, highestScore: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dup_warning_title)
            .setMessage(getString(R.string.dup_warning_message, count))
            .setPositiveButton(R.string.dup_confirm_register) { _, _ ->
                registrationViewModel.confirmRegistrationWithDuplicates()
            }
            .setNegativeButton(R.string.dup_cancel) { _, _ ->
                registrationViewModel.cancelRegistration()
            }
            .show()
    }
    
    private fun showSuccess(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }
    
    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
    
    private fun showLoading() {
        btnRegister.isEnabled = false
        btnRegister.text = "पंजीकरण हो रहा है..."
    }
    
    private fun hideLoading() {
        btnRegister.isEnabled = true
        btnRegister.text = getString(R.string.reg_register)
    }
    
    private fun clearForm() {
        etName.text.clear()
        etFatherHusbandName.text.clear()
        etAddress.text.clear()
        etMobile.text.clear()
        pickerAge.value = 25
        chipGroupGender.check(R.id.chip_female)
        capturedBiometricTemplate = null
        tvFingerprintStatus.visibility = View.GONE
    }
    
    override fun onDestroy() {
        super.onDestroy()
        voiceHelper.destroy()
    }
}
