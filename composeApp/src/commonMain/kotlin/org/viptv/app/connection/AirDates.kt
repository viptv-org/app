package org.viptv.app.connection

/**
 * The day an announced next episode airs, `Nov 4`, in the viewer's zone (app#132): the server
 * sends the release instant (`ContinuationEntry.next_episode.released_at_millis`) and each
 * platform formats it, as it already does for History's day labels.
 */
internal expect fun airDateLabel(atMillis: Long): String