import React from "react";
import { Button } from "./ui/button";
import { AlertTriangle } from "lucide-react";

interface ErrorBoundaryState {
  hasError: boolean;
  error?: Error;
}

export class ErrorBoundary extends React.Component<
  React.PropsWithChildren<{}>,
  ErrorBoundaryState
> {
  constructor(props: React.PropsWithChildren<{}>) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    console.error("ErrorBoundary caught:", error, info);
  }

  handleReset = () => {
    this.setState({ hasError: false, error: undefined });
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className="flex flex-col items-center justify-center min-h-[50vh] gap-4 p-8">
          <AlertTriangle className="h-12 w-12 text-destructive" />
          <h2 className="text-xl font-semibold">Bir şeyler ters gitti</h2>
          <p className="text-muted-foreground text-center max-w-md">
            Beklenmeyen bir hata oluştu. Lütfen sayfayı yenilemeyi veya tekrar denemeyi deneyin.
          </p>
          {this.state.error && (
            <pre className="text-xs text-muted-foreground bg-muted p-3 rounded-md max-w-lg overflow-auto">
              {this.state.error.message}
            </pre>
          )}
          <div className="flex gap-2">
            <Button onClick={this.handleReset}>Tekrar Dene</Button>
            <Button variant="outline" onClick={() => window.location.reload()}>
              Sayfayı Yenile
            </Button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
