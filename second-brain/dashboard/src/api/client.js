import axios from 'axios';

const api = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' }
});

const safeArray = async (promise) => {
  try {
    const res = await promise;
    if (Array.isArray(res.data)) return res.data;
    if (Array.isArray(res.data?.content)) return res.data.content;
    if (Array.isArray(res.data?.items)) return res.data.items;
    return [];
  } catch (err) {
    console.warn('API call failed, returning empty array:', err.message);
    return [];
  }
};

export const brainApi = {
  // Memory
  searchMemory: (query) => api.get(`/memory/search?q=${encodeURIComponent(query)}`).then(r => Array.isArray(r.data) ? r.data : []).catch(() => []),
  searchSymbols: (query) => api.get(`/memory/symbols?q=${encodeURIComponent(query)}`).then(r => Array.isArray(r.data) ? r.data : []).catch(() => []),
  getMemories: () => safeArray(api.get('/memory')),
  createMemory: (data) => api.post('/memory', data),
  consolidateMemories: () => api.post('/memory/consolidate').then(r => r.data || {}),
  decayMemories: () => api.post('/memory/decay').then(r => r.data || {}),
  
  // Projects
  getProjects: () => safeArray(api.get('/projects')),
  createProject: (data) => api.post('/projects', data),
  
  // Repositories
  getRepositories: () => safeArray(api.get('/repositories')),
  addRepository: (url, projectId) => api.post('/repository-intel/add-url', { url, projectId }),
  
  // Agents
  getAgents: () => safeArray(api.get('/agents')),
  
  // Sessions
  getRecentSessions: () => safeArray(api.get('/sessions/recent')),
  
  // Events
  getRecentEvents: () => safeArray(api.get('/events')),
  
  // Decisions
  getRecentDecisions: () => safeArray(api.get('/decisions/recent')),
  
  // Tasks
  getOpenTasks: () => safeArray(api.get('/tasks/open')),
  
  // Skills
  getSkills: () => safeArray(api.get('/skills')),
  
  // Graph
  getGraphStats: () => api.get('/graph/stats').then(r => r.data || {}).catch(() => ({})),
  getGraphVisual: (limit) => api.get('/graph/visual', { params: { limit } }).then(r => r.data || { nodes: [], edges: [] }).catch(() => ({ nodes: [], edges: [] })),
  
  // Handoffs
  getHandoffs: () => safeArray(api.get('/handoffs')),
  getLatestHandoff: (repoId) => api.get(`/handoffs/repository/${repoId}/latest`).then(r => r.data || null).catch(() => null),
  
  // Documents & Media (PDFs, Markdown Specs, Architecture Images)
  getProjectDocuments: (projectId) => api.get(`/documents/project/${projectId}`).then(r => Array.isArray(r.data) ? r.data : []).catch(() => []),
  getAllDocuments: () => api.get('/documents').then(r => Array.isArray(r.data) ? r.data : []).catch(() => []),
  uploadProjectDocument: (projectId, formData) => api.post(`/documents/project/${projectId}/upload`, formData, { headers: { 'Content-Type': 'multipart/form-data' } }).then(r => r.data),
  createProjectNote: (projectId, noteData) => api.post(`/documents/project/${projectId}/note`, noteData).then(r => r.data),
  deleteDocument: (id) => api.delete(`/documents/${id}`),
  
  // Context & NL Query
  askBrain: (query, projectId, repositoryId) => api.post('/context/ask', { query, projectId, repositoryId }).then(r => r.data || {}).catch(err => ({ error: err.message })),
  assembleContext: (query, projectId, repositoryId) => api.post('/context/assemble', { query, projectId, repositoryId }).then(r => r.data || {}).catch(() => ({})),
  
  // Repository Sync & Git Hooks
  syncRepository: (repoId) => api.post(`/repository-intel/sync/${repoId}`).then(r => r.data || {}),
  getGitHookScript: (serverUrl) => api.get('/repository-intel/git-hook-script', { params: { serverUrl } }).then(r => r.data || {}),

  // System Maintenance & Reset
  wipeWholeBrain: () => api.post('/system/wipe').then(r => r.data || {}),

  // Health
  getHealth: () => api.get('/actuator/health').then(r => r.data || {}).catch(() => ({ status: 'UNKNOWN' })),
};

export default api;