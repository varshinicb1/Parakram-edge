package com.example.data.firebase

import android.content.Context
import android.os.Bundle
import timber.log.Timber
import com.example.data.AuditLog
import com.example.data.AutomationWorkflow
import com.example.data.local.AdminProfileEntity
import com.example.data.local.AppDatabase
import com.example.data.local.AuditLogEntity
import com.example.data.local.WorkflowEntity
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

class FirebaseManager(private val context: Context, private val db: AppDatabase? = null) {
    private var auth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null
    private var analytics: FirebaseAnalytics? = null
    
    private val _isFirebaseAvailable = MutableStateFlow(false)
    val isFirebaseAvailable: StateFlow<Boolean> = _isFirebaseAvailable

    private val _currentUserFlow = MutableStateFlow<UserSession?>(null)
    val currentUserFlow: StateFlow<UserSession?> = _currentUserFlow

    init {
        try {
            // Check if Firebase is initialized. The google-services plugin might not have
            // found a google-services.json, in which case FirebaseApp won't be initialized.
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                auth = FirebaseAuth.getInstance()
                firestore = FirebaseFirestore.getInstance()
                analytics = FirebaseAnalytics.getInstance(context)
                _isFirebaseAvailable.value = true
                
                auth?.addAuthStateListener { firebaseAuth ->
                    val user = firebaseAuth.currentUser
                    _currentUserFlow.value = user?.toUserSession()
                }
                Timber.d("FirebaseManager", "Firebase successfully initialized and available (with Analytics).")
            } else {
                Timber.w("FirebaseManager", "FirebaseApp is not initialized. Using Sandbox mode.")
                _isFirebaseAvailable.value = false
            }
        } catch (e: Exception) {
            Timber.e("FirebaseManager", "Failed to initialize Firebase: ${e.message}. Using Sandbox mode.")
            _isFirebaseAvailable.value = false
        }
    }

    fun logEvent(name: String, params: Bundle? = null) {
        if (_isFirebaseAvailable.value) {
            try {
                analytics?.logEvent(name, params)
                Timber.d("FirebaseManager", "Logged Analytics Event: $name, parameters: $params")
            } catch (e: Exception) {
                Timber.e("FirebaseManager", "Error logging Analytics Event: ${e.message}")
            }
        } else {
            Timber.d("FirebaseManager", "Sandbox Mode Event Logged: $name, parameters: $params")
        }
    }

    private var sandboxUser: UserSession? = null

    private fun getDeviceUid(): String {
        return try {
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
            )
            "device_${androidId ?: java.util.UUID.randomUUID().toString().take(8)}"
        } catch (e: Exception) {
            "device_${java.util.UUID.randomUUID().toString().take(8)}"
        }
    }

    fun signInWithEmail(email: String, name: String) {
        if (_isFirebaseAvailable.value && auth != null) {
            auth?.signInWithEmailAndPassword(email, "devapi1234")
                ?.addOnFailureListener {
                    auth?.createUserWithEmailAndPassword(email, "devapi1234")
                        ?.addOnSuccessListener { result ->
                            val profileUpdates = com.google.firebase.auth.userProfileChangeRequest {
                                displayName = name
                            }
                            result.user?.updateProfile(profileUpdates)
                        }
                }
        } else {
            sandboxUser = UserSession(
                uid = getDeviceUid(),
                email = email,
                displayName = name,
                isSandbox = true
            )
            _currentUserFlow.value = sandboxUser
        }
    }

    fun signInWithGoogle(idToken: String, email: String, name: String, photoUrl: String) {
        if (_isFirebaseAvailable.value && auth != null) {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth?.signInWithCredential(credential)
                ?.addOnFailureListener {
                    signInWithEmail(email, name)
                }
        } else {
            sandboxUser = UserSession(
                uid = getDeviceUid(),
                email = email,
                displayName = name,
                photoUrl = photoUrl,
                isSandbox = true
            )
            _currentUserFlow.value = sandboxUser
        }
    }

    fun signOut() {
        if (_isFirebaseAvailable.value && auth != null) {
            auth?.signOut()
        } else {
            sandboxUser = null
            _currentUserFlow.value = null
        }
    }

    // Logs synchronization
    suspend fun saveAuditLog(log: AuditLog) {
        val user = _currentUserFlow.value ?: return
        if (_isFirebaseAvailable.value && firestore != null) {
            try {
                firestore!!.collection("users")
                    .document(user.uid)
                    .collection("logs")
                    .document(log.id)
                    .set(log)
                    .await()
            } catch (e: Exception) {
                Timber.e("FirebaseManager", "Error saving log to Firestore: ${e.message}")
            }
        } else {
            db?.auditLogDao()?.insert(log.toEntity())
        }
    }

    suspend fun getAuditLogs(): List<AuditLog> {
        val user = _currentUserFlow.value ?: return emptyList()
        if (_isFirebaseAvailable.value && firestore != null) {
            return try {
                val snapshot = firestore!!.collection("users")
                    .document(user.uid)
                    .collection("logs")
                    .orderBy("timestamp")
                    .get()
                    .await()
                snapshot.toObjects(AuditLog::class.java)
            } catch (e: Exception) {
                Timber.e("FirebaseManager", "Error loading logs: ${e.message}")
                emptyList()
            }
        } else {
            return db?.auditLogDao()?.getAll()?.map { it.toAuditLog() } ?: emptyList()
        }
    }

    // Workflows synchronization
    suspend fun saveWorkflow(workflow: AutomationWorkflow) {
        val user = _currentUserFlow.value ?: return
        if (_isFirebaseAvailable.value && firestore != null) {
            try {
                firestore!!.collection("users")
                    .document(user.uid)
                    .collection("workflows")
                    .document(workflow.id)
                    .set(workflow)
                    .await()
            } catch (e: Exception) {
                Timber.e("FirebaseManager", "Error saving workflow to Firestore: ${e.message}")
            }
        } else {
            db?.workflowDao()?.insert(workflow.toEntity())
        }
    }

    suspend fun getWorkflows(): List<AutomationWorkflow> {
        val user = _currentUserFlow.value ?: return emptyList()
        if (_isFirebaseAvailable.value && firestore != null) {
            return try {
                val snapshot = firestore!!.collection("users")
                    .document(user.uid)
                    .collection("workflows")
                    .get()
                    .await()
                val list = snapshot.toObjects(AutomationWorkflow::class.java)
                if (list.isEmpty()) {
                    getDefaultWorkflows().forEach { saveWorkflow(it) }
                    getDefaultWorkflows()
                } else {
                    list
                }
            } catch (e: Exception) {
                Timber.e("FirebaseManager", "Error loading workflows: ${e.message}")
                emptyList()
            }
        } else {
            val fromDb = db?.workflowDao()?.getAll().orEmpty()
            if (fromDb.isEmpty()) {
                val defaults = getDefaultWorkflows()
                defaults.forEach { db?.workflowDao()?.insert(it.toEntity()) }
                return defaults
            }
            return fromDb.map { it.toAutomationWorkflow() }
        }
    }

    private fun getDefaultWorkflows(): List<AutomationWorkflow> {
        return emptyList()
    }

    suspend fun saveAdminProfile(profile: AdminProfile) {
        val user = _currentUserFlow.value ?: return
        if (_isFirebaseAvailable.value && firestore != null) {
            try {
                firestore!!.collection("users")
                    .document(user.uid)
                    .collection("profile")
                    .document("admin")
                    .set(profile)
                    .await()
            } catch (e: Exception) {
                Timber.e("FirebaseManager", "Error saving admin profile to Firestore: ${e.message}")
            }
        } else {
            db?.adminProfileDao()?.insert(profile.toEntity())
        }
    }

    suspend fun getAdminProfile(): AdminProfile {
        val user = _currentUserFlow.value ?: return AdminProfile()
        if (_isFirebaseAvailable.value && firestore != null) {
            return try {
                val document = firestore!!.collection("users")
                    .document(user.uid)
                    .collection("profile")
                    .document("admin")
                    .get()
                    .await()
                document.toObject(AdminProfile::class.java) ?: AdminProfile(
                    uid = user.uid,
                    displayName = user.displayName,
                    email = user.email,
                    apiKey = "pk_live_" + java.util.UUID.randomUUID().toString().replace("-", "").take(16)
                )
            } catch (e: Exception) {
                Timber.e("FirebaseManager", "Error getting admin profile: ${e.message}")
                AdminProfile(uid = user.uid, displayName = user.displayName, email = user.email)
            }
        } else {
            val fromDb = db?.adminProfileDao()?.getByUid(user.uid)
            if (fromDb != null) return fromDb.toAdminProfile()
            val fresh = AdminProfile(
                uid = user.uid,
                displayName = user.displayName,
                email = user.email,
                apiKey = "pk_live_" + java.util.UUID.randomUUID().toString().replace("-", "").take(16)
            )
            db?.adminProfileDao()?.insert(fresh.toEntity())
            return fresh
        }
    }

    private fun AuditLog.toEntity() = AuditLogEntity(
        id = id, timestamp = timestamp, method = method,
        endpoint = endpoint, caller = caller, status = status,
        payload = payload, type = type
    )

    private fun AuditLogEntity.toAuditLog() = AuditLog(
        id = id, timestamp = timestamp, method = method,
        endpoint = endpoint, caller = caller, status = status,
        payload = payload, type = type
    )

    private fun AutomationWorkflow.toEntity() = WorkflowEntity(
        id = id, title = title, description = description,
        trigger = trigger, action = action, isActive = isActive
    )

    private fun WorkflowEntity.toAutomationWorkflow() = AutomationWorkflow(
        id = id, title = title, description = description,
        trigger = trigger, action = action, isActive = isActive
    )

    private fun AdminProfile.toEntity() = AdminProfileEntity(
        uid = uid, displayName = displayName, email = email,
        organization = organization, developerRole = developerRole,
        apiKey = apiKey, maxDevices = maxDevices, securityLevel = securityLevel
    )

    private fun AdminProfileEntity.toAdminProfile() = AdminProfile(
        uid = uid, displayName = displayName, email = email,
        organization = organization, developerRole = developerRole,
        apiKey = apiKey, maxDevices = maxDevices, securityLevel = securityLevel
    )

    private fun FirebaseUser.toUserSession() = UserSession(
        uid = uid,
        email = email ?: "",
        displayName = displayName ?: "Companion Developer",
        photoUrl = photoUrl?.toString() ?: "https://lh3.googleusercontent.com/a/default-user=s96-c",
        isSandbox = false
    )
}

data class UserSession(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val isSandbox: Boolean = false
)

data class AdminProfile(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val organization: String = "Enterprise Core",
    val developerRole: String = "Lead Systems Architect",
    val apiKey: String = "",
    val maxDevices: Int = 10,
    val securityLevel: String = "ECC-384 + TLS 1.3"
)
