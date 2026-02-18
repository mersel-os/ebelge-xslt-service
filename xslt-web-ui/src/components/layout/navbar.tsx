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
  Menu,
  X,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useTheme } from "@/hooks/use-theme";
import { useAuth } from "@/hooks/use-auth";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { LoginDialog } from "@/components/auth/login-dialog";

const navItems = [
  { title: "Doğrulama", href: "/validate", icon: ShieldCheck },
  { title: "Dönüşüm", href: "/transform", icon: FileCode2 },
  { title: "Profiller", href: "/profiles", icon: UserCog },
  { title: "Yönetim", href: "/admin", icon: Settings },
];

export function Navbar() {
  const { setTheme, resolvedTheme } = useTheme();
  const { authenticated, username, logout } = useAuth();
  const [loginOpen, setLoginOpen] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const location = useLocation();

  return (
    <>
      <header className="sticky top-0 z-50 border-b border-border">
        <div className="mx-auto flex h-14 max-w-5xl items-center px-5">
          {/* Logo */}
          <NavLink to="/validate" className="mr-8 flex items-center gap-2.5">
            <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-foreground">
              <FileCode2 className="h-3.5 w-3.5 text-background" />
            </div>
            <span className="hidden text-[15px] font-semibold tracking-tight sm:block">XSLT Service</span>
          </NavLink>

          {/* Desktop nav */}
          <nav className="hidden items-center gap-0.5 md:flex">
            {navItems.map((item) => {
              const active = location.pathname === item.href;
              return (
                <NavLink
                  key={item.href}
                  to={item.href}
                  className={cn(
                    "relative flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-[13px] font-medium transition-colors duration-200",
                    active
                      ? "text-foreground"
                      : "text-muted-foreground hover:text-foreground"
                  )}
                >
                  <item.icon className="h-3.5 w-3.5" />
                  {item.title}
                  {active && (
                    <span className="absolute inset-x-2 -bottom-[1.07rem] h-px bg-gradient-to-r from-transparent via-foreground/40 to-transparent" />
                  )}
                </NavLink>
              );
            })}
          </nav>

          <div className="flex-1" />

          {/* Right side */}
          <div className="flex items-center gap-0.5">
            <a
              href="/scalar.html"
              target="_blank"
              rel="noopener noreferrer"
              className="hidden h-8 w-8 items-center justify-center rounded-lg text-muted-foreground transition-colors hover:text-foreground/70 sm:flex"
              title="API Docs"
            >
              <Braces className="h-4 w-4" />
            </a>
            <a
              href="https://github.com/mersel-os/ebelge-xslt-service"
              target="_blank"
              rel="noopener noreferrer"
              className="hidden h-8 w-8 items-center justify-center rounded-lg text-muted-foreground transition-colors hover:text-foreground/70 sm:flex"
              title="GitHub"
            >
              <Github className="h-4 w-4" />
            </a>

            <div className="mx-2 hidden h-4 w-px bg-border sm:block" />

            {/* Theme */}
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon-sm" className="rounded-lg text-muted-foreground hover:text-foreground/70">
                  {resolvedTheme === "dark" ? <Moon className="h-4 w-4" /> : <Sun className="h-4 w-4" />}
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-32">
                <DropdownMenuItem onClick={() => setTheme("light")}><Sun className="mr-2 h-3.5 w-3.5" /> Açık</DropdownMenuItem>
                <DropdownMenuItem onClick={() => setTheme("dark")}><Moon className="mr-2 h-3.5 w-3.5" /> Koyu</DropdownMenuItem>
                <DropdownMenuItem onClick={() => setTheme("system")}><Monitor className="mr-2 h-3.5 w-3.5" /> Sistem</DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>

            {/* Auth */}
            {authenticated ? (
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="ghost" size="sm" className="gap-2 rounded-lg px-2 text-muted-foreground hover:text-foreground">
                    <div className="flex h-6 w-6 items-center justify-center rounded-full bg-muted border border-border">
                      <User className="h-3 w-3 text-foreground/80" />
                    </div>
                    <span className="hidden text-xs font-medium sm:inline">{username}</span>
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" className="w-44">
                  <div className="px-2 py-1.5 text-xs text-muted-foreground">
                    <span className="font-medium text-foreground">{username}</span>
                  </div>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem onClick={logout} className="text-destructive focus:text-destructive">
                    <LogOut className="mr-2 h-3.5 w-3.5" /> Çıkış Yap
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            ) : (
              <Button variant="ghost" size="sm" className="gap-1.5 rounded-lg text-muted-foreground hover:text-foreground" onClick={() => setLoginOpen(true)}>
                <LogIn className="h-3.5 w-3.5" />
                <span className="hidden text-xs sm:inline">Giriş</span>
              </Button>
            )}

            <Button variant="ghost" size="icon-sm" className="rounded-lg text-muted-foreground md:hidden" onClick={() => setMobileOpen(!mobileOpen)}>
              {mobileOpen ? <X className="h-4 w-4" /> : <Menu className="h-4 w-4" />}
            </Button>
          </div>
        </div>

        {/* Mobile nav */}
        {mobileOpen && (
          <div className="border-t border-border bg-(--overlay-bg) backdrop-blur-2xl md:hidden animate-fade-in">
            <nav className="flex flex-col gap-1 p-3">
              {navItems.map((item) => {
                const active = location.pathname === item.href;
                return (
                  <NavLink
                    key={item.href}
                    to={item.href}
                    onClick={() => setMobileOpen(false)}
                    className={cn(
                      "flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-medium",
                      active ? "bg-accent text-foreground" : "text-muted-foreground hover:text-foreground"
                    )}
                  >
                    <item.icon className="h-4 w-4" />
                    {item.title}
                  </NavLink>
                );
              })}
            </nav>
          </div>
        )}
      </header>

      <LoginDialog open={loginOpen} onOpenChange={setLoginOpen} />
    </>
  );
}
