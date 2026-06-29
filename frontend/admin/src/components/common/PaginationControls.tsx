interface PaginationControlsProps {
  page: number
  totalPages: number
  totalItems: number
  onPageChange: (page: number) => void
}

export function PaginationControls({ page, totalPages, totalItems, onPageChange }: PaginationControlsProps) {
  if (totalPages <= 1) {
    return (
      <p className="text-left text-sm" style={{ color: 'var(--text)' }}>
        Всего: {totalItems}
      </p>
    )
  }

  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <p className="text-sm" style={{ color: 'var(--text)' }}>
        Страница {page} из {totalPages} · всего {totalItems}
      </p>
      <div className="flex items-center gap-2">
        <button
          type="button"
          className="rounded-lg border px-3 py-2 text-sm font-medium transition hover:opacity-80 disabled:opacity-50"
          disabled={page <= 1}
          onClick={() => onPageChange(page - 1)}
          style={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)', color: 'var(--text-h)' }}
        >
          Назад
        </button>
        <button
          type="button"
          className="rounded-lg border px-3 py-2 text-sm font-medium transition hover:opacity-80 disabled:opacity-50"
          disabled={page >= totalPages}
          onClick={() => onPageChange(page + 1)}
          style={{ backgroundColor: 'var(--surface)', borderColor: 'var(--border)', color: 'var(--text-h)' }}
        >
          Вперёд
        </button>
      </div>
    </div>
  )
}
