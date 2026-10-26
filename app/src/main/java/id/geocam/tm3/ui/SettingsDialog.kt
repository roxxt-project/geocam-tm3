package id.geocam.tm3.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.geocam.tm3.StampSettings
import id.geocam.tm3.stamp.Format
import id.geocam.tm3.stamp.PhotoSaver

private const val NOTE_MAX = 40

@Composable
fun SettingsDialog(initial: StampSettings, onDismiss: () -> Unit, onSave: (StampSettings) -> Unit) {
    var note by remember { mutableStateOf(initial.note) }
    var decimalComma by remember { mutableStateOf(initial.decimalComma) }
    var showAddress by remember { mutableStateOf(initial.showAddress) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pengaturan stamp") },
        text = {
            Column {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(NOTE_MAX) },
                    label = { Text("Catatan (opsional)") },
                    placeholder = { Text("mis. BT-01") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                ToggleRow(
                    title = "Koma desimal",
                    subtitle = "Contoh: " + Format.metres(236205.15, decimalComma) + " m",
                    checked = decimalComma,
                    onChange = { decimalComma = it },
                )
                ToggleRow(
                    title = "Tampilkan alamat",
                    subtitle = "Kelurahan, kecamatan, kota",
                    checked = showAddress,
                    onChange = { showAddress = it },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Foto tersimpan di DCIM/${PhotoSaver.FOLDER}. Tanpa login, tanpa iklan, tanpa akses internet.",
                    color = Muted,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(StampSettings(note = note.trim(), decimalComma = decimalComma, showAddress = showAddress))
            }) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        },
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Orange, checkedThumbColor = Color.Black),
        )
    }
}
