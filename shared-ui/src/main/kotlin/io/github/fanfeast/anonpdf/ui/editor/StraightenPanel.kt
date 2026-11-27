package io.github.fanfeast.anonpdf.ui.editor

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.fanfeast.anonpdf.i18n.S
import io.github.fanfeast.anonpdf.pdf.PdfScan
import io.github.fanfeast.anonpdf.ui.components.LabeledSlider
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Turns crooked pages level, by hand with a slider the preview follows, or by
 * measuring each page's lines of text. Measuring makes its own edit at once,
 * one angle per page, since scanned pages rarely lean the same way.
 */
@Composable
fun StraightenPanel(
    angle: Float,
    targetLabel: String,
    measuring: Boolean,
    /** What the last automatic attempt found to say, if it found nothing. */
    message: String?,
    onAngleChange: (Float) -> Unit,
    onAuto: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    PanelFrame(
        title = S.straightenTitle,
        subtitle = targetLabel,
        onCancel = onCancel,
        onApply = onApply,
        applyEnabled = angle != 0f && !measuring,
    ) {
        LabeledSlider(
            label = S.straightenAngle,
            value = angle,
            onValueChange = { onAngleChange((it * 10).roundToInt() / 10f) },
            valueRange = -PdfScan.MAX_TILT..PdfScan.MAX_TILT,
            valueLabel = String.format(Locale.ROOT, "%.1f°", angle),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onAngleChange(((angle - 0.1f) * 10).roundToInt() / 10f) }) {
                Icon(Icons.Filled.Remove, contentDescription = "−0.1°")
            }
            IconButton(onClick = { onAngleChange(((angle + 0.1f) * 10).roundToInt() / 10f) }) {
                Icon(Icons.Filled.Add, contentDescription = "+0.1°")
            }
        }
        Spacer(Modifier.height(8.dp))
        if (measuring) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(S.straightenWorking, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            OutlinedButton(onClick = onAuto) {
                Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(S.straightenAuto)
            }
        }
        if (message != null) {
            Spacer(Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            S.straightenAutoHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
