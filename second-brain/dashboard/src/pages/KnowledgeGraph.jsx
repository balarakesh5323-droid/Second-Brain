import { useState, useCallback, useRef, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { brainApi } from '../api/client';
import { Network, RefreshCw, Search, X } from 'lucide-react';
import ReactForceGraph2D from 'react-force-graph-2d';

const NODE_COLORS = {
  Repository: '#a855f7',
  Technology: '#3b82f6',
  Language: '#22d3ee',
  File: '#6b7280',
  Class: '#f59e0b',
  Function: '#10b981',
  Module: '#ef4444',
  Unknown: '#64748b',
};

function getColor(label) {
  return NODE_COLORS[label] || NODE_COLORS.Unknown;
}

export default function KnowledgeGraph() {
  const [selectedNode, setSelectedNode] = useState(null);
  const [search, setSearch] = useState('');
  const [labelFilter, setLabelFilter] = useState(null);
  const fgRef = useRef();

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['graphVisual'],
    queryFn: () => brainApi.getGraphVisual(300).then(r => r.data),
  });

  const { data: stats } = useQuery({
    queryKey: ['graphStats'],
    queryFn: () => brainApi.getGraphStats().then(r => r.data),
  });

  const graphData = useMemo(() => {
    if (!data) return { nodes: [], links: [] };

    let nodes = data.nodes.map(n => ({
      id: n.id,
      label: n.label,
      properties: n.properties,
      color: getColor(n.label),
      size: 6,
    }));

    let links = data.edges.map(e => ({
      source: e.source,
      target: e.target,
      label: e.label,
    }));

    if (labelFilter) {
      const nodeIds = new Set(nodes.filter(n => n.label === labelFilter).map(n => n.id));
      links = links.filter(l => nodeIds.has(l.source) || nodeIds.has(l.target));
      const connectedIds = new Set();
      links.forEach(l => { connectedIds.add(l.source); connectedIds.add(l.target); });
      nodes = nodes.filter(n => connectedIds.has(n.id));
    }

    if (search) {
      const q = search.toLowerCase();
      const matchIds = new Set(nodes.filter(n =>
        n.id.toLowerCase().includes(q) || n.label.toLowerCase().includes(q)
      ).map(n => n.id));
      nodes = nodes.filter(n => matchIds.has(n.id));
      links = links.filter(l => matchIds.has(l.source) || matchIds.has(l.target));
    }

    return { nodes, links };
  }, [data, labelFilter, search]);

  const handleNodeClick = useCallback((node) => {
    setSelectedNode(prev => prev?.id === node.id ? null : node);
  }, []);

  const nodeCanvasObject = useCallback((node, ctx) => {
    const size = node.size || 6;
    ctx.beginPath();
    ctx.arc(node.x, node.y, size, 0, 2 * Math.PI);
    ctx.fillStyle = node.color || '#64748b';
    ctx.fill();

    if (selectedNode?.id === node.id) {
      ctx.beginPath();
      ctx.arc(node.x, node.y, size + 3, 0, 2 * Math.PI);
      ctx.strokeStyle = '#ffffff';
      ctx.lineWidth = 2;
      ctx.stroke();
    }

    const label = node.id.length > 30 ? node.id.slice(0, 27) + '...' : node.id;
    ctx.font = '3px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'top';
    ctx.fillStyle = '#d1d5db';
    ctx.fillText(label, node.x, node.y + size + 2);
  }, [selectedNode]);

  const linkCanvasObject = useCallback((link, ctx) => {
    ctx.beginPath();
    ctx.moveTo(link.source.x, link.source.y);
    ctx.lineTo(link.target.x, link.target.y);
    ctx.strokeStyle = 'rgba(100, 116, 139, 0.3)';
    ctx.lineWidth = 0.5;
    ctx.stroke();
  }, []);

  const labels = stats?.labels || [];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold flex items-center gap-2">
          <Network className="w-6 h-6 text-purple-400" />
          Knowledge Graph
        </h2>
        <button
          onClick={() => refetch()}
          className="flex items-center gap-2 text-sm text-gray-400 hover:text-gray-200 transition-colors"
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
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
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
          ) : (
            <ReactForceGraph2D
              ref={fgRef}
              graphData={graphData}
              nodeCanvasObject={nodeCanvasObject}
              linkCanvasObject={linkCanvasObject}
              onNodeClick={handleNodeClick}
              nodeRelSize={6}
              linkDirectionalParticles={0}
              backgroundColor="#030712"
              width={undefined}
              height={undefined}
              d3AlphaDecay={0.02}
              d3VelocityDecay={0.3}
              warmupTicks={100}
              cooldownTicks={200}
            />
          )}
        </div>

        {selectedNode && (
          <div className="w-80 bg-gray-900 border border-gray-800 rounded-xl p-5 flex-shrink-0 overflow-y-auto" style={{ height: 'calc(100vh - 280px)', minHeight: '400px' }}>
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                <span className="w-3 h-3 rounded-full" style={{ backgroundColor: getColor(selectedNode.label) }} />
                <span className="text-xs font-medium px-2 py-0.5 rounded bg-gray-800">{selectedNode.label}</span>
              </div>
              <button onClick={() => setSelectedNode(null)} className="text-gray-500 hover:text-gray-300">
                <X className="w-4 h-4" />
              </button>
            </div>
            <h3 className="font-mono text-sm font-semibold break-all mb-4">{selectedNode.id}</h3>
            {selectedNode.properties && Object.keys(selectedNode.properties).length > 0 && (
              <div className="space-y-2">
                <p className="text-xs text-gray-500 uppercase tracking-wider">Properties</p>
                <div className="space-y-1.5">
                  {Object.entries(selectedNode.properties).map(([key, value]) => (
                    <div key={key} className="text-sm">
                      <span className="text-gray-500">{key}: </span>
                      <span className="text-gray-300 break-all">{String(value)}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
