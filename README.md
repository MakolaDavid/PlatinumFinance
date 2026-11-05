# Moalosi Platinum Finance — Android PoE (Final Submission)

**Developer:** Moalosi David Makola  
**Student Number:** ST10457546  
**Module:** Open Source Coding (Final PoE)

---


---

## 2. Purpose of the Application
**Moalosi Platinum Finance** is a mobile micro-finance and personal transaction management application tailored for South African consumers. The application enables users to securely register and authenticate using their 13-digit South African ID number, calculate transparent short-term loan repayments compliant with the South African National Credit Act (NCA) fee structure (20% interest, R165 initiation fee, and R60 monthly service fee), record financial transactions offline using local SQLite persistence, automatically synchronize offline records to an ASP.NET Core REST API backed by SQL Server, and archive binary receipt images alongside unstructured metadata in a NoSQL document vault.

---

## 3. Design Considerations & System Architecture
1. **Security-First Authentication (SHA-256 & BCrypt + SSO):**
    * User passwords are encrypted client-side on the Android device using the `SHA-256` cryptographic hashing algorithm (`java.security.MessageDigest`) prior to network transmission, and salted/hashed server-side using `BCrypt.Net`.
    * Federated **Single Sign-On (SSO)** is integrated to allow seamless one-tap authentication via Google and Microsoft Entra accounts.
2. **Hybrid Multi-Database Architecture (REST SQL + Offline RoomDB + NoSQL + Blob Storage):**
    * **Cloud REST API (`Retrofit2` + ASP.NET Core + SQL Server):** Handles centralized user authentication (`api/auth/register`, `api/auth/login`) and cloud loan processing (`api/loans/apply`).
    * **Offline-First Persistence (`Android Jetpack RoomDB`):** Allows users in low-connectivity areas to record transactions offline (`isSynced = false`). When internet connectivity is restored (`ConnectivityManager`), pending records synchronize with the cloud API and update to `isSynced = true`.
    * **NoSQL Document Store & Binary Large Object (Blob) Storage:** Unstructured digital receipts (binary `.jpg` streams) are saved to a dedicated Blob Storage container, while schema-free JSON metadata documents (`_id`, `category`, `metadataNotes`, `blobUri`, `blobSizeBytes`) are written to and read from a local NoSQL JSON document collection.
3. **Inclusivity & Localization (Multi-Language Support & Dark Mode):**
    * Full localization across **three South African official languages**: **English (`en`)**, **Sesotho (`st`)**, and **isiZulu (`zu`)**, managed dynamically via `SharedPreferences` and `Locale` configuration alongside a Dark Mode theme toggle.
4. **Defensive UI & Real-Time Push Notifications:**
    * Strict input validation prevents crashes on empty inputs, malformed email addresses, or invalid 13-digit SA ID numbers.
    * Real-time status bar notifications (`NotificationChannel` & `NotificationManagerCompat`) alert users immediately upon offline saves and cloud synchronization completion.

---

## 4. Utilisation of GitHub & GitHub Actions (CI/CD Automated Testing)
* **Version Control:** All source code was incrementally committed and pushed to GitHub, culminating in the tagged release **`final PoE`**.
* **Continuous Integration (`.github/workflows/build.yml`):** A GitHub Actions workflow runs automatically on every push and pull request to the `master`/`main` branch on an `ubuntu-latest` runner with JDK 17.
* **Automated JUnit4 Test Suite (`ExampleUnitTest.kt`):**
    1. `loanRepaymentCalculation_isCorrect`: Validates the NCA loan formula (`Principal + 20% Interest + R165 Initiation + R60 Service Fee`).
    2. `loanRepaymentCalculation_handlesInvalidZeroOrNegativeInput`: Confirms defensive handling of zero and negative inputs.
    3. `passwordEncryption_generatesValidSha256Hash`: Verifies deterministic 64-character hexadecimal SHA-256 hashing.
    4. `loanApplicationModel_mapsPropertiesCorrectly`: Validates DTO serialization prior to REST API transmission.

---

## 5. Release Notes (Updates Since Prototype & Innovative Features)

### Version 2.0.0 — Final PoE Release
* **NEW (Innovative Feature) — Offline-to-Online Automatic Cloud Sync Engine:** Added a local RoomDB SQLite queue (`offline_transactions`) paired with `ConnectivityManager` network detection to allow full transaction recording without mobile data, syncing seamlessly to the C# `LoansController` REST API upon reconnection.
* **NEW (Innovative Feature) — Hybrid NoSQL Document Vault & Binary Blob Receipt Storage:** Implemented `NoSqlVaultActivity` allowing users to attach binary receipt images into a Blob container and store/retrieve flexible JSON documents.
* **NEW (Innovative Feature) — Real-Time South African Localization Engine:** Added instant in-app language switching between **English**, **Sesotho (`values-st`)**, and **isiZulu (`values-zu`)** without requiring an OS reboot.
* **NEW — Client-Side SHA-256 Password Encryption & Single Sign-On (SSO):** Upgraded `RegisterActivity` to hash passwords before HTTP transmission and added federated SSO account selection.
* **NEW — Real-Time Push Notification System:** Added Android 8.0+ Notification Channels and Android 13+ runtime notification alerts controllable via User Settings.
* **FIXED — Physical Device Network Tunneling:** Migrated `RetrofitClient` from local emulator loopback (`10.0.2.2`) to a public HTTPS Dev Tunnel endpoint for physical smartphone execution.

---

## 6. Declaration on the Use of AI Tools (Max 500 Words)
In accordance with institutional academic integrity guidelines, artificial intelligence assistance (Google Gemini, 2026) was utilised as a collaborative debugging and learning companion during the finalisation of this Portfolio of Evidence. All architectural decisions, business logic implementation, testing execution, and deployment verification were performed and validated by the developer.

Specifically, AI tools were used and cited in the following areas:
1. **Network Debugging & Dev Tunnel Configuration:** When transitioning the application from the Android Emulator (`10.0.2.2:5000`) to a physical Android smartphone, connection timeouts occurred. Gemini assisted in diagnosing the loopback limitation and guided the configuration of a public persistent Visual Studio Dev Tunnel (`https://*.devtunnels.ms/`) inside `RetrofitClient.kt` and resolving a duplicate `<PackageReference>` XML tag in `PlatinumFinanceApi.csproj`.
2. **CI/CD Workflow & JUnit Test Structure:** AI guidance was used to structure the `.github/workflows/build.yml` YAML configuration for JDK 17 Gradle builds and to refine JUnit4 assertions inside `ExampleUnitTest.kt`.

**AI Citation:**  
Google (2026) *Gemini Large Language Model* [AI Assistant]. Used for Android Kotlin debugging, localization verification, and GitHub Actions YAML structuring. Available at: https://gemini.google.com (Accessed: 04 November 2026).

---

## 7. Code & Academic References
* Android Developers (2025) *Save data in a local database using Room*. Available at: https://developer.android.com/training/data-storage/room
* Android Developers (2025) *MessageDigest Cryptographic API*. Available at: https://developer.android.com/reference/java/security/MessageDigest
* Android Developers (2025) *Create and Manage Notification Channels*. Available at: https://developer.android.com/develop/ui/views/notifications/channels
* Square (2025) *Retrofit 2 Type-Safe HTTP Client for Android and Java*. Available at: https://square.github.io/retrofit/