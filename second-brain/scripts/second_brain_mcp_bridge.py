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
