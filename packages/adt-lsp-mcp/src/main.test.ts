import { describe, expect, test } from 'bun:test'
// @ts-expect-error - workspace package without types yet
import { mcpTools } from '@openadt/adt-lsp-mcp-tools'
import { ADT_LSP_WORKFLOW_PROMPT, guidancePromptDefs } from './guidance/guidance'

describe('ADT LSP MCP Server', () => {
  test('mcpTools exports all 27 ADT tools', () => {
    expect(mcpTools.length).toBe(27)
    const toolNames = mcpTools.map((t: { name: string }) => t.name)
    expect(toolNames).toContain('adt_quick_search')
    expect(toolNames).toContain('adt_check_transport_lock')
    expect(toolNames).toContain('adt_create_transport')
    expect(toolNames).toContain('adt_assign_transport')
  })

  test('each tool has required MCP schema', () => {
    for (const tool of mcpTools) {
      expect(tool.name).toBeTruthy()
      expect(tool.description).toBeTruthy()
      expect(tool.inputSchema).toBeTruthy()
      expect(tool.handler).toBeInstanceOf(Function)
    }
  })

  test('tool names follow adt_ prefix convention', () => {
    for (const tool of mcpTools) {
      expect(tool.name).toMatch(/^adt_/)
    }
  })

  test('guidance prompt is registered for MCP prompts/list', () => {
    const defs = guidancePromptDefs()
    expect(defs.map((d) => d.name)).toContain(ADT_LSP_WORKFLOW_PROMPT)
  })

  test('transport LSP tools mention cts/transport in description', () => {
    const transportTools = mcpTools.filter((t: { name: string }) =>
      [
        'adt_create_transport',
        'adt_assign_transport',
        'adt_search_transports',
        'adt_search_transports_simple',
      ].includes(t.name)
    )
    for (const tool of transportTools) {
      expect(tool.description).toContain('adtLs/cts/transport')
    }
  })
})
