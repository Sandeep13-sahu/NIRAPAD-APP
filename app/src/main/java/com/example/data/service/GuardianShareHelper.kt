package com.example.data.service

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import java.util.Locale

object GuardianShareHelper {

    fun buildOpenStreetMapLink(lat: Double, lon: Double): String =
        String.format(
            Locale.US,
            "https://www.openstreetmap.org/?mlat=%.5f&mlon=%.5f#map=17/%.5f/%.5f",
            lat,
            lon,
            lat,
            lon
        )

    fun buildLiveShareMessage(
        travellerName: String,
        locationName: String,
        lat: Double,
        lon: Double,
        destinationLabel: String,
        shareTokenId: String,
        remainingMeters: Int? = null
    ): String {
        val mapUrl = buildOpenStreetMapLink(lat, lon)
        val cleanLoc = locationName.trim().ifBlank {
            String.format(Locale.US, "%.4f, %.4f", lat, lon)
        }
        val cleanName = travellerName.trim().ifBlank { "I am" }
        val cleanDest = destinationLabel.trim().ifBlank { "destination" }
        val tokenLine = if (shareTokenId.isNotBlank()) {
            "\nNIRAPAD Live Share Token: $shareTokenId"
        } else {
            ""
        }
        val etaLine = if (remainingMeters != null && remainingMeters > 0) {
            val distStr = if (remainingMeters >= 1000) {
                String.format(Locale.US, "%.1f km", remainingMeters / 1000.0)
            } else {
                "$remainingMeters m"
            }
            "\nDistance remaining: $distStr"
        } else {
            ""
        }
        return "$cleanName sharing live location via NIRAPAD Safety Navigation." +
            "\nCurrent location: $cleanLoc" +
            "\nHeading to: $cleanDest" +
            etaLine +
            tokenLine +
            "\nTrack live on map: $mapUrl"
    }

    fun launchWhatsAppShare(context: Context, message: String) {
        val whatsAppIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            `package` = "com.whatsapp"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        try {
            context.startActivity(whatsAppIntent)
        } catch (_: ActivityNotFoundException) {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            }
            context.startActivity(Intent.createChooser(fallback, "Share Live Location with Guardian"))
        }
    }
}
