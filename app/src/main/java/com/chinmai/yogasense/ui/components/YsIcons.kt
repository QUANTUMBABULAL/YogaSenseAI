package com.chinmai.yogasense.ui.components

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Minimal line-icon set (24-unit grid, 1.8 stroke, round caps) so the app needs no icon
 * dependency and every icon shares one visual weight. Tint via Icon(tint = ...).
 */
object YsIcons {
    private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        ).build()

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx + r, cy)
        arcTo(r, r, 0f, false, false, cx - r, cy)
        arcTo(r, r, 0f, false, false, cx + r, cy)
        close()
    }

    val Home by lazy { icon("home") {
        moveTo(4f, 10.5f); lineTo(12f, 4f); lineTo(20f, 10.5f)
        moveTo(6f, 9f); verticalLineTo(20f); horizontalLineTo(18f); verticalLineTo(9f)
        moveTo(10f, 20f); verticalLineTo(14.5f); horizontalLineTo(14f); verticalLineTo(20f)
    } }

    val Practice by lazy { icon("practice") {
        circle(12f, 5f, 1.9f)
        moveTo(12f, 7.8f); verticalLineTo(14f)
        moveTo(4.5f, 9.5f); lineTo(12f, 11f); lineTo(19.5f, 9.5f)
        moveTo(12f, 14f); lineTo(8.5f, 20f)
        moveTo(12f, 14f); lineTo(15.5f, 20f)
    } }

    val Progress by lazy { icon("progress") {
        moveTo(4f, 20f); horizontalLineTo(20f)
        moveTo(4f, 16f); lineTo(9f, 11f); lineTo(13f, 14f); lineTo(20f, 7f)
        moveTo(16f, 7f); horizontalLineTo(20f); verticalLineTo(11f)
    } }

    val Profile by lazy { icon("profile") {
        circle(12f, 8f, 3.6f)
        moveTo(5f, 20f); curveTo(5f, 16.2f, 8f, 14f, 12f, 14f); curveTo(16f, 14f, 19f, 16.2f, 19f, 20f)
    } }

    val Mood by lazy { icon("mood") {
        circle(12f, 12f, 8.5f)
        moveTo(9f, 10f); verticalLineTo(10.4f)
        moveTo(15f, 10f); verticalLineTo(10.4f)
        moveTo(8.5f, 14f); curveTo(10f, 16.6f, 14f, 16.6f, 15.5f, 14f)
    } }

    val Stress by lazy { icon("stress") {
        moveTo(3f, 12f); lineTo(7f, 12f); lineTo(9f, 7f); lineTo(12f, 17f); lineTo(15f, 9f); lineTo(17f, 12f); lineTo(21f, 12f)
    } }

    val Sleep by lazy { icon("sleep") {
        moveTo(20f, 14f); curveTo(18.5f, 18.5f, 13f, 20.5f, 9f, 18.5f)
        curveTo(5f, 16.5f, 3.5f, 11.5f, 5.5f, 7.5f); curveTo(6.8f, 5f, 9f, 3.7f, 11f, 3.5f)
        curveTo(8.5f, 7.5f, 10f, 13f, 14f, 14.5f); curveTo(16f, 15.3f, 18.2f, 15.1f, 20f, 14f); close()
    } }

    val Camera by lazy { icon("camera") {
        moveTo(4f, 8f); horizontalLineTo(8f); lineTo(9.5f, 6f); horizontalLineTo(14.5f); lineTo(16f, 8f)
        horizontalLineTo(20f); verticalLineTo(18f); horizontalLineTo(4f); close()
        circle(12f, 13f, 3.3f)
    } }

    val Shield by lazy { icon("shield") {
        moveTo(12f, 3f); lineTo(19f, 6f); verticalLineTo(11f); curveTo(19f, 15.5f, 16f, 19f, 12f, 21f)
        curveTo(8f, 19f, 5f, 15.5f, 5f, 11f); verticalLineTo(6f); close()
        moveTo(9f, 12f); lineTo(11f, 14f); lineTo(15f, 10f)
    } }

    val Bell by lazy { icon("bell") {
        moveTo(6f, 16f); verticalLineTo(11f); curveTo(6f, 7.7f, 8.7f, 5f, 12f, 5f); curveTo(15.3f, 5f, 18f, 7.7f, 18f, 11f)
        verticalLineTo(16f); lineTo(19.5f, 17.5f); horizontalLineTo(4.5f); close()
        moveTo(10f, 20.5f); horizontalLineTo(14f)
    } }

    val Info by lazy { icon("info") {
        circle(12f, 12f, 9f)
        moveTo(12f, 11f); verticalLineTo(16.5f)
        moveTo(12f, 7.8f); verticalLineTo(8f)
    } }

    val Trash by lazy { icon("trash") {
        moveTo(5f, 7f); horizontalLineTo(19f)
        moveTo(9.5f, 7f); verticalLineTo(4.5f); horizontalLineTo(14.5f); verticalLineTo(7f)
        moveTo(7f, 7f); lineTo(8f, 20f); horizontalLineTo(16f); lineTo(17f, 7f)
    } }

    val Export by lazy { icon("export") {
        moveTo(12f, 4f); verticalLineTo(15f)
        moveTo(7.5f, 10.5f); lineTo(12f, 15f); lineTo(16.5f, 10.5f)
        moveTo(5f, 19.5f); horizontalLineTo(19f)
    } }

    val ChevronRight by lazy { icon("chevron") { moveTo(9.5f, 6f); lineTo(15.5f, 12f); lineTo(9.5f, 18f) } }

    val Play by lazy { icon("play") { moveTo(8f, 5.5f); lineTo(18.5f, 12f); lineTo(8f, 18.5f); close() } }

    val Calendar by lazy { icon("calendar") {
        moveTo(4f, 6.5f); horizontalLineTo(20f); verticalLineTo(20f); horizontalLineTo(4f); close()
        moveTo(4f, 10.5f); horizontalLineTo(20f)
        moveTo(8f, 4f); verticalLineTo(8f)
        moveTo(16f, 4f); verticalLineTo(8f)
    } }

    val Leaf by lazy { icon("leaf") {
        moveTo(5f, 19f); curveTo(5f, 10f, 10f, 5f, 19f, 5f); curveTo(19f, 14f, 14f, 19f, 5f, 19f); close()
        moveTo(5f, 19f); lineTo(13f, 11f)
    } }

    val Spark by lazy { icon("spark") {
        moveTo(12f, 3.5f); lineTo(13.7f, 10.3f); lineTo(20.5f, 12f); lineTo(13.7f, 13.7f)
        lineTo(12f, 20.5f); lineTo(10.3f, 13.7f); lineTo(3.5f, 12f); lineTo(10.3f, 10.3f); close()
    } }
}
