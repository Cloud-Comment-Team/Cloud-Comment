import { describe, expect, it } from 'vitest'

import { DEFAULT_WIDGET_STYLE, isWidgetAccentAccessible } from './widgetStyle'

describe('widgetStyle', () => {
  it('сохраняет совместимые значения V2 по умолчанию', () => {
    expect(DEFAULT_WIDGET_STYLE.version).toBe(2)
    expect(DEFAULT_WIDGET_STYLE.defaultSort).toBe('PINNED_FIRST')
    expect(DEFAULT_WIDGET_STYLE.enabledReactions).toEqual(['LIKE', 'LOVE', 'LAUGH', 'WOW'])
  })

  it('разрешает акцент только с контрастом на обеих темах', () => {
    expect(isWidgetAccentAccessible('#0f766e')).toBe(true)
    expect(isWidgetAccentAccessible('#5b7f79')).toBe(false)
    expect(isWidgetAccentAccessible('#ffffff')).toBe(false)
    expect(isWidgetAccentAccessible('#141f31')).toBe(false)
    expect(isWidgetAccentAccessible('red')).toBe(false)
  })
})
