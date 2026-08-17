import { useState } from 'react';
import { BookOpen, ChevronRight, Copy, Check, ExternalLink } from 'lucide-react';

const sections = [
  { id: 'getting-started', title: 'Getting Started' },
  { id: 'architecture', title: 'Architecture' },
  { id: 'mcp-setup', title: 'Connect with Agents (MCP)' },
  { id: 'mcp-tools', title: 'MCP Tools Reference' },
  { id: 'api-reference', title: 'REST API Reference' },
  { id: 'examples', title: 'Agent Integration Examples' },
  { id: 'configuration', title: 'Configuration' },
];

function CodeBlock({ code, language = 'json', title }) {
  const [copied, setCopied] = useState(false);

  const copy = () => {
    navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="rounded-lg overflow-hidden border border-gray-800">
      {title && (
        <div className="flex items-center justify-between bg-gray-800 px-4 py-2 text-sm text-gray-400">
          <span>{title}</span>
          <button onClick={copy} className="flex items-center gap-1 hover:text-gray-200 transition-colors">
            {copied ? <Check className="w-4 h-4 text-green-400" /> : <Copy className="w-4 h-4" />}
            {copied ? 'Copied' : 'Copy'}
          </button>
        </div>
      )}
      <pre className="bg-gray-950 p-4 overflow-x-auto text-sm leading-relaxed">
        <code className="text-gray-300">{code}</code>
      </pre>
    </div>
  );
}

function Section({ id, title, children }) {
  return (
    <section id={id} className="scroll-mt-20">
      <h2 className="text-xl font-bold text-white mb-4 flex items-center gap-2">
        <ChevronRight className="w-5 h-5 text-purple-400" />
        {title}
      </h2>
      <div className="space-y-4 text-gray-300 text-sm leading-relaxed pl-7">
        {children}
      </div>
    </section>
  );
}

function InfoBox({ type = 'info', children }) {
  const styles = {
    info: 'border-blue-500/30 bg-blue-500/10 text-blue-200',
    warning: 'border-yellow-500/30 bg-yellow-500/10 text-yellow-200',
    success: 'border-green-500/30 bg-green-500/10 text-green-200',
  };
  return (
    <div className={`rounded-lg border p-4 text-sm ${styles[type]}`}>
      {children}
    </div>
  );
}

export default function Documentation() {
  const [activeSection, setActiveSection] = useState('getting-started');

  return (
    <div className="flex gap-8">
      {/* Table of contents */}
      <aside className="w-56 shrink-0">
        <div className="sticky top-6">
          <h3 className="text-xs font-semibold uppercase text-gray-500 mb-3 tracking-wider">Documentation</h3>
          <nav className="space-y-1">
            {sections.map((s) => (
              <button
                key={s.id}
                onClick={() => {
                  setActiveSection(s.id);
                  document.getElementById(s.id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
                }}
                className={`block w-full text-left text-sm px-3 py-2 rounded-lg transition-colors ${
                  activeSection === s.id
                    ? 'bg-purple-600/20 text-purple-400'
                    : 'text-gray-400 hover:bg-gray-800 hover:text-gray-200'
                }`}
              >
                {s.title}
              </button>
            ))}
          </nav>
        </div>
      </aside>

      {/* Content */}
      <div className="flex-1 space-y-10 min-w-0">
        <div className="flex items-center gap-3 mb-2">
          <BookOpen className="w-7 h-7 text-purple-500" />
          <h1 className="text-2xl font-bold">Second Brain Documentation</h1>
        </div>
        <p className="text-gray-400 text-sm">
          Connect your AI agents to a persistent memory system. Store decisions, track sessions,
          and share context across agents and sessions.
        </p>

        {/* ── Getting Started ── */}
        <Section id="getting-started" title="Getting Started">
          <p>Second Brain is a memory-as-a-service platform for AI agents. It gives your agents persistent memory across sessions.</p>

          <h3 className="text-white font-semibold mt-4">Quick Start</h3>
          <ol className="list-decimal list-inside space-y-2 mt-2">
            <li>Start the stack with Docker: <code className="bg-gray-800 px-2 py-0.5 rounded text-purple-300">docker compose up -d</code></li>
            <li>API is available at <code className="bg-gray-800 px-2 py-0.5 rounded text-purple-300">http://localhost:8080</code></li>
            <li>Dashboard at <code className="bg-gray-800 px-2 py-0.5 rounded text-purple-300">http://localhost:3000</code></li>
            <li>MCP endpoint at <code className="bg-gray-800 px-2 py-0.5 rounded text-purple-300">http://localhost:8080/mcp/messages</code></li>
          </ol>

          <h3 className="text-white font-semibold mt-4">First API Call</h3>
          <CodeBlock
            title="Create a memory via REST API"
            language="bash"
            code={`curl -X POST http://localhost:8080/api/v1/memory \\
  -H "Content-Type: application/json" \\
  -d '{
    "content": "Use PostgreSQL for user authentication persistence",
    "type": "DECLARATIVE",
    "scope": "PROJECT",
    "tags": ["architecture", "database"]
  }'`}
          />

          <CodeBlock
            title="Search your memories"
            language="bash"
            code={`curl "http://localhost:8080/api/v1/memory/search?q=PostgreSQL"`}
          />
        </Section>

        {/* ── Architecture ── */}
        <Section id="architecture" title="Architecture">
          <p>The system consists of these components:</p>
          <div className="grid grid-cols-2 gap-3 mt-3">
            {[
              { name: 'PostgreSQL', desc: 'Primary data store for memories, projects, agents, tasks, decisions' },
              { name: 'Redis', desc: 'Caching and session state' },
              { name: 'Qdrant', desc: 'Vector database for semantic search across memories' },
              { name: 'Neo4j', desc: 'Knowledge graph for relationships between entities' },
              { name: 'MinIO', desc: 'Object storage for documents and files' },
              { name: 'MCP Server', desc: 'Model Context Protocol server for agent integration' },
            ].map((c) => (
              <div key={c.name} className="bg-gray-900 border border-gray-800 rounded-lg p-4">
                <h4 className="text-white font-semibold text-sm">{c.name}</h4>
                <p className="text-gray-400 text-xs mt-1">{c.desc}</p>
              </div>
            ))}
          </div>

          <h3 className="text-white font-semibold mt-4">Data Flow</h3>
          <CodeBlock
            title="How data flows through the system"
            language="text"
            code={`Agent ──> MCP Server ──> Second Brain API
  │                              │
  │         ┌────────────────────┤
  │         │                    │
  │    PostgreSQL          Qdrant (vectors)
  │    Neo4j (graph)       Redis (cache)
  │    MinIO (docs)
  │
  └── Search / Store / Query
      decisions, memories, tasks, sessions`}
          />
        </Section>

        {/* ── MCP Setup ── */}
        <Section id="mcp-setup" title="Connect with Agents (MCP)">
          <p>
            The Model Context Protocol (MCP) is the standard way to connect AI agents to the Second Brain.
            Your agent gets access to 15 tools for searching, storing, and querying memories.
          </p>

          <InfoBox type="info">
            MCP endpoint: <code>http://localhost:8080/mcp/messages</code>
            <br />
            Protocol: SSE (Server-Sent Events) over HTTP
          </InfoBox>

          <h3 className="text-white font-semibold mt-4">Claude Desktop Configuration</h3>
          <p>Add this to your Claude Desktop config file:</p>
          <CodeBlock
            title="~/Library/Application Support/Claude/claude_desktop_config.json"
            language="json"
            code={`{
  "mcpServers": {
    "second-brain": {
      "url": "http://localhost:8080/mcp/messages",
      "transport": "sse"
    }
  }
}`}
          />

          <h3 className="text-white font-semibold mt-4">Cursor / VS Code Configuration</h3>
          <p>Add to your MCP settings (e.g. <code className="bg-gray-800 px-1 rounded">.cursor/mcp.json</code>):</p>
          <CodeBlock
            title=".cursor/mcp.json"
            language="json"
            code={`{
  "mcpServers": {
    "second-brain": {
      "url": "http://localhost:8080/mcp/messages",
      "transport": "sse"
    }
  }
}`}
          />

          <h3 className="text-white font-semibold mt-4">Custom Agent Integration (Python)</h3>
          <CodeBlock
            title="connect_agent.py"
            language="python"
            code={`from mcp import ClientSession, SSEServerTransport
import asyncio

async def connect_to_brain():
    transport = SSEServerTransport("http://localhost:8080/mcp/messages")
    async with ClientSession(transport) as session:
        # Initialize connection
        await session.initialize()

        # List available tools
        tools = await session.list_tools()
        print(f"Available tools: {[t.name for t in tools.tools]}")

        # Search memories
        result = await session.call_tool("brain_search", {
            "query": "architecture decisions"
        })
        print(result)

        # Store a memory
        await session.call_tool("brain_store_memory", {
            "content": "Agent X prefers functional programming",
            "type": "DECLARATIVE",
            "scope": "GLOBAL",
            "tags": ["agent-preference", "coding-style"]
        })

asyncio.run(connect_to_brain())`}
          />

          <h3 className="text-white font-semibold mt-4">Custom Agent Integration (TypeScript)</h3>
          <CodeBlock
            title="connect-agent.ts"
            language="typescript"
            code={`import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { SSEClientTransport } from "@modelcontextprotocol/sdk/client/sse.js";

async function connectToBrain() {
  const transport = new SSEClientTransport(
    new URL("http://localhost:8080/mcp/messages")
  );
  const client = new Client({ name: "my-agent", version: "1.0" });
  await client.connect(transport);

  // List available tools
  const { tools } = await client.listTools();
  console.log("Tools:", tools.map(t => t.name));

  // Search the brain
  const result = await client.callTool({
    name: "brain_search",
    arguments: { query: "database design" }
  });
  console.log(result);

  // Store a memory
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

        {/* ── MCP Tools Reference ── */}
        <Section id="mcp-tools" title="MCP Tools Reference">
          <p>15 tools available through the MCP endpoint:</p>

          <div className="space-y-3 mt-4">
            {[
              {
                name: 'brain_search',
                desc: 'Semantic search across all memories',
                params: 'query (required), collection, limit',
                example: `{ "query": "authentication approach", "limit": 5 }`,
              },
              {
                name: 'brain_ask',
                desc: 'Natural-language question against the brain',
                params: 'question (required)',
                example: `{ "question": "What database should I use for user sessions?" }`,
              },
              {
                name: 'brain_store_memory',
                desc: 'Store a new memory',
                params: 'content (required), type (required), scope, project_id, repository_id, tags',
                example: `{ "content": "Use JWT for API auth", "type": "DECLARATIVE", "tags": ["auth"] }`,
              },
              {
                name: 'brain_projects',
                desc: 'List all projects in the brain',
                params: 'none',
                example: `{}`,
              },
              {
                name: 'brain_start_session',
                desc: 'Start a new agent session',
                params: 'agent_name (required), task (required), repository_id, project_id',
                example: `{ "agent_name": "claude-code", "task": "Implement OAuth" }`,
              },
              {
                name: 'brain_end_session',
                desc: 'End an agent session with summary',
                params: 'session_id (required), summary',
                example: `{ "session_id": "uuid-here", "summary": "Completed OAuth flow" }`,
              },
              {
                name: 'brain_record_event',
                desc: 'Record an agent event',
                params: 'event_type (required), description (required), session_id, file_path, status',
                example: `{ "event_type": "file_edit", "description": "Updated auth.js", "status": "success" }`,
              },
              {
                name: 'brain_record_decision',
                desc: 'Record an architectural decision',
                params: 'title (required), description (required), rationale, project_id, repository_id, tags',
                example: `{ "title": "Use Stripe", "description": "Payment processing", "rationale": "Best API" }`,
              },
              {
                name: 'brain_create_task',
                desc: 'Create a task',
                params: 'title (required), description, project_id, repository_id, priority',
                example: `{ "title": "Add rate limiting", "priority": 2 }`,
              },
              {
                name: 'brain_get_open_tasks',
                desc: 'Get all open tasks',
                params: 'project_id (optional)',
                example: `{ "project_id": "uuid" }`,
              },
              {
                name: 'brain_create_handoff',
                desc: 'Create an agent-to-agent handoff',
                params: 'session_id (required), task (required), completed_items, in_progress_items, blocked_items, changed_files, next_steps, decisions, known_issues',
                example: `{ "session_id": "uuid", "task": "Auth impl", "completed_items": "JWT setup", "next_steps": "Add refresh tokens" }`,
              },
              {
                name: 'brain_get_handoff',
                desc: 'Get latest handoff for a repository',
                params: 'repository_id (required)',
                example: `{ "repository_id": "uuid" }`,
              },
              {
                name: 'brain_get_recent_activity',
                desc: 'Get recent agent activity',
                params: 'limit (optional, default 20)',
                example: `{ "limit": 10 }`,
              },
              {
                name: 'brain_knowledge_graph',
                desc: 'Query the knowledge graph (experimental)',
                params: 'label (required), id, depth',
                example: `{ "label": "Memory", "depth": 2 }`,
              },
            ].map((tool) => (
              <div key={tool.name} className="bg-gray-900 border border-gray-800 rounded-lg p-4">
                <div className="flex items-center gap-2">
                  <code className="text-purple-400 font-semibold text-sm">{tool.name}</code>
                  <span className="text-gray-500 text-xs">|</span>
                  <span className="text-gray-400 text-xs">{tool.desc}</span>
                </div>
                <p className="text-gray-500 text-xs mt-1">Params: {tool.params}</p>
                <div className="mt-2">
                  <CodeBlock code={tool.example} title="Example" />
                </div>
              </div>
            ))}
          </div>
        </Section>

        {/* ── REST API Reference ── */}
        <Section id="api-reference" title="REST API Reference">
          <p>Base URL: <code className="bg-gray-800 px-2 py-0.5 rounded text-purple-300">http://localhost:8080/api/v1</code></p>

          <div className="space-y-4 mt-4">
            {[
              {
                category: 'Memory',
                endpoints: [
                  { method: 'POST', path: '/memory', desc: 'Create a memory', body: '{ "content": "...", "type": "DECLARATIVE", "scope": "GLOBAL", "tags": ["tag1"] }' },
                  { method: 'GET', path: '/memory', desc: 'List all memories' },
                  { method: 'GET', path: '/memory/{id}', desc: 'Get memory by ID' },
                  { method: 'GET', path: '/memory/search?q={query}', desc: 'Search memories' },
                  { method: 'GET', path: '/memory/type/{type}', desc: 'Filter by type (DECLARATIVE, PROCEDURAL, EPISODIC, SEMANTIC, EPILOGICAL)' },
                  { method: 'PUT', path: '/memory/{id}', desc: 'Update a memory' },
                  { method: 'DELETE', path: '/memory/{id}', desc: 'Delete a memory' },
                ],
              },
              {
                category: 'Projects',
                endpoints: [
                  { method: 'POST', path: '/projects', desc: 'Create a project', body: '{ "name": "...", "description": "...", "path": "/path" }' },
                  { method: 'GET', path: '/projects', desc: 'List all projects' },
                  { method: 'GET', path: '/projects/{id}', desc: 'Get project by ID' },
                  { method: 'PUT', path: '/projects/{id}', desc: 'Update a project' },
                  { method: 'DELETE', path: '/projects/{id}', desc: 'Delete a project' },
                ],
              },
              {
                category: 'Agents & Sessions',
                endpoints: [
                  { method: 'POST', path: '/agents', desc: 'Register an agent', body: '{ "name": "...", "type": "claude-code", "capabilities": ["coding"] }' },
                  { method: 'GET', path: '/agents', desc: 'List all agents' },
                  { method: 'POST', path: '/sessions/start', desc: 'Start a session', body: '?agentId={uuid}&task={description}' },
                  { method: 'POST', path: '/sessions/{id}/end', desc: 'End a session', body: '?summary={summary}' },
                  { method: 'GET', path: '/sessions/recent', desc: 'Recent sessions' },
                ],
              },
              {
                category: 'Tasks & Decisions',
                endpoints: [
                  { method: 'POST', path: '/tasks', desc: 'Create a task', body: '{ "title": "...", "description": "...", "priority": 3 }' },
                  { method: 'GET', path: '/tasks', desc: 'List all tasks' },
                  { method: 'GET', path: '/tasks/open', desc: 'Get open tasks' },
                  { method: 'PUT', path: '/tasks/{id}/status?status={STATUS}', desc: 'Update task status (OPEN, IN_PROGRESS, COMPLETED, BLOCKED)' },
                  { method: 'POST', path: '/decisions', desc: 'Record a decision', body: '{ "title": "...", "description": "...", "rationale": "..." }' },
                  { method: 'GET', path: '/decisions/recent', desc: 'Recent decisions' },
                ],
              },
              {
                category: 'Events',
                endpoints: [
                  { method: 'POST', path: '/events', desc: 'Record an event', body: '{ "sessionId": "uuid", "eventType": "...", "description": "..." }' },
                  { method: 'GET', path: '/events', desc: 'Recent events' },
                  { method: 'GET', path: '/events/type/{type}', desc: 'Events by type' },
                ],
              },
              {
                category: 'Skills',
                endpoints: [
                  { method: 'POST', path: '/skills', desc: 'Create a skill', body: '{ "name": "...", "description": "...", "scope": "global" }' },
                  { method: 'GET', path: '/skills', desc: 'List all skills' },
                  { method: 'GET', path: '/skills/scope/{scope}', desc: 'Skills by scope (global, project, repository)' },
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
                <h3 className="text-white font-semibold text-sm mt-6 mb-2">{group.category}</h3>
                <div className="bg-gray-900 border border-gray-800 rounded-lg overflow-hidden">
                  {group.endpoints.map((ep, i) => (
                    <div key={i} className={`flex items-center gap-3 px-4 py-2.5 text-sm ${i > 0 ? 'border-t border-gray-800' : ''}`}>
                      <span className={`px-2 py-0.5 rounded text-xs font-bold ${
                        ep.method === 'GET' ? 'bg-green-600/20 text-green-400' :
                        ep.method === 'POST' ? 'bg-blue-600/20 text-blue-400' :
                        ep.method === 'PUT' ? 'bg-yellow-600/20 text-yellow-400' :
                        'bg-red-600/20 text-red-400'
                      }`}>{ep.method}</span>
                      <code className="text-gray-300 font-mono text-xs">{ep.path}</code>
                      <span className="text-gray-500 text-xs ml-auto">{ep.desc}</span>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </Section>

        {/* ── Examples ── */}
        <Section id="examples" title="Agent Integration Examples">
          <h3 className="text-white font-semibold">Example: Agent Workflow</h3>
          <p>A typical agent workflow using the brain:</p>
          <CodeBlock
            title="Full agent session workflow"
            language="bash"
            code={`# 1. Register your agent
curl -X POST http://localhost:8080/api/v1/agents \\
  -H "Content-Type: application/json" \\
  -d '{"name": "claude-code", "type": "coding-agent", "capabilities": ["coding", "testing"]}'

# 2. Start a session
SESSION=$(curl -s -X POST "http://localhost:8080/api/v1/sessions/start?agentId=AGENT_UUID&task=Implement+payment+gateway")

# 3. Search brain for context before coding
curl "http://localhost:8080/api/v1/memory/search?q=payment+gateway+architecture"

# 4. Record a decision
curl -X POST http://localhost:8080/api/v1/decisions \\
  -H "Content-Type: application/json" \\
  -d '{"title": "Use Stripe for payments", "description": "Chose Stripe API", "rationale": "Best developer experience"}'

# 5. Store what you learned
curl -X POST http://localhost:8080/api/v1/memory \\
  -H "Content-Type: application/json" \\
  -d '{"content": "Stripe webhook endpoint must be /webhooks/stripe", "type": "PROCEDURAL", "tags": ["stripe", "webhooks"]}'

# 6. Create a task for follow-up
curl -X POST http://localhost:8080/api/v1/tasks \\
  -H "Content-Type: application/json" \\
  -d '{"title": "Add Stripe webhook retry logic", "priority": 2}'

# 7. End session with summary
curl -X POST "http://localhost:8080/api/v1/sessions/SESSION_UUID/end?summary=Implemented+basic+Stripe+checkout"`}
          />

          <h3 className="text-white font-semibold mt-6">Example: Multiple Agents Sharing Context</h3>
          <CodeBlock
            title="Agent handoff workflow"
            language="bash"
            code={`# Agent A finishes work and creates a handoff
curl -X POST http://localhost:8080/api/v1/memory \\
  -H "Content-Type: application/json" \\
  -d '{
    "content": "HANDOFF: Agent A completed JWT auth. Next: Agent B should implement refresh tokens using the /auth/refresh endpoint pattern. Key file: src/auth/jwt.js",
    "type": "PROCEDURAL",
    "scope": "PROJECT",
    "tags": ["handoff", "agent-a", "auth"]
  }'

# Agent B starts and searches for context
curl "http://localhost:8080/api/v1/memory/search?q=JWT+auth+handoff"
# Returns the handoff memory + related auth decisions

# Agent B reads recent decisions
curl "http://localhost:8080/api/v1/decisions/recent"
# Gets all architectural decisions for context`}
          />

          <h3 className="text-white font-semibold mt-6">Example: Semantic Search</h3>
          <CodeBlock
            title="Search for related memories"
            language="bash"
            code={`# Search for architecture decisions
curl "http://localhost:8080/api/v1/memory/search?q=database+choice"

# Search by memory type
curl "http://localhost:8080/api/v1/memory/type/DECLARATIVE"

# List all open tasks for a project
curl "http://localhost:8080/api/v1/tasks/open"

# Get knowledge graph stats
curl "http://localhost:8080/api/v1/graph/stats"`}
          />
        </Section>

        {/* ── Configuration ── */}
        <Section id="configuration" title="Configuration">
          <h3 className="text-white font-semibold">Environment Variables</h3>
          <div className="bg-gray-900 border border-gray-800 rounded-lg overflow-hidden mt-3">
            {[
              { var: 'POSTGRES_DB', default: 'second_brain', desc: 'PostgreSQL database name' },
              { var: 'POSTGRES_USER', default: 'postgres', desc: 'PostgreSQL username' },
              { var: 'POSTGRES_PASSWORD', default: 'postgres', desc: 'PostgreSQL password' },
              { var: 'NEO4J_USERNAME', default: 'neo4j', desc: 'Neo4j username' },
              { var: 'NEO4J_PASSWORD', default: 'password', desc: 'Neo4j password' },
              { var: 'MINIO_ROOT_USER', default: 'minioadmin', desc: 'MinIO access key' },
              { var: 'MINIO_ROOT_PASSWORD', default: 'minioadmin', desc: 'MinIO secret key' },
              { var: 'API_PORT', default: '8080', desc: 'API port' },
              { var: 'DASHBOARD_PORT', default: '3000', desc: 'Dashboard port' },
            ].map((env, i) => (
              <div key={env.var} className={`flex items-center gap-3 px-4 py-2.5 text-sm ${i > 0 ? 'border-t border-gray-800' : ''}`}>
                <code className="text-purple-400 font-mono text-xs">{env.var}</code>
                <span className="text-gray-500 text-xs">Default: {env.default}</span>
                <span className="text-gray-400 text-xs ml-auto">{env.desc}</span>
              </div>
            ))}
          </div>

          <h3 className="text-white font-semibold mt-6">Docker Services</h3>
          <CodeBlock
            title="docker-compose.yml"
            language="yaml"
            code={`services:
  postgres:    # :5432  - Primary database
  redis:       # :6379  - Cache
  qdrant:      # :6333  - Vector search
  neo4j:       # :7474  - Knowledge graph
  minio:       # :9001  - Object storage
  api:         # :8080  - Spring Boot API + MCP Server
  dashboard:   # :3000  - React dashboard (nginx)`}
          />

          <InfoBox type="warning" className="mt-4">
            <strong>MCP requires the API to be running.</strong> Ensure the <code>api</code> container is healthy before connecting agents. Check with: <code>curl http://localhost:8080/actuator/health</code>
          </InfoBox>
        </Section>
      </div>
    </div>
  );
}
