# ASHA Diary - Android Application

## Production-Grade Offline-First Health Worker App

### Technical Stack
- **Language**: Kotlin
- **Architecture**: MVVM + Repository Pattern
- **Database**: Room (Encrypted SQLite)
- **Min SDK**: 26 (Android 8.0 Oreo)
- **Background Sync**: WorkManager
- **Biometric**: Mantra MFS100 via RD Service
- **UI Theme**: Sea-green gradient with 3D cards

### Project Structure
```
app/
├── src/main/
│   ├── java/com/upgovt/ashadiary/
│   │   ├── data/
│   │   │   ├── local/
│   │   │   │   ├── dao/          # Room DAOs
│   │   │   │   ├── entities/     # Room Entities
│   │   │   │   └── database/     # Database instance
│   │   │   ├── repository/       # Repository pattern
│   │   │   └── sync/             # Sync engine
│   │   ├── domain/
│   │   │   ├── models/           # Business models
│   │   │   └── usecases/         # Use cases
│   │   ├── presentation/
│   │   │   ├── ui/               # Activities & Fragments
│   │   │   ├── viewmodel/        # ViewModels
│   │   │   └── adapters/         # RecyclerView adapters
│   │   ├── utils/
│   │   │   ├── biometric/        # Mantra RD integration
│   │   │   ├── voice/            # Speech recognizer
│   │   │   ├── encryption/       # Local encryption
│   │   │   └── id/               # Beneficiary ID generator
│   │   └── workers/              # WorkManager workers
│   └── res/
│       ├── layout/               # XML layouts (3D cards)
│       ├── values/               # Colors, strings (Hindi)
│       └── drawable/             # Sea-green gradients
```

### Core Principles
1. **Offline-First**: All writes → Local SQLite → Sync later
2. **No Fingerprint Upload**: Biometrics never leave device
3. **Role Authority**: Higher role overrides lower role
4. **Audit Ready**: Full GPS-stamped audit trail
5. **Hindi UI**: Zero English text
6. **Voice Input**: SpeechRecognizer everywhere

### Beneficiary ID Format
```
SS-DDD-BBB-VVVV-W-NNNN
09-146-137-4462-A-0001
```

### Sync Strategy
- **Frequency**: Every 2 hours (WorkManager)
- **Conflict Resolution**: Higher role always wins
- **Data Synced**: Health data + GPS coordinates
- **Never Synced**: Fingerprint templates
- **Retry**: Failed records auto-retry

### Security
- Encrypted SQLite (SQLCipher)
- Local biometric storage only
- Role-based access control
- GPS stamping for all writes
