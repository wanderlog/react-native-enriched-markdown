package com.swmansion.enriched.markdown.input.layout

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.common.ReactConstants
import com.facebook.react.uimanager.PixelUtil
import com.facebook.react.views.text.TextAttributes
import com.facebook.yoga.YogaMeasureMode
import com.facebook.yoga.YogaMeasureOutput
import com.swmansion.enriched.markdown.input.formatting.InputFormatter
import com.swmansion.enriched.markdown.input.formatting.InputParser
import com.swmansion.enriched.markdown.input.model.BlockRange
import com.swmansion.enriched.markdown.utils.input.MarkdownStyleParser
import java.util.concurrent.ConcurrentHashMap

object InputMeasurementStore {
  private data class MeasurementParams(
    val cachedWidth: Float,
    val cachedSize: Long,
    val text: CharSequence?,
    val styleParams: InputTextStyleParams,
    val hint: CharSequence?,
    val paint: TextPaint,
    val blockRanges: List<BlockRange>,
    val formatter: InputFormatter,
  )

  private val data = ConcurrentHashMap<Int, MeasurementParams>()

  internal fun store(
    context: Context,
    id: Int,
    text: CharSequence?,
    styleParams: InputTextStyleParams,
    hint: CharSequence?,
    paint: TextPaint,
    blockRanges: List<BlockRange>,
    formatter: InputFormatter,
  ): Boolean {
    val cachedWidth = data[id]?.cachedWidth ?: 0f
    val cachedSize = data[id]?.cachedSize ?: 0L

    val paintCopy = TextPaint(paint)
    val size =
      measure(
        context = context,
        maxWidth = cachedWidth,
        text = text,
        hint = hint,
        styleParams = styleParams,
        paint = paintCopy,
        blockRanges = blockRanges,
        formatter = formatter,
      )

    data[id] =
      MeasurementParams(
        cachedWidth,
        size,
        text,
        styleParams,
        hint,
        paintCopy,
        blockRanges,
        formatter,
      )
    return size != cachedSize
  }

  fun release(id: Int) {
    data.remove(id)
  }

  fun getMeasureById(
    context: Context,
    id: Int?,
    width: Float,
    height: Float,
    heightMode: YogaMeasureMode?,
    props: ReadableMap?,
  ): Long {
    val size = getMeasureByIdInternal(context, id, width, props)
    if (heightMode !== YogaMeasureMode.AT_MOST) {
      return size
    }

    val calculatedHeight = YogaMeasureOutput.getHeight(size)
    val atMostHeight = PixelUtil.toDIPFromPixel(height)
    val finalHeight = calculatedHeight.coerceAtMost(atMostHeight)
    return YogaMeasureOutput.make(YogaMeasureOutput.getWidth(size), finalHeight)
  }

  private fun getMeasureByIdInternal(
    context: Context,
    id: Int?,
    width: Float,
    props: ReadableMap?,
  ): Long {
    if (id == null) return initialMeasure(context, width, props)
    val value = data[id] ?: return initialMeasure(context, width, props)

    if (width == value.cachedWidth) {
      return value.cachedSize
    }

    val size =
      measure(
        context = context,
        maxWidth = width,
        text = value.text,
        hint = value.hint,
        styleParams = value.styleParams,
        paint = TextPaint(value.paint),
        blockRanges = value.blockRanges,
        formatter = value.formatter,
      )
    data[id] =
      MeasurementParams(
        width,
        size,
        value.text,
        value.styleParams,
        value.hint,
        value.paint,
        value.blockRanges,
        value.formatter,
      )
    return size
  }

  private fun initialMeasure(
    context: Context,
    width: Float,
    props: ReadableMap?,
  ): Long {
    // Measure the rendered plain text, not the raw markdown. A mention link such as
    // [Label](placeholder://x) hides its URL in the source, so measuring the markdown counts those
    // invisible characters and over-estimates the height into extra lines, until the next text
    // change forces a re-measure. Parsing first matches what the editor actually renders.
    val markdown =
      props?.getString("defaultValue") ?: props?.getString("placeholder") ?: "I"
    val parseResult = InputParser.parseToPlainTextAndRanges(markdown)

    val textAttributes = TextAttributes()
    props?.getDouble("fontSize")?.toFloat()?.let { fontSize ->
      if (fontSize > 0f) {
        textAttributes.fontSize = fontSize
      }
    }
    props?.getDouble("lineHeight")?.toFloat()?.let { lineHeight ->
      if (lineHeight > 0f) {
        // Props pass lineHeight in SP; store it on TextAttributes without
        // converting to pixels. React Native converts later in
        // effectiveLineHeight when measuring or rendering.
        // https://github.com/react/react-native/blob/v0.86.2/packages/react-native/ReactAndroid/src/main/java/com/facebook/react/views/text/TextAttributes.kt#L25
        textAttributes.lineHeight = lineHeight
      }
    }
    val styleParams =
      InputTextStyleParams(
        textAttributes = textAttributes,
        defaultColor = Color.BLACK,
        fontStyle = ReactConstants.UNSET,
        fontWeight = ReactConstants.UNSET,
        fontFamily = null,
      )

    val hint = props?.getString("placeholder")
    val displayDensity = context.resources.displayMetrics.density
    val formatter = InputFormatter(displayDensity)
    formatter.bodyTextAttributes = copyTextAttributes(styleParams.textAttributes)
    props?.getMap("markdownStyle")?.let { markdownStyleMap ->
      formatter.updateStyle(MarkdownStyleParser.parse(markdownStyleMap))
    }

    val paint =
      TextPaint().apply {
        textSize = styleParams.textAttributes.effectiveFontSize.toFloat()
        isAntiAlias = true
      }

    return measure(
      context = context,
      maxWidth = width,
      text = parseResult.plainText,
      hint = hint,
      styleParams = styleParams,
      paint = paint,
      blockRanges = parseResult.blockRanges,
      formatter = formatter,
    )
  }

  private fun measure(
    context: Context,
    maxWidth: Float,
    text: CharSequence?,
    hint: CharSequence?,
    styleParams: InputTextStyleParams,
    paint: TextPaint,
    blockRanges: List<BlockRange>,
    formatter: InputFormatter,
  ): Long {
    val spannable =
      buildMeasureSpannable(
        text = text,
        hint = hint,
        styleParams = styleParams,
        assets = context.assets,
        blockRanges = blockRanges,
        formatter = formatter,
      )

    val widthPx = maxWidth.toInt().coerceAtLeast(0)

    val builder =
      StaticLayout.Builder
        .obtain(spannable, 0, spannable.length, paint, widthPx)
        .setIncludePad(true)
        // Line height comes from InputCssLineHeightSpan / InputHeadingSpan, not
        // EditText extra spacing.
        .setLineSpacing(0f, 1f)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      builder.setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      builder.setUseLineSpacingFromFallbacks(true)
    }

    val staticLayout = builder.build()
    val heightInDip = PixelUtil.toDIPFromPixel(staticLayout.height.toFloat())
    val widthInDip = PixelUtil.toDIPFromPixel(maxWidth)
    return YogaMeasureOutput.make(widthInDip, heightInDip)
  }
}
