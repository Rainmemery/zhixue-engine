"use client";

import { useRef, useCallback, useEffect } from "react";
import * as monaco from "monaco-editor";
import { aiService, CompletionContext } from "@/services/aiService";
import {
  renderGhostText,
  clearGhostText,
  acceptGhostCompletion,
  hasGhostCompletion,
} from "@/components/code-editor/completion-ghost";

interface UseCodeCompletionOptions {
  debounceDelay?: number;
  cursorIdleDelay?: number;
  maxPrefixLength?: number;
  requestTimeout?: number;
  maxConsecutiveFailures?: number;
  circuitBreakerCooldown?: number;
}

const DEFAULT_OPTIONS: Required<UseCodeCompletionOptions> = {
  debounceDelay: 800,
  cursorIdleDelay: 1500,
  maxPrefixLength: 2000,
  requestTimeout: 5000,
  maxConsecutiveFailures: 3,
  circuitBreakerCooldown: 5 * 60 * 1000,
};

export function useCodeCompletion(language: string, options?: UseCodeCompletionOptions) {
  const config = { ...DEFAULT_OPTIONS, ...options };

  const editorRef = useRef<monaco.editor.IStandaloneCodeEditor | null>(null);
  const enabledRef = useRef(true);
  const languageRef = useRef(language);
  const debounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const cursorIdleTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const abortControllerRef = useRef<AbortController | null>(null);
  const lastRequestFingerprintRef = useRef<string>("");
  const isRequestingRef = useRef(false);
  const consecutiveFailuresRef = useRef(0);
  const circuitBreakerUntilRef = useRef(0);
  const requestIdRef = useRef(0);
  const contentDisposableRef = useRef<monaco.IDisposable | null>(null);
  const cursorDisposableRef = useRef<monaco.IDisposable | null>(null);

  useEffect(() => {
    languageRef.current = language;
  }, [language]);

  const collectContext = useCallback(
    (ed: monaco.editor.IStandaloneCodeEditor): CompletionContext | null => {
      const model = ed.getModel();
      const position = ed.getPosition();
      if (!model || !position) return null;

      const totalLines = model.getLineCount();

      const prefixCode = model.getValueInRange({
        startLineNumber: 1,
        startColumn: 1,
        endLineNumber: position.lineNumber,
        endColumn: position.column,
      });

      const contextBeforeStartLine = Math.max(1, position.lineNumber - 20);
      const contextBefore = model.getValueInRange({
        startLineNumber: contextBeforeStartLine,
        startColumn: 1,
        endLineNumber: position.lineNumber,
        endColumn: position.column,
      });

      const contextAfterEndLine = Math.min(totalLines, position.lineNumber + 10);
      const contextAfter = model.getValueInRange({
        startLineNumber: position.lineNumber,
        startColumn: position.column,
        endLineNumber: contextAfterEndLine,
        endColumn: model.getLineMaxColumn(contextAfterEndLine),
      });

      const code = prefixCode.length > config.maxPrefixLength
        ? prefixCode.slice(-config.maxPrefixLength)
        : prefixCode;

      return {
        code,
        language: languageRef.current,
        cursorLine: position.lineNumber,
        cursorColumn: position.column,
        contextBefore,
        contextAfter,
        fullCode: model.getValue(),
        totalLines,
      };
    },
    [config.maxPrefixLength]
  );

  const getFingerprint = useCallback((ctx: CompletionContext): string => {
    return `${ctx.cursorLine}:${ctx.cursorColumn}:${ctx.code.slice(-100)}`;
  }, []);

  const immediateCancel = useCallback(() => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }

    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
      debounceTimerRef.current = null;
    }
    if (cursorIdleTimerRef.current) {
      clearTimeout(cursorIdleTimerRef.current);
      cursorIdleTimerRef.current = null;
    }

    const ed = editorRef.current;
    if (ed) {
      clearGhostText(ed);
    }

    isRequestingRef.current = false;
    requestIdRef.current += 1;
  }, []);

  const removeClientDuplicate = useCallback(
    (completion: string, context: CompletionContext): string => {
      const prefixLines = context.code.split("\n");
      const lastPrefixLine = prefixLines[prefixLines.length - 1];
      const lastPrefixTrimmed = lastPrefixLine.trim();

      if (!lastPrefixTrimmed) return completion;

      const completionLines = completion.split("\n");
      const firstLine = completionLines[0];
      const firstLineTrimmed = firstLine.trim();

      if (firstLineTrimmed === lastPrefixTrimmed) {
        return completionLines.slice(1).join("\n");
      }

      const prefixWords = lastPrefixTrimmed.split(/\s+/);
      const lastWord = prefixWords[prefixWords.length - 1] || "";

      if (lastWord && completion.startsWith(lastWord) && completion !== lastWord) {
        return completion.substring(lastWord.length);
      }

      if (firstLineTrimmed.includes(lastPrefixTrimmed)) {
        const idx = firstLineTrimmed.indexOf(lastPrefixTrimmed);
        const endIdx = idx + lastPrefixTrimmed.length;
        const after = firstLineTrimmed.substring(endIdx);
        if (after) {
          const indent = firstLine.substring(0, firstLine.length - firstLine.trimStart().length);
          completionLines[0] = indent + after.trimStart();
          return completionLines.join("\n");
        }
      }

      if (lastWord && firstLineTrimmed.includes(lastWord)) {
        const idx = firstLineTrimmed.indexOf(lastWord);
        const endIdx = idx + lastWord.length;
        const after = firstLineTrimmed.substring(endIdx);
        if (after && after.length < firstLineTrimmed.length) {
          const indent = firstLine.substring(0, firstLine.length - firstLine.trimStart().length);
          completionLines[0] = indent + after.trimStart();
          return completionLines.join("\n");
        }
      }

      return completion;
    },
    []
  );

  const validateCompletionResponse = useCallback(
    (data: unknown, context: CompletionContext): { completion: string; isComplete: boolean } | null => {
      if (!data || typeof data !== "object") return null;

      const response = data as Record<string, unknown>;

      if (response.success === false) {
        return null;
      }

      const result = response.data as Record<string, unknown> | undefined;
      if (!result) return null;

      let completion = typeof result.completion === "string" ? result.completion : "";
      if (!completion || completion.trim().length === 0) return null;

      completion = removeClientDuplicate(completion, context);

      if (!completion || completion.trim().length === 0) return null;

      const completionLines = completion.split("\n");
      let safeCompletion = completion;
      if (completionLines.length > 5) {
        safeCompletion = completionLines.slice(0, 5).join("\n");
      }

      if (safeCompletion.length > 300) {
        safeCompletion = safeCompletion.substring(0, 300);
      }

      safeCompletion = safeCompletion.trimEnd();

      if (!safeCompletion) return null;

      return {
        completion: safeCompletion,
        isComplete: typeof result.isComplete === "boolean" ? result.isComplete : true,
      };
    },
    [removeClientDuplicate]
  );

  const triggerCompletion = useCallback(async () => {
    const ed = editorRef.current;
    if (!ed) return;

    if (!enabledRef.current) return;
    if (Date.now() < circuitBreakerUntilRef.current) return;
    if (isRequestingRef.current) return;

    const context = collectContext(ed);
    if (!context) return;

    if (!context.code.trim()) return;

    if (context.code.split("\n").length < 3) return;

    const model = ed.getModel();
    const pos = ed.getPosition();
    if (model && pos) {
      const lineContent = model.getLineContent(pos.lineNumber);
      const textAfterCursor = lineContent.substring(pos.column - 1);
      if (textAfterCursor.trim().length > 0) return;
    }

    const fingerprint = getFingerprint(context);
    if (fingerprint === lastRequestFingerprintRef.current) return;

    const currentRequestId = ++requestIdRef.current;
    isRequestingRef.current = true;
    lastRequestFingerprintRef.current = fingerprint;

    abortControllerRef.current = new AbortController();

    try {
      const result = await aiService.completeCode(context, {
        timeout: config.requestTimeout,
        signal: abortControllerRef.current.signal,
      });

      if (currentRequestId !== requestIdRef.current) return;

      const validated = validateCompletionResponse(result, context);
      if (validated) {
        const pos = ed.getPosition();
        if (pos && pos.lineNumber === context.cursorLine && pos.column === context.cursorColumn) {
          renderGhostText(ed, validated.completion, context.cursorLine, context.cursorColumn);
        }
      }

      consecutiveFailuresRef.current = 0;
    } catch (error: unknown) {
      if (error instanceof DOMException && error.name === "AbortError") return;
      if (error && typeof error === "object" && "code" in error) {
        const axiosError = error as { code?: string };
        if (axiosError.code === "ERR_CANCELED" || axiosError.code === "ECONNABORTED") return;
      }

      if (currentRequestId !== requestIdRef.current) return;

      consecutiveFailuresRef.current += 1;
      if (consecutiveFailuresRef.current >= config.maxConsecutiveFailures) {
        circuitBreakerUntilRef.current = Date.now() + config.circuitBreakerCooldown;
      }
    } finally {
      if (currentRequestId === requestIdRef.current) {
        isRequestingRef.current = false;
      }
    }
  }, [collectContext, getFingerprint, validateCompletionResponse, config]);

  const handleContentChange = useCallback(() => {
    immediateCancel();
    if (!enabledRef.current) return;
    debounceTimerRef.current = setTimeout(triggerCompletion, config.debounceDelay);
  }, [immediateCancel, triggerCompletion, config.debounceDelay]);

  const handleCursorPositionChange = useCallback(() => {
    immediateCancel();
    if (!enabledRef.current) return;
    cursorIdleTimerRef.current = setTimeout(triggerCompletion, config.cursorIdleDelay);
  }, [immediateCancel, triggerCompletion, config.cursorIdleDelay]);

  const initEditor = useCallback((editor: monaco.editor.IStandaloneCodeEditor) => {
    editorRef.current = editor;

    if (contentDisposableRef.current) {
      contentDisposableRef.current.dispose();
    }
    if (cursorDisposableRef.current) {
      cursorDisposableRef.current.dispose();
    }

    contentDisposableRef.current = editor.onDidChangeModelContent(() => {
      handleContentChange();
    });

    cursorDisposableRef.current = editor.onDidChangeCursorPosition((e) => {
      if (
        e.reason === monaco.editor.CursorChangeReason.NotSet ||
        e.reason === monaco.editor.CursorChangeReason.Explicit
      ) {
        handleCursorPositionChange();
      }
    });
  }, [handleContentChange, handleCursorPositionChange]);

  const setEnabled = useCallback((enabled: boolean) => {
    enabledRef.current = enabled;
    if (!enabled) {
      immediateCancel();
    }
  }, [immediateCancel]);

  const doAccept = useCallback((): boolean => {
    const ed = editorRef.current;
    if (!ed) return false;
    return acceptGhostCompletion(ed);
  }, []);

  const doDismiss = useCallback(() => {
    const ed = editorRef.current;
    if (!ed) return;
    clearGhostText(ed);
  }, []);

  useEffect(() => {
    return () => {
      immediateCancel();
      if (contentDisposableRef.current) {
        contentDisposableRef.current.dispose();
      }
      if (cursorDisposableRef.current) {
        cursorDisposableRef.current.dispose();
      }
    };
  }, [immediateCancel]);

  return {
    initEditor,
    doAccept,
    doDismiss,
    hasGhostCompletion,
    setEnabled,
  };
}
