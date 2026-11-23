package com.toeipass.navi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class TransitRouterTest {

    private lateinit var dataset: TransitDataset
    private lateinit var router: TransitRouter

    @Before
    fun setUp() {
        val assetFile = listOf(
            File("src/main/assets/transit-data.json"),
            File("app/src/main/assets/transit-data.json")
        ).firstOrNull { it.exists() }
        assertNotNull("transit-data.json should exist in assets", assetFile)
        val jsonText = assetFile!!.readText(Charsets.UTF_8)
        dataset = TransitDataParser.parse(jsonText)
        router = TransitRouter(dataset)
    }

    @Test
    fun datasetLoadsExpectedCounts() {
        assertEquals(200, dataset.stations.size)
        assertEquals(377, dataset.edges.size)
        assertEquals(34, dataset.lines.size)
        assertEquals(13, dataset.operators.size)
    }

    @Test
    fun toeiDirectRouteIsFreeWithToeiPass() {
        val withPass = router.searchRoutes(
            fromId = "shinjuku",
            toId = "roppongi",
            options = SearchOptions(useToeiFreePass = true)
        )
        assertTrue(withPass.isNotEmpty())
        assertEquals(0, withPass.first().totalFare)

        val withoutPass = router.searchRoutes(
            fromId = "shinjuku",
            toId = "roppongi",
            options = SearchOptions(useToeiFreePass = false)
        )
        assertTrue(withoutPass.isNotEmpty())
        assertTrue(withoutPass.first().totalFare > 0)
    }

    @Test
    fun musashiKoganeiToRoppongiFindsMultipleRoutesAndSavesWithPass() {
        val withPass = router.searchRoutes(
            fromId = "musashi_koganei",
            toId = "roppongi",
            options = SearchOptions(useToeiFreePass = true, baseTimeMinutes = 9 * 60)
        )
        assertTrue(withPass.isNotEmpty())
        val cheapestWithPass = withPass.first()
        assertEquals("09:00", cheapestWithPass.departTime)

        val withoutPass = router.searchRoutes(
            fromId = "musashi_koganei",
            toId = "roppongi",
            options = SearchOptions(useToeiFreePass = false, baseTimeMinutes = 9 * 60)
        )
        assertTrue(withoutPass.isNotEmpty())
        assertTrue(
            "Expected Toei pass fare (${cheapestWithPass.totalFare}) < regular fare (${withoutPass.first().totalFare})",
            cheapestWithPass.totalFare < withoutPass.first().totalFare
        )
    }

    @Test
    fun arrivalTimeModeMatchesTargetArrival() {
        val routes = router.searchRoutes(
            fromId = "musashi_koganei",
            toId = "asakusa",
            options = SearchOptions(
                timeMode = TimeMode.ARRIVAL,
                baseTimeMinutes = 14 * 60 + 30
            )
        )
        assertTrue(routes.isNotEmpty())
        assertEquals("14:30", routes.first().arriveTime)
    }

    @Test
    fun accessibleTransferMarginChangesTotalMinutes() {
        val accessible = router.searchRoutes(
            fromId = "musashi_koganei",
            toId = "roppongi",
            options = SearchOptions(accessibleTransfer = true)
        ).first()

        val standard = router.searchRoutes(
            fromId = "musashi_koganei",
            toId = "roppongi",
            options = SearchOptions(accessibleTransfer = false)
        ).first()

        assertEquals(9, accessible.transferMargin)
        assertEquals(5, standard.transferMargin)
        if (accessible.transferCount > 0 && accessible.signature == standard.signature) {
            assertEquals(
                accessible.transferCount * 4,
                accessible.totalMinutes - standard.totalMinutes
            )
        }
    }

    @Test
    fun filterStationsMatchesNameKanaAndArea() {
        assertTrue(dataset.filterStations("むさしこがねい").any { it.id == "musashi_koganei" })
        assertTrue(dataset.filterStations("六本木").any { it.id == "roppongi" })
        assertTrue(dataset.filterStations("多摩").isNotEmpty())
    }
}
