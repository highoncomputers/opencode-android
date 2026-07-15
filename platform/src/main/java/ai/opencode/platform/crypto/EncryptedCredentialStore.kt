package ai.opencode.platform.crypto

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKeyAlias = "opencode_master_key"
    private val prefsFileName = "opencode_encrypted_credentials"

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context, masterKeyAlias)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setRequestStrongBoxBacked(true)
            .build()
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            prefsFileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _storedKeys = MutableStateFlow<Set<String>>(emptySet())
    val storedKeys: StateFlow<Set<String>> = _storedKeys.asStateFlow()

    init {
        refreshKeysList()
    }

    suspend fun storeCredential(
        key: String,
        value: String,
        metadata: CredentialMetadata? = null
    ): Unit = withContext(Dispatchers.IO) {
        val encryptedValue = encryptValue(value)

        encryptedPrefs.edit().apply {
            putString("${PREFIX_CREDENTIAL}$key", encryptedValue)
            metadata?.let {
                putString("${PREFIX_METADATA}$key", serializeMetadata(it))
            }
            putLong("${PREFIX_TIMESTAMP}$key", System.currentTimeMillis())
            apply()
        }

        refreshKeysList()
    }

    suspend fun retrieveCredential(key: String): String? = withContext(Dispatchers.IO) {
        val encryptedValue = encryptedPrefs.getString("${PREFIX_CREDENTIAL}$key", null)
            ?: return@withContext null

        try {
            decryptValue(encryptedValue)
        } catch (e: KeyPermanentlyInvalidatedException) {
            // Biometric/lock screen changed, credential is lost
            removeCredential(key)
            null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun retrieveCredentialMetadata(key: String): CredentialMetadata? =
        withContext(Dispatchers.IO) {
            val serialized = encryptedPrefs.getString("${PREFIX_METADATA}$key", null)
                ?: return@withContext null
            deserializeMetadata(serialized)
        }

    suspend fun hasCredential(key: String): Boolean = withContext(Dispatchers.IO) {
        encryptedPrefs.contains("${PREFIX_CREDENTIAL}$key")
    }

    suspend fun removeCredential(key: String): Unit = withContext(Dispatchers.IO) {
        encryptedPrefs.edit().apply {
            remove("${PREFIX_CREDENTIAL}$key")
            remove("${PREFIX_METADATA}$key")
            remove("${PREFIX_TIMESTAMP}$key")
            apply()
        }
        refreshKeysList()
    }

    suspend fun clearAll(): Unit = withContext(Dispatchers.IO) {
        encryptedPrefs.edit().clear().apply()
        refreshKeysList()
    }

    suspend fun listCredentials(): List<CredentialEntry> = withContext(Dispatchers.IO) {
        val keys = _storedKeys.value
        keys.mapNotNull { key ->
            val timestamp = encryptedPrefs.getLong("${PREFIX_TIMESTAMP}$key", 0)
            val metadata = retrieveCredentialMetadata(key)
            CredentialEntry(
                key = key,
                hasValue = encryptedPrefs.contains("${PREFIX_CREDENTIAL}$key"),
                createdAt = timestamp,
                metadata = metadata
            )
        }.sortedByDescending { it.createdAt }
    }

    suspend fun exportAllCredentials(): Map<String, String> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, String>()
        for (key in _storedKeys.value) {
            val value = retrieveCredential(key)
            if (value != null) {
                result[key] = value
            }
        }
        result
    }

    suspend fun importCredentials(credentials: Map<String, String>): Int =
        withContext(Dispatchers.IO) {
            var count = 0
            for ((key, value) in credentials) {
                storeCredential(key, value)
                count++
            }
            count
        }

    private fun encryptValue(plainText: String): String {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            generateEncryptionKey()
        }

        val secretKey = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
        val cipher = javax.crypto.Cipher.getInstance(TRANSFORMATION)
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey.secretKey)

        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val combined = iv.size + encrypted.size
        val buffer = java.nio.ByteBuffer.allocate(4 + combined)
        buffer.putInt(iv.size)
        buffer.put(iv)
        buffer.put(encrypted)

        return Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
    }

    private fun decryptValue(encryptedBase64: String): String {
        val data = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        val buffer = java.nio.ByteBuffer.wrap(data)

        val ivSize = buffer.int
        val iv = ByteArray(ivSize)
        buffer.get(iv)

        val encrypted = ByteArray(buffer.remaining())
        buffer.get(encrypted)

        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val secretKey = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry

        val cipher = javax.crypto.Cipher.getInstance(TRANSFORMATION)
        val ivSpec = javax.crypto.spec.IvParameterSpec(iv)
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey.secretKey, ivSpec)

        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun generateEncryptionKey() {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        keyGenerator.generateKey()
    }

    private fun serializeMetadata(metadata: CredentialMetadata): String {
        return buildString {
            append(metadata.providerName)
            append(DELIMITER)
            append(metadata.providerId)
            append(DELIMITER)
            append(metadata.description ?: "")
            append(DELIMITER)
            append(metadata.scope ?: "")
        }
    }

    private fun deserializeMetadata(serialized: String): CredentialMetadata? {
        val parts = serialized.split(DELIMITER, limit = 4)
        if (parts.size < 2) return null
        return CredentialMetadata(
            providerName = parts[0],
            providerId = parts[1],
            description = parts.getOrNull(2)?.ifEmpty { null },
            scope = parts.getOrNull(3)?.ifEmpty { null }
        )
    }

    private fun refreshKeysList() {
        val keys = mutableSetOf<String>()
        val all = encryptedPrefs.all
        for ((key, _) in all) {
            if (key.startsWith(PREFIX_CREDENTIAL)) {
                keys.add(key.removePrefix(PREFIX_CREDENTIAL))
            }
        }
        _storedKeys.value = keys
    }

    companion object {
        private const val KEY_ALIAS = "opencode_credential_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFIX_CREDENTIAL = "cred_"
        private const val PREFIX_METADATA = "meta_"
        private const val PREFIX_TIMESTAMP = "ts_"
        private const val DELIMITER = "||"
    }
}

data class CredentialMetadata(
    val providerName: String,
    val providerId: String,
    val description: String? = null,
    val scope: String? = null
)

data class CredentialEntry(
    val key: String,
    val hasValue: Boolean,
    val createdAt: Long,
    val metadata: CredentialMetadata? = null
)
