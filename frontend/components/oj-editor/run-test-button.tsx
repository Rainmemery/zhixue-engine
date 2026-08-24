"use client";

import { Play, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";

interface RunTestButtonProps {
  onClick: () => void;
  disabled?: boolean;
  loading?: boolean;
}

export function RunTestButton({ onClick, disabled = false, loading = false }: RunTestButtonProps) {
  return (
    <Button
      size="sm"
      onClick={onClick}
      disabled={disabled || loading}
      className="h-8 text-xs gap-1.5 bg-emerald-600 hover:bg-emerald-700 text-white"
    >
      {loading ? (
        <Loader2 className="w-3.5 h-3.5 animate-spin" />
      ) : (
        <Play className="w-3.5 h-3.5" />
      )}
      运行测试
    </Button>
  );
}

export default RunTestButton;
