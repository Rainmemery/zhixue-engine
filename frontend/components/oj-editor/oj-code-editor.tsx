"use client";

import { useState, useCallback, useEffect, useRef } from "react";
import { GripVertical, Terminal, Play, Send, Loader2 } from "lucide-react";
import { CodeEditor } from "@/components/code-editor/code-editor";
import { JudgeResultPanel } from "./judge-result-panel";
import { cn } from "@/lib/utils";

interface OJCodeEditorProps {
  problemId: number;
  templateCode?: Record<string, string>;
  onRunTest: (code: string, language: string, customInput?: string, customExpectedOutput?: string) => Promise<any>;
  onSubmitCode: (code: string, language: string) => Promise<any>;
  submissionResult?: any;
  isSubmissionTracking?: boolean;
}

const OJ_LANGUAGES = [
  { value: "java", label: "Java", extension: "java" },
  { value: "cpp", label: "C++", extension: "cpp" },
  { value: "c", label: "C", extension: "c" },
];

export function OJCodeEditor({ problemId, templateCode, onRunTest, onSubmitCode, submissionResult, isSubmissionTracking }: OJCodeEditorProps) {
  const [language, setLanguage] = useState("java");
  const [code, setCode] = useState("");
  const [isDragging, setIsDragging] = useState(false);
  const [editorHeight, setEditorHeight] = useState(55);
  const [judgeResult, setJudgeResult] = useState<any>(null);
  const [isRunning, setIsRunning] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [activeMode, setActiveMode] = useState<"run" | "submit" | null>(null);
  const [customInput, setCustomInput] = useState("");
  const [customExpectedOutput, setCustomExpectedOutput] = useState("");
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (templateCode && templateCode[language]) {
      setCode(templateCode[language]);
    }
  }, [language, templateCode]);

  const displayResult = activeMode === "submit" && submissionResult
    ? submissionResult
    : judgeResult;

  const handleMouseDown = useCallback(() => {
    setIsDragging(true);
  }, []);

  useEffect(() => {
    if (!isDragging) return;
    const handleMouseMove = (e: MouseEvent) => {
      if (!containerRef.current) return;
      const rect = containerRef.current.getBoundingClientRect();
      const newHeight = ((e.clientY - rect.top) / rect.height) * 100;
      setEditorHeight(Math.min(Math.max(newHeight, 30), 75));
    };
    const handleMouseUp = () => setIsDragging(false);
    document.addEventListener("mousemove", handleMouseMove);
    document.addEventListener("mouseup", handleMouseUp);
    return () => {
      document.removeEventListener("mousemove", handleMouseMove);
      document.removeEventListener("mouseup", handleMouseUp);
    };
  }, [isDragging]);

  const handleRunTest = useCallback(async () => {
    if (!code.trim()) return;
    setIsRunning(true);
    setJudgeResult(null);
    setActiveMode("run");
    try {
      const result = await onRunTest(code, language, customInput || undefined, customExpectedOutput || undefined);
      setJudgeResult(result);
    } catch (err) {
      setJudgeResult({ status: "system_error", errorMessage: String(err) });
    } finally {
      setIsRunning(false);
    }
  }, [code, language, onRunTest, customInput, customExpectedOutput]);

  const handleSubmitCode = useCallback(async () => {
    if (!code.trim()) return;
    setIsSubmitting(true);
    setJudgeResult(null);
    setActiveMode("submit");
    try {
      await onSubmitCode(code, language);
      setJudgeResult({ status: "pending" });
    } catch (err) {
      setJudgeResult({ status: "system_error", errorMessage: String(err) });
    } finally {
      setIsSubmitting(false);
    }
  }, [code, language, onSubmitCode]);

  return (
    <div ref={containerRef} className="flex flex-col w-full h-full overflow-hidden">
      <div
        className="border-b border-gray-200 dark:border-gray-700 overflow-hidden"
        style={{ height: `${editorHeight}%` }}
      >
        <CodeEditor
          initialLanguage={language}
          initialValue={templateCode?.[language] || ""}
          onChange={setCode}
          onLanguageChange={setLanguage}
          supportedLanguages={OJ_LANGUAGES}
          enableGhostCompletion={false}
          showAIActions={false}
          templateCode={templateCode}
        />
      </div>

      <div
        className="shrink-0 h-1 bg-gray-200 dark:bg-gray-800 hover:bg-cyan-400 dark:hover:bg-cyan-600 cursor-row-resize flex items-center justify-center transition-colors z-10"
        onMouseDown={handleMouseDown}
      >
        <GripVertical className="w-3 h-3 text-gray-400" />
      </div>

      <div className="flex-1 min-h-0 flex flex-col">
        <div className="shrink-0 flex items-center justify-between px-3 py-1.5 border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900">
          <div className="flex items-center gap-2">
            <Terminal className="w-3.5 h-3.5 text-muted-foreground" />
            <span className="text-xs font-medium text-muted-foreground">测试运行</span>
          </div>
          <div className="flex items-center gap-1.5">
            <button
              onClick={handleRunTest}
              disabled={isRunning || isSubmitting || !!isSubmissionTracking || !code.trim()}
              className={cn(
                "flex items-center gap-1 px-2.5 py-1 rounded-md text-xs font-medium transition-colors",
                isRunning || isSubmitting || !!isSubmissionTracking || !code.trim()
                  ? "bg-gray-100 dark:bg-gray-800 text-gray-400 cursor-not-allowed"
                  : "bg-emerald-500 hover:bg-emerald-600 text-white"
              )}
            >
              {isRunning ? <Loader2 className="w-3 h-3 animate-spin" /> : <Play className="w-3 h-3" />}
              运行
            </button>
            <button
              onClick={handleSubmitCode}
              disabled={isRunning || isSubmitting || !!isSubmissionTracking || !code.trim()}
              className={cn(
                "flex items-center gap-1 px-2.5 py-1 rounded-md text-xs font-medium transition-colors",
                isRunning || isSubmitting || !!isSubmissionTracking || !code.trim()
                  ? "bg-gray-100 dark:bg-gray-800 text-gray-400 cursor-not-allowed"
                  : "bg-primary hover:bg-primary/90 text-primary-foreground"
              )}
            >
              {isSubmitting || isSubmissionTracking ? <Loader2 className="w-3 h-3 animate-spin" /> : <Send className="w-3 h-3" />}
              提交
            </button>
          </div>
        </div>

        <div className="shrink-0 border-b border-gray-200 dark:border-gray-700 p-2.5 bg-gray-50/50 dark:bg-gray-900/50">
          <div className="grid grid-cols-2 gap-2">
            <div>
              <label className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wider flex items-center gap-1 mb-1">
                <Terminal className="w-2.5 h-2.5" />
                测试输入
              </label>
              <textarea
                value={customInput}
                onChange={(e) => setCustomInput(e.target.value)}
                placeholder="输入测试数据..."
                rows={3}
                className="w-full p-1.5 rounded border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-950 text-[11px] font-mono resize-y focus:outline-none focus:ring-1 focus:ring-primary"
              />
            </div>
            <div>
              <label className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wider flex items-center gap-1 mb-1">
                预期输出（可选）
              </label>
              <textarea
                value={customExpectedOutput}
                onChange={(e) => setCustomExpectedOutput(e.target.value)}
                placeholder="留空则仅显示运行结果..."
                rows={3}
                className="w-full p-1.5 rounded border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-950 text-[11px] font-mono resize-y focus:outline-none focus:ring-1 focus:ring-primary"
              />
            </div>
          </div>
        </div>

        <div className="flex-1 overflow-y-auto p-2.5">
          <JudgeResultPanel result={displayResult} isRunning={isRunning} isSubmitting={isSubmitting} isCustomMode={true} />
        </div>
      </div>
    </div>
  );
}

export default OJCodeEditor;
