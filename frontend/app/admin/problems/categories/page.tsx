"use client";

import { useState, useEffect, useCallback } from "react";
import { motion } from "framer-motion";
import { Plus, Pencil, Trash2 } from "lucide-react";
import { ProblemAdminService, ProblemCategory } from "@/services/problemAdminService";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export default function CategoriesManagementPage() {
  const [categories, setCategories] = useState<ProblemCategory[]>([]);
  const [loading, setLoading] = useState(true);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [formName, setFormName] = useState("");
  const [formNameEn, setFormNameEn] = useState("");
  const [formDescription, setFormDescription] = useState("");
  const [formIcon, setFormIcon] = useState("");
  const [formParentId, setFormParentId] = useState("");
  const [formSortOrder, setFormSortOrder] = useState(0);

  const fetchCategories = useCallback(async () => {
    try {
      setLoading(true);
      const data = await ProblemAdminService.getCategories();
      setCategories(data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchCategories(); }, [fetchCategories]);

  const resetForm = () => {
    setEditingId(null);
    setFormName("");
    setFormNameEn("");
    setFormDescription("");
    setFormIcon("");
    setFormParentId("");
    setFormSortOrder(0);
  };

  const handleEdit = (cat: ProblemCategory) => {
    setEditingId(cat.id);
    setFormName(cat.name);
    setFormNameEn(cat.nameEn || "");
    setFormDescription(cat.description || "");
    setFormIcon(cat.icon || "");
    setFormParentId(cat.parentId ? String(cat.parentId) : "");
    setFormSortOrder(cat.sortOrder || 0);
  };

  const handleSave = async () => {
    if (!formName.trim()) return;
    try {
      const data: Partial<ProblemCategory> = {
        name: formName,
        nameEn: formNameEn || undefined,
        description: formDescription || undefined,
        icon: formIcon || undefined,
        parentId: formParentId ? Number(formParentId) : undefined,
        sortOrder: formSortOrder,
      };
      if (editingId) {
        await ProblemAdminService.updateCategory(editingId, data);
      } else {
        await ProblemAdminService.createCategory(data);
      }
      resetForm();
      fetchCategories();
    } catch (err) {
      console.error(err);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await ProblemAdminService.deleteCategory(id);
      fetchCategories();
    } catch (err) {
      console.error(err);
    }
  };

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="p-6 space-y-6">
      <h1 className="text-2xl font-bold">分类管理</h1>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">{editingId ? "编辑分类" : "新建分类"}</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1">
              <Label className="text-xs">名称 *</Label>
              <Input value={formName} onChange={e => setFormName(e.target.value)} placeholder="分类名称" />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">英文名</Label>
              <Input value={formNameEn} onChange={e => setFormNameEn(e.target.value)} placeholder="Category Name" />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">描述</Label>
              <Textarea value={formDescription} onChange={e => setFormDescription(e.target.value)} rows={2} />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">图标 URL</Label>
              <Input value={formIcon} onChange={e => setFormIcon(e.target.value)} placeholder="https://..." />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">父分类</Label>
              <select
                value={formParentId}
                onChange={e => setFormParentId(e.target.value)}
                className="w-full h-9 rounded-md border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-950 px-3 text-sm"
              >
                <option value="">无（顶级分类）</option>
                {categories.filter(c => c.id !== editingId).map(c => (
                  <option key={c.id} value={c.id}>{c.name}</option>
                ))}
              </select>
            </div>
            <div className="space-y-1">
              <Label className="text-xs">排序</Label>
              <Input type="number" value={formSortOrder} onChange={e => setFormSortOrder(Number(e.target.value))} />
            </div>
          </div>
          <div className="flex gap-2 mt-4">
            <Button onClick={handleSave} disabled={!formName.trim()}>{editingId ? "更新" : "创建"}</Button>
            {editingId && <Button variant="outline" onClick={resetForm}>取消</Button>}
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
                <th className="px-4 py-2 text-left font-medium">描述</th>
                <th className="px-4 py-2 text-left font-medium">父分类</th>
                <th className="px-4 py-2 text-left font-medium">排序</th>
                <th className="px-4 py-2 text-left font-medium">题目数</th>
                <th className="px-4 py-2 text-right font-medium">操作</th>
              </tr>
            </thead>
            <tbody>
              {categories.map(cat => (
                <tr key={cat.id} className="border-t border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800/50">
                  <td className="px-4 py-2">{cat.id}</td>
                  <td className="px-4 py-2 font-medium">{cat.name}</td>
                  <td className="px-4 py-2 text-muted-foreground">{cat.nameEn || "-"}</td>
                  <td className="px-4 py-2 text-muted-foreground max-w-[200px] truncate">{cat.description || "-"}</td>
                  <td className="px-4 py-2 text-muted-foreground">{cat.parentId || "-"}</td>
                  <td className="px-4 py-2">{cat.sortOrder ?? 0}</td>
                  <td className="px-4 py-2">{cat.problemCount ?? 0}</td>
                  <td className="px-4 py-2 text-right">
                    <Button variant="ghost" size="sm" onClick={() => handleEdit(cat)}><Pencil className="w-3 h-3" /></Button>
                    <Button variant="ghost" size="sm" onClick={() => handleDelete(cat.id)} className="text-red-500"><Trash2 className="w-3 h-3" /></Button>
                  </td>
                </tr>
              ))}
              {categories.length === 0 && (
                <tr><td colSpan={8} className="px-4 py-8 text-center text-muted-foreground">暂无分类</td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </motion.div>
  );
}
