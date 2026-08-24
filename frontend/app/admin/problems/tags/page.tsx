"use client";

import { useState, useEffect, useCallback } from "react";
import { motion } from "framer-motion";
import { Plus, Pencil, Trash2 } from "lucide-react";
import { ProblemAdminService, ProblemTag } from "@/services/problemAdminService";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export default function TagsManagementPage() {
  const [tags, setTags] = useState<ProblemTag[]>([]);
  const [loading, setLoading] = useState(true);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [formName, setFormName] = useState("");
  const [formNameEn, setFormNameEn] = useState("");
  const [formColor, setFormColor] = useState("#3b82f6");

  const fetchTags = useCallback(async () => {
    try {
      setLoading(true);
      const data = await ProblemAdminService.getTags();
      setTags(data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchTags(); }, [fetchTags]);

  const resetForm = () => {
    setEditingId(null);
    setFormName("");
    setFormNameEn("");
    setFormColor("#3b82f6");
  };

  const handleEdit = (tag: ProblemTag) => {
    setEditingId(tag.id);
    setFormName(tag.name);
    setFormNameEn(tag.nameEn || "");
    setFormColor(tag.color || "#3b82f6");
  };

  const handleSave = async () => {
    if (!formName.trim()) return;
    try {
      if (editingId) {
        await ProblemAdminService.updateTag(editingId, { name: formName, nameEn: formNameEn || undefined, color: formColor });
      } else {
        await ProblemAdminService.createTag({ name: formName, nameEn: formNameEn || undefined, color: formColor });
      }
      resetForm();
      fetchTags();
    } catch (err) {
      console.error(err);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await ProblemAdminService.deleteTag(id);
      fetchTags();
    } catch (err) {
      console.error(err);
    }
  };

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="p-6 space-y-6">
      <h1 className="text-2xl font-bold">标签管理</h1>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">{editingId ? "编辑标签" : "新建标签"}</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-4 gap-4">
            <div className="space-y-1">
              <Label className="text-xs">名称 *</Label>
              <Input value={formName} onChange={e => setFormName(e.target.value)} placeholder="标签名称" />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">英文名</Label>
              <Input value={formNameEn} onChange={e => setFormNameEn(e.target.value)} placeholder="Tag Name" />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">颜色</Label>
              <div className="flex items-center gap-2">
                <input type="color" value={formColor} onChange={e => setFormColor(e.target.value)} className="w-8 h-8 rounded cursor-pointer" />
                <Input value={formColor} onChange={e => setFormColor(e.target.value)} className="flex-1" />
              </div>
            </div>
            <div className="flex items-end gap-2">
              <Button onClick={handleSave} disabled={!formName.trim()}>{editingId ? "更新" : "创建"}</Button>
              {editingId && <Button variant="outline" onClick={resetForm}>取消</Button>}
            </div>
          </div>
        </CardContent>
      </Card>

      {loading ? (
        <div className="flex justify-center py-8"><div className="animate-spin rounded-full h-6 w-6 border-b-2 border-primary" /></div>
      ) : (
        <div className="rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 dark:bg-gray-800">
              <tr>
                <th className="px-4 py-2 text-left font-medium">ID</th>
                <th className="px-4 py-2 text-left font-medium">名称</th>
                <th className="px-4 py-2 text-left font-medium">英文名</th>
                <th className="px-4 py-2 text-left font-medium">颜色</th>
                <th className="px-4 py-2 text-left font-medium">题目数</th>
                <th className="px-4 py-2 text-right font-medium">操作</th>
              </tr>
            </thead>
            <tbody>
              {tags.map(tag => (
                <tr key={tag.id} className="border-t border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800/50">
                  <td className="px-4 py-2">{tag.id}</td>
                  <td className="px-4 py-2">
                    <span className="inline-flex items-center gap-1.5">
                      <span className="w-3 h-3 rounded-full" style={{ backgroundColor: tag.color || "#3b82f6" }} />
                      {tag.name}
                    </span>
                  </td>
                  <td className="px-4 py-2 text-muted-foreground">{tag.nameEn || "-"}</td>
                  <td className="px-4 py-2 font-mono text-xs">{tag.color || "-"}</td>
                  <td className="px-4 py-2">{tag.problemCount ?? 0}</td>
                  <td className="px-4 py-2 text-right">
                    <Button variant="ghost" size="sm" onClick={() => handleEdit(tag)}><Pencil className="w-3 h-3" /></Button>
                    <Button variant="ghost" size="sm" onClick={() => handleDelete(tag.id)} className="text-red-500"><Trash2 className="w-3 h-3" /></Button>
                  </td>
                </tr>
              ))}
              {tags.length === 0 && (
                <tr><td colSpan={6} className="px-4 py-8 text-center text-muted-foreground">暂无标签</td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </motion.div>
  );
}
