package org.viptv.app

import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.Profile
import org.viptv.app.features.entry.*
import org.viptv.app.resources.Res
import kotlin.test.*

class AvatarCatalogTest {
    private val catalog = runBlocking { loadAvatarCatalog() }

    @Test fun bundledCatalogHasTheSixteenDesignWorldsInOrder() {
        assertEquals(listOf("disney", "princesses", "animal-friends", "villains", "critters", "pixelbot", "voxel-bot",
            "pixel-art", "pixel-art-neutral", "sprouts", "planets", "clay", "moods", "thumbs", "lorelei", "notionists"),
            catalog.worlds.map { it.style })
        assertEquals(listOf(14, 10, 12, 12) + List(12) { 48 }, catalog.worlds.map { it.count })
        assertEquals(624, catalog.total)
        assertEquals("Disney favorites", catalog.world("disney")?.name)
        assertEquals("Creatures", catalog.world("critters")?.name)
    }

    @Test fun choicesResolveToBundledFilesAndRejectPositionsOutsideTheirWorld() {
        assertEquals("files/avatars/avatar-catalog/critters-1.png", catalog.avatarPath("critters", 1))
        assertEquals("files/avatars/avatar-catalog/notionists-48.png", catalog.avatarPath("notionists", 48))
        assertEquals("files/avatars/avatar-characters/disney-6-curated.png", catalog.avatarPath("disney", 6))
        assertEquals("files/avatars/avatar-characters/villains-12.png", catalog.avatarPath("villains", 12))
        assertNull(catalog.avatarPath("critters", 0))
        assertNull(catalog.avatarPath("critters", 49))
        assertNull(catalog.avatarPath("disney", 15))
        assertNull(catalog.avatarPath("unknown", 1))
        assertNotNull(catalog.avatarUri("critters", 1))
        assertNull(catalog.avatarUri("critters", 49))
    }

    @Test fun everyCatalogPositionIsAPackagedImage() = runBlocking {
        val paths = catalog.worlds.flatMap { world -> (1..world.count).map { catalog.avatarPath(world.style, it)!! } }
        assertEquals(624, paths.toSet().size)
        paths.forEach { assertTrue(Res.readBytes(it).size > 100, it) }
    }

    @Test fun savedCatalogPickBeatsLegacyAndRemoteArtwork() {
        val remote = Profile("1", "Ann", null, avatarStyle = "clay", avatarChoice = 4, avatarUrl = "https://avatars.example/clay-4.png")
        assertEquals(AvatarPick("clay", 4), remote.avatarPick())
        assertEquals(AvatarPick("clay", 4), remote.copy(avatarId = "vynxc").avatarPick())
        assertEquals(AvatarPick("critters", 7), Profile("1", "Ann", null, avatarChoice = 7).avatarPick())
    }

    @Test fun noPickShowsTheServerSeedOrTheLocalDefault() {
        assertNull(Profile("1", "Ann", null, avatarStyle = "critters", avatarUrl = "https://avatars.example/seed.png").avatarPick())
        assertEquals(AvatarPick("critters", 1), Profile("1", "Ann", null, avatarStyle = "critters").avatarPick())
        assertNull(Profile("1", "Ann", null, avatarId = "zayne", avatarStyle = "critters").avatarPick())
        assertNull(Profile("1", "Ann", null).avatarPick())
    }

    @Test fun phonePagesHoldFifteenAndTelevisionEighteenWithWrapAround() {
        val phone = AvatarPaging(48, AvatarPickerLayout.Phone.pageSize)
        assertEquals(15, AvatarPickerLayout.Phone.pageSize)
        assertEquals(18, AvatarPickerLayout.Television.pageSize)
        assertEquals(18, AvatarPickerLayout.Desktop.pageSize)
        assertEquals(4, phone.pages)
        assertEquals(1..15, phone.choices(0))
        assertEquals(46..48, phone.choices(3))
        assertEquals(0, phone.next(3))
        assertEquals(3, phone.previous(0))
        assertEquals(2, phone.next(1))
        assertEquals(3, phone.pageOf(46))
        assertEquals(0, phone.pageOf(1))
        val tv = AvatarPaging(48, AvatarPickerLayout.Television.pageSize)
        assertEquals(3, tv.pages)
        assertEquals(37..48, tv.choices(2))
        val disney = AvatarPaging(14, 18)
        assertEquals(1, disney.pages)
        assertEquals(0, disney.next(0))
        assertEquals(0, disney.previous(0))
        assertEquals(1, AvatarPaging(0, 18).pages)
        assertTrue(AvatarPaging(0, 18).choices(0).isEmpty())
    }
}
