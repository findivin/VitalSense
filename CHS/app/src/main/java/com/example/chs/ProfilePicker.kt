package com.example.chs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun ProfilePicker(profiles: List<Profile>, selected: Profile, onSelect: (Profile) -> Unit, onAdd: (String, Int?, String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        TextButton(onClick = { expanded = true }) { Text("Profil: ${selected.name} ▾") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            profiles.forEach { profile ->
                DropdownMenuItem(text = { Text(profile.name) }, onClick = { expanded = false; onSelect(profile) })
            }
            DropdownMenuItem(text = { Text("+ Adaugă profil") }, onClick = { expanded = false; adding = true })
        }
        val details = listOfNotNull(selected.age?.let { "$it ani" }, selected.description.takeIf { it.isNotBlank() }).joinToString(" • ")
        if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodyMedium)
        if (selected.isSample) {
            Text("Profil demonstrativ: istoricul include date simulate. Măsurătorile noi vor fi adăugate la acestea.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
    if (adding) AddProfileDialog(onDismiss = { adding = false }, onAdd = { name, age, description ->
        onAdd(name, age, description)
        adding = false
    })
}

@Composable
fun AddProfileDialog(onDismiss: () -> Unit, onAdd: (String, Int?, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val validAge = age.isBlank() || (age.toIntOrNull()?.let { it in 1..120 } == true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profil nou") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nume") }, singleLine = true)
                OutlinedTextField(value = age, onValueChange = { age = it }, label = { Text("Vârstă (opțional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = !validAge, singleLine = true)
                if (!validAge) Text("Introduceți o vârstă între 1 și 120.", color = MaterialTheme.colorScheme.error)
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Descriere (opțional)") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank() && validAge, onClick = { onAdd(name.trim(), age.toIntOrNull(), description.trim()) }) { Text("Adaugă") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anulează") } }
    )
}
