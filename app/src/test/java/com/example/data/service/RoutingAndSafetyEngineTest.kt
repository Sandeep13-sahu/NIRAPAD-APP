package com.example.data.service

import com.example.data.model.JourneyState
import com.example.data.model.JourneyStateMachine
import com.example.data.model.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingAndSafetyEngineTest {

    @Test
    fun journeyStateMachine_validAndTerminalTransitions_enforced() {
        assertTrue(JourneyStateMachine.canTransition(JourneyState.PLANNED, JourneyState.ACTIVE))
        assertTrue(JourneyStateMachine.canTransition(JourneyState.ACTIVE, JourneyState.DEVIATION_DETECTED))
        assertTrue(JourneyStateMachine.canTransition(JourneyState.DEVIATION_DETECTED, JourneyState.ACTIVE))
        assertTrue(JourneyStateMachine.canTransition(JourneyState.ACTIVE, JourneyState.COMPLETED))

        // Terminal states cannot reopen
        assertFalse(JourneyStateMachine.canTransition(JourneyState.COMPLETED, JourneyState.ACTIVE))
        assertFalse(JourneyStateMachine.canTransition(JourneyState.CANCELLED, JourneyState.ACTIVE))
    }

    @Test
    fun minDistanceToPolyline_onRouteVsOffRoute_accurateMeters() {
        val route = listOf(
            LatLon(28.6300, 77.2100),
            LatLon(28.6300, 77.2200)
        )
        val onRouteDist = RoutingAndSafetyEngine.minDistanceToPolylineMeters(
            LatLon(28.6300, 77.2150),
            route
        )
        assertTrue("Expected on-route < 5m, got $onRouteDist", onRouteDist < 5.0)

        // ~0.004 deg north ~ 445m
        val offRouteDist = RoutingAndSafetyEngine.minDistanceToPolylineMeters(
            LatLon(28.6340, 77.2150),
            route
        )
        assertTrue("Expected off-route > 400m, got $offRouteDist", offRouteDist > 400.0)
        assertEquals(4, RoutingAndSafetyEngine.verifiedNationalHelplines.size)
    }
}
