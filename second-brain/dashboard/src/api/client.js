import axios from 'axios';

const api = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' }
});

export const brainApi = {
  // Memory
  searchMemory: (query) => api.get(`/memory/search?q=${encodeURIComponent(query)}`),
  getMemories: () => api.get('/memory'),
  createMemory: (data) => api.post('/memory', data),
  
  // Projects
  getProjects: () => api.get('/projects'),
  createProject: (data) => api.post('/projects', data),
  
  // Repositories
  getRepositories: () => api.get('/repositories'),
  addRepository: (url, projectId) => api.post('/repository-intel/add-url', { url, projectId }),
  
  // Agents
  getAgents: () => api.get('/agents'),
  
  // Sessions
  getRecentSessions: () => api.get('/sessions/recent'),
  
  // Events
  getRecentEvents: () => api.get('/events'),
  
  // Decisions
  getRecentDecisions: () => api.get('/decisions/recent'),
  
  // Tasks
  getOpenTasks: () => api.get('/tasks/open'),
  
  // Skills
  getSkills: () => api.get('/skills'),
  
  // Graph
  getGraphStats: () => api.get('/graph/stats'),
  getGraphVisual: (limit) => api.get('/graph/visual', { params: { limit } }),
  
  // Handoffs
  getLatestHandoff: (repoId) => api.get(`/handoffs/repository/${repoId}/latest`),
  
  // Health
  getHealth: () => api.get('/actuator/health'),
};

export default api;