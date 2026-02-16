import { useState } from "react";
import { Lock, LogIn } from "lucide-react";
import { Button } from "@/components/ui/button";
import { LoginDialog } from "./login-dialog";
import { useAuth } from "@/hooks/use-auth";

interface ProtectedRouteProps {
  children: React.ReactNode;
}

export function ProtectedRoute({ children }: ProtectedRouteProps) {
  const { authenticated } = useAuth();
  const [loginOpen, setLoginOpen] = useState(false);

  if (authenticated) {
    return <>{children}</>;
  }

  return (
    <>
      <div className="flex items-center justify-center min-h-[350px] rounded-xl border border-dashed">
        <div className="flex flex-col items-center gap-5 text-center px-6">
          <div className="relative">
            <div className="absolute inset-0 rounded-full bg-primary/10 blur-2xl scale-150" />
            <div className="relative flex h-16 w-16 items-center justify-center rounded-xl bg-gradient-to-br from-primary/15 to-primary/5 border border-primary/10">
              <Lock className="h-7 w-7 text-primary/60" />
            </div>
          </div>
          <div>
            <h3 className="text-lg font-bold">Giriş Gerekli</h3>
            <p className="mt-1.5 text-sm text-muted-foreground max-w-sm leading-relaxed">
              Bu bölüme erişebilmek için admin olarak giriş yapmanız
              gerekmektedir.
            </p>
          </div>
          <Button
            onClick={() => setLoginOpen(true)}
            className="rounded-lg h-10 px-6 shadow-lg shadow-primary/20"
          >
            <LogIn className="mr-2 h-4 w-4" />
            Giriş Yap
          </Button>
        </div>
      </div>

      <LoginDialog open={loginOpen} onOpenChange={setLoginOpen} />
    </>
  );
}
