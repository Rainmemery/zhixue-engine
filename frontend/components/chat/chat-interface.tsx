"use client";

import { useState, useRef, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Send, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { MessageBubble } from "./message-bubble";
import { cn } from "@/lib/utils";

export interface ChatMessage {
  id: string;
  role: "user" | "assistant";
  content: string;
  timestamp: string;
}

interface ChatInterfaceProps {
  messages: ChatMessage[];
  onSend: (message: string) => void;
  loading?: boolean;
  streaming?: boolean;
  placeholder?: string;
  disabled?: boolean;
  className?: string;
}

export function ChatInterface({
  messages,
  onSend,
  loading,
  streaming,
  placeholder = "输入消息...",
  disabled,
  className,
}: ChatInterfaceProps) {
  const [input, setInput] = useState("");
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  // Auto-scroll to bottom
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  // Auto-resize textarea
  useEffect(() => {
    if (inputRef.current) {
      inputRef.current.style.height = "auto";
      inputRef.current.style.height = `${Math.min(inputRef.current.scrollHeight, 120)}px`;
    }
  }, [input]);

  const handleSend = () => {
    if (!input.trim() || disabled || loading) return;
    onSend(input.trim());
    setInput("");
    if (inputRef.current) {
      inputRef.current.style.height = "auto";
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className={cn("flex flex-col h-full", className)}>
      {/* Messages Area */}
      <div className="flex-1 overflow-y-auto p-4 space-y-2">
        <AnimatePresence mode="popLayout">
          {messages.map((message, index) => (
            <MessageBubble
              key={message.id || index}
              role={message.role}
              content={message.content}
              timestamp={message.timestamp}
              isStreaming={streaming && index === messages.length - 1 && message.role === "assistant"}
            />
          ))}
        </AnimatePresence>
        <div ref={messagesEndRef} />
      </div>

      {/* Input Area */}
      <div className="p-4 border-t border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900">
        <div className="flex gap-2 items-end">
          <div className="flex-1 relative">
            <textarea
              ref={inputRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder={placeholder}
              disabled={disabled || loading}
              rows={1}
              className={cn(
                "w-full px-4 py-3 pr-12 rounded-xl resize-none",
                "bg-gray-100 dark:bg-gray-800",
                "border-0 focus:ring-2 focus:ring-indigo-500",
                "text-sm text-gray-900 dark:text-gray-100",
                "placeholder:text-gray-400",
                "transition-all duration-200",
                (disabled || loading) && "opacity-50 cursor-not-allowed"
              )}
            />
            <span className="absolute right-3 bottom-3 text-xs text-gray-400">
              {input.length}/2000
            </span>
          </div>
          <Button
            onClick={handleSend}
            disabled={!input.trim() || disabled || loading}
            className={cn(
              "h-11 px-4 rounded-xl",
              "bg-gradient-to-r from-indigo-600 to-purple-600",
              "hover:from-indigo-700 hover:to-purple-700",
              "text-white font-medium",
              "transition-all duration-200",
              "disabled:opacity-50 disabled:cursor-not-allowed"
            )}
          >
            {loading ? (
              <Loader2 className="w-4 h-4 animate-spin" />
            ) : (
              <Send className="w-4 h-4" />
            )}
          </Button>
        </div>
      </div>
    </div>
  );
}
