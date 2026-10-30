# 导入与导出

## JSON 结构

`schemaVersion = 1`。导入时先读原始对象检查 `schemaVersion`，缺省或高于当前版本会给出明确错误。

```json
{
  "schemaVersion": 1,
  "app": "Count",
  "exportedAt": "2026-10-30T00:00:00Z",
  "counters": [
    {
      "id": "0b4c...",
      "name": "喝水",
      "initialValue": 0,
      "allowNegative": false,
      "createdAt": "2026-10-01T08:00:00Z",
      "sortOrder": 0,
      "pinned": true,
      "defaultStep": 1,
      "tagIds": ["tag-id"],
      "events": [
        {
          "id": "e1...",
          "delta": 1,
          "timestamp": "2026-10-30T02:00:00Z",
          "note": "早上",
          "source": "MANUAL",
          "sessionId": null
        }
      ]
    }
  ],
  "tags": [{ "id": "tag-id", "name": "健康", "createdAt": "2026-10-01T08:00:00Z" }]
}
```

- `events` 嵌在各自计数器下，不是顶层平铺数组。
- 时间使用 ISO-8601 UTC（`Z`）或带偏移量的 `Instant` 字符串。
- `source` 缺省为 `MANUAL`；未知来源会回退为 `MANUAL` 并给出提示。
- `id` 建议使用 UUID；示例数据使用稳定字符串 id，方便重复导入时去重。

## 校验

- `schemaVersion` 必须存在且不大于当前版本。
- 计数器 id 必须唯一、名称非空；初始值、步长必须在允许范围内。
- `allowNegative=false` 时初始值不能为负。
- 事件 delta 不能为 0；时间戳必须可解析；备注不能超长。
- `tagIds` 必须指向文件或数据库中存在的标签。
- 任一结构错误会整体拒绝，不产生部分写入。

以下情况会被跳过并在导入结果中提示：

- 零 delta 事件；
- 重复事件 id；
- 未知来源（回退为 `MANUAL`）；
- 未知标签；
- 重复标签 id。

## 合并

- 计数器按 id 匹配。
- 已有计数器只取事件并集，不覆盖名称、初始值、步长、置顶与排序。
- 新计数器追加到普通区末尾，保留文件中的初始值、步长、置顶与标签。
- 标签取并集，全部保留。
- 事件按 id 去重；事件 id 缺失时用 `(counterId, timestampMillis, delta)` 复合键。
- 重复事件数量会在结果中提示。

## 替换

- 二次确认后清空全部计数器、事件、标签与关联，再写入文件内容。
- 保留文件中的 id、排序、置顶与标签。
- 适合迁移到新设备，或把一台设备恢复成备份时的状态。

## 负值冲突

如果导入后某个 `allowNegative=false` 的计数器会在历史上小于 0，导入对话框会列出受影响的计数器，并让用户选择：

- **跳过这些计数器**：不写入这些计数器的数据；
- **允许负值**：把这些计数器改为 `allowNegative=true` 再导入，以保持「不允许负值 ⇒ 值 ≥ 0」这一不变量。

## CSV

CSV 只导出，不导入。表头固定为：

```
counterId,counterName,eventId,timestamp,delta,source,note
```

用于在表格软件里查看事件；字段中的逗号、引号和换行会按 RFC 4180 转义。

## 示例数据

[`samples/long-term-demo.json`](../samples/long-term-demo.json) 是一份合成的长期使用数据：

- 一个置顶计数器「阅读」，约 14 个月、数百条 `+1` 事件；
- 两个标签「习惯」「阅读」；
- 事件时间按本地晚间/早晨分布，包含少量休息日和长假空档，因此热力图有自然的深浅变化；
- 全部为合成数据，不含任何真实用户信息。

导入步骤：

1. 把 JSON 放到手机可访问的位置（下载目录或通过文件管理器）。
2. 打开 Count → 设置 → 导入 JSON。
3. 选择文件 → **合并**（推荐，保留现有数据；重复导入不会重复计数）。
4. 回到主页，打开「阅读」详情即可看到一年以上的热力图。

如果只想看示例效果，也可以先用「替换」导入一台空设备；替换会清空当前数据，请先导出备份。
