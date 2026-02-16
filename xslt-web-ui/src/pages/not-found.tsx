import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { ArrowLeft } from "lucide-react";

export default function NotFoundPage() {
  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] gap-3">
      <span className="text-9xl font-black text-primary/20 leading-none select-none">
        404
      </span>
      <p className="text-xl font-bold -mt-4">Sayfa bulunamadı</p>
      <p className="text-sm text-muted-foreground max-w-[300px] text-center leading-relaxed">
        Aradığınız sayfa mevcut değil veya taşınmış olabilir.
      </p>
      <Button asChild className="mt-2 rounded-lg h-10 px-6 shadow-sm">
        <Link to="/validate">
          <ArrowLeft className="mr-2 h-4 w-4" />
          Ana sayfaya dön
        </Link>
      </Button>
    </div>
  );
}
