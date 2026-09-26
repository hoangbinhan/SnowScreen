package com.hoangbinhan.snowscreen.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun ProUnlockDialog(
    priceText: String = "$0.99",
    onDismiss: () -> Unit,
    onUnlockClick: () -> Unit,
    onRestoreClick: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1E293B),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Title
                Text(
                    text = "🔒 Unlock Configuration Pro",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE0F7FA),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Gain full fine-tuning control over your snowfall experience!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )

                // Feature Checklist Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF0F172A),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProFeatureRow("✨ Custom Snow Density (50 - 300 flakes)")
                        ProFeatureRow("⚡ Custom Fall Speed & Size (0.5x - 3.0x)")
                        ProFeatureRow("🌬️ Custom Base Wind & Drift Direction")
                        ProFeatureRow("♾️ Lifetime Access & Instant Restoration")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Primary Unlock Action Button
                Button(
                    onClick = onUnlockClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E5FF),
                        contentColor = Color(0xFF0F172A)
                    )
                ) {
                    Text(
                        text = "Unlock Pro ($priceText)",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp
                    )
                }

                // Restore Purchase Button
                TextButton(
                    onClick = onRestoreClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Restore Purchase",
                        color = Color(0xFF80D8FF),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ProFeatureRow(featureText: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "✓", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(
            text = featureText,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFE0F7FA),
            fontWeight = FontWeight.Medium
        )
    }
}
