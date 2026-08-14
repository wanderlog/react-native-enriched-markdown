import { View, Text, StyleSheet, Platform } from 'react-native';
import type { StyleState } from 'react-native-enriched-markdown';

export interface StyleStateDebugPanelProps {
  state: StyleState | null;
}

/** Playground readout for StyleState fields that lack other UI affordances. */
export function StyleStateDebugPanel({ state }: StyleStateDebugPanelProps) {
  const linkDestination =
    state?.link.isActive === true ? state.link.destination : null;

  return (
    <View style={styles.panel} testID="style-state-debug-panel">
      <Text style={styles.label}>Style state</Text>
      <Text style={styles.row} testID="style-state-link-active">
        Link active: {state?.link.isActive === true ? 'yes' : 'no'}
      </Text>
      <Text style={styles.row} testID="style-state-link-destination">
        Link destination: {linkDestination ?? '(none)'}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  panel: {
    borderRadius: 8,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: '#D1D5DB',
    backgroundColor: '#FFFFFF',
    padding: 10,
    gap: 4,
  },
  label: {
    fontSize: 11,
    fontWeight: '600',
    color: '#9CA3AF',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  row: {
    fontSize: 13,
    color: '#374151',
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
  },
});
