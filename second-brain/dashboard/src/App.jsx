import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Layout from './components/Layout';
import Home from './pages/Home';
import MemoryExplorer from './pages/MemoryExplorer';
import AgentActivity from './pages/AgentActivity';
import RepositoryExplorer from './pages/RepositoryExplorer';
import SkillsView from './pages/SkillsView';
import HandoffsView from './pages/HandoffsView';
import KnowledgeGraph from './pages/KnowledgeGraph';
import Documentation from './pages/Documentation';
import BrainChat from './pages/BrainChat';
import ProjectDocuments from './pages/ProjectDocuments';
import Projects from './pages/Projects';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Layout />}>
          <Route index element={<Home />} />
          <Route path="chat" element={<BrainChat />} />
          <Route path="projects" element={<Projects />} />
          <Route path="memory" element={<MemoryExplorer />} />
          <Route path="documents" element={<ProjectDocuments />} />
          <Route path="agents" element={<AgentActivity />} />
          <Route path="repositories" element={<RepositoryExplorer />} />
          <Route path="graph" element={<KnowledgeGraph />} />
          <Route path="skills" element={<SkillsView />} />
          <Route path="handoffs" element={<HandoffsView />} />
          <Route path="docs" element={<Documentation />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App