import { useState, useRef, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { brainApi } from '../api/client';
import {
  Sparkles,
  Send,
  Loader2,
  User,
  Brain,
  Network,
  Tag,
  Copy,
  Check,
  ChevronDown,
  ChevronUp,
} from 'lucide-react';

const SUGGESTIONS = [
  'What is the core architecture and technology stack?',
  'What architectural decisions have been made?',
  'What technologies are used across our repositories?',
  'Are there any open tasks or unresolved handoff issues?',
  'Explain how memories and graph nodes are linked.',
];

export default function BrainChat() {
  const [query, setQuery] = useState('');
  const [selectedProject, setSelectedProject] = useState('');
  const [selectedRepo, setSelectedRepo] = useState('');
  const [messages, setMessages] = useState([
    {
      id: 'welcome',
      role: 'assistant',
      text: 'Hello! I am your Second Brain AI assistant. Ask me anything about your projects, code architecture, architectural decisions, memories, open tasks, or knowledge graph relationships.',
      citations: [],
      graphContext: [],
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    },
  ]);
  const [isLoading, setIsLoading] = useState(false);
  const [expandedGraphMsgId, setExpandedGraphMsgId] = useState(null);
  const [copiedId, setCopiedId] = useState(null);

  const chatEndRef = useRef(null);

  const { data: projects = [] } = useQuery({
    queryKey: ['projects'],
    queryFn: () => brainApi.getProjects(),
  });

  const { data: repositories = [] } = useQuery({
    queryKey: ['repositories'],
    queryFn: () => brainApi.getRepositories(),
  });

  const projectList = Array.isArray(projects) ? projects : [];
  const repoList = Array.isArray(repositories) ? repositories : [];

  const scrollToBottom = () => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages, isLoading]);

  const handleSend = async (questionToSend) => {
    const q = (questionToSend || query).trim();
    if (!q || isLoading) return;

    const userMessage = {
      id: `user-${Date.now()}`,
      role: 'user',
      text: q,
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    };

    setMessages((prev) => [...prev, userMessage]);
    setQuery('');
    setIsLoading(true);

    try {
      const response = await brainApi.askBrain(
        q,
        selectedProject || undefined,
        selectedRepo || undefined
      );

      const assistantMessage = {
        id: `bot-${Date.now()}`,
        role: 'assistant',
        text: response.answer || 'No context could be synthesized for this query.',
        citations: response.citations || [],
        graphContext: response.context?.architecture || [],
        decisions: response.context?.decisions || [],
        tasks: response.context?.openTasks || [],
        sources: response.sources || [],
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      };

      setMessages((prev) => [...prev, assistantMessage]);
    } catch (err) {
      setMessages((prev) => [
        ...prev,
        {
          id: `bot-err-${Date.now()}`,
          role: 'assistant',
          text: `An error occurred while querying the brain: ${err.message}`,
          citations: [],
          graphContext: [],
          timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        },
      ]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleCopy = (text, id) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const handleClearHistory = () => {
    setMessages([
      {
        id: 'welcome-reset',
        role: 'assistant',
        text: 'Chat history cleared. How can I help you explore your Second Brain?',
        citations: [],
        graphContext: [],
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      },
    ]);
  };

  return (
    <div className="flex flex-col h-[calc(100vh-130px)] space-y-4">
      {/* Header Bar */}
      <div className="flex flex-wrap items-center justify-between gap-3 bg-gray-900 border border-gray-800 p-4 rounded-xl">
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-lg bg-purple-600/20 text-purple-400 border border-purple-500/30">
            <Sparkles className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-lg font-bold flex items-center gap-2">
              Brain Query & Natural Language Chat
            </h2>
            <p className="text-xs text-gray-400">
              Graph-RAG powered intelligence across vector memories, AST code graph, and decisions
            </p>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {/* Project filter */}
          <select
            value={selectedProject}
            onChange={(e) => setSelectedProject(e.target.value)}
            className="bg-gray-950 border border-gray-700 text-gray-200 text-xs rounded-lg px-3 py-1.5 focus:outline-none focus:border-purple-500"
          >
            <option value="">All Projects</option>
            {projectList.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>

          {/* Repository filter */}
          <select
            value={selectedRepo}
            onChange={(e) => setSelectedRepo(e.target.value)}
            className="bg-gray-950 border border-gray-700 text-gray-200 text-xs rounded-lg px-3 py-1.5 focus:outline-none focus:border-purple-500"
          >
            <option value="">All Repositories</option>
            {repoList.map((r) => (
              <option key={r.id} value={r.id}>
                {r.name}
              </option>
            ))}
          </select>

          <button
            onClick={handleClearHistory}
            className="text-xs text-gray-400 hover:text-gray-200 bg-gray-800 hover:bg-gray-700 px-3 py-1.5 rounded-lg transition-colors cursor-pointer"
          >
            Clear History
          </button>
        </div>
      </div>

      {/* Messages Container */}
      <div className="flex-1 overflow-y-auto space-y-4 p-4 bg-gray-900/60 border border-gray-800 rounded-xl">
        {messages.map((msg) => (
          <div
            key={msg.id}
            className={`flex gap-3 ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}
          >
            {msg.role === 'assistant' && (
              <div className="w-8 h-8 rounded-full bg-purple-600/30 border border-purple-500/40 flex items-center justify-center text-purple-400 flex-shrink-0 mt-1">
                <Brain className="w-4 h-4" />
              </div>
            )}

            <div
              className={`max-w-3xl rounded-xl p-4 space-y-3 ${
                msg.role === 'user'
                  ? 'bg-purple-600 text-white rounded-br-none'
                  : 'bg-gray-900 border border-gray-800 text-gray-100 rounded-bl-none shadow-md'
              }`}
            >
              {/* Message Header */}
              <div className="flex items-center justify-between gap-4 text-[11px] opacity-70">
                <span className="font-semibold">
                  {msg.role === 'user' ? 'You' : 'Second Brain Intelligence'}
                </span>
                <div className="flex items-center gap-2">
                  <span>{msg.timestamp}</span>
                  {msg.role === 'assistant' && (
                    <button
                      onClick={() => handleCopy(msg.text, msg.id)}
                      className="hover:text-purple-300 transition-colors cursor-pointer"
                      title="Copy response"
                    >
                      {copiedId === msg.id ? (
                        <Check className="w-3.5 h-3.5 text-green-400" />
                      ) : (
                        <Copy className="w-3.5 h-3.5" />
                      )}
                    </button>
                  )}
                </div>
              </div>

              {/* Message Body */}
              <div className="text-sm leading-relaxed whitespace-pre-wrap break-words font-sans">
                {msg.text}
              </div>

              {/* Citations & Evidence Section */}
              {msg.citations && msg.citations.length > 0 && (
                <div className="pt-3 border-t border-gray-800/80 space-y-2">
                  <div className="flex items-center gap-1.5 text-xs text-purple-400 font-semibold">
                    <Tag className="w-3.5 h-3.5" />
                    <span>Knowledge Citations ({msg.citations.length})</span>
                  </div>
                  <div className="flex flex-wrap gap-1.5">
                    {msg.citations.map((c, i) => (
                      <span
                        key={`${c.id}-${i}`}
                        className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-gray-950 border border-gray-800 text-xs text-gray-300"
                        title={c.title || c.id}
                      >
                        <span className="w-1.5 h-1.5 rounded-full bg-purple-400" />
                        <span className="font-mono text-[10px] text-purple-400 uppercase">
                          {c.type}
                        </span>
                        <span className="truncate max-w-[200px]">{c.title || c.id}</span>
                      </span>
                    ))}
                  </div>
                </div>
              )}

              {/* Graph-RAG Neighborhood Inspector */}
              {msg.graphContext && msg.graphContext.length > 0 && (
                <div className="pt-2">
                  <button
                    onClick={() =>
                      setExpandedGraphMsgId(expandedGraphMsgId === msg.id ? null : msg.id)
                    }
                    className="flex items-center gap-1.5 text-xs text-cyan-400 hover:text-cyan-300 transition-colors font-medium cursor-pointer"
                  >
                    <Network className="w-3.5 h-3.5" />
                    <span>
                      Graph-RAG Subgraph ({msg.graphContext.length} relational nodes fused)
                    </span>
                    {expandedGraphMsgId === msg.id ? (
                      <ChevronUp className="w-3.5 h-3.5" />
                    ) : (
                      <ChevronDown className="w-3.5 h-3.5" />
                    )}
                  </button>

                  {expandedGraphMsgId === msg.id && (
                    <div className="mt-2 p-3 bg-gray-950 border border-gray-800 rounded-lg space-y-2 text-xs font-mono max-h-56 overflow-y-auto">
                      {msg.graphContext.map((arch, idx) => (
                        <div
                          key={`${arch.id}-${idx}`}
                          className="p-2 rounded bg-gray-900 border border-gray-800/80 space-y-1"
                        >
                          <div className="flex items-center justify-between text-[11px]">
                            <span className="text-cyan-400 font-bold">{arch.type}</span>
                            <span className="text-gray-500 text-[10px]">{arch.id}</span>
                          </div>
                          <p className="text-gray-300 break-words">{arch.content}</p>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}

              {/* Sources tags */}
              {msg.sources && msg.sources.length > 0 && (
                <div className="flex flex-wrap gap-1 pt-1">
                  {msg.sources.map((s) => (
                    <span
                      key={s}
                      className="px-1.5 py-0.5 rounded bg-gray-950/60 text-[10px] font-mono text-gray-500 border border-gray-800/60"
                    >
                      #{s}
                    </span>
                  ))}
                </div>
              )}
            </div>

            {msg.role === 'user' && (
              <div className="w-8 h-8 rounded-full bg-purple-700 flex items-center justify-center text-white flex-shrink-0 mt-1">
                <User className="w-4 h-4" />
              </div>
            )}
          </div>
        ))}

        {isLoading && (
          <div className="flex gap-3 justify-start">
            <div className="w-8 h-8 rounded-full bg-purple-600/30 border border-purple-500/40 flex items-center justify-center text-purple-400 flex-shrink-0">
              <Brain className="w-4 h-4 animate-pulse" />
            </div>
            <div className="bg-gray-900 border border-gray-800 rounded-xl p-4 rounded-bl-none flex items-center gap-3 text-sm text-gray-400">
              <Loader2 className="w-4 h-4 animate-spin text-purple-400" />
              <span>Querying Qdrant vectors & Neo4j Graph-RAG neighborhood...</span>
            </div>
          </div>
        )}

        <div ref={chatEndRef} />
      </div>

      {/* Suggested Starter Prompts */}
      {messages.length <= 2 && (
        <div className="flex flex-wrap gap-2">
          {SUGGESTIONS.map((s) => (
            <button
              key={s}
              onClick={() => handleSend(s)}
              className="text-xs bg-gray-900 hover:bg-gray-800 border border-gray-800 hover:border-purple-500/50 text-gray-300 px-3 py-1.5 rounded-lg transition-all text-left cursor-pointer"
            >
              {s}
            </button>
          ))}
        </div>
      )}

      {/* Input Form */}
      <form
        onSubmit={(e) => {
          e.preventDefault();
          handleSend();
        }}
        className="flex gap-2 bg-gray-900 border border-gray-800 p-2 rounded-xl"
      >
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Ask Second Brain anything (e.g. 'How does authentication work?', 'What databases are configured?')..."
          className="flex-1 bg-transparent px-3 py-2 text-sm text-gray-100 placeholder-gray-500 focus:outline-none"
          disabled={isLoading}
        />
        <button
          type="submit"
          disabled={!query.trim() || isLoading}
          className="px-4 py-2 bg-purple-600 hover:bg-purple-700 disabled:opacity-50 text-white rounded-lg font-medium transition-colors flex items-center gap-2 text-sm cursor-pointer"
        >
          {isLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
          <span>Ask</span>
        </button>
      </form>
    </div>
  );
}
