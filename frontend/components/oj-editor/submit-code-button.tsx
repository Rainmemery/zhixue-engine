"use client";

import { Send, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";

interface SubmitCodeButtonProps {
  onClick: () => void;
  disabled?: boolean;
  loading?: boolean;
}

export function SubmitCodeButton({ onClick, disabled = false, loading = false }: SubmitCodeButtonProps) {
  return (
    <Button
      size="sm"
      onClick={onClick}
      disabled={disabled || loading}
      className="h-8 text-xs gap-1.5 bg-blue-600 hover:bg-blue-700 text-white"
    >
      {loading ? (
        <Loader2 className="w-3.5 h-3.5 animate-spin" />
      ) : (
        <Send className="w-3.5 h-3.5" />
      )}
      提交代码
    </Button>
  );
}

export default SubmitCodeButton;
