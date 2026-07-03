type BrandImageProps = {
  className?: string
}

export function BrandMark({ className = '' }: BrandImageProps) {
  return (
    <img
      alt=""
      aria-hidden="true"
      className={`block rounded-lg object-cover ${className}`}
      draggable={false}
      src="/brand/cloudcomment-mark.png"
    />
  )
}

export function BrandLogo({ className = '' }: BrandImageProps) {
  return (
    <img
      alt="CloudComment"
      className={`block object-contain ${className}`}
      draggable={false}
      src="/brand/cloudcomment-logo.jpg"
    />
  )
}
