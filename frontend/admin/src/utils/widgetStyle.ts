import type { WidgetStyle } from '../types/api'

export const DEFAULT_WIDGET_STYLE: WidgetStyle = {
  version: 2,
  theme: 'AUTO',
  accentColor: '#0f766e',
  cornerRadius: 'MEDIUM',
  density: 'COMFORTABLE',
  contentWidth: 'READABLE',
  alignment: 'CENTER',
  fontScale: 'MEDIUM',
  fontFamily: 'INHERIT',
  showHeader: true,
  headerTitle: 'Комментарии',
  composerPosition: 'BOTTOM',
  defaultSort: 'PINNED_FIRST',
  showSort: true,
  enabledReactions: ['LIKE', 'LOVE', 'LAUGH', 'WOW'],
  avatarStyle: 'INITIALS',
  elevation: 'BORDER',
  locale: 'RU',
  commentsTitle: 'Комментарии',
  composerPlaceholder: 'Напишите комментарий',
  emptyMessage: 'Пока нет комментариев. Будьте первым, кто начнет обсуждение.',
}

function relativeLuminance(color: string): number {
  const channels = [1, 3, 5].map((index) => Number.parseInt(color.slice(index, index + 2), 16) / 255)
  const [red, green, blue] = channels.map((value) => value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4)
  return 0.2126 * red + 0.7152 * green + 0.0722 * blue
}

function contrastRatio(first: string, second: string): number {
  const values = [relativeLuminance(first), relativeLuminance(second)].sort((left, right) => right - left)
  return (values[0] + 0.05) / (values[1] + 0.05)
}

export function isWidgetAccentAccessible(color: string): boolean {
  return /^#[0-9a-fA-F]{6}$/.test(color)
    && contrastRatio(color, '#ffffff') >= 3
    && contrastRatio(color, '#141f31') >= 3
}
