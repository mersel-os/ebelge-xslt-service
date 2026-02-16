import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { QueryClientProvider } from "@tanstack/react-query";
import { TooltipProvider } from "@/components/ui/tooltip";
import { Toaster } from "@/components/ui/sonner";
import { ErrorBoundary } from "@/components/error-boundary";
import { AuthProvider } from "@/hooks/use-auth";
import { queryClient } from "@/lib/query-client";
import App from "./App";
import "./index.css";

// Yakalanmamış Promise rejection'larını logla
window.addEventListener("unhandledrejection", (event) => {
  console.error("Unhandled promise rejection:", event.reason);
});

const rootElement = document.getElementById("root");
if (!rootElement) {
  throw new Error("Root element (#root) bulunamadı. index.html dosyasını kontrol edin.");
}

createRoot(rootElement).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <ErrorBoundary>
          <AuthProvider>
            <TooltipProvider>
              <App />
              <Toaster richColors position="bottom-right" />
            </TooltipProvider>
          </AuthProvider>
        </ErrorBoundary>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>
);
