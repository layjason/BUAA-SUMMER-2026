export type I18nTemplateValue = string | number | boolean | null | undefined

/**
 * 格式化 i18n 命名占位符模板。
 *
 * 前置条件：template 为已经从 i18n 资源中取出的文案模板；values 为可序列化的简单值。
 * 后置条件：模板中存在于 values 的 `{key}` 占位符会被替换为对应字符串。
 * 不变量：未知占位符保持原样，避免资源缺失时隐藏文案配置问题。
 */
export function formatI18nTemplate(
  template: string,
  values: Record<string, I18nTemplateValue>,
): string {
  return template.replace(/\{([A-Za-z0-9_]+)\}/g, (placeholder, key: string) => {
    const value = values[key]
    return value === null || value === undefined ? placeholder : String(value)
  })
}
