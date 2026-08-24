/**
 * 文本处理工具函数
 * 用于处理AI返回的文本内容，包括Markdown代码块清理、JSON解析等
 */

const MAX_CLEAN_DEPTH = 5;

/**
 * 去除Markdown代码块标记
 * 支持带语言标识和不带语言标识的代码块
 */
export function stripMarkdownCodeBlock(code: string): string {
  if (!code) return code;
  let result = code.trim();

  const mdPatterns = [
    /^```[a-zA-Z]*\s*\n([\s\S]*?)\n\s*```$/,
    /^```[a-zA-Z]*\s*\n([\s\S]*?)```$/,
    /^```[a-zA-Z]*\s*([\s\S]*?)```$/,
    /```[a-zA-Z]*\s*\n([\s\S]*?)\n\s*```/,
    /```[a-zA-Z]*\s*\n([\s\S]*?)```/,
    /```[a-zA-Z]*\s*([\s\S]*?)```/,
  ];

  for (const pattern of mdPatterns) {
    const match = result.match(pattern);
    if (match && match[1]) {
      return match[1].trim();
    }
  }

  if (result.startsWith("```")) {
    const firstNewline = result.indexOf("\n");
    if (firstNewline !== -1) {
      result = result.substring(firstNewline + 1);
    } else {
      result = result.substring(3);
    }
  }
  if (result.endsWith("```")) {
    result = result.substring(0, result.length - 3);
  }

  return result.trim();
}

/**
 * 清理JSON字符串，处理嵌套JSON和转义字符
 */
export function cleanJsonString(code: string, depth: number = 0): string {
  if (!code) return code;

  if (depth > MAX_CLEAN_DEPTH) {
    console.warn(`[TextProcessor] 达到最大递归深度 ${MAX_CLEAN_DEPTH}，停止清理`);
    return code;
  }

  let result = code.trim();

  result = stripMarkdownCodeBlock(result);

  if (result.includes("refactoredCode")) {
    try {
      let jsonToParse = result;
      if (jsonToParse.startsWith('"') && jsonToParse.endsWith('"')) {
        jsonToParse = jsonToParse.slice(1, -1);
        jsonToParse = jsonToParse.replace(/\\"/g, '"').replace(/\\\\/g, "\\");
      }

      if (jsonToParse.includes("{") && jsonToParse.includes("}")) {
        const start = jsonToParse.indexOf("{");
        const end = jsonToParse.lastIndexOf("}") + 1;
        jsonToParse = jsonToParse.substring(start, end);
      }

      const parsed = JSON.parse(jsonToParse);
      if (parsed.refactoredCode) {
        console.log(`[TextProcessor][深度${depth}] 检测到嵌套JSON，递归提取代码`);
        return cleanJsonString(parsed.refactoredCode, depth + 1);
      }
    } catch (e) {
      console.debug(`[TextProcessor][深度${depth}] 标准JSON解析失败，尝试正则提取:`, e);
    }

    const backtickMatch = result.match(new RegExp('"refactoredCode"\\s*:\\s*`([\\s\\S]*?)`\\s*[,}]'));
    if (backtickMatch && backtickMatch[1]) {
      const extracted = backtickMatch[1].trim();
      console.log(`[TextProcessor][深度${depth}] 通过反引号正则提取到重构代码`);
      return cleanJsonString(extracted, depth + 1);
    }

    const quoteMatch = result.match(new RegExp('"refactoredCode"\\s*:\\s*"((?:[^"\\\\]|[\\s\\S])*)"\\s*[,}]'));
    if (quoteMatch && quoteMatch[1]) {
      let extracted = quoteMatch[1];
      extracted = extracted.replace(/\\n/g, "\n").replace(/\\t/g, "\t").replace(/\\"/g, '"').replace(/\\\\/g, "\\").trim();
      console.log(`[TextProcessor][深度${depth}] 通过引号正则提取到重构代码`);
      return cleanJsonString(extracted, depth + 1);
    }
  }

  if (result.startsWith('"') && result.endsWith('"')) {
    let unquoted = result.slice(1, -1);
    unquoted = unquoted.replace(/\\"/g, '"').replace(/\\\\/g, "\\").replace(/\\n/g, "\n").replace(/\\t/g, "\t").trim();

    if (unquoted.includes("refactoredCode") || unquoted.includes("```")) {
      return cleanJsonString(unquoted, depth + 1);
    }
    result = unquoted;
  }

  return result;
}

/**
 * 处理SSE流中的文本增量
 * 清理特殊字符、转义序列等
 * 
 * 注意：不进行HTML实体转义，因为：
 * 1. React会自动处理XSS防护
 * 2. Markdown渲染器需要原始字符来解析格式
 */
export function processSSETextDelta(delta: string): string {
  if (!delta) return '';

  let processed = delta;

  processed = processed.replace(/\\u(?![0-9a-fA-F]{4})/g, '');
  processed = processed.replace(/\\$/, '');

  return processed;
}

/**
 * 解码转义字符
 * 将 \\n, \\t, \" 等转义序列转换为实际字符
 * 
 * 重要：替换顺序必须正确
 * 1. 先处理双反斜杠 \\\\ -> 临时占位符
 * 2. 再处理其他转义序列
 * 3. 最后将占位符还原为单反斜杠
 */
export function decodeEscapes(text: string): string {
  if (!text) return text;

  const BACKSLASH_PLACEHOLDER = '\x00BACKSLASH\x00';
  
  let result = text;
  
  result = result.replace(/\\\\/g, BACKSLASH_PLACEHOLDER);
  
  result = result
    .replace(/\\n/g, '\n')
    .replace(/\\t/g, '\t')
    .replace(/\\r/g, '\r')
    .replace(/\\"/g, '"')
    .replace(/\\'/g, "'");
  
  result = result.replace(new RegExp(BACKSLASH_PLACEHOLDER, 'g'), '\\');
  
  return result;
}

/**
 * 处理AI返回的完整内容
 * 综合应用各种清理和格式化
 */
export function processAIContent(content: string): string {
  if (!content) return content;

  let processed = content;

  // 解码转义字符
  processed = decodeEscapes(processed);

  // 处理可能的JSON包裹
  if (isJsonObject(processed)) {
    try {
      const parsed = JSON.parse(processed);
      if (typeof parsed === 'string') {
        processed = parsed;
      } else if (parsed.content || parsed.text || parsed.message) {
        processed = parsed.content || parsed.text || parsed.message;
      }
    } catch {
      // 解析失败，保留原内容
    }
  }

  // 清理代码块标记（但保留代码块内容）
  // 注意：这里不应该完全移除代码块，而是让MarkdownRenderer处理

  return processed;
}

/**
 * 检查字符串是否为JSON对象
 */
export function isJsonObject(str: string): boolean {
  const trimmed = str.trim();
  const stripped = stripMarkdownCodeBlock(trimmed);
  if (stripped.includes("refactoredCode")) {
    try {
      let toCheck = stripped;
      if (toCheck.startsWith('"') && toCheck.endsWith('"')) {
        toCheck = toCheck.slice(1, -1).replace(/\\"/g, '"');
      }
      if (toCheck.includes("{") && toCheck.includes("}")) {
        const start = toCheck.indexOf("{");
        const end = toCheck.lastIndexOf("}") + 1;
        toCheck = toCheck.substring(start, end);
      }
      JSON.parse(toCheck);
      return true;
    } catch {
      return false;
    }
  }
  return false;
}

/**
 * 检查文本是否包含中文字符
 */
export function containsChinese(text: string): boolean {
  if (!text) return false;
  return /[\u4e00-\u9fa5]/.test(text);
}

/**
 * 处理流式输出的文本增量
 * 确保内容正确解析，避免双重转义问题
 * 
 * @param delta 原始文本增量
 * @returns 处理后的文本
 */
export function processStreamDelta(delta: string): string {
  if (!delta) return delta;
  
  let processed = ensureUtf8Encoding(delta);
  
  if (shouldDecodeEscapes(processed)) {
    processed = decodeEscapes(processed);
  }
  
  return processed;
}

/**
 * 判断是否需要对文本进行转义解码
 * 避免对已经是正确格式的文本进行重复解码
 */
function shouldDecodeEscapes(text: string): boolean {
  if (!text) return false;
  
  const hasEscapeSequences = /\\[nrt"'\\]/.test(text);
  
  const looksLikeRawText = /^[^\\]*[\u4e00-\u9fa5a-zA-Z0-9\s\p{P}]+$/u.test(text);
  
  return hasEscapeSequences && !looksLikeRawText;
}

export function preprocessStreamingMarkdown(content: string): string {
  if (!content) return content;
  let result = content;

  const codeBlockMatches = result.match(/```/g);
  if (codeBlockMatches && codeBlockMatches.length % 2 !== 0) {
    result += '\n```';
  }

  const inlineCodeMatches = result.match(/`/g);
  if (inlineCodeMatches && inlineCodeMatches.length % 2 !== 0) {
    result += '`';
  }

  const lines = result.split('\n');
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    
    const boldCount = (line.match(/\*\*/g) || []).length;
    if (boldCount % 2 !== 0) {
      lines[i] = line + '**';
      continue;
    }
    
    const italicMatches = line.match(/(?<!\*)\*(?!\*)/g);
    if (italicMatches && italicMatches.length % 2 !== 0) {
      lines[i] = lines[i] + '*';
      continue;
    }
    
    const strikethroughMatches = line.match(/~~/g);
    if (strikethroughMatches && strikethroughMatches.length % 2 !== 0) {
      lines[i] = lines[i] + '~~';
      continue;
    }
    
    const linkMatches = line.match(/\[([^\]]*)\]\(/g);
    const closeParenMatches = line.match(/\)\s*$/g);
    if (linkMatches && linkMatches.length > 0) {
      const openParens = (line.match(/\(/g) || []).length;
      const closeParens = (line.match(/\)/g) || []).length;
      if (openParens > closeParens) {
        lines[i] = line + ')';
      }
    }
  }
  result = lines.join('\n');

  const tableRows = result.split('\n').filter(line => line.trim().startsWith('|'));
  if (tableRows.length > 0) {
    const lastTableRow = tableRows[tableRows.length - 1];
    const pipeCount = (lastTableRow.match(/\|/g) || []).length;
    if (pipeCount % 2 === 0 && !lastTableRow.trim().endsWith('|')) {
      const lines2 = result.split('\n');
      const lastRowIdx = lines2.findIndex(l => l === lastTableRow);
      if (lastRowIdx !== -1) {
        lines2[lastRowIdx] = lastTableRow + ' |';
        result = lines2.join('\n');
      }
    }
  }

  return result;
}

export function ensureUtf8Encoding(text: string): string {
  if (!text) return text;

  let cleaned = text.replace(/\uFEFF/g, '');

  cleaned = cleaned.replace(/[\u200B-\u200D\uFEFF]/g, '');

  return cleaned;
}

/**
 * 内容完整性校验结果
 */
export interface ContentValidationResult {
  isValid: boolean;
  issues: string[];
  warnings: string[];
  fixedContent?: string;
}

/**
 * 校验流式输出内容的完整性
 * 检测并尝试修复常见的格式问题
 * 
 * @param content 待校验的内容
 * @param isStreaming 是否处于流式输出状态
 * @returns 校验结果
 */
export function validateStreamContent(
  content: string,
  isStreaming: boolean = false
): ContentValidationResult {
  const issues: string[] = [];
  const warnings: string[] = [];
  let fixedContent = content;

  if (!content || content.trim().length === 0) {
    return {
      isValid: true,
      issues: [],
      warnings: ['内容为空'],
      fixedContent: content,
    };
  }

  const codeBlockCount = (content.match(/```/g) || []).length;
  if (codeBlockCount % 2 !== 0) {
    if (!isStreaming) {
      issues.push('代码块未正确闭合');
      fixedContent = fixedContent + '\n```';
    } else {
      warnings.push('代码块正在输出中（未闭合）');
    }
  }

  const inlineCodeCount = (content.match(/`/g) || []).length;
  if (inlineCodeCount % 2 !== 0) {
    if (!isStreaming) {
      issues.push('行内代码未正确闭合');
      fixedContent = fixedContent + '`';
    } else {
      warnings.push('行内代码正在输出中');
    }
  }

  const boldCount = (content.match(/\*\*/g) || []).length;
  if (boldCount % 2 !== 0) {
    if (!isStreaming) {
      issues.push('粗体标记未正确闭合');
      fixedContent = fixedContent + '**';
    }
  }

  const truncatedEscapeMatch = content.match(/\\u(?![0-9a-fA-F]{4})/);
  if (truncatedEscapeMatch) {
    issues.push('存在截断的Unicode转义序列');
    fixedContent = fixedContent.replace(/\\u(?![0-9a-fA-F]{4})/g, '');
  }

  if (content.endsWith('\\') && !content.endsWith('\\\\')) {
    warnings.push('内容以单反斜杠结尾，可能存在转义问题');
    fixedContent = fixedContent.slice(0, -1);
  }

  const lines = content.split('\n');
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    
    if (line.includes('[') && !line.includes(']')) {
      const linkStartMatch = line.match(/\[[^\]]*$/);
      if (linkStartMatch && !isStreaming) {
        warnings.push(`第${i + 1}行：链接文本可能未完成`);
      }
    }

    if (line.trim().startsWith('|') && !line.trim().endsWith('|')) {
      const pipeCount = (line.match(/\|/g) || []).length;
      if (pipeCount > 1 && !isStreaming) {
        warnings.push(`第${i + 1}行：表格行可能未完成`);
      }
    }
  }

  return {
    isValid: issues.length === 0,
    issues,
    warnings,
    fixedContent: fixedContent !== content ? fixedContent : undefined,
  };
}

/**
 * 安全处理流式文本内容
 * 综合应用各种处理和校验
 * 
 * @param delta 文本增量
 * @param accumulatedContent 已累积的内容（用于上下文校验）
 * @returns 处理后的内容
 */
export function safeProcessStreamDelta(
  delta: string,
  accumulatedContent?: string
): string {
  if (!delta) return delta;

  let processed = processStreamDelta(delta);

  if (accumulatedContent) {
    const fullContent = accumulatedContent + processed;
    const validation = validateStreamContent(fullContent, true);
    
    if (validation.warnings.length > 0) {
      console.debug('[流式处理] 内容校验警告:', validation.warnings);
    }
  }

  return processed;
}
