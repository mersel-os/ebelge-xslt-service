import { lazy, Suspense } from "react";
import { Routes, Route, Navigate } from "react-router-dom";
import { AppLayout } from "@/components/layout/app-layout";
import { ProtectedRoute } from "@/components/auth/protected-route";
import ValidatePage from "@/pages/validate";
import TransformPage from "@/pages/transform";
import NotFoundPage from "@/pages/not-found";

// Lazy load — admin sayfaları giriş gerektirdiğinden
// ana bundle'a dahil etmeye gerek yok
const ProfilesPage = lazy(() => import("@/pages/profiles"));
const AdminPage = lazy(() => import("@/pages/admin"));

function PageFallback() {
  return (
    <div className="flex items-center justify-center min-h-[300px]">
      <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
    </div>
  );
}

export default function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<Navigate to="/validate" replace />} />
        <Route path="/validate" element={<ValidatePage />} />
        <Route path="/transform" element={<TransformPage />} />
        <Route
          path="/profiles"
          element={
            <ProtectedRoute>
              <Suspense fallback={<PageFallback />}>
                <ProfilesPage />
              </Suspense>
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin"
          element={
            <ProtectedRoute>
              <Suspense fallback={<PageFallback />}>
                <AdminPage />
              </Suspense>
            </ProtectedRoute>
          }
        />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
