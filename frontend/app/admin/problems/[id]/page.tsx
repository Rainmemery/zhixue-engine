"use client";

import { useState, useEffect, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import { motion } from "framer-motion";
import { ArrowLeft, Save, Plus, Trash2, Eye, EyeOff } from "lucide-react";
import { ProblemAdminService, ProblemCategory, ProblemTag, CreateProblemRequest } from "@/services/problemAdminService";
import { MarkdownRenderer } from "@/components/chat/markdown-renderer";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { cn } from "@/lib/utils";

interface Sample {
  input: string;
  output: string;
  explanation: string;
}

function MarkdownField({ value, onChange, preview, onTogglePreview, label }: {
  value: string; onChange: (v: string) => void; preview: boolean; onTogglePreview: () => void; label: string;
}) {
  return (
    <div className="space-y-1">
      <div className="flex items-center justify-between">
        <Label className="text-sm font-medium">{label}</Label>
        <Button variant="ghost" size="sm" onClick={onTogglePreview} className="h-6 text-xs">
          {preview ? <><EyeOff className="w-3 h-3 mr-1" /> 编辑</> : <><Eye className="w-3 h-3 mr-1" /> 预览</>}
        </Button>
      </div>
      {preview ? (
        <div className="min-h-[100px] p-3 rounded-md border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-950">
          <MarkdownRenderer content={value} />
        </div>
      ) : (
        <Textarea value={value} onChange={e => onChange(e.target.value)} rows={6} className="font-mono text-sm" />
      )}
    </div>
  );
}

export default function ProblemEditPage() {
  const params = useParams();
  const router = useRouter();
  const isNew = params.id === "new";
  const problemId = !isNew ? Number(params.id) : null;

  const [saving, setSaving] = useState(false);
  const [categories, setCategories] = useState<ProblemCategory[]>([]);
  const [tags, setTags] = useState<ProblemTag[]>([]);

  const [title, setTitle] = useState("");
  const [titleEn, setTitleEn] = useState("");
  const [source, setSource] = useState("");
  const [difficulty, setDifficulty] = useState("medium");
  const [problemType, setProblemType] = useState("traditional");
  const [categoryId, setCategoryId] = useState("");
  const [selectedTagIds, setSelectedTagIds] = useState<number[]>([]);
  const [timeLimitMs, setTimeLimitMs] = useState(2000);
  const [memoryLimitMb, setMemoryLimitMb] = useState(256);
  const [isPublic, setIsPublic] = useState(true);

  const [description, setDescription] = useState("");
  const [inputDescription, setInputDescription] = useState("");
  const [outputDescription, setOutputDescription] = useState("");
  const [hint, setHint] = useState("");

  const [descPreview, setDescPreview] = useState(false);
  const [inputDescPreview, setInputDescPreview] = useState(false);
  const [outputDescPreview, setOutputDescPreview] = useState(false);
  const [hintPreview, setHintPreview] = useState(false);

  const [samples, setSamples] = useState<Sample[]>([{ input: "", output: "", explanation: "" }]);

  const [templateJava, setTemplateJava] = useState("");
  const [templateCpp, setTemplateCpp] = useState("");
  const [templateC, setTemplateC] = useState("");
  const [templateTab, setTemplateTab] = useState("java");

  useEffect(() => {
    ProblemAdminService.getCategories().then(setCategories).catch(console.error);
    ProblemAdminService.getTags().then(setTags).catch(console.error);
  }, []);

  useEffect(() => {
    if (!isNew && problemId) {
      ProblemAdminService.getProblemById(problemId).then((p: any) => {
        setTitle(p.title || "");
        setTitleEn(p.titleEn || "");
        setSource(p.source || "");
        setDifficulty(p.difficulty || "medium");
        setProblemType(p.problemType || "traditional");
        setCategoryId(String(p.categoryId || ""));
        setSelectedTagIds(p.tagIds || []);
        setTimeLimitMs(p.timeLimitMs || 2000);
        setMemoryLimitMb(p.memoryLimitMb || 256);
        setIsPublic(p.isPublic !== false);
        setDescription(p.description || "");
        setInputDescription(p.inputDescription || "");
        setOutputDescription(p.outputDescription || "");
        setHint(p.hint || "");
        if (p.samples?.length) {
          setSamples(p.samples.map((s: any) => ({
            input: s.input || s.sampleInput || "",
            output: s.output || s.sampleOutput || "",
            explanation: s.explanation || "",
          })));
        }
        const tc = p.templateCode || {};
        setTemplateJava(tc.java || "");
        setTemplateCpp(tc.cpp || "");
        setTemplateC(tc.c || "");
      }).catch(console.error);
    }
  }, [isNew, problemId]);

  const addSample = useCallback(() => setSamples(prev => [...prev, { input: "", output: "", explanation: "" }]), []);
  const removeSample = useCallback((i: number) => setSamples(prev => prev.filter((_, idx) => idx !== i)), []);
  const updateSample = useCallback((i: number, field: keyof Sample, value: string) => {
    setSamples(prev => {
      const updated = [...prev];
      updated[i] = { ...updated[i], [field]: value };
      return updated;
    });
  }, []);

  const toggleTag = useCallback((tagId: number) => {
    setSelectedTagIds(prev =>
      prev.includes(tagId) ? prev.filter(id => id !== tagId) : [...prev, tagId]
    );
  }, []);

  const handleSave = useCallback(async () => {
    if (!title.trim()) return;
    setSaving(true);
    try {
      const data: CreateProblemRequest = {
        title,
        titleEn: titleEn || undefined,
        source: source || undefined,
        difficulty: difficulty as any,
        problemType: problemType as any,
        categoryId: categoryId ? Number(categoryId) : undefined,
        tagIds: selectedTagIds,
        timeLimitMs,
        memoryLimitMb,
        isPublic,
        description,
        inputDescription: inputDescription || undefined,
        outputDescription: outputDescription || undefined,
        hint: hint || undefined,
        samples: samples.filter(s => s.input.trim() || s.output.trim()),
        templateCode: {
          ...(templateJava ? { java: templateJava } : {}),
          ...(templateCpp ? { cpp: templateCpp } : {}),
          ...(templateC ? { c: templateC } : {}),
        },
      };
      if (isNew) {
        await ProblemAdminService.createProblem(data);
      } else if (problemId) {
        await ProblemAdminService.updateProblem(problemId, data);
      }
      router.push("/admin/problems");
    } catch (err) {
      console.error("Save failed:", err);
    } finally {
      setSaving(false);
    }
  }, [title, titleEn, source, difficulty, problemType, categoryId, selectedTagIds, timeLimitMs, memoryLimitMb, isPublic, description, inputDescription, outputDescription, hint, samples, templateJava, templateCpp, templateC, isNew, problemId, router]);

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="p-6 space-y-6 max-w-5xl mx-auto">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="sm" onClick={() => router.push("/admin/problems")}>
            <ArrowLeft className="w-4 h-4 mr-1" /> 返回
          </Button>
          <h1 className="text-xl font-bold">{isNew ? "新建题目" : `编辑题目 #${problemId}`}</h1>
        </div>
        <Button onClick={handleSave} disabled={saving || !title.trim()}>
          <Save className="w-4 h-4 mr-1" /> {saving ? "保存中..." : "保存"}
        </Button>
      </div>

      <Tabs defaultValue="basic">
        <TabsList>
          <TabsTrigger value="basic">基本信息</TabsTrigger>
          <TabsTrigger value="description">题目描述</TabsTrigger>
          <TabsTrigger value="samples">样例</TabsTrigger>
          <TabsTrigger value="template">代码模板</TabsTrigger>
        </TabsList>

        <TabsContent value="basic" className="space-y-4 mt-4">
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1">
              <Label className="text-sm font-medium">题目标题 *</Label>
              <Input value={title} onChange={e => setTitle(e.target.value)} placeholder="例：两数之和" />
            </div>
            <div className="space-y-1">
              <Label className="text-sm font-medium">英文标题</Label>
              <Input value={titleEn} onChange={e => setTitleEn(e.target.value)} placeholder="Two Sum" />
            </div>
          </div>

          <div className="grid grid-cols-3 gap-4">
            <div className="space-y-1">
              <Label className="text-sm font-medium">难度</Label>
              <Select value={difficulty} onValueChange={setDifficulty}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="easy">简单</SelectItem>
                  <SelectItem value="medium">中等</SelectItem>
                  <SelectItem value="hard">困难</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1">
              <Label className="text-sm font-medium">类型</Label>
              <Select value={problemType} onValueChange={setProblemType}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="traditional">传统题</SelectItem>
                  <SelectItem value="interactive">交互题</SelectItem>
                  <SelectItem value="special_judge">特殊评判</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1">
              <Label className="text-sm font-medium">分类</Label>
              <Select value={categoryId} onValueChange={setCategoryId}>
                <SelectTrigger><SelectValue placeholder="选择分类" /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="none">无分类</SelectItem>
                  {categories.map(c => (
                    <SelectItem key={c.id} value={String(c.id)}>{c.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          <div className="grid grid-cols-3 gap-4">
            <div className="space-y-1">
              <Label className="text-sm font-medium">时间限制 (ms)</Label>
              <Input type="number" value={timeLimitMs} onChange={e => setTimeLimitMs(Number(e.target.value))} />
            </div>
            <div className="space-y-1">
              <Label className="text-sm font-medium">内存限制 (MB)</Label>
              <Input type="number" value={memoryLimitMb} onChange={e => setMemoryLimitMb(Number(e.target.value))} />
            </div>
            <div className="space-y-1">
              <Label className="text-sm font-medium">来源</Label>
              <Input value={source} onChange={e => setSource(e.target.value)} placeholder="例：LeetCode" />
            </div>
          </div>

          <div className="space-y-1">
            <Label className="text-sm font-medium">标签</Label>
            <div className="flex flex-wrap gap-2">
              {tags.map(tag => (
                <button
                  key={tag.id}
                  onClick={() => toggleTag(tag.id)}
                  className={cn(
                    "px-2.5 py-1 rounded-full text-xs border transition-colors",
                    selectedTagIds.includes(tag.id)
                      ? "bg-primary text-primary-foreground border-primary"
                      : "bg-gray-100 dark:bg-gray-800 text-muted-foreground border-gray-200 dark:border-gray-700 hover:border-primary"
                  )}
                >
                  {tag.name}
                </button>
              ))}
            </div>
          </div>

          <div className="flex items-center gap-2">
            <Label className="text-sm font-medium">公开</Label>
            <button
              onClick={() => setIsPublic(!isPublic)}
              className={cn(
                "w-10 h-5 rounded-full transition-colors relative",
                isPublic ? "bg-primary" : "bg-gray-300 dark:bg-gray-600"
              )}
            >
              <span className={cn(
                "absolute top-0.5 w-4 h-4 rounded-full bg-white transition-transform",
                isPublic ? "left-5" : "left-0.5"
              )} />
            </button>
          </div>
        </TabsContent>

        <TabsContent value="description" className="space-y-4 mt-4">
          <MarkdownField label="题目描述" value={description} onChange={setDescription} preview={descPreview} onTogglePreview={() => setDescPreview(!descPreview)} />
          <MarkdownField label="输入描述" value={inputDescription} onChange={setInputDescription} preview={inputDescPreview} onTogglePreview={() => setInputDescPreview(!inputDescPreview)} />
          <MarkdownField label="输出描述" value={outputDescription} onChange={setOutputDescription} preview={outputDescPreview} onTogglePreview={() => setOutputDescPreview(!outputDescPreview)} />
          <MarkdownField label="提示" value={hint} onChange={setHint} preview={hintPreview} onTogglePreview={() => setHintPreview(!hintPreview)} />
        </TabsContent>

        <TabsContent value="samples" className="space-y-4 mt-4">
          <div className="flex items-center justify-between">
            <Label className="text-sm font-medium">样例</Label>
            <Button variant="outline" size="sm" onClick={addSample}>
              <Plus className="w-3 h-3 mr-1" /> 添加样例
            </Button>
          </div>
          {samples.map((sample, i) => (
            <div key={i} className="rounded-lg border border-gray-200 dark:border-gray-700 p-4 space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium">样例 {i + 1}</span>
                {samples.length > 1 && (
                  <Button variant="ghost" size="sm" onClick={() => removeSample(i)} className="h-6 text-red-500 hover:text-red-600">
                    <Trash2 className="w-3 h-3" />
                  </Button>
                )}
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1">
                  <Label className="text-xs">输入</Label>
                  <Textarea value={sample.input} onChange={e => updateSample(i, "input", e.target.value)} rows={3} className="font-mono text-xs" />
                </div>
                <div className="space-y-1">
                  <Label className="text-xs">输出</Label>
                  <Textarea value={sample.output} onChange={e => updateSample(i, "output", e.target.value)} rows={3} className="font-mono text-xs" />
                </div>
              </div>
              <div className="space-y-1">
                <Label className="text-xs">解释（可选）</Label>
                <Textarea value={sample.explanation} onChange={e => updateSample(i, "explanation", e.target.value)} rows={2} className="text-xs" />
              </div>
            </div>
          ))}
        </TabsContent>

        <TabsContent value="template" className="space-y-4 mt-4">
          <Label className="text-sm font-medium">代码模板（按语言编辑）</Label>
          <Tabs value={templateTab} onValueChange={setTemplateTab}>
            <TabsList>
              <TabsTrigger value="java">Java</TabsTrigger>
              <TabsTrigger value="cpp">C++</TabsTrigger>
              <TabsTrigger value="c">C</TabsTrigger>
            </TabsList>
            <TabsContent value="java">
              <Textarea value={templateJava} onChange={e => setTemplateJava(e.target.value)} rows={12} className="font-mono text-sm mt-2" placeholder="public class Main { ... }" />
            </TabsContent>
            <TabsContent value="cpp">
              <Textarea value={templateCpp} onChange={e => setTemplateCpp(e.target.value)} rows={12} className="font-mono text-sm mt-2" placeholder="#include &lt;iostream&gt; ..." />
            </TabsContent>
            <TabsContent value="c">
              <Textarea value={templateC} onChange={e => setTemplateC(e.target.value)} rows={12} className="font-mono text-sm mt-2" placeholder="#include &lt;stdio.h&gt; ..." />
            </TabsContent>
          </Tabs>
        </TabsContent>
      </Tabs>
    </motion.div>
  );
}
