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
    <div className="min-h-screen">
      <Navbar />

      <main className="mx-auto max-w-5xl px-5 py-10">
        {meta && (
          <div className="mb-10 animate-slide-up">
            <h1 className="text-2xl font-bold tracking-tight">{meta.title}</h1>
            <p className="mt-1.5 text-sm text-muted-foreground">{meta.subtitle}</p>
          </div>
        )}

        <div className="animate-fade-in">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
