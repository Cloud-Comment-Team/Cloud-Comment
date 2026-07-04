import { forwardRef, useImperativeHandle, useRef, type ReactNode } from 'react'
import { AnimatePresence, motion } from 'framer-motion'

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
  if (reducedMotion || !intent || !bounds) {
    return null
  }

  const targetHeight = Math.max(220, Math.min(bounds.height, (bounds.viewportHeight ?? bounds.height + bounds.top) - bounds.top - 24))

  return (
    <AnimatePresence initial={false}>
      <motion.div
        key={`route-flow-${locationKey}`}
        aria-hidden="true"
        className="cc-route-flow pointer-events-none fixed z-40"
        initial={{
          opacity: 0.76,
          x: intent.rect.left,
          y: intent.rect.top,
          width: intent.rect.width,
          height: intent.rect.height,
          borderRadius: 12,
        }}
        animate={{
          opacity: [0.76, 0.48, 0],
          x: bounds.left,
          y: bounds.top,
          width: bounds.width,
          height: targetHeight,
          borderRadius: 18,
        }}
        exit={{ opacity: 0 }}
        transition={{
          duration: 0.66,
          ease: [0.16, 1, 0.3, 1],
          times: [0, 0.58, 1],
        }}
      >
        <motion.span
          className="absolute inset-y-0 left-0 w-24 rounded-full opacity-70 blur-2xl"
          style={{ background: 'var(--accent)' }}
          initial={{ x: -40, opacity: 0.44 }}
          animate={{ x: bounds.width * 0.7, opacity: 0 }}
          transition={{ duration: 0.58, ease: [0.16, 1, 0.3, 1] }}
        />
      </motion.div>
    </AnimatePresence>
  )
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

  const xOffset = direction === 0 ? 0 : direction * 30
  const yOffset = direction === 0 ? 12 : 0

  return (
    <motion.div
      key={locationKey}
      ref={localRef}
      className="min-w-0 [grid-area:1/1]"
      initial={{
        opacity: 0,
        x: xOffset,
        y: yOffset,
        scale: 0.988,
        filter: 'blur(8px)',
      }}
      animate={{
        opacity: 1,
        x: 0,
        y: 0,
        scale: 1,
        filter: 'blur(0px)',
      }}
      exit={{
        opacity: 0,
        x: direction === 0 ? 0 : -direction * 18,
        y: direction === 0 ? -8 : 0,
        scale: 0.992,
        filter: 'blur(5px)',
      }}
      transition={{
        opacity: { duration: 0.24 },
        x: { duration: 0.5, ease: [0.16, 1, 0.3, 1] },
        y: { duration: 0.5, ease: [0.16, 1, 0.3, 1] },
        scale: { duration: 0.5, ease: [0.16, 1, 0.3, 1] },
        filter: { duration: 0.42, ease: [0.22, 1, 0.36, 1] },
      }}
    >
      {children}
    </motion.div>
  )
})
