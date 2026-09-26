export interface ChunkSummary {
  chunkId: string;
  phase: string | null;
  topic: string;
  gist: string;
}

export interface IngestResult {
  sourceId: string;
  chunkCount: number;
  chunks: ChunkSummary[];
}
