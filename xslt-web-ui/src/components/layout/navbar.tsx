import { useState } from "react";
import { NavLink, useLocation } from "react-router-dom";
import {
  ShieldCheck,
  FileCode2,
  UserCog,
  Settings,
  Moon,
  Sun,
  Monitor,
  Github,
  Braces,
  LogIn,
  LogOut,
  User,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useTheme } from "@/hooks/use-theme";
import { useAuth } from "@/hooks/use-auth";
import { LoginDialog } from "@/components/auth/login-dialog";

interface NavItem {
  title: string;
  href: string;
  icon: React.ComponentType<{ className?: string }>;
}

const navItems: NavItem[] = [
  { title: "Doğrulama", href: "/validate", icon: ShieldCheck },
  { title: "Dönüşüm", href: "/transform", icon: FileCode2 },
  { title: "Profiller", href: "/profiles", icon: UserCog },
  { title: "Yönetim", href: "/admin", icon: Settings },
];

export function Navbar() {
  const { setTheme, resolvedTheme } = useTheme();
  const { authenticated, username, logout } = useAuth();
  const [loginOpen, setLoginOpen] = useState(false);
  const location = useLocation();

  return (
    <>
      <header className="sticky top-0 z-50 w-full">
        {/* Gradient top accent line */}
        <div className="h-[2px] bg-gradient-to-r from-transparent via-primary to-transparent" />

        <div className="border-b bg-card/80 backdrop-blur-2xl">
          <div className="mx-auto flex h-16 max-w-screen-xl items-center px-5 sm:px-8">
            {/* ── Brand ── */}
            <NavLink
              to="/validate"
              className="shrink-0 text-sm font-bold tracking-tight"
            >
              XSLT Service
            </NavLink>

            {/* ── Spacer (left) ── */}
            <div className="flex-1" />

            {/* ── Navigation (centered) ── */}
            <nav aria-label="Ana navigasyon" className="flex items-center gap-1 bg-muted/60 rounded-lg p-1">
              {navItems.map((item) => {
                const isActive = location.pathname === item.href;
                return (
                  <NavLink
                    key={item.href}
                    to={item.href}
                    aria-label={item.title}
                    className={cn(
                      "flex items-center gap-2 rounded-lg px-4 py-2 text-[13px] font-medium transition-all duration-200",
                      isActive
                        ? "bg-card text-foreground shadow-sm"
                        : "text-muted-foreground hover:text-foreground"
                    )}
                  >
                    <item.icon className="h-4 w-4" />
                    <span className="hidden sm:inline">{item.title}</span>
                  </NavLink>
                );
              })}
            </nav>

            {/* ── Spacer (right) ── */}
            <div className="flex-1" />

            {/* ── Actions ── */}
            <div className="flex items-center gap-1.5">
              <Button
                variant="ghost"
                size="icon"
                className="h-9 w-9 rounded-lg text-muted-foreground hover:text-foreground"
                asChild
              >
                <a
                  href="/scalar.html"
                  target="_blank"
                  rel="noopener noreferrer"
                  title="API Docs"
                  aria-label="API dokümantasyonu"
                >
                  <Braces className="h-4 w-4" />
                </a>
              </Button>

              <Button
                variant="ghost"
                size="icon"
                className="h-9 w-9 rounded-lg text-muted-foreground hover:text-foreground"
                asChild
              >
                <a
                  href="https://github.com/mersel-os/ebelge-xslt-service"
                  target="_blank"
                  rel="noopener noreferrer"
                  title="GitHub"
                  aria-label="GitHub deposu"
                >
                  <Github className="h-4 w-4" />
                </a>
              </Button>

              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-9 w-9 rounded-lg text-muted-foreground hover:text-foreground"
                    aria-label="Tema değiştir"
                  >
                    {resolvedTheme === "dark" ? (
                      <Moon className="h-4 w-4" />
                    ) : (
                      <Sun className="h-4 w-4" />
                    )}
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" className="w-36">
                  <DropdownMenuItem onClick={() => setTheme("light")}>
                    <Sun className="mr-2 h-4 w-4" /> Light
                  </DropdownMenuItem>
                  <DropdownMenuItem onClick={() => setTheme("dark")}>
                    <Moon className="mr-2 h-4 w-4" /> Dark
                  </DropdownMenuItem>
                  <DropdownMenuItem onClick={() => setTheme("system")}>
                    <Monitor className="mr-2 h-4 w-4" /> System
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>

              {/* ── Auth ── */}
              {authenticated ? (
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button
                      variant="ghost"
                      size="sm"
                      className="h-9 gap-2 rounded-lg pl-2 pr-3"
                    >
                      <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-gradient-to-br from-primary/20 to-primary/5 text-primary">
                        <User className="h-3.5 w-3.5" />
                      </div>
                      <span className="text-xs font-medium hidden sm:inline">
                        {username}
                      </span>
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end" className="w-44">
                    <div className="px-2 py-1.5 text-xs text-muted-foreground">
                      {username} olarak giriş yapıldı
                    </div>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem
                      onClick={logout}
                      className="text-destructive focus:text-destructive"
                    >
                      <LogOut className="mr-2 h-4 w-4" />
                      Çıkış Yap
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              ) : (
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-9 w-9 rounded-lg text-muted-foreground hover:text-foreground"
                  onClick={() => setLoginOpen(true)}
                  aria-label="Giriş yap"
                >
                  <LogIn className="h-4 w-4" />
                </Button>
              )}
            </div>
          </div>
        </div>
      </header>

      <LoginDialog open={loginOpen} onOpenChange={setLoginOpen} />
    </>
  );
}
