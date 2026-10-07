package io.haru.assistant.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** A labeled, read-only selector shared by appearance and AI settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> HaruOptionDropdown(
    label: String,
    selectedText: String,
    options: List<T>,
    optionLabel: (T) -> String,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    actionLabel: String? = null,
    actionEnabled: Boolean = true,
    onAction: (() -> Unit)? = null,
) {
    // Popup state is deliberately transient; dismissing/reopening a dialog
    // must not reopen a menu or restore a pending action.
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 280.dp),
        ) {
            options.forEach { option ->
                val optionSelected = isSelected(option)
                DropdownMenuItem(
                    text = {
                        Text(optionLabel(option), style = MaterialTheme.typography.bodyMedium)
                    },
                    trailingIcon = if (optionSelected) {
                        { Text("✓", color = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { selected = optionSelected },
                    onClick = {
                        expanded = false
                        if (!optionSelected) onSelect(option)
                    },
                )
            }

            if (actionLabel != null && onAction != null) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Text(actionLabel, style = MaterialTheme.typography.bodyMedium)
                    },
                    enabled = actionEnabled,
                    modifier = Modifier.heightIn(min = 48.dp),
                    onClick = {
                        expanded = false
                        onAction()
                    },
                )
            }
        }
    }
}
