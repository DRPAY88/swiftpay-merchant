package com.example.myapplication.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun MerchantLogo(
    size: Dp = 120.dp,
    modifier: Modifier = Modifier
) {
    DenshitxBankLogo(modifier = modifier, size = size)
}
