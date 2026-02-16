import { useState, useEffect } from "react";
import { Lock, LogIn, Loader2, AlertCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAuth } from "@/hooks/use-auth";

interface LoginDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function LoginDialog({ open, onOpenChange }: LoginDialogProps) {
  const { login, loading } = useAuth();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);

  // Dialog açıldığında form state'ini sıfırla
  useEffect(() => {
    if (open) {
      setUsername("");
      setPassword("");
      setError(null);
    }
  }, [open]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!username.trim() || !password.trim()) {
      setError("Kullanıcı adı ve parola gereklidir.");
      return;
    }

    const success = await login(username.trim(), password);
    if (success) {
      setUsername("");
      setPassword("");
      setError(null);
      onOpenChange(false);
    } else {
      setError("Geçersiz kullanıcı adı veya parola.");
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[420px]">
        <DialogHeader className="items-center text-center pb-2">
          <div className="relative mb-2">
            <div className="absolute inset-0 rounded-full bg-primary/15 blur-xl scale-150" />
            <div className="relative flex h-14 w-14 items-center justify-center rounded-xl bg-gradient-to-br from-primary/20 to-primary/5 border border-primary/10">
              <Lock className="h-6 w-6 text-primary" />
            </div>
          </div>
          <DialogTitle className="text-lg font-bold">Admin Girişi</DialogTitle>
          <DialogDescription className="text-center max-w-[280px]">
            Profiller ve Yönetim bölümleri için giriş yapın.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4 pt-2">
          {error && (
            <div role="alert" className="flex items-center gap-2.5 rounded-lg border border-destructive/20 bg-destructive/5 px-4 py-3 text-sm text-destructive">
              <AlertCircle className="h-4 w-4 shrink-0" />
              {error}
            </div>
          )}

          <div className="space-y-1.5">
            <Label
              htmlFor="username"
              className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground"
            >
              Kullanıcı Adı
            </Label>
            <Input
              id="username"
              type="text"
              placeholder="admin"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username"
              autoFocus
              disabled={loading}
              className="h-11 rounded-lg"
            />
          </div>

          <div className="space-y-1.5">
            <Label
              htmlFor="password"
              className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground"
            >
              Parola
            </Label>
            <Input
              id="password"
              type="password"
              placeholder="********"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              disabled={loading}
              className="h-11 rounded-lg"
            />
          </div>

          <Button
            type="submit"
            className="w-full h-11 rounded-lg font-bold shadow-lg shadow-primary/20"
            disabled={loading}
          >
            {loading ? (
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            ) : (
              <LogIn className="mr-2 h-4 w-4" />
            )}
            Giriş Yap
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}
