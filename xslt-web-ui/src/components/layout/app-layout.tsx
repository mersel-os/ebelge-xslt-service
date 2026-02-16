import { Outlet, useLocation } from "react-router-dom";
import { Navbar } from "./navbar";

const pageMeta: Record<string, { title: string; subtitle: string }> = {
  "/validate": {
    title: "XML Doğrulama",
    subtitle: "XSD şema ve Schematron kuralları ile doğrulayın",
  },
  "/transform": {
    title: "XSLT Dönüşüm",
    subtitle: "XML dosyalarınızı HTML önizlemeye dönüştürün",
  },
  "/profiles": {
    title: "Doğrulama Profilleri",
    subtitle: "Hata bastırma ve XSD override yapılandırmaları",
  },
  "/admin": {
    title: "Yönetim Paneli",
    subtitle: "Asset'ler ve GİB paket senkronizasyonu",
  },
};

export function AppLayout() {
  const { pathname } = useLocation();
  const meta = pageMeta[pathname];

  return (
    <div className="min-h-screen bg-background">
      <Navbar />

      <div className="mx-auto max-w-screen-xl px-5 sm:px-8">
        {/* ── Page Header ── */}
        {meta && (
          <div className="pt-8 pb-6 animate-fade-in">
            <h1 className="text-2xl font-bold tracking-tight">{meta.title}</h1>
            <p className="text-sm text-muted-foreground mt-1">
              {meta.subtitle}
            </p>
            {/* Decorative gradient line */}
            <div className="mt-4 h-px bg-gradient-to-r from-primary/40 via-primary/10 to-transparent" />
          </div>
        )}

        {/* ── Page Content ── */}
        <main className="pb-12 animate-slide-up">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
