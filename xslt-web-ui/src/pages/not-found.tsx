import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { ArrowLeft } from "lucide-react";

export default function NotFoundPage() {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-5">
      <span className="select-none text-9xl font-black tracking-tighter text-foreground/20">404</span>
      <p className="-mt-3 text-lg font-semibold text-foreground/80">Sayfa bulunamadı</p>
      <p className="max-w-[300px] text-center text-sm text-muted-foreground/80">
        Aradığınız sayfa mevcut değil veya taşınmış olabilir.
      </p>
      <Button asChild size="sm" className="mt-2">
        <Link to="/validate">
          <ArrowLeft className="mr-2 h-4 w-4" />
          Ana sayfaya dön
        </Link>
      </Button>
    </div>
  );
}
