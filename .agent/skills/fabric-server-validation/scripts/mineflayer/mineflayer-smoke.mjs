#!/usr/bin/env node
import mineflayer from 'mineflayer'

const host = process.env.MC_HOST ?? '127.0.0.1'
const port = Number(process.env.MC_PORT ?? '25565')
const username = process.env.MC_BOT_NAME ?? `CodexE2E_${process.pid}`
const version = process.env.MC_VERSION ?? '1.21.8'
const timeoutMs = Number(process.env.MC_TEST_TIMEOUT_MS ?? '15000')

function withTimeout(promise, label, ms = timeoutMs) {
  let timer
  return Promise.race([
    promise,
    new Promise((_, reject) => {
      timer = setTimeout(() => reject(new Error(`Timed out waiting for ${label} after ${ms}ms`)), ms)
    })
  ]).finally(() => clearTimeout(timer))
}

function once(bot, event) {
  return new Promise((resolve, reject) => {
    const cleanup = () => {
      bot.removeListener(event, onEvent)
      bot.removeListener('error', onError)
      bot.removeListener('kicked', onKicked)
    }
    const onEvent = (...args) => { cleanup(); resolve(args) }
    const onError = (err) => { cleanup(); reject(err) }
    const onKicked = (reason) => { cleanup(); reject(new Error(`Kicked before ${event}: ${String(reason)}`)) }
    bot.once(event, onEvent)
    bot.once('error', onError)
    bot.once('kicked', onKicked)
  })
}

const bot = mineflayer.createBot({ host, port, username, version })

try {
  await withTimeout(once(bot, 'spawn'), 'spawn')
  console.log(JSON.stringify({ ok: true, phase: 'spawn', username, version, position: bot.entity?.position }))

  // Add only assertions that genuinely require a real network client.
  // Prefer server-side setup/inspection through MCP Fabric in the surrounding Codex workflow.
} finally {
  try { bot.quit('test complete') } catch {}
}
