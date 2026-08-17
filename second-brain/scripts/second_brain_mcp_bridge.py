#!/usr/bin/env python3
"""
Second Brain — Universal Model Context Protocol (MCP) STDIO Bridge.
Exposes Second Brain capabilities (Graph-RAG, Continuity, Trials, Symbols, Knowledge Graph)
over JSON-RPC 2.0 STDIO for Antigravity, Claude Code, Codex, and OpenCode.
"""

import sys
import json
import urllib.request
import urllib.error
import urllib.parse
import os

BRAIN_URL = os.environ.get("BRAIN_URL", "http://localhost:8080")

TOOLS = [
    {
        "name": "brain_get_continuity_state",
        "description": "One-shot retrieval of cross-agent continuity state (uncommitted diffs, recent attempts, open tasks, decisions, and structured briefing).",
        "inputSchema": {
            "type": "object",
            "properties": {
                "repository_id_or_path": {"type": "string", "description": "Repository ID, path, or '.'"}
            }
        }
    },
    {
        "name": "brain_get_attempts",
        "description": "Query previous engineering trials, failed approaches, errors, and lessons learned.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "repository_id": {"type": "string", "description": "Repository UUID or null for global"},
                "limit": {"type": "integer", "description": "Max attempts to return (default 10)"}
            }
        }
    },
    {
        "name": "brain_record_attempt",
        "description": "Record an engineering trial or attempt (what was tried, approach, status, errors, and lessons learned).",
        "inputSchema": {
            "type": "object",
            "properties": {
                "agent_name": {"type": "string", "description": "Name of the agent (e.g. claude-code, codex)"},
                "task_description": {"type": "string", "description": "Goal or task description"},
                "approach": {"type": "string", "description": "Approach or strategy attempted"},
                "status": {"type": "string", "description": "SUCCESS, FAILURE, ABORTED, or SUPERSEDED"},
                "files_changed": {"type": "array", "items": {"type": "string"}, "description": "List of modified files"},
                "error_message": {"type": "string", "description": "Error message or stacktrace if failed"},
                "lesson_learned": {"type": "string", "description": "Key engineering takeaway / lesson"},
                "repository_id": {"type": "string", "description": "Repository UUID"}
            },
            "required": ["task_description", "approach"]
        }
    },
    {
        "name": "brain_get_context",
        "description": "Assemble rich Graph-RAG context (memories, AST call graph, decisions, open tasks) for any task or query.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "query": {"type": "string", "description": "Natural language task or question"},
                "repository_id": {"type": "string", "description": "Repository UUID"}
            },
            "required": ["query"]
        }
    },
    {
        "name": "brain_search",
        "description": "Search Second Brain memories or code symbols.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "query": {"type": "string", "description": "Search query"},
                "collection": {"type": "string", "description": "Target collection (e.g. code_symbols, documentation)"}
            },
            "required": ["query"]
        }
    },
    {
        "name": "brain_knowledge_graph",
        "description": "Query the Second Brain Neo4j knowledge graph (functions, callers, callees, API routes).",
        "inputSchema": {
            "type": "object",
            "properties": {
                "label": {"type": "string", "description": "Node label (e.g. Function, Endpoint, Repository)"},
                "limit": {"type": "integer", "description": "Max nodes (default 50)"}
            }
        }
    },
    {
        "name": "brain_record_decision",
        "description": "Record an architectural or engineering decision into persistent memory.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "title": {"type": "string", "description": "Title of the decision"},
                "description": {"type": "string", "description": "Detailed explanation"},
                "rationale": {"type": "string", "description": "Why this decision was made"},
                "repository_id": {"type": "string", "description": "Repository UUID"}
            },
            "required": ["title", "description"]
        }
    },
    {
        "name": "brain_create_handoff",
        "description": "Create an explicit state handoff from one AI agent to another.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "from_agent": {"type": "string", "description": "Current agent name"},
                "to_agent": {"type": "string", "description": "Target agent name or 'next-agent'"},
                "task_summary": {"type": "string", "description": "Summary of work accomplished"},
                "files_modified": {"type": "array", "items": {"type": "string"}, "description": "List of modified files"},
                "next_steps": {"type": "string", "description": "Actionable next steps for incoming agent"}
            },
            "required": ["from_agent", "task_summary"]
        }
    },
    {
        "name": "brain_create_project",
        "description": "Create a new Project in Second Brain with optional automatic Git repository cloning, AST analysis, and graph indexing.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "name": {"type": "string", "description": "Name of the project (e.g. 'CoreBanking' or 'payment-service')"},
                "description": {"type": "string", "description": "Optional project description"},
                "path": {"type": "string", "description": "Optional workspace path"},
                "git_repo": {"type": "string", "description": "Optional Git repository URL (e.g. 'https://github.com/org/repo.git')"}
            }
        }
    },
    {
        "name": "brain_list_projects",
        "description": "List all registered projects in Second Brain with linked repositories, paths, and task counts.",
        "inputSchema": {
            "type": "object",
            "properties": {}
        }
    },
    {
        "name": "brain_get_project",
        "description": "Get full overview and metadata of a project (repositories, tasks, decisions, documents) by project name or UUID.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "project": {"type": "string", "description": "Project name or UUID"}
            },
            "required": ["project"]
        }
    },
    {
        "name": "brain_use_project",
        "description": "Activate and switch focus to a specific project. Initializes an agent session and returns the startup context briefing.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "project": {"type": "string", "description": "Project name or UUID to work on"},
                "agent_name": {"type": "string", "description": "Your agent name (e.g. claude-code, codex, cursor)"},
                "task": {"type": "string", "description": "Current task or goal on this project"}
            },
            "required": ["project"]
        }
    },
    {
        "name": "brain_impact_analysis",
        "description": "Analyze breaking change risks, affected downstream call sites in Neo4j, and architectural drift against project decisions.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "file_path": {"type": "string", "description": "Path of the file modified"},
                "diff_or_code": {"type": "string", "description": "Code diff or modified function body"},
                "project_id": {"type": "string", "description": "Optional project UUID"}
            },
            "required": ["diff_or_code"]
        }
    },
    {
        "name": "brain_review_changes",
        "description": "Graph-augmented AI code review cross-referencing past trial failures, regressions, test coverage, and decision compliance.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "working_tree_diff": {"type": "string", "description": "Git diff of the current working tree"},
                "project_id": {"type": "string", "description": "Optional project UUID"},
                "repository_id": {"type": "string", "description": "Optional repository UUID"}
            },
            "required": ["working_tree_diff"]
        }
    },
    {
        "name": "brain_ingest_diagram",
        "description": "Parse Mermaid, PlantUML, or C4 architecture diagrams into Neo4j graph nodes and relationships.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "diagram_text": {"type": "string", "description": "Mermaid or PlantUML diagram text"},
                "format": {"type": "string", "description": "Diagram format (default 'mermaid')"},
                "project_id": {"type": "string", "description": "Optional project UUID"}
            },
            "required": ["diagram_text"]
        }
    },
    {
        "name": "brain_workspace_state",
        "description": "1-Shot Master Context: Get active project, workspace files, recent trials, handoffs, decisions, and open tasks in a single call.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "project": {"type": "string", "description": "Optional project name or UUID"},
                "repository": {"type": "string", "description": "Optional repository name, ID or local path"}
            }
        }
    },
    {
        "name": "brain_doctor",
        "description": "Check Second Brain system health, vector database, graph store, and background worker status.",
        "inputSchema": {
            "type": "object",
            "properties": {}
        }
    }
]

def make_http_request(path, method="GET", data=None):
    url = f"{BRAIN_URL}{path}"
    headers = {"Content-Type": "application/json"}
    body = json.dumps(data).encode("utf-8") if data is not None else None
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            content = resp.read().decode("utf-8")
            return json.loads(content) if content else {}
    except Exception as e:
        return {"error": str(e)}

def handle_tool_call(name, args):
    if name == "brain_get_continuity_state":
        repo = args.get("repository_id_or_path", "")
        res = make_http_request(f"/api/v1/bridge/continuity?repo={urllib.parse.quote(repo)}")
        briefing = res.get("structuredBriefing", json.dumps(res, indent=2))
        return briefing

    elif name == "brain_get_attempts":
        repo_id = args.get("repository_id")
        path = f"/api/v1/bridge/attempts/repository/{repo_id}" if repo_id else "/api/v1/bridge/attempts"
        res = make_http_request(path)
        return json.dumps(res, indent=2)

    elif name == "brain_record_attempt":
        payload = {
            "agentName": args.get("agent_name", "ai-agent"),
            "taskDescription": args.get("task_description", ""),
            "approach": args.get("approach", ""),
            "status": args.get("status", "SUCCESS"),
            "errorMessage": args.get("error_message"),
            "lessonLearned": args.get("lesson_learned"),
            "filesChanged": args.get("files_changed", []),
            "repositoryId": args.get("repository_id")
        }
        res = make_http_request("/api/v1/bridge/attempts", method="POST", data=payload)
        return f"Successfully recorded attempt: {res.get('id', 'OK')}"

    elif name == "brain_get_context":
        payload = {
            "query": args.get("query", ""),
            "repositoryId": args.get("repository_id")
        }
        res = make_http_request("/api/v1/context/assemble", method="POST", data=payload)
        return json.dumps(res, indent=2)

    elif name == "brain_search":
        q = args.get("query", "")
        coll = args.get("collection", "")
        if "symbol" in coll.lower():
            res = make_http_request(f"/api/v1/memory/symbols?q={urllib.parse.quote(q)}")
        else:
            res = make_http_request(f"/api/v1/memory/search?q={urllib.parse.quote(q)}")
        return json.dumps(res, indent=2)

    elif name == "brain_knowledge_graph":
        limit = args.get("limit", 50)
        res = make_http_request(f"/api/v1/graph/visual?limit={limit}")
        return json.dumps(res, indent=2)

    elif name == "brain_record_decision":
        payload = {
            "title": args.get("title", ""),
            "description": args.get("description", ""),
            "rationale": args.get("rationale", ""),
            "repositoryId": args.get("repository_id")
        }
        res = make_http_request("/api/v1/decisions", method="POST", data=payload)
        return f"Recorded decision: {res.get('id', 'OK')}"

    elif name == "brain_create_handoff":
        payload = {
            "fromAgent": args.get("from_agent", ""),
            "toAgent": args.get("to_agent", "next-agent"),
            "taskSummary": args.get("task_summary", ""),
            "filesModified": args.get("files_modified", []),
            "pendingItems": [args.get("next_steps")] if args.get("next_steps") else []
        }
        res = make_http_request("/api/v1/handoffs", method="POST", data=payload)
        return f"Created handoff: {res.get('id', 'OK')}"

    elif name == "brain_create_project":
        payload = {
            "name": args.get("name", ""),
            "description": args.get("description", ""),
            "path": args.get("path", ""),
            "gitRepo": args.get("git_repo", "")
        }
        res = make_http_request("/api/v1/projects/create-with-repo", method="POST", data=payload)
        return json.dumps(res, indent=2)

    elif name == "brain_list_projects":
        res = make_http_request("/api/v1/projects")
        if isinstance(res, list):
            sb = ["=== Second Brain Projects ===\n"]
            for p in res:
                sb.append(f"📁 Project: {p.get('name')}\n   ID: {p.get('id')}\n   Path: {p.get('path', 'N/A')}\n   Description: {p.get('description', '')}\n")
            return "\n".join(sb) if len(res) > 0 else "No projects registered yet."
        return json.dumps(res, indent=2)

    elif name == "brain_get_project":
        proj = args.get("project", "")
        res = make_http_request("/api/v1/projects")
        matched = None
        if isinstance(res, list):
            for p in res:
                if str(p.get("id")) == proj or p.get("name", "").lower() == proj.lower():
                    matched = p
                    break
        if matched:
            repos = make_http_request(f"/api/v1/repositories")
            linked_repos = [r for r in repos if isinstance(r, dict) and r.get("project", {}).get("id") == matched.get("id")] if isinstance(repos, list) else []
            return f"=== Project: {matched.get('name')} ===\nID: {matched.get('id')}\nPath: {matched.get('path')}\nDescription: {matched.get('description')}\nLinked Repositories: {len(linked_repos)}"
        return f"Project not found: {proj}"

    elif name == "brain_use_project":
        proj = args.get("project", "")
        agent = args.get("agent_name", "ai-agent")
        task = args.get("task", f"Working on {proj}")
        # Search project
        res = make_http_request("/api/v1/projects")
        matched = None
        if isinstance(res, list):
            for p in res:
                if str(p.get("id")) == proj or p.get("name", "").lower() == proj.lower():
                    matched = p
                    break
        if matched:
            sess = make_http_request("/api/v1/sessions", method="POST", data={
                "agentName": agent,
                "task": task,
                "projectId": matched.get("id")
            })
            return f"🎯 ACTIVATED PROJECT: {matched.get('name')}\nSession: {sess.get('id', 'OK')}\nAgent: {agent}\nTask: {task}\nWorkspace: {matched.get('path', 'N/A')}"
    elif name == "brain_impact_analysis":
        payload = {
            "filePath": args.get("file_path", ""),
            "diff": args.get("diff_or_code", ""),
            "projectId": args.get("project_id", "")
        }
        res = make_http_request("/api/v1/intel/impact-analysis", method="POST", data=payload)
        return json.dumps(res, indent=2)

    elif name == "brain_review_changes":
        payload = {
            "diff": args.get("working_tree_diff", ""),
            "projectId": args.get("project_id", ""),
            "repositoryId": args.get("repository_id", "")
        }
        res = make_http_request("/api/v1/intel/review", method="POST", data=payload)
        return res.get("markdownReport", json.dumps(res, indent=2))

    elif name == "brain_ingest_diagram":
        payload = {
            "diagram": args.get("diagram_text", ""),
            "format": args.get("format", "mermaid"),
            "projectId": args.get("project_id", "")
        }
        res = make_http_request("/api/v1/intel/ingest-diagram", method="POST", data=payload)
        return json.dumps(res, indent=2)

    elif name == "brain_workspace_state":
        proj = args.get("project", "")
        repo = args.get("repository", "")
        query = []
        if proj: query.append(f"project={urllib.parse.quote(proj)}")
        if repo: query.append(f"repo={urllib.parse.quote(repo)}")
        qs = "?" + "&".join(query) if query else ""
        res = make_http_request(f"/api/v1/bridge/workspace-state{qs}")
        return res.get("briefing", json.dumps(res, indent=2)) if isinstance(res, dict) else json.dumps(res, indent=2)

    elif name == "brain_doctor":
        health = make_http_request("/actuator/health")
        stats = make_http_request("/api/v1/graph/stats")
        return json.dumps({"health": health, "stats": stats}, indent=2)

    else:
        return f"Unknown tool: {name}"

def main():
    while True:
        line = sys.stdin.readline()
        if not line:
            break
        try:
            req = json.loads(line.strip())
        except Exception:
            continue

        req_id = req.get("id")
        method = req.get("method")

        if method == "initialize":
            resp = {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {
                    "protocolVersion": "2024-11-05",
                    "capabilities": {
                        "tools": {}
                    },
                    "serverInfo": {
                        "name": "second-brain",
                        "version": "1.0.0"
                    }
                }
            }
            sys.stdout.write(json.dumps(resp) + "\n")
            sys.stdout.flush()

        elif method == "notifications/initialized":
            pass

        elif method == "ping":
            resp = {"jsonrpc": "2.0", "id": req_id, "result": {}}
            sys.stdout.write(json.dumps(resp) + "\n")
            sys.stdout.flush()

        elif method == "tools/list":
            resp = {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {
                    "tools": TOOLS
                }
            }
            sys.stdout.write(json.dumps(resp) + "\n")
            sys.stdout.flush()

        elif method == "tools/call":
            params = req.get("params", {})
            name = params.get("name")
            args = params.get("arguments", {})
            try:
                result_text = handle_tool_call(name, args)
                resp = {
                    "jsonrpc": "2.0",
                    "id": req_id,
                    "result": {
                        "content": [
                            {
                                "type": "text",
                                "text": str(result_text)
                            }
                        ]
                    }
                }
            except Exception as ex:
                resp = {
                    "jsonrpc": "2.0",
                    "id": req_id,
                    "error": {
                        "code": -32603,
                        "message": str(ex)
                    }
                }
            sys.stdout.write(json.dumps(resp) + "\n")
            sys.stdout.flush()

if __name__ == "__main__":
    main()
