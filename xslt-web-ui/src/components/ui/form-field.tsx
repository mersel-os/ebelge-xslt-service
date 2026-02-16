import { cn } from "@/lib/utils";
import { Label } from "./label";

interface FormFieldProps {
  label: string;
  description?: string;
  required?: boolean;
  children: React.ReactNode;
  className?: string;
  htmlFor?: string;
}

export function FormField({ label, description, required, children, className, htmlFor }: FormFieldProps) {
  return (
    <div className={cn("space-y-1.5", className)}>
      <div>
        <Label htmlFor={htmlFor} className="text-sm font-medium">
          {label}
          {required && <span className="text-destructive ml-0.5">*</span>}
        </Label>
        {description && (
          <p className="text-xs text-muted-foreground mt-0.5">{description}</p>
        )}
      </div>
      {children}
    </div>
  );
}
