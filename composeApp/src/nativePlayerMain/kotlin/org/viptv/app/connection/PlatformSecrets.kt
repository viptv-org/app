package org.viptv.app.connection

import uniffi.viptv_core.SecretStore

expect fun platformSecrets(): SecretStore
