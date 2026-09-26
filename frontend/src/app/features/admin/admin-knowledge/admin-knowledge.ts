import { HttpErrorResponse } from '@angular/common/http';
import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { inject } from '@angular/core';
import { AdminApi } from '../admin';
import { IngestResult } from '../../../shared/models/knowledge.model';

type IngestMode = 'text' | 'youtube' | 'pdf';

@Component({
  selector: 'app-admin-knowledge',
  imports: [FormsModule],
  templateUrl: './admin-knowledge.html',
  styleUrl: './admin-knowledge.css',
})
export class AdminKnowledge {
  private readonly adminApi = inject(AdminApi);

  mode = signal<IngestMode>('text');

  title = '';
  text = '';
  youtubeUrl = '';
  selectedFile: File | null = null;

  submitting = signal(false);
  errorMsg = signal('');
  result = signal<IngestResult | null>(null);

  setMode(mode: IngestMode) {
    this.mode.set(mode);
    this.errorMsg.set('');
    this.result.set(null);
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    this.selectedFile = input.files?.[0] ?? null;
  }

  async submit() {
    this.submitting.set(true);
    this.errorMsg.set('');
    this.result.set(null);
    try {
      const result = await this.runIngest();
      this.result.set(result);
      this.title = '';
      this.text = '';
      this.youtubeUrl = '';
      this.selectedFile = null;
    } catch (err) {
      console.error(err);
      if (err instanceof HttpErrorResponse && err.status === 403) {
        this.errorMsg.set("Your account doesn't have admin access.");
      } else {
        this.errorMsg.set("Couldn't ingest that — try again.");
      }
    } finally {
      this.submitting.set(false);
    }
  }

  private runIngest(): Promise<IngestResult> {
    switch (this.mode()) {
      case 'text':
        return this.adminApi.ingestText(this.title, this.text);
      case 'youtube':
        return this.adminApi.ingestYoutube(this.youtubeUrl);
      case 'pdf':
        if (!this.selectedFile) return Promise.reject(new Error('No file selected'));
        return this.adminApi.ingestFile(this.selectedFile);
    }
  }
}
