package org.viptv.app.state

fun ApplicationController.detailPage(offset: Long) = run {
    require(offset in 0..10_000)
    val previous = mutable.value.detail ?: return@run
    val profile = requireNotNull(mutable.value.selectedProfile)
    val page = local?.detail(profile, previous.item, offset.toInt())
        ?: requireNotNull(connection).detail(previous.item, offset.toInt())
    if (mutable.value.detail?.item?.identity != previous.item.identity || mutable.value.selectedProfile != profile) return@run
    mutable.value = mutable.value.copy(detail = page.copy(
        episodes = (previous.episodes + page.episodes).distinctBy { it.provider_id to it.identity },
        recommendations = page.recommendations.ifEmpty { previous.recommendations },
        seasons = page.seasons.ifEmpty { previous.seasons }))
}
