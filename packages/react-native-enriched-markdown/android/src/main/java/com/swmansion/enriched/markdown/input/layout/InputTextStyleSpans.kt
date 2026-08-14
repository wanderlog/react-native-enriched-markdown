package com.swmansion.enriched.markdown.input.layout

import android.content.res.AssetManager
import android.graphics.Paint
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import com.facebook.react.common.ReactConstants
import com.facebook.react.views.text.ReactTypefaceUtils
import com.facebook.react.views.text.TextAttributes
import com.swmansion.enriched.markdown.input.formatting.InputFormatter
import com.swmansion.enriched.markdown.input.model.BlockRange
import com.swmansion.enriched.markdown.input.model.BlockType
import com.swmansion.enriched.markdown.input.spans.InputCssLineHeightSpan

private class InputAbsoluteSizeSpan(
  size: Int,
) : AbsoluteSizeSpan(size),
  InputBaseStyleSpanMarker

private class InputForegroundColorSpan(
  color: Int,
) : ForegroundColorSpan(color),
  InputBaseStyleSpanMarker

/** Typeface span matching RN CustomStyleSpan measure behavior using public APIs. */
private class InputTypefaceSpan(
  private val fontStyle: Int,
  private val fontWeight: Int,
  private val fontFamily: String?,
  private val assetManager: AssetManager,
) : MetricAffectingSpan(),
  InputBaseStyleSpanMarker {
  override fun updateDrawState(textPaint: TextPaint) {
    applyTypeface(textPaint)
  }

  override fun updateMeasureState(textPaint: TextPaint) {
    applyTypeface(textPaint)
  }

  private fun applyTypeface(paint: Paint) {
    paint.typeface =
      ReactTypefaceUtils.applyStyles(
        paint.typeface,
        fontStyle,
        fontWeight,
        fontFamily,
        assetManager,
      )
    paint.isSubpixelText = true
    paint.isLinearText = true
  }
}

/** Base TextInput style props copied for Yoga measurement caching. */
internal data class InputTextStyleParams(
  val textAttributes: TextAttributes,
  val defaultColor: Int,
  val fontStyle: Int,
  val fontWeight: Int,
  val fontFamily: String?,
)

internal fun copyTextAttributes(source: TextAttributes): TextAttributes =
  TextAttributes().apply {
    allowFontScaling = source.allowFontScaling
    fontSize = source.fontSize
    lineHeight = source.lineHeight
  }

/**
 * Extra line height (SP) above body font size: bodyLineHeight - bodyFontSize.
 * May be negative when the caller wants overlapping lines.
 */
internal fun bodyLineHeightExtraSp(textAttributes: TextAttributes): Float? {
  val bodyLineHeightSp = textAttributes.lineHeight
  if (bodyLineHeightSp.isNaN() || textAttributes.fontSize <= 0f) {
    return null
  }
  return bodyLineHeightSp - textAttributes.fontSize
}

/**
 * Derived heading line height in px: headingFontSize + bodyExtra, using the same
 * font-scaling path as body [TextAttributes.effectiveLineHeight].
 */
internal fun headingLineHeightPx(
  bodyTextAttributes: TextAttributes,
  headingFontSizePx: Float?,
): Float {
  val extraSp = bodyLineHeightExtraSp(bodyTextAttributes) ?: return Float.NaN

  val bodyFontSizeSp = bodyTextAttributes.fontSize
  if (bodyFontSizeSp <= 0f) {
    return Float.NaN
  }

  val headingFontSizeSp =
    if (headingFontSizePx != null) {
      val bodyFontSizePx = bodyTextAttributes.effectiveFontSize.toFloat()
      if (bodyFontSizePx <= 0f) {
        bodyFontSizeSp
      } else {
        headingFontSizePx / bodyFontSizePx * bodyFontSizeSp
      }
    } else {
      bodyFontSizeSp
    }

  return TextAttributes()
    .apply {
      allowFontScaling = bodyTextAttributes.allowFontScaling
      fontSize = headingFontSizeSp
      lineHeight = headingFontSizeSp + extraSp
    }.effectiveLineHeight
}

/**
 * Based on addSpansFromStyleAttributes from React Native's ReactEditText.
 * Applies base TextInput style spans (font size, color, typeface). Line height
 * is applied separately for body vs heading paragraphs.
 *
 * https://github.com/react/react-native/blob/v0.86.2/packages/react-native/ReactAndroid/src/main/java/com/facebook/react/views/textinput/ReactEditText.kt#L793-L859
 */
internal fun applyBaseStyleSpans(
  text: Spannable,
  styleParams: InputTextStyleParams,
  assets: AssetManager,
  stripExisting: Boolean,
) {
  if (stripExisting) {
    removeBaseStyleSpans(text)
  }

  if (text.isEmpty()) {
    return
  }

  var spanFlags = Spannable.SPAN_INCLUSIVE_INCLUSIVE
  // Lowest precedence so markdown formatting spans stay on top.
  spanFlags = spanFlags or Spannable.SPAN_PRIORITY

  text.setSpan(
    InputAbsoluteSizeSpan(styleParams.textAttributes.effectiveFontSize),
    0,
    text.length,
    spanFlags,
  )

  text.setSpan(
    InputForegroundColorSpan(styleParams.defaultColor),
    0,
    text.length,
    spanFlags,
  )

  if (
    styleParams.fontStyle != ReactConstants.UNSET ||
    styleParams.fontWeight != ReactConstants.UNSET ||
    styleParams.fontFamily != null
  ) {
    text.setSpan(
      InputTypefaceSpan(
        styleParams.fontStyle,
        styleParams.fontWeight,
        styleParams.fontFamily,
        assets,
      ),
      0,
      text.length,
      spanFlags,
    )
  }
}

/**
 * Body line height on plain paragraphs only, mirroring iOS
 * ENRMInputApplyBaseLineHeightToPlainParagraphs (skip heading block ranges).
 */
internal fun applyBodyLineHeightToPlainParagraphs(
  text: Spannable,
  styleParams: InputTextStyleParams,
  headingBlockRanges: List<BlockRange>,
) {
  val effectiveLineHeight = styleParams.textAttributes.effectiveLineHeight
  if (effectiveLineHeight.isNaN() || text.isEmpty()) {
    return
  }

  removeBodyLineHeightSpans(text)

  var spanFlags = Spannable.SPAN_INCLUSIVE_INCLUSIVE or Spannable.SPAN_PRIORITY

  var index = 0
  val length = text.length
  while (index < length) {
    val lineBreak = text.indexOf('\n', index)
    val paragraphStart = index
    val paragraphEnd = if (lineBreak == -1) length else lineBreak

    // Skip heading paragraphs; they get derived line height from InputHeadingSpan.
    var intersectsHeadingBlock = false
    for (range in headingBlockRanges) {
      if (range.type !in BlockType.HEADINGS) {
        continue
      }
      val rangeEnd =
        if (range.length == 0 && range.start < paragraphEnd) {
          range.start + 1
        } else {
          range.end
        }
      if (range.start < paragraphEnd && rangeEnd > paragraphStart) {
        intersectsHeadingBlock = true
        break
      }
    }

    if (paragraphEnd > paragraphStart && !intersectsHeadingBlock) {
      text.setSpan(
        InputCssLineHeightSpan(effectiveLineHeight),
        paragraphStart,
        paragraphEnd,
        spanFlags,
      )
    }

    index =
      if (lineBreak == -1) {
        length
      } else {
        lineBreak + 1
      }
  }
}

/**
 * Spannable for StaticLayout measurement using the same spans as the live editor.
 */
internal fun buildMeasureSpannable(
  text: CharSequence?,
  hint: CharSequence?,
  styleParams: InputTextStyleParams,
  assets: AssetManager,
  blockRanges: List<BlockRange>,
  formatter: InputFormatter,
): SpannableStringBuilder {
  // Text to measure when the editor buffer is empty: actual text, else hint, else
  // a single character so StaticLayout never sees zero-length input. React Native
  // does the same thing when the buffer is empty (uses hint for measurement).
  // We also fall back to "I" when there is no hint — without something to
  // measure, empty fields collapse to ~0 height and Yoga clips multiline growth.
  //
  // https://github.com/react/react-native/blob/v0.86.2/packages/react-native/ReactAndroid/src/main/java/com/facebook/react/views/textinput/ReactEditText.kt#L1093-L1100
  val workingText =
    when {
      text != null && text.isNotEmpty() -> text
      hint != null && hint.isNotEmpty() -> hint
      else -> "I"
    }
  val sb = SpannableStringBuilder(workingText)
  formatter.bodyTextAttributes = copyTextAttributes(styleParams.textAttributes)
  applyBaseStyleSpans(sb, styleParams, assets, stripExisting = false)
  formatter.applyBlockFormatting(sb, blockRanges)
  applyBodyLineHeightToPlainParagraphs(
    sb,
    styleParams,
    headingBlockRanges(blockRanges),
  )
  return sb
}

internal fun headingBlockRanges(blockRanges: List<BlockRange>): List<BlockRange> = blockRanges.filter { it.type in BlockType.HEADINGS }

private fun removeBaseStyleSpans(text: Spannable) {
  text
    .getSpans(0, text.length, InputBaseStyleSpanMarker::class.java)
    .forEach { text.removeSpan(it) }
  removeBodyLineHeightSpans(text)
}

private fun removeBodyLineHeightSpans(text: Spannable) {
  text
    .getSpans(0, text.length, InputCssLineHeightSpan::class.java)
    .forEach { text.removeSpan(it) }
}
