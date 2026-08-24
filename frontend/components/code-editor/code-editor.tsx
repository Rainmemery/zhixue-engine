"use client";

import { useState, useRef, useCallback, useEffect } from "react";
import Editor, { OnMount, useMonaco } from "@monaco-editor/react";
import * as monaco from "monaco-editor";
import { motion } from "framer-motion";
import {
  Copy,
  Download,
  Trash2,
  FileCode,
  Check,
  Sparkles,
  Send,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { appConfig } from "@/config/app.config";
import { ContextMenu, CodeSelection, ContextMenuAction, AIContextMenuAction } from "./context-menu";
import { undoManager } from "@/lib/undo-manager";
import { cn } from "@/lib/utils";
import { useCodeCompletion } from "@/hooks/useCodeCompletion";

// 代码模板
const codeTemplates: Record<string, string> = {
  java: `public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, World!");
        // 在这里编写您的Java代码
    }
}`,
  python: `def main():
    print("Hello, World!")
    # 在这里编写您的Python代码

if __name__ == "__main__":
    main()`,
  cpp: `#include <iostream>
using namespace std;

int main() {
    cout << "Hello, World!" << endl;
    // 在这里编写您的C++代码
    return 0;
}`,
  c: `#include <stdio.h>

int main() {
    printf("Hello, World!\\n");
    // 在这里编写您的C代码
    return 0;
}`,
  sql: `-- SQL查询
SELECT 'Hello, World!' AS message;
-- 在这里编写您的SQL查询`,
  javascript: `function main() {
    console.log("Hello, World!");
    // 在这里编写您的JavaScript代码
}

main();`,
  typescript: `function main(): void {
    console.log("Hello, World!");
    // 在这里编写您的TypeScript代码
}

main();`,
};

// 获取文件扩展名
const getFileExtension = (lang: string): string => {
  const extensions: Record<string, string> = {
    java: "java",
    python: "py",
    cpp: "cpp",
    c: "c",
    sql: "sql",
    javascript: "js",
    typescript: "ts",
  };
  return extensions[lang] || "txt";
};

// 支持格式化的语言列表
const languagesWithFormatter = new Set([
  'javascript', 'typescript', 'json', 'html', 'css', 'scss', 'less',
  'markdown', 'yaml', 'xml', 'sql'
]);

interface CodeEditorProps {
  initialValue?: string;
  initialLanguage?: string;
  onChange?: (code: string) => void;
  onLanguageChange?: (language: string) => void;
  onAIAction?: (
    action: AIContextMenuAction,
    selection: CodeSelection
  ) => void;
  onCodeReplace?: (newCode: string, selection: CodeSelection) => void;
  readOnly?: boolean;
  className?: string;
  showAIActions?: boolean;
  supportedLanguages?: { value: string; label: string; extension: string }[];
  enableGhostCompletion?: boolean;
  onSubmitCode?: (code: string, language: string) => void;
  templateCode?: Record<string, string>;
}

export function CodeEditor({
  initialValue,
  initialLanguage = "javascript",
  onChange,
  onLanguageChange,
  onAIAction,
  onCodeReplace,
  readOnly = false,
  className,
  supportedLanguages: supportedLanguagesProp,
  enableGhostCompletion = true,
  onSubmitCode,
  templateCode = {},
}: CodeEditorProps) {
  const [language, setLanguage] = useState(initialLanguage);
  // 根据 initialValue 和 language 计算初始代码
  const getInitialCode = () => {
    if (initialValue) return initialValue;
    if (templateCode[language]) return templateCode[language];
    return codeTemplates[language] || codeTemplates.javascript;
  };
  const [code, setCode] = useState(getInitialCode);
  const [copied, setCopied] = useState(false);
  const [contextMenuVisible, setContextMenuVisible] = useState(false);
  const [contextMenuPosition, setContextMenuPosition] = useState({ x: 0, y: 0 });
  const [codeSelection, setCodeSelection] = useState<CodeSelection | null>(null);

  const editorRef = useRef<monaco.editor.IStandaloneCodeEditor | null>(null);
  const monacoRef = useRef<typeof monaco | null>(null);
  const languageRef = useRef(language);
  const contextMenuTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // 保持languageRef始终同步
  useEffect(() => {
    languageRef.current = language;
  }, [language]);

  const {
    initEditor,
    doAccept,
    doDismiss,
    hasGhostCompletion,
    setEnabled: setCompletionEnabled,
  } = useCodeCompletion(language);

  const [completionOn, setCompletionOn] = useState(enableGhostCompletion);

  const doAcceptRef = useRef(doAccept);
  const doDismissRef = useRef(doDismiss);
  const hasGhostRef = useRef(hasGhostCompletion);
  useEffect(() => { doAcceptRef.current = enableGhostCompletion ? doAccept : () => false; }, [doAccept, enableGhostCompletion]);
  useEffect(() => { doDismissRef.current = enableGhostCompletion ? doDismiss : () => {}; }, [doDismiss, enableGhostCompletion]);
  useEffect(() => { hasGhostRef.current = enableGhostCompletion ? hasGhostCompletion : () => false; }, [hasGhostCompletion, enableGhostCompletion]);

  useEffect(() => {
    if (!enableGhostCompletion) {
      setCompletionEnabled(false);
      setCompletionOn(false);
    }
  }, [enableGhostCompletion, setCompletionEnabled]);

  const supportedLanguages = supportedLanguagesProp || appConfig.editor.supportedLanguages;

  const getSelectionInfo = useCallback((): CodeSelection | null => {
    const editor = editorRef.current;
    if (!editor) return null;

    const selection = editor.getSelection();
    if (!selection) return null;

    const model = editor.getModel();
    if (!model) return null;

    const expandedStartColumn = 1;
    const expandedEndColumn = model.getLineMaxColumn(selection.endLineNumber);

    const selectedText = model.getValueInRange({
      startLineNumber: selection.startLineNumber,
      startColumn: expandedStartColumn,
      endLineNumber: selection.endLineNumber,
      endColumn: expandedEndColumn
    });
    const fullCode = model.getValue();
    const totalLines = model.getLineCount();
    
    const contextLines = 10;
    
    let contextBefore = '';
    if (selection.startLineNumber > 1) {
      const beforeStartLine = Math.max(1, selection.startLineNumber - contextLines);
      const beforeEndLine = selection.startLineNumber - 1;
      contextBefore = model.getValueInRange({
        startLineNumber: beforeStartLine,
        startColumn: 1,
        endLineNumber: beforeEndLine,
        endColumn: model.getLineMaxColumn(beforeEndLine)
      });
    }
    
    let contextAfter = '';
    if (selection.endLineNumber < totalLines) {
      const afterStartLine = selection.endLineNumber + 1;
      const afterEndLine = Math.min(totalLines, selection.endLineNumber + contextLines);
      contextAfter = model.getValueInRange({
        startLineNumber: afterStartLine,
        startColumn: 1,
        endLineNumber: afterEndLine,
        endColumn: model.getLineMaxColumn(afterEndLine)
      });
    }

    return {
      startLine: selection.startLineNumber,
      endLine: selection.endLineNumber,
      startColumn: expandedStartColumn,
      endColumn: expandedEndColumn,
      selectedText: selectedText,
      language: languageRef.current,
      contextBefore,
      contextAfter,
      fullCode,
      totalLines
    };
  }, []);

  const replaceSelectedCode = useCallback((newCode: string, selection: CodeSelection) => {
    const editor = editorRef.current;
    if (!editor) return;

    const model = editor.getModel();
    if (!model) return;

    const currentState = {
      code: model.getValue(),
      selection: {
        startLine: selection.startLine,
        endLine: selection.endLine,
        startColumn: selection.startColumn,
        endColumn: selection.endColumn
      },
      timestamp: Date.now()
    };
    undoManager.push(currentState);

    const fullRange = new monaco.Range(
      selection.startLine,
      selection.startColumn,
      selection.endLine,
      selection.endColumn
    );

    model.pushEditOperations(
      [],
      [
        {
          range: fullRange,
          text: newCode
        }
      ],
      () => null
    );

    const newFullCode = model.getValue();
    setCode(newFullCode);
    onChange?.(newFullCode);

    const newLineCount = newCode.split('\n').length;
    const lastLineLength = newCode.split('\n').pop()?.length || 0;
    editor.setSelection(new monaco.Selection(
      selection.startLine,
      selection.startColumn,
      selection.startLine + newLineCount - 1,
      newLineCount === 1 ? selection.startColumn + newCode.length : lastLineLength + 1
    ));

    setTimeout(() => {
      try {
        editor.getAction("editor.action.formatDocument")?.run();
      } catch (err) {
        console.debug('格式化失败:', err);
      }
    }, 50);
  }, [onChange]);

  useEffect(() => {
    const handleReplace = (e: CustomEvent<{ newCode: string; selection: CodeSelection }>) => {
      const { newCode, selection } = e.detail;
      replaceSelectedCode(newCode, selection);
    };

    window.addEventListener('codeReplace', handleReplace as EventListener);
    return () => {
      window.removeEventListener('codeReplace', handleReplace as EventListener);
    };
  }, [replaceSelectedCode]);

  // 注册格式化提供程序
  const registerFormatters = useCallback((monacoInstance: typeof monaco) => {
    // 为 Java 注册基本的格式化程序
    monacoInstance.languages.registerDocumentFormattingEditProvider('java', {
      provideDocumentFormattingEdits: (model) => {
        const formatted = formatJavaCode(model.getValue());
        return [{
          range: model.getFullModelRange(),
          text: formatted,
        }];
      }
    });

    // 为 Python 注册基本的格式化程序
    monacoInstance.languages.registerDocumentFormattingEditProvider('python', {
      provideDocumentFormattingEdits: (model) => {
        const formatted = formatPythonCode(model.getValue());
        return [{
          range: model.getFullModelRange(),
          text: formatted,
        }];
      }
    });

    // 为 C/C++ 注册基本的格式化程序
    monacoInstance.languages.registerDocumentFormattingEditProvider('cpp', {
      provideDocumentFormattingEdits: (model) => {
        const formatted = formatCppCode(model.getValue());
        return [{
          range: model.getFullModelRange(),
          text: formatted,
        }];
      }
    });

    // 为 C 注册格式化程序
    monacoInstance.languages.registerDocumentFormattingEditProvider('c', {
      provideDocumentFormattingEdits: (model) => {
        const formatted = formatCppCode(model.getValue()); // C 和 C++ 使用相同的格式化逻辑
        return [{
          range: model.getFullModelRange(),
          text: formatted,
        }];
      }
    });

    // 为 SQL 注册基本的格式化程序
    monacoInstance.languages.registerDocumentFormattingEditProvider('sql', {
      provideDocumentFormattingEdits: (model) => {
        const formatted = formatSqlCode(model.getValue());
        return [{
          range: model.getFullModelRange(),
          text: formatted,
        }];
      }
    });
  }, []);

  // 格式化代码
  const handleFormat = useCallback(async () => {
    const editor = editorRef.current;
    if (!editor) return;

    try {
      // 执行 Monaco Editor 的格式化命令
      await editor.getAction("editor.action.formatDocument")?.run();
    } catch (err) {
      console.error('格式化失败:', err);
    }
  }, []);

  // 编辑器挂载
  const handleEditorDidMount: OnMount = (editor, monacoInstance) => {
    editorRef.current = editor;
    monacoRef.current = monacoInstance;

    // 注册格式化程序
    registerFormatters(monacoInstance);

    // 设置初始值
    if (!initialValue) {
      const template = templateCode[language] || codeTemplates[language] || codeTemplates.javascript;
      editor.setValue(template);
    }

    // 添加右键菜单事件监听
    editor.onContextMenu((e) => {
      e.event.preventDefault();
      
      // 清除之前的超时
      if (contextMenuTimeoutRef.current) {
        clearTimeout(contextMenuTimeoutRef.current);
      }
      
      // 使用 requestAnimationFrame 确保在下一个渲染周期获取选区
      contextMenuTimeoutRef.current = setTimeout(() => {
        const selectionInfo = getSelectionInfo();
        setCodeSelection(selectionInfo);
        
        setContextMenuPosition({
          x: e.event.browserEvent.clientX,
          y: e.event.browserEvent.clientY,
        });
        
        setContextMenuVisible(true);
      }, 0);
    });

    // 监听选区变化
    editor.onDidChangeCursorSelection(() => {
      const selectionInfo = getSelectionInfo();
      setCodeSelection(selectionInfo);
    });

    // 点击编辑器时关闭右键菜单
    editor.onMouseDown(() => {
      setContextMenuVisible(false);
    });

    // 添加格式化命令 (Shift+Alt+F)
    editor.addCommand(monaco.KeyMod.Shift | monaco.KeyMod.Alt | monaco.KeyCode.KeyF, () => {
      handleFormat();
    });

    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyZ, () => {
      const previousState = undoManager.undo();
      if (previousState && editorRef.current) {
        const model = editorRef.current.getModel();
        if (model) {
          model.setValue(previousState.code);
          setCode(previousState.code);
          onChange?.(previousState.code);
          
          editorRef.current.setSelection(new monaco.Selection(
            previousState.selection.startLine,
            previousState.selection.startColumn,
            previousState.selection.endLine,
            previousState.selection.endColumn
          ));
        }
      }
    });

    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyY, () => {
      const nextState = undoManager.redo();
      if (nextState && editorRef.current) {
        const model = editorRef.current.getModel();
        if (model) {
          model.setValue(nextState.code);
          setCode(nextState.code);
          onChange?.(nextState.code);
          
          editorRef.current.setSelection(new monaco.Selection(
            nextState.selection.startLine,
            nextState.selection.startColumn,
            nextState.selection.endLine,
            nextState.selection.endColumn
          ));
        }
      }
    });

    initEditor(editor);

    editor.addCommand(monaco.KeyCode.Tab, () => {
      if (hasGhostRef.current()) {
        doAcceptRef.current();
      } else {
        editor.trigger("keyboard", "type", { text: "\t" });
      }
    });

    editor.addCommand(monaco.KeyCode.Escape, () => {
      if (hasGhostRef.current()) {
        doDismissRef.current();
      }
    });
  };

  // 代码变化
  const handleCodeChange = (value: string | undefined) => {
    const newCode = value || "";
    setCode(newCode);
    onChange?.(newCode);
  };

  // 语言切换
  const handleLanguageChange = (lang: string) => {
    setLanguage(lang);
    const template = templateCode[lang] || codeTemplates[lang] || codeTemplates.javascript;
    setCode(template);
    if (editorRef.current) {
      editorRef.current.setValue(template);
    }
    onLanguageChange?.(lang);
  };

  // 应用模板
  const handleApplyTemplate = () => {
    const template = templateCode[language] || codeTemplates[language] || codeTemplates.javascript;
    setCode(template);
    if (editorRef.current) {
      editorRef.current.setValue(template);
    }
  };

  // 复制代码 - 使用现代 Clipboard API，带有降级处理
  const handleCopy = useCallback(async (text?: string) => {
    const textToCopy = text || code;
    
    try {
      // 优先使用现代 Clipboard API
      if (navigator.clipboard && navigator.clipboard.writeText) {
        await navigator.clipboard.writeText(textToCopy);
      } else {
        // 降级处理：使用传统的复制方法
        const textArea = document.createElement('textarea');
        textArea.value = textToCopy;
        textArea.style.cssText = 'position:fixed;left:-9999px;top:-9999px;opacity:0;';
        document.body.appendChild(textArea);
        textArea.focus();
        textArea.select();
        
        const successful = document.execCommand('copy');
        document.body.removeChild(textArea);
        
        if (!successful) {
          throw new Error('execCommand failed');
        }
      }
      
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error('复制失败:', err);
    }
  }, [code]);

  // 下载代码
  const handleDownload = () => {
    const blob = new Blob([code], { type: "text/plain" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `code.${getFileExtension(language)}`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  // 清空代码
  const handleClear = () => {
    setCode("");
    if (editorRef.current) {
      editorRef.current.setValue("");
    }
  };

  const handleSubmit = useCallback(() => {
    onSubmitCode?.(code, language);
  }, [code, language, onSubmitCode]);

  // 剪切功能
  const handleCut = useCallback(async (selection?: CodeSelection) => {
    const editor = editorRef.current;
    if (!editor) return;

    const selectionToCut = selection || getSelectionInfo();
    if (!selectionToCut?.selectedText) return;

    try {
      // 先复制到剪贴板
      await handleCopy(selectionToCut.selectedText);

      // 然后删除选中的内容
      const model = editor.getModel();
      const editorSelection = editor.getSelection();
      
      if (model && editorSelection) {
        model.pushEditOperations(
          [],
          [
            {
              range: editorSelection,
              text: "",
            },
          ],
          () => null
        );

        // 更新代码状态
        const newValue = model.getValue();
        setCode(newValue);
        onChange?.(newValue);
      }
    } catch (err) {
      console.error('剪切失败:', err);
    }
  }, [getSelectionInfo, handleCopy, onChange]);

  // 处理右键菜单操作
  const handleContextMenuAction = useCallback((action: ContextMenuAction, selection: CodeSelection | null) => {
    const editor = editorRef.current;
    if (!editor) return;

    switch (action) {
      case "copy": {
        // 如果有选区，复制选区内容；否则复制全部
        const textToCopy = selection?.selectedText || editor.getValue();
        handleCopy(textToCopy);
        break;
      }
      case "cut": {
        if (selection?.selectedText) {
          handleCut(selection);
        }
        break;
      }
      case "format": {
        handleFormat();
        break;
      }
      case "explain":
      case "review":
      case "refactor": {
        if (selection) {
          onAIAction?.(action as AIContextMenuAction, selection);
        }
        break;
      }
    }
  }, [handleCopy, handleCut, handleFormat, onAIAction]);

  // 清理超时
  useEffect(() => {
    return () => {
      if (contextMenuTimeoutRef.current) {
        clearTimeout(contextMenuTimeoutRef.current);
      }
    };
  }, []);

  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      className={cn("flex flex-col h-full rounded-xl overflow-hidden border border-gray-200 dark:border-gray-700", className)}
    >
      {/* Toolbar */}
      <div className="flex items-center justify-between p-3 bg-gray-50 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
        <div className="flex items-center gap-2">
          <Select value={language} onValueChange={handleLanguageChange}>
            <SelectTrigger className="w-32 h-8">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {supportedLanguages.map((lang) => (
                <SelectItem key={lang.value} value={lang.value}>
                  {lang.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          
          <Button
            variant="ghost"
            size="sm"
            onClick={handleApplyTemplate}
            className="h-8 text-xs"
          >
            <FileCode className="w-3.5 h-3.5 mr-1" />
            应用模板
          </Button>
        </div>

        <div className="flex items-center gap-1">
          {enableGhostCompletion && (
            <Button
              variant={completionOn ? "default" : "ghost"}
              size="sm"
              onClick={() => {
                const next = !completionOn;
                setCompletionOn(next);
                setCompletionEnabled(next);
              }}
              className={cn("h-8 text-xs", completionOn && "bg-primary/20 text-primary hover:bg-primary/30")}
              title={completionOn ? "智能补全已开启，点击关闭" : "智能补全已关闭，点击开启"}
            >
              <Sparkles className="w-3.5 h-3.5 mr-1" />
              {completionOn ? "补全开" : "补全关"}
            </Button>
          )}
          {!readOnly && (
            <>
              {onSubmitCode && (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={handleSubmit}
                  className="h-8 text-xs text-primary hover:text-primary"
                >
                  <Send className="w-3.5 h-3.5 mr-1" />
                  提交
                </Button>
              )}
              
              <Button
                variant="ghost"
                size="sm"
                onClick={() => handleCopy()}
                className="h-8 text-xs"
              >
                {copied ? (
                  <Check className="w-3.5 h-3.5 mr-1 text-green-500" />
                ) : (
                  <Copy className="w-3.5 h-3.5 mr-1" />
                )}
                复制
              </Button>
              
              <Button
                variant="ghost"
                size="sm"
                onClick={handleDownload}
                className="h-8 text-xs"
              >
                <Download className="w-3.5 h-3.5 mr-1" />
                下载
              </Button>
              
              <Button
                variant="ghost"
                size="sm"
                onClick={handleClear}
                className="h-8 text-xs text-red-500 hover:text-red-600"
              >
                <Trash2 className="w-3.5 h-3.5 mr-1" />
                清空
              </Button>
            </>
          )}
        </div>
      </div>

      {/* Editor */}
      <div className="flex-1 relative">
        <Editor
          height="100%"
          language={language}
          value={code}
          onChange={handleCodeChange}
          onMount={handleEditorDidMount}
          theme="vs-dark"
          loading="正在加载编辑器..."
          options={{
            minimap: { enabled: false },
            fontSize: 14,
            lineNumbers: "on",
            roundedSelection: false,
            scrollBeyondLastLine: false,
            automaticLayout: true,
            readOnly: readOnly,
            contextmenu: false, // 禁用默认右键菜单
            smoothScrolling: true,
            cursorSmoothCaretAnimation: "on",
            scrollbar: {
              vertical: "auto",
              horizontal: "auto",
              useShadows: false,
            },
            renderLineHighlight: "all",
            folding: true,
            glyphMargin: false,
            wordWrap: "on",
            quickSuggestions: true,
            suggestOnTriggerCharacters: true,
            acceptSuggestionOnEnter: "on",
            accessibilitySupport: "off",
            links: true,
            hover: {
              enabled: true,
              delay: 300,
            },
            formatOnPaste: true,
            formatOnType: true,
            // 性能优化选项
            renderWhitespace: "selection",
            renderControlCharacters: false,
            guides: {
              indentation: true,
              bracketPairs: true,
            },
            renderValidationDecorations: "on",
          }}
        />

        {/* Context Menu */}
        <ContextMenu
          visible={contextMenuVisible}
          position={contextMenuPosition}
          codeSelection={codeSelection}
          onAction={handleContextMenuAction}
          onClose={() => setContextMenuVisible(false)}
        />
      </div>
    </motion.div>
  );
}

// Java 代码格式化
function formatJavaCode(code: string): string {
  let indent = 0;
  const indentSize = 4;
  const lines = code.split('\n');
  const result: string[] = [];
  
  for (let line of lines) {
    line = line.trim();
    if (!line) {
      result.push('');
      continue;
    }
    
    // 减少缩进的情况
    if (line.startsWith('}') || line.startsWith(')') || line.startsWith(']')) {
      indent = Math.max(0, indent - 1);
    }
    
    result.push(' '.repeat(indent * indentSize) + line);
    
    // 增加缩进的情况
    if (line.endsWith('{') || line.endsWith('(') || line.endsWith('[') ||
        line.endsWith('} else {') || line.endsWith('} else')) {
      indent++;
    }
  }
  
  return result.join('\n');
}

// Python 代码格式化
function formatPythonCode(code: string): string {
  let indent = 0;
  const indentSize = 4;
  const lines = code.split('\n');
  const result: string[] = [];
  
  for (let line of lines) {
    const stripped = line.trim();
    if (!stripped) {
      result.push('');
      continue;
    }
    
    // 处理注释
    if (stripped.startsWith('#')) {
      result.push(' '.repeat(indent * indentSize) + stripped);
      continue;
    }
    
    // 减少缩进的情况
    if (stripped.startsWith('return') || stripped.startsWith('break') || 
        stripped.startsWith('continue') || stripped.startsWith('raise') ||
        stripped.startsWith('pass') || stripped.startsWith('}')) {
      // 保持当前缩进
    }
    
    // 检查是否需要减少缩进（例如 else, elif, except, finally）
    if (/^(else|elif|except|finally)\b/.test(stripped)) {
      indent = Math.max(0, indent - 1);
    }
    
    result.push(' '.repeat(indent * indentSize) + stripped);
    
    // 增加缩进的情况
    if (stripped.endsWith(':') && !stripped.startsWith('#')) {
      indent++;
    }
  }
  
  return result.join('\n');
}

// C/C++ 代码格式化
function formatCppCode(code: string): string {
  let indent = 0;
  const indentSize = 4;
  const lines = code.split('\n');
  const result: string[] = [];
  
  for (let line of lines) {
    line = line.trim();
    if (!line) {
      result.push('');
      continue;
    }
    
    // 处理预处理器指令
    if (line.startsWith('#')) {
      result.push(line);
      continue;
    }
    
    // 减少缩进的情况
    if (line.startsWith('}')) {
      indent = Math.max(0, indent - 1);
    }
    
    result.push(' '.repeat(indent * indentSize) + line);
    
    // 增加缩进的情况
    if (line.endsWith('{')) {
      indent++;
    }
    
    // 处理 else
    if (line === '}' || line.endsWith('} else') || line.endsWith('} else {')) {
      // 保持当前缩进级别
    }
  }
  
  return result.join('\n');
}

// SQL 代码格式化
function formatSqlCode(code: string): string {
  const keywords = [
    'SELECT', 'FROM', 'WHERE', 'AND', 'OR', 'INSERT', 'UPDATE', 'DELETE',
    'JOIN', 'LEFT', 'RIGHT', 'INNER', 'OUTER', 'ON', 'GROUP', 'BY',
    'ORDER', 'HAVING', 'LIMIT', 'OFFSET', 'UNION', 'ALL', 'VALUES',
    'CREATE', 'TABLE', 'ALTER', 'DROP', 'INDEX', 'VIEW'
  ];
  
  const lines = code.split('\n');
  const result: string[] = [];
  let indent = 0;
  const indentSize = 4;
  
  for (let line of lines) {
    const trimmed = line.trim();
    if (!trimmed) {
      result.push('');
      continue;
    }
    
    const upperLine = trimmed.toUpperCase();
    
    // 根据关键字调整缩进
    if (/^(SELECT|INSERT|UPDATE|DELETE|CREATE|ALTER|DROP)\b/.test(upperLine)) {
      indent = 0;
    } else if (/^(FROM|WHERE|GROUP|ORDER|HAVING|LIMIT|VALUES)\b/.test(upperLine)) {
      indent = 1;
    } else if (/^(AND|OR|JOIN|LEFT|RIGHT|INNER|OUTER|ON)\b/.test(upperLine)) {
      indent = 2;
    }
    
    // 大写关键字
    let formattedLine = trimmed;
    keywords.forEach(keyword => {
      const regex = new RegExp(`\\b${keyword}\\b`, 'gi');
      formattedLine = formattedLine.replace(regex, keyword);
    });
    
    result.push(' '.repeat(indent * indentSize) + formattedLine);
  }
  
  return result.join('\n');
}

export default CodeEditor;
