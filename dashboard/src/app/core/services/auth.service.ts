import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, of, throwError, ReplaySubject } from 'rxjs';
import { tap, catchError, map, first, switchMap } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { User, TokenResponse, isSuperAdmin } from '../models';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private currentUserSubject = new BehaviorSubject<User | null>(null);
  public currentUser$ = this.currentUserSubject.asObservable();
  
  private authReadySubject = new ReplaySubject<boolean>(1);
  public authReady$ = this.authReadySubject.asObservable();

  constructor(
    private http: HttpClient,
    private router: Router
  ) {
    this.loadStoredUser();
  }

  private loadStoredUser(): void {
    const token = localStorage.getItem('access_token');
    if (token) {
      this.fetchUserInfo().subscribe({
        next: () => {
          this.authReadySubject.next(true);
        },
        error: () => {
          const refreshToken = localStorage.getItem('refresh_token');
          if (refreshToken) {
            this.refreshToken().subscribe({
              next: () => {
                this.fetchUserInfo().subscribe({
                  next: () => this.authReadySubject.next(true),
                  error: () => {
                    this.clearTokens();
                    this.authReadySubject.next(false);
                  }
                });
              },
              error: () => {
                this.clearTokens();
                this.authReadySubject.next(false);
              }
            });
          } else {
            this.clearTokens();
            this.authReadySubject.next(false);
          }
        }
      });
    } else {
      this.authReadySubject.next(false);
    }
  }
  
  private clearTokens(): void {
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
    this.currentUserSubject.next(null);
  }

  get isAuthenticated(): boolean {
    return !!localStorage.getItem('access_token') && this.currentUserSubject.value !== null;
  }

  get currentUser(): User | null {
    return this.currentUserSubject.value;
  }

  initiateLogin(): void {
    const codeVerifier = this.generateCodeVerifier();
    const codeChallenge = this.generateCodeChallenge(codeVerifier);
    const state = this.generateState();

    sessionStorage.setItem('oauth2_code_verifier', codeVerifier);
    sessionStorage.setItem('oauth2_state', state);

    const params = new URLSearchParams({
      response_type: 'code',
      client_id: environment.oauth2.clientId,
      redirect_uri: environment.oauth2.redirectUri,
      scope: environment.oauth2.scopes,
      state: state,
      code_challenge: codeChallenge,
      code_challenge_method: 'S256'
    });

    window.location.href = `${environment.oauth2.loginUrl}/oauth2/authorize?${params.toString()}`;
  }

  handleCallback(code: string, state: string): Observable<boolean> {
    const storedState = sessionStorage.getItem('oauth2_state');
    const codeVerifier = sessionStorage.getItem('oauth2_code_verifier');

    if (state !== storedState) {
      return throwError(() => new Error('Invalid state'));
    }

    sessionStorage.removeItem('oauth2_state');
    sessionStorage.removeItem('oauth2_code_verifier');

    const body = new HttpParams()
      .set('grant_type', 'authorization_code')
      .set('code', code)
      .set('redirect_uri', environment.oauth2.redirectUri)
      .set('client_id', environment.oauth2.clientId)
      .set('code_verifier', codeVerifier || '');

    return this.http.post<TokenResponse>(
      `${environment.oauth2.authorizationServer}/oauth2/token`,
      body.toString(),
      {
        headers: new HttpHeaders({
          'Content-Type': 'application/x-www-form-urlencoded'
        })
      }
    ).pipe(
      tap(tokens => {
        localStorage.setItem('access_token', tokens.access_token);
        if (tokens.refresh_token) {
          localStorage.setItem('refresh_token', tokens.refresh_token);
        }
      }),
      switchMap(() => this.fetchUserInfo()),
      tap(() => this.authReadySubject.next(true)),
      map(() => true),
      catchError(err => {
        console.error('Token exchange failed:', err);
        this.clearTokens();
        this.authReadySubject.next(false);
        return of(false);
      })
    );
  }

  private storeTokens(tokens: TokenResponse): void {
    localStorage.setItem('access_token', tokens.access_token);
    if (tokens.refresh_token) {
      localStorage.setItem('refresh_token', tokens.refresh_token);
    }
  }

  fetchUserInfo(): Observable<User> {
    return this.http.get<User>(`${environment.oauth2.authorizationServer}/auth/me`, {
      headers: this.getAuthHeaders()
    }).pipe(
      tap(user => {
        console.log('Raw user info from server:', user);
        const mappedUser: User = {
          id: (user as any).sub || (user as any).id,
          email: user.email,
          firstName: (user as any).given_name || user.firstName,
          lastName: (user as any).family_name || user.lastName,
          roles: (user as any).roles || []
        };
        console.log('Mapped user:', mappedUser);
        this.currentUserSubject.next(mappedUser);
      }),
      catchError(err => {
        console.error('Failed to fetch user info:', err);
        return throwError(() => err);
      })
    );
  }

  refreshToken(): Observable<TokenResponse> {
    const refreshToken = localStorage.getItem('refresh_token');
    if (!refreshToken) {
      return throwError(() => new Error('No refresh token'));
    }

    const body = new HttpParams()
      .set('grant_type', 'refresh_token')
      .set('refresh_token', refreshToken)
      .set('client_id', environment.oauth2.clientId);

    return this.http.post<TokenResponse>(
      `${environment.oauth2.authorizationServer}/oauth2/token`,
      body.toString(),
      {
        headers: new HttpHeaders({
          'Content-Type': 'application/x-www-form-urlencoded'
        })
      }
    ).pipe(
      tap(tokens => this.storeTokens(tokens))
    );
  }

  logout(): void {
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
    this.currentUserSubject.next(null);
    this.router.navigate(['/login']);
  }

  getAuthHeaders(): HttpHeaders {
    const token = localStorage.getItem('access_token');
    return new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });
  }

  getAccessToken(): string | null {
    return localStorage.getItem('access_token');
  }

  private generateCodeVerifier(): string {
    const array = new Uint8Array(32);
    crypto.getRandomValues(array);
    return this.base64UrlEncode(array);
  }

  private generateCodeChallenge(verifier: string): string {
    const encoder = new TextEncoder();
    const data = encoder.encode(verifier);
    const hashBuffer = this.sha256Sync(data);
    return this.base64UrlEncode(new Uint8Array(hashBuffer));
  }

  private sha256Sync(data: Uint8Array): ArrayBuffer {
    let hash = 0x6a09e667;
    let h1 = 0xbb67ae85, h2 = 0x3c6ef372, h3 = 0xa54ff53a;
    let h4 = 0x510e527f, h5 = 0x9b05688c, h6 = 0x1f83d9ab, h7 = 0x5be0cd19;
    
    const k = [
      0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
      0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
      0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
      0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
      0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
      0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
      0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
      0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
    ];

    const padded = this.padMessage(data);
    const view = new DataView(padded.buffer);

    for (let i = 0; i < padded.length; i += 64) {
      const w = new Uint32Array(64);
      for (let j = 0; j < 16; j++) {
        w[j] = view.getUint32(i + j * 4);
      }
      for (let j = 16; j < 64; j++) {
        const s0 = this.rotr(w[j-15], 7) ^ this.rotr(w[j-15], 18) ^ (w[j-15] >>> 3);
        const s1 = this.rotr(w[j-2], 17) ^ this.rotr(w[j-2], 19) ^ (w[j-2] >>> 10);
        w[j] = (w[j-16] + s0 + w[j-7] + s1) >>> 0;
      }

      let a = hash, b = h1, c = h2, d = h3, e = h4, f = h5, g = h6, hh = h7;

      for (let j = 0; j < 64; j++) {
        const S1 = this.rotr(e, 6) ^ this.rotr(e, 11) ^ this.rotr(e, 25);
        const ch = (e & f) ^ (~e & g);
        const temp1 = (hh + S1 + ch + k[j] + w[j]) >>> 0;
        const S0 = this.rotr(a, 2) ^ this.rotr(a, 13) ^ this.rotr(a, 22);
        const maj = (a & b) ^ (a & c) ^ (b & c);
        const temp2 = (S0 + maj) >>> 0;

        hh = g; g = f; f = e; e = (d + temp1) >>> 0;
        d = c; c = b; b = a; a = (temp1 + temp2) >>> 0;
      }

      hash = (hash + a) >>> 0;
      h1 = (h1 + b) >>> 0; h2 = (h2 + c) >>> 0; h3 = (h3 + d) >>> 0;
      h4 = (h4 + e) >>> 0; h5 = (h5 + f) >>> 0; h6 = (h6 + g) >>> 0; h7 = (h7 + hh) >>> 0;
    }

    const result = new ArrayBuffer(32);
    const resultView = new DataView(result);
    resultView.setUint32(0, hash);
    resultView.setUint32(4, h1);
    resultView.setUint32(8, h2);
    resultView.setUint32(12, h3);
    resultView.setUint32(16, h4);
    resultView.setUint32(20, h5);
    resultView.setUint32(24, h6);
    resultView.setUint32(28, h7);
    return result;
  }

  private rotr(n: number, x: number): number {
    return (n >>> x) | (n << (32 - x));
  }

  private padMessage(data: Uint8Array): Uint8Array {
    const bitLen = data.length * 8;
    const padLen = (64 - ((data.length + 9) % 64)) % 64;
    const padded = new Uint8Array(data.length + 1 + padLen + 8);
    padded.set(data);
    padded[data.length] = 0x80;
    const view = new DataView(padded.buffer);
    view.setUint32(padded.length - 4, bitLen);
    return padded;
  }

  private base64UrlEncode(array: Uint8Array): string {
    let binary = '';
    array.forEach(byte => binary += String.fromCharCode(byte));
    return btoa(binary)
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=/g, '');
  }

  private generateState(): string {
    const array = new Uint8Array(16);
    crypto.getRandomValues(array);
    return this.base64UrlEncode(array);
  }
}
