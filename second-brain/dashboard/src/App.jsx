import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Layout from './components/Layout';
import Home from './pages/Home';
import MemoryExplorer from './pages/MemoryExplorer';
import AgentActivity from './pages/AgentActivity';
import RepositoryExplorer from './pages/RepositoryExplorer';
import SkillsView from './pages/SkillsView';
import HandoffsView from './pages/HandoffsView';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Layout />}>
          <Route index element={<Home />} />
          <Route path="memory" element={<MemoryExplorer />} />
          <Route path="agents" element={<AgentActivity />} />
          <Route path="repositories" element={<RepositoryExplorer />} />
          <Route path="skills" element={<SkillsView />} />
          <Route path="handoffs" element={<HandoffsView />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App