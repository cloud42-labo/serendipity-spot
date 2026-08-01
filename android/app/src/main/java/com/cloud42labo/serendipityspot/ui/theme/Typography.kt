package com.cloud42labo.serendipityspot.ui.theme

import androidx.compose.material3.Typography

/**
 * M3のデフォルト値をそのまま使う。既存画面の見た目を変えないことが目的で、
 * 将来フォントを変更する際にこの1箇所を書き換えれば済むようにするための明示化。
 * fontWeight等を独自に指定すると、行高・字間を含めMaterial3のデフォルトから
 * ずれて既存レイアウトの折り返しが変わりうるため、値を上書きしない。
 */
val AppTypography = Typography()
