import { useState, useRef } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { brainApi } from '../api/client';
import {
  FileText,
  Image as ImageIcon,
  Upload,
  Plus,
  Trash2,
  ExternalLink,
  Search,
  Filter,
  Folder,
  CheckCircle2,
  Loader2,
  X,
  Eye,
  FileCode,
  Tag,
  Download
} from 'lucide-react';

export default function ProjectDocuments() {
  const queryClient = useQueryClient();
  const fileInputRef = useRef(null);

  const [selectedProjectId, setSelectedProjectId] = useState('ALL');
  const [activeTab, setActiveTab] = useState('all'); // 'all' | 'documents' | 'images'
  const [searchQuery, setSearchQuery] = useState('');
  const [showUploadModal, setShowUploadModal] = useState(false);
  const [showNoteModal, setShowNoteModal] = useState(false);
  const [previewImage, setPreviewImage] = useState(null);
  const [isUploading, setIsUploading] = useState(false);

  // Upload Form State
  const [uploadFile, setUploadFile] = useState(null);
  const [uploadTitle, setUploadTitle] = useState('');
  const [uploadDesc, setUploadDesc] = useState('');
  const [uploadTags, setUploadTags] = useState('');
  const [uploadProjectId, setUploadProjectId] = useState('');

  // Note Form State
  const [noteTitle, setNoteTitle] = useState('');
  const [noteContent, setNoteContent] = useState('');
  const [noteDesc, setNoteDesc] = useState('');
  const [noteTags, setNoteTags] = useState('');
  const [noteProjectId, setNoteProjectId] = useState('');

  // Fetch Projects
  const { data: projects = [], isLoading: projectsLoading } = useQuery({
    queryKey: ['projects'],
    queryFn: () => brainApi.getProjects(),
  });

  // Fetch Documents
  const { data: documents = [], isLoading: docsLoading } = useQuery({
    queryKey: ['projectDocuments', selectedProjectId],
    queryFn: () => selectedProjectId === 'ALL'
      ? brainApi.getAllDocuments()
      : brainApi.getProjectDocuments(selectedProjectId),
  });

  const filteredDocs = documents.filter((doc) => {
    if (activeTab === 'documents' && doc.fileType !== 'DOCUMENT') return false;
    if (activeTab === 'images' && doc.fileType !== 'IMAGE') return false;
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      const matchTitle = doc.title?.toLowerCase().includes(q);
      const matchFile = doc.fileName?.toLowerCase().includes(q);
      const matchDesc = doc.description?.toLowerCase().includes(q);
      const matchProj = doc.project?.name?.toLowerCase().includes(q);
      return matchTitle || matchFile || matchDesc || matchProj;
    }
    return true;
  });

  const handleFileUpload = async (e) => {
    e.preventDefault();
    if (!uploadFile || !uploadProjectId) {
      alert('Please select a file and a target project.');
      return;
    }

    setIsUploading(true);
    try {
      const formData = new FormData();
      formData.append('file', uploadFile);
      if (uploadTitle.trim()) formData.append('title', uploadTitle);
      if (uploadDesc.trim()) formData.append('description', uploadDesc);
      if (uploadTags.trim()) {
        uploadTags.split(',').map(t => t.trim()).filter(Boolean).forEach(t => formData.append('tags', t));
      }

      await brainApi.uploadProjectDocument(uploadProjectId, formData);
      setShowUploadModal(false);
      setUploadFile(null);
      setUploadTitle('');
      setUploadDesc('');
      setUploadTags('');
      queryClient.invalidateQueries({ queryKey: ['projectDocuments'] });
    } catch (err) {
      alert('Upload failed: ' + err.message);
    } finally {
      setIsUploading(false);
    }
  };

  const handleCreateNote = async (e) => {
    e.preventDefault();
    if (!noteTitle.trim() || !noteContent.trim() || !noteProjectId) {
      alert('Please provide title, markdown content, and choose a project.');
      return;
    }

    setIsUploading(true);
    try {
      const tags = noteTags.split(',').map(t => t.trim()).filter(Boolean);
      await brainApi.createProjectNote(noteProjectId, {
        title: noteTitle,
        content: noteContent,
        description: noteDesc,
        tags: tags.length > 0 ? tags : undefined,
      });
      setShowNoteModal(false);
      setNoteTitle('');
      setNoteContent('');
      setNoteDesc('');
      setNoteTags('');
      queryClient.invalidateQueries({ queryKey: ['projectDocuments'] });
    } catch (err) {
      alert('Failed to create note: ' + err.message);
    } finally {
      setIsUploading(false);
    }
  };

  const handleDelete = async (docId) => {
    if (!confirm('Are you sure you want to delete this file from the Brain?')) return;
    try {
      await brainApi.deleteDocument(docId);
      queryClient.invalidateQueries({ queryKey: ['projectDocuments'] });
    } catch (err) {
      alert('Delete failed: ' + err.message);
    }
  };

  const formatBytes = (bytes) => {
    if (!bytes || bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold">Project Documents &amp; Images</h2>
          <p className="text-gray-400 text-xs mt-0.5">
            Architecture diagrams, markdown specs, mockups, and PDFs stored in MinIO, vectorized in Qdrant, and linked in Neo4j
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={() => {
              if (projects.length > 0) {
                setUploadProjectId(projects[0].id);
                setNoteProjectId(projects[0].id);
              }
              setShowNoteModal(true);
            }}
            className="flex items-center gap-2 px-3.5 py-2 rounded-lg bg-gray-900 hover:bg-gray-800 border border-gray-700 text-gray-200 text-xs font-semibold transition-all cursor-pointer shadow-sm"
          >
            <FileCode className="w-4 h-4 text-cyan-400" />
            <span>New Markdown Note</span>
          </button>

          <button
            onClick={() => {
              if (projects.length > 0) {
                setUploadProjectId(projects[0].id);
              }
              setShowUploadModal(true);
            }}
            className="flex items-center gap-2 px-4 py-2 rounded-lg bg-purple-600 hover:bg-purple-700 text-white text-xs font-semibold transition-all cursor-pointer shadow-lg shadow-purple-900/30"
          >
            <Upload className="w-4 h-4" />
            <span>Upload File / Image</span>
          </button>
        </div>
      </div>

      {/* Filter & Search Bar */}
      <div className="bg-gray-900 border border-gray-800 rounded-xl p-4 space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          {/* Project Filter */}
          <div className="flex items-center gap-2 min-w-[200px]">
            <Folder className="w-4 h-4 text-purple-400 shrink-0" />
            <select
              value={selectedProjectId}
              onChange={(e) => setSelectedProjectId(e.target.value)}
              className="bg-gray-950 border border-gray-800 rounded-lg px-3 py-1.5 text-xs text-gray-200 focus:outline-none focus:border-purple-500 cursor-pointer"
            >
              <option value="ALL">All Projects ({projects.length})</option>
              {projects.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </div>

          {/* Type Filter */}
          <div className="flex items-center gap-1.5 bg-gray-950 p-1 rounded-lg border border-gray-800 text-xs">
            <button
              onClick={() => setActiveTab('all')}
              className={`px-3 py-1 rounded-md font-medium transition-colors cursor-pointer ${
                activeTab === 'all' ? 'bg-purple-600 text-white' : 'text-gray-400 hover:text-gray-200'
              }`}
            >
              All ({documents.length})
            </button>
            <button
              onClick={() => setActiveTab('images')}
              className={`px-3 py-1 rounded-md font-medium transition-colors cursor-pointer flex items-center gap-1 ${
                activeTab === 'images' ? 'bg-purple-600 text-white' : 'text-gray-400 hover:text-gray-200'
              }`}
            >
              <ImageIcon className="w-3.5 h-3.5" />
              <span>Images &amp; Diagrams</span>
            </button>
            <button
              onClick={() => setActiveTab('documents')}
              className={`px-3 py-1 rounded-md font-medium transition-colors cursor-pointer flex items-center gap-1 ${
                activeTab === 'documents' ? 'bg-purple-600 text-white' : 'text-gray-400 hover:text-gray-200'
              }`}
            >
              <FileText className="w-3.5 h-3.5" />
              <span>Documents &amp; Specs</span>
            </button>
          </div>

          {/* Search Input */}
          <div className="relative flex-1 min-w-[200px]">
            <Search className="w-4 h-4 text-gray-500 absolute left-3 top-2.5" />
            <input
              type="text"
              placeholder="Search file name, description, tags..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-3 py-1.5 bg-gray-950 border border-gray-800 rounded-lg text-xs text-gray-200 placeholder-gray-500 focus:outline-none focus:border-purple-500"
            />
          </div>
        </div>
      </div>

      {/* Content Gallery / Grid */}
      {docsLoading ? (
        <div className="py-16 text-center text-gray-500 flex items-center justify-center gap-2 text-sm">
          <Loader2 className="w-5 h-5 animate-spin text-purple-400" />
          <span>Loading project assets...</span>
        </div>
      ) : filteredDocs.length === 0 ? (
        <div className="bg-gray-900 border border-gray-800 rounded-xl p-12 text-center space-y-3">
          <FileText className="w-12 h-12 text-gray-600 mx-auto" />
          <h3 className="text-gray-300 font-semibold">No Documents or Images Found</h3>
          <p className="text-gray-500 text-xs max-w-md mx-auto">
            Upload architecture diagrams, screenshots, PRDs, or Markdown specs to associate visual and structured knowledge with your projects.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
          {filteredDocs.map((doc) => {
            const isImg = doc.fileType === 'IMAGE';
            return (
              <div
                key={doc.id}
                className="bg-gray-900 border border-gray-800 hover:border-gray-700 rounded-xl overflow-hidden flex flex-col transition-all shadow-md group"
              >
                {/* Visual Preview */}
                <div className="h-40 bg-gray-950 relative flex items-center justify-center overflow-hidden border-b border-gray-800/80">
                  {isImg && doc.url ? (
                    <img
                      src={doc.url}
                      alt={doc.title}
                      className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300 cursor-pointer"
                      onClick={() => setPreviewImage(doc)}
                    />
                  ) : (
                    <div className="flex flex-col items-center gap-2 text-gray-600">
                      <FileText className="w-12 h-12 text-purple-400/60" />
                      <span className="text-[10px] font-mono uppercase tracking-wider text-gray-500">
                        {doc.fileName?.split('.').pop() || 'DOC'}
                      </span>
                    </div>
                  )}

                  {/* Badge */}
                  <span
                    className={`absolute top-2 left-2 px-2 py-0.5 rounded text-[10px] font-semibold border ${
                      isImg
                        ? 'bg-cyan-950 text-cyan-300 border-cyan-800'
                        : 'bg-purple-950 text-purple-300 border-purple-800'
                    }`}
                  >
                    {isImg ? 'IMAGE' : 'DOCUMENT'}
                  </span>

                  {/* Quick Actions */}
                  <div className="absolute top-2 right-2 flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                    {doc.url && (
                      <a
                        href={doc.url}
                        target="_blank"
                        rel="noreferrer"
                        className="p-1.5 bg-gray-900/90 hover:bg-gray-800 text-gray-300 rounded-md border border-gray-700 shadow"
                        title="Open in new tab"
                      >
                        <ExternalLink className="w-3.5 h-3.5" />
                      </a>
                    )}
                    <button
                      onClick={() => handleDelete(doc.id)}
                      className="p-1.5 bg-red-950/90 hover:bg-red-900 text-red-300 rounded-md border border-red-800 shadow cursor-pointer"
                      title="Delete document"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>

                {/* Details Body */}
                <div className="p-4 flex-1 flex flex-col justify-between space-y-3">
                  <div>
                    <h4 className="font-semibold text-gray-100 text-sm truncate" title={doc.title}>
                      {doc.title}
                    </h4>
                    <p className="text-gray-500 text-xs truncate mt-0.5 font-mono">
                      {doc.fileName}
                    </p>
                    {doc.description && (
                      <p className="text-gray-400 text-xs line-clamp-2 mt-1.5">
                        {doc.description}
                      </p>
                    )}
                  </div>

                  <div className="space-y-2 pt-2 border-t border-gray-800/60">
                    <div className="flex items-center justify-between text-[11px] text-gray-500">
                      <span>{formatBytes(doc.sizeBytes)}</span>
                      <span className="text-purple-400 font-medium truncate max-w-[120px]">
                        {doc.project?.name || 'Project'}
                      </span>
                    </div>

                    {doc.tags && doc.tags.length > 0 && (
                      <div className="flex flex-wrap gap-1">
                        {Array.from(doc.tags).map((t, idx) => (
                          <span
                            key={idx}
                            className="px-1.5 py-0.2 rounded bg-gray-950 text-gray-400 border border-gray-800 text-[10px] font-mono"
                          >
                            #{t}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Upload File Modal */}
      {showUploadModal && (
        <div className="fixed inset-0 bg-black/75 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-gray-900 border border-gray-800 rounded-xl max-w-lg w-full shadow-2xl overflow-hidden flex flex-col">
            <div className="p-4 border-b border-gray-800 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Upload className="w-5 h-5 text-purple-400" />
                <h3 className="font-bold text-gray-100 text-sm">Upload Document or Image</h3>
              </div>
              <button
                onClick={() => setShowUploadModal(false)}
                className="text-gray-400 hover:text-gray-200 cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleFileUpload} className="p-5 space-y-4 text-xs">
              <div>
                <label className="block text-gray-400 font-medium mb-1">Target Project *</label>
                <select
                  required
                  value={uploadProjectId}
                  onChange={(e) => setUploadProjectId(e.target.value)}
                  className="w-full bg-gray-950 border border-gray-800 rounded-lg p-2 text-gray-100 focus:outline-none focus:border-purple-500 cursor-pointer"
                >
                  {projects.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-gray-400 font-medium mb-1">File * (PDF, MD, PNG, JPG, SVG, etc.)</label>
                <input
                  type="file"
                  required
                  ref={fileInputRef}
                  onChange={(e) => setUploadFile(e.target.files[0])}
                  className="w-full bg-gray-950 border border-gray-800 rounded-lg p-2 text-gray-300 focus:outline-none focus:border-purple-500 cursor-pointer"
                />
              </div>

              <div>
                <label className="block text-gray-400 font-medium mb-1">Title (Optional)</label>
                <input
                  type="text"
                  placeholder="e.g. Architecture Component Diagram"
                  value={uploadTitle}
                  onChange={(e) => setUploadTitle(e.target.value)}
                  className="w-full bg-gray-950 border border-gray-800 rounded-lg p-2 text-gray-100 placeholder-gray-600 focus:outline-none focus:border-purple-500"
                />
              </div>

              <div>
                <label className="block text-gray-400 font-medium mb-1">Description / Notes</label>
                <textarea
                  rows={2}
                  placeholder="e.g. High-level auth service data flow and database relationships"
                  value={uploadDesc}
                  onChange={(e) => setUploadDesc(e.target.value)}
                  className="w-full bg-gray-950 border border-gray-800 rounded-lg p-2 text-gray-100 placeholder-gray-600 focus:outline-none focus:border-purple-500"
                />
              </div>

              <div>
                <label className="block text-gray-400 font-medium mb-1">Tags (comma-separated)</label>
                <input
                  type="text"
                  placeholder="diagram, architecture, auth, v2"
                  value={uploadTags}
                  onChange={(e) => setUploadTags(e.target.value)}
                  className="w-full bg-gray-950 border border-gray-800 rounded-lg p-2 text-gray-100 placeholder-gray-600 focus:outline-none focus:border-purple-500 font-mono"
                />
              </div>

              <div className="pt-3 border-t border-gray-800 flex justify-end gap-3">
                <button
                  type="button"
                  onClick={() => setShowUploadModal(false)}
                  className="px-4 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-gray-300 cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isUploading}
                  className="px-5 py-2 rounded-lg bg-purple-600 hover:bg-purple-700 text-white font-semibold flex items-center gap-2 cursor-pointer disabled:opacity-50"
                >
                  {isUploading ? <Loader2 className="w-4 h-4 animate-spin" /> : null}
                  <span>Upload to Brain</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* New Markdown Note Modal */}
      {showNoteModal && (
        <div className="fixed inset-0 bg-black/75 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-gray-900 border border-gray-800 rounded-xl max-w-2xl w-full shadow-2xl overflow-hidden flex flex-col">
            <div className="p-4 border-b border-gray-800 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <FileCode className="w-5 h-5 text-cyan-400" />
                <h3 className="font-bold text-gray-100 text-sm">Write Project Markdown Note</h3>
              </div>
              <button
                onClick={() => setShowNoteModal(false)}
                className="text-gray-400 hover:text-gray-200 cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleCreateNote} className="p-5 space-y-4 text-xs">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-gray-400 font-medium mb-1">Target Project *</label>
                  <select
                    required
                    value={noteProjectId}
                    onChange={(e) => setNoteProjectId(e.target.value)}
                    className="w-full bg-gray-950 border border-gray-800 rounded-lg p-2 text-gray-100 focus:outline-none focus:border-purple-500 cursor-pointer"
                  >
                    {projects.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name}
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-gray-400 font-medium mb-1">Note Title *</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. Authentication RFC & Token Spec"
                    value={noteTitle}
                    onChange={(e) => setNoteTitle(e.target.value)}
                    className="w-full bg-gray-950 border border-gray-800 rounded-lg p-2 text-gray-100 placeholder-gray-600 focus:outline-none focus:border-purple-500"
                  />
                </div>
              </div>

              <div>
                <label className="block text-gray-400 font-medium mb-1">Markdown Content *</label>
                <textarea
                  rows={8}
                  required
                  placeholder="## Architecture Overview&#10;&#10;Describe key design choices, invariants, and specs here..."
                  value={noteContent}
                  onChange={(e) => setNoteContent(e.target.value)}
                  className="w-full bg-gray-950 border border-gray-800 rounded-lg p-2.5 text-gray-100 placeholder-gray-600 focus:outline-none focus:border-purple-500 font-mono text-xs leading-relaxed"
                />
              </div>

              <div>
                <label className="block text-gray-400 font-medium mb-1">Tags (comma-separated)</label>
                <input
                  type="text"
                  placeholder="spec, rfc, auth, architecture"
                  value={noteTags}
                  onChange={(e) => setNoteTags(e.target.value)}
                  className="w-full bg-gray-950 border border-gray-800 rounded-lg p-2 text-gray-100 placeholder-gray-600 focus:outline-none focus:border-purple-500 font-mono"
                />
              </div>

              <div className="pt-3 border-t border-gray-800 flex justify-end gap-3">
                <button
                  type="button"
                  onClick={() => setShowNoteModal(false)}
                  className="px-4 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-gray-300 cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isUploading}
                  className="px-5 py-2 rounded-lg bg-purple-600 hover:bg-purple-700 text-white font-semibold flex items-center gap-2 cursor-pointer disabled:opacity-50"
                >
                  {isUploading ? <Loader2 className="w-4 h-4 animate-spin" /> : null}
                  <span>Save Note to Brain</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Image Lightbox Modal */}
      {previewImage && (
        <div
          className="fixed inset-0 bg-black/90 backdrop-blur-md z-50 flex items-center justify-center p-4"
          onClick={() => setPreviewImage(null)}
        >
          <div
            className="max-w-4xl max-h-[90vh] bg-gray-900 border border-gray-800 rounded-2xl overflow-hidden shadow-2xl flex flex-col"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="p-4 bg-gray-950/80 border-b border-gray-800 flex items-center justify-between">
              <div>
                <h3 className="font-bold text-gray-100 text-sm">{previewImage.title}</h3>
                <p className="text-xs text-gray-500 font-mono">{previewImage.fileName}</p>
              </div>
              <div className="flex items-center gap-2">
                {previewImage.url && (
                  <a
                    href={previewImage.url}
                    target="_blank"
                    rel="noreferrer"
                    className="p-1.5 bg-gray-800 hover:bg-gray-700 text-gray-200 rounded-lg text-xs flex items-center gap-1"
                  >
                    <ExternalLink className="w-3.5 h-3.5" />
                    <span>Open Full</span>
                  </a>
                )}
                <button
                  onClick={() => setPreviewImage(null)}
                  className="p-1.5 text-gray-400 hover:text-gray-200 cursor-pointer"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>
            </div>

            <div className="p-4 flex items-center justify-center overflow-auto max-h-[75vh] bg-black/50">
              <img
                src={previewImage.url}
                alt={previewImage.title}
                className="max-w-full max-h-full object-contain rounded-lg shadow-xl"
              />
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
