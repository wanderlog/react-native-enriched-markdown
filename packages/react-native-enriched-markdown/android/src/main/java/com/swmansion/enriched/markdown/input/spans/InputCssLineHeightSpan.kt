package com.swmansion.enriched.markdown.input.spans

import android.graphics.Paint.FontMetricsInt
import android.text.style.LineHeightSpan
import com.swmansion.enriched.markdown.input.layout.InputBaseStyleSpanMarker
import kotlin.math.ceil
import kotlin.math.floor

/**
 * CSS-like line height for input text, copied from RN CustomLineHeightSpan so we
 * do not depend on React Native internal span types.
 *
 * applyCssLineHeight mirrors CustomLineHeightSpan.chooseHeight:
 * https://github.com/react/react-native/blob/v0.86.2/packages/react-native/ReactAndroid/src/main/java/com/facebook/react/views/text/internal/span/CustomLineHeightSpan.kt#L44-L57
 */
internal fun applyCssLineHeight(
  fm: FontMetricsInt,
  lineHeightPx: Int,
  start: Int,
  end: Int,
  textLength: Int,
) {
  val leading = lineHeightPx - ((-fm.ascent) + fm.descent)
  fm.ascent -= ceil(leading / 2.0f).toInt()
  fm.descent += floor(leading / 2.0f).toInt()

  if (start == 0) {
    fm.top = fm.ascent
  }
  if (end == textLength) {
    fm.bottom = fm.descent
  }
}

/** Body-paragraph line height span; stripped by [InputTextStyleSpans] helpers. */
internal class InputCssLineHeightSpan(
  lineHeightPx: Float,
) : LineHeightSpan,
  InputBaseStyleSpanMarker {
  private val lineHeight: Int = ceil(lineHeightPx.toDouble()).toInt()

  override fun chooseHeight(
    text: CharSequence,
    start: Int,
    end: Int,
    spanstartv: Int,
    v: Int,
    fm: FontMetricsInt,
  ) {
    applyCssLineHeight(fm, lineHeight, start, end, text.length)
  }
}
