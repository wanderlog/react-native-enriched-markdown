package com.swmansion.enriched.markdown.input.layout

import com.facebook.react.bridge.Arguments
import com.swmansion.enriched.markdown.input.EnrichedMarkdownTextInputView

class InputLayoutManager(
  private val view: EnrichedMarkdownTextInputView,
) {
  private var forceHeightRecalculationCounter = 0

  fun invalidateLayout() {
    if (view.stateWrapper == null) return

    val needUpdate =
      InputMeasurementStore.store(
        context = view.context,
        id = view.id,
        text = view.text,
        styleParams = view.styleParamsForMeasurement(),
        hint = view.hintForMeasurement(),
        paint = view.paint,
        blockRanges = view.blockStore.allRanges,
        formatter = view.formatter,
      )
    if (!needUpdate) return

    val state = Arguments.createMap()
    state.putInt("forceHeightRecalculationCounter", forceHeightRecalculationCounter++)
    view.stateWrapper?.updateState(state)
  }

  fun release() {
    InputMeasurementStore.release(view.id)
  }
}
