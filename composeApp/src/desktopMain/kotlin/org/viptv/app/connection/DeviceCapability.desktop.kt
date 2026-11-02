package org.viptv.app.connection

// libmpv exposes no decoder answers through the bridge, and CPU features are not evidence.
internal actual suspend fun platformDeviceCapability(): DeviceCapability = DeviceCapability()
