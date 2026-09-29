package com.magicarmy.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.magicarmy.app.R
import com.magicarmy.core.model.CardType
import com.magicarmy.core.model.Race

object Ink {
    val Paper = Color(0xFFF4ECDC)
    val PaperDeep = Color(0xFFEADFC8)
    val Line = Color(0xFF2B2320)
    val Faded = Color(0xFF6B5E54)
    val You = Color(0xFF2F6DB5)
    val Enemy = Color(0xFFB33A3A)
    val Gold = Color(0xFFD9A21E)
    val Move = Color(0xFF3F7FC0)
    val Attack = Color(0xFFC8412F)
    val Target = Color(0xFF8E5BC9)
    val Deploy = Color(0xFF3F9A5A)
    val Heal = Color(0xFF2E8B57)

    fun race(r: Race): Color = when (r) {
        Race.WOLF -> Color(0xFF5B6C8F)
        Race.BEAR -> Color(0xFF8A5A3B)
        Race.HAWK -> Color(0xFFB7832F)
        Race.SERPENT -> Color(0xFF3F7D57)
        Race.LION -> Color(0xFFC0662B)
    }

    fun type(t: CardType): Color = when (t) {
        CardType.UNIT -> Color(0xFF7A5C3E)
        CardType.MAGIC -> Color(0xFF7D4FB0)
        CardType.STRATEGY -> Color(0xFFB9852A)
        CardType.EQUIPMENT -> Color(0xFF5E7282)
    }
}

val HandFont = FontFamily(
    Font(R.font.kalam_regular, FontWeight.Normal),
    Font(R.font.kalam_bold, FontWeight.Bold),
)
val BrushFont = FontFamily(Font(R.font.caveat_brush, FontWeight.Normal))

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = BrushFont, fontSize = 56.sp, color = Ink.Line),
    headlineMedium = TextStyle(fontFamily = BrushFont, fontSize = 34.sp, color = Ink.Line),
    titleLarge = TextStyle(fontFamily = BrushFont, fontSize = 26.sp, color = Ink.Line),
    titleMedium = TextStyle(fontFamily = HandFont, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink.Line),
    bodyLarge = TextStyle(fontFamily = HandFont, fontSize = 16.sp, color = Ink.Line),
    bodyMedium = TextStyle(fontFamily = HandFont, fontSize = 14.sp, color = Ink.Line),
    bodySmall = TextStyle(fontFamily = HandFont, fontSize = 12.sp, color = Ink.Line),
    labelLarge = TextStyle(fontFamily = HandFont, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink.Line),
    labelMedium = TextStyle(fontFamily = HandFont, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Ink.Line),
    labelSmall = TextStyle(fontFamily = HandFont, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Ink.Line),
)

@Composable
fun MagicArmyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink.You,
            onPrimary = Ink.Paper,
            secondary = Ink.Gold,
            background = Ink.Paper,
            surface = Ink.Paper,
            onSurface = Ink.Line,
            onBackground = Ink.Line,
            error = Ink.Enemy,
        ),
        typography = AppTypography,
        content = content,
    )
}
