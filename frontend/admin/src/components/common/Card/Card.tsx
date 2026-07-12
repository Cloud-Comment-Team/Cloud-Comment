import type { HTMLAttributes } from 'react'

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  muted?: boolean
  density?: 'compact' | 'default'
}

export default function Card({ muted = false, density = 'default', className = '', ...props }: CardProps) {
  const padding = density === 'compact' ? 'p-4' : 'p-5 md:p-6'
  return <div className={`${muted ? 'cc-card-muted' : 'cc-card'} ${padding} ${className}`} {...props} />
}
