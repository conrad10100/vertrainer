import { Service, signal } from '@angular/core';
import { createClient, Session, SupabaseClient } from '@supabase/supabase-js';
import { environment } from '../../environments/environment';

@Service()
export class Supabase {
  readonly client: SupabaseClient = createClient(environment.supabaseUrl, environment.supabaseAnonKey);
  readonly session = signal<Session | null>(null);

  constructor() {
    this.client.auth.getSession().then(({ data }) => this.session.set(data.session));
    this.client.auth.onAuthStateChange((_event, session) => this.session.set(session));
  }

  async signUp(email: string, password: string) {
    const { error } = await this.client.auth.signUp({ email, password });
    if (error) throw error;
  }

  async signIn(email: string, password: string) {
    const { error } = await this.client.auth.signInWithPassword({ email, password });
    if (error) throw error;
  }

  async signOut() {
    await this.client.auth.signOut();
  }
}
