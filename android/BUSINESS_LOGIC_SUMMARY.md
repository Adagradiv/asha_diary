# Phase 4/5: Business Logic Layer - COMPLETE

## ✅ Duplicate Detection & Workflow Management

### Files Created (7 Files)

#### Domain Layer (3 Files)

##### 1. DuplicateDetectionEngine.kt
**Advanced Duplicate Scoring Algorithm**
- ✅ **Weighted scoring** (0-100 scale):
  - Name similarity: 40% (Levenshtein distance)
  - Village match: 20%
  - Gender match: 15%
  - Age similarity: 15% (with ±2 year tolerance)
  - Biometric match: 10% (optional)
- ✅ **Confidence levels**:
  - 90+: HIGH (Likely duplicate)
  - 70-89: MEDIUM (Possible duplicate)
  - 50-69: LOW (Review recommended)
  - <50: VERY_LOW (Safe to register)
- ✅ **Hindi recommendations** for each confidence level
- ✅ **Levenshtein distance** algorithm for name matching

**Scoring Example:**
```kotlin
Beneficiary A: "राम कुमार", Village 1, Male, Age 25
Beneficiary B: "राम कुमार", Village 1, Male, Age 26

Score Breakdown:
- Name: 100% × 40 = 40 points
- Village: Match × 20 = 20 points
- Gender: Match × 15 = 15 points
- Age: 85% × 15 = 12.75 points
Total: 87.75 → MEDIUM confidence → "डुप्लिकेट हो सकता है"
```

##### 2. DuplicateMergeManager.kt
**Duplicate Resolution for Block Nodal**
- ✅ **Merge duplicate into master:**
  - Transfers ALL visit records (ANC, BP, Blood Sugar, Vaccination)
  - Keeps master's biometric data (deletes duplicate's)
  - Marks duplicate as inactive with `duplicate_of_beneficiary_id`
  - Creates comprehensive audit trail
- ✅ **Visit transfer report:**
  - Counts ANC, BP, Blood Sugar, Vaccination visits
  - Re-syncs transferred records (marks `is_synced = false`)
- ✅ **Inactivate without merge** (for non-duplicates)
- ✅ **Role validation** (Block Nodal or above only)

**Merge Flow:**
```
1. Validate user role (≥ Block Nodal)
2. Get both beneficiaries
3. Transfer all visits from duplicate → master
4. Delete duplicate's biometric data
5. Mark duplicate inactive with link to master
6. Create audit log with visit counts
```

##### 3. EditRequestWorkflowManager.kt
**Edit Request Approval Workflow**
- ✅ **Submit request** (Field Staff):
  - Can request edits to: name, age, gender, mobile, father/husband name
  - Cannot edit directly (must go through approval)
  - GPS stamped request
- ✅ **Approve request** (Supervisor/Block Nodal):
  - Validates reviewer role (≥ Supervisor)
  - Applies edit to beneficiary
  - Increments `sync_version`
  - Creates audit log
- ✅ **Reject request** (Supervisor/Block Nodal):
  - Marks request as rejected with notes
  - Creates audit log
- ✅ **Flow-based queries:**
  - Pending requests for reviewers
  - User's submitted requests for tracking
  - Requests for specific beneficiary

**Workflow:**
```
Field Staff → Submit Request → PENDING
                                  ↓
                    Supervisor/Block Nodal Reviews
                                  ↓
                        Approve ← → Reject
                          ↓            ↓
                  Update Beneficiary  Close Request
```

#### Use Case Layer (1 File)

##### 4. RegisterBeneficiaryUseCase.kt
**Complete Registration Workflow**
- ✅ **Step-by-step process:**
  1. Get village for ID generation
  2. Generate unique beneficiary ID (SS-DDD-BBB-VVVV-W-NNNN)
  3. Create beneficiary entity
  4. Check for duplicates with scoring
  5. If duplicates found: Return for user confirmation
  6. If confirmed/no duplicates: Insert beneficiary
  7. Create duplicate flags if matches found
  8. Store fingerprint (if captured)
  9. Add to sync queue
  10. Create audit log
- ✅ **Duplicate override confirmation:**
  - User can proceed despite duplicates
  - Flags are still created for supervisor review
- ✅ **Result types:**
  - Success: Beneficiary registered
  - DuplicatesFound: User must confirm
  - Error: Registration failed

#### ViewModel Layer (3 Files)

##### 5. BeneficiaryRegistrationViewModel.kt
**Registration UI Integration**
- ✅ LiveData state management:
  - Idle, Registering, Success, DuplicatesDetected, Error
- ✅ Duplicate override flow:
  - Shows matches to user
  - User confirms or cancels
  - Retries registration with override flag
- ✅ Integrates:
  - RegisterBeneficiaryUseCase
  - BiometricRepository
  - Database DAOs

##### 6. DuplicateReviewViewModel.kt
**Block Nodal Duplicate Review**
- ✅ Flow-based pending duplicates list
- ✅ Merge operation with visit transfer
- ✅ Mark as non-duplicate (keep both)
- ✅ Inactivate duplicate
- ✅ Real-time pending count

##### 7. EditRequestViewModel.kt
**Edit Workflow UI Integration**
- ✅ Submit edit request (Field Staff)
- ✅ Approve/reject requests (Supervisor/Block Nodal)
- ✅ Track user's submitted requests
- ✅ View requests for specific beneficiary
- ✅ Real-time pending count

---

## Key Features Implemented

### ✅ Intelligent Duplicate Detection
```
Scoring Algorithm:
├── Name: Levenshtein distance (40%)
├── Village: Exact match (20%)
├── Gender: Exact match (15%)
├── Age: ±2 years tolerance (15%)
└── Biometric: ISO template match (10%)

Thresholds:
├── 90+: Exact match → Block registration
├── 70-89: Possible → Supervisor review
├── 50-69: Low → Warning + Continue
└── <50: Safe → Register normally
```

### ✅ Role-Based Authority Enforcement
```
Field Staff (Role 1):
├── Cannot edit beneficiary directly
├── Must submit edit requests
└── Can register beneficiaries

Supervisor (Role 2):
├── Can approve/reject edit requests
├── Can edit beneficiaries directly
└── Cannot merge duplicates

Block Nodal (Role 3+):
├── Final authority on all edits
├── Can merge duplicates
└── Can inactivate beneficiaries
```

### ✅ Complete Audit Trail
Every action logged with:
- User ID & Role ID
- Action type (CREATE, UPDATE, DELETE, MERGE, APPROVE, REJECT)
- Entity type & ID
- Description in Hindi
- GPS coordinates
- Timestamps

### ✅ Visit Transfer on Merge
When merging duplicates:
- All ANC visits transferred
- All BP visits transferred
- All Blood Sugar visits transferred
- All Vaccination visits transferred
- Each transferred record re-queued for sync
- Audit log includes visit counts

---

## Usage Examples

### Beneficiary Registration with Duplicate Detection
```kotlin
// In Activity/Fragment
val viewModel: BeneficiaryRegistrationViewModel by viewModels()

// Create request
val request = BeneficiaryRegistrationRequest(
    nameHindi = "राम कुमार",
    villageId = "village_uuid",
    // ... other fields
    biometricTemplate = capturedTemplate,  // Optional
    confirmDuplicateOverride = false
)

// Register
viewModel.registerBeneficiary(request)

// Observe result
viewModel.registrationState.observe(this) { state ->
    when (state) {
        is RegistrationState.DuplicatesDetected -> {
            // Show duplicates to user
            val matches = viewModel.duplicateMatches.value
            showDuplicateWarning(matches)
            
            // User can confirm or cancel
            btnConfirm.setOnClickListener {
                viewModel.confirmRegistrationWithDuplicates()
            }
            btnCancel.setOnClickListener {
                viewModel.cancelRegistration()
            }
        }
        is RegistrationState.Success -> {
            showSuccess(state.beneficiaryId)
        }
        is RegistrationState.Error -> {
            showError(state.message)
        }
        else -> {}
    }
}
```

### Edit Request Workflow (Field Staff)
```kotlin
val viewModel: EditRequestViewModel by viewModels()

// Submit edit request
viewModel.submitEditRequest(
    beneficiaryId = "ben_uuid",
    fieldName = "name_hindi",
    currentValue = "राम कुमार",
    requestedValue = "राम कुमार शर्मा",
    reason = "नाम गलत दर्ज हो गया था",
    userId = currentUser.id,
    userRoleId = 1,  // Field Staff
    gpsLat = location.latitude,
    gpsLng = location.longitude
)

// Observe result
viewModel.requestState.observe(this) { state ->
    when (state) {
        is EditRequestState.Submitted -> {
            showSuccess("अनुरोध सबमिट हो गया")
        }
        is EditRequestState.Error -> {
            showError(state.message)
        }
        else -> {}
    }
}
```

### Edit Review (Supervisor/Block Nodal)
```kotlin
val viewModel: EditRequestViewModel by viewModels()

// Show pending requests
viewModel.pendingRequests.collect { requests ->
    adapter.submitList(requests)
}

// Approve request
btnApprove.setOnClickListener {
    viewModel.approveRequest(
        requestId = request.id,
        userId = currentUser.id,
        userRoleId = 2,  // Supervisor
        reviewNotes = "स्वीकृत"
    )
}

// Reject request
btnReject.setOnClickListener {
    viewModel.rejectRequest(
        requestId = request.id,
        userId = currentUser.id,
        userRoleId = 2,
        reviewNotes = "नाम सही नहीं है"
    )
}
```

### Duplicate Merge (Block Nodal)
```kotlin
val viewModel: DuplicateReviewViewModel by viewModels()

// Show pending duplicates
viewModel.pendingDuplicates.collect { flags ->
    adapter.submitList(flags)
}

// Merge duplicate
btnMerge.setOnClickListener {
    viewModel.mergeDuplicate(
        duplicateBeneficiaryId = "dup_uuid",
        masterBeneficiaryId = "master_uuid",
        userId = currentUser.id,
        userRoleId = 3,  // Block Nodal
        notes = "दोनों एक ही व्यक्ति हैं"
    )
}

// Observe result
viewModel.mergeState.observe(this) { state ->
    when (state) {
        is MergeState.Success -> {
            showSuccess("${state.transferredVisits} विज़िट ट्रांसफर की गई")
        }
        is MergeState.Error -> {
            showError(state.message)
        }
        else -> {}
    }
}
```

---

## Integration Points

### With Phase 1 (Data Layer)
- Uses all DAOs (Beneficiary, DuplicateFlag, EditRequest, etc.)
- Leverages Room queries for duplicate detection
- Creates audit logs for all operations

### With Phase 2 (Biometric)
- Integrates BiometricRepository for fingerprint search
- Uses biometric matching in duplicate scoring
- Deletes duplicate biometrics on merge

### With Phase 3 (Sync)
- Adds items to sync queue after registration
- Marks transferred visits for re-sync
- Updates sync_version on edits

---

## Production Checklist

### ✅ Implemented
- [x] Weighted duplicate detection algorithm
- [x] Levenshtein distance name matching
- [x] Confidence levels with thresholds
- [x] Complete merge workflow with visit transfer
- [x] Edit request approval chain
- [x] Role-based access control
- [x] Comprehensive audit logging
- [x] ViewModels for all workflows
- [x] Flow-based reactive queries
- [x] Hindi messages and recommendations

### Ready for Phase 6: Android UI
All business logic is complete and ready for UI integration:
- Registration screens with duplicate warnings
- Edit request submission forms
- Approval/rejection interfaces
- Duplicate review dashboards

---

## Files Structure

```
domain/
├── duplicate/
│   ├── DuplicateDetectionEngine.kt  # Scoring algorithm
│   └── DuplicateMergeManager.kt     # Merge operations
├── editrequest/
│   └── EditRequestWorkflowManager.kt # Approval workflow
└── usecases/
    └── RegisterBeneficiaryUseCase.kt # Complete registration

presentation/viewmodel/
├── BeneficiaryRegistrationViewModel.kt # Registration UI
├── DuplicateReviewViewModel.kt         # Merge UI
└── EditRequestViewModel.kt             # Edit workflow UI
```

**Total: 7 production-ready files**

---

**Phases 4 & 5 Complete** ✅  
Business logic layer ready for UI development (Phase 6) or Backend API (Phase 7).
