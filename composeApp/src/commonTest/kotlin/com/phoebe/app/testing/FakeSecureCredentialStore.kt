package com.phoebe.app.testing

import com.phoebe.app.domain.ListenBrainzCredentialStorageStatus
import com.phoebe.app.platform.SecureCredentialAvailability
import com.phoebe.app.platform.SecureCredentialKey
import com.phoebe.app.platform.SecureCredentialStore

class FakeSecureCredentialStore(
    override val availability: SecureCredentialAvailability = SecureCredentialAvailability(
        status = ListenBrainzCredentialStorageStatus.PersistentSecure,
        description = "Fake secure store",
    ),
) : SecureCredentialStore {
    val values = mutableMapOf<SecureCredentialKey, String>()

    override suspend fun read(key: SecureCredentialKey): String? = values[key]

    override suspend fun write(key: SecureCredentialKey, value: String) {
        values[key] = value
    }

    override suspend fun delete(key: SecureCredentialKey) {
        values.remove(key)
    }
}
