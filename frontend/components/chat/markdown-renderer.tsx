"use client";

import React, { useState, useCallback } from "react";
import ReactMarkdown, { Components } from "react-markdown";
import remarkGfm from "remark-gfm";
import remarkMath from "remark-math";
import rehypeKatex from "rehype-katex";
import "katex/dist/katex.min.css";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { vscDarkPlus } from "react-syntax-highlighter/dist/esm/styles/prism";
import { cn } from "@/lib/utils";
import { preprocessStreamingMarkdown } from "@/lib/textProcessor";

interface MarkdownRendererProps {
  content: string;
  className?: string;
  style?: React.CSSProperties;
  isStreaming?: boolean;
}

interface CodeComponentProps {
  className?: string;
  children?: React.ReactNode;
}

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);

  const handleCopy = useCallback(() => {
    navigator.clipboard.writeText(text).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  }, [text]);

  return (
    <button
      onClick={handleCopy}
      className="absolute top-2 right-2 p-1.5 rounded-md bg-white/10 hover:bg-white/20 transition-colors text-gray-400 hover:text-gray-200"
      title={copied ? "已复制" : "复制代码"}
    >
      {copied ? (
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
        </svg>
      ) : (
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
        </svg>
      )}
    </button>
  );
}

export const MarkdownRenderer: React.FC<MarkdownRendererProps> = ({
  content,
  className = "",
  style,
  isStreaming = false,
}) => {
  if (!content) {
    return <div className={className} style={style}>暂无内容</div>;
  }

  const displayContent = isStreaming ? preprocessStreamingMarkdown(content) : content;

  return (
    <div className={`markdown-wrapper ${className}`} style={{ ...style, overflowWrap: "break-word", wordBreak: "break-word" }}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkMath]}
        rehypePlugins={[rehypeKatex]}
        components={{
          code({ className, children, ...props }: CodeComponentProps) {
            const match = /language-(\w+)/.exec(className || "");
            const inline = !/\n/.test(children?.toString() || "");
            if (!inline) {
              const codeString = String(children).replace(/\n$/, "");
              return (
                <div className="relative group">
                  <SyntaxHighlighter
                    style={vscDarkPlus}
                    language={match ? match[1] : "text"}
                    PreTag="div"
                    className="rounded-lg my-2 text-sm"
                    {...props}
                  >
                    {codeString}
                  </SyntaxHighlighter>
                  <CopyButton text={codeString} />
                </div>
              );
            }
            return (
              <code
                className="bg-gray-100 dark:bg-gray-800 px-1.5 py-0.5 rounded text-sm font-mono text-pink-600 dark:text-pink-400"
                {...props}
              >
                {children}
              </code>
            );
          },
          a({ ...props }) {
            return (
              <a
                {...props}
                target="_blank"
                rel="noopener noreferrer"
                className="text-blue-600 dark:text-blue-400 hover:underline"
              />
            );
          },
          table({ ...props }) {
            return (
              <div className="overflow-x-auto my-4">
                <table
                  className="min-w-full border-collapse border border-gray-300 dark:border-gray-700"
                  {...props}
                />
              </div>
            );
          },
          thead({ ...props }) {
            return (
              <thead
                className="bg-gray-100 dark:bg-gray-800"
                {...props}
              />
            );
          },
          tbody({ ...props }) {
            return <tbody {...props} />;
          },
          tr({ ...props }) {
            return (
              <tr
                className="border-b border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800/50"
                {...props}
              />
            );
          },
          td({ ...props }) {
            return (
              <td
                className="px-4 py-2 border border-gray-200 dark:border-gray-700"
                {...props}
              />
            );
          },
          th({ ...props }) {
            return (
              <th
                className="px-4 py-2 border border-gray-200 dark:border-gray-700 font-semibold text-left"
                {...props}
              />
            );
          },
          blockquote({ ...props }) {
            return (
              <blockquote
                className="border-l-4 border-blue-500 pl-4 py-1 my-3 bg-blue-50/50 dark:bg-blue-900/20 italic text-gray-700 dark:text-gray-300"
                {...props}
              />
            );
          },
          li({ ...props }) {
            return <li className="my-1 ml-4" {...props} />;
          },
          ol({ ...props }) {
            return (
              <ol
                className="list-decimal list-inside my-2 space-y-1"
                {...props}
              />
            );
          },
          ul({ ...props }) {
            return (
              <ul
                className="list-disc list-inside my-2 space-y-1"
                {...props}
              />
            );
          },
          h1({ ...props }) {
            return (
              <h1
                className="text-2xl font-bold my-4 text-gray-900 dark:text-gray-100 border-b pb-2"
                {...props}
              />
            );
          },
          h2({ ...props }) {
            return (
              <h2
                className="text-xl font-bold my-3 text-gray-900 dark:text-gray-100"
                {...props}
              />
            );
          },
          h3({ ...props }) {
            return (
              <h3
                className="text-lg font-semibold my-2 text-gray-900 dark:text-gray-100"
                {...props}
              />
            );
          },
          h4({ ...props }) {
            return (
              <h4
                className="text-base font-semibold my-2 text-gray-900 dark:text-gray-100"
                {...props}
              />
            );
          },
          h5({ ...props }) {
            return (
              <h5
                className="text-sm font-semibold my-1 text-gray-900 dark:text-gray-100"
                {...props}
              />
            );
          },
          h6({ ...props }) {
            return (
              <h6
                className="text-xs font-semibold my-1 text-gray-900 dark:text-gray-100"
                {...props}
              />
            );
          },
          p({ ...props }) {
            return <p className="my-2 leading-relaxed" {...props} />;
          },
          strong({ ...props }) {
            return (
              <strong
                className="font-bold text-gray-900 dark:text-gray-100"
                {...props}
              />
            );
          },
          em({ ...props }) {
            return <em className="italic" {...props} />;
          },
          hr({ ...props }) {
            return (
              <hr
                className="my-4 border-gray-300 dark:border-gray-700"
                {...props}
              />
            );
          },
        }}
      >
        {displayContent}
      </ReactMarkdown>
    </div>
  );
};

export default MarkdownRenderer;
