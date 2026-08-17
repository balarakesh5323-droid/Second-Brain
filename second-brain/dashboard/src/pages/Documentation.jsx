import { useState } from 'react';
import { BookOpen, ChevronRight, Copy, Check } from 'lucide-react';

const sections = [
  { id: 'getting-started', title: 'Getting Started' },
  { id: 'architecture', title: 'Architecture' },
  { id: 'mcp-setup', title: 'Connect with Agents (MCP)' },
  { id: 'mcp-tools', title: 'MCP Tools Reference' },
  { id: 'api-reference', title: 'REST API Reference' },
  { id: 'examples', title: 'Agent Integration Examples' },
  { id: 'configuration', title: 'Configuration' },
];

function CodeBlock({ code, title }) {
  const [copied, setCopied] = useState(false);

  const copy = () => {
    navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="rounded-lg overflow-hidden border border-gray-800/60 my-3">
      {title && (
        <div className="flex items-center justify-between bg-gray-800/80 px-4 py-2 text-xs text-gray-400 border-b border-gray-800/60">
          <span className="truncate">{title}</span>
          <button onClick={copy} className="shrink-0 ml-3 flex items-center gap-1 hover:text-gray-200 transition-colors">
            {copied ? <Check className="w-3.5 h-3.5 text-green-400" /> : <Copy className="w-3.5 h-3.5" />}
            {copied ? 'Copied' : 'Copy'}
          </button>
        </div>
      )}
      <pre className="bg-gray-950 p-4 overflow-x-auto text-[13px] leading-relaxed">
        <code className="text-gray-300 whitespace-pre">{code}</code>
      </pre>
    </div>
  );
}

function Section({ id, title, children }) {
  return (
    <section id={id} className="scroll-mt-8">
      <h2 className="text-lg font-bold text-white mb-3 flex items-center gap-2 border-b border-gray-800/60 pb-2">
        <ChevronRight className="w-4 h-4 text-purple-400 shrink-0" />
        {title}
      </h2>
      <div className="space-y-3 text-gray-300 text-[13px] leading-relaxed">
        {children}
      </div>
    </section>
  );
}

function InfoBox({ type = 'info', children }) {
  const styles = {
    info: 'border-blue-500/20 bg-blue-500/5 text-blue-300',
    warning: 'border-yellow-500/20 bg-yellow-500/5 text-yellow-300',
    success: 'border-green-500/20 bg-green-500/5 text-green-300',
  };
  return (
    <div className={`rounded-lg border p-3 text-xs leading-relaxed ${styles[type]}`}>
      {children}
    </div>
  );
}

function ToolCard({ tool }) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="bg-gray-900/60 border border-gray-800/60 rounded-lg overflow-hidden">
      <button
        onClick={() => setExpanded(!expanded)}
        className="w-full flex items-center gap-3 px-4 py-3 text-left hover:bg-gray-800/30 transition-colors"
      >
        <code className="text-purple-400 font-mono text-xs font-semibold shrink-0">{tool.name}</code>
        <span className="text-gray-500 text-[11px] hidden sm:inline">|</span>
        <span className="text-gray-400 text-xs truncate">{tool.desc}</span>
        <ChevronRight className={`w-3.5 h-3.5 text-gray-500 ml-auto shrink-0 transition-transform ${expanded ? 'rotate-90' : ''}`} />
      </button>
      {expanded && (
        <div className="px-4 pb-3 border-t border-gray-800/40">
          <p className="text-gray-500 text-[11px] mt-2 font-mono">Params: {tool.params}</p>
          <CodeBlock code={tool.example} title="Example" />
        </div>
      )}
    </div>
  );
}

function MethodBadge({ method }) {
  const colors = {
    GET: 'bg-green-500/15 text-green-400 border-green-500/20',
    POST: 'bg-blue-500/15 text-blue-400 border-blue-500/20',
    PUT: 'bg-amber-500/15 text-amber-400 border-amber-500/20',
    DELETE: 'bg-red-500/15 text-red-400 border-red-500/20',
  };
  return (
    <span className={`inline-block px-1.5 py-0.5 rounded text-[10px] font-bold border ${colors[method]} w-12 text-center shrink-0`}>
      {method}
    </span>
  );
}

export default function Documentation() {
  const [activeSection, setActiveSection] = useState('getting-started');

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="flex items-center gap-3">
        <div className="p-2 rounded-lg bg-purple-600/10">
          <BookOpen className="w-6 h-6 text-purple-400" />
        </div>
        <div>
          <h1 className="text-2xl font-bold">Documentation</h1>
          <p className="text-gray-500 text-xs mt-0.5">Connect your agents to a persistent memory system</p>
        </div>
      </div>

      {/* TOC pills */}
      <div className="flex flex-wrap gap-1.5">
        {sections.map((s) => (
          <button
            key={s.id}
            onClick={() => {
              setActiveSection(s.id);
              document.getElementById(s.id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }}
            className={`px-3 py-1.5 rounded-full text-xs font-medium transition-all ${
              activeSection === s.id
                ? 'bg-purple-600/20 text-purple-300 ring-1 ring-purple-500/30'
                : 'bg-gray-800/50 text-gray-400 hover:bg-gray-800 hover:text-gray-200'
            }`}
          >
            {s.title}
          </button>
        ))}
      </div>

      {/* Content */}
      <div className="space-y-10 min-w-0">

        {/* ── Getting Started ── */}
        <Section id="getting-started" title="Getting Started">
          <p>Second Brain is a memory-as-a-service platform for AI agents. It gives your agents persistent memory across sessions.</p>

          <h3 className="text-white font-semibold text-sm mt-3">Quick Start</h3>
          <ol className="list-decimal list-inside space-y-1.5 mt-2">
            <li>Start the stack: <code className="bg-gray-800/80 px-1.5 py-0.5 rounded text-purple-300 text-xs">docker compose up -d</code></li>
            <li>API at <code className="bg-gray-800/80 px-1.5 py-0.5 rounded text-purple-300 text-xs">http://localhost:8080</code></li>
            <li>Dashboard at <code className="bg-gray-800/80 px-1.5 py-0.5 rounded text-purple-300 text-xs">http://localhost:3000</code></li>
            <li>MCP endpoint at <code className="bg-gray-800/80 px-1.5 py-0.5 rounded text-purple-300 text-xs">http://localhost:8080/mcp/messages</code></li>
          </ol>

          <h3 className="text-white font-semibold text-sm mt-4">First API Call</h3>
          <CodeBlock
            title="Create a memory"
            code={`curl -X POST http://localhost:8080/api/v1/memory \\
  -H "Content-Type: application/json" \\
  -d '{"content":"Use PostgreSQL for auth persistence","type":"DECLARATIVE","scope":"PROJECT","tags":["architecture","database"]}'`}
          />
          <CodeBlock
            title="Search your memories"
            code={`curl "http://localhost:8080/api/v1/memory/search?q=PostgreSQL"`}
          />
        </Section>

        {/* ── Architecture ── */}
        <Section id="architecture" title="Architecture">
          <p>Seven services working together:</p>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2.5 mt-3">
            {[
              { name: 'PostgreSQL', port: ':5432', desc: 'Primary data store', color: 'text-blue-400' },
              { name: 'Redis', port: ':6379', desc: 'Cache & session state', color: 'text-red-400' },
              { name: 'Qdrant', port: ':6333', desc: 'Vector search', color: 'text-cyan-400' },
              { name: 'Neo4j', port: ':7474', desc: 'Knowledge graph', color: 'text-green-400' },
              { name: 'MinIO', port: ':9001', desc: 'Object storage', color: 'text-amber-400' },
              { name: 'API + MCP', port: ':8080', desc: 'Spring Boot server', color: 'text-purple-400' },
            ].map((c) => (
              <div key={c.name} className="bg-gray-900/60 border border-gray-800/60 rounded-lg p-3 flex items-start gap-3">
                <div className={`text-xs font-mono ${c.color} mt-0.5 shrink-0`}>{c.port}</div>
                <div>
                  <h4 className="text-white font-semibold text-xs">{c.name}</h4>
                  <p className="text-gray-500 text-[11px] mt-0.5">{c.desc}</p>
                </div>
              </div>
            ))}
          </div>

          <h3 className="text-white font-semibold text-sm mt-4">Data Flow</h3>
          <CodeBlock
            title="Request flow"
            code={`Agent ──> MCP Server ──> Second Brain API ──> PostgreSQL + Qdrant + Neo4j
                                                  │
                                            Dashboard (port 3000)`}
          />
        </Section>

        {/* ── MCP Setup ── */}
        <Section id="mcp-setup" title="Connect with Agents (MCP)">
          <p>
            The Model Context Protocol (MCP) is the standard way to connect AI agents to the Second Brain.
            Your agent gets 15 tools for searching, storing, and querying memories.
          </p>

          <InfoBox type="info">
            MCP endpoint: <code className="text-blue-200">http://localhost:8080/mcp/messages</code> &middot; Protocol: SSE over HTTP
          </InfoBox>

          <h3 className="text-white font-semibold text-sm mt-4">Claude Desktop</h3>
          <p className="text-gray-500 text-xs">Add to <code>~/Library/Application Support/Claude/claude_desktop_config.json</code>:</p>
          <CodeBlock
            title="claude_desktop_config.json"
            code={`{
  "mcpServers": {
    "second-brain": {
      "url": "http://localhost:8080/mcp/messages",
      "transport": "sse"
    }
  }
}`}
          />

          <h3 className="text-white font-semibold text-sm mt-4">Cursor / VS Code</h3>
          <p className="text-gray-500 text-xs">Add to <code>.cursor/mcp.json</code>:</p>
          <CodeBlock
            title=".cursor/mcp.json"
            code={`{
  "mcpServers": {
    "second-brain": {
      "url": "http://localhost:8080/mcp/messages",
      "transport": "sse"
    }
  }
}`}
          />

          <h3 className="text-white font-semibold text-sm mt-4">Python Agent</h3>
          <CodeBlock
            title="connect_agent.py"
            code={`from mcp import ClientSession, SSEServerTransport
import asyncio

async def connect_to_brain():
    transport = SSEServerTransport("http://localhost:8080/mcp/messages")
    async with ClientSession(transport) as session:
        await session.initialize()

        # List available tools
        tools = await session.list_tools()
        print(f"Tools: {[t.name for t in tools.tools]}")

        # Search memories
        result = await session.call_tool("brain_search", {
            "query": "architecture decisions"
        })

        # Store a memory
        await session.call_tool("brain_store_memory", {
            "content": "Agent X prefers functional programming",
            "type": "DECLARATIVE",
            "scope": "GLOBAL",
            "tags": ["agent-preference"]
        })

asyncio.run(connect_to_brain())`}
          />

          <h3 className="text-white font-semibold text-sm mt-4">TypeScript Agent</h3>
          <CodeBlock
            title="connect-agent.ts"
            code={`import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { SSEClientTransport } from "@modelcontextprotocol/sdk/client/sse.js";

async function connectToBrain() {
  const transport = new SSEClientTransport(
    new URL("http://localhost:8080/mcp/messages")
  );
  const client = new Client({ name: "my-agent", version: "1.0" });
  await client.connect(transport);

  const { tools } = await client.listTools();
  console.log("Tools:", tools.map(t => t.name));

  // Search
  const result = await client.callTool({
    name: "brain_search",
    arguments: { query: "database design" }
  });

  // Store
  await client.callTool({
    name: "brain_store_memory",
    arguments: {
      content: "Redis is used for session caching only",
      type: "DECLARATIVE",
      scope: "PROJECT",
      tags: ["redis", "architecture"]
    }
  });
}

connectToBrain();`}
          />
        </Section>

        {/* ── MCP Tools ── */}
        <Section id="mcp-tools" title="MCP Tools Reference">
          <p>15 tools available. Click any tool to expand its details and example.</p>
          <div className="space-y-2 mt-3">
            {[
              { name: 'brain_search', desc: 'Semantic search across all memories', params: 'query (required), collection, limit', example: '{ "query": "authentication approach", "limit": 5 }' },
              { name: 'brain_ask', desc: 'Natural-language question against the brain', params: 'question (required)', example: '{ "question": "What database for user sessions?" }' },
              { name: 'brain_store_memory', desc: 'Store a new memory', params: 'content (required), type (required), scope, project_id, repository_id, tags', example: '{ "content": "Use JWT for API auth", "type": "DECLARATIVE", "tags": ["auth"] }' },
              { name: 'brain_projects', desc: 'List all projects', params: 'none', example: '{}' },
              { name: 'brain_start_session', desc: 'Start a new agent session', params: 'agent_name (required), task (required), repository_id, project_id', example: '{ "agent_name": "claude-code", "task": "Implement OAuth" }' },
              { name: 'brain_end_session', desc: 'End an agent session', params: 'session_id (required), summary', example: '{ "session_id": "uuid", "summary": "Done" }' },
              { name: 'brain_record_event', desc: 'Record an agent event', params: 'event_type (required), description (required), session_id, file_path, status', example: '{ "event_type": "file_edit", "description": "Updated auth.js" }' },
              { name: 'brain_record_decision', desc: 'Record an architectural decision', params: 'title (required), description (required), rationale, project_id, tags', example: '{ "title": "Use Stripe", "description": "Payments" }' },
              { name: 'brain_create_task', desc: 'Create a task', params: 'title (required), description, project_id, priority', example: '{ "title": "Add rate limiting", "priority": 2 }' },
              { name: 'brain_get_open_tasks', desc: 'Get all open tasks', params: 'project_id (optional)', example: '{ "project_id": "uuid" }' },
              { name: 'brain_create_handoff', desc: 'Create an agent-to-agent handoff', params: 'session_id (required), task (required), completed_items, next_steps, decisions', example: '{ "session_id": "uuid", "task": "Auth", "completed_items": "JWT" }' },
              { name: 'brain_get_handoff', desc: 'Get latest handoff for a repository', params: 'repository_id (required)', example: '{ "repository_id": "uuid" }' },
              { name: 'brain_get_recent_activity', desc: 'Get recent agent activity', params: 'limit (optional, default 20)', example: '{ "limit": 10 }' },
              { name: 'brain_knowledge_graph', desc: 'Query the knowledge graph (experimental)', params: 'label (required), id, depth', example: '{ "label": "Memory", "depth": 2 }' },
            ].map((tool) => (
              <ToolCard key={tool.name} tool={tool} />
            ))}
          </div>
        </Section>

        {/* ── REST API ── */}
        <Section id="api-reference" title="REST API Reference">
          <p>Base URL: <code className="bg-gray-800/80 px-1.5 py-0.5 rounded text-purple-300 text-xs">http://localhost:8080/api/v1</code></p>

          <div className="space-y-5 mt-4">
            {[
              {
                category: 'Memory',
                endpoints: [
                  { method: 'POST', path: '/memory', desc: 'Create a memory' },
                  { method: 'GET', path: '/memory', desc: 'List all memories' },
                  { method: 'GET', path: '/memory/{id}', desc: 'Get memory by ID' },
                  { method: 'GET', path: '/memory/search?q={query}', desc: 'Search memories' },
                  { method: 'GET', path: '/memory/type/{type}', desc: 'Filter by type' },
                  { method: 'PUT', path: '/memory/{id}', desc: 'Update a memory' },
                  { method: 'DELETE', path: '/memory/{id}', desc: 'Delete a memory' },
                ],
              },
              {
                category: 'Projects',
                endpoints: [
                  { method: 'POST', path: '/projects', desc: 'Create a project' },
                  { method: 'GET', path: '/projects', desc: 'List all projects' },
                  { method: 'GET', path: '/projects/{id}', desc: 'Get project by ID' },
                  { method: 'PUT', path: '/projects/{id}', desc: 'Update a project' },
                  { method: 'DELETE', path: '/projects/{id}', desc: 'Delete a project' },
                ],
              },
              {
                category: 'Agents & Sessions',
                endpoints: [
                  { method: 'POST', path: '/agents', desc: 'Register an agent' },
                  { method: 'GET', path: '/agents', desc: 'List all agents' },
                  { method: 'POST', path: '/sessions/start', desc: 'Start a session' },
                  { method: 'POST', path: '/sessions/{id}/end', desc: 'End a session' },
                  { method: 'GET', path: '/sessions/recent', desc: 'Recent sessions' },
                ],
              },
              {
                category: 'Tasks & Decisions',
                endpoints: [
                  { method: 'POST', path: '/tasks', desc: 'Create a task' },
                  { method: 'GET', path: '/tasks', desc: 'List all tasks' },
                  { method: 'GET', path: '/tasks/open', desc: 'Get open tasks' },
                  { method: 'PUT', path: '/tasks/{id}/status', desc: 'Update task status' },
                  { method: 'POST', path: '/decisions', desc: 'Record a decision' },
                  { method: 'GET', path: '/decisions/recent', desc: 'Recent decisions' },
                ],
              },
              {
                category: 'Events & Skills',
                endpoints: [
                  { method: 'POST', path: '/events', desc: 'Record an event' },
                  { method: 'GET', path: '/events', desc: 'Recent events' },
                  { method: 'GET', path: '/events/type/{type}', desc: 'Events by type' },
                  { method: 'POST', path: '/skills', desc: 'Create a skill' },
                  { method: 'GET', path: '/skills', desc: 'List all skills' },
                  { method: 'GET', path: '/skills/scope/{scope}', desc: 'Skills by scope' },
                ],
              },
              {
                category: 'Knowledge Graph',
                endpoints: [
                  { method: 'GET', path: '/graph/stats', desc: 'Graph statistics' },
                  { method: 'POST', path: '/graph/sync', desc: 'Sync data to graph' },
                ],
              },
            ].map((group) => (
              <div key={group.category}>
                <h3 className="text-white font-semibold text-xs uppercase tracking-wider text-gray-400 mb-2">{group.category}</h3>
                <div className="bg-gray-900/60 border border-gray-800/60 rounded-lg overflow-hidden">
                  {group.endpoints.map((ep, i) => (
                    <div key={i} className={`flex items-center gap-2.5 px-3 py-2 text-xs ${i > 0 ? 'border-t border-gray-800/40' : ''}`}>
                      <MethodBadge method={ep.method} />
                      <code className="text-gray-300 font-mono text-[11px] break-all">{ep.path}</code>
                      <span className="text-gray-500 text-[11px] ml-auto shrink-0 hidden sm:inline">{ep.desc}</span>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </Section>

        {/* ── Examples ── */}
        <Section id="examples" title="Agent Integration Examples">
          <h3 className="text-white font-semibold text-sm">Full Agent Workflow</h3>
          <CodeBlock
            title="7-step agent session"
            code={`# 1. Register agent
curl -X POST http://localhost:8080/api/v1/agents \\
  -H "Content-Type: application/json" \\
  -d '{"name":"claude-code","type":"coding-agent","capabilities":["coding","testing"]}'

# 2. Start session
SESSION=$(curl -s -X POST "http://localhost:8080/api/v1/sessions/start?agentId=AGENT_UUID&task=Implement+payment+gateway")

# 3. Search brain for context
curl "http://localhost:8080/api/v1/memory/search?q=payment+gateway+architecture"

# 4. Record a decision
curl -X POST http://localhost:8080/api/v1/decisions \\
  -H "Content-Type: application/json" \\
  -d '{"title":"Use Stripe","description":"Chose Stripe API","rationale":"Best DX"}'

# 5. Store what you learned
curl -X POST http://localhost:8080/api/v1/memory \\
  -H "Content-Type: application/json" \\
  -d '{"content":"Stripe webhook must be /webhooks/stripe","type":"PROCEDURAL","tags":["stripe"]}'

# 6. Create follow-up task
curl -X POST http://localhost:8080/api/v1/tasks \\
  -H "Content-Type: application/json" \\
  -d '{"title":"Add Stripe retry logic","priority":2}'

# 7. End session
curl -X POST "http://localhost:8080/api/v1/sessions/$SESSION/end?summary=Implemented+Stripe+checkout"`}
          />

          <h3 className="text-white font-semibold text-sm mt-4">Multi-Agent Handoff</h3>
          <CodeBlock
            title="Agent A passes context to Agent B"
            code={`# Agent A stores handoff
curl -X POST http://localhost:8080/api/v1/memory \\
  -H "Content-Type: application/json" \\
  -d '{"content":"HANDOFF: Agent A completed JWT auth. Agent B: implement refresh tokens. Key file: src/auth/jwt.js","type":"PROCEDURAL","scope":"PROJECT","tags":["handoff"]}'

# Agent B searches for context
curl "http://localhost:8080/api/v1/memory/search?q=JWT+auth+handoff"
curl "http://localhost:8080/api/v1/decisions/recent"`}
          />
        </Section>

        {/* ── Configuration ── */}
        <Section id="configuration" title="Configuration">
          <h3 className="text-white font-semibold text-xs uppercase tracking-wider text-gray-400 mb-2">Environment Variables</h3>
          <div className="bg-gray-900/60 border border-gray-800/60 rounded-lg overflow-hidden">
            {[
              { variable: 'POSTGRES_DB', defaultValue: 'second_brain', desc: 'Database name' },
              { variable: 'POSTGRES_USER', defaultValue: 'postgres', desc: 'Database user' },
              { variable: 'POSTGRES_PASSWORD', defaultValue: 'postgres', desc: 'Database password' },
              { variable: 'NEO4J_USERNAME', defaultValue: 'neo4j', desc: 'Neo4j user' },
              { variable: 'NEO4J_PASSWORD', defaultValue: 'password', desc: 'Neo4j password' },
              { variable: 'MINIO_ROOT_USER', defaultValue: 'minioadmin', desc: 'MinIO access key' },
              { variable: 'MINIO_ROOT_PASSWORD', defaultValue: 'minioadmin', desc: 'MinIO secret key' },
              { variable: 'API_PORT', defaultValue: '8080', desc: 'API port' },
              { variable: 'DASHBOARD_PORT', defaultValue: '3000', desc: 'Dashboard port' },
            ].map((env, i) => (
              <div key={env.variable} className={`flex items-center gap-3 px-3 py-2 text-xs ${i > 0 ? 'border-t border-gray-800/40' : ''}`}>
                <code className="text-purple-400 font-mono text-[11px] shrink-0 w-36 truncate">{env.variable}</code>
                <code className="text-gray-500 font-mono text-[11px] shrink-0 w-24 text-right">{env.defaultValue}</code>
                <span className="text-gray-400 text-[11px]">{env.desc}</span>
              </div>
            ))}
          </div>

          <h3 className="text-white font-semibold text-sm mt-4">Docker Services</h3>
          <CodeBlock
            title="docker-compose.yml (7 services)"
            code={`postgres   :5432   Primary database
redis      :6379   Cache & sessions
qdrant     :6333   Vector search
neo4j      :7474   Knowledge graph
minio      :9001   Object storage
api        :8080   Spring Boot API + MCP Server
dashboard  :3000   React dashboard (nginx)`}
          />

          <InfoBox type="warning">
            <strong>MCP requires the API to be running.</strong> Ensure the <code>api</code> container is healthy before connecting agents. Check with: <code>curl http://localhost:8080/actuator/health</code>
          </InfoBox>
        </Section>
      </div>
    </div>
  );
}
