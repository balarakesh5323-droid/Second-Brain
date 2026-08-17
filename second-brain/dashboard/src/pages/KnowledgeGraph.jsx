import { useState, useCallback, useRef, useMemo, useEffect, lazy, Suspense } from 'react';
import { useQuery } from '@tanstack/react-query';
import { brainApi } from '../api/client';
import {
  Network,
  RefreshCw,
  Search,
  X,
  Copy,
  Check,
  ExternalLink,
  Code2,
  FileCode,
  FileText,
  Focus,
  Layers,
  Link2,
  Tag,
  FolderGit2,
  Brain,
  Bot,
  Boxes,
  Folder,
  ArrowRight,
  ArrowLeft,
  Globe,
} from 'lucide-react';

const ReactForceGraph2D = lazy(() => import('react-force-graph-2d'));

const NODE_COLORS = {
  Repository: '#a855f7',
  Technology: '#3b82f6',
  Language: '#22d3ee',
  File: '#6b7280',
  Class: '#f59e0b',
  Function: '#10b981',
  Endpoint: '#06b6d4',
  Module: '#ef4444',
  Project: '#ec4899',
  Memory: '#8b5cf6',
  Agent: '#06b6d4',
  Task: '#f97316',
  Decision: '#14b8a6',
  Unknown: '#64748b',
};

function getColor(label) {
  return NODE_COLORS[label] || NODE_COLORS.Unknown;
}

function getNodeIcon(label) {
  switch (label) {
    case 'Repository':
      return FolderGit2;
    case 'File':
      return FileCode;
    case 'Function':
      return Code2;
    case 'Endpoint':
      return Globe;
    case 'Class':
      return Layers;
    case 'Language':
      return FileText;
    case 'Technology':
      return Boxes;
    case 'Memory':
      return Brain;
    case 'Project':
      return Folder;
    case 'Agent':
      return Bot;
    default:
      return Network;
  }
}

export function getNodeDisplayName(node) {
  if (!node) return 'Unknown';
  const props = node.properties || {};

  if (props.name && typeof props.name === 'string' && props.name.trim()) {
    return props.name.trim();
  }

  if (node.label === 'File') {
    if (props.name && typeof props.name === 'string') return props.name;
    if (props.path) {
      const parts = props.path.split(/[/\\]/);
      return parts[parts.length - 1];
    }
  }

  if (node.label === 'Endpoint') {
    if (props.path) {
      return `${props.method || 'GET'} ${props.path}`;
    }
  }

  if (node.label === 'Function' || node.label === 'Class') {
    if (props.name) return props.name;
  }

  if (node.label === 'Memory' && props.content) {
    return props.content.length > 25 ? props.content.slice(0, 22) + '...' : props.content;
  }

  if (props.title) return props.title;

  if (typeof node.id === 'string' && node.id.includes('::')) {
    const segments = node.id.split('::');
    return segments[segments.length - 1];
  }

  if (typeof node.id === 'string' && (node.id.includes('/') || node.id.includes('\\'))) {
    const parts = node.id.split(/[/\\]/);
    return parts[parts.length - 1];
  }

  if (typeof node.id === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}/i.test(node.id)) {
    return `${node.label || 'Node'} (${node.id.slice(0, 8)})`;
  }

  return String(node.id || node.label || 'Node');
}

function NodeDetailPanel({ node, allNodes, allEdges, onClose, onSelectNodeId, onCenterNode }) {
  const [activeTab, setActiveTab] = useState('content');
  const [copiedId, setCopiedId] = useState(false);
  const [copiedContent, setCopiedContent] = useState(false);
  const [copiedKey, setCopiedKey] = useState(null);
  const [propSearch, setPropSearch] = useState('');

  const properties = node.properties || {};
  const label = node.label || 'Unknown';
  const IconComponent = getNodeIcon(label);
  const nodeColor = getColor(label);

  // Compute connections from graph data
  const relationships = useMemo(() => {
    if (!node || !allEdges) return { incoming: [], outgoing: [], total: 0 };
    const incoming = [];
    const outgoing = [];
    const nodeMap = new Map((allNodes || []).map(n => [n.id, n]));

    (allEdges || []).forEach(e => {
      const srcId = typeof e.source === 'object' ? e.source.id : e.source;
      const tgtId = typeof e.target === 'object' ? e.target.id : e.target;

      if (srcId === node.id) {
        const tgtNode = nodeMap.get(tgtId) || { id: tgtId, label: 'Unknown' };
        outgoing.push({
          id: e.id || `${srcId}-${tgtId}`,
          relType: e.label || 'CONNECTED_TO',
          targetId: tgtId,
          targetLabel: tgtNode.label || 'Unknown',
          targetName: getNodeDisplayName(tgtNode),
          targetColor: getColor(tgtNode.label),
        });
      }
      if (tgtId === node.id) {
        const srcNode = nodeMap.get(srcId) || { id: srcId, label: 'Unknown' };
        incoming.push({
          id: e.id || `${srcId}-${tgtId}`,
          relType: e.label || 'CONNECTED_TO',
          sourceId: srcId,
          sourceLabel: srcNode.label || 'Unknown',
          sourceName: getNodeDisplayName(srcNode),
          sourceColor: getColor(srcNode.label),
        });
      }
    });

    return { incoming, outgoing, total: incoming.length + outgoing.length };
  }, [node, allNodes, allEdges]);

  // Extract primary content string if available
  const rawContent = properties.content || properties.text || properties.description || properties.summary || properties.code || properties.body || properties.snippet || properties.docstring || properties.documentation || properties.rationale || properties.notes || '';

  const handleCopyId = useCallback(() => {
    if (node?.id) {
      navigator.clipboard.writeText(node.id);
      setCopiedId(true);
      setTimeout(() => setCopiedId(false), 2000);
    }
  }, [node?.id]);

  const handleCopyContent = useCallback((text) => {
    if (text) {
      navigator.clipboard.writeText(typeof text === 'string' ? text : JSON.stringify(text, null, 2));
      setCopiedContent(true);
      setTimeout(() => setCopiedContent(false), 2000);
    }
  }, []);

  const handleCopyProp = useCallback((key, value) => {
    navigator.clipboard.writeText(String(value));
    setCopiedKey(key);
    setTimeout(() => setCopiedKey(null), 2000);
  }, []);

  // Display name formatting
  const displayName = getNodeDisplayName(node);

  const filteredProperties = useMemo(() => {
    const entries = Object.entries(node?.properties || {});
    if (!propSearch.trim()) return entries;
    const q = propSearch.toLowerCase();
    return entries.filter(([k, v]) => k.toLowerCase().includes(q) || String(v).toLowerCase().includes(q));
  }, [node?.properties, propSearch]);

  return (
    <div
      className="w-96 lg:w-[420px] bg-gray-900 border border-gray-800 rounded-xl flex flex-col flex-shrink-0 overflow-hidden shadow-2xl transition-all"
      style={{ height: 'calc(100vh - 280px)', minHeight: '420px' }}
    >
      {/* Header */}
      <div className="p-4 border-b border-gray-800 bg-gray-900/90 backdrop-blur sticky top-0 z-10 space-y-3">
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2 min-w-0">
            <span className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ backgroundColor: nodeColor }} />
            <span
              className="text-xs font-semibold px-2 py-0.5 rounded text-white flex items-center gap-1.5 flex-shrink-0"
              style={{ backgroundColor: `${nodeColor}33`, color: nodeColor }}
            >
              <IconComponent className="w-3.5 h-3.5" />
              {label}
            </span>
          </div>
          <div className="flex items-center gap-1">
            <button
              onClick={() => onCenterNode(node)}
              title="Focus in graph"
              className="p-1.5 text-gray-400 hover:text-purple-300 hover:bg-gray-800 rounded-lg transition-colors cursor-pointer"
            >
              <Focus className="w-4 h-4" />
            </button>
            <button
              onClick={handleCopyId}
              title="Copy ID"
              className="p-1.5 text-gray-400 hover:text-purple-300 hover:bg-gray-800 rounded-lg transition-colors cursor-pointer"
            >
              {copiedId ? <Check className="w-4 h-4 text-green-400" /> : <Copy className="w-4 h-4" />}
            </button>
            <button
              onClick={onClose}
              title="Close panel"
              className="p-1.5 text-gray-400 hover:text-gray-200 hover:bg-gray-800 rounded-lg transition-colors cursor-pointer"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        <div>
          <h3 className="text-base font-bold text-gray-100 break-words leading-snug">{displayName}</h3>
          {node.id !== displayName && (
            <p className="text-xs font-mono text-gray-400 break-all mt-0.5 line-clamp-2" title={node.id}>
              {node.id}
            </p>
          )}
        </div>

        {/* Tab Navigation */}
        <div className="flex bg-gray-950 p-1 rounded-lg border border-gray-800/80 gap-1 text-xs">
          <button
            onClick={() => setActiveTab('content')}
            className={`flex-1 py-1.5 px-2 rounded-md font-medium flex items-center justify-center gap-1.5 transition-colors cursor-pointer ${
              activeTab === 'content' ? 'bg-purple-600 text-white shadow-sm' : 'text-gray-400 hover:text-gray-200 hover:bg-gray-900'
            }`}
          >
            <FileText className="w-3.5 h-3.5" />
            Content
          </button>
          <button
            onClick={() => setActiveTab('properties')}
            className={`flex-1 py-1.5 px-2 rounded-md font-medium flex items-center justify-center gap-1.5 transition-colors cursor-pointer ${
              activeTab === 'properties' ? 'bg-purple-600 text-white shadow-sm' : 'text-gray-400 hover:text-gray-200 hover:bg-gray-900'
            }`}
          >
            <Tag className="w-3.5 h-3.5" />
            Properties ({Object.keys(properties).length})
          </button>
          <button
            onClick={() => setActiveTab('relationships')}
            className={`flex-1 py-1.5 px-2 rounded-md font-medium flex items-center justify-center gap-1.5 transition-colors cursor-pointer ${
              activeTab === 'relationships' ? 'bg-purple-600 text-white shadow-sm' : 'text-gray-400 hover:text-gray-200 hover:bg-gray-900'
            }`}
          >
            <Link2 className="w-3.5 h-3.5" />
            Links ({relationships.total})
          </button>
        </div>
      </div>

      {/* Tab Content */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        {activeTab === 'content' && (
          <div className="space-y-4">
            {/* 1. Specialized Views Based on Node Label */}
            {label === 'Memory' && (
              <div className="space-y-3">
                <div className="bg-purple-950/30 border border-purple-900/50 rounded-xl p-4 space-y-3">
                  <div className="flex items-center justify-between text-xs text-purple-300">
                    <span className="font-semibold uppercase tracking-wider flex items-center gap-1">
                      <Brain className="w-3.5 h-3.5" /> Memory Record
                    </span>
                    <button
                      onClick={() => handleCopyContent(properties.content || rawContent)}
                      className="flex items-center gap-1 text-purple-400 hover:text-purple-200 transition-colors cursor-pointer"
                    >
                      {copiedContent ? <Check className="w-3.5 h-3.5 text-green-400" /> : <Copy className="w-3.5 h-3.5" />}
                      <span>{copiedContent ? 'Copied' : 'Copy'}</span>
                    </button>
                  </div>
                  <p className="text-sm text-gray-100 leading-relaxed whitespace-pre-wrap">
                    {properties.content || rawContent || 'No memory text recorded.'}
                  </p>
                  <div className="flex flex-wrap gap-2 pt-2 border-t border-purple-900/30">
                    {properties.type && (
                      <span className="px-2 py-0.5 rounded bg-purple-600/30 text-purple-300 text-xs font-mono">
                        {properties.type}
                      </span>
                    )}
                    {properties.scope && (
                      <span className="px-2 py-0.5 rounded bg-blue-600/30 text-blue-300 text-xs font-mono">
                        {properties.scope}
                      </span>
                    )}
                    {properties.status && (
                      <span className="px-2 py-0.5 rounded bg-gray-800 text-gray-300 text-xs font-mono">
                        {properties.status}
                      </span>
                    )}
                    {properties.confidence != null && (
                      <span className="px-2 py-0.5 rounded bg-emerald-600/30 text-emerald-300 text-xs font-mono">
                        {typeof properties.confidence === 'number'
                          ? `${(properties.confidence * 100).toFixed(0)}% Confidence`
                          : properties.confidence}
                      </span>
                    )}
                  </div>
                </div>
              </div>
            )}

            {label === 'Endpoint' && (
              <div className="space-y-3">
                <div className="space-y-1.5">
                  <div className="flex items-center justify-between">
                    <span className="text-xs text-gray-400 uppercase tracking-wider font-semibold">HTTP API Endpoint</span>
                    <button
                      onClick={() => handleCopyContent(`${properties.method || 'GET'} ${properties.path || '/'}`)}
                      className="text-xs text-gray-400 hover:text-purple-300 flex items-center gap-1 cursor-pointer"
                    >
                      {copiedContent ? <Check className="w-3 h-3 text-green-400" /> : <Copy className="w-3 h-3" />}
                      <span>{copiedContent ? 'Copied' : 'Copy Route'}</span>
                    </button>
                  </div>
                  <div className="bg-gray-950 border border-gray-800 rounded-lg p-3 font-mono text-xs flex items-center gap-2 overflow-x-auto">
                    <span className="px-2 py-0.5 rounded font-bold bg-cyan-950 text-cyan-400 border border-cyan-800 text-[11px]">
                      {properties.method || 'GET'}
                    </span>
                    <span className="text-gray-100 font-bold">{properties.path || '/'}</span>
                  </div>
                </div>

                {properties.handler && (
                  <div className="bg-gray-950 border border-gray-800/80 rounded-lg p-3 space-y-1 text-xs">
                    <span className="text-gray-500 block">Handler Function</span>
                    <span className="font-mono text-purple-400 font-semibold">{properties.handler}()</span>
                  </div>
                )}

                {properties.file && (
                  <div className="bg-gray-950 border border-gray-800/80 rounded-lg p-3 space-y-1 text-xs">
                    <span className="text-gray-500 block">Declared In</span>
                    <span className="font-mono text-gray-300 break-all">{properties.file}</span>
                  </div>
                )}
              </div>
            )}

            {label === 'Function' && (
              <div className="space-y-3">
                <div className="space-y-1.5">
                  <div className="flex items-center justify-between">
                    <span className="text-xs text-gray-400 uppercase tracking-wider font-semibold">Function Signature</span>
                    <button
                      onClick={() => handleCopyContent(`function ${properties.name || displayName}(${properties.parameters || ''}): ${properties.returnType || 'void'}`)}
                      className="text-xs text-gray-400 hover:text-purple-300 flex items-center gap-1 cursor-pointer"
                    >
                      {copiedContent ? <Check className="w-3 h-3 text-green-400" /> : <Copy className="w-3 h-3" />}
                      <span>{copiedContent ? 'Copied' : 'Copy Code'}</span>
                    </button>
                  </div>
                  <div className="bg-gray-950 border border-gray-800 rounded-lg p-3 font-mono text-xs text-emerald-400 overflow-x-auto">
                    <span className="text-purple-400">function </span>
                    <span className="text-yellow-300 font-bold">{properties.name || displayName}</span>
                    <span className="text-gray-300">({properties.parameters || ''})</span>
                    <span className="text-gray-500">: </span>
                    <span className="text-cyan-400">{properties.returnType || 'void'}</span>
                  </div>
                </div>

                {properties.file && (
                  <div className="bg-gray-950 border border-gray-800/80 rounded-lg p-3 space-y-1 text-xs">
                    <span className="text-gray-500 block">Defined in File</span>
                    <span className="font-mono text-gray-300 break-all">{properties.file}</span>
                  </div>
                )}

                {properties.parameters && (
                  <div className="bg-gray-950 border border-gray-800/80 rounded-lg p-3 space-y-1.5 text-xs">
                    <span className="text-gray-500 block">Parameters</span>
                    <code className="text-amber-300 font-mono block bg-gray-900 p-2 rounded border border-gray-800 break-all">
                      {properties.parameters}
                    </code>
                  </div>
                )}
              </div>
            )}

            {label === 'Class' && (
              <div className="space-y-3">
                <div className="space-y-1.5">
                  <div className="flex items-center justify-between">
                    <span className="text-xs text-gray-400 uppercase tracking-wider font-semibold">Class Declaration</span>
                    <button
                      onClick={() => handleCopyContent(`${properties.type || 'class'} ${properties.name || displayName} {\n  // defined in ${properties.file || ''}\n}`)}
                      className="text-xs text-gray-400 hover:text-purple-300 flex items-center gap-1 cursor-pointer"
                    >
                      {copiedContent ? <Check className="w-3 h-3 text-green-400" /> : <Copy className="w-3 h-3" />}
                      <span>{copiedContent ? 'Copied' : 'Copy Code'}</span>
                    </button>
                  </div>
                  <div className="bg-gray-950 border border-gray-800 rounded-lg p-3 font-mono text-xs text-amber-300 overflow-x-auto">
                    <span className="text-purple-400">{properties.type || 'class'} </span>
                    <span className="text-yellow-300 font-bold">{properties.name || displayName}</span>
                    <span className="text-gray-400"> {'{'}</span>
                    <div className="text-gray-500 pl-4 py-1">// Defined in {properties.file || 'source'}</div>
                    <span className="text-gray-400">{'}'}</span>
                  </div>
                </div>

                {properties.file && (
                  <div className="bg-gray-950 border border-gray-800/80 rounded-lg p-3 space-y-1 text-xs">
                    <span className="text-gray-500 block">Source File</span>
                    <span className="font-mono text-gray-300 break-all">{properties.file}</span>
                  </div>
                )}
              </div>
            )}

            {label === 'File' && (
              <div className="space-y-3">
                <div className="bg-gray-950 border border-gray-800 rounded-lg p-3 space-y-2 text-xs">
                  <div className="flex items-center justify-between">
                    <span className="text-gray-500">File Path</span>
                    {properties.language && (
                      <span className="px-2 py-0.5 rounded bg-cyan-950 text-cyan-400 border border-cyan-800/50 font-mono text-[10px]">
                        {properties.language}
                      </span>
                    )}
                  </div>
                  <p className="font-mono text-gray-200 break-all bg-gray-900 p-2 rounded border border-gray-800">
                    {properties.path || node.id}
                  </p>
                </div>

                {/* Definitions inside this file from outgoing DEFINES relations */}
                {relationships.outgoing.filter(r => r.relType === 'DEFINES').length > 0 && (
                  <div className="space-y-2">
                    <span className="text-xs text-gray-400 uppercase tracking-wider font-semibold">
                      Declared Symbols ({relationships.outgoing.filter(r => r.relType === 'DEFINES').length})
                    </span>
                    <div className="flex flex-wrap gap-1.5">
                      {relationships.outgoing
                        .filter(r => r.relType === 'DEFINES')
                        .map(r => (
                          <button
                            key={r.targetId}
                            onClick={() => onSelectNodeId(r.targetId)}
                            className="flex items-center gap-1.5 px-2.5 py-1 rounded bg-gray-950 border border-gray-800 text-xs font-mono hover:border-purple-500 hover:text-purple-300 transition-colors text-left cursor-pointer"
                          >
                            <span className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: r.targetColor }} />
                            <span className="text-gray-300">{r.targetName}</span>
                          </button>
                        ))}
                    </div>
                  </div>
                )}
              </div>
            )}

            {label === 'Repository' && (
              <div className="space-y-3">
                <div className="bg-gray-950 border border-gray-800 rounded-lg p-3.5 space-y-3 text-xs">
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <h4 className="font-bold text-sm text-gray-100">{properties.name || displayName}</h4>
                      {properties.owner && <p className="text-gray-400">by {properties.owner}</p>}
                    </div>
                    {properties.branch && (
                      <span className="px-2 py-0.5 rounded bg-purple-950/60 text-purple-400 border border-purple-800/40 font-mono text-[10px]">
                        {properties.branch}
                      </span>
                    )}
                  </div>

                  {properties.url && (
                    <a
                      href={properties.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="flex items-center gap-1.5 text-purple-400 hover:text-purple-300 underline break-all"
                    >
                      <ExternalLink className="w-3.5 h-3.5 flex-shrink-0" />
                      <span>{properties.url}</span>
                    </a>
                  )}

                  {properties.path && (
                    <div>
                      <span className="text-gray-500 block mb-1">Local Path</span>
                      <code className="text-gray-300 font-mono block bg-gray-900 p-1.5 rounded border border-gray-800/80 break-all">
                        {properties.path}
                      </code>
                    </div>
                  )}

                  {properties.languages && (
                    <div>
                      <span className="text-gray-500 block mb-1.5">Languages</span>
                      <div className="flex flex-wrap gap-1">
                        {properties.languages.split(',').map(lang => (
                          <span key={lang.trim()} className="px-2 py-0.5 rounded bg-cyan-950 text-cyan-400 border border-cyan-800/40 font-mono text-[10px]">
                            {lang.trim()}
                          </span>
                        ))}
                      </div>
                    </div>
                  )}

                  {properties.frameworks && (
                    <div>
                      <span className="text-gray-500 block mb-1.5">Frameworks & Tools</span>
                      <div className="flex flex-wrap gap-1">
                        {properties.frameworks.split(',').map(fw => (
                          <span key={fw.trim()} className="px-2 py-0.5 rounded bg-blue-950 text-blue-400 border border-blue-800/40 font-mono text-[10px]">
                            {fw.trim()}
                          </span>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              </div>
            )}

            {(label === 'Language' || label === 'Technology') && (
              <div className="space-y-3">
                <div className="bg-gray-950 border border-gray-800 rounded-lg p-3.5 space-y-3 text-xs">
                  <div className="flex items-center justify-between">
                    <span className="text-gray-500">Classification</span>
                    <span
                      className="px-2 py-0.5 rounded font-mono text-[10px]"
                      style={{ backgroundColor: `${nodeColor}22`, color: nodeColor }}
                    >
                      {properties.category || (label === 'Language' ? 'Programming / Markup' : 'Technology Stack')}
                    </span>
                  </div>
                  <div>
                    <span className="text-gray-500 block mb-1">Identifier</span>
                    <p className="text-sm font-bold text-gray-200 font-mono">{properties.name || node.id}</p>
                  </div>
                  <div className="pt-2 border-t border-gray-800/80 text-gray-400">
                    Connected to <span className="text-purple-400 font-semibold">{relationships.total}</span> items in the knowledge graph.
                  </div>
                </div>

                {relationships.incoming.length > 0 && (
                  <div className="space-y-2">
                    <span className="text-xs text-gray-400 uppercase tracking-wider font-semibold">
                      Associated Ingested Sources ({relationships.incoming.length})
                    </span>
                    <div className="space-y-1.5">
                      {relationships.incoming.map(r => (
                        <button
                          key={r.sourceId}
                          onClick={() => onSelectNodeId(r.sourceId)}
                          className="w-full flex items-center justify-between p-2 rounded bg-gray-950 border border-gray-800 text-xs hover:border-purple-500 hover:text-purple-300 transition-colors text-left cursor-pointer"
                        >
                          <div className="flex items-center gap-2 min-w-0">
                            <span className="w-2 h-2 rounded-full flex-shrink-0" style={{ backgroundColor: r.sourceColor }} />
                            <span className="text-gray-200 font-medium truncate">{r.sourceName}</span>
                          </div>
                          <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-gray-900 text-gray-500 border border-gray-800 flex-shrink-0">
                            {r.relType}
                          </span>
                        </button>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}

            {label === 'Project' && (
              <div className="space-y-3">
                <div className="bg-gray-950 border border-gray-800 rounded-lg p-3.5 space-y-3 text-xs">
                  <h4 className="font-bold text-sm text-gray-100">{properties.name || displayName}</h4>
                  {properties.description && (
                    <p className="text-gray-300 leading-relaxed bg-gray-900 p-2.5 rounded border border-gray-800 whitespace-pre-wrap">
                      {properties.description}
                    </p>
                  )}
                  {properties.path && (
                    <div>
                      <span className="text-gray-500 block mb-1">Project Path</span>
                      <code className="text-gray-300 font-mono block bg-gray-900 p-1.5 rounded border border-gray-800 break-all">
                        {properties.path}
                      </code>
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* 2. Generic Raw/Full Content Box (if content exists or for any other node type) */}
            {rawContent && label !== 'Memory' && (
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-xs text-gray-400 uppercase tracking-wider font-semibold">Node Content</span>
                  <button
                    onClick={() => handleCopyContent(rawContent)}
                    className="text-xs text-gray-400 hover:text-purple-300 flex items-center gap-1 cursor-pointer"
                  >
                    {copiedContent ? <Check className="w-3 h-3 text-green-400" /> : <Copy className="w-3 h-3" />}
                    <span>{copiedContent ? 'Copied' : 'Copy'}</span>
                  </button>
                </div>
                <div className="bg-gray-950 border border-gray-800 rounded-lg p-3 text-xs font-mono text-gray-200 whitespace-pre-wrap break-words max-h-60 overflow-y-auto leading-relaxed">
                  {rawContent}
                </div>
              </div>
            )}

            {/* 3. Quick Overview if no special views matched */}
            {!['Memory', 'Function', 'Class', 'File', 'Repository', 'Language', 'Technology', 'Project'].includes(label) && !rawContent && (
              <div className="bg-gray-950 border border-gray-800 rounded-lg p-4 text-center text-xs text-gray-500 space-y-2">
                <p>No dedicated content text is stored on this node.</p>
                <p className="text-gray-400">View the Properties and Links tabs for details.</p>
              </div>
            )}
          </div>
        )}

        {activeTab === 'properties' && (
          <div className="space-y-3">
            {Object.keys(properties).length > 4 && (
              <div className="relative">
                <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-500" />
                <input
                  type="text"
                  value={propSearch}
                  onChange={(e) => setPropSearch(e.target.value)}
                  placeholder="Filter properties..."
                  className="w-full bg-gray-950 border border-gray-800 rounded-lg pl-8 pr-3 py-1.5 text-xs text-gray-200 placeholder-gray-500 focus:outline-none focus:border-purple-500"
                />
              </div>
            )}

            {filteredProperties.length === 0 ? (
              <p className="text-xs text-gray-500 text-center py-4">No matching properties</p>
            ) : (
              <div className="space-y-2">
                {filteredProperties.map(([key, value]) => {
                  const valStr = typeof value === 'object' ? JSON.stringify(value) : String(value);
                  const isUrl = typeof value === 'string' && (value.startsWith('http://') || value.startsWith('https://'));
                  return (
                    <div
                      key={key}
                      className="bg-gray-950 border border-gray-800/80 rounded-lg p-2.5 text-xs space-y-1 group hover:border-gray-700 transition-colors"
                    >
                      <div className="flex items-center justify-between">
                        <span className="font-mono text-purple-300 font-semibold">{key}</span>
                        <button
                          onClick={() => handleCopyProp(key, valStr)}
                          className="opacity-0 group-hover:opacity-100 text-gray-400 hover:text-gray-200 transition-opacity cursor-pointer"
                          title="Copy value"
                        >
                          {copiedKey === key ? (
                            <Check className="w-3 h-3 text-green-400" />
                          ) : (
                            <Copy className="w-3 h-3" />
                          )}
                        </button>
                      </div>
                      {isUrl ? (
                        <a
                          href={value}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="text-cyan-400 hover:underline break-all block font-mono text-[11px]"
                        >
                          {value}
                        </a>
                      ) : (
                        <div className="text-gray-300 break-words font-mono text-[11px] max-h-32 overflow-y-auto leading-relaxed">
                          {valStr}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {activeTab === 'relationships' && (
          <div className="space-y-4">
            {relationships.total === 0 ? (
              <div className="bg-gray-950 border border-gray-800 rounded-lg p-4 text-center text-xs text-gray-500">
                No connected links found for this node.
              </div>
            ) : (
              <>
                {relationships.outgoing.length > 0 && (
                  <div className="space-y-2">
                    <span className="text-xs text-gray-400 uppercase tracking-wider font-semibold flex items-center gap-1.5">
                      <ArrowRight className="w-3.5 h-3.5 text-purple-400" />
                      Outgoing Connections ({relationships.outgoing.length})
                    </span>
                    <div className="space-y-1.5">
                      {relationships.outgoing.map((r, idx) => (
                        <button
                          key={`${r.id}-${idx}`}
                          onClick={() => onSelectNodeId(r.targetId)}
                          className="w-full flex items-center justify-between p-2.5 rounded-lg bg-gray-950 border border-gray-800 text-xs hover:border-purple-500 hover:bg-gray-900/60 transition-all text-left group cursor-pointer"
                        >
                          <div className="flex items-center gap-2 min-w-0 pr-2">
                            <span className="w-2 h-2 rounded-full flex-shrink-0" style={{ backgroundColor: r.targetColor }} />
                            <div className="truncate">
                              <span className="text-gray-200 font-medium group-hover:text-purple-300 transition-colors block truncate">
                                {r.targetName}
                              </span>
                              <span className="text-[10px] text-gray-500 font-mono">{r.targetLabel}</span>
                            </div>
                          </div>
                          <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-purple-950/60 text-purple-300 border border-purple-800/40 flex-shrink-0">
                            {r.relType}
                          </span>
                        </button>
                      ))}
                    </div>
                  </div>
                )}

                {relationships.incoming.length > 0 && (
                  <div className="space-y-2">
                    <span className="text-xs text-gray-400 uppercase tracking-wider font-semibold flex items-center gap-1.5">
                      <ArrowLeft className="w-3.5 h-3.5 text-cyan-400" />
                      Incoming Connections ({relationships.incoming.length})
                    </span>
                    <div className="space-y-1.5">
                      {relationships.incoming.map((r, idx) => (
                        <button
                          key={`${r.id}-${idx}`}
                          onClick={() => onSelectNodeId(r.sourceId)}
                          className="w-full flex items-center justify-between p-2.5 rounded-lg bg-gray-950 border border-gray-800 text-xs hover:border-cyan-500 hover:bg-gray-900/60 transition-all text-left group cursor-pointer"
                        >
                          <div className="flex items-center gap-2 min-w-0 pr-2">
                            <span className="w-2 h-2 rounded-full flex-shrink-0" style={{ backgroundColor: r.sourceColor }} />
                            <div className="truncate">
                              <span className="text-gray-200 font-medium group-hover:text-cyan-300 transition-colors block truncate">
                                {r.sourceName}
                              </span>
                              <span className="text-[10px] text-gray-500 font-mono">{r.sourceLabel}</span>
                            </div>
                          </div>
                          <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-cyan-950/60 text-cyan-300 border border-cyan-800/40 flex-shrink-0">
                            {r.relType}
                          </span>
                        </button>
                      ))}
                    </div>
                  </div>
                )}
              </>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

export default function KnowledgeGraph() {
  const [selectedNode, setSelectedNode] = useState(null);
  const [search, setSearch] = useState('');
  const [labelFilter, setLabelFilter] = useState(null);
  const [graphReady, setGraphReady] = useState(false);
  const fgRef = useRef();

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['graphVisual'],
    queryFn: () => brainApi.getGraphVisual(300),
  });

  const { data: stats } = useQuery({
    queryKey: ['graphStats'],
    queryFn: () => brainApi.getGraphStats(),
  });

  useEffect(() => {
    setGraphReady(false);
  }, [data, labelFilter, search]);

  const graphData = useMemo(() => {
    if (!data || !data.nodes || !data.edges) return { nodes: [], links: [] };

    const nodes = (Array.isArray(data.nodes) ? data.nodes : []).map(n => {
      const displayName = getNodeDisplayName(n);
      return {
        id: n.id,
        label: n.label,
        properties: n.properties || {},
        name: displayName,
        color: getColor(n.label),
        size: n.label === 'Project' ? 10 : n.label === 'Repository' ? 9 : n.label === 'File' ? 7 : 5,
      };
    });

    const nodeIdSet = new Set(nodes.map(n => n.id));

    const links = (Array.isArray(data.edges) ? data.edges : [])
      .filter(e => e.source && e.target && nodeIdSet.has(e.source) && nodeIdSet.has(e.target))
      .map(e => ({
        source: e.source,
        target: e.target,
        label: e.label,
      }));

    let filteredNodes = nodes;
    let filteredLinks = links;

    if (labelFilter) {
      const matchIds = new Set(nodes.filter(n => n.label === labelFilter).map(n => n.id));
      filteredLinks = links.filter(l => matchIds.has(l.source) || matchIds.has(l.target));
      const connectedIds = new Set();
      filteredLinks.forEach(l => { connectedIds.add(l.source); connectedIds.add(l.target); });
      filteredNodes = nodes.filter(n => connectedIds.has(n.id));
    }

    if (search) {
      const q = search.toLowerCase();
      const matchIds = new Set(filteredNodes.filter(n => {
        const name = (n.name || getNodeDisplayName(n)).toLowerCase();
        return name.includes(q) || n.id.toLowerCase().includes(q) || n.label.toLowerCase().includes(q);
      }).map(n => n.id));
      filteredNodes = filteredNodes.filter(n => matchIds.has(n.id));
      filteredLinks = filteredLinks.filter(l => matchIds.has(l.source) || matchIds.has(l.target));
    }

    return { nodes: filteredNodes, links: filteredLinks };
  }, [data, labelFilter, search]);

  useEffect(() => {
    if (graphData.nodes.length > 0) {
      const timer = setTimeout(() => setGraphReady(true), 100);
      return () => clearTimeout(timer);
    }
  }, [graphData]);

  const handleNodeClick = useCallback((node) => {
    setSelectedNode(prev => prev?.id === node.id ? null : node);
  }, []);

  const handleSelectNodeById = useCallback((id) => {
    const target = graphData.nodes.find(n => n.id === id);
    if (target) {
      setSelectedNode(target);
      if (fgRef.current && target.x != null && target.y != null) {
        fgRef.current.centerAt(target.x, target.y, 400);
        fgRef.current.zoom(3, 400);
      }
    } else {
      const rawNode = (data?.nodes || []).find(n => n.id === id);
      if (rawNode) {
        setSelectedNode({
          id: rawNode.id,
          label: rawNode.label,
          properties: rawNode.properties || {},
          color: getColor(rawNode.label),
          size: 6,
        });
      }
    }
  }, [graphData.nodes, data]);

  const handleCenterNode = useCallback((node) => {
    if (fgRef.current && node?.x != null && node?.y != null) {
      fgRef.current.centerAt(node.x, node.y, 400);
      fgRef.current.zoom(3, 400);
    }
  }, []);

  const nodeCanvasObject = useCallback((node, ctx) => {
    if (!node || node.x == null || node.y == null) return;
    const size = node.size || 6;
    const isSelected = selectedNode?.id === node.id;

    if (isSelected) {
      ctx.beginPath();
      ctx.arc(node.x, node.y, size + 3.5, 0, 2 * Math.PI);
      ctx.fillStyle = 'rgba(168, 85, 247, 0.35)';
      ctx.fill();
      ctx.strokeStyle = '#ffffff';
      ctx.lineWidth = 1.5;
      ctx.stroke();
    }

    ctx.beginPath();
    ctx.arc(node.x, node.y, size, 0, 2 * Math.PI);
    ctx.fillStyle = node.color || '#64748b';
    ctx.fill();

    ctx.strokeStyle = isSelected ? '#ffffff' : 'rgba(255, 255, 255, 0.25)';
    ctx.lineWidth = isSelected ? 1.5 : 0.8;
    ctx.stroke();

    const displayName = node.name || getNodeDisplayName(node);
    const labelText = displayName.length > 24 ? displayName.slice(0, 22) + '…' : displayName;

    const fontSize = 3.6;
    ctx.font = `600 ${fontSize}px system-ui, -apple-system, sans-serif`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'top';

    const textWidth = ctx.measureText(labelText).width;
    const padding = 1.2;
    const pillHeight = fontSize + 1.8;
    const pillY = node.y + size + 1.8;

    ctx.fillStyle = 'rgba(3, 7, 18, 0.85)';
    ctx.beginPath();
    ctx.roundRect(
      node.x - textWidth / 2 - padding,
      pillY,
      textWidth + padding * 2,
      pillHeight,
      1.5
    );
    ctx.fill();
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.12)';
    ctx.lineWidth = 0.4;
    ctx.stroke();

    ctx.fillStyle = isSelected ? '#38bdf8' : '#f1f5f9';
    ctx.fillText(labelText, node.x, pillY + 0.8);
  }, [selectedNode]);

  const linkCanvasObject = useCallback((link, ctx) => {
    if (!link?.source || !link?.target) return;
    if (link.source.x == null || link.target.x == null) return;
    ctx.beginPath();
    ctx.moveTo(link.source.x, link.source.y);
    ctx.lineTo(link.target.x, link.target.y);
    ctx.strokeStyle = 'rgba(100, 116, 139, 0.3)';
    ctx.lineWidth = 0.5;
    ctx.stroke();
  }, []);

  const labels = Array.isArray(stats?.labels) ? stats.labels : [];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold flex items-center gap-2">
          <Network className="w-6 h-6 text-purple-400" />
          Knowledge Graph
        </h2>
        <button
          onClick={() => refetch()}
          className="flex items-center gap-2 text-sm text-gray-400 hover:text-gray-200 transition-colors cursor-pointer"
        >
          <RefreshCw className="w-4 h-4" />
          Refresh
        </button>
      </div>

      {stats && (
        <div className="flex gap-4 text-sm text-gray-400">
          <span>{stats.nodeCount} nodes</span>
          <span>{stats.relationshipCount} relationships</span>
        </div>
      )}

      <div className="flex flex-wrap gap-3">
        <div className="relative flex-1 max-w-xs">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-500" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search nodes..."
            className="w-full bg-gray-900 border border-gray-700 rounded-lg pl-9 pr-8 py-2 text-sm focus:outline-none focus:border-purple-500"
          />
          {search && (
            <button onClick={() => setSearch('')} className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-500 hover:text-gray-300">
              <X className="w-4 h-4" />
            </button>
          )}
        </div>
        <div className="flex gap-1.5 flex-wrap">
          {labels.map((label) => (
            <button
              key={label}
              onClick={() => setLabelFilter(prev => prev === label ? null : label)}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition-colors cursor-pointer ${
                labelFilter === label
                  ? 'text-white'
                  : 'bg-gray-800 text-gray-400 hover:bg-gray-700'
              }`}
              style={labelFilter === label ? { backgroundColor: getColor(label) } : {}}
            >
              <span className="w-2 h-2 rounded-full" style={{ backgroundColor: getColor(label) }} />
              {label}
            </button>
          ))}
        </div>
      </div>

      <div className="flex gap-4">
        <div className={`bg-gray-900 border border-gray-800 rounded-xl overflow-hidden ${selectedNode ? 'flex-1' : 'w-full'}`}
             style={{ height: 'calc(100vh - 280px)', minHeight: '400px' }}>
          {isLoading ? (
            <div className="flex items-center justify-center h-full text-gray-500">Loading graph...</div>
          ) : graphData.nodes.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-full text-gray-500 gap-2">
              <Network className="w-12 h-12 opacity-30" />
              <p>No graph data found. Add a repository to populate the knowledge graph.</p>
            </div>
          ) : graphReady ? (
            <Suspense fallback={<div className="flex items-center justify-center h-full text-gray-500">Loading graph library...</div>}>
              <ReactForceGraph2D
                ref={fgRef}
                graphData={graphData}
                nodeCanvasObject={nodeCanvasObject}
                linkCanvasObject={linkCanvasObject}
                onNodeClick={handleNodeClick}
                nodeLabel={(node) => `${node.label}: ${node.name || getNodeDisplayName(node)}`}
                nodeRelSize={6}
                linkDirectionalParticles={0}
                backgroundColor="#030712"
                d3AlphaDecay={0.02}
                d3VelocityDecay={0.3}
                warmupTicks={100}
                cooldownTicks={200}
              />
            </Suspense>
          ) : (
            <div className="flex items-center justify-center h-full text-gray-500">Initializing graph...</div>
          )}
        </div>

        {selectedNode && (
          <NodeDetailPanel
            node={selectedNode}
            allNodes={data?.nodes || graphData.nodes}
            allEdges={data?.edges || graphData.links}
            onClose={() => setSelectedNode(null)}
            onSelectNodeId={handleSelectNodeById}
            onCenterNode={handleCenterNode}
          />
        )}
      </div>
    </div>
  );
}

