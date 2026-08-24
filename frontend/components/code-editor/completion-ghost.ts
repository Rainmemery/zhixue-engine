import * as monaco from "monaco-editor";

let completionText: string = "";
let viewZoneId: string | null = null;
let overlayNode: HTMLElement | null = null;
let overlayEditor: monaco.editor.IStandaloneCodeEditor | null = null;

function getCursorPixelPosition(
  editor: monaco.editor.IStandaloneCodeEditor,
  line: number,
  column: number
): { top: number; left: number; lineHeight: number } | null {
  const layoutInfo = editor.getLayoutInfo();
  const editorDom = editor.getDomNode();
  if (!editorDom) return null;

  const top = editor.getTopForLineNumber(line);
  const lineContent = editor.getModel()?.getLineContent(line) || "";
  const left = editor.getOffsetForColumn(line, column);

  const scrollTop = editor.getScrollTop();
  const scrollLeft = editor.getScrollLeft();

  const fontInfo = editor.getOption(monaco.editor.EditorOption.fontInfo);
  const lineHeight = editor.getOption(monaco.editor.EditorOption.lineHeight);

  const contentLeft = layoutInfo.contentLeft;

  return {
    top: top - scrollTop,
    left: contentLeft + left - scrollLeft,
    lineHeight,
  };
}

function ensureOverlayContainer(editor: monaco.editor.IStandaloneCodeEditor): HTMLDivElement {
  const editorDom = editor.getDomNode();
  if (!editorDom) throw new Error("No editor DOM");

  const existing = editorDom.querySelector(".completion-ghost-overlay") as HTMLDivElement | null;
  if (existing) return existing;

  const container = document.createElement("div");
  container.className = "completion-ghost-overlay";
  container.style.cssText = "position:absolute;top:0;left:0;width:100%;height:100%;pointer-events:none;overflow:hidden;z-index:10;";
  editorDom.style.position = "relative";
  editorDom.appendChild(container);
  return container;
}

export function renderGhostText(
  editor: monaco.editor.IStandaloneCodeEditor,
  text: string,
  line: number,
  column: number
): void {
  clearGhostText(editor);

  completionText = text;
  overlayEditor = editor;

  const model = editor.getModel();
  if (!model) return;

  const lines = text.split("\n");
  const fontInfo = editor.getOption(monaco.editor.EditorOption.fontInfo);

  if (lines[0]) {
    const pos = getCursorPixelPosition(editor, line, column);
    if (pos) {
      const container = ensureOverlayContainer(editor);
      overlayNode = document.createElement("span");
      overlayNode.className = "completion-ghost-inline-overlay";
      overlayNode.textContent = lines[0];
      overlayNode.style.cssText = `
        position: absolute;
        top: ${pos.top}px;
        left: ${pos.left}px;
        line-height: ${pos.lineHeight}px;
        font-family: ${fontInfo.fontFamily};
        font-size: ${fontInfo.fontSize}px;
        letter-spacing: ${fontInfo.letterSpacing}px;
        font-style: italic;
        color: rgba(255, 255, 255, 0.55);
        white-space: pre;
        pointer-events: none;
        user-select: none;
      `;
      container.appendChild(overlayNode);
    }
  }

  if (lines.length > 1) {
    const restLines = lines.slice(1);
    editor.changeViewZones((accessor) => {
      if (viewZoneId !== null) {
        accessor.removeZone(viewZoneId);
      }

      const domNode = document.createElement("div");
      domNode.className = "completion-ghost-zone";

      restLines.forEach((ln) => {
        const div = document.createElement("div");
        div.className = "completion-ghost-zone-line";
        div.textContent = ln || "\u00A0";
        div.style.fontFamily = fontInfo.fontFamily;
        div.style.fontSize = fontInfo.fontSize + "px";
        div.style.lineHeight = fontInfo.lineHeight + "px";
        div.style.letterSpacing = fontInfo.letterSpacing + "px";
        domNode.appendChild(div);
      });

      viewZoneId = accessor.addZone({
        afterLineNumber: line,
        afterColumn: model.getLineMaxColumn(line),
        heightInLines: restLines.length,
        domNode,
      });
    });
  }

  const scrollDisposable = editor.onDidScrollChange(() => {
    if (!completionText || !overlayEditor) return;
    const pos = getCursorPixelPosition(overlayEditor, line, column);
    if (pos && overlayNode) {
      overlayNode.style.top = `${pos.top}px`;
      overlayNode.style.left = `${pos.left}px`;
    }
  });

  (editor as any).__completionScrollDisposable = scrollDisposable;
}

export function clearGhostText(editor: monaco.editor.IStandaloneCodeEditor): void {
  completionText = "";

  if (overlayNode && overlayNode.parentNode) {
    overlayNode.parentNode.removeChild(overlayNode);
  }
  overlayNode = null;

  const scrollDisposable = (editor as any).__completionScrollDisposable;
  if (scrollDisposable) {
    scrollDisposable.dispose();
    (editor as any).__completionScrollDisposable = null;
  }

  if (viewZoneId !== null) {
    try {
      editor.changeViewZones((accessor) => {
        accessor.removeZone(viewZoneId!);
      });
    } catch {}
    viewZoneId = null;
  }

  overlayEditor = null;
}

export function acceptGhostCompletion(
  editor: monaco.editor.IStandaloneCodeEditor
): boolean {
  if (!completionText) return false;

  const pos = editor.getPosition();
  if (!pos) return false;

  const model = editor.getModel();
  if (!model) return false;

  const text = completionText;
  clearGhostText(editor);

  model.pushEditOperations(
    [],
    [
      {
        range: new monaco.Range(pos.lineNumber, pos.column, pos.lineNumber, pos.column),
        text,
      },
    ],
    () => null
  );

  const textLines = text.split("\n");
  let newLine: number;
  let newCol: number;

  if (textLines.length === 1) {
    newLine = pos.lineNumber;
    newCol = pos.column + text.length;
  } else {
    newLine = pos.lineNumber + textLines.length - 1;
    newCol = textLines[textLines.length - 1].length + 1;
  }

  editor.setPosition(new monaco.Position(newLine, newCol));
  editor.revealPositionInCenterIfOutsideViewport(new monaco.Position(newLine, newCol));

  return true;
}

export function hasGhostCompletion(): boolean {
  return completionText.length > 0;
}

export function getGhostCompletionText(): string {
  return completionText;
}
