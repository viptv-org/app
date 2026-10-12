package org.viptv.app.connection

import uniffi.viptv_core.SecretStore
import uniffi.viptv_core.systemSecretStore

actual fun platformSecrets(): SecretStore = systemSecretStore()
