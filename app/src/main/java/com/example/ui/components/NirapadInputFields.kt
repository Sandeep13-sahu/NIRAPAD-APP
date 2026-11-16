package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.theme.NirapadDarkTeal
import com.example.ui.theme.NirapadEmergencyRed
import com.example.ui.theme.NirapadPrimaryTeal

object NirapadInputDefaults {
    val Shape: Shape = RoundedCornerShape(12.dp)

    private val LightContainerFocused = Color(0xFFFFFFFF)
    private val LightContainerUnfocused = Color(0xFFF8FAFC)
    private val LightContainerDisabled = Color(0xFFE8EEF4)
    private val LightText = Color(0xFF0F172A)
    private val LightPlaceholder = Color(0xFF64748B)
    private val LightLabelUnfocused = Color(0xFF334155)
    private val LightBorderUnfocused = Color(0xFF64748B)
    private val LightIconUnfocused = Color(0xFF475569)

    private val DarkContainerFocused = Color(0xFF13283C)
    private val DarkContainerUnfocused = Color(0xFF1A334B)
    private val DarkContainerDisabled = Color(0xFF1F354B)
    private val DarkText = Color(0xFFFFFFFF)
    private val DarkPlaceholder = Color(0xFFABC0D5)
    private val DarkLabelUnfocused = Color(0xFFDDE7F0)
    private val DarkBorderUnfocused = Color(0xFF607E9C)
    private val DarkIconUnfocused = Color(0xFFD0DFEC)

    @Composable
    fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

    @Composable
    fun textColor(darkTheme: Boolean = isDarkTheme()): Color =
        if (darkTheme) DarkText else LightText

    @Composable
    fun placeholderColor(darkTheme: Boolean = isDarkTheme()): Color =
        if (darkTheme) DarkPlaceholder else LightPlaceholder

    @Composable
    fun containerFocusedColor(darkTheme: Boolean = isDarkTheme()): Color =
        if (darkTheme) DarkContainerFocused else LightContainerFocused

    @Composable
    fun colors(darkTheme: Boolean = isDarkTheme()): TextFieldColors {
        val text = if (darkTheme) DarkText else LightText
        val placeholder = if (darkTheme) DarkPlaceholder else LightPlaceholder
        val focusedContainer = if (darkTheme) DarkContainerFocused else LightContainerFocused
        val unfocusedContainer = if (darkTheme) DarkContainerUnfocused else LightContainerUnfocused
        val disabledContainer = if (darkTheme) DarkContainerDisabled else LightContainerDisabled
        val focusedAccent = if (darkTheme) NirapadDarkTeal else NirapadPrimaryTeal
        val unfocusedLabel = if (darkTheme) DarkLabelUnfocused else LightLabelUnfocused
        val unfocusedBorder = if (darkTheme) DarkBorderUnfocused else LightBorderUnfocused
        val unfocusedIcon = if (darkTheme) DarkIconUnfocused else LightIconUnfocused
        val errorAccent = if (darkTheme) Color(0xFFFF8A80) else NirapadEmergencyRed

        return OutlinedTextFieldDefaults.colors(
            focusedTextColor = text,
            unfocusedTextColor = text,
            disabledTextColor = placeholder,
            errorTextColor = text,
            focusedContainerColor = focusedContainer,
            unfocusedContainerColor = unfocusedContainer,
            disabledContainerColor = disabledContainer,
            errorContainerColor = focusedContainer,
            cursorColor = focusedAccent,
            errorCursorColor = errorAccent,
            focusedBorderColor = focusedAccent,
            unfocusedBorderColor = unfocusedBorder,
            disabledBorderColor = unfocusedBorder.copy(alpha = 0.5f),
            errorBorderColor = errorAccent,
            focusedLeadingIconColor = focusedAccent,
            unfocusedLeadingIconColor = unfocusedIcon,
            focusedTrailingIconColor = focusedAccent,
            unfocusedTrailingIconColor = unfocusedIcon,
            focusedLabelColor = focusedAccent,
            unfocusedLabelColor = unfocusedLabel,
            disabledLabelColor = placeholder,
            errorLabelColor = errorAccent,
            focusedPlaceholderColor = placeholder,
            unfocusedPlaceholderColor = placeholder,
            disabledPlaceholderColor = placeholder.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun NirapadOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = NirapadInputDefaults.Shape,
    forceLightSurface: Boolean = false
) {
    val isDark = if (forceLightSurface) false else NirapadInputDefaults.isDarkTheme()
    val resolvedTextColor = NirapadInputDefaults.textColor(isDark)
    val mergedTextStyle = textStyle.merge(
        MaterialTheme.typography.bodyLarge.copy(
            color = resolvedTextColor,
            fontWeight = FontWeight.Medium
        )
    )

    val wrappedLabel: (@Composable () -> Unit)? = label?.let { inner ->
        {
            if (isDark) {
                Box(
                    modifier = Modifier
                        .background(
                            color = NirapadInputDefaults.containerFocusedColor(darkTheme = true),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp)
                ) {
                    inner()
                }
            } else {
                inner()
            }
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.defaultMinSize(minHeight = 56.dp),
        enabled = enabled,
        readOnly = readOnly,
        textStyle = mergedTextStyle,
        label = wrappedLabel,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        shape = shape,
        colors = NirapadInputDefaults.colors(darkTheme = isDark)
    )
}
