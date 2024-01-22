package com.klaviyo.sdktestapp.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension

@Composable
fun CopyText(
    value: String = "",
    defaultValue: String = "",
    label: String = "",
    onTextCopied: () -> Unit = {},
    copyButtonLabel: String = "Copy"
) {
    ConstraintLayout(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(16.dp)
    ) {
        val (labelField, displayField, copyButton) = createRefs()
        Text(
            text = label,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.constrainAs(labelField) {
                top.linkTo(parent.top)
                bottom.linkTo(displayField.top)
                start.linkTo(parent.start)
            },
            fontSize = 12.sp
        )
        Text(
            text = value.ifEmpty { defaultValue },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .selectable(selected = false, enabled = false, null) {}
                .constrainAs(displayField) {
                    top.linkTo(labelField.bottom)
                    bottom.linkTo(parent.bottom)
                    start.linkTo(parent.start)
                    end.linkTo(copyButton.start, 16.dp)
                    width = Dimension.fillToConstraints
                }
        )
        Button(
            enabled = value.isNotBlank(),
            onClick = onTextCopied,
            elevation = ButtonDefaults.elevation(0.dp),
            shape = CircleShape,
            modifier = Modifier.constrainAs(copyButton) {
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                end.linkTo(parent.end)
            }
        ) {
            Text(text = copyButtonLabel)
        }
    }
}

@Preview
@Composable
private fun PreviewCopyText() {
    CopyText("Sample copy string", "Default Value", "PreviewCopyText")
}

@Preview
@Composable
private fun PreviewEmptyCopyText() {
    CopyText("", "Default Value", "PreviewEmptyCopyText")
}
