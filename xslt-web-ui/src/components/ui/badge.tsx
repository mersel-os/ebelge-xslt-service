import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"
import { Slot } from "radix-ui"

import { cn } from "@/lib/utils"

const badgeVariants = cva(
  "inline-flex items-center justify-center rounded-md border border-transparent px-2 py-0.5 text-xs font-medium w-fit whitespace-nowrap shrink-0 [&>svg]:size-3 gap-1 [&>svg]:pointer-events-none transition-colors overflow-hidden",
  {
    variants: {
      variant: {
        default: "bg-foreground/10 text-foreground [a&]:hover:bg-foreground/15",
        secondary: "bg-white/8 text-secondary-foreground [a&]:hover:bg-white/12",
        destructive: "bg-destructive/15 text-destructive [a&]:hover:bg-destructive/25",
        outline: "border-white/10 bg-transparent text-muted-foreground [a&]:hover:bg-white/8",
        ghost: "bg-transparent text-muted-foreground [a&]:hover:bg-white/8",
        link: "text-foreground underline-offset-4 [a&]:hover:underline",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  }
)

function Badge({
  className,
  variant = "default",
  asChild = false,
  ...props
}: React.ComponentProps<"span"> &
  VariantProps<typeof badgeVariants> & { asChild?: boolean }) {
  const Comp = asChild ? Slot.Root : "span"

  return (
    <Comp
      data-slot="badge"
      data-variant={variant}
      className={cn(badgeVariants({ variant }), className)}
      {...props}
    />
  )
}

export { Badge, badgeVariants }
