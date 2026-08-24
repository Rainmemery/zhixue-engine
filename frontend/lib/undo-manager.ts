interface UndoState {
  code: string;
  selection: {
    startLine: number;
    endLine: number;
    startColumn: number;
    endColumn: number;
  };
  timestamp: number;
}

export class UndoManager {
  private undoStack: UndoState[] = [];
  private redoStack: UndoState[] = [];
  private maxSize: number = 50;

  push(state: UndoState): void {
    this.undoStack.push(state);
    if (this.undoStack.length > this.maxSize) {
      this.undoStack.shift();
    }
    this.redoStack = [];
  }

  undo(): UndoState | null {
    const state = this.undoStack.pop();
    if (state) {
      this.redoStack.push(state);
      return state;
    }
    return null;
  }

  redo(): UndoState | null {
    const state = this.redoStack.pop();
    if (state) {
      this.undoStack.push(state);
      return state;
    }
    return null;
  }

  canUndo(): boolean {
    return this.undoStack.length > 0;
  }

  canRedo(): boolean {
    return this.redoStack.length > 0;
  }

  clear(): void {
    this.undoStack = [];
    this.redoStack = [];
  }
}

export const undoManager = new UndoManager();
