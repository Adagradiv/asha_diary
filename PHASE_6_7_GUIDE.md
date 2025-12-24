# Phase 6 & 7: Android UI + Backend API - IMPLEMENTATION GUIDE

## ✅ Phase 6: Android UI (Hindi + Voice + Sea-green Theme)

### Files Created (3 Core Files + Guides)

#### 1. colors.xml
**Sea-green Gradient Theme**
```xml
Primary: #00897B (Sea Green)
Gradient: #00897B → #26A69A
Accent: #FF6F00 (Orange)
Online: #4CAF50 (Green)
Offline: #F44336 (Red)
Syncing: #FF9800 (Orange)
```

- ✅ 3D card shadows (`#40000000`)
- ✅ Duplicate match colors (Red/Orange/Yellow for HIGH/MEDIUM/LOW)
- ✅ Transparent overlays for modals

#### 2. strings.xml
**Comprehensive Hindi-only UI Strings**
- ✅ Login screen (लॉगिन, यूज़रनेम, पासवर्ड)
- ✅ Dashboard (डैशबोर्ड, कुल लाभार्थी, सिंक बाकी)
- ✅ Beneficiary registration (पंजीकरण, नाम, आयु, गाँव)
- ✅ Duplicate warnings (संभावित डुप्लिकेट मिले)
- ✅ Edit requests (संपादन अनुरोध)
- ✅ Voice input (अभी बोलें, सुन रहा हूं)
- ✅ Visit recording (विज़िट दर्ज करें)
- ✅ Error messages (नेटवर्क उपलब्ध नहीं है)

**Total: 70+ Hindi strings**

#### 3. VoiceInputHelper.kt
**Hindi Voice Recognition**
```kotlin
Language: hi-IN (Hindi - India)
SpeechRecognizer with partial results
Callbacks: onReadyForSpeech, onResult, onError
Error messages in Hindi
```

**Usage:**
```kotlin
val voiceHelper = VoiceInputHelper(context)
voiceHelper.startListening(object : VoiceInputCallback {
    override fun onResult(text: String) {
        editText.setText(text)  // Auto-fill field
    }
    override fun onError(error: String) {
        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
    }
})
```

### Sample UI Screen Architecture

**Recommended XML Layout Pattern (3D Card + Gradient):**
```xml
<ConstraintLayout background="@color/background">
    <!-- Gradient Header -->
    <View
        android:background="@drawable/gradient_primary"
        android:elevation="4dp" />
    
    <!-- 3D Card -->
    <MaterialCardView
        android:elevation="8dp"
        app:cardCornerRadius="16dp"
        android:translationZ="4dp">
        
        <!-- Content with voice buttons -->
        <EditText
            android:hint="@string/reg_name"
            android:inputType="none" /> <!-- No keyboard -->
        
        <ImageButton
            android:src="@drawable/ic_mic"
            android:contentDescription="@string/voice_button_hint" />
    </MaterialCardView>
</ConstraintLayout>
```

**Gradient drawable (res/drawable/gradient_primary.xml):**
```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <gradient
        android:startColor="@color/primary_gradient_start"
        android:endColor="@color/primary_gradient_end"
        android:angle="135" />
</shape>
```

### UI Components Checklist

#### ✅ Core Components Provided
- [x] Colors (sea-green theme)
- [x] Strings (Hindi-only)
- [x] Voice input helper

#### ⏳ To Implement (Follow Patterns)
- [ ] MainActivity with navigation drawer
- [ ] LoginActivity
- [ ] DashboardFragment (shows stats)
- [ ] BeneficiaryRegistrationFragment (with voice + biometric + duplicate warning)
- [ ] VisitRecordingFragments (ANC/BP/BloodSugar/Vaccination)
- [ ] EditRequestListFragment (for Supervisors)
- [ ] DuplicateReviewFragment (for Block Nodal)
- [ ] OfflineIndicatorView (custom status bar)
- [ ] VoiceInputButton (reusable component)

### Key UI Principles

1. **No-Keyboard Policy:**
   - All text fields use `android:inputType="none"`
   - Voice button next to every input
   - Date/Time pickers for dates
   - Chips for gender selection
   - Spinners for dropdowns

2. **Large Touch Targets:**
   - Minimum button size: 56dp (Material guideline)
   - Card padding: 16dp
   - Icon size: 24dp

3. **Offline Indicator:**
```kotlin
// In MainActivity toolbar
class OfflineIndicator : View {
    private val networkMonitor = NetworkMonitor(context)
    
    init {
        lifecycleScope.launch {
            networkMonitor.observeNetworkConnectivity().collect { status ->
                setBackgroundColor(
                    if (status.isConnected) Color.GREEN else Color.RED
                )
            }
        }
    }
}
```

4. **3D Cards with Elevation:**
   - Card elevation: 8dp
   - Shadow blur: 12dp
   - Corner radius: 16dp

---

## ✅ Phase 7: Backend API (PHP 8.2 + MySQL)

### Files Created (4 Core Files + Schema)

#### 1. schema.sql
**Complete MySQL Database Schema**

**13 Tables:**
1. `roles` - 5 pre-populated roles (Field Staff → State Admin)
2. `users` - User accounts with passwords, roles, worker codes
3. `villages` - Geographic hierarchy (State/District/Block/Village)
4. `user_area_mappings` - Field worker assignments
5. `beneficiaries` - Main entity with unique ID, GPS, sync_version
6. `anc_visits` - Antenatal care visits
7. `bp_visits` - Blood pressure monitoring
8. `blood_sugar_visits` - Diabetes screening
9. `vaccination_visits` - Child immunization
10. `edit_requests` - Field Staff → Supervisor workflow
11. `duplicate_flags` - Automatic + manual flagging
12. `audit_logs` - Government-grade audit trail
13. (Biometrics NEVER synced - local only)

**Key Features:**
- UTF-8MB4 collation (Hindi support)
- Proper foreign keys with CASCADE
- Indices on search columns
- FULLTEXT index for name search
- Unique constraints for beneficiary_id
- Sync version tracking

#### 2. database.php
**PDO Connection Class**
```php
- UTF-8MB4 charset
- Error mode: EXCEPTION
- Fetch mode: ASSOC
- Emulate prepares: false (security)
```

#### 3. login.php
**Authentication Endpoint**
```
POST /api/auth/login.php
Request: {username, password}
Response: {token, user {id, role, permissions}}
```

**Features:**
- Password verification (password_verify)
- JWT token generation
- Role-based permissions
- Last login tracking

#### 4. push.php
**Sync Push Endpoint**
```
POST /api/sync/push.php
Headers: Authorization: Bearer {token}
Request: {changes: []}
Response: {synced, conflicts, errors}
```

**Conflict Resolution:**
1. Check server sync_version > client
2. Compare role authority levels
3. Higher role ALWAYS wins
4. Return conflict with server data
5. Client resolves using ConflictResolver

**Supported Entities:**
- BENEFICIARY (CREATE, UPDATE)
- ANC_VISIT, BP_VISIT, BLOOD_SUGAR_VISIT, VACCINATION_VISIT
- EDIT_REQUEST
- DUPLICATE_FLAG
- AUDIT_LOG (insert only)

#### 5. jwt.php
**JWT Utilities**
```php
generateToken($payload) → JWT string
validateRequest() → payload or false
```

- HMAC SHA-256 signing
- 24-hour expiration
- Base64 URL encoding

### API Endpoints Structure

```
backend/api/
├── config/
│   └── database.php
├── utils/
│   ├── jwt.php
│   └── conflict_resolver.php
├── auth/
│   ├── login.php
│   ├── logout.php
│   └── validate.php
├── sync/
│   ├── push.php
│   └── pull.php
├── beneficiaries/
│   ├── create.php
│   ├── update.php
│   ├── search.php
│   └── get.php
├── visits/
│   ├── create.php
│   └── list.php
├── edit_requests/
│   ├── create.php
│   ├── approve.php
│   └── reject.php
└── duplicates/
    ├── list.php
    ├── merge.php
    └── resolve.php
```

### Remaining Endpoints to Implement

#### sync/pull.php
```php
POST /api/sync/pull.php
Request: {last_sync_timestamp}
Response: {changes: [server changes since timestamp]}
```

#### beneficiaries/search.php
```php
GET /api/beneficiaries/search.php?q={hindi_name}&village_id={uuid}
Response: {beneficiaries: [...]}
```

#### duplicates/merge.php
```php
POST /api/duplicates/merge.php
Request: {duplicate_id, master_id, notes}
Response: {success, transferred_visits}
```

### Database Connection Example

```php
require_once '../config/database.php';

$database = new Database();
$db = $database->getConnection();

$query = "SELECT * FROM beneficiaries WHERE village_id = :village_id";
$stmt = $db->prepare($query);
$stmt->bindParam(":village_id", $village_id);
$stmt->execute();

$results = $stmt->fetchAll();
```

### Security Checklist

✅ **Implemented:**
- [x] JWT authentication
- [x] Password hashing (password_verify)
- [x] Prepared statements (SQL injection prevention)
- [x] CORS headers
- [x] Role-based access control

⏳ **To Implement:**
- [ ] Rate limiting
- [ ] Input validation/sanitization
- [ ] HTTPS enforcement
- [ ] SQL injection testing
- [ ] XSS prevention

---

## Integration Flow

```
Android App                     Backend API
    ↓                               ↓
1. Login → POST /api/auth/login.php → JWT Token
    ↓
2. Register Beneficiary Offline → SQLite
    ↓
3. Auto-sync (2 hours) →
    ↓
4. POST /api/sync/push.php
   {changes: [{entity_type: "BENEFICIARY", data: {...}}]}
    ↓                               ↓
5. Server checks conflict → sync_version + role_id
    ↓                               ↓
6. If conflict → return server data
   If success → insert/update MySQL
    ↓                               ↓
7. Response: {synced: [...], conflicts: [...]}
    ↓
8. Client resolves conflicts → ConflictResolver
    ↓
9. Retry sync with resolved data
```

---

## Deployment Guide (Bluehost)

### 1. Upload Files
```
public_html/
└── asha-diary/
    ├── api/
    │   ├── config/database.php
    │   ├── auth/login.php
    │   ├── sync/push.php
    │   └── ... (all endpoints)
    └── index.html (Web PWA - Phase 8)
```

### 2. Create MySQL Database
```
1. cPanel → MySQL Databases
2. Create database: asha_diary
3. Create user: asha_user
4. Grant ALL PRIVILEGES
5. Import schema.sql via phpMyAdmin
```

### 3. Update database.php
```php
private $host = "localhost";  // Bluehost default
private $db_name = "username_asha_diary";  // With cPanel prefix
private $username = "username_asha_user";
private $password = "STRONG_PASSWORD";
```

### 4. Set Permissions
```
chmod 644 *.php
chmod 755 api/
```

### 5. Test Endpoints
```
https://yourdomain.com/asha-diary/api/auth/login.php
```

---

## Production Checklist

### ✅ Phase 6 (Android UI)
- [x] Sea-green gradient theme
- [x] Hindi-only strings (70+)
- [x] Voice input helper (hi-IN)
- [ ] Complete all activity/fragment screens
- [ ] Offline indicator
- [ ] 3D card designs

### ✅ Phase 7 (Backend API)
- [x] Complete MySQL schema (13 tables)
- [x] Database connection class
- [x] JWT authentication
- [x] Login endpoint
- [x] Sync push endpoint with conflict resolution
- [ ] Sync pull endpoint
- [ ] All CRUD endpoints
- [ ] Input validation
- [ ] Rate limiting

---

## Next Steps

1. **Complete UI Screens** (Phase 6):
   - Use ViewModels from Phase 5
   - Follow sea-green theme
   - Implement voice buttons
   - Add 3D card elevations

2. **Complete API Endpoints** (Phase 7):
   - Implement sync/pull.php
   - Add all CRUD endpoints
   - Add validation middleware
   - Implement rate limiting

3. **Phase 8: Web PWA**:
   - Admin dashboard
   - Duplicate review interface
   - Edit request approval
   - Reports generation

---

**Phases 6 & 7 Foundation Complete!** ✅

Ready for:
- UI screen development (follow patterns)
- Remaining API endpoints (follow structure)
- Web PWA development (Phase 8)
