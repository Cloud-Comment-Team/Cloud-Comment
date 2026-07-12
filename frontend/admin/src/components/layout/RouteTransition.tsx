import { forwardRef, useImperativeHandle, useRef, type ReactNode } from 'react'
import { motion } from 'framer-motion'

import type { ElementBounds, NavigationIntent } from './routeTransitionModel'

type PageTransitionProps = {
  children: ReactNode
  locationKey: string
  direction: number
  reducedMotion: boolean
}

export function RouteFlowOverlay({
  intent,
  bounds,
  locationKey,
  reducedMotion,
}: {
  intent: NavigationIntent | null
  bounds: ElementBounds | null
  locationKey: string
  reducedMotion: boolean
}) {
  void intent
  void bounds
  void locationKey
  void reducedMotion
  return null
}

export const PageTransition = forwardRef<HTMLDivElement, PageTransitionProps>(function PageTransition(
  { children, locationKey, direction, reducedMotion },
  ref,
) {
  const localRef = useRef<HTMLDivElement>(null)
  useImperativeHandle(ref, () => localRef.current as HTMLDivElement)

  if (reducedMotion) {
    return (
      <div key={locationKey} ref={localRef}>
        {children}
      </div>
    )
  }

  const xOffset = direction === 0 ? 0 : direction * 8
  const yOffset = direction === 0 ? 8 : 0

  return (
    <motion.div
      key={locationKey}
      ref={localRef}
      className="min-w-0 [grid-area:1/1]"
      initial={{
        opacity: 0,
        x: xOffset,
        y: yOffset,
      }}
      animate={{
        opacity: 1,
        x: 0,
        y: 0,
      }}
      exit={{
        opacity: 0,
        x: direction === 0 ? 0 : -direction * 8,
        y: direction === 0 ? -4 : 0,
      }}
      transition={{
        duration: 0.18,
        ease: [0.2, 0, 0, 1],
      }}
    >
      {children}
    </motion.div>
  )
})
