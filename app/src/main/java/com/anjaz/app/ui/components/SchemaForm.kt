@file:OptIn(ExperimentalLayoutApi::class)

package com.anjaz.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.anjaz.app.schema.FieldSchema
import com.anjaz.app.schema.FieldType
import com.anjaz.app.schema.ModuleSchema
import com.anjaz.app.ui.theme.Anjaz

@Composable
fun SchemaForm(
    schema: ModuleSchema,
    values: Map<String, String>,
    errors: Map<String, String>,
    onChange: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        schema.fields.forEach { f ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    f.label + if (f.required) " *" else "",
                    color = Anjaz.colors.ink, style = MaterialTheme.typography.labelLarge,
                )
                if (f.type == FieldType.CHOICE) ChoiceRow(f, values[f.key].orEmpty()) { onChange(f.key, it) }
                else TextInput(f, values[f.key].orEmpty(), errors[f.key]) { onChange(f.key, it) }
                errors[f.key]?.let { Text(it, color = Anjaz.colors.redDeep, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun TextInput(f: FieldSchema, value: String, error: String?, onChange: (String) -> Unit) {
    val c = Anjaz.colors
    val numeric = f.type == FieldType.PHONE || f.type == FieldType.NUMBER
    val style = MaterialTheme.typography.bodyLarge.copy(
        color = c.ink,
        textDirection = if (numeric) TextDirection.Ltr else TextDirection.Content,
    )
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= f.maxLength) onChange(it) },
        isError = error != null,
        singleLine = f.type != FieldType.MULTILINE,
        minLines = if (f.type == FieldType.MULTILINE) 3 else 1,
        textStyle = style,
        keyboardOptions = KeyboardOptions(
            keyboardType = when (f.type) {
                FieldType.PHONE -> KeyboardType.Phone
                FieldType.NUMBER -> KeyboardType.Number
                else -> KeyboardType.Text
            },
        ),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.emerald, unfocusedBorderColor = c.line, errorBorderColor = c.red,
            focusedContainerColor = c.card, unfocusedContainerColor = c.card, errorContainerColor = c.card,
            cursorColor = c.emerald,
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    )
}

@Composable
private fun ChoiceRow(f: FieldSchema, selected: String, onPick: (String) -> Unit) {
    val c = Anjaz.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        f.choices.forEach { ch ->
            val on = ch.key == selected
            val shape = RoundedCornerShape(14.dp)
            Text(
                ch.label,
                color = if (on) c.onNavy else c.ink,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.heightIn(min = 44.dp).clip(shape)
                    .background(if (on) c.navy else c.card)
                    .border(BorderStroke(1.dp, if (on) c.navy else c.line), shape)
                    .clickable { onPick(ch.key) }
                    .semantics { role = Role.RadioButton }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

private fun Modifier.fillMaxWidth(): Modifier = this.then(Modifier.fillMaxWidth())
