package com.erumi.nativeapp.shared.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.erumi.nativeapp.ui.theme.ErumiBorder
import com.erumi.nativeapp.ui.theme.ErumiCardBg
import com.erumi.nativeapp.ui.theme.ErumiMuted
import com.erumi.nativeapp.ui.theme.ErumiPrimary
import com.erumi.nativeapp.ui.theme.ErumiPrimaryText
import com.erumi.nativeapp.ui.theme.ErumiSecondaryText

@Composable
fun MediaSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit = { onQueryChange("") },
    modifier: Modifier = Modifier,
    placeholder: String = "Search...",
    onSearch: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(text = placeholder, color = ErumiMuted) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = ErumiPrimary)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = ErumiSecondaryText)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = ErumiCardBg,
            unfocusedContainerColor = ErumiCardBg,
            focusedBorderColor = ErumiPrimary,
            unfocusedBorderColor = ErumiBorder,
            focusedTextColor = ErumiPrimaryText,
            unfocusedTextColor = ErumiPrimaryText,
            cursorColor = ErumiPrimary
        )
    )
}
