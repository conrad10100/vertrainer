import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Supabase } from './supabase';

export const authGuard: CanActivateFn = () => {
  if (inject(Supabase).session()) {
    return true;
  }
  return inject(Router).createUrlTree(['/login']);
};
