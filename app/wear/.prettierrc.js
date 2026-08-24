module.exports = {
  printWidth: 100, // 指定代码长度，超出换行
  tabWidth: 2, // tab 键的宽度
  useTabs: false, // 使用空格替代tab
  semi: false, // 结尾加上分号
  singleQuote: false, // 使用单引号
  quoteProps: "consistent", // 要求对象字面量属性是否使用引号包裹,(‘as-needed’: 没有特殊要求，禁止使用，'consistent': 保持一致 , preserve: 不限制，想用就用)
  trailingComma: "none", // 不添加对象和数组最后一个元素的逗号
  bracketSpacing: false, // 对象中对空格和空行进行处理
  jsxBracketSameLine: false, // 在多行JSX元素的最后一行追加 >
  requirePragma: false, // 是否严格按照文件顶部的特殊注释格式化代码
  insertPragma: false, // 是否在格式化的文件顶部插入Pragma标记，以表明该文件被prettier格式化过了
  proseWrap: "preserve", // 按照文件原样折行
  htmlWhitespaceSensitivity: "ignore", // html文件的空格敏感度，控制空格是否影响布局
  endOfLine: "auto", // 结尾是 \n \r \n\r auto
  overrides: [
    {
      files: "*.ux",
      options: {parser: "vue"}
    }
  ]
}
