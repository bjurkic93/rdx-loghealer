import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { map, take } from 'rxjs/operators';

export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.authReady$.pipe(
    take(1),
    map(isAuthenticated => {
      if (isAuthenticated && authService.isAuthenticated) {
        return true;
      }
      router.navigate(['/login']);
      return false;
    })
  );
};
